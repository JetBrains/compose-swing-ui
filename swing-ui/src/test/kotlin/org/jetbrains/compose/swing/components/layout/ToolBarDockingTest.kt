package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.HierarchyEvent
import javax.swing.JFrame
import javax.swing.JToolBar
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Where a [ToolBar] stands once it is docked.
 *
 * Docking is the look and feel's own move: it puts the bar back into the container it came from, under
 * a `BorderLayout` region it picks itself. That region is not the composition's, so the bar is settled
 * back under the one its own modifier declares. A bar docked on a side edge is turned to face along it,
 * and is turned back to the orientation declared.
 *
 * The host is a [PanelLayout.Border] panel throughout, which is what `BasicToolBarUI` is written against: it names
 * the region it docks under itself, and falls back to `BorderLayout.NORTH` for a container whose manager
 * reads no such thing. A bar the user can drag out of any other container is refused as it is composed.
 *
 * Floating needs a window to open the bar's own beside, so these cases realize one and skip without a
 * display. They run under Metal, whose tool bars drag - a look and feel need not.
 */
class ToolBarDockingTest {
    @Test
    fun aBarMountingIntoItsPanelIsPlacedOnce() = runComposeSwingTest {
        val ownMoves = mutableListOf<Container?>()
        setContent {
            Panel(PanelLayout.Border()) {
                ToolBar(
                    modifier =
                        SwingModifier.north().hierarchyListener { event ->
                            val moved = event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L
                            if (moved && event.changed === event.component) ownMoves += event.component.parent
                        },
                ) {
                    Button(text = "New", onClick = {})
                }
                Label(text = "below", modifier = SwingModifier.center())
            }
        }
        awaitIdle()

        assertEquals(
            1,
            ownMoves.size,
            "mounting the bar should add it and nothing more; a settle that took the applier's own add " +
                "for a move of the look and feel's would take the bar out and put it back: $ownMoves",
        )
    }

