package me.sweettie.oma.ui.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission

class CallActionReceiver : BroadcastReceiver() {
    @RequiresPermission("android.permission.BROADCAST_CLOSE_SYSTEM_DIALOGS")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_DECLINE" -> {
                OmaConnection.currentConnection?.onReject()

            }

            "ACTION_ACCEPT_CALL" -> {
                OmaConnection.currentConnection?.onAnswer()

            }
        }
    }
}