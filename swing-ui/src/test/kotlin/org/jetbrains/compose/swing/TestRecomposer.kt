package org.jetbrains.compose.swing

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds

/** A recomposer driven by frames the test sends, for tests that compose under a parent they own. */
internal class TestRecomposer(
    scope: CoroutineScope,
) {
    private val clock = BroadcastFrameClock()
    val recomposer = Recomposer(scope.coroutineContext + clock)
    private var frameTimeNanos = 0L

    init {
        scope.launch(clock) { recomposer.runRecomposeAndApplyChanges() }
    }

    /** Sends frames until the recomposer has nothing pending, failing after a bound. */
    suspend fun awaitIdle() {
        repeat(MAX_FRAMES) {
            Snapshot.sendApplyNotifications() // before each frame, as the frame clock does
            frameTimeNanos += (1.seconds / 60).inWholeNanoseconds
            clock.sendFrame(frameTimeNanos)
            yield()
            if (!recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()) return
        }
        throw AssertionError(
            "awaitIdle did not become idle after $MAX_FRAMES frames " +
                "(hasPendingWork=${recomposer.hasPendingWork}, " +
                "hasPendingChanges=${Snapshot.current.hasPendingChanges()}).",
        )
    }

    fun cancel() = recomposer.cancel()

    private companion object {
        const val MAX_FRAMES: Int = 10_000
    }
}
