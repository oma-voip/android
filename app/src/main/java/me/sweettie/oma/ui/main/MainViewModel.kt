package me.sweettie.oma.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.sweettie.oma.data.Server
import me.sweettie.oma.network.EventRepository
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.network.udp.Key
import me.sweettie.oma.network.udp.Packet
import me.sweettie.oma.network.udp.UdpClient
import me.sweettie.oma.utils.HexToByteArray
import me.sweettie.oma.utils.SessionManager
import me.sweettie.oma.utils.toByteArray
import proto.OmaOuterClass
import java.util.UUID

class MainViewModel : ViewModel() {

    private val eventRepository = EventRepository()

    // Состояние для UI
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        // Подписываемся на события
        eventRepository.events
            .onEach { event -> handleEvent(event) }
            .launchIn(viewModelScope) // ← запускает корутину, отменяет при onCleared()
    }

    private fun handleEvent(event: OmaOuterClass.Event) {
        println("New Event: ${event.eventCase}")
        when (event.eventCase) {

            OmaOuterClass.Event.EventCase.VOICERELAYREADY -> {
                val vrr = event.voiceRelayReady
                val connectionUUID = UUID.fromString(vrr.connectionId)
                val spaceUUID = UUID.fromString(vrr.spaceId)
                val channelUUID = UUID.fromString(vrr.channelId)
                val key = Key(vrr.key.toByteArray())
                val pairData = UdpClient.parseAddress(vrr.address)
                val udp = UdpClient(pairData.first, pairData.second)
                udp.startListening { data, i ->
                    val message = data.toString(Charsets.UTF_8)
                    println("Received: $message")
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(2000)
                    println("2")
                    // todo: декодировать токен (являющийся хекс строкой) (длинна 32 байта)
                    val packet = Packet.connectPacket(
                        connectionUUID.toByteArray(),
                        key.encrypt(SessionManager.token!!.HexToByteArray() + spaceUUID.toByteArray() + channelUUID.toByteArray())
                    )
                    udp.send(packet)
                    println("2")
                    delay(2000)
                    println("SOcket: " + udp.socket!!.isConnected)

                }


                println("+ VOICE_RELEY_READY | Space = ${event.voiceRelayReady.spaceId} | Peer id = ${event.voiceRelayReady.channelId} | Addr. = ${event.voiceRelayReady.address} | Key = ${event.voiceRelayReady.key}")
            }

            OmaOuterClass.Event.EventCase.ONLINESTATEUPDATED -> {
                val ev = event.onlineStateUpdated
                println("Online status update")
                SessionManager.updateOnlineInfo(ev.userId, ev.online)
            }

            OmaOuterClass.Event.EventCase.JOINSPACE -> {
                val space = event.joinSpace
                println("🚀 JOIN_SPACE: ${space.id} by ${space.userId}")
                CoroutineScope(Dispatchers.IO).launch {
                    if (SessionManager.getServerBySpaceId(space.id) == null) {
                        val r = GrpcClient.getSpace(space.id) ?: return@launch
                        SessionManager.servers.add(Server(r.space))
                    } else {
                        SessionManager.getServerBySpaceId(space.id)!!.loadAllNecessaryInfo()
                    }
                }
                _uiState.value = UiState.ServerJoined(space.id, space.userId)
            }


            OmaOuterClass.Event.EventCase.CHANNELCREATED -> {
                val channel = event.channelCreated

                println("🆕 CHANNEL_CREATED: ${channel.channelId} in ${channel.spaceId}")
                _uiState.value = UiState.ChannelCreated(channel)
            }

            OmaOuterClass.Event.EventCase.MESSAGERECEIVED -> {
                val message = event.messageReceived.message
                println("💬 MESSAGE_RECEIVED: ${message.text} from ${message.userId}")
                _uiState.value = UiState.NewMessage(event.messageReceived)
            }

            OmaOuterClass.Event.EventCase.CHANNELJOINED -> {
                val channel = event.channelJoined
                println("🚪 CHANNEL_JOINED: ${channel.channelId}")
                val t = SessionManager.channelMembers.getOrDefault(channel.channelId, ArrayList())
                val vm = OmaOuterClass.VoiceMember.newBuilder().setId(channel.userId).build()
                t.add(vm)
                SessionManager.channelMembers[channel.channelId] = t
                _uiState.value = UiState.ChannelJoined(channel)
            }

            OmaOuterClass.Event.EventCase.CHANNELLEFT -> {
                val channel = event.channelLeft
                println("🚪 CHANNEL_LEFT: ${channel.channelId}")
                val t = SessionManager.channelMembers.getOrDefault(channel.channelId, ArrayList())
                var deleted = false
                t.forEach {
                    if (it.id.equals(channel.userId)) {
                        t.remove(it)
                        deleted = true
                        return@forEach
                    }
                }

                if (deleted) {
                    SessionManager.channelMembers[channel.channelId] = t
                }
                _uiState.value = UiState.ChannelLeft(channel)
            }

            OmaOuterClass.Event.EventCase.CHANNELMEMBERS -> {
                SessionManager.channelMembers[event.channelMembers.channelId] =
                    ArrayList(event.channelMembers.membersList)
                _uiState.value = UiState.ChannelMembers(event.channelMembers)
            }

            OmaOuterClass.Event.EventCase.KEEPALIVE -> {}

            else -> {
                println("❓ Необработанное событие: ${event.eventCase}")
            }
        }
    }

    sealed class UiState {
        object Idle : UiState()
        data class ServerJoined(val spaceId: String, val userId: String) : UiState()
        data class ChannelCreated(val channel: OmaOuterClass.ChannelCreated) : UiState()
        data class NewMessage(val message: OmaOuterClass.MessageReceived) : UiState()
        data class ChannelJoined(val channel: OmaOuterClass.ChannelJoined) : UiState()
        data class ChannelLeft(val channel: OmaOuterClass.ChannelLeft) : UiState()
        data class ChannelMembers(val channelMembers: OmaOuterClass.ChannelMembers) : UiState()
    }
}