package org.jetbrains.compose.swing.test

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.BorderLayout
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeSwingTestSmokeTest {
    @Test
    fun clickRecomposesAndUpdatesLabel() = runComposeSwingTest {
        val count = mutableIntStateOf(0)
        setContent {
            Button(text = "Increment", onClick = { count.value++ })
            Label(text = "Count: ${count.value}")
        }

        onNodeWithText("Count: 0").assertExists()
        onNodeWithText("Increment").assertExists().assertIsEnabled()

        onNodeWithText("Increment").performClick()

        onNodeWithText("Count: 0").assertDoesNotExist()
        onNodeWithText("Count: 1").assertExists()
    }

    @Test
    fun textInputDrivesState() = runComposeSwingTest {
        var current = "start"
        val value = mutableStateOf("start")
        setContent {
            TextField(value = value.value, onValueChange = {
                value.value = it
                current = it
            })
        }

        onNodeWithText("start").performTextReplacement("hello")
        onNodeWithText("hello").assertExists()
        assertEquals("hello", current)
    }

    @Test
    fun bordersExposeConstraints() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label(text = "N", modifier = SwingModifier.north())
                Label(text = "C", modifier = SwingModifier.center())
            }
        }

        onNodeWithText("N").assertLayoutConstraint(BorderLayout.NORTH)
        onNodeWithText("C").assertLayoutConstraint(BorderLayout.CENTER)
    }

    @Test
    fun collectionCountAndType() = runComposeSwingTest {
        setContent {
            Label(text = "dup")
            Label(text = "dup")
            Label(text = "other")
        }

        onAllNodesWithText("dup").assertCountEquals(2)
        onAllNodesWithText("o", substring = true).assertCountEquals(1)
    }

    @Test
    fun clickShowsStateThroughDeterministicIdle() = runComposeSwingTest {
        val ready = mutableStateOf(false)
        setContent {
            Button(text = "go", onClick = { ready.value = true })
            if (ready.value) Label(text = "done")
        }
        onNodeWithName("missing").assertDoesNotExist()
        // performClick already drives awaitIdle, so the recomposed "done" label is present without
        // any wall-clock wait. Prefer the deterministic idle gate over the waitUntil escape hatch.
        onNodeWithText("go").performClick()
        onNodeWithText("done").assertExists()
    }

    @Test
    fun waitUntilEscapeHatchObservesRecomposedState() = runComposeSwingTest {
        val ready = mutableStateOf(false)
        setContent {
            Button(text = "go", onClick = { ready.value = true })
            if (ready.value) Label(text = "done")
        }
        onNodeWithText("go").performClick()
        // Exercises the escape hatch directly. It pumps frames (bounded by the iteration cap) until
        // the condition holds; here it returns on the first check since the click already settled.
        waitUntil { root.findMatching(SwingMatcher.hasText("done")).isNotEmpty() }
        onNodeWithText("done").assertExists()
    }

    @Test
    fun textFieldIsOfType() = runComposeSwingTest {
        setContent { TextField(value = "x", onValueChange = { }) }
        val matches = root.findMatching(SwingMatcher.isOfType<JTextField>())
        assertEquals(1, matches.size)
    }
}
