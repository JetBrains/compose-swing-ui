package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Behavioral tests for the [BorderPanel] scope-based DSL.
 *
 * Every assertion reads the real AWT tree: each region's child must be attached to the panel's
 * [BorderLayout] under the matching constraint. The two families - absolute compass
 * (`north`/`south`/`east`/`west`/`center`) and orientation-aware
 * (`pageStart`/`pageEnd`/`lineStart`/`lineEnd`) - both map onto their own BorderLayout fields, so the
 * tests confirm the constraint Swing actually recorded rather than any internal slot bookkeeping.
 */
class BorderPanelDslTest {
    @Test
    fun eachCompassRegionPlacesItsChildAtTheMatchingConstraint() = runComposeSwingTest {
        setContent {
            BorderPanel {
                north { Label(text = "N") }
                south { Label(text = "S") }
                east { Label(text = "E") }
                west { Label(text = "W") }
                center { Label(text = "C") }
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
                pageStart { Label(text = "PS") }
                pageEnd { Label(text = "PE") }
                lineStart { Label(text = "LS") }
                lineEnd { Label(text = "LE") }
                center { Label(text = "C") }
            }
        }

        onNodeWithText("PS").assertLayoutConstraint(BorderLayout.PAGE_START)
        onNodeWithText("PE").assertLayoutConstraint(BorderLayout.PAGE_END)
        onNodeWithText("LS").assertLayoutConstraint(BorderLayout.LINE_START)
        onNodeWithText("LE").assertLayoutConstraint(BorderLayout.LINE_END)
        onNodeWithText("C").assertLayoutConstraint(BorderLayout.CENTER)
    }

    @Test
    fun redeclaringARegionReplacesItsChildWithTheLastDeclaration() = runComposeSwingTest {
        setContent {
            BorderPanel {
                north { Label(text = "first") }
                north { Label(text = "second") }
            }
        }

        // The last declaration wins: only "second" is attached, at NORTH; "first" never appears.
        onNodeWithText("first").assertDoesNotExist()
        onNodeWithText("second").assertLayoutConstraint(BorderLayout.NORTH)
    }

    @Test
    fun droppingARegionClearsItsChildWhileSiblingsKeepTheirConstraints() = runComposeSwingTest {
        var showNorth by mutableStateOf(true)
        setContent {
            BorderPanel {
                if (showNorth) {
                    north { Label(text = "N") }
                }
                center { Label(text = "C") }
            }
        }

        val center = onNodeWithText("C")
        onNodeWithText("N").assertLayoutConstraint(BorderLayout.NORTH)
        center.assertLayoutConstraint(BorderLayout.CENTER)

        showNorth = false
        awaitIdle()

        // The dropped region's child is gone and the panel no longer reports a NORTH child; the
        // surviving CENTER child keeps its constraint.
        onNodeWithText("N").assertDoesNotExist()
        center.assertLayoutConstraint(BorderLayout.CENTER)
        val layout = center.onParent().fetch<JPanel>().layout as BorderLayout
        assertNull(
            layout.getLayoutComponent(BorderLayout.NORTH),
            "NORTH region still holds a child after the region was dropped",
        )
    }

    @Test
    fun swappingARegionsChildKeepsItAttachedAtTheSameConstraint() = runComposeSwingTest {
        var flag by mutableStateOf(true)
        setContent {
            BorderPanel {
                center {
                    if (flag) Label(text = "First") else Label(text = "Second")
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
