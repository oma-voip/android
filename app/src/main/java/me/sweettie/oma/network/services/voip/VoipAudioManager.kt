package me.sweettie.oma.network.services.voip

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import eu.buney.kopus.OpusApplication
import eu.buney.kopus.OpusDecoder
import eu.buney.kopus.OpusEncoder
import eu.buney.kopus.encode
import eu.buney.kopus.setDTX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.sweettie.oma.utils.Utils.getDeviceByType
import java.util.UUID

class VoipAudioManager(private val context: Context) {
    private val opusLock = Any()
    private val sampleRate = 48000
    private val channels = 1
    val frameSize = 960

    var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    var opusEncoder: OpusEncoder? = null

    @Volatile
    var running = false

    val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startProcessingLoops() {
        audioScope.launch {
            playoutLoop()
        }

    }

    private val decoders = java.util.concurrent.ConcurrentHashMap<UUID, OpusDecoder>()

    fun getOrCreateDecoder(uuid: UUID): OpusDecoder? {
        decoders[uuid]?.let { return it }
        synchronized(decoders) {
            decoders[uuid]?.let { return it }
            return try {
                val d = OpusDecoder(sampleRate, channels)
                decoders[uuid] = d
                d
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun destroyAllDecoders() {
        for ((_, d) in decoders) {
            try {
                d.close()
            } catch (_: Exception) {
            }
        }
        decoders.clear()
    }
    private fun playoutLoop() {
        while (running && audioScope.isActive) {
            val pkt = VoipForegroundService.AudioFragmentManager.getDatas()
            audioTrack?.write(pkt, 0, pkt.size)
        }
    }


    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (running) return
        running = true


        setAudioMode(true)
        initOpus()
        startAudioDevices()

        startProcessingLoops()


    }

    fun stop() {
        running = false
        audioScope.cancel()
        stopAudioDevices()
        destroyOpus()
        setAudioMode(false)


    }
    private fun initOpus() {
        synchronized(opusLock) {
            opusEncoder = try {
                OpusEncoder(
                    sampleRate = sampleRate,
                    channels = channels,
                    application = OpusApplication.Voip,
                ).apply {
                    setDTX(1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun destroyOpus() {
        synchronized(opusLock) {
            try {
                opusEncoder?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            opusEncoder = null
        }
        destroyAllDecoders()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioDevices() {
        startAudioRecord()
        startAudioTrack()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(frameSize * 6)

        audioRecord = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)

                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .build()
                .apply { startRecording() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    }

    private fun startAudioTrack() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
            .coerceAtLeast(frameSize * 6)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().apply { play() }
    }

    private fun stopAudioDevices() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun changeOutputType(type: Int) {
        val dev = getDeviceByType(context, type)
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (dev?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            // Попробуй включить Bluetooth SCO для разговора, если возможно
            am.isBluetoothScoOn = true
            am.startBluetoothSco()
        }

        am.mode = AudioManager.MODE_IN_COMMUNICATION
        dev?.let {
            am.setCommunicationDevice(it)
        }
    }

    private fun setAudioMode(enable: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = if (enable) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun encodeSync(buffer: ShortArray): ByteArray? {
        // простая валидация размера
        if (buffer.size != frameSize) {
            Log.w("VoipAudioManager", "Invalid buffer size ${buffer.size}, expected $frameSize")
            return null
        }
        synchronized(opusLock) {
            return try {
                opusEncoder?.encode(buffer)
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    }



}