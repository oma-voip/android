package me.sweettie.oma.network.services.voip

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import eu.buney.kopus.OpusDecoder
import eu.buney.kopus.decode
import eu.buney.kopus.encode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.network.udp.Key
import me.sweettie.oma.network.udp.Packet
import me.sweettie.oma.network.udp.UdpClient
import me.sweettie.oma.network.udp.UdpHeaders
import me.sweettie.oma.network.whip.ScreenCaptureService
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.utils.HexToByteArray
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.startsWith
import me.sweettie.oma.utils.toByteArray
import java.nio.ByteBuffer
import java.util.UUID

class VoipForegroundService : Service() {

    object AudioFragmentManager {

        val audios = HashMap<UUID, ArrayList<ShortArray>>()
        var isSelfSpeaking = BooleanArray(1) { false }
        val decoders = HashMap<UUID, OpusDecoder>()
        fun addAudio(uuid: UUID, data: ShortArray, decoder: OpusDecoder): Boolean {
            val first = audios.isEmpty()
            val r: ArrayList<ShortArray> = audios.getOrDefault(uuid, ArrayList())
            r.add(data)
            audios[uuid] = r

            decoders.put(uuid, decoder)

            return first
        }

        fun clear() {
            audios.clear()
            decoders.clear()
        }

