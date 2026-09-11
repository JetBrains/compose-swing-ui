package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.key
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.lang.ref.WeakReference
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [SwingModifier.ComponentNode] is attached to the composition its component stands in: its
 * `coroutineScope` runs on that composition's clock, and [observeReads] reports changes for as long as the
 * node stays attached.
 */
class ModifierNodeTest {
    @Test
    fun aComponentNodeCoroutineScopeIsDrivenByTheHarnessClockAndCancelledOnDetach() = runComposeSwingTest {
        mainClock.autoAdvance = false
        var frames = 0
        var present by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = if (present) SwingModifier then FrameCountingElement { frames++ } else SwingModifier,
            )
        }

        assertEquals(0, frames, "a coroutine waiting for a frame must not run before one is sent")
        mainClock.advanceTimeByFrame()
        assertEquals(1, frames)
        mainClock.advanceTimeByFrame()
        assertEquals(2, frames)

        present = false
        awaitIdle()
        // A coroutine parked in withFrameNanos may be resumed with the cancellation on a later frame.
        mainClock.advanceTimeByFrame()
        val framesAtDetach = frames
        mainClock.advanceTimeByFrame()

        assertEquals(framesAtDetach, frames, "detaching the node must cancel what its coroutine scope was running")
    }

    @Test
    fun aDetachedNodeHearsNoChangeToWhatItObserved() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        var changes = 0
        var present by mutableStateOf(true)
        val observing = SwingModifier then ObservingElement(watched::intValue) { changes++ }
        setContent {
            SwingNode(factory = { JPanel() }, modifier = if (present) observing else SwingModifier)
        }

        watched.intValue = 1
        awaitIdle()
        assertEquals(1, changes, "an attached node hears a change to what it read")

        present = false
        awaitIdle()
        watched.intValue = 2
        awaitIdle()
        assertEquals(1, changes, "a detached node must not hear a change to what it read")
    }

    @Test
    fun aDetachedNodesCallbackIsNotHeldByTheComposition() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        var present by mutableStateOf(true)
        var probe: ProbeNode? = null
        setContent {
            if (present) SwingNode(factory = { JPanel() }, modifier = SwingModifier then ProbeElement { probe = it })
        }
        val callback = observeUnderAFreshCallback(checkNotNull(probe), watched)
        probe = null

        present = false
        awaitIdle()

        assertTrue(isCollected(callback), "a detached node's callback must not stay reachable from the composition")
    }

    @Test
    fun aDeactivatedNodesCallbackIsNotHeldByTheComposition() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        var active by mutableStateOf(true)
        var probe: ProbeNode? = null
        setContent {
            ReusableContentHost(active) {
                SwingNode(factory = { JPanel() }, modifier = SwingModifier then ProbeElement { probe = it })
            }
        }
        val callback = observeUnderAFreshCallback(checkNotNull(probe), watched)
        probe = null

        active = false
        awaitIdle()

        assertTrue(isCollected(callback), "a deactivated node's callback must not stay reachable from the composition")
    }

    /**
     * A change applied on another thread is reported by a task posted to the event dispatch thread. A node
     * that detaches before that task runs must not hear it, even while another node keeps observing.
     */
    @Test
    fun aNodeDetachedBeforeAPostedChangeReportIsNotToldOfIt() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        var changes = 0
        var present by mutableStateOf(true)
        val observing =
            SwingModifier then
                ObservingElement(watched::intValue, onDetach = {
                    thread { Snapshot.withMutableSnapshot { watched.intValue = 1 } }.join()
                }) { changes++ }
        val other = mutableIntStateOf(0)
        setContent {
            SwingNode(factory = { JPanel() }, modifier = if (present) observing else SwingModifier)
            SwingNode(factory = { JPanel() }, modifier = SwingModifier then ObservingElement(other::intValue) {})
        }

        present = false
        awaitIdle()

        assertEquals(0, changes, "a change reported after the node detached must not reach it")
    }

    @Test
    fun aNodeInAMenuObservesItsReads() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        var changes = 0
        composeMenu {
            MenuItem(
                text = "Open",
                onClick = {},
                modifier = SwingModifier then ObservingElement(watched::intValue) { changes++ },
            )
        }

        watched.intValue = 1
        awaitIdle()

        assertEquals(1, changes, "a node in a menu composition hears a change to what it read")
    }

    @Test
    fun aChangeAppliedOffTheEventDispatchThreadIsReportedOnIt() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        val reportedOnEdt = mutableListOf<Boolean>()
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    SwingModifier then
                        ObservingElement(watched::intValue) { reportedOnEdt += SwingUtilities.isEventDispatchThread() },
            )
        }

        thread { Snapshot.withMutableSnapshot { watched.intValue = 1 } }.join()
        awaitIdle()

        assertEquals(listOf(true), reportedOnEdt, "a change applied on another thread must be reported on the EDT")
    }

    @Test
    fun aComponentNodeIsResetThenDetachedWhenItsHolderIsReused() = runComposeSwingTest {
        val events = mutableListOf<String>()
        var item by mutableIntStateOf(0)
        val modifier = SwingModifier then RecordingElement(events)
        setContent {
            ReusableContent(item) {
                ReusableComposeNode<SwingNodeHolder<JPanel>, SwingApplier>(
                    factory = { SwingNodeHolder(JPanel()) },
                    update = { SwingNodeUpdater(this).applyModifier(CompositionLocalMap.Empty, modifier) },
                )
            }
        }
        assertEquals(listOf("onAttach"), events)

        item = 1
        awaitIdle()

        assertEquals(
            listOf("onAttach", "onReset", "onDetach", "onAttach"),
            events,
            "reuse must reset a node before detaching it, and the reused holder attaches its chain again",
        )
    }

    @Test
    fun aComponentNodeIsResetThenDetachedWhenItsReusableContentHostDeactivates() = runComposeSwingTest {
        val events = mutableListOf<String>()
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active) {
                SwingNode(factory = { JPanel() }, modifier = SwingModifier then RecordingElement(events))
            }
        }
        events.clear()

        active = false
        awaitIdle()

        assertEquals(listOf("onReset", "onDetach"), events, "deactivation resets a node before detaching it")
    }

    @Test
    fun everyNodeOfAChainIsAttachedWhileAnyOfItsNodesAttachesOrDetaches() = runComposeSwingTest {
        val chain = mutableListOf<ChainNode>()
        val sightings = mutableListOf<String>()
        var generation by mutableIntStateOf(0)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    SwingModifier.key(generation) then
                        ChainElement("first", chain, sightings) then
                        ChainElement("second", chain, sightings) then
                        ChainElement("third", chain, sightings, additive = true),
            )
        }
        assertEquals(
            listOf("onAttach first: 3 of 3", "onAttach second: 3 of 3", "onAttach third: 3 of 3"),
            sightings,
            "every node of the chain must be attached before the first onAttach runs",
        )
        sightings.clear()

        generation = 1
        awaitIdle()

        assertEquals(
            listOf(
                "onDetach second: 3 of 3",
                "onDetach first: 3 of 3",
                "onDetach third: 3 of 3",
                "onAttach first: 3 of 3",
                "onAttach second: 3 of 3",
                "onAttach third: 3 of 3",
            ),
            sightings,
            "a key change must run every onDetach before any node detaches, then attach the new chain whole",
        )
    }

    @Test
    fun nodesEnteringAStandingChainAreEachMarkedAndRunOnTheirOwn() = runComposeSwingTest {
        val chain = mutableListOf<ChainNode>()
        val sightings = mutableListOf<String>()
        var grown by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier =
                    if (grown) {
                        SwingModifier then
                            ChainElement("first", chain, sightings) then
                            ChainElement("second", chain, sightings)
                    } else {
                        SwingModifier
                    },
            )
        }

        grown = true
        awaitIdle()

        assertEquals(
            listOf("onAttach first: 1 of 1", "onAttach second: 2 of 2"),
            sightings,
            "a node entering a standing chain must run onAttach before the next one is created and marked",
        )
    }

    @Test
    fun twoReadSetsOfOneNodeUnderTwoCallbacksAreObservedApart() = runComposeSwingTest {
        val first = mutableIntStateOf(0)
        val second = mutableIntStateOf(0)
        val unrelated = mutableIntStateOf(0)
        val heard = mutableListOf<String>()
        val byFirst: (ProbeNode) -> Unit = { heard += "first" }
        val bySecond: (ProbeNode) -> Unit = { heard += "second" }
        val node = composeProbe()
        node.observeReads(byFirst) { first.intValue }
        node.observeReads(bySecond) { second.intValue }

        // Moves the global snapshot forward, so the next observeReads replaces reads recorded under an earlier
        // snapshot even when nothing else applied one in between.
        unrelated.intValue = 1
        Snapshot.sendApplyNotifications()
        node.observeReads(byFirst) { first.intValue }
        second.intValue = 1
        Snapshot.sendApplyNotifications()

        assertEquals(listOf("second"), heard, "observing under one callback must keep the other callback's reads")
    }

    @Test
    fun observingAgainUnderACallbackReplacesOnlyTheReadsRecordedUnderIt() = runComposeSwingTest {
        val dropped = mutableIntStateOf(0)
        val kept = mutableIntStateOf(0)
        val unrelated = mutableIntStateOf(0)
        val heard = mutableListOf<String>()
        val onChanged: (ProbeNode) -> Unit = { heard += "changed" }
        val node = composeProbe()
        node.observeReads(onChanged) { dropped.intValue }

        unrelated.intValue = 1
        Snapshot.sendApplyNotifications()
        node.observeReads(onChanged) { kept.intValue }
        dropped.intValue = 1
        Snapshot.sendApplyNotifications()
        assertEquals(emptyList(), heard, "a read the latest call did not make must no longer be observed")

        kept.intValue = 1
        Snapshot.sendApplyNotifications()
        assertEquals(listOf("changed"), heard, "the latest call's read must be observed")
    }

    @Test
    fun aComponentNodeIsDetachedWithoutResettingWhenItsHolderIsReleased() = runComposeSwingTest {
        val events = mutableListOf<String>()
        var present by mutableStateOf(true)
        setContent {
            if (present) SwingNode(factory = { JPanel() }, modifier = SwingModifier then RecordingElement(events))
        }
        events.clear()

        present = false
        awaitIdle()

        assertEquals(listOf("onDetach"), events, "a released node is detached, not reset")
    }
}

