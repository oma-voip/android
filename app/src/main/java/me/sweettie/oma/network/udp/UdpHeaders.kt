package me.sweettie.oma.network.udp

enum class UdpHeaders(val s: ByteArray) {
    CONNECT_PREFIX("5  CONNECT ".toByteArray()),
    CONNECTED_RESPONSE("5  CONNECTED".toByteArray()),
    DISCONNECT_MESSAGE("5  DISCONNECT".toByteArray()),
    DISCONNECTED_RESPONSE("5  DISCONNECTED".toByteArray()),
    PING_MESSAGE("5  PING".toByteArray()),
    PING_RESPONSE("5  PONG".toByteArray()),
    DATA("5  DATA ".toByteArray()),
    DATAID("5  DATAID ".toByteArray());
}