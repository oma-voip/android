package me.sweettie.oma.network.udp

import android.os.SystemClock
import me.sweettie.oma.utils.startsWith
import java.nio.ByteBuffer
import java.util.UUID

object Packet {

    // рот ебал

    fun connectPacket(connectionId: ByteArray, data: ByteArray): ByteArray {
        return UdpHeaders.CONNECT_PREFIX.s + connectionId + data
    }

    fun pingPacket(): ByteArray {
        val ts = SystemClock.elapsedRealtime()
        val tsBytes = ByteBuffer.allocate(8).putLong(ts).array()
        return UdpHeaders.PING_MESSAGE.s + tsBytes
    }

    fun parseDataIdPacket(decrypted: ByteArray): Pair<UUID, ByteArray>? {
        val header = UdpHeaders.DATAID.s
        val idSize = 16
        if (!decrypted.startsWith(header)) return null
        if (decrypted.size < header.size + idSize) return null

        val idBytes = decrypted.copyOfRange(header.size, header.size + idSize)
        val payload = decrypted.copyOfRange(header.size + idSize, decrypted.size)

        val bb = ByteBuffer.wrap(idBytes)
        val msb = bb.long
        val lsb = bb.long
        val uuid = UUID(msb, lsb)

        return uuid to payload
    }

    fun parseDataWithIdOrData(decrypted: ByteArray): Pair<UUID?, ByteArray>? {
        val headerDataId = UdpHeaders.DATAID.s
        val headerData = UdpHeaders.DATA.s

        if (decrypted.size >= headerDataId.size + 16 && decrypted.startsWith(headerDataId)) {
            val idBytes = decrypted.copyOfRange(headerDataId.size, headerDataId.size + 16)
            val payload = decrypted.copyOfRange(headerDataId.size + 16, decrypted.size)
            val bb = ByteBuffer.wrap(idBytes)
            val msb = bb.long
            val lsb = bb.long
            val uuid = UUID(msb, lsb)
            return uuid to payload
        }

        if (decrypted.size >= headerData.size && decrypted.startsWith(headerData)) {
            val payload = decrypted.copyOfRange(headerData.size, decrypted.size)
            return null to payload
        }

        return null
    }
}