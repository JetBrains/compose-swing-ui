package org.jetbrains.compose.swing.modifier.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the actual-size modifiers (`size`/`width`/`height`). They set the component's
 * live size (`setSize`), which a layout manager would override on its next pass, so each test hosts the
 * modified child in a [JLayeredPane] - a parent with no layout manager - where the set size persists.
 * The assertions read what an observer of the live Swing component sees: its `getSize`/`getWidth`/
 * `getHeight`.
 */
class SizeModifierTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeCases")
    fun aSizeDeclarationSetsTheActualSize(
        message: String,
        modifier: SwingModifier,
        expected: Dimension,
    ) = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(text = "child", modifier = modifier.layer(JLayeredPane.DEFAULT_LAYER))
            }
        }
        onNodeOfType<JLabel>().assertProperty(expected, message) { size }
    }

    @Test
    fun theSizeDeclarationNamesEachAxisItsWriteCovers() {
        val held =
            SwingModifier.size(120, 40).foldIn(emptySet<String>()) { names, element ->
                names + (element as SwingModifier.NodeElement<*, *>).heldProperties
            }

        assertEquals(setOf("size", "width", "height"), held, "one write, every axis it lands on named")
    }

    @Test
    fun droppingAWholeSizeAndThenAnAxisLeavesTheChildWhereItStood() = runComposeSwingTest {
        // The axis slot joins the modifier over a width the size slot has already written, so what it puts
        // back has to be the width the child came with rather than the one it read on the way in.
        var declared by mutableStateOf(0)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier =
                        when (declared) {
                            1 -> onDefaultLayer.size(120, 40).width(10)
                            2 -> onDefaultLayer.width(10)
                            else -> onDefaultLayer
                        },
                )
            }
        }
        val child = onNodeOfType<JLabel>().fetch()
        val stood = child.size

        declared = 1
        awaitIdle()
        assertEquals(Dimension(10, 40), child.size, "the width is declared last and wins its axis")

        // The whole size goes first, leaving the axis standing on what that size wrote.
        declared = 2
        awaitIdle()
        declared = 3
        awaitIdle()

        assertEquals(stood, child.size, "the child is left at the size it stood at before either declaration")
    }

    @Test
    fun removingTheSizeModifierRestoresTheOriginalSize() = runComposeSwingTest {
        var sized by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = if (sized) onDefaultLayer.size(120, 40) else onDefaultLayer,
                )
            }
        }
        val sizedComponent = onNodeOfType<JLabel>()
        // The size the component had before the modifier captured it - its pre-modifier original.
        sizedComponent.assertProperty(Dimension(120, 40), "size should set the actual size while applied") { size }

        sized = false
        awaitIdle()
        // The size declaration is gone, so the component returns to the size it had before it.
        val restored = onNodeOfType<JLabel>()
        restored.assertProperty(
            Dimension(0, 0),
            "removing the size modifier restores the component's original size",
        ) { size }
    }

    @Test
    fun addingTheSizeLeavesTheAxisAnotherModifierDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(false)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = (if (whole) onDefaultLayer.size(120, 40) else onDefaultLayer).width(10),
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the declared width is what the child carries") { width }

        // The size arrives ahead of the width declaration and writes the axis it declares, so the
        // later declaration is written again over it.
        whole = true
        awaitIdle()

        child.assertProperty(10, "the declared width stands after the size arrives under it") { width }
        child.assertProperty(40, "the size reaches the axis it owns") { height }
    }

    @Test
    fun droppingTheSizeLeavesTheAxisAnotherModifierDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = (if (whole) onDefaultLayer.size(120, 40) else onDefaultLayer).width(10),
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Dimension(10, 40), "the later width wins its axis, height stays from size") { size }

        whole = false
        awaitIdle()

        // Restoring a size puts both axes back, width included, so the modifier declaring that axis
        // alone writes it again on the same pass.
        child.assertProperty(Dimension(10, 0), "the declared width stands after the size is dropped") { size }
    }

    @Test
    fun sizeReactsToStateChangeAcrossRecomposition() = runComposeSwingTest {
        var wide by mutableStateOf(true)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).size(if (wide) 120 else 60, 40),
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(
            Dimension(120, 40),
            "size should set the actual size before the state changes",
        ) { size }

        wide = false
        awaitIdle()
        label.assertProperty(
            Dimension(60, 40),
            "size should react to the state change",
        ) { size }
    }

    @Test
    fun theLastPlaceAKeyIsDeclaredAtIsWhereItStands() = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier =
                        SwingModifier
                            .layer(JLayeredPane.DEFAULT_LAYER)
                            .width(10)
                            .size(20, 30)
                            .width(5),
                )
            }
        }
        onNodeOfType<JLabel>().assertProperty(
            Dimension(5, 30),
            "a key declared again later should stand where it was declared last",
        ) { size }
    }

    @Test
    fun swappingTwoDeclarationsOfOneAxisWritesTheOneNowDeclaredLast() = runComposeSwingTest {
        var sizeFirst by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier =
                        onDefaultLayer then
                            if (sizeFirst) {
                                // Two pairs swap places, so only writing from the first of them puts
                                // the geometry where the order declares it.
                                SwingModifier
                                    .size(20, 30)
                                    .width(10)
                                    .foreground(Color.RED)
                                    .background(Color.BLUE)
                            } else {
                                SwingModifier
                                    .width(10)
                                    .size(20, 30)
                                    .background(Color.BLUE)
                                    .foreground(Color.RED)
                            },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Dimension(10, 30), "the width is declared last and wins its axis") { size }

        // No declaration changed, so nothing but their order says which one stands.
        sizeFirst = false
        awaitIdle()

        child.assertProperty(Dimension(20, 30), "the size is declared last now and wins the width axis") { size }
    }

    companion object {
        @JvmStatic
        fun sizeCases(): List<Arguments> = listOf(
            Arguments.of(
                Named.of("size", "size should set the component's actual size"),
                SwingModifier.size(120, 40),
                Dimension(120, 40),
            ),
            Arguments.of(
                Named.of("Dimension overload", "the Dimension overload should set the actual size"),
                SwingModifier.size(Dimension(90, 30)),
                Dimension(90, 30),
            ),
            Arguments.of(
                Named.of("width keeps height", "width must keep the current height"),
                SwingModifier.size(50, 33).width(150),
                Dimension(150, 33),
            ),
            Arguments.of(
                Named.of("height keeps width", "height must keep the current width"),
                SwingModifier.size(44, 50).height(55),
                Dimension(44, 55),
            ),
            Arguments.of(
                Named.of("width then height", "width+height combine into a full size"),
                SwingModifier.width(10).height(20),
                Dimension(10, 20),
            ),
            Arguments.of(
                Named.of("height then width", "distinct axes combine regardless of order"),
                SwingModifier.height(20).width(10),
                Dimension(10, 20),
            ),
            Arguments.of(
                Named.of("size after width", "later size wins the width axis"),
                SwingModifier.width(10).size(20, 30),
                Dimension(20, 30),
            ),
            Arguments.of(
                Named.of("width after size", "later width wins the width axis, height stays from size"),
                SwingModifier.size(20, 30).width(10),
                Dimension(10, 30),
            ),
        )
    }
}
