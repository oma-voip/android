package me.sweettie.oma.network.udp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpClient(
    private val host: String,
    private val port: Int,
    private val bufferSize: Int = 4096,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = job + ioDispatcher

    public var socket: DatagramSocket? = null

    private var receiveJob: Job? = null

    /** Запускает сокет и начинает слушать входящие пакеты */
    fun startListening(onReceive: (ByteArray, Int) -> Unit) {
        if (receiveJob?.isActive == true) return  // уже слушаем

        socket = DatagramSocket() // bind к случайному порту

        receiveJob = launch {
            val buf = ByteArray(bufferSize)
            val packet = DatagramPacket(buf, buf.size)
            try {
                while (isActive) {
                    socket?.receive(packet)
                    val data = packet.data.copyOf(packet.length) // точные данные
                    onReceive(data, packet.length)
                }
            } catch (e: Exception) {
                // Обработка ошибок при получении
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /** Отправляет данные в указанный хост и порт */
    suspend fun send(data: ByteArray) = withContext(ioDispatcher) {
        try {
            val address = InetAddress.getByName(host)
            val packet = DatagramPacket(data, data.size, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    /** Останавливает слушание и закрывает сокет */
    fun stop() {
        receiveJob?.cancel()
        receiveJob = null
        socket?.close()
        socket = null
        coroutineContext.cancel()
    }

    companion object {
        fun parseAddress(addressWithPort: String): Pair<String, Int> {
            val parts = addressWithPort.split(":")
            require(parts.size == 2) { "Address must be in format 'ip:port'" }
            val ip = parts[0]
            val port = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid port")
            return ip to port
        }
    }
}