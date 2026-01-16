package me.sweettie.oma.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import me.sweettie.oma.utils.SessionManager
import proto.OmaOuterClass

class EventRepository {
    val events: Flow<OmaOuterClass.Event> = GrpcClient.subscribeToEvents()
        .repeatOnComplete(onConnected = {
            SessionManager.needForceUpdate = true
            println("✅ Подключение установлено — получена первая секция")
        })
        .retryOnFailure() // <- теперь retry срабатывает для всей repeatOnComplete
        .onStart {
            println("📡 [EventRepository] Подписка запущена")
        }
        .onCompletion { cause ->
            if (cause != null) {
                println("⚠️ [EventRepository] Подписка завершена с ошибкой: $cause")
            } else {
                println("✅ [EventRepository] Подписка завершена")
            }
        }
        .catch { error ->
            println("❌ [EventRepository] Необработанная ошибка: $error")
            throw error
        }
        .shareIn(
            scope = SessionManager.appScope,
            started = SharingStarted.WhileSubscribed(100L),
            replay = 1
        )


    private fun Flow<OmaOuterClass.Event>.retryOnFailure(): Flow<OmaOuterClass.Event> {
        return this.retryWhen { cause, attempt ->
            SessionManager.waitingNetworkFunction?.invoke()
            println("Subscription failed (attempt $attempt): $cause")
            delay(1000)
            true
        }.catch { error ->
            println("Subscription permanently failed: $error")
            throw error
        }
    }


}


fun <T> Flow<T>.repeatOnComplete(
    delayMillis: Long = 1000L,
    onConnected: suspend () -> Unit = {}
): Flow<T> = flow {
    while (true) {
        var connected = false
        try {
            collect { value ->
                if (!connected) {
                    connected = true
                    onConnected() // вызываем при первой полученной записи в этой сессии
                }
                emit(value)
            }
            // сюда попадём, когда upstream корректно завершился
            delay(delayMillis)
        } catch (e: Throwable) {
            // при ошибке цикл прервётся вверх по стэку (или можно реализовать backoff и retry)
            throw e
        }
    }
}