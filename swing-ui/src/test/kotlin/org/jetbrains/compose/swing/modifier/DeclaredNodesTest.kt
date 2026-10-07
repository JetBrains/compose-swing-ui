package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.node.CompositionLocalConsumerModifierNode
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.node.TestMeasurementParentProtocol
import org.jetbrains.compose.swing.node.attachedChild
import org.jetbrains.compose.swing.node.currentValueOf
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val LocalStatic = staticCompositionLocalOf { "default" }

private val LocalDynamic = compositionLocalOf { "default" }

/** Which modifier nodes a component is handed, in what order, and when. */
class DeclaredNodesTest {
    @Test
    fun layoutNodesAreVisitedBetweenTheAdditiveNodesAtTheirDeclaredPlaceAndPropertyNodesNotAtAll() {
        val owner = TestCompositionOwner()
        val component = ListeningPanel()
        val child = attachedChild(owner, component)

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
        assertSame(child, component.componentNode)
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

        assertEquals(listOf(listOf("l1", "a1", "l2", "a2")), visits, "the last node visits the declared order")
        assertEquals(
            listOf(listOf("l1", "a1", "l2", "a2")),
            child.component.received,
            "the component is handed the declared order",
        )
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
    fun aPassThatAttachesOnlyAPropertyNodeHandsNothingOver() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))

        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")).opaque(false))

        assertEquals(listOf(listOf("a", "l")), child.component.received, "only the attaching pass hands the nodes over")
        owner.dispose()
    }

    @Test
    fun aPassThatWritesAnElementHandsTheNodesOverOnce() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a")).then(Layout("l")))

        child.applyModifierDiff(SwingModifier.then(Additive("b")).then(Layout("l")))

        assertEquals(listOf(listOf("a", "l"), listOf("b", "l")), child.component.received)
        owner.dispose()
    }

    @Test
    fun aNodeThatDoesNotAutoInvalidateHandsNothingOver() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a", autoInvalidates = false)).then(Layout("l")))

        child.applyModifierDiff(SwingModifier.then(Additive("b", autoInvalidates = false)).then(Layout("l")))

        assertEquals(listOf(listOf("a", "l")), child.component.received, "the node owns its update invalidation")
        owner.dispose()
    }

    @Test
    fun structuralChangesHandOverNodesThatDoNotAutoInvalidate() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyModifierDiff(SwingModifier.then(Additive("a", autoInvalidates = false)))

        child.applyModifierDiff(SwingModifier.then(Additive("b", autoInvalidates = false)))
        assertEquals(listOf(listOf("a")), child.component.received, "an update uses the node's policy")

        child.applyModifierDiff(
            SwingModifier.then(Additive("b", autoInvalidates = false)).then(Layout("l", autoInvalidates = false)),
        )
        child.applyModifierDiff(
            SwingModifier.then(Layout("l", autoInvalidates = false)).then(Additive("b", autoInvalidates = false)),
        )
        child.applyModifierDiff(SwingModifier.then(Additive("b", autoInvalidates = false)))

        assertEquals(
            listOf(listOf("a"), listOf("b", "l"), listOf("l", "b"), listOf("b")),
            child.component.received,
            "attach, reorder and removal always hand over the current nodes",
        )
        owner.dispose()
    }

    @Test
    fun anAdditiveElementDeclaredWithAnotherValueIsWrittenInPlace() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val keyed = KeyReadingElement()
        val additive = Additive("a", autoInvalidates = false)
        child.applyDeclaredModifier(SwingModifier.then(keyed).then(additive).then(Layout("l")))
        val visits = ArrayList<List<String>>()

        child.applyDeclaredModifier(
            SwingModifier.then(keyed).then(Additive("b", visits = visits, autoInvalidates = false)).then(Layout("l")),
        )

        assertEquals("b", additive.created.single().label, "the slot's node must take the new element")
        assertEquals(1, visits.size, "the new element must be written once")
        assertEquals(listOf(listOf("a", "l")), child.component.received, "the node owns its update invalidation")
        owner.dispose()
    }

    @Test
    fun anAutoInvalidatingAdditiveElementIsWrittenInPlaceAndHandsTheNodesOverOnce() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val keyed = KeyReadingElement()
        val additive = Additive("a")
        child.applyDeclaredModifier(SwingModifier.then(keyed).then(additive).then(Layout("l")))

        child.applyDeclaredModifier(SwingModifier.then(keyed).then(Additive("b")).then(Layout("l")))

        assertEquals(listOf(listOf("a", "l"), listOf("b", "l")), child.component.received)
        assertEquals(
            "b",
            additive.created.single().label,
            "the slot's node must be written in place with the new element",
        )
        owner.dispose()
    }

    @Test
    fun anAutoInvalidatingKeyedSlotWrittenBeforeAKeyedChangeHandsTheNodesOverOnce() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val writes = AtomicInteger()
        child.applyDeclaredModifier(
            SwingModifier.then(CountingElement("kept", writes)).then(Additive("a")).opaque(false),
        )

        child.applyDeclaredModifier(
            SwingModifier.then(CountingElement("kept", writes)).then(Additive("b")).opaque(true),
        )

        assertTrue(child.component.isOpaque, "the keyed slot must be written with its new value")
        assertEquals(listOf(listOf("a"), listOf("b")), child.component.received)
        assertEquals(
            1,
            writes.get(),
            "an unchanged keyed element declared before the write must not be rewritten by the diverging opaque change",
        )
        owner.dispose()
    }

    @Test
    fun aKeyedElementDeclaredWithAnotherValueIsWritten() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        val keyed = KeyReadingElement()
        child.applyDeclaredModifier(SwingModifier.then(keyed).then(Additive("a")).opaque(false))

        child.applyDeclaredModifier(SwingModifier.then(keyed).then(Additive("a")).opaque(true))

        assertTrue(child.component.isOpaque, "the keyed slot must be written with its new value")
        owner.dispose()
    }

    @Test
    fun anAdditiveSlotDeclaredAsAKeyedElementOfItsClassIsDiffed() {
        val owner = TestCompositionOwner()
        val child = attachedChild(owner, ListeningPanel())
        child.applyDeclaredModifier(SwingModifier.then(KeyableAdditive("p", additive = true)))
        assertEquals(1, child.modifierState?.chain?.size, "the additive element must hold a chain slot")

        child.applyDeclaredModifier(SwingModifier.then(KeyableAdditive("p", additive = false)))

        assertEquals(0, child.modifierState?.chain?.size, "a keyed element of the same class holds no chain slot")
        owner.dispose()
    }

    @Test
    fun aCompositionLocalChangeThatRewritesANodeHandsTheNodesOver() = runComposeSwingTest {
        var value by mutableStateOf("a")
        lateinit var needed: ListeningPanel
        lateinit var declined: ListeningPanel
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(factory = { JPanel() }) {
                    SwingNode(
                        factory = { ListeningPanel().also { needed = it } },
                        modifier = SwingModifier.then(ConsumingAdditive("a")),
                    )
                    SwingNode(
                        factory = { ListeningPanel().also { declined = it } },
                        modifier = SwingModifier.then(ConsumingAdditive("a", autoInvalidates = false)),
                    )
                }
            }
        }

        value = "changed"
        awaitIdle()

        assertEquals(listOf(listOf("a"), listOf("a")), needed.received, "a needed node hands over")
        assertEquals(listOf(listOf("a")), declined.received, "the node owns its update invalidation")
    }

    @Test
    fun aChangedLocalDynamicThatRewritesANodeHandsTheNodesOverOnce() = runComposeSwingTest {
        var value by mutableStateOf("hello")
        lateinit var needed: ListeningPanel
        lateinit var declined: ListeningPanel
        setContent {
            CompositionLocalProvider(LocalDynamic provides value) {
                SwingNode(factory = { JPanel() }) {
                    SwingNode(
                        factory = { ListeningPanel().also { needed = it } },
                        modifier = SwingModifier.then(DynamicConsumingAdditive("a")),
                    )
                    SwingNode(
                        factory = { ListeningPanel().also { declined = it } },
                        modifier = SwingModifier.then(DynamicConsumingAdditive("a", autoInvalidates = false)),
                    )
                }
            }
        }

        value = "changed"
        awaitIdle()

        assertEquals(2, needed.received.size, "the needed node hands over once for the local change")
        assertEquals(1, declined.received.size, "the node owns its update invalidation")
    }

    @Test
    fun aStaticLocalRefreshOfAnAutoInvalidatingKeyedNodeHandsTheDeclaredNodesOver() = runComposeSwingTest {
        var value by mutableStateOf("first")
        lateinit var panel: ListeningPanel
        lateinit var node: KeyedStaticConsumerNode
        val element = KeyedStaticConsumerElement { node = it }
        setContent {
            CompositionLocalProvider(LocalStatic provides value) {
                SwingNode(factory = { JPanel() }) {
                    SwingNode(
                        factory = { ListeningPanel().also { panel = it } },
                        modifier =
                            SwingModifier.then(element).then(
                                Additive("step", autoInvalidates = false),
                            ),
                    )
                }
            }
        }
        assertEquals(listOf(listOf("step")), panel.received)
        assertEquals("first", node.label)

        value = "changed"
        awaitIdle()

        assertEquals(listOf(listOf("step"), listOf("step")), panel.received)
        assertEquals("changed", node.label)
    }

    @Test
    fun aDynamicLocalRefreshOfAnAutoInvalidatingKeyedNodeHandsTheDeclaredNodesOver() = runComposeSwingTest {
        var value by mutableStateOf("first")
        lateinit var panel: ListeningPanel
        lateinit var node: KeyedDynamicConsumerNode
        val element = KeyedDynamicConsumerElement { node = it }
        setContent {
            CompositionLocalProvider(LocalDynamic provides value) {
                SwingNode(factory = { JPanel() }) {
                    SwingNode(
                        factory = { ListeningPanel().also { panel = it } },
                        modifier = SwingModifier.then(element).then(Additive("step", autoInvalidates = false)),
                    )
                }
            }
        }
        assertEquals(listOf(listOf("step")), panel.received)
        assertEquals("first", node.label)

        value = "changed"
        awaitIdle()

        assertEquals(listOf(listOf("step"), listOf("step")), panel.received)
        assertEquals("changed", node.label)
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

        assertEquals(
            listOf(listOf("a"), emptyList()),
            reused.component.received,
            "a reused component is handed no nodes",
        )
        assertEquals(
            listOf(listOf("a"), emptyList()),
            deactivated.component.received,
            "a deactivated component is handed no nodes",
        )
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
internal class ListeningPanel :
    JPanel(),
    DeclaredNodesListener {
    val received = ArrayList<List<String>>()
    var componentNode: SwingComponentNode<*>? = null

    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) {
        this.componentNode = componentNode
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
internal open class Additive(
    private val label: String,
    private var failsOnce: Boolean = false,
    private val visits: MutableList<List<String>>? = null,
    private val autoInvalidates: Boolean = true,
) : SwingModifier.NodeElement<Component, LabelNode>() {
    val created = ArrayList<LabelNode>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): LabelNode = LabelNode(autoInvalidates).also { created += it }

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

/** A non-additive element written in place while [label] stays equal; each write increments [writes]. */
private class CountingElement(
    private val label: String,
    private val writes: AtomicInteger,
) : SwingModifier.NodeElement<Component, LabelNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): LabelNode = LabelNode()

    override fun update(node: LabelNode) {
        writes.incrementAndGet()
        node.label = label
    }

    override fun equals(other: Any?): Boolean =
        other is CountingElement && other.label == label && other.writes === writes

    override fun hashCode(): Int = 31 * label.hashCode() + System.identityHashCode(writes)
}

