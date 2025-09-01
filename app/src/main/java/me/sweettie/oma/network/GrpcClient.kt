package me.sweettie.oma.network

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import proto.OmaGrpc
import proto.OmaOuterClass

object GrpcClient {

    private val channel: ManagedChannel = OkHttpChannelBuilder
        .forAddress("oma.sovamor.co", 443)
        .useTransportSecurity()
        .build()

    private val stub = OmaGrpc.newBlockingStub(channel) // для блокирующих вызовов

    suspend fun login(username: String, password: String): OmaOuterClass.LoginResponse {
        val request = OmaOuterClass.LoginRequest.newBuilder()
            .setUsername(username)
            .setPassword(password)
            .build()

        return withContext(Dispatchers.IO) {
            stub.login(request)
        }
    }

    fun shutdown() {
        channel.shutdown()
    }
}