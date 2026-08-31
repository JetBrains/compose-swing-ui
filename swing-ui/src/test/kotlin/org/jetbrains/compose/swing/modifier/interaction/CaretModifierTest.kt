package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the caret a text component is given and the rate it blinks at.
 *
 * A caret is a stateful object rather than a value: installing one puts it at offset 0 with nothing
 * selected, so these pin that the declared caret both reaches the component and is installed exactly
 * once, and that the caret the component carried before comes back when the declaration leaves.
 */
class CaretModifierTest {
    @Test
    fun aDeclaredCaretReplacesTheOneTheFieldCarriedAndIsPutBackOnRemoval() = runComposeSwingTest {
        val caret = DefaultCaret()
        var declared by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) SwingModifier.caret(caret) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caret
        assertNotSame(caret, installed, "the field starts with the caret its look and feel installed")

        declared = true
        awaitIdle()
        assertSame(caret, field.caret, "the declared caret should reach the field")

        declared = false
        awaitIdle()
        assertSame(installed, field.caret, "dropping the declaration should put back the caret the field carried")
    }

    @Test
    fun theDeclaredCaretIsTheOneTheFieldNavigatesWith() = runComposeSwingTest {
        val caret = DefaultCaret()
        setContent {
            SwingNode(factory = { JTextField(TEXT) }, modifier = SwingModifier.caret(caret))
        }
        val field = onNodeOfType<JTextField>().fetch()

        field.caretPosition = CARET_OFFSET

        assertEquals(CARET_OFFSET, caret.dot, "moving the field's caret should run through the declared caret")
        assertEquals(CARET_OFFSET, field.caretPosition, "the field reports the position of the caret it was given")
    }

    @Test
    fun aCaretRedeclaredAcrossARecompositionKeepsTheSelectionMadeBetweenPasses() = runComposeSwingTest {
        val caret = DefaultCaret()
        var tip by mutableStateOf("first")
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caret(caret).toolTip(tip),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.select(SELECTION_START, SELECTION_END)

        // The chain changes, so it is diffed again with the same caret declared in it. Installing that
        // caret a second time would put it at offset 0 and drop what the user selected.
        tip = "second"
        awaitIdle()

        assertSame(caret, field.caret, "the same caret should stay installed across the pass")
        assertEquals(SELECTION_START, field.selectionStart, "the selection made between passes should survive")
        assertEquals(SELECTION_END, field.selectionEnd, "the selection made between passes should survive")
    }

    @Test
    fun aDeclaredBlinkRateReachesTheCaretAndIsPutBackOnRemoval() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = if (declared) SwingModifier.caretBlinkRate(0) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installedRate = field.caret.blinkRate
        assertTrue(installedRate > 0, "a look and feel gives the caret it installs a blink rate of its own")

        declared = true
        awaitIdle()
        assertEquals(0, field.caret.blinkRate, "the declared rate should reach the caret")

        declared = false
        awaitIdle()
        assertEquals(installedRate, field.caret.blinkRate, "dropping it should put back the rate the caret carried")
    }

    @Test
    fun aCaretHandedToTheFieldBlinksOnlyOnceItIsGivenARate() = runComposeSwingTest {
        val silent = DefaultCaret()
        val blinking = DefaultCaret()
        setContent {
            SwingNode(factory = { JTextField(TEXT) }, modifier = SwingModifier.caret(silent))
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.caret(blinking).caretBlinkRate(BLINK_RATE),
            )
        }

        assertEquals(0, silent.blinkRate, "a look and feel's blink rate reaches only the caret it created itself")
        assertEquals(BLINK_RATE, blinking.blinkRate, "the rate declared with the caret reaches it")
    }
}

private const val TEXT = "hello world"
private const val CARET_OFFSET = 4
private const val SELECTION_START = 2
private const val SELECTION_END = 5
private const val BLINK_RATE = 250
