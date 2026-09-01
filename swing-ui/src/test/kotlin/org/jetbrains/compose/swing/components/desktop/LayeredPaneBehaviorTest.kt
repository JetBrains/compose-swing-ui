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
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [LayeredPane] over a real
 * [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier]. Each assertion reads the rendered
 * [JLayeredPane] itself - the layer a child sits on (`JLayeredPane.getLayer`), its stacking order
 * (`getIndexOf`) - rather than any internal bookkeeping.
 */
class LayeredPaneBehaviorTest {
    @Test
    fun eachDeclaredChildIsHostedOnTheLayeredPaneAtItsLayer() = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(text = "back", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                Label(text = "front", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
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
                Label(text = "seven", modifier = SwingModifier.layer(7))
            }
        }

        assertEquals(
            7,
            JLayeredPane.getLayer(onNodeWithText("seven").fetch<JComponent>()),
            "a layer named by a raw integer is the one the child sits on",
        )
    }

    @Test
    fun anEarlierDeclarationOnALayerPaintsAboveALaterOne() = runComposeSwingTest {
        var showFront by mutableStateOf(false)
        setContent {
            LayeredPane {
                Label(text = "back", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                Label(text = "middle", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                if (showFront) {
                    Label(text = "front", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                }
            }
        }

        // A container paints its children from its last index down to its first, so the child on top
        // of a layer is the one holding the lowest index.
        val pane = onNodeOfType<JLayeredPane>().fetch()
        assertTrue(
            pane.getIndexOf(onNodeWithText("back").fetch<JComponent>()) <
                pane.getIndexOf(onNodeWithText("middle").fetch<JComponent>()),
            "the child declared earlier on the layer paints above the later one",
        )

        showFront = true
        awaitIdle()
        assertTrue(
            pane.getIndexOf(onNodeWithText("middle").fetch<JComponent>()) <
                pane.getIndexOf(onNodeWithText("front").fetch<JComponent>()),
            "a child the layer gains on recomposition paints below the children already on it",
        )
        assertTrue(
            pane.getIndexOf(onNodeWithText("back").fetch<JComponent>()) <
                pane.getIndexOf(onNodeWithText("middle").fetch<JComponent>()),
            "the children already on the layer keep their order when the layer gains another",
        )
    }

    @Test
    fun aChildNamingNoLayerStandsOnTheDefaultLayer() = runComposeSwingTest {
        // JLayeredPane reads a child that carries no depth as one on its default layer, so a pane whose
        // children share that depth is written without naming it at all.
        setContent {
            LayeredPane {
                Label(text = "loose")
                Label(text = "named", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
            }
        }

        val pane = onNodeOfType<JLayeredPane>().fetch()
        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            JLayeredPane.getLayer(onNodeWithText("loose").fetch<JComponent>()),
            "a child that declares no depth stands on the pane's default layer",
        )
        assertEquals(
            2,
            pane.getComponentCountInLayer(JLayeredPane.DEFAULT_LAYER),
            "the child that names the default layer stands alongside the one that names none",
        )
        assertTrue(
            pane.getIndexOf(onNodeWithText("loose").fetch<JComponent>()) <
                pane.getIndexOf(onNodeWithText("named").fetch<JComponent>()),
            "the two spellings of one depth stack in the order the composition declares them",
        )
    }

    @Test
    fun oneLayerHoldsEveryChildDeclaringIt() = runComposeSwingTest {
        // A depth is a region many children share, so naming one layer twice declares two children on
        // it rather than one taking the layer over from the other.
        setContent {
            LayeredPane {
                Label(text = "lower", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
                Label(text = "upper", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
            }
        }

        onNodeOfType<JLayeredPane>().onChildren().assertCountEquals(2)
        onNodeWithText("lower").assertExists()
        onNodeWithText("upper").assertExists()
        assertEquals(
            2,
            onNodeOfType<JLayeredPane>().fetch().getComponentCountInLayer(JLayeredPane.PALETTE_LAYER),
            "both children declaring one depth stand on that layer",
        )
    }

    @Test
    fun aDepthLeavesTheChainItWasDeclaredOnCarriedOnce() = runComposeSwingTest {
        var reports = 0
        val listener = ActionListener { reports++ }
        setContent {
            LayeredPane {
                Button(
                    text = "once",
                    onClick = {},
                    modifier =
                        SwingModifier
                            .actionListener(listener)
                            .layer(JLayeredPane.PALETTE_LAYER)
                            .bounds(0, 0, 80, 24),
                )
            }
        }

        onNodeOfType<JButton>().performClick()

        // A depth chains onto the chain it is given. Joining it with `then` to a factory that takes
        // that chain implicitly puts everything declared before the depth into the chain twice, and
        // this listener, installed once per appearance, then reports every click twice.
        assertEquals(1, reports, "a click should reach a listener declared before the depth once")
    }

    @Test
    fun boundsModifierPositionsAChildWithinTheLayeredPane() = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(
                    text = "fixed",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).bounds(15, 25, 120, 40),
                )
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
                Label(text = "base", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                if (showTop) {
                    Label(text = "top", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
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
                Label(text = "mover", modifier = SwingModifier.layer(depth))
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
    fun droppingTheDeclaredLayerReturnsTheChildToTheDefaultLayer() = runComposeSwingTest {
        var raised by mutableStateOf(true)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = if (raised) SwingModifier.layer(JLayeredPane.DRAG_LAYER) else SwingModifier,
                )
            }
        }

        assertEquals(
            JLayeredPane.DRAG_LAYER,
            JLayeredPane.getLayer(onNodeWithText("child").fetch<JComponent>()),
            "the child sits on the layer its chain declares",
        )

        raised = false
        awaitIdle()
        assertEquals(
            JLayeredPane.DEFAULT_LAYER,
            JLayeredPane.getLayer(onNodeWithText("child").fetch<JComponent>()),
            "a child whose chain stops declaring a layer returns to the pane's default layer",
        )

        raised = true
        awaitIdle()
        assertEquals(
            JLayeredPane.DRAG_LAYER,
            JLayeredPane.getLayer(onNodeWithText("child").fetch<JComponent>()),
            "the child follows the layer its chain declares again",
        )
    }

    @Test
    fun aLayersChildFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var editing by mutableStateOf(false)
        setContent {
            // Which child the layer carries is decided at composition time, so every pass hands the
            // pane a different declaration: a pane that keeps the child it was first given would go on
            // showing the one it started with.
            val showsEditor = editing
            LayeredPane {
                if (showsEditor) {
                    Button(text = "editor", onClick = { }, modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                } else {
                    Label(text = "viewer", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
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
                Label(text = label, modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
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
                Label(text = "child", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
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
                    Label(text = "child", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
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

    @Test
    fun aChildALayerGainsBetweenTwoSiblingsStacksBetweenThemWhateverOtherLayersHold() = runComposeSwingTest {
        var showMiddle by mutableStateOf(false)
        setContent {
            LayeredPane {
                Label(text = "tool", modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
                Label(text = "back", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                if (showMiddle) {
                    Label(text = "middle", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
                }
                Label(text = "front", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER))
            }
        }

        showMiddle = true
        awaitIdle()

        // The pane reads a position as one within the child's own layer, so a sibling on another layer
        // must not count toward it - counted in, "middle" lands past the end of its layer, below "front".
        val pane = onNodeOfType<JLayeredPane>().fetch()
        val back = pane.getIndexOf(onNodeWithText("back").fetch<JComponent>())
        val middle = pane.getIndexOf(onNodeWithText("middle").fetch<JComponent>())
        val front = pane.getIndexOf(onNodeWithText("front").fetch<JComponent>())
        assertTrue(
            back < middle && middle < front,
            "a child the layer gains between two siblings stacks between them: back=$back middle=$middle front=$front",
        )
    }
}
