package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanelScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExistingSwingNodeTest {
    @Test
    fun theNodeConfiguresTheComponentItsParentHolds() = runComposeSwingTest {
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode(
                    claim = PartsPanel::part,
                    modifier = SwingModifier.background(Color.RED),
                    update = { set("declared") { toolTipText = it } },
                )
            }
        }

        val parent = onNodeOfType<PartsPanel>().fetch()
        assertSame(parent, parent.part.parent, "the claimed component should stay where its parent put it")
        assertEquals(Color.RED, parent.part.background, "the modifier should reach the claimed component")
        assertEquals("declared", parent.part.toolTipText, "the update block should reach the claimed component")
    }

    @Test
    fun leavingGivesBackWhatTheModifierDeclaredAndRemovesTheComposedChildren() = runComposeSwingTest {
        var claimed by mutableStateOf(true)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                if (claimed) {
                    ExistingSwingNode(
                        claim = PartsPanel::pane,
                        modifier = SwingModifier.background(Color.RED),
                        update = { set("declared") { toolTipText = it } },
                    ) {
                        Label("child")
                    }
                }
            }
        }
        val parent = onNodeOfType<PartsPanel>().fetch()
        val index = parent.getComponentZOrder(parent.pane)
        assertEquals(1, parent.pane.componentCount, "the content should compose into the claimed container")

        claimed = false
        awaitIdle()

        assertEquals(
            PartsPanel().pane.background,
            parent.pane.background,
            "the declared background should be given back",
        )
        assertEquals(0, parent.pane.componentCount, "the children the content composed should be removed")
        assertSame(parent, parent.pane.parent, "the claimed container should stay in its parent")
        assertEquals(index, parent.getComponentZOrder(parent.pane), "the claimed container should keep its index")
        assertEquals("declared", parent.pane.toolTipText, "what the update block wrote should stay")
    }

    @Test
    fun aParkedClaimLeavesTheComponentInPlaceAndAReactivatedOneClaimsItAgain() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ReusableContentHost(active = active) {
                    ExistingSwingNode(claim = PartsPanel::part, modifier = SwingModifier.background(Color.RED))
                }
            }
        }
        val parent = onNodeOfType<PartsPanel>().fetch()
        val original = PartsPanel().part.background

        active = false
        awaitIdle()

        assertSame(parent, parent.part.parent, "a parked claim should leave the component where its parent holds it")
        assertEquals(original, parent.part.background, "a parked claim should give the declared background back")

        active = true
        awaitIdle()

        assertEquals(Color.RED, parent.part.background, "the reactivated content should claim the component again")
    }

    @Test
    fun aClaimComposedUnderAnotherTypeOfParentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Panel { ExistingSwingNode(claim = PartsPanel::part) }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("claims its component from a PartsPanel, but it is composed under a JPanel"),
            "the refusal should name the parent type the claim needs and the one it found: $message",
        )
    }

    @Test
    fun aClaimThatFindsNothingIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    SwingNode(factory = { PartsPanel() }) {
                        ExistingSwingNode<PartsPanel, JLabel>(claim = { null })
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("found no component: its claim returned null on the JPanel"),
            "the refusal should say the claim found nothing on its parent: $message",
        )
    }

    @Test
    fun aClaimThatFindsAnotherTypeIsRefused() = runComposeSwingTest {
        // A claim erased to another result type is what a caller's unchecked cast hands over.
        @Suppress("UNCHECKED_CAST")
        val claim = PartsPanel::part as PartsPanel.() -> JButton?
        val message =
            failureOf {
                setContent {
                    SwingNode(factory = { PartsPanel() }) { ExistingSwingNode(claim = claim) }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("claims a JButton, but its claim returned a JLabel from the JPanel"),
            "the refusal should name the expected type and what was found: $message",
        )
    }

    @Test
    fun aChangedClaimIsNotCalledAndTheNodeKeepsItsComponent() = runComposeSwingTest {
        var laterCalls = 0
        val later: PartsPanel.() -> JLabel = {
            laterCalls++
            other
        }
        var changed by mutableStateOf(false)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode(
                    claim = if (changed) later else PartsPanel::part,
                    modifier = SwingModifier.background(if (changed) Color.BLUE else Color.RED),
                )
            }
        }
        val parent = onNodeOfType<PartsPanel>().fetch()
        val original = parent.other.background

        changed = true
        awaitIdle()

        assertEquals(0, laterCalls, "a claim changed after the node was created should not be called")
        assertEquals(Color.BLUE, parent.part.background, "the node should go on configuring the component it claimed")
        assertEquals(original, parent.other.background, "the component the changed claim names should be left alone")
    }

    @Test
    fun aSecondNodeClaimingTheSameComponentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    SwingNode(factory = { PartsPanel() }) {
                        ExistingSwingNode(claim = PartsPanel::part)
                        ExistingSwingNode(claim = PartsPanel::part)
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("A JLabel is declared twice at once"),
            "the refusal should say the component is claimed twice: $message",
        )
    }

    @Test
    fun aNodeReplacingTheClaimUnderAChangedKeyIsRefused() = runComposeSwingTest {
        // The replacing node arrives before the node it replaces is released, so both would configure the
        // component. An on-demand composition reports a failure of its pass to the caller that drove it.
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        var generation by mutableIntStateOf(0)
        val composition =
            OnDemandComposition(parentContext) {
                SwingNode(factory = { PartsPanel() }) {
                    key(generation) { ExistingSwingNode(claim = PartsPanel::part) }
                }
            }
        try {
            val message = failureOf { composition.recompose { generation = 1 } }

            assertTrue(
                message.contains("A JLabel is declared twice at once"),
                "the refusal should say the component is claimed twice: $message",
            )
        } finally {
            composition.dispose()
        }
    }

    @Test
    fun aClaimRelocatedByMovableContentIsNotASecondClaim() = runComposeSwingTest {
        var first by mutableStateOf(true)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                val claim =
                    remember {
                        movableContentOf {
                            ExistingSwingNode(claim = PartsPanel::part, modifier = SwingModifier.background(Color.RED))
                        }
                    }
                if (first) claim()
                Label("between")
                if (!first) claim()
            }
        }
        val parent = onNodeOfType<PartsPanel>().fetch()

        first = false
        awaitIdle()

        assertSame(
            parent,
            parent.part.parent,
            "the relocated claim should leave the component where its parent holds it",
        )
        assertEquals(Color.RED, parent.part.background, "the relocated claim should go on configuring the component")
    }

    @Test
    fun keyingOnAReplacedPartMovesTheDeclarationsToIt() = runComposeSwingTest {
        var current by mutableStateOf<JLabel?>(null)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                key(current) {
                    ExistingSwingNode(claim = PartsPanel::part, modifier = SwingModifier.background(Color.RED))
                }
            }
        }
        val parent = onNodeOfType<PartsPanel>().fetch()
        val replaced = parent.part
        val original = PartsPanel().part.background

        current = parent.replacePart()
        awaitIdle()

        assertEquals(Color.RED, parent.part.background, "the new part should carry the declarations")
        assertEquals(original, replaced.background, "the replaced part should get its background back")
    }

    @Test
    fun aClaimedContainerIsLaidOutTheWayPanelLaysOutItsOwn() = runComposeSwingTest {
        var claimed by mutableStateOf(true)
        var border by mutableStateOf(false)
        var hgap by mutableIntStateOf(5)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                if (claimed) {
                    val layout: PanelLayout<*> = if (border) PanelLayout.Border() else PanelLayout.Flow(hgap = hgap)
                    ExistingSwingNode(claim = PartsPanel::pane, update = { layout.installOn(this) }) {
                        key(layout.javaClass) {
                            when (val scope = layout.contentScope) {
                                is BorderPanelScope -> with(scope) { Label("child", SwingModifier.north()) }
                                else -> Label("child")
                            }
                        }
                    }
                }
            }
        }
        val pane = onNodeOfType<PartsPanel>().fetch().pane
        val flow = assertIs<FlowLayout>(pane.layout, "the declared layout should be installed")
        val flowChild = pane.getComponent(0)

        hgap = 9
        awaitIdle()

        assertSame(flow, pane.layout, "a parameter change should update the standing manager in place")
        assertEquals(9, flow.hgap, "a parameter change should reach the manager")

        border = true
        awaitIdle()

        val borderLayout = assertIs<BorderLayout>(pane.layout, "a kind change should install the new manager")
        val borderChild = pane.getComponent(0)
        assertNotSame(flowChild, borderChild, "a kind change should build the children anew")
        assertSame(
            borderChild,
            borderLayout.getLayoutComponent(BorderLayout.NORTH),
            "a child's scoped placement call should reach the manager",
        )

        claimed = false
        awaitIdle()

        assertEquals(0, pane.componentCount, "leaving should remove the children the content composed")
        assertSame(borderLayout, pane.layout, "the layout the update block installed should stay")
    }

    @Test
    fun anIndexedHostNeverAddsRemovesOrMovesAClaimedComponent() = runComposeSwingTest {
        var order by mutableStateOf(listOf("A", CLAIM, "B"))
        setContent {
            SwingNode(factory = { LonePartPanel() }) {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) ExistingSwingNode(claim = LonePartPanel::part) else Label(name)
                    }
                }
            }
        }
        val parent = onNodeOfType<LonePartPanel>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.part)
        assertEquals(
            listOf("A", "B", "part"),
            parent.texts(),
            "the composed siblings should be placed in composition order ahead of the part their parent placed",
        )

        order = listOf("A", "B")
        awaitIdle()
        assertEquals(listOf("A", "B", "part"), parent.texts(), "removing the claim should leave the component in place")

        order = listOf("B", CLAIM, "A")
        awaitIdle()
        assertEquals(listOf("B", "A", "part"), parent.texts(), "reordering should move only the composed siblings")

        order = listOf("B", CLAIM, "C", "A")
        awaitIdle()
        assertEquals(
            listOf("B", "C", "A", "part"),
            parent.texts(),
            "a child inserted after the claim should land in composition order among the composed siblings",
        )

        assertSame(parent, parent.part.parent, "the claimed component should stay in its parent")
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")
    }

    @Test
    fun aJLayeredPaneDepthNeverCountsAClaimedComponent() = runComposeSwingTest {
        var order by mutableStateOf(listOf("A", CLAIM, "B"))
        setContent {
            SwingNode(factory = { LayeredPartPane() }) {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) {
                            ExistingSwingNode(claim = LayeredPartPane::part)
                        } else {
                            Label(name, SwingModifier.layoutConstraint(PART_LAYER))
                        }
                    }
                }
            }
        }
        val parent = onNodeOfType<LayeredPartPane>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.part)
        assertEquals(
            listOf("A", "B", "part"),
            parent.texts(),
            "the composed siblings should be placed in depth order ahead of the part their parent placed",
        )

        order = listOf("B", CLAIM, "A")
        awaitIdle()
        assertEquals(listOf("B", "A", "part"), parent.texts(), "reordering should move only the composed siblings")

        order = listOf("B", CLAIM, "C", "A")
        awaitIdle()
        assertEquals(
            listOf("B", "C", "A", "part"),
            parent.texts(),
            "a child inserted after the claim should land in composition order among the composed siblings",
        )

        assertSame(parent, parent.part.parent, "the claimed component should stay in its parent")
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")
    }

    @Test
    fun aClaimReorderedAmongCreatedSiblingsStaysWhereItsParentHoldsIt() = runComposeSwingTest {
        var order by mutableStateOf(listOf("A", CLAIM, "B"))
        setContent {
            SwingNode(factory = { LonePartPanel() }) {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) ExistingSwingNode(claim = LonePartPanel::part) else Label(name)
                    }
                }
            }
        }
        val parent = onNodeOfType<LonePartPanel>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.part)

        order = listOf(CLAIM, "A", "B")
        awaitIdle()
        order = listOf("B", "A", CLAIM)
        awaitIdle()

        assertEquals(listOf("B", "A", "part"), parent.texts(), "only the composed siblings should follow the order")
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")
    }

    @Test
    fun aCreatedChildInsertedBetweenAClaimAndASiblingAfterAReorderLandsInCompositionOrder() = runComposeSwingTest {
        var order by mutableStateOf(listOf("A", CLAIM, "B"))
        setContent {
            SwingNode(factory = { LonePartPanel() }) {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) ExistingSwingNode(claim = LonePartPanel::part) else Label(name)
                    }
                }
            }
        }
        val parent = onNodeOfType<LonePartPanel>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.part)

        order = listOf("B", CLAIM, "A")
        awaitIdle()
        order = listOf("B", CLAIM, "C", "A")
        awaitIdle()

        assertEquals(
            listOf("B", "C", "A", "part"),
            parent.texts(),
            "the inserted child should land between the siblings it was composed between",
        )
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")
    }

    @Test
    fun aClaimReorderedInARegionHostStaysWhereItsParentHoldsIt() = runComposeSwingTest {
        var order by mutableStateOf(listOf(CLAIM, "body"))
        setContent {
            Framed {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) {
                            FramedHeader()
                        } else {
                            Label(name, SwingModifier.slot(FramedProtocol, BODY_REGION, BodyAttachment))
                        }
                    }
                }
            }
        }
        val parent = onNodeOfType<FramedPanel>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.header)

        order = listOf("body", CLAIM)
        awaitIdle()

        val layout = parent.layout as BorderLayout
        assertSame(parent.header, layout.getLayoutComponent(BorderLayout.NORTH), "the header should stay where it was")
        assertEquals("body", (layout.getLayoutComponent(BorderLayout.CENTER) as JLabel).text)
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")
    }

    @Test
    fun aClaimReorderedInAnOrderedRegionHostStaysWhereItsParentHoldsIt() = runComposeSwingTest {
        var order by mutableStateOf(listOf("A", CLAIM, "B"))
        setContent {
            SwingNode(factory = { LonePartPanel() }, childPlacement = ChildPlacement.OrderedSlots(ITEM_REGION)) {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) {
                            ExistingSwingNode(
                                claim = LonePartPanel::part,
                                modifier = SwingModifier.slot(StripProtocol, ITEM_REGION),
                            )
                        } else {
                            Label(name, SwingModifier.slot(StripProtocol, ITEM_REGION, ItemAttachment))
                        }
                    }
                }
            }
        }
        val parent = onNodeOfType<LonePartPanel>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.part)

        order = listOf(CLAIM, "A", "B")
        awaitIdle()
        order = listOf(CLAIM, "C", "A", "B")
        awaitIdle()

        assertEquals(
            listOf("C", "A", "B", "part"),
            parent.texts(),
            "the composed items should stand in composition order ahead of the part their parent placed",
        )
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")
    }

    @Test
    fun aLooseChildBesideAClaimIsRefusedWithTheRegions() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Framed {
                        FramedHeader()
                        Label("loose")
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains(
                "The JLabel declared here names none. Name the region it fills through one of: " +
                    "$HEADER_REGION, $BODY_REGION.",
            ),
            "the refusal should list the regions: $message",
        )
    }

    @Test
    fun aClaimAsTheTopLevelChildOfATabHeaderIsRefused() = runComposeSwingTest {
        val part = JLabel("part")
        val message =
            failureOf {
                setContent {
                    TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                        Label(
                            "body",
                            SwingModifier.tab(
                                "General",
                                header = { ExistingSwingNode<Container, JLabel>(claim = { part }) },
                            ),
                        )
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains(
                "A JLabel claimed by ExistingSwingNode cannot be the one top-level component of this content",
            ),
            "the refusal should say a claim cannot be the content's unnamed child: $message",
        )
    }

    @Test
    fun aClaimUnderACallerHostWithContentIsRefusedNamingTheHost() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    SwingNode(
                        factory = { PartsPanel() },
                        childPlacement = ChildPlacement.Slots(content = ItemAttachment),
                    ) {
                        ExistingSwingNode(claim = PartsPanel::part)
                    }
                }
                awaitIdle()
            }

        assertEquals(
            "A JLabel claimed by ExistingSwingNode cannot be the unnamed child of a JPanel, which installs that " +
                "child as its content while the claimed component's parent already holds it. Its ChildPlacement " +
                "offers no region to name, so the part cannot be claimed under it.",
            message,
            "the refusal should name the host, which is not the composition's root",
        )
    }

    @Test
    fun parentDataOnAClaimIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    SwingNode(factory = { PartsPanel() }) {
                        ExistingSwingNode(
                            claim = PartsPanel::part,
                            modifier = SwingModifier.layoutConstraint(BorderLayout.NORTH),
                        )
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains(
                "SwingModifier.layoutConstraint() places a component in its parent, but this ExistingSwingNode " +
                    "configures a JLabel its parent JPanel placed. Declare placement where the parent builds the " +
                    "component.",
            ),
            "the refusal should say the parent placed the component: $message",
        )
    }

    @Test
    fun parentDataAStandingClaimComesToDeclareIsRefused() = runComposeSwingTest {
        var constrained by mutableStateOf(false)
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode(
                    claim = PartsPanel::part,
                    modifier =
                        SwingModifier.background(Color.RED).let {
                            if (constrained) it.layoutConstraint(BorderLayout.NORTH) else it
                        },
                )
            }
        }
        awaitIdle()
        val parent = onNodeOfType<PartsPanel>().fetch()
        assertEquals(Color.RED, parent.part.background, "the claim should stand and configure its part first")

        val message =
            failureOf {
                constrained = true
                awaitIdle()
            }

        assertTrue(
            message.contains(
                "SwingModifier.layoutConstraint() places a component in its parent, but this ExistingSwingNode " +
                    "configures a JLabel its parent JPanel placed.",
            ),
            "the refusal should say the parent placed the component: $message",
        )
    }

    @Test
    fun aClaimRelocatedUnderAnotherParentIsRefused() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        setContent {
            val claim = remember { movableContentOf { ExistingSwingNode(claim = PartsPanel::part) } }
            SwingNode(factory = { PartsPanel() }) { if (!inSecond) claim() }
            SwingNode(factory = { PartsPanel() }) { if (inSecond) claim() }
        }
        awaitIdle()

        val message =
            failureOf {
                inSecond = true
                awaitIdle()
            }

        assertTrue(
            message.contains(
                "A JLabel claimed by ExistingSwingNode was placed by a JPanel, but the node was relocated under " +
                    "another JPanel, which did not place it. Keep the claim under the node whose component holds " +
                    "the part, or key it on the part there.",
            ),
            "the refusal should say the node was relocated under a parent that did not place its component: $message",
        )
    }

    @Test
    fun aClaimWithNoSwingParentIsNoIndexSpaceFailure() = runComposeSwingTest {
        // The harness mounts every composition with SwingCompositionDiagnostics, so the index-space walk
        // runs after each pass and would report a composed child that stands in no container.
        val loose = JLabel("loose")
        setContent {
            SwingNode(factory = { PartsPanel() }) {
                ExistingSwingNode<PartsPanel, JLabel>(
                    claim = { loose },
                    modifier = SwingModifier.background(Color.RED),
                )
                Label("sibling")
            }
        }
        awaitIdle()

        assertNull(loose.parent, "the claimed component should stay outside any container")
        assertEquals(Color.RED, loose.background, "the modifier should reach the claimed component")
    }
}

