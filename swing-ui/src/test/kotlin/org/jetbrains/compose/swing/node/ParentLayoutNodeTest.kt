// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.captureParentContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.style.NestedProbe
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Component
import java.lang.ref.WeakReference
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParentLayoutNodeTest {
    @Test
    fun aModifierUpdateThatChangesKeysAndDropsAStyleDeclaresWithoutTheStyleLayoutElements() = runComposeSwingTest {
        val host = MeasuringHost()
        var dropped by mutableStateOf(false)
        setContent {
            StyledHost(host, "styled", MarkProperty) {
                SwingNode(
                    factory = { JLabel("a") },
                    modifier =
                        if (dropped) {
                            SwingModifier.mark("own", slot = 6)
                        } else {
                            SwingModifier.mark("own", slot = 5).styleable(NestedProbe, SwingStyle)
                        },
                )
            }
        }
        val label = host.components.single()
        assertEquals(listOf("own", "styled"), host.recording.tagsOf(label))

        dropped = true
        awaitIdle()

        assertEquals(listOf("own"), host.recording.tagsOf(label))
    }

    @Test
    fun aLayoutNodeElementCreatesAttachesThenUpdatesItsNodeOnce() {
        val owner = TestCompositionOwner()
        val root = JPanel(RecordingMeasurementLayout())
        val child = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        root.add(child.component)
        val events = mutableListOf<String>()

        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))
        child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))

        assertEquals(listOf("create(a)", "onAttach", "update(a)"), events)
        owner.dispose()
    }

    @Test
    fun aLayoutNodeElementUpdatesTheSameNodeWhenItsDeclarationChanges() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))

        val firstNode = child.declaration.parentLayoutElements.single()
        events.clear()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("b", events)))

        assertEquals(listOf("update(b)"), events, "a changed declaration of the same kind must not recreate the node")
        assertSame(firstNode, child.declaration.parentLayoutElements.single())
        owner.dispose()
    }

    @Test
    fun anUnchangedLayoutNodeElementIsNotUpdatedWhenASiblingChanges() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyModifierDiff(
            SwingModifier then RecordingLayoutNodeElement("a", events) then RecordingLayoutNodeElement("b", events),
        )
        events.clear()

        child.applyModifierDiff(
            SwingModifier then RecordingLayoutNodeElement("a", events) then RecordingLayoutNodeElement("c", events),
        )

        assertEquals(listOf("update(c)"), events, "only the slot whose declaration changed is updated")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeIsDetachedWhenItsDeclarationLeavesTheChain() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))
        events.clear()

        child.applyModifierDiff(SwingModifier)

        assertEquals(listOf("onDetach"), events)
        owner.dispose()
    }

    @Test
    fun theParentIsDeclaredToAgainOnlyWhenALayoutNodeAttachesIsRewrittenOrDetaches() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        root.add(child.component)
        child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))
        val events = mutableListOf<String>()
        val padding = TestParentLayoutElement("padding")

        fun declaresAgain(modifier: SwingModifier): Boolean {
            val calls = layout.declarations.size
            child.applyModifierDiff(modifier)
            return layout.declarations.size > calls
        }

        assertTrue(declaresAgain(SwingModifier then padding then RecordingLayoutNodeElement("a", events)), "attach")
        assertFalse(declaresAgain(SwingModifier then padding then RecordingLayoutNodeElement("a", events)), "unchanged")
        assertTrue(declaresAgain(SwingModifier then padding then RecordingLayoutNodeElement("b", events)), "rewrite")
        assertTrue(declaresAgain(SwingModifier then RecordingLayoutNodeElement("b", events) then padding), "reorder")
        assertTrue(declaresAgain(SwingModifier then padding), "detach")
        assertFalse(declaresAgain(SwingModifier then padding), "unchanged")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeWrittenInPlaceIsDeclaredToTheParentAgain() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child: SwingNodeHolder<Component> = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        root.add(child.component)
        child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))
        val keyed = KeyReadingElement()
        val events = mutableListOf<String>()
        child.applyDeclaredModifier(SwingModifier then keyed then RecordingLayoutNodeElement("a", events, key = "slot"))
        val node = child.declaration.parentLayoutElements.single()
        val declarations = layout.declarations.size
        val keyReads = keyed.keyReads
        events.clear()

        child.applyDeclaredModifier(SwingModifier then keyed then RecordingLayoutNodeElement("b", events, key = "slot"))

        assertEquals(keyReads, keyed.keyReads, "a layout node written in place must leave the modifier undiffed")

        assertEquals(listOf("update(b)"), events, "the slot's node must be written once with the new element")
        assertEquals(declarations + 1, layout.declarations.size, "the parent must be declared to once again")
        assertEquals(listOf(node), layout.declarations.last().elements, "the parent must be handed the same node")
        owner.dispose()
    }

    @ParameterizedTest(name = "grouped: {0}")
    @ValueSource(booleans = [false, true])
    fun aLayoutNodeWrittenInPlaceToAProtocolTheHostRefusesIsRefused(grouped: Boolean) = runComposeSwingTest {
        val host = MeasuringHost()
        val parentContext = captureParentContext()
        var protocol by mutableStateOf(MeasuringProtocol)
        val composition =
            OnDemandComposition(parentContext) {
                SwingNode(factory = { host }) {
                    if (grouped) {
                        VirtualSwingNode(SwingModifier.mark("group")) {
                            Label("a", SwingModifier then ProtocolLayoutNodeElement(protocol))
                        }
                    } else {
                        Label("a", SwingModifier then ProtocolLayoutNodeElement(protocol))
                    }
                }
            }
        try {
            assertEquals(listOf("a"), host.texts())

            host.recording.declarations.clear()
            assertFailsWith<IllegalStateException> {
                composition.recompose { protocol = parentProtocolOf("refusing parent") { false } }
            }
            assertEquals(emptyList(), host.recording.declarations)
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aLayoutElementALaterOneOfItsKeyReplacesWritesNothingWhereOnlyItChanges() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyDeclaredModifier(
            SwingModifier then RecordingLayoutNodeElement("a", events, key = "k") then
                RecordingLayoutNodeElement("b", events, key = "k"),
        )
        events.clear()

        child.applyDeclaredModifier(
            SwingModifier then RecordingLayoutNodeElement("c", events, key = "k") then
                RecordingLayoutNodeElement("b", events, key = "k"),
        )

        assertEquals(emptyList(), events, "the slot holds the later declaration, which did not change")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeWriteTellsTheParentEvenWhenItsComponentNodeDoesNotAutoInvalidate() {
        val owner = TestCompositionOwner()
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = CreatedNodeHolder(ListenerButton()).attachedTo(owner)
        root.add(child.component)
        child.declaration.attachedUnder(root, child.declaration.checkAttachableUnder(root))
        val events = mutableListOf<String>()

        fun declaresAgain(modifier: SwingModifier): Boolean {
            val calls = layout.declarations.size
            child.applyModifierDiff(modifier)
            return layout.declarations.size > calls
        }

        assertTrue(declaresAgain(SwingModifier.then(RecordingLayoutNodeElement("a", events))), "attach")
        assertTrue(
            declaresAgain(SwingModifier.then(RecordingLayoutNodeElement("b", events))),
            "a layout-node write must reach the parent even when its component node does not auto-invalidate",
        )
        owner.dispose()
    }

    @Test
    fun aDetachedLayoutNodesCallbackIsNotHeldByTheComposition() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val watched = mutableIntStateOf(0)
        var callback: WeakReference<Any>? = null
        val element =
            RecordingLayoutNodeElement("a", mutableListOf()) { callback = observeUnderAFreshCallback(it, watched) }
        SwingUtilities.invokeAndWait { child.applyModifierDiff(SwingModifier.then(element)) }

        child.applyModifierDiff(SwingModifier)

        val reference = checkNotNull(callback)
        // One System.gc() is a hint, so a collection is asked for several times.
        repeat(20) { if (reference.get() != null) System.gc() }
        assertNull(reference.get(), "a detached layout node's callback must not stay reachable from the composition")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeSurvivesTheChildMovingBetweenTwoCapableParents() {
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(JPanel()).attachedTo(owner))
        val firstHost = CreatedNodeHolder(JPanel(RecordingMeasurementLayout()))
        val secondHost = CreatedNodeHolder(JPanel(RecordingMeasurementLayout()))
        val child = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        val events = mutableListOf<String>()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertTopDown(0, firstHost)
            applier.insertBottomUp(0, firstHost)
            applier.insertTopDown(1, secondHost)
            applier.insertBottomUp(1, secondHost)
            applier.down(firstHost)
            applier.insertTopDown(0, child)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.up()
            applier.onEndChanges()

            val nodeUnderFirstHost = child.declaration.parentLayoutElements.single()
            events.clear()

            // A `movableContent` relocation: the runtime removes the child from the host it is leaving,
            // then hands it to the host it arrives at bottom-up before top-down - see SwingApplier's own
            // relocateChild helper in SwingApplierRegionTest. Neither call runs onRelease, onReuse or
            // onDeactivate on the same node instance, which is what an AndroidX `Modifier.Node` relies on to
            // survive a move.
            applier.onBeginChanges()
            applier.down(firstHost)
            applier.remove(0, 1)
            applier.up()
            applier.down(secondHost)
            applier.insertBottomUp(0, child)
            applier.insertTopDown(0, child)
            applier.up()
            applier.onEndChanges()

            assertSame(
                nodeUnderFirstHost,
                child.declaration.parentLayoutElements.single(),
                "the same node must keep standing for the child's declaration under its new parent",
            )
            assertEquals(
                emptyList<String>(),
                events,
                "an unchanged declaration reaching a new parent must not re-run create or update",
            )
        } finally {
            owner.dispose()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("layoutNodeKeyWrites")
    fun aLayoutNodeWrittenToAnotherNodesKeyDeclaresTheRetainedNodesValue(
        case: String,
        initial: SwingModifier,
        updated: SwingModifier,
        expected: List<String>,
    ) {
        val layout = RecordingMeasurementLayout()
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"), layout)
        child.applyDeclaredModifier(initial)

        child.applyDeclaredModifier(updated)

        assertEquals(
            expected,
            layout.declarations
                .last()
                .elements
                .map { (it as ValuedLayoutNode).value },
            case,
        )
        owner.dispose()
    }

    companion object {
        @JvmStatic
        fun layoutNodeKeyWrites(): List<Arguments> = listOf(
            Arguments.of(
                "updated to the key of the node that follows",
                SwingModifier then ValuedLayoutNodeElement("x", "a") then ValuedLayoutNodeElement("y", "b"),
                SwingModifier then ValuedLayoutNodeElement("x", "a") then ValuedLayoutNodeElement("x", "b2"),
                listOf("b2"),
            ),
            Arguments.of(
                "written to the key of the node that follows",
                SwingModifier then ValuedLayoutNodeElement("a", "A") then ValuedLayoutNodeElement("b", "B"),
                SwingModifier then ValuedLayoutNodeElement("b", "A2") then ValuedLayoutNodeElement("b", "B"),
                listOf("B"),
            ),
        )
    }

    @Test
    fun aLayoutNodeIsResetThenDetachedWhenItsSlotIsReusedForAnotherItem() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))
        events.clear()

        child.onReuse()

        assertEquals(listOf("onReset", "onDetach"), events, "reuse must drop the old item's state before detaching")
        assertTrue(child.declaration.parentLayoutElements.isEmpty(), "a reused slot keeps no stale node")
        owner.dispose()
    }

    @Test
    fun everyLayoutNodeOfAChainIsAttachedWhileAnyOfItsNodesIsResetOrDetachesOnReuse() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val chain = mutableListOf<SwingModifier.Node>()
        val sightings = mutableListOf<String>()
        val declared =
            SwingModifier then
                ChainLayoutNodeElement("first", chain, sightings) then
                ChainLayoutNodeElement("second", chain, sightings)
        child.applyModifierDiff(declared)
        sightings.clear()

        child.onReuse()

        assertEquals(
            listOf(
                "onReset first: 2 of 2",
                "onReset second: 2 of 2",
                "onDetach first: 2 of 2",
                "onDetach second: 2 of 2",
            ),
            sightings,
            "reuse must reset and then run every onDetach before any node detaches",
        )
        chain.clear()
        sightings.clear()

        child.applyModifierDiff(declared)

        assertEquals(
            listOf("onAttach first: 2 of 2", "onAttach second: 2 of 2"),
            sightings,
            "the reused holder must mark every node of its new chain attached before the first onAttach runs",
        )
        owner.dispose()
    }

    @Test
    fun layoutNodesEnteringAStandingChainAreEachMarkedAndRunOnTheirOwn() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val chain = mutableListOf<SwingModifier.Node>()
        val sightings = mutableListOf<String>()

        child.applyModifierDiff(SwingModifier)

        child.applyModifierDiff(
            SwingModifier then
                ChainLayoutNodeElement("first", chain, sightings) then
                ChainLayoutNodeElement("second", chain, sightings),
        )

        assertEquals(
            listOf("onAttach first: 1 of 1", "onAttach second: 2 of 2"),
            sightings,
            "a node entering a standing chain must run onAttach before the next one is created and marked",
        )
        owner.dispose()
    }

    @Test
    fun everyNodeOfBothFamiliesIsAttachedWhileAnyNodeOfTheModifierAttachesResetsOrDetaches() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val chain = mutableListOf<SwingModifier.Node>()
        val sightings = mutableListOf<String>()
        val modifier =
            SwingModifier then
                ChainLayoutNodeElement("layout", chain, sightings) then
                ChainComponentNodeElement("component", chain, sightings)

        child.applyModifierDiff(modifier)

        assertEquals(
            listOf("onAttach layout: 2 of 2", "onAttach component: 2 of 2"),
            sightings,
            "both families must be marked attached before the first onAttach of the modifier runs",
        )
        sightings.clear()

        child.onReuse()

        assertEquals(
            listOf(
                "onReset component: 2 of 2",
                "onReset layout: 2 of 2",
                "onDetach component: 2 of 2",
                "onDetach layout: 2 of 2",
            ),
            sightings,
            "reuse must reset, then run every onDetach of both families, before any node detaches",
        )
        chain.clear()
        child.applyModifierDiff(modifier)
        sightings.clear()

        child.onRelease()

        assertEquals(
            listOf("onDetach component: 2 of 2", "onDetach layout: 2 of 2"),
            sightings,
            "release must run every onDetach of both families before any node detaches",
        )
        owner.dispose()
    }

    @Test
    fun aLayoutNodeIsDetachedWithoutResettingWhenItsHolderLeavesTheCompositionForGood() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach"), events, "leaving the composition for good must not reset the node")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeIsResetThenDetachedWhenItsHolderIsParked() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        val events = mutableListOf<String>()
        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))
        events.clear()

        child.onDeactivate()

        assertEquals(listOf("onReset", "onDetach"), events, "parking a node resets it before detaching it")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeCoroutineScopeRunsWhileAttachedAndCancelsOnDetach() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, JButton("child"))
        var running = false
        var cancellation: Throwable? = null
        val element =
            RecordingLayoutNodeElement("a", mutableListOf()) {
                it.coroutineScope.launch {
                    running = true
                    try {
                        awaitCancellation()
                    } catch (cancelled: CancellationException) {
                        cancellation = cancelled
                        throw cancelled
                    } finally {
                        running = false
                    }
                }
            }
        child.applyModifierDiff(SwingModifier.then(element))
        assertTrue(running, "the node's own coroutine scope must run its launched work while attached")

        child.applyModifierDiff(SwingModifier)

        assertFalse(running, "detaching the node must cancel what its coroutine scope was running")
        assertTrue(cancellation is CancellationException, "the launched job must observe a cancellation, not just stop")
        owner.dispose()
    }
}

