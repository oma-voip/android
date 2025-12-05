package me.sweettie.oma.ui.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Connection
import android.telecom.DisconnectCause
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import me.sweettie.oma.R
import me.sweettie.oma.ui.StartUp
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState


class OmaConnection(private val context: Context, private val extras: Bundle?) : Connection() {

    private var ringtonePlayer: MediaPlayer? = null

    companion object {
        var currentConnection: OmaConnection? = null
        const val NOTIFICATION_ID = 8888
        fun showIncomingCallNotification(context: Context, extras: Bundle?) {
            val userName = extras?.getString("user", "Unknow")
            val channelID =
                extras?.getString("channelID")
            val spaceID =
                extras?.getString("spaceID")

            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("user", userName)
                putExtra("channelID", channelID)
                putExtra("spaceID", spaceID)
            }

            val acceptIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = "ACTION_ACCEPT_CALL"
            }
            val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = "ACTION_DECLINE"
            }

            val acceptPendingIntent = PendingIntent.getBroadcast(
                context, 100, acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val declinePendingIntent = PendingIntent.getBroadcast(
                context, 101, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val actionAcceptCompat = NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, android.R.drawable.ic_menu_call),
                context.getString(R.string.accept),
                acceptPendingIntent
            ).build()

            val actionDeclineCompat = NotificationCompat.Action.Builder(
                IconCompat.createWithResource(
                    context,
                    android.R.drawable.ic_menu_close_clear_cancel
                ),
                context.getString(R.string.decline),
                declinePendingIntent // Исправлено: используем declinePendingIntent
            ).build()

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                112233,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val channelId = "oma_incoming_calls_channel_v3"

            val channel = NotificationChannel(
                channelId,
                "Incoming call",
                NotificationManager.IMPORTANCE_HIGH
            )
                .apply {
                    description = "$userName calling you"
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setSound(null, null)
                }
            notificationManager.createNotificationChannel(channel)

            var notificationBuilder: Notification =
                NotificationCompat.Builder(
                    context,
                    channelId
                )
                    .setSmallIcon(R.mipmap.svg_icon)
                    .setContentTitle("Incoming call")
                    .setColor(context.getColor(R.color.svg_icon_background))
                    .setContentText("$userName calling you")
                    .setPriority(NotificationCompat.PRIORITY_MAX) // ВАЖНО!
                    .setCategory(NotificationCompat.CATEGORY_CALL) // ВАЖНО!
                    .setAutoCancel(true)
                    .addAction(actionAcceptCompat)
                    .addAction(actionDeclineCompat)
                    .setOngoing(true) // Чтобы случайно не смахнули
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val icon = Icon.createWithResource(context, R.drawable.ic_member)
                val caller = Person.Builder()
                    .setIcon(icon)
                    .setName(userName)
                    .setImportant(true)
                    .build()

                val notificationStyle = Notification.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    acceptPendingIntent
                )
                notificationBuilder = Notification.Builder(context, channelId)
                    .setSmallIcon(R.mipmap.svg_icon)
                    .setContentTitle("Incoming call")
                    .setColor(context.getColor(R.color.svg_icon_background))
                    .setContentText("$userName calling you")
                    .setStyle(notificationStyle)
                    .setCategory(NotificationCompat.CATEGORY_CALL) // ВАЖНО!
                    .setAutoCancel(true)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .setOngoing(true)
                    .build()
            }

            notificationManager.notify(
                NOTIFICATION_ID,
                notificationBuilder
            )
        }
    }

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
        audioModeIsVoip = true
        currentConnection = this
    }

    override fun onShowIncomingCallUi() {
        showIncomingCallNotification(context, extras)
        startRinging()
    }

    private fun startRinging() {
        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        try {
            ringtonePlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)

                isLooping = true

                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRinging() {
        try {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ringtonePlayer = null
    }


    override fun onAnswer(videoState: Int) {
        stopRinging()

        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        setActive()

        openVoiceChat()

    }

    private fun openVoiceChat() {
        var intent: Intent
        if (SessionManager.currentState != SessionState.NULL) {
            intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = "ACTION_OPEN_VOICE_CHAT"
                putExtras(currentConnection!!.extras!!)
            }
        } else {
            intent = Intent(context, StartUp::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = "ACTION_OPEN_VOICE_CHAT"
                putExtras(currentConnection!!.extras!!)
            }
        }
        context.startActivity(intent)

    }

    override fun onReject() {
        stopRinging()

        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
        currentConnection = null
    }

    override fun onDisconnect() {
        stopRinging()

        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        currentConnection = null
    }
}