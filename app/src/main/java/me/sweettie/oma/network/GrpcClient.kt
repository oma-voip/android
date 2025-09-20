package me.sweettie.oma.network

import com.google.protobuf.Empty
import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import proto.OmaGrpc
import proto.OmaOuterClass
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object GrpcClient {

    private val channel: ManagedChannel = OkHttpChannelBuilder
        .forAddress("oma.sovamor.co", 443)
        .useTransportSecurity()
        .build()

    private val ic: Channel = ClientInterceptors.intercept(channel, AuthInterceptor())

    private val stub = OmaGrpc.newBlockingStub(ic)
    private val eventStub = OmaGrpc.newStub(ic)

    fun subscribeToEvents(): Flow<OmaOuterClass.Event> = callbackFlow {
        val request = Empty.getDefaultInstance()
        val streamObserver = object : StreamObserver<OmaOuterClass.Event> {
            override fun onNext(value: OmaOuterClass.Event) {
                trySend(value) // Отправляем событие в Flow
            }

            override fun onError(t: Throwable) {
                close(t) // Закрываем канал с ошибкой
            }

            override fun onCompleted() {
                close() // Сервер закрыл стрим
            }
        }
        eventStub.subscribe(request, streamObserver)
        awaitClose {
        }
    }


    suspend fun registerFbToken(token: String) {
        try {
            val req = OmaOuterClass.RegisterDeviceRequest.newBuilder()
                .setToken(token)
                .build()
            withContext(Dispatchers.IO) {
                stub.registerDevice(req)
            }
        } catch (_: Exception) {
        }
    }

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

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun downloadChunks(attachId: String): Iterator<OmaOuterClass.FileChunk> =
        suspendCoroutine { cont ->
            GlobalScope.launch {
                try {
                    getAttachment(
                        attachId,
                        onSuccess = { iterator -> cont.resume(iterator) },
                        onError = { e -> cont.resumeWithException(e) }
                    )
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }
        }

    suspend fun getAttachment(
        attachId: String, onSuccess: (Iterator<OmaOuterClass.FileChunk>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val req = OmaOuterClass.DownloadRequest.newBuilder()
                .setId(attachId)
                .build()
            onSuccess(withContext(Dispatchers.IO) {
                stub.download(req)
            })
        } catch (e: Exception) {
            onError(e)
        }
    }

    suspend fun getMessages(
        spaceId: String,
        chatId: String, offset: Long, onSuccess: (OmaOuterClass.GetMessagesResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val req = OmaOuterClass.GetMessagesRequest.newBuilder()
                .setSpaceId(spaceId)
                .setChannelId(chatId)
                .setOffset(offset)
                .build()
            onSuccess(withContext(Dispatchers.IO) {
                stub.getMessages(req)
            })
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

    suspend fun getServers(): OmaOuterClass.GetSpacesResponse? {
        return try {
            withContext(Dispatchers.IO) {
                stub.getSpaces(Empty.getDefaultInstance())
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getChannels(id: String): OmaOuterClass.GetChannelsResponse? {
        return try {
            val request = OmaOuterClass.GetChannelsRequest.newBuilder()
                .setSpaceId(id)
                .build()

            withContext(Dispatchers.IO) {
                stub.getChannels(request)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getChannel(sid: String, id: String): OmaOuterClass.Channel? {
        return try {
            val request = OmaOuterClass.GetChannelRequest.newBuilder()
                .setSpaceId(sid)
                .setId(id)
                .build()

            withContext(Dispatchers.IO) {
                stub.getChannel(request)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSpace(id: String): OmaOuterClass.GetSpaceResponse? {
        return try {
            val request = OmaOuterClass.GetSpaceRequest.newBuilder()
                .setId(id)
                .build()

            withContext(Dispatchers.IO) {
                stub.getSpace(request)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun sendMessage(serverId: String, channelId: String, message: String) {
        try {
            val req = OmaOuterClass.SendMessageRequest.newBuilder()
                .setSpaceId(serverId)
                .setChannelId(channelId)
                .setText(message)
                .build()
            withContext(Dispatchers.IO) {
                stub.sendMessage(req)
            }
        } catch (_: Exception) {
        }
    }


}