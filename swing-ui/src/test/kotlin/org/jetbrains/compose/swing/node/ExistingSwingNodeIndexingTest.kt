package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** An [ExistingSwingNode] never adds, removes or moves the component its parent holds, whatever its siblings do. */
class ExistingSwingNodeIndexingTest {
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
        val itemRegion = "SwingModifier.item()"
        var order by mutableStateOf(listOf("A", CLAIM, "B"))
        setContent {
            SwingNode(factory = { LonePartPanel() }, childPlacement = ChildPlacement.OrderedSlots(itemRegion)) {
                for (name in order) {
                    key(name) {
                        if (name == CLAIM) {
                            ExistingSwingNode(
                                claim = LonePartPanel::part,
                                modifier = SwingModifier.slot(StripProtocol, itemRegion),
                            )
                        } else {
                            Label(name, SwingModifier.slot(StripProtocol, itemRegion, ItemAttachment))
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
}

private const val CLAIM = "claim"

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

private val StripProtocol: ParentProtocol = parentProtocolOf("LonePartPanel item") { it is LonePartPanel }
