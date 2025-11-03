package me.sweettie.oma.network.whip

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import org.webrtc.JniCommon
import org.webrtc.Logging
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.voiceengine.WebRtcAudioEffects
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.util.concurrent.ScheduledExecutorService


class KotlinAudioDeviceModule : AudioDeviceModule {

    val TAG: String = "JavaAudioDeviceModule"

    companion object {

        fun isBuiltInAcousticEchoCancelerSupported(): Boolean {
            return WebRtcAudioEffects.isAcousticEchoCancelerSupported()
        }

        fun isBuiltInNoiseSuppressorSupported(): Boolean {
            return WebRtcAudioEffects.isNoiseSuppressorSupported()
        }

        val TAG: String = "JavaAudioDeviceModule"

        @JvmStatic
        private external fun nativeCreateAudioDeviceModule(
            context: Context?,
            audioManager: AudioManager?,
            audioInput: WebRtcAudioRecord?,
            audioOutput: WebRtcAudioTrack?,
            inputSampleRate: Int,
            outputSampleRate: Int,
            useStereoInput: Boolean,
            useStereoOutput: Boolean
        ): Long

        fun builder(context: Context): Builder {
            return Builder(context)
        }

        fun getSampleRate(audioManager: AudioManager): Int {
            if (WebRtcAudioUtils.runningOnEmulator()) {
                Logging.d(TAG, "Running emulator, overriding sample rate to 8 kHz.")
                return 8000
            }
            val sampleRateHz: Int = getSampleRateForApiLevel(audioManager)
            Logging.d(TAG, "Sample rate is set to " + sampleRateHz + " Hz")
            return sampleRateHz
        }

        private fun getSampleRateForApiLevel(audioManager: AudioManager): Int {
            val sampleRateString =
                audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            return sampleRateString?.toInt() ?: 16000
        }
    }


    class Builder {
        private var context: Context
        private var scheduler: ScheduledExecutorService? = null
        private var audioManager: AudioManager
        private var inputSampleRate = 0
        private var outputSampleRate = 0
        private var audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        private var audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
        private var audioTrackErrorCallback: JavaAudioDeviceModule.AudioTrackErrorCallback? = null
        private var audioRecordErrorCallback: AudioRecordErrorCallback? = null
        private var samplesReadyCallback: SamplesReadyCallback? = null
        private var audioTrackStateCallback: JavaAudioDeviceModule.AudioTrackStateCallback? = null
        private var audioRecordStateCallback: AudioRecordStateCallback? = null
        private var useHardwareAcousticEchoCanceler: Boolean =
            isBuiltInAcousticEchoCancelerSupported()
        private var useHardwareNoiseSuppressor: Boolean = isBuiltInNoiseSuppressorSupported()
        private var useStereoInput = false
        private var useStereoOutput = false
        private var audioAttributes: AudioAttributes? = null
        private var useLowLatency = false
        private var enableVolumeLogger = false
        private var isCustomAudioFeed = false

        constructor(context: Context) {
            this.context = context
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            inputSampleRate = getSampleRate(audioManager)
            outputSampleRate = getSampleRate(audioManager)
            useLowLatency = false
            enableVolumeLogger = true
        }


        fun setScheduler(scheduler: ScheduledExecutorService?): Builder {
            this.scheduler = scheduler
            return this
        }

        fun setSampleRate(sampleRate: Int): Builder {
            Logging.d(TAG, "Input/Output sample rate overridden to: $sampleRate")
            this.inputSampleRate = sampleRate
            this.outputSampleRate = sampleRate
            return this
        }

        fun setInputSampleRate(inputSampleRate: Int): Builder {
            Logging.d(TAG, "Input sample rate overridden to: $inputSampleRate")
            this.inputSampleRate = inputSampleRate
            return this
        }

        fun setOutputSampleRate(outputSampleRate: Int): Builder {
            Logging.d(TAG, "Output sample rate overridden to: $outputSampleRate")
            this.outputSampleRate = outputSampleRate
            return this
        }

        fun setAudioSource(audioSource: Int): Builder {
            this.audioSource = audioSource
            return this
        }

