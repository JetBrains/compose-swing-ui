package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.node.TestMeasurementParentProtocol
import org.jetbrains.compose.swing.node.attachedChild
import java.awt.Component
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Which modifier nodes a component is handed, in what order, and when. */
class DeclaredNodesTest {
    @Test
    fun layoutNodesAreVisitedBetweenTheAdditiveNodesAtTheirDeclaredPlaceAndPropertyNodesNotAtAll() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())

        child.applyModifierDiff(
            SwingModifier
                .then(Layout("l1"))
                .then(Additive("a1"))
                .opaque(false)
                .then(Layout("l2"))
                .then(Layout("l3"))
                .then(Additive("a2")),
        )

        assertEquals(listOf("l1", "a1", "l2", "l3", "a2"), child.component.received.last())
        owner.dispose()
    }

    @Test
    fun aNodeVisitsTheNodesItsComponentIsHandedAndNothingOnceDetached() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.then(Layout("l")).then(additive))
        val node = additive.created.single()
        val visited = ArrayList<String>()

        node.visitDeclaredNodes { visited += it.label }
        child.applyModifierDiff(SwingModifier.then(Layout("l")))
        node.visitDeclaredNodes { visited += it.label }

        assertEquals(listOf("l", "a"), visited)
        owner.dispose()
    }

    @Test
    fun aStandingLayoutNodeMovesWhenTheAdditiveElementsDeclaredBeforeItChange() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Layout("l")).then(Additive("a")))

        child.applyModifierDiff(SwingModifier.then(Additive("b")).then(Layout("l")))

        assertEquals(listOf(listOf("l", "a"), listOf("b", "l")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aLayoutNodeUpdatedAsItMovesAcrossAnAdditiveNodeVisitsTheDeclaredOrder() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Layout("l")).then(Additive("a")))
        val visits = ArrayList<List<String>>()

        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("m", visits)))

        assertEquals(listOf(listOf("a", "m")), visits)
        assertEquals(listOf(listOf("l", "a"), listOf("a", "m")), child.component.received)
        owner.dispose()
    }

    @Test
    fun anAdditiveNodeUpdatedAsALayoutNodeMovesAcrossItVisitsTheDeclaredOrder() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))
        val visits = ArrayList<List<String>>()

        child.applyModifierDiff(SwingModifier.then(Layout("l")).then(Additive("b", visits = visits)))

        assertEquals(listOf(listOf("l", "b")), visits)
        assertEquals(listOf(listOf("a", "l"), listOf("l", "b")), child.component.received)
        owner.dispose()
    }

    @Test
    fun theLastNodeOfAChainAttachedWholeVisitsTheDeclaredOrder() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val visits = ArrayList<List<String>>()

        child.applyModifierDiff(
            SwingModifier
                .then(Layout("l1"))
                .then(Additive("a1"))
                .then(Layout("l2"))
                .then(Additive("a2", visits = visits)),
        )

        assertEquals(listOf(listOf("l1", "a1", "l2", "a2")), visits)
        assertEquals(listOf(listOf("l1", "a1", "l2", "a2")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aNodeEnteringBetweenStandingNodesVisitsTheDeclaredOrder() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))
        val visits = ArrayList<List<String>>()

        child.applyModifierDiff(
            SwingModifier
                .then(Additive("a"))
                .then(Additive("n", visits = visits))
                .then(Layout("l")),
        )

        assertEquals(listOf(listOf("a", "n", "l")), visits)
        assertEquals(listOf(listOf("a", "l"), listOf("a", "n", "l")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aNodeVisitsEveryNodeStillAttachedAfterAPassThatThrewPartway() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.then(additive).then(Additive("b")))

        assertFailsWith<IllegalStateException> {
            child.applyModifierDiff(SwingModifier.then(additive).then(FailingToAttach(additive = false)))
        }

        assertEquals(listOf("a", "b"), visitedLabels(additive.created.single()))
        owner.dispose()
    }

    @Test
    fun aNodeDetachedByAKeyChangeVisitsTheOrderLastApplied() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("b")
        child.applyModifierDiff(SwingModifier.key(1).then(Additive("a")).then(additive))

        child.applyModifierDiff(SwingModifier.key(2).then(Additive("c")))

        assertEquals(listOf(listOf("a", "b")), additive.created.single().detachVisits)
        owner.dispose()
    }

    @Test
    fun aPassThatLeavesTheNodesInPlaceHandsNothingOver() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")).opaque(false))

        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")).opaque(true))

        assertEquals(1, child.component.received.size, "only the attaching pass hands the nodes over")
        owner.dispose()
    }

    @Test
    fun aPassThatAttachesOnlyAPropertyNodeOrUpdatesTheNodesInPlaceHandsNothingOver() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))

        child.applyModifierDiff(SwingModifier.then(Additive("b")).then(Layout("m")).opaque(false))

        assertEquals(listOf(listOf("a", "l")), child.component.received, "only the attaching pass hands the nodes over")
        owner.dispose()
    }

    @Test
    fun aLayoutNodeStandsWithItsStateThroughAKeyChange() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        val layout = Layout("l")
        child.applyModifierDiff(
            SwingModifier
                .key(1)
                .then(additive)
                .then(layout)
                .then(Additive("b")),
        )
        val node = layout.created.single()
        node.state = "kept"

        child.applyModifierDiff(
            SwingModifier
                .key(2)
                .then(additive)
                .then(layout)
                .then(Additive("b")),
        )

        assertEquals(1, layout.created.size, "a key change must not create the layout node again")
        assertEquals(listOf(1, 0), listOf(node.attaches, node.detaches), "the layout node must stay attached")
        assertEquals("kept", node.state)
        assertEquals(2, additive.created.size, "the key change attaches the component node again")
        assertEquals(2, child.component.received.size)
        assertEquals(listOf("a", "l", "b"), child.component.received.last())
        owner.dispose()
    }

    @Test
    fun aLayoutNodeStandsWithItsStateWhileAComponentElementBeforeItComesAndGoes() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val layout = Layout("l")
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(layout))
        val node = layout.created.single()
        node.state = "kept"

        child.applyModifierDiff(SwingModifier.then(layout))
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(layout))

        assertEquals(1, layout.created.size, "the component element leaving or entering must not recreate the node")
        assertEquals(listOf(1, 0), listOf(node.attaches, node.detaches), "the layout node must stay attached")
        assertEquals("kept", node.state)
        assertEquals(listOf(listOf("a", "l"), listOf("l"), listOf("a", "l")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aComponentNodeStandsWhileALayoutElementBeforeItComesAndGoes() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.then(Layout("l")).then(additive))
        val node = additive.created.single()

        child.applyModifierDiff(SwingModifier.then(additive))
        child.applyModifierDiff(SwingModifier.then(Layout("l")).then(additive))

        assertEquals(
            listOf(node),
            additive.created,
            "the layout element leaving or entering must not recreate the node",
        )
        assertEquals(1, node.attaches, "the component node must stay attached")
        assertEquals(listOf(listOf("l", "a"), listOf("a"), listOf("l", "a")), child.component.received)
        owner.dispose()
    }

    @Test
    fun nodesNeverHandedOverAreNotTakenBackOnRelease() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        assertFailsWith<IllegalStateException> {
            child.applyModifierDiff(SwingModifier.then(Additive("a")).then(FailingToAttach()))
        }

        child.onRelease()

        assertEquals(emptyList(), child.component.received, "a component handed no nodes is handed no empty list")
        owner.dispose()
    }

    @Test
    fun nodesAttachedByAPassThatThrewAreHandedOverByTheNextPass() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val failing = Additive("a", failsOnce = true)
        assertFailsWith<IllegalStateException> { child.applyModifierDiff(SwingModifier.then(failing)) }

        child.applyModifierDiff(SwingModifier.then(failing))

        assertEquals(listOf(listOf("a")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aKeyChangeHandsOverTheNodesAttachedAgain() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.key(1).then(additive))

        child.applyModifierDiff(SwingModifier.key(2).then(additive))

        assertEquals(listOf(listOf("a"), listOf("a")), child.component.received)
        assertEquals(2, additive.created.size, "the key change attaches the slot again")
        owner.dispose()
    }

    @Test
    fun aSlotTakingAnotherKindOfElementHandsOverItsNewNode() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))

        child.applyModifierDiff(SwingModifier.then(OtherAdditive("b")).then(Layout("l")))

        assertEquals(listOf(listOf("a", "l"), listOf("b", "l")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aSlotTakingAnElementOfASubclassHandsOverANewNode() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.then(additive).then(Layout("l")))
        val node = additive.created.single()

        child.applyModifierDiff(SwingModifier.then(SubAdditive("b")).then(Layout("l")))

        assertEquals(listOf(listOf("a", "l"), listOf("b", "l")), child.component.received)
        assertEquals(1, node.detachVisits.size, "the node built for the other class detaches")
        owner.dispose()
    }

    @Test
    fun aNodeReplacingAnotherKindFindsItselfInItsPlaceAsItAttaches() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val layout = Layout("l")
        child.applyModifierDiff(SwingModifier.then(layout).then(Additive("a")))
        val replacing = VisitingOnAttach()

        child.applyModifierDiff(SwingModifier.then(layout).then(replacing))

        val node = replacing.created.single()
        assertEquals(listOf(layout.created.single(), node), node.attachVisits)
        owner.dispose()
    }

    @Test
    fun aNodeFindsItselfInItsPlaceAsItAttaches() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val layout = Layout("l")
        val first = VisitingOnAttach()
        child.applyModifierDiff(SwingModifier.then(first).then(layout))
        val entering = VisitingOnAttach()
        child.applyModifierDiff(SwingModifier.then(first).then(layout).then(entering))
        child.applyModifierDiff(
            SwingModifier
                .key(1)
                .then(first)
                .then(layout)
                .then(entering),
        )

        val l = layout.created.single()
        val (a, keyedA) = first.created
        val (e, keyedE) = entering.created
        assertEquals(listOf(a, l), a.attachVisits, "attached whole")
        assertEquals(listOf(a, l, e), e.attachVisits, "entering a standing chain")
        assertEquals(listOf(keyedA, l), keyedA.attachVisits, "attached whole again by a key change")
        assertEquals(listOf(keyedA, l, keyedE), keyedE.attachVisits, "attached whole again by a key change")
        owner.dispose()
    }

    @Test
    fun aNodeEnteringAStandingChainThatFailsToAttachLeavesNoSlot() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.then(additive))

        assertFailsWith<IllegalStateException> {
            child.applyModifierDiff(SwingModifier.then(additive).then(FailingToAttach()))
        }

        assertEquals(listOf("a"), visitedLabels(additive.created.single()))
        owner.dispose()
    }

    @Test
    fun aSlotWhoseReplacementFailsToAttachKeepsItsNode() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val additive = Additive("a")
        child.applyModifierDiff(SwingModifier.then(additive))
        val node = additive.created.single()

        assertFailsWith<IllegalStateException> { child.applyModifierDiff(SwingModifier.then(FailingToAttach())) }

        assertEquals(listOf("a"), visitedLabels(node))
        child.onRelease()
        assertEquals(listOf(listOf("a")), node.detachVisits, "the kept node detaches once, on release")
        owner.dispose()
    }

    @Test
    fun nodesHandedOverAreTakenBackOnReleaseAfterAPassThatThrewHavingDetachedThem() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.key(1).then(Additive("a")))
        assertFailsWith<IllegalStateException> {
            child.applyModifierDiff(SwingModifier.key(2).then(FailingToAttach()))
        }

        child.onRelease()

        assertEquals(listOf(listOf("a"), emptyList()), child.component.received)
        owner.dispose()
    }

    @Test
    fun reusingOrDeactivatingTheComponentHandsOverNoNodes() {
        val owner = TestCompositionOwner()
        val reused = attachedChild(owner, ListeningPanel())
        val deactivated = attachedChild(owner, ListeningPanel())
        reused.applyModifierDiff(SwingModifier.then(Additive("a")))
        deactivated.applyModifierDiff(SwingModifier.then(Additive("a")))

        reused.onReuse()
        deactivated.onDeactivate()

        assertEquals(listOf(listOf("a"), emptyList()), reused.component.received)
        assertEquals(listOf(listOf("a"), emptyList()), deactivated.component.received)
        owner.dispose()
    }

    @Test
    fun releasingTheComponentHandsOverNoNodes() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))

        child.onRelease()

        assertEquals(listOf(listOf("a", "l"), emptyList()), child.component.received)
        owner.dispose()
    }
}

