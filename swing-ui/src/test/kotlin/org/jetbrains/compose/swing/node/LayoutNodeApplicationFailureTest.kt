package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.visitDeclaredNodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A layout node application that throws partway leaves every recorded node to detach once, and the next one writes
 * every standing node again.
 */
class LayoutNodeApplicationFailureTest {
    @Test
    fun aReplacementLayoutNodeThatFailsToCreateLeavesItsSlotHoldingTheAttachedNode() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val chain = mutableListOf<SwingModifier.Node>()
        val sightings = mutableListOf<String>()
        val events = mutableListOf<String>()
        child.declare(
            listOf(ChainLayoutNodeElement("a", chain, sightings), ChainLayoutNodeElement("b", chain, sightings)),
        )
        sightings.clear()

        assertFailsWith<IllegalStateException> {
            child.declare(listOf(RecordingLayoutNodeElement("x", events), FailingLayoutNodeElement))
        }

        assertEquals(listOf("onDetach a: 2 of 2"), sightings, "only the slot that was replaced detaches its node")
        assertEquals(listOf("create(x)", "onAttach", "update(x)"), events)
        val (replacement, kept) = child.layoutNodes()
        assertTrue(replacement is RecordingLayoutNode && replacement.isAttached)
        assertSame(chain[1], kept, "the slot whose replacement failed keeps its node")
        assertTrue(kept.isAttached)
        sightings.clear()
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach b: 1 of 2"), sightings)
        assertEquals(listOf("onDetach"), events)
        assertFalse(replacement.isAttached)
        assertFalse(kept.isAttached)
        owner.dispose()
    }

    @Test
    fun aDeclarationWhoseApplicationThrewIsAppliedWhenDeclaredAgain() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val standing = RecordingLayoutNodeElement("a", events)
        child.declare(listOf(standing))
        val declared = listOf(standing, FailingOnceLayoutNodeElement(events))
        assertFailsWith<IllegalStateException> {
            child.declare(declared)
        }
        events.clear()

        child.declare(declared)

        assertEquals(listOf("update(a)", "create(retried)", "onAttach", "update(retried)"), events)
        val nodes = child.layoutNodes()
        assertEquals(2, nodes.size)
        assertEquals(nodes, child.declaration.parentLayoutElements, "the declaration names the node its retry created")
        owner.dispose()
    }

    @Test
    fun theDeclarationStandingBeforeAFailedApplicationIsAppliedWhenDeclaredAgain() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val standing = listOf(RecordingLayoutNodeElement("a", events), RecordingLayoutNodeElement("b", events))
        child.declare(standing)
        assertFailsWith<IllegalStateException> {
            child.declare(listOf(RecordingLayoutNodeElement("changed", events), FailingLayoutNodeElement))
        }
        events.clear()

        child.declare(standing)

        assertEquals(listOf("update(a)", "update(b)"), events, "every slot takes its declaration back")
        owner.dispose()
    }

    @Test
    fun aFailedApplicationLeavesTheParentReadingOnlyTheLayoutNodesStanding() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        child.declare(listOf(RecordingLayoutNodeElement("a", events), RecordingLayoutNodeElement("b", events)))
        val (first, second) = child.layoutNodes()

        assertFailsWith<IllegalStateException> { child.declare(listOf(FailingLayoutNodeElement)) }

        assertFalse(second.isAttached, "the slot past the declaration detaches before the failure")
        assertEquals(listOf(first), child.declaration.parentLayoutElements, "the parent reads the node standing")
        owner.dispose()
    }

    @Test
    fun aWholeChainWhoseLayoutNodeFailsToAttachReleasesOnlyTheNodesThatAttached() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val chain = mutableListOf<SwingModifier.Node>()
        val sightings = mutableListOf<String>()
        val events = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            child.declare(
                listOf(
                    ChainLayoutNodeElement("first", chain, sightings),
                    RecordingLayoutNodeElement("failing", events) { error("onAttach fails") },
                    ChainLayoutNodeElement("last", chain, sightings),
                ),
            )
        }

        assertEquals(listOf("onAttach first: 2 of 2"), sightings)
        assertEquals(listOf(chain[0]), child.layoutNodes(), "only the node whose onAttach ran is recorded")
        assertFalse(
            chain[1].isAttached,
            "a node marked attached while the chain was walked, but whose onAttach never got a turn, is unwound",
        )
        sightings.clear()
        events.clear()

        child.onRelease()

        assertEquals(
            listOf("onDetach first: 1 of 2"),
            sightings,
            "release detaches the node that attached, once; the node that never attached was already unwound",
        )
        assertEquals(emptyList(), events, "the node whose onAttach threw does not detach")
        assertFalse(chain[0].isAttached)
        owner.dispose()
    }

    @Test
    fun aWholeChainWhoseLayoutNodeFailsToUpdateReleasesItOnce() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val declared = listOf(RecordingLayoutNodeElement("a", events), FailingUpdateLayoutNodeElement(events))

        assertFailsWith<IllegalStateException> {
            child.declare(declared)
        }
        val nodes = child.layoutNodes()
        assertEquals(2, nodes.size, "a node whose onAttach ran is recorded though its update threw")
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach", "onDetach"), events, "release detaches each node that attached, once")
        assertTrue(nodes.none { it.isAttached })
        owner.dispose()
    }

    @Test
    fun aLayoutNodeEnteringAStandingChainThatFailsToUpdateIsReleasedOnce() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val standing = RecordingLayoutNodeElement("a", events)
        child.declare(listOf(standing))

        assertFailsWith<IllegalStateException> {
            child.declare(listOf(standing, FailingUpdateLayoutNodeElement(events)))
        }
        val nodes = child.layoutNodes()
        assertEquals(2, nodes.size, "a node whose onAttach ran is recorded though its update threw")
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach", "onDetach"), events, "release detaches each node that attached, once")
        assertTrue(nodes.none { it.isAttached })
        owner.dispose()
    }

    @Test
    fun aReplacementLayoutNodeThatFailsToUpdateTakesItsSlotAndIsReleasedOnce() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        child.declare(listOf(RecordingLayoutNodeElement("a", events)))
        val replaced = child.layoutNodes().single()
        events.clear()

        assertFailsWith<IllegalStateException> {
            child.declare(listOf(FailingUpdateLayoutNodeElement(events)))
        }
        assertEquals(listOf("create(failing)", "onAttach", "onDetach"), events, "the replaced node detaches")
        assertFalse(replaced.isAttached)
        val replacement = child.layoutNodes().single()
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach"), events, "release detaches the replacement, once")
        assertFalse(replacement.isAttached)
        owner.dispose()
    }

    @Test
    fun aWholeChainLayoutNodeWhoseUpdateThrewIsUpdatedAgainWhenDeclaredAgain() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val declared = listOf(RecordingLayoutNodeElement("a", events), FailingUpdateOnceLayoutNodeElement(events))
        assertFailsWith<IllegalStateException> {
            child.declare(declared)
        }
        val nodes = child.layoutNodes()
        events.clear()

        child.declare(declared)

        assertEquals(
            listOf("update(a)", "update(retried)"),
            events,
            "the node whose update threw is updated with the one before it, not created again",
        )
        assertEquals(nodes, child.layoutNodes())
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach", "onDetach"), events, "release detaches each node once")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeEnteringAStandingChainWhoseUpdateThrewIsUpdatedAgainWhenDeclaredAgain() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val standing = RecordingLayoutNodeElement("a", events)
        child.declare(listOf(standing))
        val declared = listOf(standing, FailingUpdateOnceLayoutNodeElement(events))
        assertFailsWith<IllegalStateException> {
            child.declare(declared)
        }
        val nodes = child.layoutNodes()
        events.clear()

        child.declare(declared)

        assertEquals(
            listOf("update(a)", "update(retried)"),
            events,
            "the node whose update threw is updated with the one before it, not created again",
        )
        assertEquals(nodes, child.layoutNodes())
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach", "onDetach"), events, "release detaches each node once")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeWhoseUpdateThrewTakesBackTheModifierDeclaredBeforeIt() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
        val events = mutableListOf<String>()
        val standing = SwingModifier.then(ValueLayoutNodeElement("a", events))
        child.applyDeclaredModifier(standing)
        assertFailsWith<IllegalStateException> {
            child.applyDeclaredModifier(SwingModifier.then(ValueLayoutNodeElement("failing", events)))
        }
        val nodes = child.layoutNodes()
        events.clear()

        child.applyDeclaredModifier(standing)

        assertEquals(
            listOf("update(a)"),
            events,
            "the node whose update threw is updated with the modifier declared again",
        )
        assertEquals(nodes, child.layoutNodes())
        events.clear()

        child.onRelease()

        assertEquals(listOf("onDetach"), events, "release detaches the node once")
        owner.dispose()
    }
}

