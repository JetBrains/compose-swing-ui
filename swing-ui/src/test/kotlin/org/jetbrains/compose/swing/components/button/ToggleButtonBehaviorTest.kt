package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JToggleButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral tests for [ToggleButton], driven through the real composition pipeline and asserting
 * against the live `JToggleButton`.
 *
 * The central guarantees: text renders onto the button; clicking toggles the selected state
 * and reports the new value; the pressed state is controlled from composition; an external
 * pressed-state update applies without echoing back as a spurious callback; and a click the caller
 * does not adopt does not stand - the next settled pass writes the declared state back over it.
 */
class ToggleButtonBehaviorTest {
    @Test
    fun textRendersOntoTheButton() = runComposeSwingTest {
        setContent { ToggleButton(text = "Bold") }

        onNodeOfType<JToggleButton>().assertTextEquals("Bold")
    }

    @Test
    fun clickingTogglesAndReportsTheNewState() = runComposeSwingTest {
        var pressed by mutableStateOf(false)
        val received = mutableListOf<Boolean>()
        setContent {
            ToggleButton(
                text = "Bold",
                pressed = pressed,
                onPressedChange = {
                    received += it
                    pressed = it
                },
            )
        }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.assert(SwingMatcher.isSelected(false))

        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected())
        assertEquals(listOf(true), received, "reports the new pressed state")

        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected(false))
        assertEquals(listOf(true, false), received, "the second click reports the released state")
    }

    @Test
    fun theTextFollowsTheStateDrivingIt() = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        setContent { ToggleButton(text = text) }

        onNodeOfType<JToggleButton>().assertTextEquals("Bold")

        text = "Strong"
        awaitIdle()
        onNodeOfType<JToggleButton>().assertTextEquals("Strong")

        text = "Bold"
        awaitIdle()
        onNodeOfType<JToggleButton>().assertTextEquals("Bold")
    }

    @Test
    fun theModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Bold text")
        setContent {
            ToggleButton(
                text = "Bold",
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val button = onNodeOfType<JToggleButton>().fetch()
        assertEquals("Bold text", button.toolTipText, "the modifier applies to the button")

        tip = "Heavier weight"
        awaitIdle()
        assertEquals("Heavier weight", button.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(button.toolTipText, "dropping the element restores the tooltip the button had without it")
    }

    @Test
    fun aClickRunsTheMostRecentlyComposedCallback() = runComposeSwingTest {
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // The round is captured at composition time, so every pass declares a callback that
            // reports a different value: a callback captured once when the button was built keeps
            // reporting the round it was born in, however often the declaration changes.
            val current = round
            ToggleButton(text = "Bold", onPressedChange = { runs += current })
        }

        onNodeOfType<JToggleButton>().performClick()
        assertEquals(listOf(1), runs, "the click runs the composed callback")

        round = 2
        awaitIdle()
        onNodeOfType<JToggleButton>().performClick()
        assertEquals(listOf(1, 2), runs, "the click runs the recomposed callback, not the one it replaced")
    }

    @Test
    fun recompositionLeavesExactlyOneListenerOnTheButton() = runComposeSwingTest {
        var text by mutableStateOf("One")
        val reported = mutableListOf<Boolean>()
        setContent { ToggleButton(text = text, onPressedChange = { reported += it }) }

        text = "Two"
        awaitIdle()
        text = "Three"
        awaitIdle()

        assertEquals(
            1,
            onNodeOfType<JToggleButton>().fetch().actionListeners.size,
            "recomposition must not stack up action listeners",
        )
        onNodeOfType<JToggleButton>().performClick()
        assertEquals(listOf(true), reported, "a single click reports exactly once")
    }

    @Test
    fun thePressedStateFollowsTheStateDrivingIt() = runComposeSwingTest {
        // A JToggleButton is built released, so declaring `true` first proves the parameter reaches the
        // button on the very first composition rather than the button merely keeping its own default.
        var pressed by mutableStateOf(true)
        setContent { ToggleButton(text = "Bold", pressed = pressed) }

        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected())

        pressed = false
        awaitIdle()
        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected(false))

        pressed = true
        awaitIdle()
        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected())
    }

    @Test
    fun theRawListenerOverloadAppliesTextAndPressedStateAndFires() = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        var pressed by mutableStateOf(false)
        var tip by mutableStateOf<String?>("Bold text")
        val reported = mutableListOf<Boolean>()
        setContent {
            val listener =
                remember { ActionListener { event -> reported += (event.source as JToggleButton).isSelected } }
            ToggleButton(
                text = text,
                actionListener = listener,
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
                pressed = pressed,
            )
        }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.assertTextEquals("Bold")
        toggle.assert(SwingMatcher.isSelected(false))

        text = "Strong"
        pressed = true
        tip = null
        awaitIdle()
        toggle.assertTextEquals("Strong")
        toggle.assert(SwingMatcher.isSelected())
        val button = toggle.fetch()
        assertNull(button.toolTipText, "dropping the element restores the tooltip the button had without it")
        assertEquals(1, button.actionListeners.size, "a stable listener instance stays attached exactly once")

        pressed = false
        awaitIdle()
        toggle.assert(SwingMatcher.isSelected(false))

        toggle.performClick()
        assertEquals(listOf(true), reported, "the raw listener sees the state the click produced")
    }

    @Test
    fun swappingTheRawListenerHandsClicksToTheNewInstance() = runComposeSwingTest {
        var second by mutableStateOf(false)
        var first = 0
        var latest = 0
        setContent {
            val firstListener = remember { ActionListener { first++ } }
            val secondListener = remember { ActionListener { latest++ } }
            ToggleButton(text = "Bold", actionListener = if (second) secondListener else firstListener)
        }

        onNodeOfType<JToggleButton>().performClick()
        assertEquals(1, first, "the declared listener receives the click")

        second = true
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JToggleButton>().fetch().actionListeners.size,
            "the replaced listener must be detached, not stacked",
        )

        onNodeOfType<JToggleButton>().performClick()
        assertEquals(1, latest, "the newly declared listener receives the click")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun controlledPressedStateAppliesWithoutCallback() = runComposeSwingTest {
        var pressed by mutableStateOf(false)
        val received = mutableListOf<Boolean>()
        setContent {
            ToggleButton(text = "Bold", pressed = pressed, onPressedChange = { received += it })
        }

        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected(false))

        // Pushing pressed=true from composition must update the button without firing the callback:
        // only a user-driven click reports through onPressedChange.
        pressed = true
        awaitIdle()

        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected())
        assertEquals(emptyList(), received, "a controlled update must not fire onPressedChange")
    }

    @Test
    fun aPressTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        setContent { ToggleButton(text = text, pressed = false) }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected(false))

        // An unrelated recomposition changes nothing here: the button was already showing the
        // declared state right after the click, not just once this pass ran.
        text = "Strong"
        awaitIdle()
        toggle.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aReleaseTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        setContent { ToggleButton(text = text, pressed = true) }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected())

        text = "Strong"
        awaitIdle()
        toggle.assert(SwingMatcher.isSelected())
    }
}