private val SwingModifier.Node.label: String
    get() =
        when (this) {
            is LabelNode -> label
            is LabelLayoutNode -> label
            else -> error("unexpected node $this")
        }

/** Records the labels of every node list it is handed. */
private class ListeningPanel :
    JPanel(),
    DeclaredNodesListener {
    val received = ArrayList<List<String>>()

    override fun onDeclaredNodesChanged(nodes: List<SwingModifier.Node>) {
        received += nodes.map { it.label }
    }
}

/** Labels what [visitDeclaredNodes][SwingModifier.Node.visitDeclaredNodes] visits from [node]. */
private fun visitedLabels(node: SwingModifier.Node): List<String> {
    val labels = ArrayList<String>()
    node.visitDeclaredNodes { labels += it.label }
    return labels
}

/** An additive element; each `update` adds what its node visits to [visits]. */
private open class Additive(
    private val label: String,
    private var failsOnce: Boolean = false,
    private val visits: MutableList<List<String>>? = null,
) : SwingModifier.NodeElement<Component, LabelNode>() {
    val created = ArrayList<LabelNode>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): LabelNode = LabelNode().also { created += it }

    override fun update(node: LabelNode) {
        if (failsOnce) {
            failsOnce = false
            error("update fails")
        }
        node.label = label
        visits?.add(visitedLabels(node))
    }

    override fun equals(other: Any?): Boolean = other is Additive && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

