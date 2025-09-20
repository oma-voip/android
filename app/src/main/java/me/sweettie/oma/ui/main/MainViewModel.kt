package me.sweettie.oma.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.sweettie.oma.network.EventRepository
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

            OmaOuterClass.Event.EventCase.ONLINESTATEUPDATED -> {
                val ev = event.onlineStateUpdated
                SessionManager.updateOnlineInfo(ev)
            }

            OmaOuterClass.Event.EventCase.JOINSPACE -> {
                val space = event.joinSpace
                println("🚀 JOIN_SPACE: ${space.id} by ${space.userId}")
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
                val channelId = event.channelJoined.channelId
                println("🚪 CHANNEL_JOINED: $channelId")
                _uiState.value = UiState.ChannelJoined(channelId)
            }

            OmaOuterClass.Event.EventCase.CHANNELLEFT -> {
                val channelId = event.channelLeft.channelId
                println("🚪 CHANNEL_LEFT: $channelId")
                _uiState.value = UiState.ChannelLeft(channelId)
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
        data class ChannelJoined(val channelId: String) : UiState()
        data class ChannelLeft(val channelId: String) : UiState()
    }
}