        private val FRAME_SIZE = 960
        private val empt = ArrayList<ByteArray>()
        fun getDatas(): ShortArray {
            val buffers = ArrayList<ShortArray>()
            audios.forEach {
                if (it.value.isNotEmpty())
                    buffers.add(it.value.removeAt(0))
                else {
                    decoders[it.key]?.decode(empt[0], 960)
                }
            }
            if (buffers.isEmpty()) return ShortArray(960).apply { fill(0) }
            val output = ShortArray(FRAME_SIZE)
            output.fill(0)


            buffers.forEach {
                try {
                    for (i in it.indices) {
                        if (i < output.size) {
                            var a: Short = (output[i] + it[i]).toShort()
                            if (a > Short.Companion.MAX_VALUE) a = Short.Companion.MAX_VALUE
                            if (a < Short.Companion.MIN_VALUE) a = Short.Companion.MIN_VALUE
                            output[i] = a
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return output
        }

        fun setSilence(encoded: ByteArray) {
            empt.add(encoded)
        }

    }

    companion object {

        const val ACTION_START = "me.sweettie.oma.ACTION_START"
        const val ACTION_STOP = "me.sweettie.oma.ACTION_STOP"
        const val ACTION_CHANGE_OUTPUT = "me.sweettie.oma.ACTION_CHANGE_OUTPUT"


        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_CONNECTION_ID = "extra_connection_id"
        const val EXTRA_SPACE_ID = "extra_space_id"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_KEY_BYTES = "extra_key_bytes"
        const val EXTRA_OUTPUT_TYPE = "extra_output_type"

        const val CHANNEL_ID = "voip_call_channel"
        const val NOTIF_ID = 1
    }

    private var udpClient: UdpClient? = null
    private var keyObj: Key? = null
    private var connectionUUID: UUID? = null
    private var spaceUUID: UUID? = null
    private var channelUUID: UUID? = null

    private var keepAliveJob: Job? = null
    private var isConnectedToRelay = false
    private var audioManager: VoipAudioManager? = null

    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private lateinit var powerManager: PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        try {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "me.sweettie.oma:proximity"
            ).apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            proximityWakeLock = null
        }
    }

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val distance = event.values[0]
            val max = proximitySensor?.maximumRange ?: 0f
            val isNear = distance < max
            if (isNear && isConnectedToRelay && SessionManager.oldOutputType == 1 && !SessionManager.isWatchStream) {
                // Прикрыл ухо -> гасим экран
                try {
                    if (proximityWakeLock != null && proximityWakeLock?.isHeld != true) {
                        proximityWakeLock?.acquire(10 * 60 * 1000L)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    if (proximityWakeLock?.isHeld == true) {
                        proximityWakeLock?.release()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun startProximityMonitoring() {
        proximitySensor?.let {
            sensorManager.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopProximityMonitoring() {
        try {
            sensorManager.unregisterListener(proximityListener)
        } catch (_: Exception) {
        }
        try {
            if (proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
        } catch (_: Exception) {
        }
    }


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    override fun onBind(intent: Intent?): IBinder? = null

    private var start = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {


                startForeground(NOTIF_ID, buildNotification("Starting VoIP relay..."))
                AudioFragmentManager.clear()

                val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return START_NOT_STICKY
                intent.getStringExtra(EXTRA_CONNECTION_ID)
                    ?.let { connectionUUID = UUID.fromString(it) }
                intent.getStringExtra(EXTRA_SPACE_ID)?.let { spaceUUID = UUID.fromString(it) }
                intent.getStringExtra(EXTRA_CHANNEL_ID)?.let { channelUUID = UUID.fromString(it) }
                intent.getByteArrayExtra(EXTRA_KEY_BYTES)?.let { keyObj = Key(it) }

                startUdp(address)

            }

            ACTION_STOP -> {
                stopKeepAlive()
                try {
                    audioManager?.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    serviceScope.launch {
                        val encrypted = keyObj!!.encrypt(UdpHeaders.DISCONNECT_MESSAGE.s)
                        udpClient?.send(encrypted)
                        udpClient?.stop()
                    }

                    val stopIntent =
                        Intent(this, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_STOP
                        }
                    startService(stopIntent)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopForeground(true)
                stopProximityMonitoring()
                stopSelf()


            }

            ACTION_CHANGE_OUTPUT -> {
                val type = intent.getIntExtra(EXTRA_OUTPUT_TYPE, 0)
                println("Try to change output type to: $type")
                if (type != 0)
                    audioManager?.changeOutputType(type)
            }
        }
        return START_STICKY
    }


    private fun startUdp(addressWithPort: String) {
        val (host, port) = UdpClient.parseAddress(addressWithPort)

        udpClient?.stop()
        udpClient = UdpClient(host, port)
        println("host: $host; port: $port")
        udpClient!!.currentOnReceiveCallback = { data, len ->
            serviceScope.launch {
                try {
                    val decrypted = try {
                        keyObj?.decrypt(data) ?: data
                    } catch (_: Exception) {
                        data
                    }
                    when {
                        decrypted.startsWith(UdpHeaders.CONNECTED_RESPONSE.s) -> {
                            if (!isConnectedToRelay) {
                                isConnectedToRelay = true
                                startKeepAlive()
                                audioManager =
                                    VoipAudioManager(applicationContext)
                                if (ActivityCompat.checkSelfPermission(
                                        applicationContext,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    audioManager!!.start()
                                    serviceScope.launch {
                                        startCaptureLoop()
                                    }
                                }
                                updateNotification("Connected! Talking...")
                                startProximityMonitoring()
                            }
                        }

                        decrypted.startsWith(UdpHeaders.DISCONNECTED_RESPONSE.s) -> {
                            stopKeepAlive()
                            isConnectedToRelay = false
                        }

                        decrypted.startsWith(UdpHeaders.PING_RESPONSE.s) -> {
                            val tsBytes = decrypted.copyOfRange(
                                UdpHeaders.PING_RESPONSE.s.size,
                                UdpHeaders.PING_RESPONSE.s.size + 8
                            )
                            val sentTs = ByteBuffer.wrap(tsBytes).long
                            val now = SystemClock.elapsedRealtime()
                            println("Voice ping: ${(now - sentTs)}ms")
                        }

                        decrypted.startsWith(UdpHeaders.DATAID.s) -> {
                            val parsed = Packet.parseDataWithIdOrData(decrypted) ?: run {
                                return@launch
                            }
                            val (senderId, opusPayload) = parsed
                            if (opusPayload.isEmpty()) {
                                println("Dropped empty opus payload from $senderId")
                                return@launch
                            }
                            if (opusPayload.size > 64_000) { // произвольный потолок
                                println("Suspicious large opus payload: ${opusPayload.size}")
                                return@launch
                            }
                            val decoder =
                                audioManager!!.getOrCreateDecoder(senderId!!) ?: return@launch
                            val pcm = decoder.decode(
                                opusPayload,
                                audioManager!!.frameSize
                            )
                            val f = AudioFragmentManager.addAudio(senderId, pcm, decoder)
                            if (f) {
                                val encoded = try {
                                    audioManager!!.opusEncoder?.encode(ShortArray(960).apply {
                                        fill(
                                            0
                                        )
                                    }) ?: return@launch
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    return@launch
                                }
                                AudioFragmentManager.setSilence(encoded)
                            }
                        }

                        else -> {
                            val text = try {
                                String(decrypted, Charsets.UTF_8)
                            } catch (_: Exception) {
                                "<binary ${decrypted.size}b>"
                            }
                            updateNotification("Received: ${text.take(80)}")
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


//        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
//        val networkCallback = object : ConnectivityManager.NetworkCallback() {
//            override fun onAvailable(network: Network) {
//                if(udpClient!!.isInit)
//                    startUdp(addressWithPort)
//            }
//            override fun onLost(network: Network) {
////                udpClient?.onNetworkLost(network)
//            }
//        }
//        cm.registerDefaultNetworkCallback(networkCallback)


        udpClient?.startListening(udpClient!!.currentOnReceiveCallback)

        serviceScope.launch {
            delay(150)
            try {
                val tokenBytes = SessionManager.token?.HexToByteArray() ?: ByteArray(0)
                val payload = tokenBytes + (spaceUUID?.toByteArray()
                    ?: ByteArray(0)) + (channelUUID?.toByteArray() ?: ByteArray(0))
                val encrypted = keyObj?.encrypt(payload) ?: payload
                val connIdBytes = connectionUUID?.toByteArray() ?: ByteArray(0)
                val packet = Packet.connectPacket(connIdBytes, encrypted)
                udpClient?.send(packet)
                updateNotification("CONNECT sent, listening...")
            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("Error sending CONNECT: ${e.localizedMessage}")
            }
        }
    }


    private fun startKeepAlive() {
        if (keepAliveJob?.isActive == true) return

        keepAliveJob = serviceScope.launch {
            try {
                while (isActive) {
                    try {
                        udpClient?.send(keyObj!!.encrypt(Packet.pingPacket()))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(1000L)
                }
            } finally {
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoIP Call",
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Channel for VoIP call service"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun createDisconnectAction(): PendingIntent? {
        val disconnectIntent = Intent(this, VoipForegroundService::class.java).apply {
            action = ACTION_STOP
        }


        val disconnectPendingIntent = PendingIntent.getService(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return disconnectPendingIntent
    }

    private fun createNotificationClickIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            action = "START_ACTIVITY"
            putExtra("spaceID", spaceUUID.toString())
            putExtra("channelID", channelUUID.toString())
        }

        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoIP Call in progress")
            .setSmallIcon(R.mipmap.svg_icon)
            .setContentText(text)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                createDisconnectAction()
            ) // <-- Добавь кнопку
            .setColor(getColor(R.color.svg_icon_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createNotificationClickIntent())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }


    private suspend fun startCaptureLoop() {
        val buffer = ShortArray(audioManager!!.frameSize)
        while (audioManager!!.running && serviceScope.isActive) {
            if (SessionManager.muted) {
                delay(10)
                AudioFragmentManager.isSelfSpeaking[0] = false
                continue
            }
            var got = 0
            while (got < audioManager!!.frameSize) {
                val r =
                    audioManager!!.audioRecord?.read(buffer, got, audioManager!!.frameSize - got)
                        ?: 0
                if (r < 0) break
                got += r
            }
            if (got > 0) {
                AudioFragmentManager.isSelfSpeaking[0] = true
                processAndSendAudio(buffer)
            } else {
                AudioFragmentManager.isSelfSpeaking[0] = false
                delay(5)
            }
        }
    }


    private suspend fun processAndSendAudio(buffer: ShortArray) {
        val encoded = try {
            audioManager!!.opusEncoder?.encode(buffer) ?: return
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        if (encoded.isEmpty()) return

        try {
            val encrypted = keyObj?.encrypt(UdpHeaders.DATA.s + encoded) ?: return
            udpClient?.send(encrypted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}