        fun setAudioFormat(audioFormat: Int): Builder {
            this.audioFormat = audioFormat
            return this
        }

        fun setAudioTrackErrorCallback(audioTrackErrorCallback: JavaAudioDeviceModule.AudioTrackErrorCallback?): Builder {
            this.audioTrackErrorCallback = audioTrackErrorCallback
            return this
        }

        fun setAudioRecordErrorCallback(audioRecordErrorCallback: AudioRecordErrorCallback?): Builder {
            this.audioRecordErrorCallback = audioRecordErrorCallback
            return this
        }

        fun setSamplesReadyCallback(samplesReadyCallback: SamplesReadyCallback?): Builder {
            this.samplesReadyCallback = samplesReadyCallback
            return this
        }

        fun setAudioTrackStateCallback(audioTrackStateCallback: JavaAudioDeviceModule.AudioTrackStateCallback?): Builder {
            this.audioTrackStateCallback = audioTrackStateCallback
            return this
        }

        fun setAudioRecordStateCallback(audioRecordStateCallback: AudioRecordStateCallback?): Builder {
            this.audioRecordStateCallback = audioRecordStateCallback
            return this
        }

        fun setUseHardwareNoiseSuppressor(useHardwareNoiseSuppressor: Boolean): Builder {
            var useHardwareNoiseSuppressor = useHardwareNoiseSuppressor
            if (useHardwareNoiseSuppressor && !isBuiltInNoiseSuppressorSupported()) {
                Logging.e(TAG, "HW NS not supported")
                useHardwareNoiseSuppressor = false
            }
            this.useHardwareNoiseSuppressor = useHardwareNoiseSuppressor
            return this
        }

        fun setUseHardwareAcousticEchoCanceler(useHardwareAcousticEchoCanceler: Boolean): Builder {
            var useHardwareAcousticEchoCanceler = useHardwareAcousticEchoCanceler
            if (useHardwareAcousticEchoCanceler && !isBuiltInAcousticEchoCancelerSupported()) {
                Logging.e(TAG, "HW AEC not supported")
                useHardwareAcousticEchoCanceler = false
            }
            this.useHardwareAcousticEchoCanceler = useHardwareAcousticEchoCanceler
            return this
        }

        fun setUseStereoInput(useStereoInput: Boolean): Builder {
            this.useStereoInput = useStereoInput
            return this
        }


        fun setUseStereoOutput(useStereoOutput: Boolean): Builder {
            this.useStereoOutput = useStereoOutput
            return this
        }

        fun setUseLowLatency(useLowLatency: Boolean): Builder {
            this.useLowLatency = useLowLatency
            return this
        }

        fun setAudioAttributes(audioAttributes: AudioAttributes?): Builder {
            this.audioAttributes = audioAttributes
            return this
        }

        fun setEnableVolumeLogger(enableVolumeLogger: Boolean): Builder {
            this.enableVolumeLogger = enableVolumeLogger
            return this
        }

        fun createAudioDeviceModule(): AudioDeviceModule {
            Logging.d(TAG, "createAudioDeviceModule")
            if (useHardwareNoiseSuppressor) {
                Logging.d(TAG, "HW NS will be used.")
            } else {
                if (isBuiltInNoiseSuppressorSupported()) {
                    Logging.d(TAG, "Overriding default behavior; now using WebRTC NS!")
                }
                Logging.d(TAG, "HW NS will not be used.")
            }
            if (useHardwareAcousticEchoCanceler) {
                Logging.d(TAG, "HW AEC will be used.")
            } else {
                if (isBuiltInAcousticEchoCancelerSupported()) {
                    Logging.d(TAG, "Overriding default behavior; now using WebRTC AEC!")
                }
                Logging.d(TAG, "HW AEC will not be used.")
            }
            if (useLowLatency) {
                Logging.d(TAG, "Low latency mode will be used.")
            }
            var executor = this.scheduler
            if (executor == null) {
                executor = WebRtcAudioRecord.newDefaultScheduler()
            }
            val audioInput = WebRtcAudioRecord(
                context,
                executor,
                audioManager,
                audioSource,
                audioFormat,
                audioRecordErrorCallback as JavaAudioDeviceModule.AudioRecordErrorCallback?,
                audioRecordStateCallback as JavaAudioDeviceModule.AudioRecordStateCallback?,
                samplesReadyCallback as JavaAudioDeviceModule.SamplesReadyCallback?,
                useHardwareAcousticEchoCanceler,
                useHardwareNoiseSuppressor
            )
            val audioOutput =
                WebRtcAudioTrack(
                    context, audioManager, audioAttributes, audioTrackErrorCallback,
                    audioTrackStateCallback, useLowLatency, enableVolumeLogger
                )
            return me.sweettie.oma.network.whip.KotlinAudioDeviceModule(
                context, audioManager, audioInput, audioOutput,
                inputSampleRate, outputSampleRate, useStereoInput, useStereoOutput
            )
        }

