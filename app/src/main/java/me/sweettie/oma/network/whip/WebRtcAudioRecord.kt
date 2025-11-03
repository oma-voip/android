package me.sweettie.oma.network.whip

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.MODE_IN_CALL
import android.media.AudioManager.MODE_IN_COMMUNICATION
import android.media.AudioManager.MODE_NORMAL
import android.media.AudioManager.MODE_RINGTONE
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder.AudioSource
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import org.webrtc.CalledByNative
import org.webrtc.Logging
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordErrorCallback
import org.webrtc.audio.JavaAudioDeviceModule.AudioRecordStateCallback
import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback
import org.webrtc.voiceengine.WebRtcAudioEffects
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile


open class WebRtcAudioRecord {
    val TAG: String = "WebRtcAudioRecordExternal"

    companion object {

        val DEFAULT_AUDIO_SOURCE = AudioSource.VOICE_COMMUNICATION
        val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        val nextSchedulerId: AtomicInteger = AtomicInteger(0)

        fun newDefaultScheduler(): ScheduledExecutorService {
            val nextThreadId = AtomicInteger(0)
            return Executors.newScheduledThreadPool(0) { r ->
                val thread = Executors.defaultThreadFactory().newThread(r)
                thread.name = java.lang.String.format(
                    "WebRtcAudioRecordScheduler-%s-%s",
                    nextSchedulerId.getAndIncrement(), nextThreadId.getAndIncrement()
                )
                thread
            }
        }

        fun of(context: Context, audioManager: AudioManager): WebRtcAudioRecord {
            return WebRtcAudioRecord(
                context,
                newDefaultScheduler(),
                audioManager,
                AudioSource.VOICE_COMMUNICATION,
                AudioFormat.ENCODING_PCM_16BIT,
                null,
                null,
                null,
                WebRtcAudioEffects.isAcousticEchoCancelerSupported(),
                WebRtcAudioEffects.isNoiseSuppressorSupported()
            )
        }


        val CHECK_REC_STATUS_DELAY_MS: Int = 100
        val CALLBACK_BUFFER_SIZE_MS: Int = 10
        val BUFFERS_PER_SECOND = 1000 / CALLBACK_BUFFER_SIZE_MS

        val BUFFER_SIZE_FACTOR = 2


        fun audioSourceToString(source: Int): String {
            val VOICE_PERFORMANCE = 10
            return when (source) {
                AudioSource.DEFAULT -> "DEFAULT"
                AudioSource.MIC -> "MIC"
                AudioSource.VOICE_UPLINK -> "VOICE_UPLINK"
                AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK"
                AudioSource.VOICE_CALL -> "VOICE_CALL"
                AudioSource.CAMCORDER -> "CAMCORDER"
                AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
                AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                AudioSource.UNPROCESSED -> "UNPROCESSED"
                VOICE_PERFORMANCE -> "VOICE_PERFORMANCE"
                else -> "INVALID"
            }
        }

        fun channelMaskToString(mask: Int): String {
            return when (mask) {
                AudioFormat.CHANNEL_IN_STEREO -> "IN_STEREO"
                AudioFormat.CHANNEL_IN_MONO -> "IN_MONO"
                else -> "INVALID"
            }
        }

        fun audioEncodingToString(enc: Int): String {
            return when (enc) {
                AudioFormat.ENCODING_INVALID -> "INVALID"
                AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT"
                AudioFormat.ENCODING_PCM_8BIT -> "PCM_8BIT"
                AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
                AudioFormat.ENCODING_AC3 -> "AC3"
                AudioFormat.ENCODING_E_AC3 -> "AC3"
                AudioFormat.ENCODING_DTS -> "DTS"
                AudioFormat.ENCODING_DTS_HD -> "DTS_HD"
                AudioFormat.ENCODING_MP3 -> "MP3"
                else -> "Invalid encoding: $enc"
            }
        }

        fun deviceTypeToString(type: Int): String {
            when (type) {
                AudioDeviceInfo.TYPE_UNKNOWN -> return "TYPE_UNKNOWN"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> return "TYPE_BUILTIN_EARPIECE"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> return "TYPE_BUILTIN_SPEAKER"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> return "TYPE_WIRED_HEADSET"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return "TYPE_WIRED_HEADPHONES"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> return "TYPE_LINE_ANALOG"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> return "TYPE_LINE_DIGITAL"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return "TYPE_BLUETOOTH_SCO"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> return "TYPE_BLUETOOTH_A2DP"
                AudioDeviceInfo.TYPE_HDMI -> return "TYPE_HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC -> return "TYPE_HDMI_ARC"
                AudioDeviceInfo.TYPE_USB_DEVICE -> return "TYPE_USB_DEVICE"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> return "TYPE_USB_ACCESSORY"
                AudioDeviceInfo.TYPE_DOCK -> return "TYPE_DOCK"
                AudioDeviceInfo.TYPE_FM -> return "TYPE_FM"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> return "TYPE_BUILTIN_MIC"
                AudioDeviceInfo.TYPE_FM_TUNER -> return "TYPE_FM_TUNER"
                AudioDeviceInfo.TYPE_TV_TUNER -> return "TYPE_TV_TUNER"
                AudioDeviceInfo.TYPE_TELEPHONY -> return "TYPE_TELEPHONY"
                AudioDeviceInfo.TYPE_AUX_LINE -> return "TYPE_AUX_LINE"
                AudioDeviceInfo.TYPE_IP -> return "TYPE_IP"
                AudioDeviceInfo.TYPE_BUS -> return "TYPE_BUS"
                AudioDeviceInfo.TYPE_USB_HEADSET -> return "TYPE_USB_HEADSET"
                else -> return "TYPE_UNKNOWN"
            }
        }

        fun logDeviceInfo(tag: String) {
            Logging.d(
                tag,
                ("Android SDK: " + Build.VERSION.SDK_INT + ", "
                        + "Release: " + Build.VERSION.RELEASE + ", "
                        + "Brand: " + Build.BRAND + ", "
                        + "Device: " + Build.DEVICE + ", "
                        + "Id: " + Build.ID + ", "
                        + "Hardware: " + Build.HARDWARE + ", "
                        + "Manufacturer: " + Build.MANUFACTURER + ", "
                        + "Model: " + Build.MODEL + ", "
                        + "Product: " + Build.PRODUCT)
            )
        }

        fun modeToString(mode: Int): String {
            return when (mode) {
                MODE_IN_CALL -> "MODE_IN_CALL"
                MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
                MODE_NORMAL -> "MODE_NORMAL"
                MODE_RINGTONE -> "MODE_RINGTONE"
                else -> "MODE_INVALID"
            }
        }

        fun hasMicrophone(context: Context): Boolean {
            return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        }

        fun streamTypeToString(stream: Int): String {
            when (stream) {
                AudioManager.STREAM_VOICE_CALL -> return "STREAM_VOICE_CALL"
                AudioManager.STREAM_MUSIC -> return "STREAM_MUSIC"
                AudioManager.STREAM_RING -> return "STREAM_RING"
                AudioManager.STREAM_ALARM -> return "STREAM_ALARM"
                AudioManager.STREAM_NOTIFICATION -> return "STREAM_NOTIFICATION"
                AudioManager.STREAM_SYSTEM -> return "STREAM_SYSTEM"
                else -> return "STREAM_INVALID"
            }
        }

        fun logIsStreamMute(
            tag: String?, audioManager: AudioManager, stream: Int, info: java.lang.StringBuilder
        ) {
            info.append(", muted=").append(audioManager.isStreamMute(stream))
        }

        fun logAudioStateVolume(tag: String, audioManager: AudioManager) {
            val streams = intArrayOf(
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_RING,
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM
            )
            Logging.d(tag, "Audio State: ")
            // Some devices may not have volume controls and might use a fixed volume.
            val fixedVolume = audioManager.isVolumeFixed
            Logging.d(tag, "  fixed volume=$fixedVolume")
            if (!fixedVolume) {
                for (stream in streams) {
                    val info = java.lang.StringBuilder()
                    info.append("  " + streamTypeToString(stream) + ": ")
                    info.append("volume=").append(audioManager.getStreamVolume(stream))
                    info.append(", max=").append(audioManager.getStreamMaxVolume(stream))
                    logIsStreamMute(tag, audioManager, stream, info)
                    Logging.d(tag, info.toString())
                }
            }
        }

        fun logAudioStateBasic(tag: String, context: Context, audioManager: AudioManager) {
            Logging.d(
                tag,
                ("Audio State: "
                        + "audio mode: " + modeToString(audioManager.mode) + ", "
                        + "has mic: " + hasMicrophone(context) + ", "
                        + "mic muted: " + audioManager.isMicrophoneMute + ", "
                        + "music active: " + audioManager.isMusicActive + ", "
                        + "speakerphone: " + audioManager.isSpeakerphoneOn + ", "
                        + "BT SCO: " + audioManager.isBluetoothScoOn)
            )
        }

        fun logAudioDeviceInfo(tag: String, audioManager: AudioManager) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            if (devices.size == 0) {
                return;
            }
            Logging.d(tag, "Audio Devices: ");
            for (device: AudioDeviceInfo in devices) {
                val info = StringBuilder()
                info.append("  ").append(deviceTypeToString(device.type))
                info.append(if (device.isSource) "(in): " else "(out): ")
                // An empty array indicates that the device supports arbitrary channel counts.
                if (device.channelCounts.isNotEmpty()) {
                    info.append("channels=").append(device.channelCounts.contentToString());
                    info.append(", ");
                }
                if (device.encodings.isNotEmpty()) {
                    // Examples: ENCODING_PCM_16BIT = 2, ENCODING_PCM_FLOAT = 4.
                    info.append("encodings=").append(device.encodings.contentToString());
                    info.append(", ");
                }
                if (device.sampleRates.isNotEmpty()) {
                    info.append("sample rates=").append(device.sampleRates.contentToString());
                    info.append(", ");
                }
                info.append("id=").append(device.id);
                Logging.d(tag, info.toString());
            }
        }

