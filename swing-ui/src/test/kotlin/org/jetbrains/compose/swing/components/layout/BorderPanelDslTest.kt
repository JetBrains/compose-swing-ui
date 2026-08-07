package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral tests for the regions a [BorderPanelScope] gives a [BorderPanel]'s children.
 *
 * Every assertion reads the real AWT tree: a child that names a region must be attached to the panel's
 * [BorderLayout] under the matching constraint. The two families - absolute compass
 * (`north`/`south`/`east`/`west`/`center`) and orientation-aware
 * (`pageStart`/`pageEnd`/`lineStart`/`lineEnd`) - both map onto their own BorderLayout fields.
 *
 * A child that names no region is held to the same reading: the panel hands it to BorderLayout with no
 * constraint, and BorderLayout is what puts it in the center region.
 */
class BorderPanelDslTest {
    @Test
    fun aChildNamingNoRegionOccupiesTheCenterWhileSiblingsKeepTheirRegions() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label(text = "header", modifier = SwingModifier.north())
                Label(text = "body")
            }
        }

        val body = onNodeWithText("body")
        body.assertLayoutConstraint(BorderLayout.CENTER)
        val layout = body.onParent().fetch<JPanel>().layout as BorderLayout
        assertSame(
            body.fetch<JLabel>(),
            layout.getLayoutComponent(BorderLayout.CENTER),
            "CENTER region does not hold the child that named no region",
        )

        onNodeWithText("header").assertLayoutConstraint(BorderLayout.NORTH)
    }

    @Test
    fun eachCompassRegionPlacesItsChildAtTheMatchingConstraint() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label(text = "N", modifier = SwingModifier.north())
                Label(text = "S", modifier = SwingModifier.south())
                Label(text = "E", modifier = SwingModifier.east())
                Label(text = "W", modifier = SwingModifier.west())
                Label(text = "C", modifier = SwingModifier.center())
            }
        }

        onNodeWithText("N").assertLayoutConstraint(BorderLayout.NORTH)
        onNodeWithText("S").assertLayoutConstraint(BorderLayout.SOUTH)
        onNodeWithText("E").assertLayoutConstraint(BorderLayout.EAST)
        onNodeWithText("W").assertLayoutConstraint(BorderLayout.WEST)
        onNodeWithText("C").assertLayoutConstraint(BorderLayout.CENTER)
    }

    @Test
    fun eachOrientationAwareRegionPlacesItsChildAtTheMatchingConstraint() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label(text = "PS", modifier = SwingModifier.pageStart())
                Label(text = "PE", modifier = SwingModifier.pageEnd())
                Label(text = "LS", modifier = SwingModifier.lineStart())
                Label(text = "LE", modifier = SwingModifier.lineEnd())
                Label(text = "C", modifier = SwingModifier.center())
            }
        }

        onNodeWithText("PS").assertLayoutConstraint(BorderLayout.PAGE_START)
        onNodeWithText("PE").assertLayoutConstraint(BorderLayout.PAGE_END)
        onNodeWithText("LS").assertLayoutConstraint(BorderLayout.LINE_START)
        onNodeWithText("LE").assertLayoutConstraint(BorderLayout.LINE_END)
        onNodeWithText("C").assertLayoutConstraint(BorderLayout.CENTER)
    }

    @Test
    fun theLastRegionNamedInAChainIsTheOneTheChildOccupies() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label(text = "child", modifier = SwingModifier.north().south())
            }
        }

        val child = onNodeWithText("child")
        child.assertLayoutConstraint(BorderLayout.SOUTH)

        // The region the chain names first is left holding nothing at all - a child occupies one region.
        val layout = child.onParent().fetch<JPanel>().layout as BorderLayout
        assertNull(
            layout.getLayoutComponent(BorderLayout.NORTH),
            "NORTH region holds a child although the chain went on to name SOUTH",
        )
    }

    @Test
    fun droppingAChildClearsItsRegionWhileSiblingsKeepTheirConstraints() = runComposeSwingTest {
        var showNorth by mutableStateOf(true)
        setContent {
            BorderPanel {
                if (showNorth) {
                    Label(text = "N", modifier = SwingModifier.north())
                }
                Label(text = "C", modifier = SwingModifier.center())
            }
        }

        val center = onNodeWithText("C")
        onNodeWithText("N").assertLayoutConstraint(BorderLayout.NORTH)
        center.assertLayoutConstraint(BorderLayout.CENTER)

        showNorth = false
        awaitIdle()

        onNodeWithText("N").assertDoesNotExist()
        center.assertLayoutConstraint(BorderLayout.CENTER)
        val layout = center.onParent().fetch<JPanel>().layout as BorderLayout
        assertNull(
            layout.getLayoutComponent(BorderLayout.NORTH),
            "NORTH region still holds a child after the child that named it was dropped",
        )
    }

    @Test
    fun aRegionNamedConditionallyIsReleasedWhenItLeavesTheChain() = runComposeSwingTest {
        var placeNorth by mutableStateOf(true)
        setContent {
            BorderPanel {
                Label(text = "child", modifier = if (placeNorth) SwingModifier.north() else SwingModifier)
                Label(text = "S", modifier = SwingModifier.south())
            }
        }

        val child = onNodeWithText("child")
        child.assertLayoutConstraint(BorderLayout.NORTH)

        placeNorth = false
        awaitIdle()

        // The child stays in the panel; only its placement is given up, so NORTH is free again and the
        // sibling that named its own region is untouched.
        child.assertExists()
        onNodeWithText("S").assertLayoutConstraint(BorderLayout.SOUTH)
        val layout = child.onParent().fetch<JPanel>().layout as BorderLayout
        assertNull(
            layout.getLayoutComponent(BorderLayout.NORTH),
            "NORTH region still holds a child after the placement left that child's chain",
        )
    }

    @Test
    fun swappingARegionsChildKeepsItAttachedAtTheSameConstraint() = runComposeSwingTest {
        var flag by mutableStateOf(true)
        setContent {
            BorderPanel {
                if (flag) {
                    Label(text = "First", modifier = SwingModifier.center())
                } else {
                    Label(text = "Second", modifier = SwingModifier.center())
                }
            }
        }

        onNodeWithText("First").assertLayoutConstraint(BorderLayout.CENTER)

        flag = false
        awaitIdle()

        onNodeWithText("First").assertDoesNotExist()
        onNodeWithText("Second").assertLayoutConstraint(BorderLayout.CENTER)
    }
}
