// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.setterCaseTests
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import javax.swing.JTextArea
import javax.swing.text.DefaultCaret
import kotlin.test.Test
import kotlin.test.assertEquals

class CaretUpdatePolicyStyleTest {
    @TestFactory
    fun settersWriteUpdateAndRestore(): List<DynamicTest> = setterCaseTests(
        listOf(
            textCase("caretUpdatePolicy", DefaultCaret.ALWAYS_UPDATE, DefaultCaret.NEVER_UPDATE, {
                (it.caret as DefaultCaret).updatePolicy
            }) { caretUpdatePolicy(it) },
        ),
    )

    @ParameterizedTest
    @CsvSource("-1, false, 21", "1, false, 11", "2, true, 21", "0, true, 11")
    fun policyControlsCaretMovement(
        policy: Int,
        offEdt: Boolean,
        expected: Int,
    ) = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JTextArea("hello world").apply { caretPosition = 11 } },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (policy != -1) caretUpdatePolicy(policy)
                    },
            )
        }
        onNodeOfType<JTextArea>().apply {
            val insert = { fetch<JTextArea>().document.insertString(0, "appended: ", null) }
            if (offEdt) {
                Thread(insert).apply {
                    start()
                    join()
                }
            } else {
                insert()
            }
            awaitIdle()
            assertProperty(expected) { caretPosition }
        }
    }

    @Test
    fun aPolicyDeclaredBeforeTheCaretReachesItAndComesApartAfterIt() = runComposeSwingTest {
        val mine = DefaultCaret()
        var decorated by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JTextArea("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        if (decorated) {
                            caretUpdatePolicy(DefaultCaret.NEVER_UPDATE)
                            caret(mine)
                        }
                    },
            )
        }
        val installedPolicy = (JTextArea().caret as DefaultCaret).updatePolicy
        assertEquals(DefaultCaret.NEVER_UPDATE, mine.updatePolicy)

        // The caret is declared last, so it comes apart first: the area carries its own caret again
        // while the policy declaration still stands, and the policy hands that caret back what it read.
        decorated = false
        awaitIdle()

        onNodeOfType<JTextArea>().apply {
            assertProperty(true) { caret !== mine }
            assertProperty(installedPolicy) { (caret as DefaultCaret).updatePolicy }
        }
        assertEquals(DefaultCaret.NEVER_UPDATE, mine.updatePolicy)
    }

    @Test
    fun aCaretReplacingAnotherUnderTheSamePolicyHoldsToIt() = runComposeSwingTest {
        val first = DefaultCaret()
        val second = DefaultCaret()
        var swapped by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { JTextArea("hello world") },
                modifier =
                    SwingModifier.styleable(TestTextNodeKind) {
                        caret(if (swapped) second else first)
                        caretUpdatePolicy(DefaultCaret.NEVER_UPDATE)
                    },
            )
        }
        // The caret slot writes on this pass, so the policy slot declared after it is written again
        // rather than reused, onto the caret that has just arrived.
        swapped = true
        awaitIdle()
        onNodeOfType<JTextArea>().assertProperty(true) { caret === second }

        onNodeOfType<JTextArea>().apply {
            fetch().caretPosition = 11
            fetch().document.insertString(0, "appended: ", null)
        }
        awaitIdle()

        onNodeOfType<JTextArea>().assertProperty(11) { caretPosition }
    }
}
