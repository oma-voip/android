package me.sweettie.oma.network

import io.grpc.*
import me.sweettie.oma.utils.SessionManager

class AuthInterceptor : ClientInterceptor {

    companion object {
        val TOKEN_KEY: Metadata.Key<String> =
            Metadata.Key.of("token", Metadata.ASCII_STRING_MARSHALLER)
        val UUID_KEY: Metadata.Key<String> =
            Metadata.Key.of("client-id", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                if (SessionManager.token != null) {
                    headers.put(TOKEN_KEY, SessionManager.token)
                }
                headers.put(UUID_KEY, SessionManager.uuid.toString())
                super.start(responseListener, headers)
            }
        }
    }
}