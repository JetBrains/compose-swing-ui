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
import javax.swing.JCheckBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral coverage for both [CheckBox] overloads, asserted on the live `JCheckBox`.
 *
 * The checked state is controlled: the box shows whatever `checked` holds, a click reports the new
 * state through the callback, and a value pushed in from composition applies without echoing back as a
 * callback. Text, checked state and the modifier are each driven through more than one value and back,
 * so a parameter honored only when the component is built would fail here. A click the caller does not
 * adopt does not stand - the next settled pass writes the declared state back over it.
 */
class CheckBoxBehaviorTest {
    @Test
    fun theTextFollowsTheStateDrivingIt() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        setContent { CheckBox(text = text, checked = false, onCheckedChange = {}) }

        onNodeOfType<JCheckBox>().assertTextEquals("Word wrap")

        text = "Wrap lines"
        awaitIdle()
        onNodeOfType<JCheckBox>().assertTextEquals("Wrap lines")

        text = "Word wrap"
        awaitIdle()
        onNodeOfType<JCheckBox>().assertTextEquals("Word wrap")
    }

    @Test
    fun theCheckedStateFollowsTheStateDrivingIt() = runComposeSwingTest {
        // A JCheckBox is built unchecked, so declaring `true` first proves the parameter reaches the
        // box on the very first composition rather than the box merely keeping its own default.
        var checked by mutableStateOf(true)
        setContent { CheckBox(text = "Word wrap", checked = checked, onCheckedChange = {}) }

        onNodeOfType<JCheckBox>().assert(SwingMatcher.isSelected())

        checked = false
        awaitIdle()
        onNodeOfType<JCheckBox>().assert(SwingMatcher.isSelected(false))

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assert(SwingMatcher.isSelected())
    }

    @Test
    fun clickingReportsTheNewCheckedStateAndTheBoxKeepsIt() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            CheckBox(
                text = "Word wrap",
                checked = checked,
                onCheckedChange = {
                    reported += it
                    checked = it
                },
            )
        }

        val checkBox = onNodeOfType<JCheckBox>()
        checkBox.performClick()
        assertEquals(listOf(true), reported, "the click reports the new checked state")
        checkBox.assert(SwingMatcher.isSelected())

        checkBox.performClick()
        assertEquals(listOf(true, false), reported, "the second click reports the box being cleared")
        checkBox.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aCheckedStatePushedFromCompositionDoesNotFireTheCallback() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            CheckBox(text = "Word wrap", checked = checked, onCheckedChange = { reported += it })
        }

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assert(SwingMatcher.isSelected())
        assertEquals(emptyList(), reported, "only a user gesture reports through the callback")
    }

    @Test
    fun aClickRunsTheMostRecentlyComposedCallback() = runComposeSwingTest {
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // The round is captured at composition time, so every pass declares a callback that
            // reports a different value: a callback captured once when the box was built keeps
            // reporting the round it was born in, however often the declaration changes.
            val current = round
            CheckBox(text = "Word wrap", checked = false, onCheckedChange = { runs += current })
        }

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(1), runs, "the click runs the composed callback")

        round = 2
        awaitIdle()
        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(1, 2), runs, "the click runs the recomposed callback, not the one it replaced")
    }

    @Test
    fun recompositionLeavesExactlyOneListenerOnTheBox() = runComposeSwingTest {
        var text by mutableStateOf("One")
        val reported = mutableListOf<Boolean>()
        setContent { CheckBox(text = text, checked = false, onCheckedChange = { reported += it }) }

        text = "Two"
        awaitIdle()
        text = "Three"
        awaitIdle()

        assertEquals(
            1,
            onNodeOfType<JCheckBox>().fetch().actionListeners.size,
            "recomposition must not stack up action listeners",
        )
        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(true), reported, "a single click reports exactly once")
    }

    @Test
    fun theModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Wraps long lines")
        setContent {
            CheckBox(
                text = "Word wrap",
                checked = false,
                onCheckedChange = {},
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val box = onNodeOfType<JCheckBox>().fetch()
        assertEquals("Wraps long lines", box.toolTipText, "the modifier applies to the box")

        tip = "Wraps text at the viewport edge"
        awaitIdle()
        assertEquals("Wraps text at the viewport edge", box.toolTipText, "the modifier follows its state")

        tip = null
        awaitIdle()
        assertNull(box.toolTipText, "dropping the element restores the tooltip the box had without it")
    }

    @Test
    fun theRawListenerOverloadAppliesTextAndCheckedStateAndFires() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        var checked by mutableStateOf(false)
        var tip by mutableStateOf<String?>("Wraps long lines")
        val reported = mutableListOf<Boolean>()
        setContent {
            val listener = remember { ActionListener { event -> reported += (event.source as JCheckBox).isSelected } }
            CheckBox(
                text = text,
                checked = checked,
                actionListener = listener,
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val checkBox = onNodeOfType<JCheckBox>()
        checkBox.assertTextEquals("Word wrap")
        checkBox.assert(SwingMatcher.isSelected(false))

        checked = true
        text = "Wrap lines"
        tip = null
        awaitIdle()
        checkBox.assert(SwingMatcher.isSelected())
        checkBox.assertTextEquals("Wrap lines")
        val box = checkBox.fetch()
        assertNull(box.toolTipText, "dropping the element restores the tooltip the box had without it")
        assertEquals(1, box.actionListeners.size, "a stable listener instance stays attached exactly once")

        checked = false
        awaitIdle()
        checkBox.assert(SwingMatcher.isSelected(false))

        checkBox.performClick()
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
            CheckBox(
                text = "Word wrap",
                checked = false,
                actionListener = if (second) secondListener else firstListener,
            )
        }

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(1, first, "the declared listener receives the click")

        second = true
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JCheckBox>().fetch().actionListeners.size,
            "the replaced listener must be detached, not stacked",
        )

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(1, latest, "the newly declared listener receives the click")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun aClickTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        setContent { CheckBox(text = text, checked = false, onCheckedChange = {}) }

        val checkBox = onNodeOfType<JCheckBox>()
        checkBox.performClick()
        checkBox.assert(SwingMatcher.isSelected(false))

        // An unrelated recomposition changes nothing here: the box was already showing the
        // declared state right after the click, not just once this pass ran.
        text = "Wrap lines"
        awaitIdle()
        checkBox.assert(SwingMatcher.isSelected(false))
    }
}
