package me.sweettie.oma.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.call.OmaConnectionService
import me.sweettie.oma.ui.login.LoginActivity
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.register.ConfirmEmailActivity
import me.sweettie.oma.ui.register.RegisterActivity
import me.sweettie.oma.ui.update.UpdateActivity
import me.sweettie.oma.updater.VersionChecker
import me.sweettie.oma.utils.AttachmentCacheManager
import me.sweettie.oma.utils.AttachmentStorage
import me.sweettie.oma.utils.SecurePrefs
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.Utils.getVersionCode


class StartUp : AppCompatActivity() {

    private var keepSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)

        createNotificationChannel()

        setContentView(R.layout.start_up)
        SecurePrefs.init(this)
        SessionManager.loadState()
        SessionManager.cacheManager = AttachmentCacheManager(this, GrpcClient::downloadChunks)
        AttachmentStorage.init(this)
        registerPhoneAccount(this)

        if (Build.VERSION.SDK_INT >= 34) {
            requestPermissions(
                listOf(Manifest.permission.USE_FULL_SCREEN_INTENT).toTypedArray(),
                10001
            )
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    startActivity(intent)
                } catch (e: Exception) {
                    println("Error while calling Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)")
                    e.printStackTrace()
                }
            }
        }

        lifecycleScope.launch {
            try {
                val checker = VersionChecker()
                try {
                    val info = checker.fetchVersionInfo()
                    SessionManager.updateInfo = info
                    println("${getVersionCode(applicationContext)} | ${info.version}")
                    if (getVersionCode(applicationContext) < info.version) {
                        Utils.openActivity(this@StartUp, UpdateActivity::class.java)
                    } else
                        logic()
                } catch (e: Exception) {
                    println("Error fetching version info: ${e.message}")
                    logic()
                }


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun logic() {
        val token: String? = SecurePrefs.sf!!.getString("token", null)
//        val token: String? = null //only for test login activity
        if (token != null) {
            SessionManager.token = token
            val user = GrpcClient.getUser("me")
            if (user != null) {
                SessionManager.currentUser = user
                SecurePrefs.save("user_id", SessionManager.currentUser!!.id)

                val t = GrpcClient.getServers()
                if (t == null) {
                    return
                }
                val servers = arrayListOf<Server>()
                println("Init servers: ${t.spacesList.size}")
                t.spacesList.forEach {
                    println("STARTUP: prepear server with id ${it.id}")
                    servers.add(Server(it))

                }
                SessionManager.servers = servers
//                SessionManager.servers.forEach { s ->
//                    s.loadAllNecessaryInfo()
//                    s.hashChannels.forEach {
//                        GrpcClient.getMessages(s.space.id, it.value.id, 0, onSuccess = { ml_ ->
//                            // LOAD MESSAGE LIST, CHECK LAST SAVED MESSAGE, IF LAST SAVED MESSAGE ID NOT EQUALS NEW LAST MESSAGE ID - MARK HIM AS UNREAD
//                            val ml = MessageList(s.space.id, it.value.id, ml_)
//                            SessionManager.cachedMessageList[it.value.id] = ml
//                            val lastMessage = ml.messageLis[0]
//                            if (!SecurePrefs.sf!!.getString("last_${s.space.id}_${it.key}", "")
//                                    .equals(lastMessage.id)
//                            ) {
//                                val temp = SessionManager.unreadedMessages.getOrDefault(
//                                    s.space.id,
//                                    arrayListOf()
//                                )
//                                if (!temp.contains(it.value.id)) {
//                                    temp.add(it.value.id)
//                                    SessionManager.unreadedMessages.put(s.space.id, temp)
//                                }
//                            }
//
//                        }, onError = {})
//                    }
//                }
                keepSplash = false
                val intent2 = Intent(this@StartUp, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP and Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (intent.action != null) {

                    if (arrayOf(
                            "START_ACTIVITY",
                            "ACTION_OPEN_VOICE_CHAT"
                        ).contains(intent.action)
                    ) {
                        val spaceId = intent.getStringExtra("spaceID")
                        val server = SessionManager.getServerBySpaceId(spaceId!!)
                        if (server != null) {
                            server.loadAllNecessaryInfo()
                            intent2.action = intent.action
                            intent2.putExtras(intent.extras!!)
                        }
                    }
                }
                this@StartUp.startActivity(intent2)
                return
            }
        }

        keepSplash = false
        when (SessionManager.currentState) {
            SessionState.SIGNIN -> Utils.openActivity(
                this@StartUp,
                RegisterActivity::class.java
            )

            SessionState.CEMAIL -> Utils.openActivity(
                this@StartUp,
                ConfirmEmailActivity::class.java
            )

            else -> Utils.openActivity(this@StartUp, LoginActivity::class.java)

        }
    }

    fun registerPhoneAccount(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val componentName = ComponentName(context, OmaConnectionService::class.java)

        val phoneAccountHandle = PhoneAccountHandle(componentName, "oma_app_id")

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "Oma VoIP")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()

        telecomManager.registerPhoneAccount(phoneAccount)
    }

    private fun createNotificationChannel() {
        val channelId = getString(R.string.oma_notification_channel)
        val channelName = "Default Oma Notification Channel"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = "Channel for default notifications"
        }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}