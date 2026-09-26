package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Container
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * A failure raised while laying out the tree ends the drain loop and surfaces from the gate. Without
 * that exit, the event dispatch thread stays parked in the loop; asserting on elapsed time instead of
 * a tight [runComposeSwingTest] timeout keeps the test from depending on AWT's idle-shutdown timing.
 * Only the pass that adds the failing component is timed, after a first composition has run.
 */
class DrainQueueValidateFailureTest {
    @Test
    fun aFailureDuringValidateStillEndsTheDrainLoopPromptly() = runComposeSwingTest {
        var refused by mutableStateOf(false)
        setContent {
            SwingNode(factory = { Container() })
            if (refused) SwingNode(factory = { RefusesToLayOut() })
        }

        refused = true
        val elapsed = measureTime { assertFailsWith<IllegalStateException> { awaitIdle() } }

        assertTrue(elapsed < 500.milliseconds, "drain loop took $elapsed to end")
    }
}

private class RefusesToLayOut : Container() {
    override fun doLayout() {
        error("layout is refused")
    }
}
