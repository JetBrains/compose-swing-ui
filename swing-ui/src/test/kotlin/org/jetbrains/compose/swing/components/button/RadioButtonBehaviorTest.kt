package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsPutBack
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral coverage for both [RadioButton] overloads, asserted on the live `JRadioButton`.
 *
 * The selected state is controlled: the button shows whatever `selected` holds, a value pushed in from
 * composition applies without echoing back as a callback, and both edges of a user gesture - choosing
 * and clearing the button - report through `onSelectedChange`. A gesture the caller does not adopt does
 * not stand: the next settled pass writes the declared state back over it.
 */
class RadioButtonBehaviorTest {
    @Test
    fun theTextFollowsTheStateDrivingIt() = runComposeSwingTest {
        var text by mutableStateOf("Compact")
        setContent { RadioButton(text = text, selected = false, onSelectedChange = {}) }

        onNodeOfType<JRadioButton>().assertTextEquals("Compact")

        text = "Comfortable"
        awaitIdle()
        onNodeOfType<JRadioButton>().assertTextEquals("Comfortable")

        text = "Compact"
        awaitIdle()
        onNodeOfType<JRadioButton>().assertTextEquals("Compact")
    }

    @Test
    fun theSelectedStateFollowsTheStateDrivingIt() = runComposeSwingTest {
        // A JRadioButton starts unselected, so declaring `true` first proves the parameter reaches the
        // button on the first composition, not just the button's own default.
        var selected by mutableStateOf(true)
        setContent { RadioButton(text = "Compact", selected = selected, onSelectedChange = {}) }

        onNodeOfType<JRadioButton>().assert(SwingMatcher.isSelected())

        selected = false
        awaitIdle()
        onNodeOfType<JRadioButton>().assert(SwingMatcher.isSelected(false))

        selected = true
        awaitIdle()
        onNodeOfType<JRadioButton>().assert(SwingMatcher.isSelected())
    }

    @Test
    fun clickingReportsTheNewSelectedStateAndTheButtonKeepsIt() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            RadioButton(
                text = "Compact",
                selected = selected,
                onSelectedChange = {
                    reported += it
                    selected = it
                },
            )
        }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        assertEquals(listOf(true), reported, "the click reports the button being chosen")
        radioButton.assert(SwingMatcher.isSelected())

        // A button with no group is cleared by a second click, and that edge reaches the caller too.
        radioButton.performClick()
        assertEquals(listOf(true, false), reported, "the second click reports the button being cleared")
        radioButton.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aSelectedStatePushedFromCompositionDoesNotFireTheCallback() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        var selects = 0
        setContent {
            RadioButton(text = "Compact", selected = selected, onSelectedChange = { selects++ })
        }

        selected = true
        awaitIdle()
        onNodeOfType<JRadioButton>().assert(SwingMatcher.isSelected())
        assertEquals(0, selects, "only a user gesture reports through the callback")
    }

    @Test
    fun choosingTheButtonReportsThroughTheMostRecentlyComposedCallback() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // Each pass captures `round` at composition time, so a callback keeps reporting the round
            // it was built with, however often the declaration changes.
            val current = round
            RadioButton(
                text = "Compact",
                selected = selected,
                onSelectedChange = {
                    runs += current
                    selected = it
                },
            )
        }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        assertEquals(listOf(1), runs, "choosing the button runs the composed callback")
        radioButton.assert(SwingMatcher.isSelected())

        round = 2
        selected = false
        awaitIdle()
        radioButton.performClick()
        assertEquals(listOf(1, 2), runs, "the gesture runs the recomposed callback, not the one it replaced")
        assertEquals(
            1,
            radioButton.fetch().actionListeners.size,
            "recomposition must not stack up action listeners",
        )
    }

    @Test
    fun theModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Denser layout")
        setContent {
            RadioButton(
                text = "Compact",
                selected = false,
                onSelectedChange = {},
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val radio = onNodeOfType<JRadioButton>().fetch()
        assertEquals("Denser layout", radio.toolTipText, "the modifier applies to the button")

        tip = "Fits more rows on screen"
        awaitIdle()
        assertEquals("Fits more rows on screen", radio.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(radio.toolTipText, "dropping the element restores the tooltip the button had without it")
    }

    @Test
    fun theRawListenerOverloadAppliesTextAndSelectedStateAndFires() = runComposeSwingTest {
        var text by mutableStateOf("Compact")
        var selected by mutableStateOf(false)
        var tip by mutableStateOf<String?>("Denser layout")
        val reported = mutableListOf<Boolean>()
        setContent {
            val listener =
                remember { ActionListener { event -> reported += (event.source as JRadioButton).isSelected } }
            RadioButton(
                text = text,
                selected = selected,
                actionListener = listener,
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.assertTextEquals("Compact")
        radioButton.assert(SwingMatcher.isSelected(false))

        text = "Comfortable"
        selected = true
        tip = null
        awaitIdle()
        radioButton.assertTextEquals("Comfortable")
        radioButton.assert(SwingMatcher.isSelected())
        val radio = radioButton.fetch()
        assertNull(radio.toolTipText, "dropping the element restores the tooltip the button had without it")
        assertEquals(1, radio.actionListeners.size, "a stable listener instance stays attached exactly once")

        selected = false
        awaitIdle()
        radioButton.assert(SwingMatcher.isSelected(false))

        radioButton.performClick()
        assertEquals(listOf(true), reported, "the raw listener sees the state the gesture produced")
    }

    @Test
    fun swappingTheRawListenerHandsTheGestureToTheNewInstance() = runComposeSwingTest {
        var second by mutableStateOf(false)
        var first = 0
        var latest = 0
        setContent {
            val firstListener = remember { ActionListener { first++ } }
            val secondListener = remember { ActionListener { latest++ } }
            RadioButton(
                text = "Compact",
                selected = false,
                actionListener = if (second) secondListener else firstListener,
            )
        }

        onNodeOfType<JRadioButton>().performClick()
        assertEquals(1, first, "the declared listener receives the gesture")

        second = true
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JRadioButton>().fetch().actionListeners.size,
            "the replaced listener must be detached, not stacked",
        )

        onNodeOfType<JRadioButton>().performClick()
        assertEquals(1, latest, "the newly declared listener receives the gesture")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Compact")
        setContent { RadioButton(text = text, selected = false, onSelectedChange = {}) }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        radioButton.assert(SwingMatcher.isSelected(false))

        // An unrelated recomposition changes nothing: the button already showed the declared state
        // right after the click, not only once this pass ran.
        text = "Comfortable"
        awaitIdle()
        radioButton.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aClearingClickTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Compact")
        setContent { RadioButton(text = text, selected = true, onSelectedChange = {}) }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        radioButton.assert(SwingMatcher.isSelected())

        text = "Comfortable"
        awaitIdle()
        radioButton.assert(SwingMatcher.isSelected())
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptComesOffWithinEventCycles() = runSwingTest {
        assertUnadoptedChangeIsPutBack(
            type = JRadioButton::class.java,
            declared = false,
            content = { RadioButton(text = "Nightly", selected = false, onSelectedChange = {}) },
            change = { it.doClick(0) },
            read = { it.isSelected },
        )
    }
}
