package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performTextInput
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Validates the test harness itself, including that its assertions fail when they should.
 */
class TestApiContractTest {
    @Test
    fun uniqueFinderVsCollectionCount() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
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
    fun enabledAssertionsReflectState() = runComposeSwingTest {
        var on by mutableStateOf(true)
        setContent {
            Button(text = "toggle", onClick = { }, modifier = SwingModifier.enabled(on))
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
            Panel(PanelLayout.Box()) {
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
            Panel(PanelLayout.Box()) {
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
    fun performClickDeliversMouseEventsToALabelWithoutChangingItsText() = runComposeSwingTest {
        val delivered = mutableListOf<Int>()
        setContent {
            Label(
                text = "not a button",
                modifier =
                    SwingModifier.mouseListener(
                        onMousePressed = { delivered += it.id },
                        onMouseReleased = { delivered += it.id },
                        onMouseClicked = { delivered += it.id },
                    ),
            )
        }

        val label = onNodeWithText("not a button")
        label.assertIsDisplayed().assertTextEquals("not a button")
        assertEquals(emptyList(), delivered)

        label.performClick().assertTextEquals("not a button")

        assertEquals(
            listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED),
            delivered,
        )
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
        setContent { TextField(value = "field text", onValueChange = { }) }
        onNodeWithText("field text").assertTextEquals("field text")
    }

    @Test
    fun genericMatcherFindersResolveByType() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
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
            Panel(PanelLayout.Box()) {
                TextField(value = "field", onValueChange = { })
                Button(text = "btn", onClick = {})
            }
        }

        onNodeOfType<JTextField>().assertTextEquals("field")
        onAllNodesOfType<JButton>().assertCountEquals(1)
    }

    @Test
    fun fetchReturnsTheTypedComponentForDirectDriving() = runComposeSwingTest {
        setContent { TextField(value = "typed", onValueChange = { }) }

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
    fun performClickFiresTheButtonFromTheEventsTheToolkitIsHandedFirst() = runComposeSwingTest {
        // A widget only asks for the repaint a change provokes once it is processing an event, and the
        // recomposer settles a declaration ahead of that repaint, from a frame queued the moment the
        // toolkit is handed the event. A click written straight onto the widget skips that, so a
        // wrapper whose behavior differs between a user's change and a plain write cannot be
        // exercised through this action unless the click is made inside an event.
        val order = mutableListOf<String>()
        val watcher = AWTEventListener { order += "toolkit" }

        setContent { Button(text = "Go", onClick = { order += "click" }) }
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(watcher, AWTEvent.MOUSE_EVENT_MASK)
        try {
            onNodeOfType<JButton>().performClick()
        } finally {
            toolkit.removeAWTEventListener(watcher)
        }

        assertEquals(
            listOf("toolkit", "toolkit", "click", "toolkit"),
            order,
            "a click is the press, the release and the MOUSE_CLICKED the toolkit delivers, and the button " +
                "fires from the release it is handed - so the toolkit sees the press and the release before " +
                "the button acts, which is what lets the recomposer queue its frame ahead of the repaint " +
                "the click provokes",
        )
    }

    @Test
    fun performTextReplacementEditsFromTheKeyEventsTheToolkitIsHandedFirst() = runComposeSwingTest {
        val order = mutableListOf<String>()
        val watcher = AWTEventListener { order += "toolkit" }

        setContent { TextField(value = "Ada", onValueChange = { order += "edit" }) }
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(watcher, AWTEvent.KEY_EVENT_MASK)
        try {
            onNodeOfType<JTextField>().performTextReplacement("Adam")
        } finally {
            toolkit.removeAWTEventListener(watcher)
        }

        assertEquals(
            listOf("toolkit", "edit"),
            order.distinct(),
            "the text is typed, so the toolkit is handed the key events before the edit they make " +
                "reaches the document - which is what lets the recomposer queue its frame ahead of the " +
                "repaint the edit provokes",
        )
    }
}
