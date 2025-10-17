package me.sweettie.oma.network.services.voip

import android.telecom.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoipConnection : Connection() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onAnswer() {
        super.onAnswer()
        setActive()
        // Запускай приём и передачу голоса в сервисе
        startVoiceStreaming()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        // Останавливаем голосовую передачу и завершаем звонок
        stopVoiceStreaming()
//        setDisconnected()
        destroy()
    }

    private fun startVoiceStreaming() {
        // Здесь запусти UdpClient, обмен Opus с микрофона
        scope.launch {
            // Твой UDP клиент или сервис — стартуй трансляцию
        }
    }

    private fun stopVoiceStreaming() {
        scope.cancel()
        // Останови UDP клиент, освободи ресурсы
    }
}