/** Composes a panel carrying a [ProbeNode] and returns the attached node. */
private fun ComposeSwingTest.composeProbe(): ProbeNode {
    var probe: ProbeNode? = null
    setContent { SwingNode(factory = { JPanel() }, modifier = SwingModifier then ProbeElement { probe = it }) }
    return checkNotNull(probe)
}

private class ProbeElement(
    private val created: (ProbeNode) -> Unit,
) : SwingModifier.NodeElement<Component, ProbeNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ProbeNode = ProbeNode().also(created)

    override fun update(node: ProbeNode) = Unit

    override fun equals(other: Any?): Boolean = other is ProbeElement

    override fun hashCode(): Int = ProbeElement::class.hashCode()
}

private class ProbeNode : SwingModifier.ComponentNode<Component>()

/** Observes [watched] on [node] under a callback no one else holds, returning a weak reference to it. */
internal fun observeUnderAFreshCallback(
    node: SwingModifier.Node,
    watched: MutableIntState,
): WeakReference<Any> {
    val onChanged: (SwingModifier.Node) -> Unit = { watched.intValue }
    node.observeReads(onChanged) { watched.intValue }
    return WeakReference(onChanged)
}

/** Whether [reference] is cleared, asking for a collection several times, since one `System.gc()` is a hint. */
private suspend fun ComposeSwingTest.isCollected(reference: WeakReference<*>): Boolean {
    repeat(20) {
        if (reference.get() == null) return true
        System.gc()
        awaitIdle()
    }
    return reference.get() == null
}

