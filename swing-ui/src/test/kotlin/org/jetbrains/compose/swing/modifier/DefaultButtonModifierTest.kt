package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.setContent
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * End-to-end tests for the `defaultButton` modifier mounted under a real [JRootPane]. They assert the
 * observable wiring - the root pane's default button - that pressing Enter would activate, which a bare
 * `JPanel` test root cannot express because it has no root pane.
 */
class DefaultButtonModifierTest {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val rootPane: JRootPane = onEdt { JRootPane() }
    private val root: Container = onEdt { rootPane.contentPane }
    private var handle: DisposableHandle? = null
    private var frameTimeNanos = 0L

    init {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    @AfterTest
    fun tearDown() {
        onEdt { handle?.dispose() }
        recomposer.cancel()
        scope.cancel()
    }

    private fun setContent(content: @Composable () -> Unit) {
        onEdt { handle = root.setContent(parent = recomposer, content = content) }
        waitForIdle()
    }

    /** The single button the composition mounted under the root pane's content pane. */
    private fun theButton(): JButton = onEdt {
        fun find(component: Component): JButton? = when {
            component is JButton -> component
            component is Container -> component.components.firstNotNullOfOrNull(::find)
            else -> null
        }
        find(root) ?: error("the composition mounted no button")
    }

    @Test
    fun defaultButtonBecomesRootPaneDefault() {
        setContent {
            Button("OK", modifier = SwingModifier.defaultButton())
        }
        assertSame(
            theButton(),
            onEdt { rootPane.defaultButton },
            "the modifier should make the button the root pane default",
        )
    }

    @Test
    fun clearingDefaultButtonReleasesRootPaneDefault() {
        var isDefault by mutableStateOf(true)
        setContent {
            Button("OK", modifier = SwingModifier.defaultButton(isDefault))
        }
        assertSame(
            theButton(),
            onEdt { rootPane.defaultButton },
            "the button should start as the root pane default",
        )

        onEdt { isDefault = false }
        waitForIdle()
        assertNull(onEdt { rootPane.defaultButton }, "clearing the flag should release the root pane default")
    }

    @Test
    fun removingDefaultButtonModifierReleasesRootPaneDefault() {
        var present by mutableStateOf(true)
        setContent {
            Button("OK", modifier = if (present) SwingModifier.defaultButton() else SwingModifier)
        }
        assertSame(
            theButton(),
            onEdt { rootPane.defaultButton },
            "the button should start as the root pane default",
        )

        onEdt { present = false }
        waitForIdle()
        // The element left the chain, so its reset releases the root pane's default button.
        assertNull(onEdt { rootPane.defaultButton }, "removing the modifier should release the root pane default")
    }

    private fun waitForIdle() {
        var iterations = 0
        while (true) {
            onEdt { Snapshot.sendApplyNotifications() }
            frameTimeNanos += FRAME_INTERVAL_NANOS
            clock.sendFrame(frameTimeNanos)
            // invokeAndWait drains the EDT queue, running the modifier's deferred root-pane resolution.
            SwingUtilities.invokeAndWait { }
            if (!recomposer.hasPendingWork && !Snapshot.current.hasPendingChanges()) return
            if (++iterations >= MAX_IDLE_FRAMES) {
                throw AssertionError("waitForIdle did not settle after $MAX_IDLE_FRAMES frames.")
            }
        }
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var outcome: Result<T>? = null
        SwingUtilities.invokeAndWait { outcome = runCatching(action) }
        return checkNotNull(outcome) { "EDT action did not run." }.getOrThrow()
    }

    private companion object {
        const val FRAME_INTERVAL_NANOS: Long = 16_666_667L
        const val MAX_IDLE_FRAMES: Int = 10_000
    }
}
