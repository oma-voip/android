package me.sweettie.oma.utils

import me.sweettie.oma.data.MessageList
import me.sweettie.oma.data.Server
import proto.OmaOuterClass
import java.util.*

enum class SessionState {
    NULL,
    LOGIN,
    SIGNIN,
    CEMAIL,
    MAIN
}

object SessionManager {

    var selectedServer: Server? = null
    var token: String? = null
    var currentUser: OmaOuterClass.User? = null
    var currentState: SessionState = SessionState.NULL
    var servers: List<Server> = Collections.emptyList()
    val onlineUsers: ArrayList<String> = arrayListOf()
    var uuid: UUID? = null
    var selectedChannel: String? = null
    val cachedMessageList: HashMap<String, MessageList> = HashMap()

//    Utils

    lateinit var cacheManager: AttachmentCacheManager

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

    fun updateOnlineInfo(on: OmaOuterClass.OnlineStateUpdated) {
        onlineUsers.remove(on.userId)
        if (on.online) {
            onlineUsers.add(on.userId)
        }
    }

    fun getServerBySpaceId(serverId: String): Server? {
        servers.forEach {
            if (it.space.id == serverId)
                return it
        }
        return null
    }

}