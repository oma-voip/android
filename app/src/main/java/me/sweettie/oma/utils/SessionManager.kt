package me.sweettie.oma.utils

import proto.OmaOuterClass

enum class SessionState {
    NULL,
    LOGIN,
    SIGNIN,
    CEMAIL,
    MAIN
}

object SessionManager {

    var token: String? = null
    var currentUser: OmaOuterClass.User? = null
    var currentState: SessionState = SessionState.NULL

    fun loadState() {
        val state = SecurePrefs.sf.getString("state", "CEMAIL")!!
        currentState = try {
            SessionState.valueOf(state)
        } catch (e: java.lang.IllegalArgumentException) {
            SessionState.NULL
        }
    }

    fun saveState(state: SessionState) {
        currentState = state
        SecurePrefs.save("state", currentState.toString())
    }

}