package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a [SwingComponentNodeListener] component may do with its node while it is told the node detached. */
class OnDetachedContractTest {
    @Test
    fun aListenerMayObserveAndRequestWhileItsNodeIsRemoved() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val panel = ObservingOnDetachPanel()
        setContent { if (shown) SwingNode(factory = { panel }) }
        awaitIdle()

        shown = false
        awaitIdle()

        assertDetachedWhileAttached(panel)
    }

    @Test
    fun aListenerMayObserveAndRequestWhileItsNodeIsDeactivated() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val panel = ObservingOnDetachPanel()
        setContent { ReusableContentHost(active) { SwingNode(factory = { panel }) } }
        awaitIdle()

        active = false
        awaitIdle()

        assertDetachedWhileAttached(panel)
    }

    /** No public node is reusable, so the holder is declared directly to reach its reuse callback. */
    @Test
    fun aListenerMayObserveAndRequestWhileItsNodeIsReused() = runComposeSwingTest {
        var key by mutableIntStateOf(0)
        val panel = ObservingOnDetachPanel()
        setContent {
            ReusableContent(key) {
                ReusableComposeNode<SwingNodeHolder<ObservingOnDetachPanel>, SwingApplier>(
                    factory = { CreatedNodeHolder(panel) },
                    update = {},
                )
            }
        }
        awaitIdle()

        key = 1
        awaitIdle()

        assertDetachedWhileAttached(panel)
    }

    @Test
    fun aListenerMayObserveAndRequestWhileItsNodeMovesToAnotherComposition() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        val panel = ObservingOnDetachPanel()
        val host = JPanel()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content = remember { movableContentOf { SwingNode(factory = { panel }) } }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val handle = host.setContent(parent = context) { if (!inOuter) content() }
        awaitIdle()

        inOuter = true
        awaitIdle()

        assertDetachedWhileAttached(panel)
        handle.dispose()
    }

    @Test
    fun aListenerMayObserveAndRequestWhileItsRootNodeIsDisposed() = runComposeSwingTest {
        val host = ObservingOnDetachPanel()
        setContent { SwingNode(factory = { JPanel(BorderLayout()).apply { add(host) } }) }
        awaitIdle()
        val handle = host.setContent {}
        awaitIdle()

        handle.dispose()

        assertDetachedWhileAttached(host)
    }

    private suspend fun ComposeSwingTest.assertDetachedWhileAttached(panel: ObservingOnDetachPanel) {
        val detached = panel.detachments.single()
        assertEquals(true, detached.attached, "the node reports attached while the listener is told it detached")
        assertEquals(null, detached.observeFailure, "the listener may observe reads under the node")
        assertEquals(null, detached.requestFailure, "the listener may request an after-validation action")
        panel.state.intValue = 1
        Snapshot.sendApplyNotifications()
        awaitIdle()
        assertEquals(0, panel.changes, "a read observed while detaching runs no callback once the node detached")
        assertEquals(0, panel.requested, "an action requested while detaching never runs")
    }

    /** What [ObservingOnDetachPanel] saw of its node as it was told the node detached. */
    private class Detachment(
        val attached: Boolean,
        val observeFailure: Throwable?,
        val requestFailure: Throwable?,
    )

    /**
     * Observes [state] and requests an action under each node it is told detached, recording how the calls went. The
     * request is made under the tree lock, so it is deferred rather than run at once.
     */
    private class ObservingOnDetachPanel :
        JPanel(),
        SwingComponentNodeListener {
        val state = mutableIntStateOf(0)
        val detachments = ArrayList<Detachment>()
        var changes = 0
        var requested = 0

        override fun onAttached(node: SwingComponentNode<*>) = Unit

        override fun onDetached(node: SwingComponentNode<*>) {
            detachments +=
                Detachment(
                    attached = node.isAttached,
                    observeFailure =
                        runCatching { node.observeReads({ changes++ }) { state.intValue } }.exceptionOrNull(),
                    requestFailure =
                        runCatching {
                            synchronized(treeLock) { node.requestAfterValidation { requested++ } }
                        }.exceptionOrNull(),
                )
        }
    }
}
