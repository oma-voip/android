package me.sweettie.oma.ui.call

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class OmaConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        val connection = OmaConnection(applicationContext, request.extras)

        val callerName = request.extras?.getString("user") ?: "Unknown"
        connection.setCallerDisplayName(callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.putExtras(request.extras)
        connection.setRinging()
        connection.setInitializing()

        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val connection = OmaConnection(applicationContext, request?.extras)
        connection.setDialing()
        connection.setActive()
        return connection
    }
}