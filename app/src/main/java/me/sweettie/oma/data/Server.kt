package me.sweettie.oma.data

import me.sweettie.oma.network.GrpcClient
import proto.OmaOuterClass
import java.util.*

class Server(
    val space: OmaOuterClass.Space,
    var channels: List<OmaOuterClass.Channel> = Collections.emptyList()
) {
    lateinit var members: List<OmaOuterClass.User>
    suspend fun loadAllNecessaryInfo() {
        val r = GrpcClient.getSpace(space.id) ?: return
        members = r.membersList
        val t = GrpcClient.getChannels(space.id) ?: return
        channels = t.channelsList

    }
}