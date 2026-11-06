package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parameter-level coverage for both [Button] overloads: every declared parameter reaches the live
 * `JButton` on every recomposition, in both directions.
 *
 * Each test drives a parameter from composition state through at least two distinct values and back,
 * and asserts on what the button itself reports - its text, its name, its registered action
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
                modifier = tip?.let { SwingModifier.name(it) } ?: SwingModifier,
            )
        }

        val button = onNodeOfType<JButton>()
        button.assertProperty("Saves the file", "the modifier applies to the button") { name }

        tip = "Writes the file to disk"
        awaitIdle()
        button.assertProperty("Writes the file to disk", "the modifier follows the state driving it") { name }

        tip = null
        awaitIdle()
        button.assertProperty(null, "dropping the element restores the name the button had without it") { name }
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

        onNodeOfType<JButton>().assertProperty(1, "recomposition must not stack up action listeners") {
            actionListeners.size
        }
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
        button.assertProperty(1, "a stable listener instance stays attached exactly once") { actionListeners.size }

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
        onNodeOfType<JButton>().assertProperty(1, "the replaced listener must be detached, not stacked") {
            actionListeners.size
        }

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
                modifier = tip?.let { SwingModifier.name(it) } ?: SwingModifier,
            )
        }

        val button = onNodeOfType<JButton>()
        button.assertProperty("Runs the task", "the modifier applies to the button") { name }

        tip = "Starts the build"
        awaitIdle()
        button.assertProperty("Starts the build", "the modifier follows the state driving it") { name }

        tip = null
        awaitIdle()
        button.assertProperty(null, "dropping the element restores the name the button had without it") { name }

        onNodeOfType<JButton>().performClick()
        assertEquals(1, fired, "a modifier change must not displace the listener the overload installs")
    }
}
