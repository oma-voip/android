package me.sweettie.oma.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import proto.OmaOuterClass

class EventRepository {
    val events: Flow<OmaOuterClass.Event> = GrpcClient.subscribeToEvents()
        .repeatOnComplete()
        .onStart { println("📡 [EventRepository] Подписка запущена") }
        .retryOnFailure()
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
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.WhileSubscribed(5000), // ← останавливается, если нет подписчиков >5 сек
            replay = 0
        )


    private fun Flow<OmaOuterClass.Event>.retryOnFailure(): Flow<OmaOuterClass.Event> {
        return this.retryWhen { cause, attempt ->
            println("Subscription failed (attempt $attempt): $cause")
            delay(1000)
            true
        }.catch { error ->
            println("Subscription permanently failed: $error")
            throw error
        }
    }


}


fun <T> Flow<T>.repeatOnComplete(delayMillis: Long = 1000L): Flow<T> = flow {
    while (true) {
        try {
            collect { emit(it) }
            delay(delayMillis)
        } catch (e: Throwable) {
            throw e
        }
    }
}