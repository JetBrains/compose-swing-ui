package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotApplyConflictException
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The conflict contract of a synchronously recomposed composition.
 *
 * A state object the composition writes, also written outside it after the composition's snapshot was taken,
 * cannot merge, so the apply fails. It reaches the caller as a [SnapshotApplyConflictException] instead
 * of being swallowed: silently rendering an uncommitted value would show state nothing else agrees with.
 *
 * The composition survives the failure: a further synchronous recompose drives new state to the Swing tree,
 * and the failed pass's composed state is still there to read, because that pass recomposed and applied
 * within one snapshot, leaving the composition with nothing computed but unapplied.
 */
class SynchronousRecomposeConflictTest {
    @Test
    fun aFailedApplyReachesTheCallerAndLeavesTheCompositionRecomposable() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val input = mutableStateOf("")
        val host = JPanel()
        val mount =
            SwingContentComposition.nested(parentContext) { observer -> SwingApplier(host, observer) }
        try {
            mount.setContent {
                val text = input.value
                if (text.isNotEmpty()) {
                    // Composed by the pass whose apply then fails, so its value survives to the next pass.
                    val kept = remember { mutableStateOf(KEPT) }
                    Label(text = "$text:${kept.value}")
                }
            }

            assertFailsWith<SnapshotApplyConflictException> {
                mount.recomposeSynchronously {
                    input.value = "conflicting"
                    // Written in its own snapshot off the global state, not the composition's: the write
                    // the composition's apply cannot merge with.
                    thread { Snapshot.withMutableSnapshot { input.value = "elsewhere" } }.join()
                }
            }

            mount.recomposeSynchronously { input.value = "settled" }
            val label =
                host.components
                    .filterIsInstance<JLabel>()
                    .single()
            assertEquals(
                "settled:$KEPT",
                label.text,
                "the composition should still recompose and materialize, reading what the failed pass composed",
            )
        } finally {
            mount.dispose()
        }
    }

    private companion object {
        const val KEPT = "kept"
    }
}
