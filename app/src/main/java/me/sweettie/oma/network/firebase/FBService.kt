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
import me.sweettie.oma.ui.StartUp
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState


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
//        var text = ""
//        message.data.forEach { (t, u) ->
//            println("$t -> $u")
//            text += "$t -> $u | "
//        }
        if (message.notification == null) {
            if (message.data.getOrDefault("type", "").equals("message_received"))
                showNotification(this, message)
        }
    }

    companion object {
        fun showNotification(context: Context, message: RemoteMessage) {

            val isAppForgeground = SessionManager.currentState == SessionState.NULL

            if (!isAppForgeground)
                return

            val notificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, StartUp::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                action = "START_ACTIVITY"
                putExtra("spaceID", message.data["spaceID"])
                putExtra("channelID", message.data["channelID"])
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            var text = message.data["text"]!!
            if (text.isEmpty() || text == "") {
                text = "*image*"
                var text1 = ""
                message.data.forEach { (t, u) ->
                    println("$t -> $u")
                    text1 += "$t -> $u | "
                }
                println(text1)
            }
            val notificationBuilder =
                NotificationCompat.Builder(
                    context,
                    context.getString(R.string.oma_notification_channel)
                )
                    .setSmallIcon(R.mipmap.svg_icon)
                    .setContentTitle(message.data["space"] + "@" + message.data["channel"] + " | " + message.data["user"])
                    .setColor(context.getColor(R.color.svg_icon_background))
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)

            notificationManager.notify(
                System.currentTimeMillis().toInt(),
                notificationBuilder.build()
            )
        }
    }
}