/** An additive element of its own class whose [additive] a declaration can also decide, keyed by [label]. */
private class KeyableAdditive(
    private val label: String,
    override val additive: Boolean,
) : SwingModifier.NodeElement<Component, LabelNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override val key: Any get() = label

    override fun create(): LabelNode = LabelNode()

    override fun update(node: LabelNode) {
        node.label = label
    }

    override fun equals(other: Any?): Boolean =
        other is KeyableAdditive && other.label == label && other.additive == additive

    override fun hashCode(): Int = 31 * label.hashCode() + additive.hashCode()
}

/** An additive element whose node is a [CompositionLocalConsumerModifierNode], so a local refresh rewrites it. */
private class ConsumingAdditive(
    private val label: String,
    private val autoInvalidates: Boolean = true,
) : SwingModifier.NodeElement<Component, ConsumingLabelNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): ConsumingLabelNode = ConsumingLabelNode(autoInvalidates)

    override fun update(node: ConsumingLabelNode) {
        node.label = label
    }

    override fun equals(other: Any?): Boolean = other is ConsumingAdditive && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class ConsumingLabelNode(
    autoInvalidates: Boolean,
) : LabelNode(autoInvalidates),
    CompositionLocalConsumerModifierNode

/** An additive element whose node reads [LocalDynamic] in `update`, so a change of its value rewrites it. */
private class DynamicConsumingAdditive(
    private val label: String,
    private val autoInvalidates: Boolean = true,
) : SwingModifier.NodeElement<Component, DynamicConsumingLabelNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): DynamicConsumingLabelNode = DynamicConsumingLabelNode(autoInvalidates)

    override fun update(node: DynamicConsumingLabelNode) {
        node.label = "$label:${node.currentValueOf(LocalDynamic)}"
    }

    override fun equals(other: Any?): Boolean = other is DynamicConsumingAdditive && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

