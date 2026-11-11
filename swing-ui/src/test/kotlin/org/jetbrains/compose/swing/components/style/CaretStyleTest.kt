// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.setterCaseTests
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral coverage for the caret a text component is given and the rate it blinks at.
 *
 * A caret is a stateful object rather than a value: installing one puts it at offset 0 with nothing
 * selected, so these pin that the declared caret both reaches the component and is installed exactly
 * once, and that the caret the component carried before comes back when the declaration leaves.
 */
class CaretStyleTest {
    @TestFactory
    fun settersWriteUpdateAndRestore(): List<DynamicTest> = setterCaseTests(
        listOf(
            textCase("caret", DefaultCaret(), DefaultCaret(), { it.caret }) { caret(it) },
            textCase("caretBlinkRate", 250, 750, { it.caret.blinkRate }) { caretBlinkRate(it) },
        ),
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("textComponents")
    fun caretSelectionSurvivesRecompositionAndRemoval(content: @Composable (SwingStyle<TextStyleScope>) -> Unit) {
        runComposeSwingTest {
            val declaredCaret = DefaultCaret()
            var declared by mutableStateOf(false)
            var tip by mutableStateOf("first")
            setContent {
                val style =
                    SwingStyle<TextStyleScope> {
                        if (declared) caret(declaredCaret)
                        toolTip(tip)
                    }
                content(style)
            }
            awaitIdle()
            val originalCaret = onNodeOfType<JTextComponent>().fetch().caret
            declared = true
            awaitIdle()
            assertEquals(0, declaredCaret.blinkRate)
            onNodeOfType<JTextComponent>().assertProperty(4) {
                caretPosition = 4
                caretPosition
            }
            assertEquals(4, declaredCaret.dot)
            onNodeOfType<JTextComponent>().assertProperty(2 to 5) {
                select(2, 5)
                selectionStart to selectionEnd
            }

            tip = "second"
            awaitIdle()
            onNodeOfType<JTextComponent>().apply {
                assertProperty(declaredCaret) { caret }
                assertProperty(2 to 5) { selectionStart to selectionEnd }
            }

            declared = false
            awaitIdle()
            onNodeOfType<JTextComponent>().apply {
                assertProperty(originalCaret) { caret }
                assertProperty(2 to 5) { selectionStart to selectionEnd }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun movingCaretPreservesSelection(backward: Boolean) = runComposeSwingTest {
        val caret = DefaultCaret()
        val field = JTextField("hello world")
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
            val selectionStart = 2
            val selectionEnd = 5
            val mark = if (backward) selectionEnd else selectionStart
            val dot = if (backward) selectionStart else selectionEnd
            field.caretPosition = mark
            field.moveCaretPosition(dot)

            repeat(2) {
                inOuter = !inOuter
                awaitIdle()

                assertSame(caret, field.caret)
                assertEquals(selectionStart, field.selectionStart)
                assertEquals(selectionEnd, field.selectionEnd)
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
        var rate by mutableStateOf(250)
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caret(if (swapped) second else first)
                        caretBlinkRate(rate)
                    },
            )
        }
        onNodeOfType<JTextField>().apply {
            assertEquals(250, first.blinkRate)

            // The rate changes on a pass of its own, so the caret arriving on the next one is written from
            // what the rate stands at now and not from what it stood at when it was first declared.
            if (changeRate) rate = 500
            awaitIdle()
            val latestRate = if (changeRate) 500 else 250
            assertEquals(latestRate, first.blinkRate)

            swapped = true
            awaitIdle()
            assertProperty(true) { caret === second }
            assertEquals(latestRate, second.blinkRate)

            // The declaration still stands over the field, and the caret it let go of is no longer its target.
            rate = 250
            awaitIdle()
            assertEquals(250, second.blinkRate)
            assertEquals(latestRate, first.blinkRate)
        }
    }

    @Test
    fun rateSurvivesNullCaretAndRestoresOntoTheReplacement() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier = SwingModifier.styleable(TestTextNodeKind) { if (declared) caretBlinkRate(250) },
            )
        }
        val found = JTextField().caret.blinkRate
        onNodeOfType<JTextField>().apply {
            assertProperty(250) { caret.blinkRate }
            val installed = fetch().caret
            fetch().caret = null
            assertEquals(250, installed.blinkRate)
        }

        // A look and feel that answers a caret it is handed by installing one of its own is what this
        // stands in for: the caret the rate was read from is gone before the declaration leaves.
        val arriving = DefaultCaret()
        onNodeOfType<JTextField>().fetch().caret = arriving
        assertEquals(250, arriving.blinkRate)

