package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parameter-level coverage for both [Button] overloads: every declared parameter reaches the live
 * `JButton` on every recomposition, in both directions.
 *
 * Each test drives a parameter from composition state through at least two distinct values and back,
 * and asserts on what the button itself reports - its text, its tooltip, its registered action
 * listeners, and which callback a click actually runs.
 */
class ButtonReactivityTest {
    @Test
    fun theTextFollowsTheStateDrivingIt() = runComposeSwingTest {
        var text by mutableStateOf("Save")
        setContent { Button(text = text, onClick = { }) }

        onNodeOfType<JButton>().assertTextEquals("Save")

        text = "Apply"
        awaitIdle()
        onNodeOfType<JButton>().assertTextEquals("Apply")

        text = "Save"
        awaitIdle()
        onNodeOfType<JButton>().assertTextEquals("Save")
    }

    @Test
    fun theModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Saves the file")
        setContent {
            Button(
                text = "Save",
                onClick = { },
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertEquals("Saves the file", button.toolTipText, "the modifier applies to the button")

        tip = "Writes the file to disk"
        awaitIdle()
        assertEquals("Writes the file to disk", button.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(button.toolTipText, "dropping the element restores the tooltip the button had without it")
    }

    @Test
    fun aClickRunsTheMostRecentlyComposedOnClick() = runComposeSwingTest {
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // The round is captured at composition time, so every pass declares a callback that
            // reports a different value: a callback captured once when the button was built keeps
            // reporting the round it was born in, however often the declaration changes.
            val current = round
            Button(text = "Run", onClick = { runs += current })
        }

        onNodeOfType<JButton>().performClick()
        assertEquals(listOf(1), runs, "the click runs the composed callback")

        round = 2
        awaitIdle()
        onNodeOfType<JButton>().performClick()
        assertEquals(listOf(1, 2), runs, "the click runs the recomposed callback, not the one it replaced")
    }

    @Test
    fun recompositionLeavesExactlyOneListenerOnTheButton() = runComposeSwingTest {
        var text by mutableStateOf("One")
        var clicks = 0
        setContent { Button(text = text, onClick = { clicks++ }) }

        text = "Two"
        awaitIdle()
        text = "Three"
        awaitIdle()

        assertEquals(
            1,
            onNodeOfType<JButton>().fetch().actionListeners.size,
            "recomposition must not stack up action listeners",
        )
        onNodeOfType<JButton>().performClick()
        assertEquals(1, clicks, "a single click reports exactly once")
    }

    @Test
    fun theRawListenerOverloadAppliesTheTextAndFiresItsListener() = runComposeSwingTest {
        var text by mutableStateOf("Save")
        var fired = 0
        setContent {
            val listener = remember { ActionListener { fired++ } }
            Button(text = text, actionListener = listener)
        }

        val button = onNodeOfType<JButton>()
        button.assertTextEquals("Save")
        button.performClick()
        assertEquals(1, fired, "the raw listener receives the click")

        text = "Apply"
        awaitIdle()
        button.assertTextEquals("Apply")
        assertEquals(
            1,
            button.fetch().actionListeners.size,
            "a stable listener instance stays attached exactly once",
        )

        button.performClick()
        assertEquals(2, fired, "the raw listener keeps receiving clicks after recomposition")
    }

    @Test
    fun swappingTheRawListenerHandsClicksToTheNewInstance() = runComposeSwingTest {
        var second by mutableStateOf(false)
        var first = 0
        var latest = 0
        setContent {
            val firstListener = remember { ActionListener { first++ } }
            val secondListener = remember { ActionListener { latest++ } }
            Button(text = "Run", actionListener = if (second) secondListener else firstListener)
        }

        onNodeOfType<JButton>().performClick()
        assertEquals(1, first, "the declared listener receives the click")

        second = true
        awaitIdle()
        assertEquals(
            1,
            onNodeOfType<JButton>().fetch().actionListeners.size,
            "the replaced listener must be detached, not stacked",
        )

        onNodeOfType<JButton>().performClick()
        assertEquals(1, latest, "the newly declared listener receives the click")
        assertEquals(1, first, "the listener that left the declaration no longer fires")
    }

    @Test
    fun theModifierFollowsTheStateOnTheRawListenerOverload() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Runs the task")
        var fired = 0
        setContent {
            val listener = remember { ActionListener { fired++ } }
            Button(
                text = "Run",
                actionListener = listener,
                modifier = tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier,
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertEquals("Runs the task", button.toolTipText, "the modifier applies to the button")

        tip = "Starts the build"
        awaitIdle()
        assertEquals("Starts the build", button.toolTipText, "the modifier follows the state driving it")

        tip = null
        awaitIdle()
        assertNull(button.toolTipText, "dropping the element restores the tooltip the button had without it")

        onNodeOfType<JButton>().performClick()
        assertEquals(1, fired, "a modifier change must not displace the listener the overload installs")
    }
}
