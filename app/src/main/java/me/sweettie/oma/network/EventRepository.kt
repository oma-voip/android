package me.sweettie.oma.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import me.sweettie.oma.ui.main.MainViewModel
import me.sweettie.oma.utils.SessionManager
import proto.OmaOuterClass
import kotlin.coroutines.cancellation.CancellationException

class EventRepository {

    val events: Flow<OmaOuterClass.Event> = GrpcClient.subscribeToEvents()
//        .repeatOnComplete()
        .onStart { println("📡 [EventRepository] Подписка запущена") }
        .onEach { MainViewModel.Companion.handHandleEvent(it) }
        .repeatOnComplete()
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
            scope = SessionManager.applicationScope,
            started = SharingStarted.WhileSubscribed(100L),
            replay = 0
        )



    private fun Flow<OmaOuterClass.Event>.retryOnFailure(): Flow<OmaOuterClass.Event> {
        println("TRY TO WAKE UP AFTER FAIL")
        return this.retryWhen { cause, attempt ->
            println("CAUSE: $cause")
            if (cause is CancellationException) return@retryWhen false
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
            println("TRY TO WAKE UP AFTER FAIL 2 MEGA ${delayMillis}")

            collect { emit(it) }
            delay(delayMillis)
        } catch (e: Throwable) {
            throw e
        }
    }
}