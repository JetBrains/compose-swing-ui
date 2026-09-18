package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.ComponentOrientation
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * The increments a [ScrollPane]'s content declares through [ScrollPaneScope.viewport] are set on the
 * pane's scroll bars, and the content stays the viewport's own view, so it scrolls and lays out as it
 * does in a plain `JScrollPane`.
 */
class ScrollPaneIncrementsTest {
    @Test
    fun declaredIncrementsAreTheOnesBothScrollBarsScrollBy() = runComposeSwingTest {
        var unitIncrement by mutableStateOf(17)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                Label(
                    text = "Body",
                    modifier =
                        SwingModifier
                            .preferredSize(400, 400)
                            .viewport(unitIncrement = unitIncrement, blockIncrement = 130),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertSame(onNodeWithText("Body").fetch(), pane.viewport.view, "the content is the viewport's view")
        for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            assertEquals(17, bar.getUnitIncrement(1), "one unit scrolls by the declared increment")
            assertEquals(130, bar.getBlockIncrement(1), "one page scrolls by the declared increment")
        }

        unitIncrement = 33
        awaitIdle()

        for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            assertEquals(33, bar.getUnitIncrement(1), "an increment declared later replaces it")
        }
    }

    @Test
    fun anIncrementDeclaredLaterAsksForNoLayout() = runComposeSwingTest {
        var unitIncrement by mutableStateOf(17)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                Label(text = "Body", modifier = SwingModifier.viewport(unitIncrement = unitIncrement))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        // An increment is read only as the user scrolls, so nothing is measured or placed differently.
        withRecordedRepaints { recorded ->
            unitIncrement = 33
            awaitIdle()

            assertEquals(
                0,
                recorded.relayoutsOver(pane),
                "an increment change revalidates neither the pane nor its ancestors: ${recorded.relayouts}",
            )
        }
        assertEquals(33, pane.verticalScrollBar.getUnitIncrement(1))
    }

    @Test
    fun anUndeclaredIncrementIsTheContentsOwn() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                TextArea(
                    value = "line\n".repeat(40),
                    onValueChange = {},
                    modifier = SwingModifier.viewport(blockIncrement = 130),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        assertEquals(
            area.getScrollableUnitIncrement(pane.viewport.viewRect, SwingConstants.VERTICAL, 1),
            pane.verticalScrollBar.getUnitIncrement(1),
            "the pane scrolls by the line the area answers with",
        )
        assertEquals(130, pane.verticalScrollBar.getBlockIncrement(1), "and by the declared page")
    }

    @Test
    fun contentDeclaringNoIncrementScrollsAsInAPlainScrollPane() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                Label(
                    text = "Body",
                    modifier = SwingModifier.preferredSize(400, 400).viewport(),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(1, pane.verticalScrollBar.getUnitIncrement(1), "scrolls by 1 per unit")
        assertEquals(
            pane.viewport.extentSize.height,
            pane.verticalScrollBar.getBlockIncrement(1),
            "and a full viewport per page",
        )
    }

    @Test
    fun aWithdrawnIncrementIsTheContentsOwnAgainAndTheScrollPositionStays() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(17)
        val scroll = ScrollState(0, 0)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100), state = scroll) {
                TextArea(
                    value = "line\n".repeat(40),
                    onValueChange = {},
                    modifier = SwingModifier.viewport(unitIncrement = unitIncrement),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        scroll.y = 60
        awaitIdle()

        unitIncrement = null
        awaitIdle()

        assertEquals(
            area.getScrollableUnitIncrement(pane.viewport.viewRect, SwingConstants.VERTICAL, 1),
            pane.verticalScrollBar.getUnitIncrement(1),
            "withdrawing the increment hands the unit back to the area",
        )
        assertEquals(60, pane.viewport.viewPosition.y, "the viewport stays where it was scrolled to")
        assertEquals(60, pane.verticalScrollBar.value, "and the scroll bar shows that position")
    }

    @Test
    fun withdrawingTheUnitIncrementKeepsTheDeclaredBlockIncrement() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(17)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                TextArea(
                    value = "line\n".repeat(40),
                    onValueChange = {},
                    modifier = SwingModifier.viewport(unitIncrement = unitIncrement, blockIncrement = 130),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        unitIncrement = null
        awaitIdle()

        assertEquals(
            area.getScrollableUnitIncrement(pane.viewport.viewRect, SwingConstants.VERTICAL, 1),
            pane.verticalScrollBar.getUnitIncrement(1),
            "the withdrawn unit is the area's own again",
        )
        for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            assertEquals(130, bar.getBlockIncrement(1), "the block increment still declared stays")
        }
    }

    @Test
    fun withdrawingTheBlockIncrementKeepsTheDeclaredUnitIncrement() = runComposeSwingTest {
        var blockIncrement by mutableStateOf<Int?>(130)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                TextArea(
                    value = "line\n".repeat(40),
                    onValueChange = {},
                    modifier = SwingModifier.viewport(unitIncrement = 17, blockIncrement = blockIncrement),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        blockIncrement = null
        awaitIdle()

        assertEquals(
            area.getScrollableBlockIncrement(pane.viewport.viewRect, SwingConstants.VERTICAL, 1),
            pane.verticalScrollBar.getBlockIncrement(1),
            "the withdrawn block is the area's own again",
        )
        for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            assertEquals(17, bar.getUnitIncrement(1), "the unit increment still declared stays")
        }
    }

    @Test
    fun aWithdrawnIncrementKeepsTheHorizontalScrollPosition() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(17)
        val scroll = ScrollState(0, 0)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100), state = scroll) {
                Label(
                    text = "Body",
                    modifier = SwingModifier.preferredSize(400, 400).viewport(unitIncrement = unitIncrement),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        scroll.x = 70
        awaitIdle()

        unitIncrement = null
        awaitIdle()

        assertEquals(70, pane.viewport.viewPosition.x, "the viewport stays where it was scrolled to")
        assertEquals(70, pane.horizontalScrollBar.value, "and the horizontal bar shows that position")
    }

    @Test
    fun aWithdrawnIncrementKeepsBothScrollBarsInThePanesOrientation() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(17)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                Label(
                    text = "Body",
                    modifier = SwingModifier.preferredSize(400, 400).viewport(unitIncrement = unitIncrement),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        pane.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT
        unitIncrement = null
        awaitIdle()

        assertFalse(pane.verticalScrollBar.componentOrientation.isLeftToRight, "the vertical bar stays right-to-left")
        assertFalse(
            pane.horizontalScrollBar.componentOrientation.isLeftToRight,
            "the horizontal bar stays right-to-left",
        )
    }

    @Test
    fun aTableDeclaringAnIncrementKeepsItsHeaderAndItsHeight() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(200, 100)) {
                Table(
                    model = DefaultTableModel(arrayOf(arrayOf<Any>("a", "b")), arrayOf<Any>("one", "two")),
                    modifier = SwingModifier.viewport(unitIncrement = 17),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val table = onNodeOfType<JTable>().fetch()
        assertSame(table.tableHeader, pane.columnHeader?.view, "the table installs its own header")
        assertEquals(
            table.preferredSize.height,
            table.height,
            "a table that does not fill the viewport's height keeps its own, as in a plain pane",
        )
        assertEquals(17, pane.verticalScrollBar.getUnitIncrement(1), "and scrolls as declared")
    }
}
