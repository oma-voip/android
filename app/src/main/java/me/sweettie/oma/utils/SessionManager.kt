package me.sweettie.oma.utils

import me.sweettie.oma.data.MessageList
import me.sweettie.oma.data.Server
import me.sweettie.oma.updater.VersionInfo
import proto.OmaOuterClass
import java.util.UUID

enum class SessionState {
    NULL,
    LOGIN,
    SIGNIN,
    CEMAIL,
    MAIN
}

object SessionManager {

    lateinit var updateInfo: VersionInfo
    var startCallTime: Long = -1L
    var selectedServer: Server? = null
    var token: String? = null
    var currentUser: OmaOuterClass.User? = null
    var currentState: SessionState = SessionState.NULL
    var servers: ArrayList<Server> = arrayListOf()
    val onlineUsers: ArrayList<String> = arrayListOf()
    var uuid: UUID? = null
    var selectedChannel: String? = null
    val cachedMessageList: HashMap<String, MessageList> = HashMap()
    val channelMembers: HashMap<String, ArrayList<OmaOuterClass.VoiceMember>> = HashMap()
    var muted: Boolean = false

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
            if (it.space.id.equals(serverId))
                return it
        }
        return null
    }

    fun updateServer(r: Server) {
        val t = getServerBySpaceId(r.space.id)
        servers.remove(t)
        servers.add(r)
        if (selectedServer != null && selectedServer == t) {
            selectedServer = r
        }
    }

}