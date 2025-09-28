package me.sweettie.oma.ui.update

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.data.Server
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.ui.login.LoginActivity
import me.sweettie.oma.ui.main.MainActivity
import me.sweettie.oma.ui.register.ConfirmEmailActivity
import me.sweettie.oma.ui.register.RegisterActivity
import me.sweettie.oma.updater.APKDownloader
import me.sweettie.oma.utils.SecurePrefs
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState
import me.sweettie.oma.utils.Utils
import me.sweettie.oma.utils.Utils.showConfirmationDialog

class UpdateActivity : AppCompatActivity() {

    var check = false
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        progressBar = findViewById(R.id.progressBar)

        if (!packageManager.canRequestPackageInstalls()) {
            showConfirmationDialog(
                this,
                "To continue, please, confirm upload from unknow source",
                "Exit",
                "Continue",
                onPositiveClick = {
                    it.dismiss()
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            "package:me.sweettie.oma".toUri()
                        )
                    )
                    check = true
                },
                onNegativeClick = { runUpdater() })
        } else
            runUpdater()
    }

    override fun onResume() {
        super.onResume()
        if (!check)
            return
        if (packageManager.canRequestPackageInstalls()) {
            runUpdater()
        }
    }


    private fun runUpdater() {

        val info = SessionManager.updateInfo
        showConfirmationDialog(
            this, info.title, "Download", "Skip",
            onPositiveClick = {
                val apkDownloader = APKDownloader(this, info)
                progressBar.visibility = View.VISIBLE

                apkDownloader.downloadAPK(
                    onProgress = { progress ->
                        runOnUiThread {
                            progressBar.progress = progress
                        }
                    },
                    onComplete = { file ->
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            if (file != null) {
                                apkDownloader.openApk(file)
                            } else {
                                Toast.makeText(this, "Error while updating apk", Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            },
            onNegativeClick = {
                lifecycleScope.launch {
                    logic()
                }
            }
        )
    }

    private suspend fun logic() {
        val token: String? = SecurePrefs.sf.getString("token", null)
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
                    servers.add(Server(it))
                }
                SessionManager.servers = servers

                Utils.openActivity(this@UpdateActivity, MainActivity::class.java)
                return
            }
        }
        when (SessionManager.currentState) {
            SessionState.SIGNIN -> Utils.openActivity(
                this@UpdateActivity,
                RegisterActivity::class.java
            )

            SessionState.CEMAIL -> Utils.openActivity(
                this@UpdateActivity,
                ConfirmEmailActivity::class.java
            )

            else -> Utils.openActivity(this@UpdateActivity, LoginActivity::class.java)

        }
    }
}