/** A chain member recording, as it attaches and detaches, how many nodes of its own chain are attached. */
private class ChainElement(
    private val label: String,
    private val chain: MutableList<ChainNode>,
    private val sightings: MutableList<String>,
    override val additive: Boolean = false,
) : SwingModifier.NodeElement<Component, ChainNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val key: Any get() = label

    override fun create(): ChainNode {
        // A node of the chain this one replaces has detached by the time a new chain is created.
        chain.removeAll { !it.isAttached }
        return ChainNode(label, chain, sightings).also { chain += it }
    }

    override fun update(node: ChainNode) = Unit

    override fun equals(other: Any?): Boolean = other is ChainElement && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class ChainNode(
    private val label: String,
    private val chain: List<ChainNode>,
    private val sightings: MutableList<String>,
) : SwingModifier.ComponentNode<Component>() {
    override fun onAttach() {
        sightings += "onAttach $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }

    override fun onDetach() {
        sightings += "onDetach $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }
}

private class RecordingElement(
    private val events: MutableList<String>,
) : SwingModifier.NodeElement<Component, RecordingNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): RecordingNode = RecordingNode(events)

    override fun update(node: RecordingNode) = Unit

    override fun equals(other: Any?): Boolean = other is RecordingElement

    override fun hashCode(): Int = RecordingElement::class.hashCode()
}

private class RecordingNode(
    private val events: MutableList<String>,
) : SwingModifier.ComponentNode<Component>() {
    override fun onAttach() {
        events += "onAttach"
    }

    override fun onReset() {
        events += "onReset"
    }

    override fun onDetach() {
        events += "onDetach"
    }
}

private class FrameCountingElement(
    private val onFrame: () -> Unit,
) : SwingModifier.NodeElement<Component, FrameCountingNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): FrameCountingNode = FrameCountingNode(onFrame)

    override fun update(node: FrameCountingNode) = Unit

    override fun equals(other: Any?): Boolean = other is FrameCountingElement

    override fun hashCode(): Int = FrameCountingElement::class.hashCode()
}

private class FrameCountingNode(
    private val onFrame: () -> Unit,
) : SwingModifier.ComponentNode<Component>() {
    override fun onAttach() {
        coroutineScope.launch {
            while (true) withFrameNanos { onFrame() }
        }
    }
}

/** Reads [read] under observation when attached, and observes again each time it hears a change. */
private class ObservingElement(
    private val read: () -> Int,
    private val onDetach: () -> Unit = {},
    private val onChange: () -> Unit,
) : SwingModifier.NodeElement<Component, ObservingNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ObservingNode = ObservingNode(read, onDetach, onChange)

    override fun update(node: ObservingNode) = Unit

    override fun equals(other: Any?): Boolean = other is ObservingElement

    override fun hashCode(): Int = ObservingElement::class.hashCode()
}

private class ObservingNode(
    private val read: () -> Int,
    private val onDetach: () -> Unit,
    private val onChange: () -> Unit,
) : SwingModifier.ComponentNode<Component>(),
    ObserverModifierNode {
    override fun onAttach() = observe()

    override fun onDetach() = onDetach.invoke()

    override fun onObservedReadsChanged() {
        onChange()
        observe()
    }

    private fun observe() = observeReads { read() }
}
