package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSplitPane
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral tests for [SplitPane]. They assert what an observer of the live [JSplitPane] sees: each
 * declared side becomes the matching left/right (top/bottom) component, dropping a side clears it,
 * orientation and resize weight map through, and the divider position is controlled while a user drag
 * fires the callback.
 */
class SplitPaneBehaviorTest {
    private fun ComposeSwingTest.splitPane(): JSplitPane = onNodeOfType<JSplitPane>().fetch()

    @Test
    fun declaredSidesBecomeTheLeftAndRightComponents() = runComposeSwingTest {
        setContent {
            SplitPane(orientation = JSplitPane.HORIZONTAL_SPLIT) {
                first { Label(text = "Leading") }
                second { Label(text = "Trailing") }
            }
        }

        val pane = splitPane()
        onNodeWithText("Leading").assertExists()
        onNodeWithText("Trailing").assertExists()
        // The first side is hosted as the left component, the second as the right component.
        assertSame(onNodeWithText("Leading").fetch(), pane.leftComponent, "the first side should be the left component")
        assertSame(
            onNodeWithText("Trailing").fetch(),
            pane.rightComponent,
            "the second side should be the right component",
        )
    }

    @Test
    fun droppingASideClearsThatSplitPaneComponent() = runComposeSwingTest {
        var showSecond by mutableStateOf(true)
        setContent {
            SplitPane {
                first { Label(text = "Leading") }
                if (showSecond) {
                    second { Label(text = "Trailing") }
                }
            }
        }

        val pane = splitPane()
        onNodeWithText("Trailing").assertExists()

        showSecond = false
        awaitIdle()

        onNodeWithText("Trailing").assertDoesNotExist()
        assertNull(pane.rightComponent, "right component leaked after the second side was dropped")
        // The remaining side is untouched.
        assertSame(
            onNodeWithText("Leading").fetch(),
            pane.leftComponent,
            "the surviving side should keep its component",
        )
    }

    @Test
    fun swappingASideUpdatesThatComponentInPlace() = runComposeSwingTest {
        var flag by mutableStateOf(true)
        setContent {
            SplitPane {
                first { if (flag) Label(text = "First") else Label(text = "Second") }
                second { Label(text = "Fixed") }
            }
        }

        val pane = splitPane()
        onNodeWithText("First").assertExists()

        flag = false
        awaitIdle()

        onNodeWithText("Second").assertExists()
        onNodeWithText("First").assertDoesNotExist()
        assertSame(
            onNodeWithText("Second").fetch(),
            pane.leftComponent,
            "swapping should update the left component in place",
        )
        // The unchanged side keeps its component.
        assertSame(onNodeWithText("Fixed").fetch(), pane.rightComponent, "the unchanged side should keep its component")
    }

    @Test
    fun orientationMapsThrough() = runComposeSwingTest {
        var orientation by mutableStateOf(JSplitPane.HORIZONTAL_SPLIT)
        setContent {
            SplitPane(orientation = orientation) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = splitPane()
        assertEquals(
            JSplitPane.HORIZONTAL_SPLIT,
            pane.orientation,
            "the pane should start with the horizontal orientation",
        )

        orientation = JSplitPane.VERTICAL_SPLIT
        awaitIdle()
        assertEquals(JSplitPane.VERTICAL_SPLIT, pane.orientation, "the orientation should map through to vertical")
    }

    @Test
    fun resizeWeightMapsThrough() = runComposeSwingTest {
        setContent {
            SplitPane(resizeWeight = 0.25) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        assertEquals(0.25, splitPane().resizeWeight)
    }

    @Test
    fun dividerLocationIsControlled() = runComposeSwingTest {
        var location by mutableIntStateOf(120)
        setContent {
            SplitPane(dividerLocation = location) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = splitPane()
        assertEquals(120, pane.dividerLocation, "the divider should start at the controlled location")

        location = 200
        awaitIdle()
        assertEquals(200, pane.dividerLocation, "the divider should follow the controlled location")
    }

    @Test
    fun defaultDividerLocationDoesNotFightAUserDrag() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var firstLabel by mutableStateOf("A")
        setContent {
            SplitPane(onDividerLocationChange = { reported += it }) {
                first { Label(text = firstLabel) }
                second { Label(text = "B") }
            }
        }

        val pane = splitPane()
        reported.clear()

        // A user drag of the divider is observable as a dividerLocation property change.
        pane.dividerLocation = 150
        awaitIdle()
        assertEquals(150, reported.last(), "the drag must flow through onDividerLocationChange")

        // An unrelated recomposition must not re-assert the default location over the drag.
        firstLabel = "A!"
        awaitIdle()
        assertEquals(150, pane.dividerLocation, "the divider must stay where the user dragged it")
    }

    @Test
    fun aNegativeDividerLocationAppliesTheDocumentedReset() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var location by mutableIntStateOf(200)
        setContent {
            SplitPane(
                dividerLocation = location,
                onDividerLocationChange = {
                    reported += it
                    location = it
                },
            ) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = splitPane()
        assertEquals(200, pane.dividerLocation, "the divider should start at the explicit location")

        // A negative offset is JSplitPane's documented request to re-derive the divider position
        // from the sides' preferred sizes.
        location = -1
        awaitIdle()

        assertContains(reported, -1, "an explicit -1 must be written through as the documented reset request")
        assertNotEquals(200, pane.dividerLocation, "the reset must move the divider off the explicit location")
    }

    @Test
    fun userDraggingTheDividerFiresOnDividerLocationChange() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var location by mutableIntStateOf(100)
        setContent {
            SplitPane(
                dividerLocation = location,
                onDividerLocationChange = {
                    reported += it
                    location = it
                },
            ) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = splitPane()
        reported.clear()

        // A user drag of the divider is observable as a dividerLocation property change.
        pane.dividerLocation = 175
        awaitIdle()

        assertEquals(175, reported.last(), "dragging the divider should report the new location")
        assertEquals(175, pane.dividerLocation, "the divider should land at the dragged location")
    }
}
