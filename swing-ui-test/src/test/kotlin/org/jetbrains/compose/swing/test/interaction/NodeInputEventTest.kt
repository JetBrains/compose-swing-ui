package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.modifier.listener.mouseWheelListener
import org.jetbrains.compose.swing.platform.hostOs
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JSlider
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins what the harness's input gestures deliver: a real event, shaped the way the toolkit shapes one,
 * reaching the node's own handling.
 *
 * Each test asserts the widget behavior that only follows from a correctly shaped event - a button the
 * UI arms and fires, a slider thumb the UI drags, a character an editor inserts - rather than the event
 * itself, because a gesture that arrives with the wrong button, no modifiers or no position reaches the
 * component and then does nothing.
 */
class NodeInputEventTest {
    @Test
    fun clickingFiresTheButtonThroughItsOwnUi() = runComposeSwingTest {
        var clicks = 0
        setContent { Button(text = "Go", onClick = { clicks++ }, modifier = buttonSize) }

        onNodeOfType<JButton>().performClick()

        assertEquals(1, clicks, "a click the UI resolves fires the button exactly once")
    }

    @Test
    fun pressingAndReleasingFiresTheButtonOnce() = runComposeSwingTest {
        var clicks = 0
        setContent { Button(text = "Go", onClick = { clicks++ }, modifier = buttonSize) }

        onNodeOfType<JButton>().performMousePress()
        assertEquals(0, clicks, "a press arms the button; a button fires on the release")

        onNodeOfType<JButton>().performMouseRelease()
        assertEquals(1, clicks, "the release of an armed button fires it")
    }

    @Test
    fun aDoubleClickIsTwoClicksTheSecondCountedAsTwo() = runComposeSwingTest {
        val counts = mutableListOf<Int>()
        setContent {
            Button(
                text = "Go",
                onClick = { },
                modifier = buttonSize.mouseListener(onMouseClicked = { counts += it.clickCount }),
            )
        }

        onNodeOfType<JButton>().performClick(clicks = 2)

        assertEquals(
            listOf(1, 2),
            counts,
            "a double click is two clicks carrying their running count, which is how a component tells " +
                "one from two separate clicks",
        )
    }

    @Test
    fun aClickCarriesTheModifiersHeldThroughIt() = runComposeSwingTest {
        var held = 0
        setContent {
            Button(
                text = "Go",
                onClick = { },
                modifier = buttonSize.mouseListener(onMouseClicked = { held = it.modifiersEx }),
            )
        }

        onNodeOfType<JButton>().performClick(modifiers = InputEvent.SHIFT_DOWN_MASK)

        assertTrue(
            held and InputEvent.SHIFT_DOWN_MASK != 0,
            "a shift-click must reach the node holding shift, which is what extends a selection",
        )
    }

    @Test
    fun aSecondaryClickCarriesTheButtonItWasMadeWith() = runComposeSwingTest {
        var button = MouseEvent.NOBUTTON
        setContent {
            Button(
                text = "Go",
                onClick = { },
                modifier = buttonSize.mouseListener(onMouseClicked = { button = it.button }),
            )
        }

        onNodeOfType<JButton>().performClick(button = MouseEvent.BUTTON3)

        assertEquals(MouseEvent.BUTTON3, button, "the click must name the button it was asked for")
    }

    @Test
    fun aContextClickReportsItselfAsThePopupTrigger() = runComposeSwingTest {
        val triggers = mutableListOf<Int>()
        setContent {
            Button(
                text = "Go",
                onClick = { },
                modifier =
                    buttonSize.mouseListener(
                        onMousePressed = { if (it.isPopupTrigger) triggers += it.id },
                        onMouseReleased = { if (it.isPopupTrigger) triggers += it.id },
                    ),
            )
        }

        onNodeOfType<JButton>().performContextClick()

        assertEquals(
            listOf(if (hostOs.isWindows) MouseEvent.MOUSE_RELEASED else MouseEvent.MOUSE_PRESSED),
            triggers,
            "the host platform carries the popup trigger on exactly one of the two events, so a " +
                "component reading both is asked once",
        )
    }