/** Applies a modifier declaring [elements], in order. */
private fun SwingNodeHolder<*>.declare(elements: List<SwingModifier.Element>) =
    applyModifierDiff(elements.fold(SwingModifier as SwingModifier) { modifier, element -> modifier then element })

/** The layout nodes the holder's modifier records, in declaration order. */
private fun SwingNodeHolder<*>.layoutNodes(): List<ParentLayoutNode> =
    buildList { visitDeclaredNodes { if (it is ParentLayoutNode) add(it) } }

/** A layout node element updating its node with [value], failing to update it with `"failing"`. */
private class ValueLayoutNodeElement(
    private val value: String,
    private val events: MutableList<String>,
) : ParentLayoutNodeElement<RecordingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode {
        events += "create($value)"
        return RecordingLayoutNode(events) {}
    }

    override fun update(node: RecordingLayoutNode) {
        events += "update($value)"
        check(value != "failing") { "update() fails" }
    }

    override fun equals(other: Any?): Boolean = other is ValueLayoutNodeElement && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

/** A layout node element whose node attaches and fails to be updated the first time only. */
private class FailingUpdateOnceLayoutNodeElement(
    private val events: MutableList<String>,
) : ParentLayoutNodeElement<RecordingLayoutNode>() {
    private var failed = false

    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode {
        events += "create(failing once)"
        return RecordingLayoutNode(events) {}
    }

    override fun update(node: RecordingLayoutNode) {
        if (!failed) {
            failed = true
            error("update() fails")
        }
        events += "update(retried)"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** A layout node element whose node cannot be created. */
private object FailingLayoutNodeElement : ParentLayoutNodeElement<RecordingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode = error("create() fails")

    override fun update(node: RecordingLayoutNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** A layout node element whose node fails to be created the first time only. */
private class FailingOnceLayoutNodeElement(
    private val events: MutableList<String>,
) : ParentLayoutNodeElement<RecordingLayoutNode>() {
    private var failed = false

    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode {
        if (!failed) {
            failed = true
            error("create() fails")
        }
        events += "create(retried)"
        return RecordingLayoutNode(events) {}
    }

    override fun update(node: RecordingLayoutNode) {
        events += "update(retried)"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** A layout node element whose node attaches and then fails to be updated. */
private class FailingUpdateLayoutNodeElement(
    private val events: MutableList<String>,
) : ParentLayoutNodeElement<RecordingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode {
        events += "create(failing)"
        return RecordingLayoutNode(events) {}
    }

    override fun update(node: RecordingLayoutNode): Unit = error("update() fails")

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}
