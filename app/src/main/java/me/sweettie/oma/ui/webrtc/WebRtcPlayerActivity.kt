package me.sweettie.oma.ui.webrtc

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Window
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.utils.SessionManager

class WebRtcPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET_URL = "extra_target_url"
        const val EXTRA_AUTH = "extra_auth"
        const val EXTRA_ID = "extra_id"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        webView = WebView(this)
        setContentView(webView)
        SessionManager.isWatchStream = true
        val targetUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: ""
        val auth = intent.getStringExtra(EXTRA_AUTH) ?: ""
        val id = intent.getStringExtra(EXTRA_ID) ?: ""

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.domStorageEnabled = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                println("WebView: ${message?.message() ?: ""}")
                return true
            }
        }

        webView.loadUrl("file:///android_asset/player.html?url=${targetUrl}&auth=${auth}")



        lifecycleScope.launch {
            SessionManager.viewModel.uiState.collect { state ->
                when (state) {
                    is MainViewModel.UiState.StreamEnded -> {
                        if (state.userID == id)
                            finishActivity(1000)
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        SessionManager.isWatchStream = false
    }

    override fun onResume() {
        super.onResume()
        SessionManager.isWatchStream = true
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        SessionManager.isWatchStream = false
    }


}