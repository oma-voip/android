package me.sweettie.oma.ui.call

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import me.sweettie.oma.R

class IncomingCallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndKeyguard()

        setContentView(R.layout.activity_incoming_call)


        val userName = intent.getStringExtra("user")

        val callNameTextView = findViewById<TextView>(R.id.caller_name)
        callNameTextView.text = userName

        findViewById<FloatingActionButton>(R.id.btn_answer).setOnClickListener {
            OmaConnection.currentConnection?.onAnswer()
            finish()
        }

        findViewById<FloatingActionButton>(R.id.btn_reject).setOnClickListener {
            OmaConnection.currentConnection?.onReject()
            finish()
        }
    }

    private fun turnScreenOnAndKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}