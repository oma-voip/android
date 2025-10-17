package me.sweettie.oma.network.services.voip

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import eu.buney.kopus.decode
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
import me.sweettie.oma.utils.HexToByteArray
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.startsWith
import me.sweettie.oma.utils.toByteArray
import java.nio.ByteBuffer
import java.util.UUID

class VoipForegroundService : Service() {

    object AudioFragmentManager {

        val audios = HashMap<UUID, ArrayList<ShortArray>>()
        fun addAudio(uuid: UUID, data: ShortArray): Boolean {
            val first = audios.isEmpty()
            val r: ArrayList<ShortArray> = audios.getOrDefault(uuid, ArrayList())
            r.add(data)
            audios[uuid] = r
            return first
        }

        fun clear() {
            audios.clear()
        }
        private val FRAME_SIZE = 960
        fun getDatas(): ShortArray {
            val buffers = ArrayList<ShortArray>()
            audios.forEach {
                if (it.value.isNotEmpty())
                    buffers.add(it.value.removeAt(0))
            }
            if (buffers.isEmpty()) return ShortArray(FRAME_SIZE).apply { fill(0) }
            val output = ShortArray(FRAME_SIZE)
            output.fill(0)


            buffers.forEach {
                for (i in it.indices) {
                    if (i < output.size) {
                        var a: Short = (output[i] + it[i]).toShort()
                        if (a > Short.Companion.MAX_VALUE) a = Short.Companion.MAX_VALUE
                        if (a < Short.Companion.MIN_VALUE) a = Short.Companion.MIN_VALUE
                        output[i] = a
                    }
                }
            }
            return output
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
            if (isNear && isConnectedToRelay) {
                // Прикрыл ухо -> гасим экран
                try {
                    if (proximityWakeLock != null && proximityWakeLock?.isHeld != true) {
                        proximityWakeLock?.acquire()
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                stopForeground(true)
                stopProximityMonitoring()
                stopSelf()
            }

            ACTION_CHANGE_OUTPUT -> {
                val type = intent.getIntExtra(EXTRA_OUTPUT_TYPE, 0)
                audioManager?.changeOutputType(type)
            }
        }
        return START_STICKY
    }

    private fun startUdp(addressWithPort: String) {
        val (host, port) = UdpClient.parseAddress(addressWithPort)

        udpClient?.stop()
        udpClient = UdpClient(host, port)

        udpClient?.startListening { data, len ->
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
                                    VoipAudioManager(applicationContext, udpClient!!, keyObj!!)
                                if (ActivityCompat.checkSelfPermission(
                                        applicationContext,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    audioManager!!.start()
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
                            val pcm = audioManager!!.getOrCreateDecoder(senderId!!)!!.decode(
                                opusPayload,
                                audioManager!!.frameSize
                            )
                            AudioFragmentManager.addAudio(senderId, pcm)

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


    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VoIP Call in progress")
            .setSmallIcon(R.mipmap.svg_icon)
            .setContentText(text)
            .setColor(getColor(R.color.svg_icon_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

    }
}