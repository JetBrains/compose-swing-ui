package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.image.BufferedImage
import javax.swing.JSplitPane
import javax.swing.LookAndFeel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [SplitPane]. They assert what an observer of the live [JSplitPane] sees: each
 * declared side becomes the matching left/right (top/bottom) component, dropping a side clears it,
 * orientation and resize weight map through, the divider position is controlled while a user drag
 * fires the callback, and the divider size and one-touch flag follow the look and feel until the
 * caller declares them.
 */
class SplitPaneBehaviorTest {
    @Test
    fun declaredSidesBecomeTheLeftAndRightComponents() = runComposeSwingTest {
        setContent {
            SplitPane(orientation = JSplitPane.HORIZONTAL_SPLIT) {
                first { Label(text = "Leading") }
                second { Label(text = "Trailing") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
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

        val pane = onNodeOfType<JSplitPane>().fetch()
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

        val pane = onNodeOfType<JSplitPane>().fetch()
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

        val pane = onNodeOfType<JSplitPane>().fetch()
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

        assertEquals(0.25, onNodeOfType<JSplitPane>().fetch().resizeWeight)
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

        val pane = onNodeOfType<JSplitPane>().fetch()
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
            SplitPane(
                modifier = SwingModifier.preferredSize(400, 300),
                onDividerLocationChange = { reported += it },
            ) {
                first { Label(text = firstLabel) }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        // A divider is only ever on screen, draggable, at a resolved position; painting resolves the
        // default negative request the same way realizing the pane on screen would.
        val image = BufferedImage(root.width, root.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        root.paint(graphics)
        graphics.dispose()
        awaitIdle()
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
    fun aNegativeDividerLocationIsWrittenThroughWithoutBeingReported() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var location by mutableIntStateOf(200)
        setContent {
            SplitPane(dividerLocation = location, onDividerLocationChange = { reported += it }) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(200, pane.dividerLocation, "the divider should start at the explicit location")
        reported.clear()

        // A negative offset asks the pane to derive the divider position from the sides' preferred
        // sizes. The pane keeps the request itself as its dividerLocation - the position the sides
        // resolve to is the look and feel's to compute and is never published as the property - so the
        // request is what stands here, and it reaches the pane without being mistaken for a user drag.
        location = -1
        awaitIdle()

        assertEquals(-1, pane.dividerLocation, "the pane holds the request the declaration made of it")
        assertTrue(reported.isEmpty(), "a declared reset must not be reported as if the user made it")
    }

    @Test
    fun aNegativeDividerLocationResolvedByPaintingIsNotReportedAsAUserMove() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            SplitPane(
                modifier = SwingModifier.preferredSize(400, 300),
                onDividerLocationChange = { reported += it },
            ) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        reported.clear()

        // A negative divider location resolves only once the pane is painted; painting the
        // (unrealized, peer-less) test root offscreen is enough to trigger the same resolution a
        // realized window would, publishing it as an ordinary dividerLocation property change.
        val image = BufferedImage(root.width, root.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        root.paint(graphics)
        graphics.dispose()
        awaitIdle()

        assertTrue(reported.isEmpty(), "the pane settling a negative request must not be reported as a user move")

        // A genuine drag afterwards is measured against the position the pane resolved to, and still reports.
        val dragged = pane.dividerLocation + 30
        pane.dividerLocation = dragged
        awaitIdle()

        assertEquals(dragged, reported.last(), "a drag made after the resolution must still report")
    }

    @Test
    fun declaringANewDividerLocationDoesNotInvokeOnDividerLocationChange() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var location by mutableIntStateOf(120)
        setContent {
            SplitPane(dividerLocation = location, onDividerLocationChange = { reported += it }) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        onNodeOfType<JSplitPane>().fetch()
        reported.clear()

        // JSplitPane fires its dividerLocation property change synchronously on every write,
        // including this one; a caller that echoed every event back would see its own declaration
        // reported as if the user had dragged to it.
        location = 200
        awaitIdle()

        assertTrue(reported.isEmpty(), "a declared change must not be reported as if the user made it")
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

        val pane = onNodeOfType<JSplitPane>().fetch()
        reported.clear()

        // A user drag of the divider is observable as a dividerLocation property change.
        pane.dividerLocation = 175
        awaitIdle()

        assertEquals(175, reported.last(), "dragging the divider should report the new location")
        assertEquals(175, pane.dividerLocation, "the divider should land at the dragged location")
    }

    @Test
    fun undeclaredDividerSizeAndOneTouchFlagFollowTheLookAndFeel() = runComposeSwingTest {
        setContent {
            SplitPane {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        val bare = JSplitPane()
        assertEquals(bare.dividerSize, pane.dividerSize, "the divider size should start at the look-and-feel size")
        assertEquals(
            bare.isOneTouchExpandable,
            pane.isOneTouchExpandable,
            "the one-touch flag should start at the look-and-feel value",
        )

        // A look and feel installs either property only onto a pane that has never had it set
        // explicitly, so an undeclared property must leave the pane as receptive as a bare widget.
        val installedSize = bare.dividerSize + 7
        val installedOneTouch = !bare.isOneTouchExpandable
        for (target in listOf(pane, bare)) {
            LookAndFeel.installProperty(target, "dividerSize", installedSize)
            LookAndFeel.installProperty(target, "oneTouchExpandable", installedOneTouch)
        }
        assertEquals(installedSize, pane.dividerSize, "the pane should accept a look-and-feel divider size")
        assertEquals(
            installedOneTouch,
            pane.isOneTouchExpandable,
            "the pane should accept a look-and-feel one-touch flag",
        )

        SwingUtilities.updateComponentTreeUI(pane)
        SwingUtilities.updateComponentTreeUI(bare)
        assertEquals(bare.dividerSize, pane.dividerSize, "the divider size should still track a bare widget")
        assertEquals(
            bare.isOneTouchExpandable,
            pane.isOneTouchExpandable,
            "the one-touch flag should still track a bare widget",
        )
    }

    @Test
    fun dividerSizeMapsThrough() = runComposeSwingTest {
        var size by mutableIntStateOf(12)
        setContent {
            SplitPane(dividerSize = size) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(12, pane.dividerSize, "the pane should start at the declared divider size")

        size = 20
        awaitIdle()
        assertEquals(20, pane.dividerSize, "the divider size should follow the declared value")

        LookAndFeel.installProperty(pane, "dividerSize", 41)
        assertEquals(20, pane.dividerSize, "a declared divider size should outrank the look and feel")
    }

    @Test
    fun oneTouchExpandableMapsThrough() = runComposeSwingTest {
        var expandable by mutableStateOf(true)
        setContent {
            SplitPane(oneTouchExpandable = expandable) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertTrue(pane.isOneTouchExpandable, "the pane should start with the declared one-touch flag")

        expandable = false
        awaitIdle()
        assertFalse(pane.isOneTouchExpandable, "the one-touch flag should follow the declared value")

        LookAndFeel.installProperty(pane, "oneTouchExpandable", true)
        assertFalse(pane.isOneTouchExpandable, "a declared one-touch flag should outrank the look and feel")
    }
}
