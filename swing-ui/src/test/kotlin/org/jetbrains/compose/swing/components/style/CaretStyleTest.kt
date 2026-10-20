package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavioral coverage for the caret a text component is given and the rate it blinks at.
 *
 * A caret is a stateful object rather than a value: installing one puts it at offset 0 with nothing
 * selected, so these pin that the declared caret both reaches the component and is installed exactly
 * once, and that the caret the component carried before comes back when the declaration leaves.
 */
class CaretStyleTest {
    @Test
    fun caretSelectionSurvivesRecompositionAndRemoval() = runComposeSwingTest {
        val declaredCaret = DefaultCaret()
        var declared by mutableStateOf(true)
        var tip by mutableStateOf("first")
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (declared) caret(declaredCaret)
                        toolTip(tip)
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(0, declaredCaret.blinkRate)
        field.caretPosition = 4
        assertEquals(4, declaredCaret.dot)
        assertEquals(4, field.caretPosition)
        field.select(SELECTION_START, SELECTION_END)

        tip = "second"
        awaitIdle()
        assertSame(declaredCaret, field.caret)
        assertEquals(SELECTION_START, field.selectionStart)
        assertEquals(SELECTION_END, field.selectionEnd)

        declared = false
        awaitIdle()
        assertNotSame(declaredCaret, field.caret)
        assertEquals(SELECTION_START, field.selectionStart)
        assertEquals(SELECTION_END, field.selectionEnd)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun movingCaretPreservesSelection(backward: Boolean) = runComposeSwingTest {
        val caret = DefaultCaret()
        val field = JTextField(TEXT)
        val host = JPanel()
        var inOuter by mutableStateOf(false)
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        SwingNode(
                            factory = { field },
                            modifier = SwingModifier.styleable(TestTextNodeKind) { caret(caret) },
                        )
                    }
                }
            SwingNode(factory = { host })
            if (inOuter) content()
        }
        awaitIdle()
        val nested = host.setContent(parent = context) { if (!inOuter) content() }
        try {
            awaitIdle()
            val mark = if (backward) SELECTION_END else SELECTION_START
            val dot = if (backward) SELECTION_START else SELECTION_END
            field.caretPosition = mark
            field.moveCaretPosition(dot)

            repeat(2) {
                inOuter = !inOuter
                awaitIdle()

                assertSame(caret, field.caret)
                assertEquals(SELECTION_START, field.selectionStart)
                assertEquals(SELECTION_END, field.selectionEnd)
                assertEquals(dot, field.caret.dot)
                assertEquals(mark, field.caret.mark)
            }
        } finally {
            nested.dispose()
        }
    }

    @Test
    fun rateRestoresAfterMovingCaret() = runComposeSwingTest {
        var inOuter by mutableStateOf(false)
        var blinking by mutableStateOf(true)
        val host = JPanel()
        val moved =
            movableContentOf {
                val caret = remember { DefaultCaret() }
                SwingNode(
                    factory = { JTextField("hello").also { it.caret.blinkRate = 500 } },
                    modifier =
                        SwingModifier.styleable(TestTextNodeKind) {
                            caret(caret)
                            if (blinking) caretBlinkRate(750)
                        },
                )
            }
        lateinit var context: CompositionContext
        setContent {
            context = rememberCompositionContext()
            SwingNode(factory = { host })
            if (inOuter) moved()
        }
        val nested = host.setContent(parent = context) { if (!inOuter) moved() }
        try {
            awaitIdle()
            onNodeOfType<JTextField>().assertProperty(750) { caret.blinkRate }

            inOuter = true
            awaitIdle()
            onNodeOfType<JTextField>().assertProperty(750) { caret.blinkRate }

            blinking = false
            awaitIdle()
            onNodeOfType<JTextField>().assertProperty(0) { caret.blinkRate }
        } finally {
            nested.dispose()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun replacementCaretFollowsTheLatestRate(changeRate: Boolean) = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        var rate by mutableStateOf(BLINK_RATE)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caret(if (swapped) second else first)
                        caretBlinkRate(rate)
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(BLINK_RATE, first.blinkRate)

        // The rate changes on a pass of its own, so the caret arriving on the next one is written from
        // what the rate stands at now and not from what it stood at when it was first declared.
        if (changeRate) rate = SLOW_BLINK_RATE
        awaitIdle()
        val latestRate = if (changeRate) SLOW_BLINK_RATE else BLINK_RATE
        assertEquals(latestRate, first.blinkRate)

        swapped = true
        awaitIdle()
        assertSame(second, field.caret)
        assertEquals(latestRate, second.blinkRate)

        // The declaration still stands over the field, and the caret it let go of is no longer its target.
        rate = BLINK_RATE
        awaitIdle()
        assertEquals(BLINK_RATE, second.blinkRate)
        assertEquals(latestRate, first.blinkRate)
    }

    @Test
    fun rateSurvivesNullCaretAndRestoresOntoTheReplacement() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier = SwingModifier.styleable(TestTextNodeKind) { if (declared) caretBlinkRate(BLINK_RATE) },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val found = JTextField().caret.blinkRate
        assertEquals(BLINK_RATE, field.caret.blinkRate)
        val installed = field.caret
        field.caret = null
        assertEquals(BLINK_RATE, installed.blinkRate)

        // A look and feel that answers a caret it is handed by installing one of its own is what this
        // stands in for: the caret the rate was read from is gone before the declaration leaves.
        val arriving = DefaultCaret()
        field.caret = arriving
        assertEquals(BLINK_RATE, arriving.blinkRate)

        declared = false
        awaitIdle()

        assertEquals(found, field.caret.blinkRate)
    }

    @Test
    fun removedRateLeavesNewCaretsAlone() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caret(if (swapped) second else first)
                        if (declared) caretBlinkRate(BLINK_RATE)
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(BLINK_RATE, first.blinkRate)

        // Dropping the rate leaves the field with nothing declaring one, so the caret installed on the
        // next pass is only reached if the dropped declaration is still listening for it.
        declared = false
        awaitIdle()
        assertEquals(0, first.blinkRate)

        swapped = true
        awaitIdle()
        assertSame(second, field.caret)
        assertEquals(0, second.blinkRate)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun caretAndRateRestoreInEitherOrder(rateFirst: Boolean) = runComposeSwingTest {
        val declaredCaret = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (decorated) {
                            if (rateFirst) caretBlinkRate(BLINK_RATE)
                            caret(declaredCaret)
                            if (!rateFirst) caretBlinkRate(BLINK_RATE)
                        }
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installedRate = JTextField().caret.blinkRate
        assertEquals(BLINK_RATE, declaredCaret.blinkRate)

        decorated = false
        awaitIdle()
        assertNotSame(declaredCaret, field.caret)
        assertEquals(installedRate, field.caret.blinkRate)
        assertEquals(if (rateFirst) BLINK_RATE else 0, declaredCaret.blinkRate)
    }

    @Test
    fun readdedCaretRestoresRateAfterSlotOrderChanges() = runComposeSwingTest {
        val mine = DefaultCaret()
        var declared by mutableStateOf(true)
        var styled by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (styled) {
                            if (declared) caret(mine)
                            caretBlinkRate(BLINK_RATE)
                        }
                    },
            )
        }
        val field = onNodeOfType<JTextField>()
        assertEquals(BLINK_RATE, mine.blinkRate)

        // The caret leaves the style and comes back, so the slot holding it is no longer the older
        // of the two. Whichever slot comes apart first, the rate restores what it read to the caret it
        // read it from. The field's own caret was given the rate while it stood in for the declared
        // one, and keeps it the way any caret the declaration let go of does.
        declared = false
        awaitIdle()
        declared = true
        awaitIdle()
        styled = false
        awaitIdle()

        assertEquals(0, mine.blinkRate)
        field.assertProperty(BLINK_RATE, "the caret given the rate while it stood keeps it") { caret.blinkRate }
    }

    @Test
    fun lookAndFeelReplacementKeepsRateAndPolicy() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JTextField(TEXT) },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caretBlinkRate(BLINK_RATE)
                        caretUpdatePolicy(DefaultCaret.NEVER_UPDATE)
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caret

        // A look and feel takes its own caret off the field before putting the next one on, so the field
        // carries none for the span of the swap and the rate has nothing to be written to until it ends.
        // It gives the caret it installs the rate its own defaults name, and fires the UI property change
        // only once that is done.
        SwingUtilities.updateComponentTreeUI(field)
        awaitIdle()

        assertNotSame(installed, field.caret)
        assertEquals(BLINK_RATE, field.caret.blinkRate)
        assertEquals(DefaultCaret.NEVER_UPDATE, (field.caret as DefaultCaret).updatePolicy)
    }

    @Test
    fun rateOverridesPreinstallStash() = runComposeSwingTest {
        // A caret given a rate while it carries no component stashes it and answers with the stash from
        // then on. A look and feel that rates its caret as it installs leaves every caret it builds in
        // that state, so a declaration written afterwards has to reach what the caret answers with, not
        // only its blink timer.
        val stashed = DefaultCaret()
        stashed.blinkRate = SLOW_BLINK_RATE
        assertEquals(SLOW_BLINK_RATE, stashed.blinkRate)

        setContent {
            SwingNode(
                factory = { JTextField(TEXT).also { it.caret = stashed } },
                modifier = SwingModifier.styleable(TestTextNodeKind) { caretBlinkRate(BLINK_RATE) },
            )
        }

        // The rate reaches whichever caret the component carries, so the case says which one before
        // reading the rate off it.
        assertSame(stashed, onNodeOfType<JTextField>().fetch().caret, "the field should still carry that caret")
        assertEquals(BLINK_RATE, stashed.blinkRate)
    }
}

private const val TEXT = "hello world"
private const val SELECTION_START = 2
private const val SELECTION_END = 5
private const val BLINK_RATE = 250
private const val SLOW_BLINK_RATE = 500
