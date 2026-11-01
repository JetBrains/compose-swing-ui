package org.jetbrains.compose.swing.modifier.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.modifier.SwingModifier
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

/**
 * Behavioral tests for the actual-location modifiers (`location`/`x`/`y`). They set the component's
 * live location (`setLocation`), which a layout manager would override on its next pass, so each test
 * hosts the modified child in a [JLayeredPane] - a parent with no layout manager - where the set
 * location persists. The assertions read what an observer of the live Swing component sees: its
 * `getLocation`/`getX`/`getY`.
 */
class PositionModifierTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("locationCases")
    fun aLocationDeclarationSetsTheActualLocation(
        message: String,
        modifier: SwingModifier,
        expected: Point,
    ) = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(text = "child", modifier = modifier.layer(JLayeredPane.DEFAULT_LAYER))
            }
        }
        onNodeOfType<JLabel>().assertProperty(expected, message) { location }
    }

    @Test
    fun removingTheLocationModifierRestoresTheOriginalLocation() = runComposeSwingTest {
        var positioned by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = if (positioned) onDefaultLayer.location(120, 40) else onDefaultLayer,
                )
            }
        }
        val positionedComponent = onNodeOfType<JLabel>()
        positionedComponent.assertProperty(
            Point(120, 40),
            "location should set the actual location while applied",
        ) { location }

        positioned = false
        awaitIdle()
        // The location declaration is gone, so the component returns to the location it had before it.
        val restored = onNodeOfType<JLabel>()
        restored.assertProperty(
            Point(0, 0),
            "removing the location modifier restores the component's original location",
        ) { location }
    }

    @Test
    fun droppingTheLocationLeavesTheAxisAnotherModifierDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = (if (whole) onDefaultLayer.location(20, 30) else onDefaultLayer).y(50),
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Point(20, 50), "the later y wins its axis, x stays from location") { location }

        whole = false
        awaitIdle()

        // Restoring a location puts both axes back, y included, so the modifier declaring that axis
        // alone writes it again on the same pass.
        child.assertProperty(Point(0, 50), "the declared y stands after the location is dropped") { location }
    }

    @Test
    fun addingTheBoundsLeavesTheAxisAnotherModifierDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(false)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = (if (whole) onDefaultLayer.bounds(20, 30, 100, 40) else onDefaultLayer).x(10),
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
    fun movingTheBoundsLeavesTheAxisAnotherModifierDeclares() = runComposeSwingTest {
        var top by mutableStateOf(30)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier =
                        SwingModifier
                            .layer(JLayeredPane.DEFAULT_LAYER)
                            .bounds(20, top, 100, 40)
                            .x(10),
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the declared x is what the child carries") { x }

        // Nothing joins or leaves the modifier: the bounds simply declare a new value, which writes the
        // x axis on its way past.
        top = 60
        awaitIdle()

        child.assertProperty(60, "the redeclared bounds reach the axis they own") { y }
        child.assertProperty(10, "the declared x stands after the bounds are written again") { x }
    }

    @Test
    fun droppingTheBoundsLeavesTheAxisAnotherModifierDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = (if (whole) onDefaultLayer.bounds(20, 30, 100, 40) else onDefaultLayer).x(10),
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the later x wins its axis") { x }

        whole = false
        awaitIdle()

        child.assertProperty(10, "the declared x stands after the bounds are dropped") { x }
    }

    @Test
    fun locationReactsToStateChangeAcrossRecomposition() = runComposeSwingTest {
        var shifted by mutableStateOf(true)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).location(if (shifted) 120 else 60, 40),
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(
            Point(120, 40),
            "location should set the actual location before the state changes",
        ) { location }

        shifted = false
        awaitIdle()
        label.assertProperty(
            Point(60, 40),
            "location should react to the state change",
        ) { location }
    }

    companion object {
        @JvmStatic
        fun locationCases(): List<Arguments> = listOf(
            Arguments.of(
                Named.of("location", "location should set the component's actual location"),
                SwingModifier.location(120, 40),
                Point(120, 40),
            ),
            Arguments.of(
                Named.of("Point overload", "the Point overload should set the actual location"),
                SwingModifier.location(Point(90, 30)),
                Point(90, 30),
            ),
            Arguments.of(
                Named.of("x keeps y", "x must keep the current y"),
                SwingModifier.location(50, 33).x(150),
                Point(150, 33),
            ),
            Arguments.of(
                Named.of("y keeps x", "y must keep the current x"),
                SwingModifier.location(44, 50).y(55),
                Point(44, 55),
            ),
            Arguments.of(
                Named.of("x then y", "x+y combine into a full location"),
                SwingModifier.x(10).y(20),
                Point(10, 20),
            ),
            Arguments.of(
                Named.of("y then x", "distinct axes combine regardless of order"),
                SwingModifier.y(20).x(10),
                Point(10, 20),
            ),
            Arguments.of(
                Named.of("location after x", "later location wins the x axis"),
                SwingModifier.x(10).location(20, 30),
                Point(20, 30),
            ),
            Arguments.of(
                Named.of("x after location", "later x wins the x axis, y stays from location"),
                SwingModifier.location(20, 30).x(10),
                Point(10, 30),
            ),
        )
    }
}
