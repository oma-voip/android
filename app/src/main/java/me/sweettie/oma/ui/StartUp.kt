package me.sweettie.oma.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.network.GrpcClient
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
//                val token: String? = null //only for test login activity
        if (token != null) {
            SessionManager.token = token
            val user = GrpcClient.getUser("me")
            if (user != null) {
                SessionManager.currentUser = user

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

                keepSplash = false
                val intent2 = Intent(this@StartUp, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP and Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                if (intent.action != null) {
                    if (intent.action == "START_ACTIVITY") {
                        val spaceId = intent.getStringExtra("spaceID")
                        val server = SessionManager.getServerBySpaceId(spaceId!!)
                        if (server != null) {
                            server.loadAllNecessaryInfo()
                            intent2.action = "START_ACTIVITY"
                            intent2.putExtra("spaceID", spaceId)
                            intent2.putExtra("channelID", intent.getStringExtra("channelID"))
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