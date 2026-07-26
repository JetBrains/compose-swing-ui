package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.LayoutManager
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Regression guard for the nested constrained-layout crash.
 *
 * A container [org.jetbrains.compose.swing.SwingNode] reads [BorderLayout]'s region constraint from
 * `LocalSwingConstraint` for its OWN placement when it sits inside a [BorderPanel] region. The defect
 * was that it did not reset that local for its CONTENT, so every child of, say, a [GridBagPanel] placed
 * in the CENTER region also inherited the `"Center"` constraint string. The applier then called
 * `gridBagContainer.add(child, "Center", index)`, and `GridBagLayout.addLayoutComponent` throws
 * `IllegalArgumentException` ("cannot add to layout: constraints must be a GridBagConstraint"). The fix
 * resets `LocalSwingConstraint` to its default for the subtree, exactly like `LocalSlotAttachment`, so
 * each child is placed by the inner panel's own layout manager with the default (null) constraint.
 *
 * Every assertion reads the real AWT tree (parent/child relationships, layout managers,
 * [BorderLayout.getLayoutComponent]); none touches internal bookkeeping.
 */
class NestedLayoutConstraintTest {
    /** Resolves the single component with [text], failing with a tree dump otherwise. */
    private fun ComposeSwingTest.componentWithText(text: String): Component = onNodeWithText(text).fetch<Component>()

    /** The single [GridBagPanel]'s backing [JPanel] (the [JPanel] whose layout is a [GridBagLayout]). */
    private fun ComposeSwingTest.gridBagPanel(): JPanel = singlePanelWithLayout<GridBagLayout>()

    @Test
    fun gridBagPanelInsideBorderPanelCenterComposesWithoutCrashing() = runComposeSwingTest {
        // Before the fix this throws inside the applier (GridBagLayout rejects the "Center" string),
        // so setContent itself surfaces the crash and fails the test.
        setContent {
            BorderPanel {
                center {
                    GridBagPanel {
                        Label(text = "one")
                        Label(text = "two")
                    }
                }
            }
        }

        val gridBag = gridBagPanel()

        // The GridBagPanel is the BorderPanel's CENTER child.
        val borderPanel = gridBag.parent as JPanel
        val borderLayout = borderPanel.layout as BorderLayout
        assertSame(
            gridBag,
            borderLayout.getLayoutComponent(BorderLayout.CENTER),
            "GridBagPanel is not the BorderLayout CENTER child",
        )

        // Its two labels are its own children, placed by GridBagLayout (default constraint).
        val labels =
            (onAllNodesWithText("one").fetchAll<Component>() + onAllNodesWithText("two").fetchAll<Component>())
                .filter { SwingUtilities.isDescendingFrom(it, gridBag) }
        assertEquals(2, gridBag.componentCount, "GridBagPanel should hold exactly two children")
        assertTrue(
            labels.all { it.parent === gridBag },
            "Both labels must be direct children of the GridBagPanel",
        )
    }

    @Test
    fun switchingBorderCenterBetweenLeafAndNestedPanelNeverCarriesStaleConstraint() = runComposeSwingTest {
        // Models the sample's section switching: the BorderPanel CENTER toggles between a leaf
        // Label and a nested GridBagPanel { ... }. The reused/replaced node must not carry a stale
        // BorderLayout constraint into the nested panel's children on any recomposition.
        var nested by mutableStateOf(false)
        setContent {
            BorderPanel {
                center {
                    if (nested) {
                        GridBagPanel {
                            Label(text = "nestedChild")
                        }
                    } else {
                        Label(text = "leaf")
                    }
                }
            }
        }

        // Leaf is the CENTER child.
        assertEquals(BorderLayout.CENTER, centerConstraintOf("leaf"), "the leaf should start at CENTER")
        onNodeWithText("nestedChild").assertDoesNotExist()

        // Switch to the nested panel: must compose without exception and place the child by the
        // inner GridBagLayout.
        nested = true
        awaitIdle()
        onNodeWithText("leaf").assertDoesNotExist()
        assertNestedChildLaidOutByGridBag()

        // Switch back to the leaf.
        nested = false
        awaitIdle()
        onNodeWithText("nestedChild").assertDoesNotExist()
        assertEquals(
            BorderLayout.CENTER,
            centerConstraintOf("leaf"),
            "the leaf should return to CENTER after switching back",
        )

        // And forward again to prove the cycle is stable.
        nested = true
        awaitIdle()
        onNodeWithText("leaf").assertDoesNotExist()
        assertNestedChildLaidOutByGridBag()
    }