/** An [Additive] of its own class, whose node a slot holding an [Additive] cannot host. */
private class SubAdditive(
    label: String,
) : Additive(label)

/** An additive element of another kind than [Additive], whose node a slot holding an [Additive] cannot host. */
private class OtherAdditive(
    private val label: String,
) : SwingModifier.NodeElement<Component, LabelNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): LabelNode = LabelNode()

    override fun update(node: LabelNode) {
        node.label = label
    }

    override fun equals(other: Any?): Boolean = other is OtherAdditive && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

/** An additive element of another kind than [Additive], whose node records what it visits in `onAttach`. */
private class VisitingOnAttach : SwingModifier.NodeElement<Component, VisitingOnAttach.Node>() {
    val created = ArrayList<Node>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): Node = Node().also { created += it }

    override fun update(node: Node) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)

    class Node : LabelNode() {
        val attachVisits = ArrayList<SwingModifier.Node>()

        override fun onAttach() {
            super.onAttach()
            visitDeclaredNodes { attachVisits += it }
        }
    }
}

private open class LabelNode : SwingModifier.ComponentNode<Component>() {
    var label = ""

    var attaches = 0

    /** What this node visited in each `onDetach`. */
    val detachVisits = ArrayList<List<String>>()

    override fun onAttach() {
        attaches++
    }

    override fun onDetach() {
        detachVisits += visitedLabels(this)
    }
}

/** An element whose node fails to attach; additive unless [additive] says otherwise. */
private class FailingToAttach(
    override val additive: Boolean = true,
) : SwingModifier.NodeElement<Component, LabelNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): LabelNode = object : LabelNode() {
        override fun onAttach(): Unit = error("onAttach fails")
    }

    override fun update(node: LabelNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** A parent-layout element; each `update` adds what its node visits to [visits]. */
private class Layout(
    private val label: String,
    private val visits: MutableList<List<String>>? = null,
) : ParentLayoutNodeElement<LabelLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override val additive: Boolean get() = true

    val created = ArrayList<LabelLayoutNode>()

    override fun create(): LabelLayoutNode = LabelLayoutNode().also { created += it }

    override fun update(node: LabelLayoutNode) {
        node.label = label
        visits?.add(visitedLabels(node))
    }

    override fun equals(other: Any?): Boolean = other is Layout && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class LabelLayoutNode : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    var label = ""

    /** Set by a test; nothing in the node writes it. */
    var state = ""

    var attaches = 0

    var detaches = 0

    override fun onAttach() {
        attaches++
    }

    override fun onDetach() {
        detaches++
    }
}
