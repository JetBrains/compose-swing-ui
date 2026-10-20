package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.event.ActionListener
import javax.swing.JToggleButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for [ToggleButton], driven through the real composition pipeline and asserting
 * against the live `JToggleButton`.
 */
class ToggleButtonBehaviorTest {
    @Test
    fun anUndeclaredToggleButtonIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { ToggleButton(text = "", selected = false, onSelectedChange = {}) }
        onNodeOfType<JToggleButton>().assertTreeMatches(JToggleButton())
    }

    @Test
    fun clickingTogglesAndReportsTheNewState() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        val received = mutableListOf<Boolean>()
        setContent {
            ToggleButton(
                text = "Bold",
                selected = selected,
                onSelectedChange = {
                    received += it
                    selected = it
                },
            )
        }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.assert(SwingMatcher.isSelected(false))

        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected())
        assertEquals(listOf(true), received, "reports the new selected state")

        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected(false))
        assertEquals(listOf(true, false), received, "the second click reports the released state")
    }

    @Test
    fun theTextFollowsTheStateDrivingIt() = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        setContent { ToggleButton(text = text, selected = false, onSelectedChange = {}) }

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
                selected = false,
                onSelectedChange = {},
                modifier = tip?.let { SwingModifier.name(it) } ?: SwingModifier,
            )
        }

        val button = onNodeOfType<JToggleButton>()
        button.assertProperty("Bold text", "the modifier applies to the button") { name }

        tip = "Heavier weight"
        awaitIdle()
        button.assertProperty("Heavier weight", "the modifier follows the state driving it") { name }

        tip = null
        awaitIdle()
        button.assertProperty(null, "dropping the element restores the name the button had without it") { name }
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
            ToggleButton(text = "Bold", selected = false, onSelectedChange = { runs += current })
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
        setContent { ToggleButton(text = text, selected = false, onSelectedChange = { reported += it }) }

        text = "Two"
        awaitIdle()
        text = "Three"
        awaitIdle()

        onNodeOfType<JToggleButton>().assertProperty(1, "recomposition must not stack up action listeners") {
            actionListeners.size
        }
        onNodeOfType<JToggleButton>().performClick()
        assertEquals(listOf(true), reported, "a single click reports exactly once")
    }

    @Test
    fun theSelectedStateFollowsTheStateDrivingIt() = runComposeSwingTest {
        // A JToggleButton is built released, so declaring `true` first proves the parameter reaches the
        // button on the very first composition rather than the button merely keeping its own default.
        var selected by mutableStateOf(true)
        setContent { ToggleButton(text = "Bold", selected = selected, onSelectedChange = {}) }

        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected())

        selected = false
        awaitIdle()
        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected(false))

        selected = true
        awaitIdle()
        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected())
    }

    @Test
    fun theRawListenerOverloadAppliesTextAndSelectedStateAndFires() = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        var selected by mutableStateOf(false)
        var tip by mutableStateOf<String?>("Bold text")
        val reported = mutableListOf<Boolean>()
        setContent {
            val listener =
                remember { ActionListener { event -> reported += (event.source as JToggleButton).isSelected } }
            ToggleButton(
                text = text,
                selected = selected,
                actionListener = listener,
                modifier = tip?.let { SwingModifier.name(it) } ?: SwingModifier,
            )
        }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.assertTextEquals("Bold")
        toggle.assert(SwingMatcher.isSelected(false))

        text = "Strong"
        selected = true
        tip = null
        awaitIdle()
        toggle.assertTextEquals("Strong")
        toggle.assert(SwingMatcher.isSelected())
        toggle.assertProperty(null, "dropping the element restores the name the button had without it") { name }
        toggle.assertProperty(1, "a stable listener instance stays attached exactly once") { actionListeners.size }

        selected = false
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
            ToggleButton(
                text = "Bold",
                selected = false,
                actionListener = if (second) secondListener else firstListener,
            )
        }

        onNodeOfType<JToggleButton>().performClick()
        assertEquals(1, first, "the declared listener receives the click")

        second = true
        awaitIdle()
        onNodeOfType<JToggleButton>().assertProperty(1, "the replaced listener must be detached, not stacked") {
            actionListeners.size
        }

        onNodeOfType<JToggleButton>().performClick()
        assertEquals(1, latest, "the newly declared listener receives the click")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun controlledSelectedStateAppliesWithoutCallback() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        val received = mutableListOf<Boolean>()
        setContent {
            ToggleButton(text = "Bold", selected = selected, onSelectedChange = { received += it })
        }

        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected(false))

        // Pushing selected=true from composition must update the button without firing the callback:
        // only a user-driven click reports through onSelectedChange.
        selected = true
        awaitIdle()

        onNodeOfType<JToggleButton>().assert(SwingMatcher.isSelected())
        assertEquals(emptyList(), received, "a controlled update must not fire onSelectedChange")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredSelections")
    fun aClickTheCallerDoesNotAdoptDoesNotStand(declared: Boolean) = runComposeSwingTest {
        var text by mutableStateOf("Bold")
        setContent { ToggleButton(text = text, selected = declared, onSelectedChange = {}) }

        val toggle = onNodeOfType<JToggleButton>()
        toggle.performClick()
        toggle.assert(SwingMatcher.isSelected(declared))

        // An unrelated recomposition changes nothing here: the button was already showing the
        // declared state right after the click, not just once this pass ran.
        text = "Strong"
        awaitIdle()
        toggle.assert(SwingMatcher.isSelected(declared))
    }

    @Test
    fun aSelectingClickTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JToggleButton::class.java,
            declared = false,
            content = { report -> ToggleButton(text = "Bold", selected = false, onSelectedChange = { report() }) },
            change = { it.click() },
            read = { it.isSelected },
        )
    }

    companion object {
        @JvmStatic
        fun declaredSelections(): List<Named<Boolean>> = listOf(
            Named.of("a selecting click", false),
            Named.of("a clearing click", true),
        )
    }
}
