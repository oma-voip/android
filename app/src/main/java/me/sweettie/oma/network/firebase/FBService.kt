package me.sweettie.oma.network.firebase

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.StartUp
import me.sweettie.oma.ui.call.OmaConnectionService
import me.sweettie.oma.utils.SecurePrefs
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
            if (message.data.get("type").equals("incoming_call"))
                openCallView(this, message)
            else if (message.data.getOrDefault("type", "").equals("message_received"))
                showNotification(this, message)

        }
    }

    companion object {

        fun openCallView(context: Context, message: RemoteMessage) {
            if (SecurePrefs.sf == null)
                SecurePrefs.init(context)

            val userName = message.data.get("user")
            val channelID = message.data.get("channelID")
            val spaceID = message.data.get("spaceID")


            val isAppForgeground = SessionManager.currentState == SessionState.NULL
            if (!isAppForgeground && SessionManager.selectedChannel == channelID) {
                println("Block open CallUI, because its self calling")
                return
            }


            val telecomManager = context.getSystemService(TELECOM_SERVICE) as TelecomManager
            val componentName = ComponentName(context, OmaConnectionService::class.java)
            val phoneAccountHandle = PhoneAccountHandle(componentName, "oma_app_id")

            val extras = Bundle().apply {
                putString("user", userName)
                putString("channelID", channelID)
                putString("spaceID", spaceID)
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
            }

            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.MANAGE_OWN_CALLS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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