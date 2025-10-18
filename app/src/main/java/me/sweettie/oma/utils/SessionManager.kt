package me.sweettie.oma.utils

import kotlinx.coroutines.CoroutineScope
import me.sweettie.oma.data.MessageList
import me.sweettie.oma.data.Server
import me.sweettie.oma.data.Streamer
import me.sweettie.oma.data.Thumb
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

    lateinit var applicationScope: CoroutineScope
    var isWatchStream = false
    val mutedStates: HashMap<String, Boolean> = HashMap()
    var oldOutputType: Int = 1
    lateinit var updateInfo: VersionInfo
    var startCallTime: Long = -1L
    val streamers: HashMap<String, Streamer> = HashMap()
    val thumbnail: HashMap<String, Thumb> = HashMap()
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

    fun updateOnlineInfo(id: String, online: Boolean) {
        onlineUsers.remove(id)
        if (online) {
            onlineUsers.add(id)
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