package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [reconcileWithChildren] and [ReconcileWithChildrenScope.restoreDeclaredPlacement] - the public
 * mechanism that replaced the internal `settleWithChildren` and `ToolBar`'s own reach into
 * `ParentDeclaration`.
 */
class ReconcileWithChildrenTest {
    @Test
    fun multipleReconcileWithChildrenRegistrationsRunForTheSameNode() = runComposeSwingTest {
        val events = mutableListOf<String>()
        var tick by mutableIntStateOf(0)
        setContent {
            Panel(PanelLayout.Border()) {
                SwingNode(
                    factory = { JLabel("reconciled") },
                    update = {
                        tick
                        reconcileWithChildren { events += "first" }
                        reconcileWithChildren { events += "second" }
                    },
                )
            }
        }

        events.clear()
        tick++
        awaitIdle()

        assertEquals(listOf("first", "second"), events)
    }

    @Test
    fun subsequentRecompositionReplacesPriorPassChildSettle() = runComposeSwingTest {
        val events = mutableListOf<String>()
        var mode by mutableStateOf("initial")
        var childCount by mutableIntStateOf(1)
        setContent {
            Panel(PanelLayout.Border()) {
                val declaredMode = mode
                SwingNode(
                    factory = { JPanel() },
                    update = {
                        reconcileWithChildren { events += declaredMode }
                    },
                ) {
                    repeat(childCount) { index ->
                        SwingNode(factory = { JLabel("child-$index") })
                    }
                }
            }
        }

        events.clear()
        mode = "updated"
        childCount = 2
        awaitIdle()

        assertEquals(listOf("updated"), events)
    }

    @Test
    fun restoreDeclaredPlacementPutsTheComponentBackUnderItsDeclaredRegion() = runComposeSwingTest {
        var tick by mutableIntStateOf(0)
        setContent {
            Panel(PanelLayout.Border()) {
                SwingNode(
                    factory = { JLabel("moved") },
                    modifier = SwingModifier.north(),
                    update = {
                        tick
                        reconcileWithChildren { restoreDeclaredPlacement() }
                    },
                )
            }
        }
        val label = onNodeOfType<JLabel>().fetch()
        val panel = label.parent as Container
        val layout = panel.layout as BorderLayout
        assertSame(label, layout.getLayoutComponent(BorderLayout.NORTH), "the label starts in its declared region")

        // Something outside the composition - a look and feel docking a bar under another region, say -
        // moves the component, bypassing the applier entirely.
        panel.remove(label)
        panel.add(label, BorderLayout.SOUTH)
        assertSame(
            label,
            layout.getLayoutComponent(BorderLayout.SOUTH),
            "the move landed the label in the new region",
        )
        assertNull(layout.getLayoutComponent(BorderLayout.NORTH), "the move vacated the declared region")

        // The next pass that reconciles this node calls restoreDeclaredPlacement, which is the first
        // point where the runtime is in a position to put the component back.
        tick++
        awaitIdle()

        assertSame(
            label,
            layout.getLayoutComponent(BorderLayout.NORTH),
            "restoreDeclaredPlacement should undo the move",
        )
        assertNull(
            layout.getLayoutComponent(BorderLayout.SOUTH),
            "the region taken outside the composition should be vacated",
        )
    }

    @Test
    fun restoreDeclaredPlacementLeavesTheComponentAloneWhenItStandsInAnotherParent() = runComposeSwingTest {
        var tick by mutableIntStateOf(0)
        setContent {
            Panel(PanelLayout.Border()) {
                SwingNode(
                    factory = { JLabel("moved") },
                    modifier = SwingModifier.north(),
                    update = {
                        tick
                        reconcileWithChildren { restoreDeclaredPlacement() }
                    },
                )
            }
        }
        val label = onNodeOfType<JLabel>().fetch()
        val panel = label.parent as Container
        val elsewhere = JPanel()

        // The label leaves the container the composition put it in entirely, rather than moving to
        // another region of the same one.
        panel.remove(label)
        elsewhere.add(label)
        assertSame(elsewhere, label.parent, "the label now stands in a parent the composition never put it in")

        tick++
        awaitIdle()

        assertSame(
            elsewhere,
            label.parent,
            "restoreDeclaredPlacement should do nothing for a component standing in another parent",
        )
    }

    @Test
    fun restoreDeclaredPlacementLeavesAnUnconstrainedComponentAlone() = runComposeSwingTest {
        var tick by mutableIntStateOf(0)
        setContent {
            Panel(PanelLayout.Border()) {
                SwingNode(
                    factory = { JLabel("unconstrained") },
                    update = {
                        tick
                        reconcileWithChildren { restoreDeclaredPlacement() }
                    },
                )
            }
        }
        val label = onNodeOfType<JLabel>().fetch()
        val panel = label.parent as Container
        val layout = panel.layout as BorderLayout

        panel.remove(label)
        panel.add(label, BorderLayout.SOUTH)
        assertSame(label, layout.getLayoutComponent(BorderLayout.SOUTH))

        tick++
        awaitIdle()

        assertSame(
            label,
            layout.getLayoutComponent(BorderLayout.SOUTH),
            "restoreDeclaredPlacement should do nothing for an unconstrained component",
        )
        assertNull(
            layout.getLayoutComponent(BorderLayout.CENTER),
            "the unconstrained component should remain outside the default region",
        )
    }
}
