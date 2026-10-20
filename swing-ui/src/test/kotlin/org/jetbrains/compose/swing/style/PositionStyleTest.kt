package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test

class PositionStyleTest {
    private fun ComposeSwingTest.positionedChild(style: SwingStyle<LabelStyleScope>): SwingNodeInteraction<JLabel> {
        setContent {
            LayeredPane {
                Label(text = "child", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER), style = style)
            }
        }
        return onNodeOfType<JLabel>()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("locationCases")
    fun locationKeepsOtherAxis(
        message: String,
        style: SwingStyle<LabelStyleScope>,
        expected: Point,
    ) = runComposeSwingTest {
        positionedChild(style).assertProperty(expected, message) { location }
    }

    @Test
    fun droppingTheLocationLeavesTheAxisAnotherSetterDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(true)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        if (whole) location(20, 30)
                        y(50)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Point(20, 50), "the later y wins its axis, x stays from location") { location }

        whole = false
        awaitIdle()

        // Restoring a location puts both axes back, y included, so the setter declaring that axis
        // alone writes it again on the same pass.
        child.assertProperty(Point(0, 50), "the declared y stands after the location is dropped") { location }
    }

    @Test
    fun addingTheBoundsLeavesTheAxisAnotherSetterDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(false)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        if (whole) bounds(20, 30, 100, 40)
                        x(10)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the declared x is what the child carries") { x }

        // The bounds arrive ahead of the x declaration and write the axis it declares, so the later
        // declaration is written again over them.
        whole = true
        awaitIdle()

        child.assertProperty(10, "the declared x stands after the bounds arrive under it") { x }
    }

    @Test
    fun movingTheBoundsLeavesTheAxisAnotherSetterDeclares() = runComposeSwingTest {
        var top by mutableStateOf(30)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        bounds(20, top, 100, 40)
                        x(10)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the declared x is what the child carries") { x }

        // Nothing joins or leaves the style: the bounds simply declare a new value, which writes the
        // x axis on its way past.
        top = 60
        awaitIdle()

        child.assertProperty(60, "the redeclared bounds reach the axis they own") { y }
        child.assertProperty(10, "the declared x stands after the bounds are written again") { x }
    }

    @Test
    fun droppingTheBoundsLeavesTheAxisAnotherSetterDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(true)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        if (whole) bounds(20, 30, 100, 40)
                        x(10)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the later x wins its axis") { x }

        whole = false
        awaitIdle()

        child.assertProperty(10, "the declared x stands after the bounds are dropped") { x }
    }

    companion object {
        @JvmStatic
        fun locationCases(): List<Arguments> = listOf(
            Arguments.of(
                "x then location",
                SwingStyle<LabelStyleScope> {
                    x(10)
                    location(20, 30)
                },
                Point(20, 30),
            ),
            Arguments.of(
                Named.of("x keeps y", "x must keep the current y"),
                SwingStyle<LabelStyleScope> {
                    location(50, 33)
                    x(150)
                },
                Point(150, 33),
            ),
            Arguments.of(
                Named.of("y keeps x", "y must keep the current x"),
                SwingStyle<LabelStyleScope> {
                    location(44, 50)
                    y(55)
                },
                Point(44, 55),
            ),
            Arguments.of(
                Named.of("x then y", "x+y combine into a full location"),
                SwingStyle<LabelStyleScope> {
                    x(10)
                    y(20)
                },
                Point(10, 20),
            ),
            Arguments.of(
                Named.of("y then x", "distinct axes combine regardless of order"),
                SwingStyle<LabelStyleScope> {
                    y(20)
                    x(10)
                },
                Point(10, 20),
            ),
        )
    }
}
