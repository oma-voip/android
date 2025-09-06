package me.sweettie.oma.ui.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import me.sweettie.oma.R
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.SessionState
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var gestureDetector: GestureDetectorCompat
    private val SWIPE_DISTANCE_THRESHOLD = 300
    private val SWIPE_VELOCITY_THRESHOLD = 100

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SessionManager.saveState(SessionState.MAIN)

        val navHostFragment = NavHostFragment.create(R.navigation.nav_graph)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, navHostFragment)
            .commitNow()

        navController = navHostFragment.navController
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
                    if (abs(diffX) > SWIPE_DISTANCE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
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

    override fun onBackPressed() {
        if (!navController.navigateUp()) {
            super.onBackPressed() // закрываем приложение, если нечего "разворачивать"
        }
    }

}