package me.sweettie.oma.network.services.voip

import android.telecom.Connection
import android.telecom.ConnectionService

class VoipConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: android.telecom.PhoneAccountHandle?,
        request: android.telecom.ConnectionRequest?
    ): Connection {
        val connection = VoipConnection()
        connection.setInitializing()

        connection.setRinging()
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: android.telecom.PhoneAccountHandle?,
        request: android.telecom.ConnectionRequest?
    ): Connection {
        val connection = VoipConnection()
        connection.setInitializing()

        connection.setDialing()
        return connection
    }
}