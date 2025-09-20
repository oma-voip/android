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
        message.data.forEach { (t, u) ->
            println("$t -> $u")
        }

        // only for in app notification
//        if (message.notification != null) {
//            println("Try to show notification with title ${message.notification!!.title} and body ${message.notification!!.body}")
//            showNotification(this,
//                message.notification!!.title!!,
//                message.notification!!.body!!
//            )
//        }
    }

    companion object {
        fun showNotification(context: Context, title: String, body: String) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

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
                    .setSmallIcon(R.mipmap.icon)
                    .setContentTitle(title)
                    .setContentText(body)
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