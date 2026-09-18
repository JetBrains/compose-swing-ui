package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import java.awt.BorderLayout
import java.awt.Container
import java.util.concurrent.Executors
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeSwingTestSmokeTest {
    @Test
    fun clickRecomposesAndUpdatesLabel() = runComposeSwingTest {
        val count = mutableIntStateOf(0)
        setContent {
            Button(text = "Increment", onClick = { count.intValue++ })
            Label(text = "Count: ${count.intValue}")
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
            Panel(PanelLayout.Border()) {
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
        // the condition holds; here it returns on the first check since the click already awaited idle.
        waitUntil { root.findMatching(SwingMatcher.hasText("done")).isNotEmpty() }
        onNodeWithText("done").assertExists()
    }

    @Test
    fun textFieldIsOfType() = runComposeSwingTest {
        setContent { TextField(value = "x", onValueChange = { }) }
        val matches = root.findMatching(SwingMatcher.isOfType<JTextField>())
        assertEquals(1, matches.size)
    }

    @Test
    fun theRootIsDisplayableButNeverShowingAndRootsItsFocusCycle() = runComposeSwingTest {
        setContent { Label(text = "x") }

        assertTrue(root.isDisplayable, "the root is displayable, so an invalidation travels up to it")
        assertFalse(root.isShowing, "the root stands in no window, so it never shows")
        assertFalse(onNodeWithText("x").fetch<JLabel>().isShowing, "and nothing under it shows either")
        assertTrue(root.isFocusCycleRoot, "the root is the root of its focus cycle, as a window is")
    }

    @Test
    fun theRootIsNoLongerDisplayableOnceTheTestEnds() {
        var captured: Container? = null
        runComposeSwingTest {
            setContent { Label(text = "x") }
            captured = root
        }

        assertFalse(checkNotNull(captured).isDisplayable, "the root releases its peer when the test ends")
    }

    @Test
    fun effectsRunUnderTheGivenContextOnTheHarnessDispatcher() {
        var name: CoroutineName? = null
        var onDispatchThread = false
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { foreign ->
            runComposeSwingTest(effectContext = CoroutineName("given") + foreign) {
                setContent {
                    LaunchedEffect(Unit) {
                        name = coroutineContext[CoroutineName]
                        onDispatchThread = SwingUtilities.isEventDispatchThread()
                    }
                }
                awaitIdle()

                assertEquals(CoroutineName("given"), name, "an effect sees the element the test named")
                assertTrue(onDispatchThread, "the harness's dispatcher wins over the one the test named")
            }
        }
    }
}
