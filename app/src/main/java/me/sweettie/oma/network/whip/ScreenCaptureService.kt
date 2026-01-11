package me.sweettie.oma.network.whip

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import me.sweettie.oma.ui.main.MainActivity

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "me.example.ACTION_START_CAPTURE"
        const val ACTION_STOP = "me.example.ACTION_STOP_CAPTURE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_INTENT = "extra_result_intent"
        const val EXTRA_WHIP_URL = "extra_whip_url"
        const val EXTRA_AUTH = "extra_auth"

        const val NOTIF_CHANNEL = "screen_capture_channel"
        const val NOTIF_ID = 4242
    }

    private var isCamera: Boolean = false
    private var streamer: WHIPPublisher? = null

    override fun onBind(intent: Intent?): IBinder? = null


    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val useCamera = intent.getBooleanExtra("isCamera", false)
                val whipUrl = intent.getStringExtra(EXTRA_WHIP_URL) ?: ""
                val auth = intent.getStringExtra(EXTRA_AUTH)

                createNotificationChannelIfNeeded()
                val notification = buildNotification()
                var fgType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (useCamera) {
                    fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    else
                        0
                }


                startForeground(NOTIF_ID, notification, fgType)

                streamer = WHIPPublisher(this, whipUrl, auth)
                streamer?.initialize()

                if (useCamera) {
                    streamer?.startCameraShare(
                        width = 1280,
                        height = 720,
                        fps = 30,
                        preferFront = true
                    )
                } else {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_INTENT)
                    if (resultCode == Activity.RESULT_OK && data != null) {
                        streamer?.startScreenShare(resultCode, data)
                    } else {
                        stopSelf()
                    }
                }
            }

            ACTION_STOP -> {
                stopSelfGracefully()
            }

            else -> {
                // unknown action
            }
        }
        return START_STICKY
    }


    private fun stopSelfGracefully() {
        streamer?.stopBlocking()
        streamer?.release()
        streamer = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfGracefully()
        super.onDestroy()
    }

    private fun createNotificationChannelIfNeeded() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            NOTIF_CHANNEL,
            "Screen streaming",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )


        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder =
            Notification.Builder(this, NOTIF_CHANNEL)
        builder.setContentTitle("Screen streaming")
            .setContentText("Streaming in progress")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
        return builder.build()
    }
}