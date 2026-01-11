package me.sweettie.oma.network.udp

import android.net.Network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
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

    public var currentOnReceiveCallback: (ByteArray, Int) -> Unit = { h, h2 -> }
    private val job = SupervisorJob()
    override val coroutineContext = job + ioDispatcher

    public var socket: DatagramSocket? = null

    private var receiveJob: Job? = null
    var isInit = false
    fun startListening(onReceive: (ByteArray, Int) -> Unit) {
        if (receiveJob?.isActive == true) return
        socket = DatagramSocket()

        receiveJob = launch {
            val buf = ByteArray(bufferSize)
            val packet = DatagramPacket(buf, buf.size)
            try {
                while (isActive) {
                    socket?.receive(packet)
                    val data = packet.data.copyOf(packet.length)
                    onReceive(data, packet.length)
                    isInit = true
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun onNetworkAvailable(network: Network) {
        println("onNetworkAvailable called, isInit=$isInit, host=$host, port=$port")
        if (!isInit) return

        launch {
            try {
                try {
                    receiveJob?.cancelAndJoin()
                } catch (_: Exception) {
                }
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                socket = null

                // валидация порта
                if (port !in 0..65535) {
                    println("Invalid port: $port")
                    return@launch
                }

                val newSocket = DatagramSocket(null).apply { reuseAddress = true }
                try {
                    network.bindSocket(newSocket)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    newSocket.connect(java.net.InetSocketAddress(host, port))
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        newSocket.close()
                    } catch (_: Exception) {
                    }
                    return@launch
                }

                socket = newSocket

                // Перезапуск receive loop (используем currentOnReceiveCallback)
                startListening(currentOnReceiveCallback)
                println("Socket recreated, bound to network and listening")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onNetworkLost(network: Network) {
        receiveJob?.cancel()
        socket?.close()
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