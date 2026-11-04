package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.KeyReadingElement
import org.jetbrains.compose.swing.LayoutDeclaration
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNode
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.ResolvedParentDeclarations
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.modifier.resolveParentDeclarations
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.LayoutManager
import java.lang.ref.WeakReference
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Holds the parent-layout protocol to the core runtime behavior without depending on Foundation. */
class ParentDeclarationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("layoutNodeChanges")
    fun aModifierUpdatePublishesOnDetachAndAfterTheUpdate(
        case: String,
        initial: SwingModifier,
        updated: SwingModifier,
        expectedElementCounts: List<Int>,
    ) = runComposeSwingTest {
        val layout = RecordingMeasurementLayout()
        val parent = JPanel(layout)
        val child = JLabel("child")
        var modifier by mutableStateOf(initial)
        setContent {
            SwingNode(factory = { parent }, content = {
                SwingNode(factory = { child }, modifier = modifier)
            })
        }
        layout.declarations.clear()
        layout.removals.clear()

        modifier = updated
        awaitIdle()

        assertEquals(listOf(child), parent.components.toList(), case)
        assertEquals(
            expectedElementCounts,
            layout.declarations.map { it.elements.size },
            case,
        )
        assertEquals(emptyList(), layout.removals, case)
    }

    @Test
    fun attachmentAndChangesDeclareConstraintAndElementsAtomicallyInOrder() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = CreatedNodeHolder(JButton("child"))
        val first = TestParentLayoutElement("first")
        val second = TestParentLayoutElement("second")

        child.declaration.applyComponentLayout(
            resolvedDeclaration("first constraint", RawParentProtocol, listOf(first, second)),
            listOf(first, second),
        )
        root.add(child.component)
        child.declaration.attachedUnder(root)

        assertEquals(
            LayoutDeclaration(child.component, "first constraint", listOf(first, second)),
            layout.declarations.last(),
        )
        assertEquals(1, layout.declarations.size, "attachment declares the complete initial state once")
        assertEquals(0, layout.removals.size, "capable layouts retain the attached child")

        child.declaration.applyComponentLayout(
            resolvedDeclaration("second constraint", RawParentProtocol, listOf(first, second)),
            listOf(first, second),
        )

        assertEquals(
            LayoutDeclaration(child.component, "second constraint", listOf(first, second)),
            layout.declarations.last(),
        )
        assertEquals(2, layout.declarations.size, "a parent-data change must make one atomic call")

        child.declaration.applyComponentLayout(
            resolvedDeclaration("second constraint", RawParentProtocol, listOf(second, first)),
            listOf(second, first),
        )

        assertEquals(
            LayoutDeclaration(child.component, "second constraint", listOf(second, first)),
            layout.declarations.last(),
        )
        assertEquals(3, layout.declarations.size, "an element change must make one atomic call")
        assertEquals(0, layout.removals.size, "an update must not re-register the component")
    }

    @Test
    fun applierDefersACapableParentsRawConstraintUntilItsAtomicDeclaration() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child"))
        val element = TestParentLayoutElement("padding")
        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, listOf(element)),
            listOf(element),
        )

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(listOf<Any?>(null), layout.registrations.map { it.constraints })
            assertEquals(
                LayoutDeclaration(child.component, "constraint", listOf(element)),
                layout.declarations.single(),
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun modifierDiffCarriesParentDataAndMeasuringElementsInOneAtomicDeclaration() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child: SwingNodeHolder<Component> = CreatedNodeHolder(JButton("child"))
        val padding = TestParentLayoutElement("padding")
        child.applyModifierDiff(SwingModifier.layoutConstraint("constraint") then padding)

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(
                LayoutDeclaration(child.component, "constraint", listOf(padding)),
                layout.declarations.single(),
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun equalLayoutDeclarationDoesNotCallTheManagerAgain() {
        val layout = RecordingMeasurementLayout()
        val root = JPanel(layout)
        val child = attached(root)
        val declaration = listOf(TestParentLayoutElement("padding"))

        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, declaration),
            declaration,
        )
        val calls = layout.declarations.size
        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, declaration),
            declaration,
        )

        assertEquals(calls, layout.declarations.size)
    }

    @Test
    fun parentLayoutElementsAreCopiedAtTheDeclarationBoundary() {
        val layout = RecordingMeasurementLayout()
        val child = attached(JPanel(layout))
        val elements = mutableListOf<ParentLayoutElement>(TestParentLayoutElement("padding"))

        child.declaration.applyComponentLayout(
            resolvedDeclaration("constraint", RawParentProtocol, elements),
            elements,
        )
        elements.clear()

        assertEquals(listOf(TestParentLayoutElement("padding")), child.declaration.parentLayoutElements)
    }

    @Test
    fun initialParentLayoutElementsUnderAnUnsupportedParentAreRefusedOnAttachment() {
        val root = JPanel(BorderLayout())
        val child = CreatedNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(
            resolvedDeclaration(null, null, listOf(TestParentLayoutElement("padding"))),
            listOf(TestParentLayoutElement("padding")),
        )
        root.add(child.component)

        assertFailsWith<IllegalStateException> { child.declaration.attachedUnder(root) }
    }

    @Test
    fun changedParentLayoutElementsUnderAnUnsupportedParentAreRefused() {
        val root = JPanel(BorderLayout())
        val child = attached(root)

        assertFailsWith<IllegalStateException> {
            child.declaration.applyComponentLayout(
                resolvedDeclaration(null, null, listOf(TestParentLayoutElement("padding"))),
                listOf(TestParentLayoutElement("padding")),
            )
        }
        assertTrue(child.declaration.parentLayoutElements.isEmpty(), "a rejected declaration must not be retained")
    }

    @Test
    fun conventionalLayoutManagersKeepTheirConstraintRegistrationPath() {
        val layout = RecordingLayout()
        val root = JPanel(layout)
        val child = attached(root)

        child.declaration.applyComponentLayout(
            resolvedDeclaration("replacement", RawParentProtocol, emptyList()),
            emptyList(),
        )

        assertSame(child.component, layout.removals.single())
        assertEquals("replacement", layout.registrations.last().constraints)
    }

    @Test
    fun legacyLayoutManagerIsReregisteredWhenParentDataIsNull() {
        val layout = mockk<LayoutManager>(relaxed = true)
        val child = attached(JPanel(layout))

        child.declaration.applyComponentLayout(
            resolvedDeclaration(null, RawParentProtocol, emptyList()),
            emptyList(),
        )

        verifyOrder {
            layout.removeLayoutComponent(child.component)
            layout.addLayoutComponent(null, child.component)
        }
    }

    @Test
    fun applierRefusesWrongTypedParentDataBeforeItAddsTheChild() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(
            resolvedDeclaration("region", BorderOnlyParentData, emptyList()),
            emptyList(),
        )

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()
            assertEquals(0, root.componentCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun applierRefusesWrongSlotFamilyBeforeItsAttachmentMutatesTheHost() {
        val root = JPanel()
        val owner = TestCompositionOwner()
        val rootHolder = CreatedNodeHolder(root).attachedTo(owner)
        val applier = SwingApplier(rootHolder)
        val child = CreatedNodeHolder(JButton("child"))
        var installs = 0
        val scrollPaneSlot = parentProtocolOf("JScrollPane slot") { it is JScrollPane }
        child.applyModifierDiff(
            SwingModifier.slot(
                scrollPaneSlot,
                "viewport",
                SlotAttachment { _, _, _ ->
                    installs++
                    {}
                },
            ),
        )
        rootHolder.childPlacement = ChildPlacement.Slots("viewport")

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            val refusal = assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()

            assertTrue(refusal.message.orEmpty().contains("JScrollPane slot"))
            assertEquals(0, installs)
            assertEquals(0, root.componentCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun aHostThatCannotMeasureNamesTheLayoutElementDeclaredRatherThanTheNodeStandingForIt() {
        val root = JPanel(FlowLayout())
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        child.applyModifierDiff(SwingModifier.then(AnyHostLayoutNodeElement))

        try {
            applier.onBeginChanges()
            applier.insertTopDown(0, child)
            applier.down(applier.root)
            val refusal = assertFailsWith<IllegalStateException> { applier.insertBottomUp(0, child) }
            applier.up()

            assertTrue(
                refusal.message.orEmpty().contains("declares SwingModifier.anyHostLayout()."),
                "the refusal must name the declared element, but was: ${refusal.message}",
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rawParentDataCanAttachToALayeredPaneWithoutALayoutManager() {
        val root = JLayeredPane()
        val owner = TestCompositionOwner()
        val applier = SwingApplier(CreatedNodeHolder(root).attachedTo(owner))
        val child = CreatedNodeHolder(JButton("child"))
        child.declaration.applyComponentLayout(
            resolvedDeclaration(200, RawParentProtocol, emptyList()),
            emptyList(),
        )

        try {
            applier.onBeginChanges()
            applier.down(applier.root)
            applier.insertBottomUp(0, child)
            applier.up()
            applier.onEndChanges()

            assertEquals(1, root.componentCount)
            assertEquals(200, root.getLayer(child.component))
        } finally {
            owner.dispose()
        }
    }

    companion object {
        @JvmStatic
        fun layoutNodeChanges(): List<Arguments> = listOf(
            Arguments.of(
                "update one layout node and remove another",
                SwingModifier then RecordingLayoutNodeElement("a", mutableListOf(), key = "first") then
                    RecordingLayoutNodeElement("b", mutableListOf(), key = "second"),
                SwingModifier then RecordingLayoutNodeElement("c", mutableListOf(), key = "first"),
                listOf(1, 1),
            ),
            Arguments.of(
                "remove all layout nodes",
                SwingModifier then RecordingLayoutNodeElement("a", mutableListOf()) then
                    RecordingLayoutNodeElement("b", mutableListOf()),
                SwingModifier,
                listOf(1, 0),
            ),
            Arguments.of(
                "replace a layout node type and update another",
                SwingModifier then ChainLayoutNodeElement("first", mutableListOf(), mutableListOf()) then
                    RecordingLayoutNodeElement("a", mutableListOf(), key = "second"),
                SwingModifier then RecordingLayoutNodeElement("first", mutableListOf()) then
                    RecordingLayoutNodeElement("c", mutableListOf(), key = "second"),
                listOf(1, 2),
            ),
        )
    }

    private fun resolvedDeclaration(
        data: Any?,
        protocol: ParentProtocol?,
        elements: List<ParentLayoutElement>,
    ): ResolvedParentDeclarations = resolveParentDeclarations(
        buildList {
            if (protocol != null) add(TestParentData(data, protocol))
            addAll(elements)
        },
    )

    private data class TestParentData(
        val data: Any?,
        override val parentProtocol: ParentProtocol,
    ) : ParentDataModifier {
        override fun modifyParentData(parentData: Any?): Any? = data
    }

    private fun attached(root: JPanel): SwingNodeHolder<JButton> {
        val child = CreatedNodeHolder(JButton("child"))
        root.add(child.component)
        child.declaration.attachedUnder(root)
        return child
    }
}

class ParentLayoutNodeTest {
    @Test
    fun aLayoutNodeElementCreatesAttachesThenUpdatesItsNodeOnce() {
        val owner = TestCompositionOwner()
        val root = JPanel(RecordingMeasurementLayout())
        val child = CreatedNodeHolder(JButton("child")).attachedTo(owner)
        root.add(child.component)
        val events = mutableListOf<String>()

        child.applyModifierDiff(SwingModifier.then(RecordingLayoutNodeElement("a", events)))
        child.declaration.attachedUnder(root)

        assertEquals(listOf("create(a)", "onAttach", "update(a)"), events)
        owner.dispose()
    }

    @Test
    fun aLayoutNodeElementUpdatesTheSameNodeWhenItsDeclarationChanges() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        child.declaration.attachedUnder(root)
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
        child.declaration.attachedUnder(root)
        val keyed = KeyReadingElement()
        val events = mutableListOf<String>()
        child.applyDeclaredModifier(SwingModifier then keyed then RecordingLayoutNodeElement("a", events))
        val node = child.declaration.parentLayoutElements.single()
        val declarations = layout.declarations.size
        val keyReads = keyed.keyReads
        events.clear()

        child.applyDeclaredModifier(SwingModifier then keyed then RecordingLayoutNodeElement("b", events))

        assertEquals(keyReads, keyed.keyReads, "a layout node written in place must leave the modifier undiffed")

        assertEquals(listOf("update(b)"), events, "the slot's node must be written once with the new element")
        assertEquals(declarations + 1, layout.declarations.size, "the parent must be declared to once again")
        assertEquals(listOf(node), layout.declarations.last().elements, "the parent must be handed the same node")
        owner.dispose()
    }

    @Test
    fun aLayoutElementALaterOneOfItsKeyReplacesWritesNothingWhereOnlyItChanges() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
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
        child.declaration.attachedUnder(root)
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
        val child = attachedLayoutNode(owner)
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

    @Test
    fun aLayoutNodeIsResetThenDetachedWhenItsSlotIsReusedForAnotherItem() {
        val owner = TestCompositionOwner()
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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
        val child = attachedLayoutNode(owner)
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

internal fun attachedLayoutNode(owner: TestCompositionOwner): SwingNodeHolder<JButton> =
    attachedChild(owner, JButton("child"))

/** [component]'s holder, attached to [owner] under a parent measuring with [layout]. */
internal fun <T : Component> attachedChild(
    owner: TestCompositionOwner,
    component: T,
    layout: MeasurementLayoutManager = RecordingMeasurementLayout(),
): SwingNodeHolder<T> {
    val root = JPanel(layout)
    val child = CreatedNodeHolder(component).attachedTo(owner)
    root.add(child.component)
    child.declaration.attachedUnder(root)
    return child
}

internal class RecordingLayoutNodeElement(
    private val value: String,
    private val events: MutableList<String>,
    /** Keyed by value unless given a key, so two declared together hold two slots. */
    override val key: Any = value,
    private val onAttach: (RecordingLayoutNode) -> Unit = {},
) : ParentLayoutNodeElement<RecordingLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun create(): RecordingLayoutNode {
        events += "create($value)"
        return RecordingLayoutNode(events, onAttach)
    }

    override fun update(node: RecordingLayoutNode) {
        events += "update($value)"
    }

    override fun equals(other: Any?): Boolean = other is RecordingLayoutNodeElement && other.value == value

    override fun hashCode(): Int = value.hashCode()
}

internal class RecordingLayoutNode(
    private val events: MutableList<String>,
    private val onAttachHook: (RecordingLayoutNode) -> Unit,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun onAttach() {
        events += "onAttach"
        onAttachHook(this)
    }

    override fun onDetach() {
        events += "onDetach"
    }

    override fun onReset() {
        events += "onReset"
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

/** A chain member recording, at each lifecycle step, how many nodes of its own chain are attached. */
internal class ChainLayoutNodeElement(
    private val label: String,
    private val chain: MutableList<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : ParentLayoutNodeElement<ChainLayoutNode>() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    /** Keyed by label, so two declared together hold two slots. */
    override val key: Any get() = label

    override fun create(): ChainLayoutNode = ChainLayoutNode(label, chain, sightings).also { chain += it }

    override fun update(node: ChainLayoutNode) = Unit

    override fun equals(other: Any?): Boolean = other is ChainLayoutNodeElement && other.label == label

    override fun hashCode(): Int = label.hashCode()
}

internal class ChainLayoutNode(
    private val label: String,
    private val chain: List<SwingModifier.Node>,
    private val sightings: MutableList<String>,
) : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override fun onAttach() = sight("onAttach")

    override fun onReset() = sight("onReset")

    override fun onDetach() = sight("onDetach")

    private fun sight(step: String) {
        sightings += "$step $label: ${chain.count { it.isAttached }} of ${chain.size}"
    }
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

/** A layout node element whose protocol accepts any host, named apart from the class of the node it creates. */
private data object AnyHostLayoutNodeElement : ParentLayoutNodeElement<AnyHostLayoutNode>() {
    override val name: String get() = "anyHostLayout"

    override val parentProtocol: ParentProtocol = parentProtocolOf("any host") { true }

    override fun create(): AnyHostLayoutNode = AnyHostLayoutNode()

    override fun update(node: AnyHostLayoutNode) = Unit
}

private class AnyHostLayoutNode : ParentLayoutNode() {
    override val parentProtocol: ParentProtocol get() = AnyHostLayoutNodeElement.parentProtocol
}

internal data class TestParentLayoutElement(
    override val name: String,
) : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = TestMeasurementParentProtocol

    override val key: Any get() = name
}

internal val TestMeasurementParentProtocol = MeasurementLayoutManager.parentProtocol("test measurement parent")

private data object BorderOnlyParentData : ParentProtocol {
    override val description: String get() = "BorderLayout parent data"

    override fun accepts(parent: Container): Boolean = parent.layout is BorderLayout
}