        declared = false
        awaitIdle()

        onNodeOfType<JTextField>().assertProperty(found) { caret.blinkRate }
    }

    @Test
    fun aCaretArrivingAfterTheRateDeclarationIsDroppedIsLeftAlone() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caret(if (swapped) second else first)
                        if (declared) caretBlinkRate(250)
                    },
            )
        }
        assertEquals(250, first.blinkRate)

        // Dropping the rate leaves the field with nothing declaring one, so the caret installed on the
        // next pass is only reached if the dropped declaration is still listening for it.
        declared = false
        awaitIdle()
        assertEquals(0, first.blinkRate)

        swapped = true
        awaitIdle()
        onNodeOfType<JTextField>().assertProperty(true) { caret === second }
        assertEquals(0, second.blinkRate)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun caretAndRateRestoreInEitherOrder(rateFirst: Boolean) = runComposeSwingTest {
        val declaredCaret = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (decorated) {
                            if (rateFirst) caretBlinkRate(250)
                            caret(declaredCaret)
                            if (!rateFirst) caretBlinkRate(250)
                        }
                    },
            )
        }
        val installedRate = JTextField().caret.blinkRate
        assertEquals(250, declaredCaret.blinkRate)

        decorated = false
        awaitIdle()
        onNodeOfType<JTextField>().apply {
            assertProperty(true) { caret !== declaredCaret }
            assertProperty(installedRate) { caret.blinkRate }
        }
        assertEquals(if (rateFirst) 250 else 0, declaredCaret.blinkRate)
    }

    @Test
    fun aCaretLeavingAndComingBackUnderAStandingRateLeavesTheFieldAtTheDeclaredRate() = runComposeSwingTest {
        val mine = DefaultCaret()
        var declared by mutableStateOf(true)
        var styled by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (styled) {
                            if (declared) caret(mine)
                            caretBlinkRate(250)
                        }
                    },
            )
        }
        assertEquals(250, mine.blinkRate)

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
        onNodeOfType<JTextField>().assertProperty(250, "the caret given the rate while it stood keeps it") {
            caret.blinkRate
        }
    }

    @Test
    fun lookAndFeelReplacementKeepsRateAndPolicy() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JTextField("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caretBlinkRate(250)
                        caretUpdatePolicy(DefaultCaret.NEVER_UPDATE)
                    },
            )
        }
        onNodeOfType<JTextField>().apply {
            val installed = fetch().caret

            // A look and feel takes its own caret off the field before putting the next one on, so the field
            // carries none for the span of the swap and the rate has nothing to be written to until it ends.
            // It gives the caret it installs the rate its own defaults name, and fires the UI property change
            // only once that is done.
            SwingUtilities.updateComponentTreeUI(fetch())
            awaitIdle()

            assertProperty(true) { caret !== installed }
            assertProperty(250) { caret.blinkRate }
            assertProperty(DefaultCaret.NEVER_UPDATE) { (caret as DefaultCaret).updatePolicy }
        }
    }

    @Test
    fun aRateReachesACaretThatWasGivenOneBeforeItCarriedAComponent() = runComposeSwingTest {
        // A caret given a rate while it carries no component stashes it and answers with the stash from
        // then on. A look and feel that rates its caret as it installs leaves every caret it builds in
        // that state, so a declaration written afterwards has to reach what the caret answers with, not
        // only its blink timer.
        val stashed = DefaultCaret()
        stashed.blinkRate = 500
        assertEquals(500, stashed.blinkRate)

        setContent {
            SwingNode(
                factory = { JTextField("hello world").also { it.caret = stashed } },
                modifier = SwingModifier.styleable(TestTextNodeKind) { caretBlinkRate(250) },
            )
        }

        // The rate reaches whichever caret the component carries, so the case says which one before
        // reading the rate off it.
        assertSame(stashed, onNodeOfType<JTextField>().fetch().caret, "the field should still carry that caret")
        assertEquals(250, stashed.blinkRate)
    }

    companion object {
        @JvmStatic
        fun textComponents(): List<Named<@Composable (SwingStyle<TextStyleScope>) -> Unit>> {
            val textField: @Composable (SwingStyle<TextStyleScope>) -> Unit = { style ->
                TextField("hello world", onValueChange = {}, style = style)
            }
            val textArea: @Composable (SwingStyle<TextStyleScope>) -> Unit = { style ->
                TextArea("hello world", onValueChange = {}, style = style)
            }
            return listOf(
                Named.of("TextField", textField),
                Named.of("TextArea", textArea),
            )
        }
    }
}
