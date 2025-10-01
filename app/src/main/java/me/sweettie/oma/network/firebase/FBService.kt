package me.sweettie.oma.network.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.main.MainActivity


class FBService : FirebaseMessagingService() {



    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("New token = $token")
        CoroutineScope(Dispatchers.IO).launch {
            GrpcClient.registerFbToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        println("New message:\n type = ${message.messageType}\n ${message.data}")
        var text = ""
        message.data.forEach { (t, u) ->
            println("$t -> $u")
            text += "$t -> $u | "
        }
        if (message.notification == null) {
            if (message.data.getOrDefault("type", "").equals("message_received"))
                showNotification(this, message)
        }
    }

//    private fun test1(context: Context){
//
////        val inboxStyle = NotificationCompat.InboxStyle()
////            .setBigContentTitle("ServerName #channel")
////            .setSummaryText("${messages.size} new messages")
//
////        val markAsReadIntent = Intent(context, MainActivity::class.java).apply {
////            action = "ACTION_MARK_AS_READ"
////            putExtra("notification_id", "notificationId")
////        }
////        val markAsReadPendingIntent = PendingIntent.getBroadcast(
////            context, 0, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
////        )
//
////        val replyIntent = Intent(context, MainActivity::class.java).apply {
////            action = "ACTION_REPLY"
////            putExtra("notification_id", "testid")
////        }
////        val replyPendingIntent = PendingIntent.getBroadcast(
////            context, 1, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
////        )
//
////        val notification = NotificationCompat.Builder(context, context.getString(R.string.oma_notification_channel))
////            .setSmallIcon(R.mipmap.svg_icon)
////            .setContentTitle("ServerName #channel")
////            .setStyle(inboxStyle)
////            .addAction(R.drawable.ic_attachment_file, "Mark As Read", markAsReadPendingIntent)
////            .addAction(R.drawable.ic_mic, "Reply", replyPendingIntent)
////            .setAutoCancel(true)
////            .build()
//
//        val notificationManager =
//            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
//    }

    companion object {
        fun showNotification(context: Context, message: RemoteMessage) {
            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("serverId", message.data["spaceID"])
                putExtra("channelId", message.data["channelID"])
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder =
                NotificationCompat.Builder(
                    context,
                    context.getString(R.string.oma_notification_channel)
                )
                    .setSmallIcon(R.mipmap.svg_icon)
                    .setContentTitle(message.data["space"] + "@" + message.data["channel"] + " | " + message.data["user"])
                    .setColor(context.getColor(R.color.svg_icon_background))
                    .setContentText(message.data["text"])
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

            notificationManager.notify(
                System.currentTimeMillis().toInt(),
                notificationBuilder.build()
            )
        }
    }
//    fun ShowNotification(title: String?, text: String?, color: Int) {
//        val mNotify = NotificationCompat.Builder(SessionManager., "")
//        mNotify.setLights(color, 100, 200)
//        mNotify.setSmallIcon(R.drawable.service_icon)
//        mNotify.setContentTitle(title)
//        mNotify.setContentText(text)
//        mNotify.setDefaults(Notification.DEFAULT_SOUND)
//        val mNotificationManager =
//            getSystemService<Any>(Context.NOTIFICATION_SERVICE) as NotificationManager
//        val mId = 1001
//        try {
//            mNotificationManager.notify(mId, mNotify.build())
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
}