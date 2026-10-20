package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs a holder's database writes one at a time, in the order the holder asked for them. A write is started on
 * [scope], the app's event thread, and suspends there while the database does the work on its I/O dispatcher.
 *
 * A write that throws does not stop the later ones. The queue keeps the first such exception as [failure].
 *
 * Call it from the event thread only.
 */
internal class WriteQueue(
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val latest = HashMap<Any, suspend () -> Unit>()

    /** The first exception a write threw, kept for the rest of the run; `null` while every write has succeeded. */
    var failure by mutableStateOf<Throwable?>(null)
        private set

    fun write(block: suspend () -> Unit) {
        scope.launch {
            lock.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    // Safe: the exception is recorded as the failure and shown, never dropped. The database throws
                    // exceptions of no common type (SQL, I/O, closed database), so no narrower catch is possible.
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    if (failure == null) failure = e
                }
            }
        }
    }

    /**
     * Like [write], but a write under the same [key] that has not started yet is replaced by this one, so a burst of
     * changes (a divider drag) stores only the last.
     */
    fun writeLatest(
        key: Any,
        block: suspend () -> Unit,
    ) {
        val isQueued = key in latest
        latest[key] = block
        if (!isQueued) write { latest.remove(key)?.invoke() }
    }

    /** Returns once every write asked for before the call has finished, with the queue's [failure], if any. */
    suspend fun awaitWrites(): Throwable? {
        scope.launch { lock.withLock { } }.join()
        return failure
    }
}
