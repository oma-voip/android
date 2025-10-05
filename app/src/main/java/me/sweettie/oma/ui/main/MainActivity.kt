@file:Suppress("DEPRECATION")

package me.sweettie.oma.ui.main

import android.Manifest
import android.R.attr
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.sweettie.oma.R
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    lateinit var navController: NavController
    private lateinit var gestureDetector: GestureDetectorCompat
    private val swipeDistance = 300
    private val swipeVelocity = 100

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()


        val typedValue = TypedValue()
        val found = theme.resolveAttribute(attr.windowBackground, typedValue, true)
        val color: Int = if (found) {
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            ContextCompat.getColor(this, R.color.colorSidebarBackground)
        }
        window.navigationBarColor = color

        super.onCreate(savedInstanceState)
        try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            FirebaseApp.initializeApp(this)
        }

        setContentView(R.layout.activity_main)


        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("OMA", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            val token = task.result
            println("New token = $token")
            CoroutineScope(Dispatchers.IO).launch {
                GrpcClient.registerFbToken(token)
            }
        })

        SessionManager.saveState(SessionState.MAIN)
        window?.apply {
            statusBarColor = Color.TRANSPARENT
        }
        navController =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onShowPress(e: MotionEvent) {}
            override fun onSingleTapUp(e: MotionEvent): Boolean = true
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean = true

            override fun onLongPress(e: MotionEvent) {}

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                println(diffX)
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > swipeDistance && abs(velocityX) > swipeVelocity) {
                        if (diffX < 0) {
                            navigateForward()
                            return true
                        }
                    }
                }
                return false
            }

        })


    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gestureDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    private fun navigateForward() {
        val currentDestination = navController.currentDestination ?: return
        val actionId = when (currentDestination.id) {
            R.id.homeFragment -> R.id.action_home_to_chat
            R.id.chatFragment -> R.id.action_chat_to_members
            else -> null
        }
        actionId?.let {
            if (navController.currentDestination?.getAction(it) != null) {
                navController.navigate(it)
            }
        }
    }


}