/** A listener component whose own node policy does not control layout-node invalidation. */
private class ListenerButton :
    JButton("child"),
    DeclaredNodesListener {
    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) = Unit
}

/** A component node recording, at each lifecycle step, how many nodes of its modifier are attached. */
private class ChainComponentNodeElement(
    private val label: String,
    private val chain: MutableList<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : SwingModifier.NodeElement<Component, ChainComponentNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ChainComponentNode = ChainComponentNode(label, chain, sightings).also { chain += it }

    override fun update(node: ChainComponentNode) = Unit

    override fun equals(other: Any?): Boolean = other is ChainComponentNodeElement && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class ChainComponentNode(
    private val label: String,
    private val chain: List<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : SwingModifier.ComponentNode<Component>() {
    override fun onAttach() = sight("onAttach")

    override fun onReset() = sight("onReset")

    override fun onDetach() = sight("onDetach")

    private fun sight(step: String) {
        sightings += "$step $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }
}

/**
 * A layout node element of one key whose update moves its node to [protocol]. Its node does not invalidate its parent,
 * so a parent measuring with it reads no update of it.
 */
private data class ProtocolLayoutNodeElement(
    private val protocol: ParentProtocol,
) : ParentLayoutNodeElement<ProtocolLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = protocol

    override val key: Any get() = "protocol"

    override fun create(): ProtocolLayoutNode = ProtocolLayoutNode(protocol)

    override fun update(node: ProtocolLayoutNode) {
        node.protocol = protocol
    }
}

private class ProtocolLayoutNode(
    var protocol: ParentProtocol,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = protocol

    override val shouldAutoInvalidate: Boolean get() = false
}

private class ValuedLayoutNodeElement(
    override val key: Any,
    val value: String,
) : ParentLayoutNodeElement<ValuedLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): ValuedLayoutNode = ValuedLayoutNode().also { it.value = value }

    override fun update(node: ValuedLayoutNode) {
        node.value = value
    }

    override fun equals(other: Any?): Boolean =
        other is ValuedLayoutNodeElement && other.key == key && other.value == value

    override fun hashCode(): Int = key.hashCode() * 31 + value.hashCode()
}

private class ValuedLayoutNode : ParentLayoutNode() {
    var value: String = ""

    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol
}
