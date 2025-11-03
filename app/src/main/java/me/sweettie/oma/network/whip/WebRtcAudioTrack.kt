package me.sweettie.oma.network.whip

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import me.sweettie.oma.network.whip.KotlinAudioDeviceModule.Companion.TAG
import me.sweettie.oma.network.whip.WebRtcAudioRecord.Companion.logAudioState
import org.webrtc.CalledByNative
import org.webrtc.Logging
import org.webrtc.ThreadUtils
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback
import org.webrtc.voiceengine.WebRtcAudioUtils.getThreadInfo
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile


class WebRtcAudioTrack {

    val TAG: String = "WebRtcAudioTrackExternal"

    val BITS_PER_SAMPLE: Int = 16

    val CALLBACK_BUFFER_SIZE_MS: Int = 10

    val BUFFERS_PER_SECOND: Int = 1000 / CALLBACK_BUFFER_SIZE_MS

    val AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS: Long = 2000

    val DEFAULT_USAGE: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION

    val AUDIO_TRACK_START: Int = 0

    val AUDIO_TRACK_STOP: Int = 1

    private var nativeAudioTrack: Long = 0
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private val threadChecker = ThreadUtils.ThreadChecker()

    private var byteBuffer: ByteBuffer? = null

    private var audioAttributes: AudioAttributes? = null

    private var audioTrack: AudioTrack? = null

    private var audioThread: AudioTrackThread? = null
    private var volumeLogger: VolumeLogger? = null

    // Samples to be played are replaced by zeros if `speakerMute` is set to true.
    // Can be used to ensure that the speaker is fully muted.
    @Volatile
    var speakerMute: Boolean = false
    private var emptyBytes: ByteArray? = null
    private var useLowLatency = false
    private var initialBufferSizeInFrames = 0

    private var errorCallback: AudioTrackErrorCallback? = null

    private var stateCallback: AudioTrackStateCallback? = null

    companion object {

        const val AUDIO_TRACK_START: Int = 0

        // Indicates the AudioTrack has stopped playing audio.
        const val AUDIO_TRACK_STOP: Int = 1
        private external fun nativeGetPlayoutData(nativeAudioTrackJni: Long, bytes: Int)

        private external fun nativeCacheDirectBufferAddress(
            nativeAudioTrackJni: Long, byteBuffer: ByteBuffer?
        )

        @SuppressLint("Range")
        private fun createAudioTrackOnOreoOrHigher(
            sampleRateInHz: Int, channelConfig: Int,
            bufferSizeInBytes: Int, overrideAttributes: AudioAttributes?
        ): AudioTrack {
            Logging.d(TAG, "createAudioTrackOnOreoOrHigher")
            logNativeOutputSampleRate(sampleRateInHz)

            // Create an audio track where the audio usage is for VoIP and the content type is speech.
            return AudioTrack.Builder()
                .setAudioAttributes(getAudioAttributes(overrideAttributes))
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateInHz)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
        }

        private fun createAudioTrackBeforeOreo(
            sampleRateInHz: Int, channelConfig: Int,
            bufferSizeInBytes: Int, overrideAttributes: AudioAttributes?
        ): AudioTrack {
            Logging.d(TAG, "createAudioTrackBeforeOreo")
            logNativeOutputSampleRate(sampleRateInHz)

            // Create an audio track where the audio usage is for VoIP and the content type is speech.
            return AudioTrack(
                getAudioAttributes(overrideAttributes),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateInHz)
                    .setChannelMask(channelConfig)
                    .build(),
                bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        }

