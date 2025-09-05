package me.sweettie.oma.network

import io.grpc.Channel
import io.grpc.ClientInterceptors
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

    private val ic: Channel = ClientInterceptors.intercept(channel, AuthInterceptor())

    private val stub = OmaGrpc.newBlockingStub(ic)


    suspend fun register(
        username: String,
        password: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val req = OmaOuterClass.RegisterRequest.newBuilder()
                .setEmail(email)
                .setUsername(username)
                .setPassword(password)
                .build()

            withContext(Dispatchers.IO) {
                stub.register(req)
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    suspend fun confirmCode(
        code: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val req = OmaOuterClass.VerifyEmailRequest.newBuilder()
                .setCode(code)
                .setEmail(email)
                .build()

            withContext(Dispatchers.IO) {
                stub.verifyEmail(req)
            }
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    suspend fun login(
        username: String,
        password: String,
        onSuccess: (OmaOuterClass.LoginResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val request = OmaOuterClass.LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build()

            onSuccess(withContext(Dispatchers.IO) {
                stub.login(request)
            })
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun shutdown() {
        channel.shutdown()
    }

    //, onError: (Exception) -> Unit

    suspend fun getUser(id: String): OmaOuterClass.User? {
        return try {
            val request = OmaOuterClass.GetUserRequest.newBuilder()
                .setId(id)
                .build()

            withContext(Dispatchers.IO) {
                stub.getUser(request)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun resendCode(email: String, onComplete: (Boolean) -> Unit) {
        var b = false
        try {
            val request = OmaOuterClass.ResendVerificationRequest.newBuilder()
                .setEmail(email)
                .build()

            withContext(Dispatchers.IO) {
                stub.resendVerification(request)
            }
        } catch (e: Exception) {
            b = true
        }
        onComplete.invoke(b)
    }


}