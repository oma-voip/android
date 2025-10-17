package me.sweettie.oma.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.sweettie.oma.data.Server
import me.sweettie.oma.network.EventRepository
import me.sweettie.oma.network.GrpcClient
import me.sweettie.oma.utils.SessionManager
import proto.OmaOuterClass

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
                println("+ VOICE_RELEY_READY | Space = ${event.voiceRelayReady.spaceId} | Peer id = ${event.voiceRelayReady.channelId} | Addr. = ${event.voiceRelayReady.address} | Key = ${event.voiceRelayReady.key}")
                _uiState.value = UiState.VoiceRelayReady(event.voiceRelayReady)
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
                message.attachmentsList.forEach {
                    println("File with id ${it.id} and content.size() = ${it.preview.content.size()}")
                }
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
                val iter = t.iterator()
                while (iter.hasNext()) {
                    val member = iter.next()
                    if (member.id == channel.userId) {
                        iter.remove()
                        deleted = true
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
        data class VoiceRelayReady(val voiceRelay: OmaOuterClass.VoiceRelayReady) : UiState()
    }
}