    @Test
    fun aBarTheUserDocksBackFromItsWindowStandsWhereItIsComposed() = underMetal {
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var floating by mutableStateOf(false)
            setContent {
                Window(onCloseRequest = {}, title = USER_DOCK_WINDOW_TITLE, visible = false) {
                    Panel(PanelLayout.Border()) {
                        ToolBar(
                            modifier = SwingModifier.north(),
                            floating = floating,
                            onFloatingChange = { floating = it },
                        ) {
                            Button(text = "New", onClick = {})
                        }
                        Label(text = "below", modifier = SwingModifier.center())
                    }
                }
            }
            val frame = onWindowWithTitle(USER_DOCK_WINDOW_TITLE).fetch<JFrame>()
            val bar = assertNotNull(toolBarIn(frame), "the bar should mount into the panel")
            val panel = assertNotNull(bar.parent, "the bar should stand in the panel that declares it")

            floating = true
            awaitIdle()
            assertTrue(bar.isFloatingNow, "the declared float should move the bar into a window of its own")

            // What the look and feel does when the user closes the bar's window or drags the bar back in:
            // it docks the bar under the region it recorded before floating it, with nothing of the
            // wrapper's between the two.
            bar.toolBarUi.setFloating(false, null)
            awaitIdle()

            assertSame(
                bar,
                (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.NORTH),
                "the bar should dock back into the region it is composed in",
            )
        }
    }

    @Test
    fun aBarDockedBackIntoAPanelStandsWhereItIsComposed() = underMetal {
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var floating by mutableStateOf(false)
            setContent {
                // Composed `visible = false`: sizing to content realizes the peer, which is all the bar
                // needs to have a window to open its own beside.
                Window(onCloseRequest = {}, title = WINDOW_TITLE, visible = false) {
                    Panel(PanelLayout.Border()) {
                        ToolBar(modifier = SwingModifier.north(), floating = floating) {
                            Button(text = "New", onClick = {})
                        }
                        Label(text = "below", modifier = SwingModifier.center())
                    }
                }
            }
            val frame = onWindowWithTitle(WINDOW_TITLE).fetch<JFrame>()
            val bar = assertNotNull(toolBarIn(frame), "the bar should mount into the panel")
            val panel = assertNotNull(bar.parent, "the bar should stand in the panel that declares it")

            floating = true
            awaitIdle()
            assertTrue(bar.isFloatingNow, "the declared float should move the bar into a window of its own")

            floating = false
            awaitIdle()

            assertEquals(
                emptyList(),
                takeCallerFailures(),
                "docking a bar back should carry the region the composition declares to it, not the one " +
                    "the look and feel picked",
            )
            assertSame(
                bar,
                (panel.layout as BorderLayout).getLayoutComponent(BorderLayout.NORTH),
                "the bar should dock back into the region it is composed in",
            )
        }
    }

    @Test
    fun aBarDockedBackBesideAFloatingSiblingStandsWhereItIsComposed() = underMetal {
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var floating by mutableStateOf(false)
            setContent {
                Window(onCloseRequest = {}, title = SIBLING_WINDOW_TITLE, visible = false) {
                    Panel(PanelLayout.Border()) {
                        ToolBar(modifier = SwingModifier.north(), floating = floating) {
                            Button(text = "Cut", onClick = {})
                        }
                        ToolBar(modifier = SwingModifier.south()) { Button(text = "New", onClick = {}) }
                        Label(text = "below", modifier = SwingModifier.center())
                    }
                }
            }
            val frame = onWindowWithTitle(SIBLING_WINDOW_TITLE).fetch<JFrame>()
            val mounted = assertNotNull(toolBarIn(frame), "the bars should mount into the panel")
            val panel = assertNotNull(mounted.parent, "the bars should stand in the panel that declares them")
            val layout = panel.layout as BorderLayout
            val first = assertNotNull(layout.getLayoutComponent(BorderLayout.NORTH)) as JToolBar
            val second = assertNotNull(layout.getLayoutComponent(BorderLayout.SOUTH)) as JToolBar

            floating = true
            awaitIdle()
            assertTrue(first.isFloatingNow, "the declared float should move the first bar out")

            // The second bar's own drag out and back. The declaration still says docked, so the wrapper
            // settles this float back and places the bar.
            second.toolBarUi.setFloating(true, null)
            awaitIdle()
            assertFalse(second.isFloatingNow, "the unadopted float should settle back")

            assertSame(
                second,
                layout.getLayoutComponent(BorderLayout.SOUTH),
                "the docked bar should stand in the region it declares",
            )
        }
    }

    @Test
    fun aFloatableBarComposedAsATabPageIsRefused() = runComposeSwingTest {
        // A tab is reached through the pane's own setter rather than by adding to the pane, and the look
        // and feel docks by adding: a bar dragged out of a tab would come back as a tab of the look and
        // feel's own naming, with the tab it is composed in gone. So the bar is refused as it is composed.
        val refusal =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                        ToolBar(modifier = SwingModifier.tab(title = "Edit")) {
                            Button(text = "New", onClick = {})
                        }
                    }
                }
            }

        val message = refusal.message.orEmpty()
        assertTrue(
            "JToolBar" in message && "floatable = false" in message && "PanelLayout.Border" in message,
            "the refusal should name the bar and both ways out of it, but read: $message",
        )
    }

    @Test
    fun aBarDockedOntoASideEdgeSettlesBackToTheRegionAndOrientationDeclared() = underMetal {
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            setContent {
                Window(onCloseRequest = {}, title = EDGE_WINDOW_TITLE, visible = false) {
                    Panel(PanelLayout.Border()) {
                        ToolBar(modifier = SwingModifier.north(), orientation = SwingConstants.HORIZONTAL) {
                            Button(text = "New", onClick = {})
                        }
                        Label(text = "a center wide enough to leave an edge on either side of it")
                    }
                }
            }
            val frame = onWindowWithTitle(EDGE_WINDOW_TITLE).fetch<JFrame>()
            val bar = assertNotNull(toolBarIn(frame), "the bar should mount into the panel")
            val panel = assertNotNull(bar.parent, "the bar should stand in the panel that declares it")
            val edge = bar.height
            // The look and feel reads a drop within the bar's own height of a side as a drop onto that
            // edge, so the point below lands on the west edge only while the panel is bigger than that.
            assertTrue(
                panel.height > edge + 1 && panel.width > edge + 1,
                "the panel should be laid out larger than the edge strip, but was ${panel.size}",
            )

            // A drop needs a docking source, which the look and feel records as it floats the bar out.
            bar.toolBarUi.setFloating(true, null)
            awaitIdle()
            assertFalse(bar.isFloatingNow, "the unadopted float should settle back before the drop below")

            // What the look and feel's own drag handler does when the user drags a docked bar straight
            // onto the panel's left edge. It leaves the floating state alone, so this move alone is what
            // has to provoke the pass that answers.
            bar.toolBarUi.setFloating(false, Point(1, edge + 1))
            assertEquals(
                SwingConstants.VERTICAL,
                bar.orientation,
                "the look and feel should have docked the bar on the west edge and turned it to face " +
                    "along it, which is the change the pass below settles",
            )

            awaitIdle()

            assertEquals(
                SwingConstants.HORIZONTAL,
                bar.orientation,
                "the bar should face the way it is declared to, not the way the edge it was dropped on " +
                    "would have it",
            )
            val layout = panel.layout as BorderLayout
            assertSame(
                bar,
                layout.getLayoutComponent(BorderLayout.NORTH),
                "the bar should hold the region its modifier declares, not the one it was dropped on",
            )
            assertNull(
                layout.getLayoutComponent(BorderLayout.WEST),
                "putting the declared region back should take the dropped-on one away, since a manager " +
                    "holding the bar under two regions lays it out in whichever it reaches first",
            )
        }
    }

    @Test
    fun aFloatRefusedForWantOfAWindowLeavesAnEarlierUnansweredMoveStanding() = runComposeSwingTest {
        var floating by mutableStateOf(false)
        setContent {
            Panel(PanelLayout.Border()) {
                ToolBar(modifier = SwingModifier.north(), floating = floating, onFloatingChange = {}) {
                    Button(text = "New", onClick = {})
                }
                Label(text = "below", modifier = SwingModifier.center())
            }
        }
        val bar = onNodeOfType<JToolBar>().fetch()
        val panel = assertNotNull(bar.parent, "the bar should stand in the panel that declares it")
        val layout = panel.layout as BorderLayout

        // A move the wrapper did not make - the shape a look and feel's own dock produces, taking the bar
        // out of where it stood and adding it back under a region it names itself - leaves an outstanding
        // placement to answer.
        panel.remove(bar)
        panel.add(bar, BorderLayout.WEST)

        // Composed with no window anywhere in the tree, so the float this declares next has nowhere to
        // open a window beside and is refused.
        floating = true
        awaitIdle()

        assertSame(
            bar,
            layout.getLayoutComponent(BorderLayout.NORTH),
            "a float the wrapper refuses should leave the earlier move standing rather than clear it, so " +
                "the bar is put back under the region it is composed in",
        )
    }

    private companion object {
        const val WINDOW_TITLE = "tool-bar-docking-test"
        const val USER_DOCK_WINDOW_TITLE = "tool-bar-user-docking-test"
        const val EDGE_WINDOW_TITLE = "tool-bar-edge-docking-test"
        const val SIBLING_WINDOW_TITLE = "tool-bar-sibling-docking-test"
    }
}