        private fun logNativeOutputSampleRate(requestedSampleRateInHz: Int) {
            val nativeOutputSampleRate =
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_VOICE_CALL)
            Logging.d(TAG, "nativeOutputSampleRate: $nativeOutputSampleRate")
            if (requestedSampleRateInHz != nativeOutputSampleRate) {
                Logging.w(TAG, "Unable to use fast mode since requested sample rate is not native")
            }
        }

        var DEFAULT_USAGE: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION
        private fun getAudioAttributes(overrideAttributes: AudioAttributes?): AudioAttributes {
            var attributesBuilder =
                AudioAttributes.Builder()
                    .setUsage(DEFAULT_USAGE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)

            if (overrideAttributes != null) {
                if (overrideAttributes.usage != AudioAttributes.USAGE_UNKNOWN) {
                    attributesBuilder.setUsage(overrideAttributes.usage)
                }
                if (overrideAttributes.contentType != AudioAttributes.CONTENT_TYPE_UNKNOWN) {
                    attributesBuilder.setContentType(overrideAttributes.contentType)
                }

                attributesBuilder.setFlags(overrideAttributes.flags)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    attributesBuilder =
                        applyAttributesOnQOrHigher(attributesBuilder, overrideAttributes)
                }
            }
            return attributesBuilder.build()
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun applyAttributesOnQOrHigher(
            builder: AudioAttributes.Builder, overrideAttributes: AudioAttributes
        ): AudioAttributes.Builder {
            return builder.setAllowedCapturePolicy(overrideAttributes.allowedCapturePolicy)
        }
    }

    inner class AudioTrackThread(name: String) : Thread(name) {
        @Volatile
        private var keepAlive = true
        private var bufferManager: LowLatencyAudioBufferManager = LowLatencyAudioBufferManager()

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            Logging.d(TAG, "AudioTrackThread" + getThreadInfo())
            assert(audioTrack!!.playState == AudioTrack.PLAYSTATE_PLAYING)

            doAudioTrackStateCallback(AUDIO_TRACK_START)

            val sizeInBytes = byteBuffer!!.capacity()

            while (keepAlive) {
                nativeGetPlayoutData(nativeAudioTrack, sizeInBytes)
                assert(sizeInBytes <= byteBuffer!!.remaining())
                val bytesWritten =
                    audioTrack!!.write(byteBuffer!!, sizeInBytes, AudioTrack.WRITE_BLOCKING)
                if (bytesWritten != sizeInBytes) {
                    Logging.e(
                        TAG,
                        "AudioTrack.write played invalid number of bytes: $bytesWritten"
                    );
                    // If a write() returns a negative value, an error has occurred.
                    // Stop playing and report an error in this case.
                    if (bytesWritten < 0) {
                        keepAlive = false
                        reportWebRtcAudioTrackError("AudioTrack.write failed: $bytesWritten");
                    }
                }
                if (useLowLatency) {
                    bufferManager.maybeAdjustBufferSize(audioTrack)
                }

                byteBuffer!!.rewind()
            }

        }

        fun stopThread() {
            Logging.d(TAG, "stopThread")
            keepAlive = false
        }
    }

    @CalledByNative
    constructor(context: Context, audioManager: AudioManager) : this(
        context,
        audioManager,
        null,
        null,
        null,
        false,
        true
    )


    constructor(
        context: Context,
        audioManager: AudioManager,
        audioAttributes: AudioAttributes?,
        errorCallback: AudioTrackErrorCallback?,
        stateCallback: AudioTrackStateCallback?,
        useLowLatency: Boolean,
        enableVolumeLogger: Boolean
    ) {
        threadChecker.detachThread()
        this.context = context
        this.audioManager = audioManager
        this.audioAttributes = audioAttributes
        this.errorCallback = errorCallback
        this.stateCallback = stateCallback
        this.volumeLogger = if (enableVolumeLogger) VolumeLogger(audioManager) else null
        this.useLowLatency = useLowLatency
        Logging.d(TAG, "ctor" + getThreadInfo())
    }

    @CalledByNative
    fun setNativeAudioTrack(nativeAudioTrack: Long) {
        this.nativeAudioTrack = nativeAudioTrack
    }


    @CalledByNative
    private fun initPlayout(sampleRate: Int, channels: Int, bufferSizeFactor: Double): Int {
        threadChecker.checkIsOnValidThread()
        Logging.d(
            TAG,
            ("initPlayout(sampleRate=" + sampleRate + ", channels=" + channels
                    + ", bufferSizeFactor=" + bufferSizeFactor + ")")
        )
        val bytesPerFrame = channels * (BITS_PER_SAMPLE / 8)
        byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * (sampleRate / BUFFERS_PER_SECOND))
        Logging.d(TAG, "byteBuffer.capacity: " + byteBuffer!!.capacity())
        emptyBytes = ByteArray(byteBuffer!!.capacity())
        nativeCacheDirectBufferAddress(nativeAudioTrack, byteBuffer)

        val channelConfig: Int = channelCountToConfiguration(channels)
        val minBufferSizeInBytes = (AudioTrack.getMinBufferSize(
            sampleRate, channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
                * bufferSizeFactor).toInt()
        Logging.d(TAG, "minBufferSizeInBytes: $minBufferSizeInBytes")

        if (minBufferSizeInBytes < byteBuffer!!.capacity()) {
            reportWebRtcAudioTrackInitError("AudioTrack.getMinBufferSize returns an invalid value.")
            return -1
        }

        if (bufferSizeFactor > 1.0) {
            useLowLatency = false
        }

        if (audioTrack != null) {
            reportWebRtcAudioTrackInitError("Conflict with existing AudioTrack.")
            return -1
        }
        try {
            // Create an AudioTrack object and initialize its associated audio buffer.
            // The size of this buffer determines how long an AudioTrack can play
            // before running out of data.
            if (useLowLatency) {
                // On API level 26 or higher, we can use a low latency mode.
                audioTrack = createAudioTrackOnOreoOrHigher(
                    sampleRate, channelConfig, minBufferSizeInBytes, audioAttributes
                )
            } else {
                audioTrack = createAudioTrackBeforeOreo(
                    sampleRate, channelConfig, minBufferSizeInBytes, audioAttributes
                )
            }
        } catch (e: IllegalArgumentException) {
            reportWebRtcAudioTrackInitError(e.message)
            releaseAudioResources()
            return -1
        }

        // It can happen that an AudioTrack is created but it was not successfully
        // initialized upon creation. Seems to be the case e.g. when the maximum
        // number of globally available audio tracks is exceeded.
        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            reportWebRtcAudioTrackInitError("Initialization of audio track failed.")
            releaseAudioResources()
            return -1
        }
        initialBufferSizeInFrames = audioTrack!!.bufferSizeInFrames
        logMainParameters()
        logMainParametersExtended()
        return minBufferSizeInBytes
    }


    @CalledByNative
    private fun startPlayout(): Boolean {
        threadChecker.checkIsOnValidThread()
        volumeLogger?.start()
        Logging.d(TAG, "startPlayout")

        // Starts playing an audio track.
        try {
            audioTrack!!.play()
        } catch (e: IllegalStateException) {
            reportWebRtcAudioTrackStartError(
                JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_EXCEPTION,
                "AudioTrack.play failed: " + e.message
            )
            releaseAudioResources()
            return false
        }
        if (audioTrack!!.playState != AudioTrack.PLAYSTATE_PLAYING) {
            reportWebRtcAudioTrackStartError(
                JavaAudioDeviceModule.AudioTrackStartErrorCode.AUDIO_TRACK_START_STATE_MISMATCH,
                "AudioTrack.play failed - incorrect state :" + audioTrack!!.playState
            )
            releaseAudioResources()
            return false
        }

        // Create and start new high-priority thread which calls AudioTrack.write()
        // and where we also call the native nativeGetPlayoutData() callback to
        // request decoded audio from WebRTC.
        audioThread = AudioTrackThread("AudioTrackJavaThread")
        audioThread!!.start()
        return true
    }

    @CalledByNative
    private fun getStreamMaxVolume(): Int {
        threadChecker.checkIsOnValidThread()
        Logging.d(TAG, "getStreamMaxVolume")
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
    }

    // Set current volume level for a phone call audio stream.
    @CalledByNative
    private fun setStreamVolume(volume: Int): Boolean {
        threadChecker.checkIsOnValidThread()
        Logging.d(TAG, "setStreamVolume($volume)")
        if (audioManager.isVolumeFixed) {
            Logging.e(TAG, "The device implements a fixed volume policy.")
            return false
        }
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, volume, 0)
        return true
    }

    /** Get current volume level for a phone call audio stream.  */
    @CalledByNative
    private fun getStreamVolume(): Int {
        threadChecker.checkIsOnValidThread()
        Logging.d(TAG, "getStreamVolume")
        return audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
    }

    @CalledByNative
    private fun GetPlayoutUnderrunCount(): Int {
        return if (audioTrack != null) {
            audioTrack!!.underrunCount
        } else {
            -1
        }
    }

    @CalledByNative
    private fun stopPlayout(): Boolean {
        threadChecker.checkIsOnValidThread()
        volumeLogger?.stop()
        Logging.d(TAG, "stopPlayout")
        audioThread!!.stopThread()

        Logging.d(TAG, "Stopping the AudioTrackThread...")
        audioThread!!.interrupt()
        if (!ThreadUtils.joinUninterruptibly(audioThread, AUDIO_TRACK_THREAD_JOIN_TIMEOUT_MS)) {
            Logging.e(TAG, "Join of AudioTrackThread timed out.")
            logAudioState(TAG, context, audioManager)
        }
        Logging.d(TAG, "AudioTrackThread has now been stopped.")
        audioThread = null
        if (audioTrack != null) {
            Logging.d(TAG, "Calling AudioTrack.stop...")
            try {
                audioTrack!!.stop()
                Logging.d(TAG, "AudioTrack.stop is done.")
                doAudioTrackStateCallback(AUDIO_TRACK_STOP)
            } catch (e: IllegalStateException) {
                Logging.e(TAG, "AudioTrack.stop failed: " + e.message)
            }
        }
        releaseAudioResources()
        return true
    }

    private fun logMainParameters() {
        Logging.d(
            TAG,
            ("AudioTrack: "
                    + "session ID: " + audioTrack!!.audioSessionId + ", "
                    + "channels: " + audioTrack!!.channelCount + ", "
                    + "sample rate: " + audioTrack!!.sampleRate
                    + ", " // Gain (>=1.0) expressed as linear multiplier on sample values.
                    + "max gain: " + AudioTrack.getMaxVolume())
        )
    }

    private fun logMainParametersExtended() {
        logBufferSizeInFrames()
        logBufferCapacityInFrames()
    }

    private fun logBufferSizeInFrames() {
        Logging.d(
            TAG,
            ("AudioTrack: " // The effective size of the AudioTrack buffer that the app writes to.
                    + "buffer size in frames: " + audioTrack!!.bufferSizeInFrames)
        )
    }

    @CalledByNative
    private fun getBufferSizeInFrames(): Int {
        return audioTrack!!.bufferSizeInFrames
    }

    @CalledByNative
    private fun getInitialBufferSizeInFrames(): Int {
        return initialBufferSizeInFrames
    }

    private fun logBufferCapacityInFrames() {
        Logging.d(
            TAG,
            ("AudioTrack: " // Maximum size of the AudioTrack buffer in frames.
                    + "buffer capacity in frames: " + audioTrack!!.bufferCapacityInFrames)
        )
    }

    private fun releaseAudioResources() {
        Logging.d(TAG, "releaseAudioResources")
        if (audioTrack != null) {
            audioTrack!!.release()
            audioTrack = null
        }
    }


    private fun channelCountToConfiguration(channels: Int): Int {
        return (if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO)
    }


    private fun reportWebRtcAudioTrackInitError(errorMessage: String?) {
        Logging.e(TAG, "Init playout error: $errorMessage")
        logAudioState(TAG, context!!, audioManager!!)
        errorCallback?.onWebRtcAudioTrackInitError(errorMessage)
    }

    private fun reportWebRtcAudioTrackStartError(
        errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?, errorMessage: String?
    ) {
        Logging.e(TAG, "Start playout error: $errorCode. $errorMessage")
        logAudioState(TAG, context, audioManager)
        errorCallback?.onWebRtcAudioTrackStartError(errorCode, errorMessage)
    }

    private fun reportWebRtcAudioTrackError(errorMessage: String?) {
        Logging.e(TAG, "Run-time playback error: $errorMessage")
        logAudioState(TAG, context, audioManager)
        errorCallback?.onWebRtcAudioTrackError(errorMessage)
    }

    private fun doAudioTrackStateCallback(audioState: Int) {
        Logging.d(TAG, "doAudioTrackStateCallback: $audioState")
        stateCallback?.let {
            when (audioState) {
                WebRtcAudioTrack.AUDIO_TRACK_START -> {
                    stateCallback!!.onWebRtcAudioTrackStart()
                }

                WebRtcAudioTrack.AUDIO_TRACK_STOP -> {
                    stateCallback!!.onWebRtcAudioTrackStop()
                }

                else -> {
                    Logging.e(TAG, "Invalid audio state")
                }
            }
        }
    }
}