    @Test
    fun gridAndGridBagChildrenInsideBorderRegionsUseTheDefaultConstraint() = runComposeSwingTest {
        // A GridPanel and a GridBagPanel placed in different BorderPanel regions. Each must add its
        // own children with the DEFAULT constraint (children present, no per-child BorderLayout
        // string), i.e. the children are laid out by the inner panel rather than rejected.
        setContent {
            BorderPanel {
                north {
                    GridPanel(rows = 1, cols = 2) {
                        Label(text = "g1")
                        Label(text = "g2")
                    }
                }
                center {
                    GridBagPanel {
                        Label(text = "b1")
                        Label(text = "b2")
                    }
                }
            }
        }

        // GridPanel: its JPanel uses a GridLayout, holds both labels, and the region constraint
        // applies only to the GridPanel itself (NORTH), not to its children.
        val grid = singlePanelWithLayout<GridLayout>()
        assertEquals(
            BorderLayout.NORTH,
            (grid.parent as JPanel).borderConstraintOf(grid),
            "the grid panel should sit at NORTH",
        )
        assertChildrenAre(grid, "g1", "g2")

        // GridBagPanel: its JPanel uses a GridBagLayout, holds both labels, region is CENTER.
        val gridBag = gridBagPanel()
        assertEquals(
            BorderLayout.CENTER,
            (gridBag.parent as JPanel).borderConstraintOf(gridBag),
            "the grid-bag panel should sit at CENTER",
        )
        assertChildrenAre(gridBag, "b1", "b2")
    }

    /** The [BorderLayout] constraint [text]'s component was placed at, within its [BorderPanel] parent. */
    private fun ComposeSwingTest.centerConstraintOf(text: String): Any? {
        val component = componentWithText(text)
        val parent = component.parent as JPanel
        return (parent.layout as BorderLayout).getConstraints(component)
    }

    /** Asserts the single nested child sits under the [GridBagPanel], placed by its [GridBagLayout]. */
    private fun ComposeSwingTest.assertNestedChildLaidOutByGridBag() {
        val child = componentWithText("nestedChild")
        val gridBag = child.parent as JPanel
        assertTrue(gridBag.layout is GridBagLayout, "nested child's parent is not a GridBagLayout panel")
        return assertSame(gridBag, gridBagPanel(), "nested child is not under the GridBagPanel")
    }

    /** The single [JPanel] whose layout is of type [L]. */
    private inline fun <reified L : LayoutManager> ComposeSwingTest.singlePanelWithLayout(): JPanel {
        val matches = onAllNodesOfType<JPanel>().fetchAll<JPanel>().filter { it.layout is L }
        return matches.singleOrNull()
            ?: throw AssertionError(
                "Expected exactly one ${L::class.simpleName} JPanel but found ${matches.size}.",
            )
    }

    /** The [BorderLayout] constraint [child] was added with, read from this [BorderLayout] parent. */
    private fun JPanel.borderConstraintOf(child: Component): Any? = (layout as BorderLayout).getConstraints(child)

    /** Asserts [container] holds exactly the labels with [texts], each a direct child. */
    private fun ComposeSwingTest.assertChildrenAre(
        container: Container,
        vararg texts: String,
    ) {
        assertEquals(texts.size, container.componentCount, "unexpected child count under $container")
        for (text in texts) {
            val matches =
                onAllNodesWithText(text)
                    .fetchAll<Component>()
                    .filter { SwingUtilities.isDescendingFrom(it, container) }
            val child =
                matches.singleOrNull()
                    ?: throw AssertionError(
                        "Expected exactly one child with text \"$text\" under the panel but found " +
                            "${matches.size}.",
                    )
            assertSame(container, child.parent, "child \"$text\" is not a direct child of the panel")
        }
    }
}
