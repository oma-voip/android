package me.sweettie.oma.data

import me.sweettie.oma.network.GrpcClient
import proto.OmaOuterClass
import proto.OmaOuterClass.Space
import java.util.Collections

class Server(
    val space: Space,
    var channels: List<OmaOuterClass.Channel> = Collections.emptyList()
) {

    val hashChannels: HashMap<String, OmaOuterClass.Channel> = HashMap()

    lateinit var members: List<OmaOuterClass.User>
    suspend fun loadAllNecessaryInfo() {
        val r = GrpcClient.getSpace(space.id) ?: return
        members = r.membersList
        val t = GrpcClient.getChannels(space.id) ?: return
        channels = t.channelsList
        updateHash()
    }

    private fun updateHash() {
        hashChannels.clear()
        channels.forEach {
            hashChannels[it.id] = it
        }
    }

    fun getMemberById(id: String): OmaOuterClass.User? {
        members.forEach {
            if (it.id.equals(id))
                return it
        }
        return null
    }

    init {
        updateHash()
    }
}