private const val CLAIM = "claim"
private const val ITEM_REGION = "SwingModifier.item()"

private class PartsPanel : JPanel() {
    var part: JLabel = JLabel("part")
        private set
    val other: JLabel = JLabel("other")
    val pane: JPanel = JPanel()

    init {
        add(part)
        add(other)
        add(pane)
    }

    /** Replaces [part] the way a component replaces a part it owns. */
    fun replacePart(): JLabel {
        val index = getComponentZOrder(part)
        remove(part)
        part = JLabel("replacement")
        add(part, index)
        return part
    }
}

private class LonePartPanel : JPanel() {
    val part: JLabel = JLabel("part")

    init {
        add(part)
    }

    fun texts(): List<String> = components.map { (it as JLabel).text }
}

private const val PART_LAYER = 5

private class LayeredPartPane : JLayeredPane() {
    val part: JLabel = JLabel("part")

    init {
        add(part, PART_LAYER as Any)
    }

    fun texts(): List<String> = components.map { (it as JLabel).text }
}

/** Records every time [child] is added to or removed from this container. */
internal fun Container.recordAddsAndRemovesOf(child: Component): List<String> {
    val events = ArrayList<String>()
    addContainerListener(
        object : ContainerListener {
            override fun componentAdded(e: ContainerEvent) {
                if (e.child === child) events += "added"
            }

            override fun componentRemoved(e: ContainerEvent) {
                if (e.child === child) events += "removed"
            }
        },
    )
    return events
}

private val StripProtocol: ParentProtocol = parentProtocolOf("LonePartPanel item") { it is LonePartPanel }

private val ItemAttachment =
    SlotAttachment { host, component, index ->
        host.add(component, index)
        return@SlotAttachment { host.remove(component) }
    }
