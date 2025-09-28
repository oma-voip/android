package me.sweettie.oma.network.udp

object Packet {

    // рот ебал

    fun connectPacket(connectionId: ByteArray, data: ByteArray): ByteArray {
        return UdpHeaders.CONNECT_PREFIX.s + connectionId + data
    }
}