    @Test
    fun thePointerArrivesMovesAndLeaves() = runComposeSwingTest {
        val ids = mutableListOf<Int>()
        val motionClickCounts = mutableListOf<Int>()
        setContent {
            Button(
                text = "Go",
                onClick = { },
                modifier =
                    buttonSize
                        .mouseListener(
                            onMouseEntered = {
                                ids += it.id
                                motionClickCounts += it.clickCount
                            },
                            onMouseExited = {
                                ids += it.id
                                motionClickCounts += it.clickCount
                            },
                        ).mouseMotionListener(
                            onMouseMoved = {
                                ids += it.id
                                motionClickCounts += it.clickCount
                            },
                        ),
            )
        }

        onNodeOfType<JButton>().performMouseEnter()
        onNodeOfType<JButton>().performMouseMove(Point(3, 3))
        onNodeOfType<JButton>().performMouseExit()

        assertEquals(
            listOf(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_EXITED),
            ids,
            "the pointer must arrive, travel and leave as three separate notifications",
        )
        assertEquals(
            listOf(0, 0, 0),
            motionClickCounts,
            "motion events carry a click count of 0",
        )
    }

    @Test
    fun theWheelTurnsByTheNotchesItIsAskedFor() = runComposeSwingTest {
        var rotation = 0
        setContent {
            Button(
                text = "Go",
                onClick = { },
                modifier = buttonSize.mouseWheelListener { rotation = it.wheelRotation },
            )
        }

        onNodeOfType<JButton>().performMouseWheel(rotation = 2)

        assertEquals(2, rotation, "the wheel must turn by the notches the gesture names")
    }

    @Test
    fun draggingMovesTheSliderThumb() = runComposeSwingTest {
        var value by mutableIntStateOf(0)
        setContent { Slider(value = value, onValueChange = { value = it }, modifier = sliderSize) }

        val slider = onNodeOfType<JSlider>().fetch()
        val middleHeight = slider.height / 2
        onNodeOfType<JSlider>().performMouseDrag(
            from = Point(THUMB_GRAB_INSET, middleHeight),
            to = Point(slider.width - THUMB_GRAB_INSET, middleHeight),
        )

        assertEquals(
            slider.maximum,
            value,
            "a drag with the primary button held moves the thumb to where the drag ended",
        )
    }

    @Test
    fun typingInsertsCharactersThroughTheEditor() = runComposeSwingTest {
        var text by mutableStateOf("")
        setContent { TextField(value = text, onValueChange = { text = it }, columns = 10) }

        onNodeOfType<JTextField>().performTyping("Hi!")

        assertEquals("Hi!", text, "each typed character reaches the editor and is reported")
    }

    @Test
    fun aKeyPressRunsTheKeyBindingItIsBoundTo() = runComposeSwingTest {
        var clicks = 0
        setContent { Button(text = "Go", onClick = { clicks++ }, modifier = buttonSize) }

        onNodeOfType<JButton>().performKeyPress(KeyEvent.VK_SPACE)

        assertEquals(1, clicks, "space is bound to pressing a button, so the key press fires it")
    }

    @Test
    fun aCallerBuiltEventReachesTheNode() = runComposeSwingTest {
        var moved = false
        setContent {
            Button(
                text = "Go",
                onClick = {},
                modifier = buttonSize.mouseMotionListener(onMouseMoved = { moved = true }),
            )
        }

        onNodeOfType<JButton>().performEvent { button ->
            MouseEvent(
                button,
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                button.width / 2,
                button.height / 2,
                0,
                false,
                MouseEvent.NOBUTTON,
            )
        }

        assertTrue(moved, "an event the caller builds is delivered to the node it names")
    }

    @Test
    fun aDragReportsTheHeldButtonAsTheToolkitDoes() = runComposeSwingTest {
        val dragged = mutableListOf<MouseEvent>()
        setContent {
            Button(
                text = "Go",
                onClick = {},
                modifier = buttonSize.mouseMotionListener(onMouseDragged = { dragged += it }),
            )
        }

        onNodeOfType<JButton>().performMouseDrag(from = Point(2, 2), to = Point(20, 8))

        val event = dragged.single()
        assertEquals(MouseEvent.NOBUTTON, event.button, "a real drag names no button")
        assertTrue(
            event.modifiersEx and InputEvent.BUTTON1_DOWN_MASK != 0,
            "a real drag keeps the primary button's mask down, which is how a UI reads the held button",
        )
    }

    private companion object {
        val buttonSize: SwingModifier = SwingModifier.preferredSize(120, 30)
        val sliderSize: SwingModifier = SwingModifier.preferredSize(200, 30)

        /** Far enough into the slider to be on the thumb at its minimum, in the node's coordinates. */
        const val THUMB_GRAB_INSET: Int = 5
    }
}
