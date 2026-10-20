package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Runs the app's turns in [scope], so they last as long as the app, not as long as the composable that started them.
 * One turn runs at a time.
 */
@Stable
internal class TurnRunner(
    private val scope: CoroutineScope,
) {
    /** The turn being streamed, if any. */
    var runningTurn by mutableStateOf<Job?>(null)
        private set

    val isRunning: Boolean get() = runningTurn != null

    /**
     * Starts [block] as the running turn; it ends when [block] returns, fails or is cancelled.
     * The block enters its cleanup even when Stop cancels it before the first dispatch.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun launch(block: suspend () -> Unit) {
        runningTurn =
            scope.launch(start = CoroutineStart.ATOMIC) {
                try {
                    block()
                } finally {
                    runningTurn = null
                }
            }
    }

    /** Cancels the running turn, if any. */
    fun stop() {
        runningTurn?.cancel()
    }

    /** Cancels the running turn, if any, and returns once it has ended. */
    suspend fun stopAndJoin() {
        runningTurn?.cancelAndJoin()
    }
}
