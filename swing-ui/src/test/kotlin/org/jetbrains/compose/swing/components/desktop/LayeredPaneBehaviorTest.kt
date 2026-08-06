package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.bounds
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral tests for [LayeredPane] over a real
 * [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier]. Each assertion reads the rendered
 * [JLayeredPane]: a declared child is hosted on it at its requested depth (`JLayeredPane.getLayer`),
 * children are added and removed dynamically as the composition changes, a child's layer re-applies on
 * recomposition, and disposing the pane tears it down.
 */
class LayeredPaneBehaviorTest {
    @Test
    fun eachDeclaredChildIsHostedOnTheLayeredPaneAtItsLayer() = runComposeSwingTest {
        setContent {
            LayeredPane {
                layer(JLayeredPane.DEFAULT_LAYER) { Label(text = "back") }
                layer(JLayeredPane.PALETTE_LAYER) { Label(text = "front") }
            }
        }

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            JLayeredPane.getLayer(onNodeWithText("back").fetch<JComponent>()),
            "the back child sits on the default layer",
        )
        assertEquals(
            JLayeredPane.PALETTE_LAYER,
            JLayeredPane.getLayer(onNodeWithText("front").fetch<JComponent>()),
            "the front child sits on the palette layer",
        )
    }

    @Test
    fun rawIntegerLayerIsHonored() = runComposeSwingTest {
        setContent {
            LayeredPane {
                layer(7) { Label(text = "seven") }
            }
        }

        assertEquals(
            7,
            JLayeredPane.getLayer(onNodeWithText("seven").fetch<JComponent>()),
            "a layer named by a raw integer is the one the child sits on",
        )
    }

    @Test
    fun boundsModifierPositionsAChildWithinTheLayeredPane() = runComposeSwingTest {
        setContent {
            LayeredPane {
                layer(JLayeredPane.DEFAULT_LAYER) {
                    Label(text = "fixed", modifier = SwingModifier.bounds(15, 25, 120, 40))
                }
            }
        }

        // A JLayeredPane has no layout manager, so the child keeps the bounds the modifier set.
        assertEquals(
            Rectangle(15, 25, 120, 40),
            onNodeOfType<JLabel>().fetch().bounds,
            "a child positioned by the bounds modifier keeps those bounds on the pane",
        )
    }

    @Test
    fun droppingAChildFromCompositionRemovesItDynamically() = runComposeSwingTest {
        var showTop by mutableStateOf(true)
        setContent {
            LayeredPane {
                layer(JLayeredPane.DEFAULT_LAYER) { Label(text = "base") }
                if (showTop) {
                    layer(JLayeredPane.PALETTE_LAYER) { Label(text = "top") }
                }
            }
        }

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)

        showTop = false
        awaitIdle()
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(1)
        onNodeWithText("top").assertDoesNotExist()
        onNodeWithText("base").assertExists()

        showTop = true
        awaitIdle()
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        onNodeWithText("top").assertExists()
    }

    @Test
    fun changingAChildsLayerReappliesItOnRecomposition() = runComposeSwingTest {
        var depth by mutableIntStateOf(JLayeredPane.DEFAULT_LAYER)
        setContent {
            LayeredPane {
                layer(depth) { Label(text = "mover") }
            }
        }

        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            JLayeredPane.getLayer(onNodeWithText("mover").fetch<JComponent>()),
            "the child should start on the default layer",
        )

        depth = JLayeredPane.DRAG_LAYER
        awaitIdle()
        assertEquals(
            JLayeredPane.DRAG_LAYER,
            JLayeredPane.getLayer(onNodeWithText("mover").fetch<JComponent>()),
            "child layer did not update on recomposition",
        )

        depth = JLayeredPane.DEFAULT_LAYER
        awaitIdle()
        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            JLayeredPane.getLayer(onNodeWithText("mover").fetch<JComponent>()),
            "the child follows its layer back down again",
        )
    }

    @Test
    fun aLayersContentFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var editing by mutableStateOf(false)
        setContent {
            // Which child the layer declares is decided at composition time, so every pass hands the
            // layer a different declaration: a layer that keeps the content it was first given would
            // go on showing the child it started with.
            val showsEditor = editing
            LayeredPane {
                layer(JLayeredPane.DEFAULT_LAYER) {
                    if (showsEditor) Button(text = "editor") else Label(text = "viewer")
                }
            }
        }

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(1)
        onNodeOfType<JLabel>().assertExists()
        onNodeOfType<JButton>().assertDoesNotExist()

        editing = true
        awaitIdle()
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(1)
        onNodeOfType<JButton>().assertExists()
        onNodeOfType<JLabel>().assertDoesNotExist()
        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            JLayeredPane.getLayer(onNodeOfType<JButton>().fetch<JComponent>()),
            "the replacing child is hosted on the declared layer",
        )

        editing = false
        awaitIdle()
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(1)
        onNodeOfType<JLabel>().assertExists()
        onNodeOfType<JButton>().assertDoesNotExist()
    }

    @Test
    fun aChildsContentFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            LayeredPane {
                layer(JLayeredPane.DEFAULT_LAYER) { Label(text = label) }
            }
        }

        onNodeWithText("first").assertExists()

        label = "second"
        awaitIdle()
        onNodeWithText("second").assertExists()
        onNodeWithText("first").assertDoesNotExist()

        label = "first"
        awaitIdle()
        onNodeWithText("first").assertExists()
    }

    @Test
    fun theLayeredPanesOwnModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Canvas")
        setContent {
            LayeredPane(modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier) {
                layer(JLayeredPane.DEFAULT_LAYER) { Label(text = "child") }
            }
        }

        val pane = onNodeOfType<JLayeredPane>().fetch()
        assertEquals("Canvas", pane.toolTipText, "the modifier applies to the layered pane itself")

        tip = "Stacked children"
        awaitIdle()
        assertEquals("Stacked children", pane.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(pane.toolTipText, "dropping the element restores the tooltip the pane had without it")
    }

    @Test
    fun disposingTheLayeredPaneTearsItDown() = runComposeSwingTest {
        var show by mutableStateOf(true)
        setContent {
            if (show) {
                LayeredPane {
                    layer(JLayeredPane.DEFAULT_LAYER) { Label(text = "child") }
                }
            }
        }

        onNodeOfType<JLayeredPane>().assertExists()
        onNodeWithText("child").assertExists()

        show = false
        awaitIdle()

        onNodeOfType<JLayeredPane>().assertDoesNotExist()
        onNodeWithText("child").assertDoesNotExist()
    }
}