        fun logAudioState(tag: String, context: Context, audioManager: AudioManager) {
            logDeviceInfo(tag)
            logAudioStateBasic(tag, context, audioManager)
            logAudioStateVolume(tag, audioManager)
            logAudioDeviceInfo(tag, audioManager)
        }
    }

    fun reportWebRtcAudioRecordInitError(errorMessage: String?) {
        Logging.e(TAG, "Init recording error: $errorMessage")
        logAudioState(TAG, context!!, audioManager!!)
        logRecordingConfigurations(audioRecord)
        if (errorCallback != null) {
            errorCallback!!.onWebRtcAudioRecordInitError(errorMessage)
        }
    }

    fun logRecordingConfigurations(audioRecord: AudioRecord?): Int {
        if (audioRecord == null) {
            return 0;
        }
        val configs = audioManager!!.activeRecordingConfigurations;
        val numActiveRecordingSessions = configs.size
        Logging.d(TAG, "Number of active recording sessions: $numActiveRecordingSessions");
        if (numActiveRecordingSessions > 0) {
            logActiveRecordingConfigs(audioRecord.audioSessionId, configs);
//            if (verifyAudioConfig) {
//                // Run an extra check to verify that the existing audio source doing the recording (tied
//                // to the AudioRecord instance) is matching what the audio recording configuration lists
//                // as its client parameters. If these do not match, recording might work but under invalid
//                // conditions.
//                audioSourceMatchesRecordingSessionRef.set(
//                    verifyAudioConfig(
//                        audioRecord.getAudioSource(), audioRecord.getAudioSessionId(),
//                        audioRecord.getFormat(), audioRecord.getRoutedDevice(), configs
//                    )
//                );
//            }
        }
        return numActiveRecordingSessions;
    }

    var context: Context? = null
    private var audioManager: AudioManager? = null
    private var audioSource = 0
    var audioFormat = 0

    val nativeAudioRecord: Long = 0

    var byteBuffer: ByteBuffer? = null

    var audioRecord: AudioRecord? = null
    var mediaProjection: MediaProjection? = null

    private val audioThread: AudioRecordThread? = null

    private var preferredDevice: AudioDeviceInfo? = null

    private val executor: ScheduledExecutorService? = null

    private val future: ScheduledFuture<String?>? = null

    @Volatile
    private var microphoneMute = false
    private val audioSourceMatchesRecordingSessionRef: AtomicReference<Boolean> =
        AtomicReference<Boolean>()
    private var isAudioConfigVerified = false
    var emptyBytes: ByteArray? = byteArrayOf()

    private var errorCallback: AudioRecordErrorCallback? = null

    private var stateCallback: AudioRecordStateCallback? = null

    private var audioSamplesReadyCallback: SamplesReadyCallback? = null
    private var isAcousticEchoCancelerSupported = false
    private var isNoiseSuppressorSupported = false

    inner class AudioRecordThread(name: String) : Thread(name) {
        @Volatile
        private var keepAlive = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val lastTime = System.nanoTime()
            if (byteBuffer == null)
                return
            while (keepAlive) {
                val bytesRead = audioRecord?.read(byteBuffer!!, byteBuffer!!.capacity())
                if (bytesRead == byteBuffer!!.capacity()) {
                    if (keepAlive) {
                        nativeDataIsRecorded(nativeAudioRecord, bytesRead)
                    }
                    if (audioSamplesReadyCallback != null) {
                        val data: ByteArray = byteBuffer!!.array().copyOfRange(
                            byteBuffer!!.arrayOffset(),
                            byteBuffer!!.capacity() + byteBuffer!!.arrayOffset()
                        )
                        audioSamplesReadyCallback!!.onWebRtcAudioRecordSamplesReady(
                            JavaAudioDeviceModule.AudioSamples(
                                audioRecord!!.audioFormat,
                                audioRecord!!.channelCount,
                                audioRecord!!.sampleRate,
                                data
                            )
                        )
                    }
                } else {
                    val error = "AudioRecord.read failed: $bytesRead"
                    println(error)
                    if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        keepAlive = false;
                        println("WebRTCAudioRecordErrror")
                    }
                }
            }
            try {
                audioRecord?.stop()
            } catch (e: IllegalStateException) {
                println("AudioRecord.stop failed: ${e.message}")
            }
        }
    }


    constructor(
        context: Context?,
        audioManager1: ScheduledExecutorService,
        audioManager: AudioManager?,
        audioSource: Int,
        audioFormat: Int,
        errorCallback: AudioRecordErrorCallback?,
        stateCallback: AudioRecordStateCallback?,
        audioSamplesReadyCallback: SamplesReadyCallback?,
        isAcousticEchoCancelerSupported: Boolean,
        isNoiseSuppressorSupported: Boolean
    ) {
        require(!(isAcousticEchoCancelerSupported && !WebRtcAudioEffects.isAcousticEchoCancelerSupported())) { "HW AEC not supported" }
        require(!(isNoiseSuppressorSupported && !WebRtcAudioEffects.isNoiseSuppressorSupported())) { "HW NS not supported" }
        this.context = context
        this.audioManager = audioManager
        this.audioSource = audioSource
        this.audioFormat = audioFormat
        this.errorCallback = errorCallback
        this.stateCallback = stateCallback
        this.audioSamplesReadyCallback = audioSamplesReadyCallback
        this.isAcousticEchoCancelerSupported = isAcousticEchoCancelerSupported
        this.isNoiseSuppressorSupported = isNoiseSuppressorSupported
        println("ctor" + WebRtcAudioUtils.getThreadInfo())
    }


    @CalledByNative
    private fun initRecording(sampleRate: Int, channels: Int): Int {
        println("initRecording(samleRate=$sampleRate, channels=$channels)")
        if (audioRecord != null)
            return -1
        val bytesPerFrame = channels * getBytesPerSample(audioFormat)
        val framesPerBuffer = sampleRate / BUFFERS_PER_SECOND
        byteBuffer = ByteBuffer.allocateDirect(bytesPerFrame * framesPerBuffer)
        if (!(byteBuffer!!.hasArray())) {
            return -1
        }
        emptyBytes = ByteArray(byteBuffer!!.capacity())
        nativeCacheDirectBufferAddress(nativeAudioRecord, byteBuffer)

        val channelConfig = channelCountToConfiguration(channels)
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return -1
        }
        println("AudioRecord.getMinBufferSize: $minBufferSize")

        val bufferSizeInBytes =
            (BUFFER_SIZE_FACTOR * minBufferSize).coerceAtLeast(byteBuffer!!.capacity())
        println("bufferSizeInBytes: $bufferSizeInBytes")
        audioRecord = try {
            if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createAudioRecordOnQOrHigher(
                    audioSource,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSizeInBytes,
                    mediaProjection!!
                )
            } else {
                createAudioRecordOnMOrHigher(
                    audioSource,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSizeInBytes
                )
            }
        } catch (e: IllegalArgumentException) {
            return -1
        }
        if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            return -1
        }

        logMainParameters()
        logMainParametersExtended()
        val numActiveRecordingSessions = logRecordingConfigurations(false)
        if (numActiveRecordingSessions != 0)
            println("Potential microphone conflict. Active sessions: $numActiveRecordingSessions")
        return framesPerBuffer
    }


    private fun logMainParameters() {
        println("AudioRecord:\nsession ID: ${audioRecord!!.audioSessionId}\nchannels: ${audioRecord!!.channelCount}\nsample rate: ${audioRecord!!.sampleRate}")
    }

    private fun logMainParametersExtended() {
        println("AudioRecord:\nbuffer size in frames: ${audioRecord!!.bufferSizeInFrames}")
    }

    private fun logRecordingConfigurations(verifyAudioConfig: Boolean): Int {
        if (audioRecord == null)
            return 0
        val configs = audioManager!!.activeRecordingConfigurations
        val numActiveRecordingSessions = configs.size
        println("Number of active recording sessions: $numActiveRecordingSessions")
        if (numActiveRecordingSessions > 0) {
            logActiveRecordingConfigs(audioRecord!!.audioSessionId, configs)
            if (verifyAudioConfig) {
                audioSourceMatchesRecordingSessionRef.set(
                    verifyAudioConfig(
                        audioRecord!!.audioSource, audioRecord!!.audioSessionId,
                        audioRecord!!.format, audioRecord!!.routedDevice, configs
                    )
                )
                isAudioConfigVerified = true
            }
        }
        return numActiveRecordingSessions
    }

    private fun verifyAudioConfig(
        source: Int, session: Int, format: AudioFormat, device: AudioDeviceInfo,
        configs: List<AudioRecordingConfiguration>
    ): Boolean {
        val it = configs.iterator()
        while (it.hasNext()) {
            val config = it.next()
            val configDevice = config.audioDevice
            if (configDevice == null) {
                continue;
            }
            if ((config.clientAudioSource == source)
                && (config.clientAudioSessionId == session)
                // Check the client format (should match the format of the AudioRecord instance).
                && (config.clientFormat.encoding == format.encoding)
                && (config.clientFormat.sampleRate == format.sampleRate)
                && (config.clientFormat.channelMask == format.channelMask)
                && (config.clientFormat.channelIndexMask == format.channelIndexMask)
                // Ensure that the device format is properly configured.
                && (config.format.encoding != AudioFormat.ENCODING_INVALID)
                && (config.format.sampleRate > 0)
                //  For the channel mask, either the position or index-based value must be valid.
                && ((config.format.channelMask != AudioFormat.CHANNEL_INVALID)
                        || (config.format
                    .channelIndexMask != AudioFormat.CHANNEL_INVALID))
                && checkDeviceMatch(configDevice, device)
            ) {
                println("verifyAudioConfig: PASS");
                return true;
            }
        }
        println("verifyAudioConfig: FAILED");
        return false;
    }


    private fun checkDeviceMatch(devA: AudioDeviceInfo, devB: AudioDeviceInfo): Boolean {
        return ((devA.id == devB.id && (devA.type == devB.type)))
    }

    private fun logActiveRecordingConfigs(
        session: Int,
        configs: List<AudioRecordingConfiguration>
    ): Boolean {
        val it = configs.iterator();
        println("AudioRecordingConfigurations: ");
        while (it.hasNext()) {
            val config = it.next()
            val conf = StringBuilder()
            // The audio source selected by the client.
            audioSource = config.clientAudioSource;
            conf.append("  client audio source=")
                .append(audioSourceToString(audioSource))
                .append(", client session id=")
                .append(config.clientAudioSessionId)
                // Compare with our own id (based on AudioRecord#getAudioSessionId()).
                .append(" (")
                .append(session)
                .append(")")
                .append("\n");
            // Audio format at which audio is recorded on this Android device. Note that it may differ
            // from the client application recording format (see getClientFormat()).
            var format = config.format;
            conf.append("  Device AudioFormat: ")
                .append("channel count=")
                .append(format.channelCount)
                .append(", channel index mask=")
                .append(format.channelIndexMask)
                // Only AudioFormat#CHANNEL_IN_MONO is guaranteed to work on all devices.
                .append(", channel mask=")
                .append(channelMaskToString(format.channelMask))
                .append(", encoding=")
                .append(audioEncodingToString(format.encoding))
                .append(", sample rate=")
                .append(format.sampleRate)
                .append("\n");
            // Audio format at which the client application is recording audio.
            format = config.clientFormat;
            conf.append("  Client AudioFormat: ")
                .append("channel count=")
                .append(format.channelCount)
                .append(", channel index mask=")
                .append(format.channelIndexMask)
                // Only AudioFormat#CHANNEL_IN_MONO is guaranteed to work on all devices.
                .append(", channel mask=")
                .append(channelMaskToString(format.channelMask))
                .append(", encoding=")
                .append(audioEncodingToString(format.encoding))
                .append(", sample rate=")
                .append(format.sampleRate)
                .append("\n");
            // Audio input device used for this recording session.
            val device = config.audioDevice;
            if (device != null) {
                conf.append("  AudioDevice: ")
                    .append("type=")
                    .append(deviceTypeToString(device.type))
                    .append(", id=")
                    .append(device.id);
            }
            println(conf.toString());
        }
        return true;
    }


    @SuppressLint("MissingPermission")
    private fun createAudioRecordOnMOrHigher(
        audioSource: Int,
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSizeInBytes: Int
    ): AudioRecord {
        println("createAudioRecordOnMOrHigher")
        return AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .build()
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun createAudioRecordOnQOrHigher(
        audioSource: Int,
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSizeInBytes: Int,
        mediaProjection: MediaProjection
    ): AudioRecord {
        println("createAudioRecordOnQOrHigher")
        val audioConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        return AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setAudioPlaybackCaptureConfig(audioConfig)
            .build()
    }

    fun getBytesPerSample(audioFormat: Int): Int {
        return when (audioFormat) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_IEC61937, AudioFormat.ENCODING_DEFAULT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_INVALID -> throw IllegalArgumentException("Bad audio format ${0}")
            else -> throw IllegalArgumentException("Bad audio format $audioFormat")
        }
    }

    external fun nativeDataIsRecorded(nativeAudioRecordJni: Long, bytes: Int)
    external fun nativeDataIsRecorded(
        nativeAudioRecordJni: Long, bytes: Int, captureTimestampNs: Int
    )

    external fun nativeCacheDirectBufferAddress(
        nativeAudioRecordJni: Long, byteBuffer: ByteBuffer?
    )

    fun channelCountToConfiguration(channels: Int): Int {
        return (if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO)
    }

    fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) {
        Logging.d(
            TAG,
            "setPreferredDevice " + (preferredDevice?.id)
        )
        this.preferredDevice = preferredDevice
        if (audioRecord != null) {
            if (!audioRecord!!.setPreferredDevice(preferredDevice)) {
                Logging.e(TAG, "setPreferredDevice failed")
            }
        }
    }


}