        fun setCustomAudioFeed(isCustomAudioFeed: Boolean): Builder {
            this.isCustomAudioFeed = isCustomAudioFeed
            return this
        }
    }

    enum class AudioRecordStartErrorCode {
        AUDIO_RECORD_START_EXCEPTION,
        AUDIO_RECORD_START_STATE_MISMATCH,
    }

    interface AudioRecordErrorCallback {
        fun onWebRtcAudioRecordInitError(errorMessage: String?)
        fun onWebRtcAudioRecordStartError(
            errorCode: AudioRecordStartErrorCode?,
            errorMessage: String?
        )

        fun onWebRtcAudioRecordError(errorMessage: String?)
    }


    interface SamplesReadyCallback {
        fun onWebRtcAudioRecordSamplesReady(samples: JavaAudioDeviceModule.AudioSamples?)
    }

    interface AudioRecordStateCallback {
        fun onWebRtcAudioRecordStart()
        fun onWebRtcAudioRecordStop()
    }


    private var context: Context
    private val audioManager: AudioManager
    private val audioInput: WebRtcAudioRecord
    private var audioOutput: WebRtcAudioTrack
    private var inputSampleRate = 0
    private var outputSampleRate = 0
    private var useStereoInput = false
    private var useStereoOutput = false

    private val nativeLock = Any()
    private var nativeAudioDeviceModule: Long = 0

    private constructor(
        context: Context,
        audioManager: AudioManager,
        audioInput: WebRtcAudioRecord,
        audioOutput: me.sweettie.oma.network.whip.WebRtcAudioTrack,
        inputSampleRate: Int,
        outputSampleRate: Int,
        useStereoInput: Boolean,
        userStereoOutput: Boolean
    ) {
        this.context = context
        this.audioManager = audioManager
        this.audioInput = audioInput
        this.audioOutput = audioOutput
        this.inputSampleRate = inputSampleRate
        this.outputSampleRate = outputSampleRate
        this.useStereoInput = useStereoInput
        this.useStereoOutput = userStereoOutput
    }


    override fun getNativeAudioDeviceModulePointer(): Long {
        synchronized(nativeLock) {
            if (nativeAudioDeviceModule == 0L) {
                nativeAudioDeviceModule = nativeCreateAudioDeviceModule(
                    context, audioManager, audioInput,
                    audioOutput, inputSampleRate, outputSampleRate, useStereoInput, useStereoOutput
                );
            }
            return nativeAudioDeviceModule;
        }
    }

    override fun release() {
        synchronized(nativeLock) {
            if (nativeAudioDeviceModule != 0L) {
                JniCommon.nativeReleaseRef(nativeAudioDeviceModule);
                nativeAudioDeviceModule = 0;
            }
        }
    }

    override fun setSpeakerMute(p0: Boolean) {
        Logging.d(TAG, "setSpeakerMute: $p0");
    }

    override fun setMicrophoneMute(p0: Boolean) {
        Logging.d(TAG, "setMicrophoneMute: $p0");
    }

    override fun setMediaProjection(mediaProjection: MediaProjection?) {
        audioInput.mediaProjection = mediaProjection

    }

    fun setPreferredInputDevice(preferredInputDevice: AudioDeviceInfo?) {
        Logging.d(TAG, "setPreferredInputDevice: $preferredInputDevice")
        audioInput.setPreferredDevice(preferredInputDevice)
    }


}