package me.sweettie.oma.network.services.voip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.sweettie.oma.network.udp.Key
import me.sweettie.oma.network.udp.UdpClient
import me.sweettie.oma.network.udp.UdpHeaders
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.Utils.getDeviceByType
import java.util.UUID

class VoipAudioManager(
    private val context: Context,
    private val udpClient: UdpClient,
    private val key: Key
) {

    private val sampleRate = 48000
    private val channels = 1
    val frameSize = 960

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var opusEncoder: OpusEncoder? = null

    @Volatile
    private var running = false

    // Корутины
    val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private fun startProcessingLoops() {
        audioScope.launch {
            playoutLoop()
        }

    }

    private val decoders = java.util.concurrent.ConcurrentHashMap<UUID, OpusDecoder>()

    var add = false
    fun getOrCreateDecoder(uuid: UUID): OpusDecoder? {
        decoders[uuid]?.let { return it }
        synchronized(decoders) {
            decoders[uuid]?.let { return it } // re-check
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


        audioScope.launch {
            startCaptureLoop()
        }
    }

    fun stop() {
        running = false
        audioScope.cancel()
        stopAudioDevices()
        destroyOpus()
        setAudioMode(false)


    }


    private fun initOpus() {
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

    private fun destroyOpus() {
        try {
            opusEncoder?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        opusEncoder = null

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

    @SuppressLint("NewApi")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun changeOutputType(type: Int) {
        val dev = getDeviceByType(context, type)
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        dev?.let { am.setCommunicationDevice(it) }
    }

    private fun setAudioMode(enable: Boolean) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = if (enable) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun startCaptureLoop() {
        val buffer = ShortArray(frameSize)
        while (running && audioScope.isActive) {
            if (SessionManager.muted) {
                delay(10)
                continue
            }
            var got = 0
            while (got < frameSize) {
                val r = audioRecord?.read(buffer, got, frameSize - got) ?: 0
                if (r < 0) break
                got += r
            }
            if (got > 0) {
                processAndSendAudio(buffer)
            } else {
                delay(5)
            }
        }
    }


    private suspend fun processAndSendAudio(buffer: ShortArray) {
        val encoded = try {
            opusEncoder?.encode(buffer) ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        if (encoded.isEmpty()) return

        try {
            val encrypted = key.encrypt(UdpHeaders.DATA.s + encoded)
            udpClient.send(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


}