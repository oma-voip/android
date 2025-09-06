package me.sweettie.oma.ui

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
import me.sweettie.oma.utils.SecurePrefs
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState
import me.sweettie.oma.utils.Utils

class StartUp : AppCompatActivity() {

    private var keepSplash = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.start_up)
        SecurePrefs.init(this)
        SessionManager.loadState()
        lifecycleScope.launch {
            try {
                val token: String? = SecurePrefs.sf.getString("token", null)
//                val token: String? = null //only for test login activity
                if (token != null) {
                    SessionManager.token = token
                    val user = GrpcClient.getUser("me")
                    if (user != null) {
                        SessionManager.currentUser = user

                        val t = GrpcClient.getServers()
                        if (t == null) {
                            return@launch
                        }
                        val servers = mutableListOf<Server>()
                        println("Init servers: ${t.spacesList.size}")
                        t.spacesList.forEach {

                            servers.add(Server(it))
                        }
                        SessionManager.servers = servers

                        keepSplash = false
                        Utils.openActivity(this@StartUp, MainActivity::class.java)
                        return@launch
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}