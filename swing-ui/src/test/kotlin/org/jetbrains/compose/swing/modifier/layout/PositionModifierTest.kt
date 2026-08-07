package org.jetbrains.compose.swing.modifier.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the actual-location modifiers (`location`/`x`/`y`). They set the component's
 * live location (`setLocation`), which a layout manager would override on its next pass, so each test
 * hosts the modified child in a [JLayeredPane] - a parent with no layout manager - where the set
 * location persists. The assertions read what an observer of the live Swing component sees: its
 * `getLocation`/`getX`/`getY`.
 */
class PositionModifierTest {
    /** Hosts a single [LayeredPane] child carrying [modifier] and returns its live component. */
    private fun ComposeSwingTest.positionedChild(modifier: SwingModifier): JLabel {
        setContent {
            LayeredPane {
                Label(text = "child", modifier = modifier.layer(JLayeredPane.DEFAULT_LAYER))
            }
        }
        return onNodeOfType<JLabel>().fetch()
    }

    @Test
    fun locationSetsTheActualLocation() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.location(120, 40))
        assertEquals(Point(120, 40), child.location, "location should set the component's actual location")
    }

    @Test
    fun locationPointOverloadSetsTheActualLocation() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.location(Point(90, 30)))
        assertEquals(Point(90, 30), child.location, "the Point overload should set the actual location")
    }

    @Test
    fun xSetsTheXKeepingY() = runComposeSwingTest {
        // Establish a known y first, then move x: the y must survive.
        val child = positionedChild(SwingModifier.location(50, 33).x(150))
        assertEquals(150, child.x, "x should set the actual x")
        assertEquals(33, child.y, "x must keep the current y")
    }

    @Test
    fun ySetsTheYKeepingX() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.location(44, 50).y(55))
        assertEquals(55, child.y, "y should set the actual y")
        assertEquals(44, child.x, "y must keep the current x")
    }

    @Test
    fun xAndYCombineIntoAFullLocation() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.x(10).y(20))
        // Each axis comes from its own modifier; they combine into a full location.
        assertEquals(Point(10, 20), child.location, "x+y combine into a full location")
    }

    @Test
    fun yThenXAlsoCombinesRegardlessOfOrder() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.y(20).x(10))
        assertEquals(Point(10, 20), child.location, "distinct axes combine regardless of order")
    }

    @Test
    fun locationAfterXWinsTheXAxis() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.x(10).location(20, 30))
        // location is applied later in the chain, so its x wins over the earlier x(10).
        assertEquals(Point(20, 30), child.location, "later location wins the x axis")
    }

    @Test
    fun xAfterLocationWinsTheXAxis() = runComposeSwingTest {
        val child = positionedChild(SwingModifier.location(20, 30).x(10))
        // x is applied later, so it wins the x axis; the y axis stays from location.
        assertEquals(Point(10, 30), child.location, "later x wins the x axis, y stays from location")
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
        val positionedComponent = onNodeOfType<JLabel>().fetch()
        assertEquals(
            Point(120, 40),
            positionedComponent.location,
            "location should set the actual location while applied",
        )

        positioned = false
        awaitIdle()
        // The location modifier left the chain, so the component returns to the location it had before it.
        val restored = onNodeOfType<JLabel>().fetch()
        assertEquals(
            Point(0, 0),
            restored.location,
            "removing the location modifier restores the component's original location",
        )
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
        assertEquals(
            Point(120, 40),
            label.fetch().location,
            "location should set the actual location before the state changes",
        )

        shifted = false
        awaitIdle()
        assertEquals(
            Point(60, 40),
            label.fetch().location,
            "location should react to the state change",
        )
    }
}