private class DynamicConsumingLabelNode(
    autoInvalidates: Boolean,
) : LabelNode(autoInvalidates),
    CompositionLocalConsumerModifierNode

private class KeyedStaticConsumerElement(
    private val onCreate: (KeyedStaticConsumerNode) -> Unit,
) : SwingModifier.NodeElement<Component, KeyedStaticConsumerNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): KeyedStaticConsumerNode = KeyedStaticConsumerNode().also(onCreate)

    override fun update(node: KeyedStaticConsumerNode) {
        node.label = node.currentValueOf(LocalStatic)
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class KeyedStaticConsumerNode :
    LabelNode(),
    CompositionLocalConsumerModifierNode

private class KeyedDynamicConsumerElement(
    private val onCreate: (KeyedDynamicConsumerNode) -> Unit,
) : SwingModifier.NodeElement<Component, KeyedDynamicConsumerNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): KeyedDynamicConsumerNode = KeyedDynamicConsumerNode().also(onCreate)

    override fun update(node: KeyedDynamicConsumerNode) {
        node.label = node.currentValueOf(LocalDynamic)
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class KeyedDynamicConsumerNode :
    LabelNode(),
    CompositionLocalConsumerModifierNode

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

internal open class LabelNode(
    override val shouldAutoInvalidate: Boolean = true,
) : SwingModifier.ComponentNode<Component>() {
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

/**
 * A parent-layout element; each `update` adds what its node visits to [visits]. Its node has the parent lay the
 * component out again after an update only where [autoInvalidates].
 */
internal class Layout(
    private val label: String,
    private val visits: MutableList<List<String>>? = null,
    private val autoInvalidates: Boolean = true,
) : ParentLayoutNodeElement<LabelLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override val additive: Boolean get() = true

    val created = ArrayList<LabelLayoutNode>()

    override fun create(): LabelLayoutNode = LabelLayoutNode(autoInvalidates).also { created += it }

    override fun update(node: LabelLayoutNode) {
        node.label = label
        node.shouldAutoInvalidate = autoInvalidates
        visits?.add(visitedLabels(node))
    }

    override fun equals(other: Any?): Boolean = other is Layout && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

internal class LabelLayoutNode(
    override var shouldAutoInvalidate: Boolean,
) : ParentLayoutNode() {
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
