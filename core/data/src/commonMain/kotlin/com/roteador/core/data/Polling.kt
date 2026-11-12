package com.roteador.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/**
 * Runs [block] now and then every [period], emitting each outcome. Failures are
 * emitted, not thrown, so one timeout does not stop the stream. Polling stops
 * when the collector goes away (screens collect with `WhileSubscribed`).
 */
fun <T> pollEvery(period: Duration, block: suspend () -> T): Flow<Result<T>> = flow {
    while (true) {
        emit(runCatchingCancellable { block() })
        delay(period)
    }
}

suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
