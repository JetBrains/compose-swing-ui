package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Validates the test harness itself: finders (unique vs. collection, by-text and by-name), the
 * assertion vocabulary (text/enabled/exists/constraint), and actions - so the regression tests above
 * are standing on a trustworthy foundation. Also asserts that assertions FAIL when they should.
 */
class TestApiContractTest {
    @Test
    fun uniqueFinderVsCollectionCount() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "dup")
                Label(text = "dup")
                Label(text = "solo")
            }
        }

        // onNodeWithText requires exactly one match; "dup" has two -> it must throw.
        assertFailsWith<AssertionError> { onNodeWithText("dup").assertExists() }
        onAllNodesWithText("dup").assertCountEquals(2)
        assertEquals(2, onAllNodesWithText("dup").fetchSize())
        onNodeWithText("solo").assertExists()
        onAllNodesWithText("solo").assertCountEquals(1)
    }

    @Test
    fun layoutConstraintAcrossAllRegions() = runComposeSwingTest {
        setContent {
            BorderPanel {
                Label(text = "N", modifier = SwingModifier.north())
                Label(text = "C", modifier = SwingModifier.center())
                Label(text = "S", modifier = SwingModifier.south())
                Label(text = "W", modifier = SwingModifier.west())
                Label(text = "E", modifier = SwingModifier.east())
            }
        }

        onNodeWithText("N").assertLayoutConstraint(BorderLayout.NORTH)
        onNodeWithText("C").assertLayoutConstraint(BorderLayout.CENTER)
        onNodeWithText("S").assertLayoutConstraint(BorderLayout.SOUTH)
        onNodeWithText("W").assertLayoutConstraint(BorderLayout.WEST)
        onNodeWithText("E").assertLayoutConstraint(BorderLayout.EAST)

        assertFailsWith<AssertionError> {
            onNodeWithText("N").assertLayoutConstraint(BorderLayout.SOUTH)
        }
    }

    @Test
    fun enabledAssertionsReflectState() = runComposeSwingTest {
        var on by mutableStateOf(true)
        setContent {
            Button(text = "toggle", modifier = SwingModifier.enabled(on))
        }

        onNodeWithText("toggle").assertIsEnabled()
        assertFailsWith<AssertionError> { onNodeWithText("toggle").assertIsNotEnabled() }

        on = false
        awaitIdle()

        onNodeWithText("toggle").assertIsNotEnabled()
        assertFailsWith<AssertionError> { onNodeWithText("toggle").assertIsEnabled() }
    }

    @Test
    fun textEqualsAssertionIsExact() = runComposeSwingTest {
        setContent { Label(text = "exact value") }

        onNodeWithText("exact value").assertTextEquals("exact value")
        assertFailsWith<AssertionError> {
            onNodeWithText("exact value").assertTextEquals("wrong")
        }
        onNodeWithText("value", substring = true).assertExists()
    }

    @Test
    fun byNameFinderLocatesComponent() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "noname")
                SwingNode(
                    factory = { JLabel() },
                    update = {
                        set("named") { this.text = it }
                        set("labelId") { this.name = it }
                    },
                )
            }
        }

        onNodeWithName("labelId").assertExists().assertTextEquals("named")
        onNodeWithName("does-not-exist").assertDoesNotExist()
    }

    @Test
    fun assertExistsChainsAndActionsReturnInteraction() = runComposeSwingTest {
        var clicks by mutableIntStateOf(0)
        setContent {
            BoxPanel {
                Button(text = "click me", onClick = { clicks++ })
                Label(text = "clicks=$clicks")
            }
        }

        // Chaining: assertExists -> assertIsEnabled -> performClick all return the interaction.
        onNodeWithText("click me").assertExists().assertIsEnabled().performClick()
        onNodeWithText("clicks=1").assertExists()
        check(clicks == 1)
    }

    @Test
    fun performClickRejectsNonButton() = runComposeSwingTest {
        setContent { Label(text = "not a button") }
        assertFailsWith<AssertionError> { onNodeWithText("not a button").performClick() }
    }

    @Test
    fun performTextInputRejectsNonTextComponent() = runComposeSwingTest {
        setContent { Label(text = "label") }
        assertFailsWith<AssertionError> { onNodeWithText("label").performTextInput("x") }
    }

    @Test
    fun rootInteractionResolves() = runComposeSwingTest {
        setContent { Label(text = "child") }
        onRoot().assertExists()
    }

    @Test
    fun textComponentTextEqualsReadsFieldText() = runComposeSwingTest {
        setContent { TextField(value = "field text") }
        onNodeWithText("field text").assertTextEquals("field text")
    }

    @Test
    fun genericMatcherFindersResolveByType() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "a")
                Button(text = "b", onClick = {})
                Button(text = "c", onClick = {})
            }
        }

        onNode(SwingMatcher.isOfType<JLabel>()).assertExists().assertTextEquals("a")
        onAllNodes(SwingMatcher.isOfType<JButton>()).assertCountEquals(2)
        assertFailsWith<AssertionError> { onNode(SwingMatcher.isOfType<JButton>()).assertExists() }
        onNode(SwingMatcher.isOfType<JButton>() and SwingMatcher.hasText("b")).assertExists()
    }

    @Test
    fun reifiedTypeFindersAreConvenienceForMatcherFinders() = runComposeSwingTest {
        setContent {
            BoxPanel {
                TextField(value = "field")
                Button(text = "btn", onClick = {})
            }
        }

        onNodeOfType<JTextField>().assertTextEquals("field")
        onAllNodesOfType<JButton>().assertCountEquals(1)
    }

    @Test
    fun fetchReturnsTheTypedComponentForDirectDriving() = runComposeSwingTest {
        setContent { TextField(value = "typed") }

        // fetch returns the match typed, so the component's own API is reachable without a cast.
        val field: JTextField = onNodeOfType<JTextField>().fetch()
        assertEquals("typed", field.text, "fetch should return the field carrying its rendered text")
        assertSame(
            field,
            onNodeOfType<JTextField>().fetch<JTextField>(),
            "each fetch should return the same live instance",
        )
    }

    @Test
    fun fetchFailsWhenTheMatchedTypeMismatches() = runComposeSwingTest {
        setContent { Label(text = "just a label") }

        assertFailsWith<AssertionError> { onNodeWithText("just a label").fetch<JTextField>() }
    }
}
