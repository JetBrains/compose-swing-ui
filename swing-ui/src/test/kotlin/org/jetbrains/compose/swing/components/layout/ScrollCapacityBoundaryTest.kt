package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollCapacityBoundaryTest {
    @TestFactory
    fun capacityFollowsContentAndViewportBounds(): List<DynamicTest> =
        listOf("fitting", "unbound", "both", "horizontal", "vertical").flatMap { overflow ->
            listOf(
                "LTR" to ComponentOrientation.LEFT_TO_RIGHT,
                "RTL" to ComponentOrientation.RIGHT_TO_LEFT,
            ).map { (direction, orientation) ->
                val size =
                    when (overflow) {
                        "both" -> Dimension(500, 400)
                        "horizontal" -> Dimension(500, 30)
                        "vertical" -> Dimension(100, 400)
                        else -> Dimension(75, 24)
                    }
                val case =
                    "overflow=$overflow direction=$direction content=${size.width}x${size.height} " +
                        "bound=${overflow != "unbound"}"
                dynamicTest(case) {
                    assertCapacity(overflow, orientation, size, case)
                }
            }
        }

    private fun assertCapacity(
        overflow: String,
        orientation: ComponentOrientation,
        size: Dimension,
        case: String,
    ) = runComposeSwingTest {
        lateinit var state: ScrollState
        setContent {
            state =
                rememberScrollState(
                    x = if (overflow == "unbound") -20 else 0,
                    y = if (overflow == "unbound") 30 else 0,
                )
            if (overflow != "unbound") {
                ScrollPane(
                    modifier = SwingModifier.preferredSize(300, 200).componentOrientation(orientation),
                    state = state,
                ) {
                    Viewport {
                        SwingNode(factory = { scrollContent(overflow, size) })
                    }
                }
            }
        }
        if (overflow == "unbound") {
            assertRoom(state, listOf(false, false, false, false), "$case: an unbound state must have no capacity")
        } else if (overflow == "fitting") {
            assertTrue(state.viewWidth < state.extentWidth, "$case: the table must fit horizontally")
            assertTrue(state.viewHeight < state.extentHeight, "$case: the table must fit vertically")
            if (!orientation.isLeftToRight) {
                assertTrue(state.x < 0, "$case: native RTL alignment must use a negative view coordinate")
            }
            assertRoom(state, listOf(false, false, false, false), "$case: fully visible content must have no capacity")
        } else {
            val pane = onNodeOfType<JScrollPane>().fetch()
            val horizontal = overflow != "vertical"
            val vertical = overflow != "horizontal"
            pane.viewport.viewPosition = Point(0, 0)
            assertRoom(
                state,
                listOf(horizontal, false, vertical, false),
                "$case: the origin must have room only ahead on overflowing axes",
            )
            if (overflow == "both") {
                pane.viewport.viewPosition = Point(100, 100)
                assertRoom(
                    state,
                    listOf(true, true, true, true),
                    "$case: the middle must have room in both directions on both axes",
                )
            }
            pane.viewport.viewPosition = Point(if (horizontal) state.maxX else 0, if (vertical) state.maxY else 0)
            assertRoom(
                state,
                listOf(false, horizontal, false, vertical),
                "$case: the end must have room only behind on overflowing axes",
            )
        }
    }

    private fun scrollContent(
        overflow: String,
        size: Dimension,
    ): Component = if (overflow == "fitting") {
        JTable(DefaultTableModel(1, 1)).apply {
            autoResizeMode = JTable.AUTO_RESIZE_OFF
            preferredSize = size
        }
    } else {
        JPanel().apply { preferredSize = size }
    }

    private fun assertRoom(
        state: ScrollState,
        expected: List<Boolean>,
        message: String,
    ) {
        assertEquals(
            expected,
            listOf(
                state.canScrollForwardX,
                state.canScrollBackwardX,
                state.canScrollForwardY,
                state.canScrollBackwardY,
            ),
            message,
        )
    }
}
