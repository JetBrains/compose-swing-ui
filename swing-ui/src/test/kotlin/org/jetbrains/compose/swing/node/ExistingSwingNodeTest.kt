package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanelScope
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.JLabel
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
                    modifier = SwingModifier.styleable { background(Color.RED) },
                    update = { set("declared") { toolTipText = it } },
                )
            }
        }

        val parent = onNodeOfType<PartsPanel>().fetch()
        assertSame(parent, parent.part.parent, "the claimed component should stay where its parent put it")
        assertEquals(Color.RED, parent.part.background, "the style should reach the claimed component")
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
                        modifier = SwingModifier.styleable { background(Color.RED) },
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
                    ExistingSwingNode(
                        claim = PartsPanel::part,
                        modifier = SwingModifier.styleable { background(Color.RED) },
                    )
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
                                header = { ExistingSwingNode<Container, _, JLabel>(claim = { part }) },
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
            "A JLabel claimed by ExistingSwingNode cannot be the unnamed child of a PartsPanel, which installs that " +
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
                    "configures a JLabel its parent PartsPanel placed. Declare placement where the parent builds the " +
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
                        (if (constrained) SwingModifier.layoutConstraint(BorderLayout.NORTH) else SwingModifier)
                            .styleable { background(Color.RED) },
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
                    "configures a JLabel its parent PartsPanel placed.",
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
                "A JLabel claimed by ExistingSwingNode was placed by a PartsPanel, but the node was relocated under " +
                    "another PartsPanel, which did not place it. Keep the claim under the node whose component holds " +
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
                ExistingSwingNode<PartsPanel, _, JLabel>(
                    claim = { loose },
                    modifier = SwingModifier.styleable { background(Color.RED) },
                )
                Label("sibling")
            }
        }
        awaitIdle()

        assertNull(loose.parent, "the claimed component should stay outside any container")
        assertEquals(Color.RED, loose.background, "the style should reach the claimed component")
    }
}

internal class PartsPanel : JPanel() {
    var part: JLabel = JLabel("part")
        private set
    val wide: Component get() = part
    val other: JLabel = JLabel("other")
    val pane: JPanel = JPanel()
    val widePane: Component get() = pane

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

internal val ItemAttachment =
    SlotAttachment { host, component, index ->
        host.add(component, index)
        return@SlotAttachment { host.remove(component) }
    }
