// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.interaction.FocusRequester
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.style.TestLabelKind
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a chain, a style and the catalog do with a second declaration of an element that allows one value. */
class RedeclarationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    fun aSecondDifferentDeclarationIsRefused(
        case: String,
        modifier: SwingModifier,
    ) = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException>(case) {
                setContent { Label("x", modifier = modifier) }
                awaitIdle()
            }

        assertConflictNames(error)
    }

    @Test
    fun aSecondDifferentFocusRequesterIsRefused() = runComposeSwingTest {
        val modifier = SwingModifier.focusRequester(FocusRequester()).focusRequester(FocusRequester())
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("x", modifier = modifier)
                }
                awaitIdle()
            }

        assertTrue("focusRequester" in error.message.orEmpty(), "the error names the declaration: ${error.message}")
        assertTrue("declared twice" in error.message.orEmpty(), "the error says why: ${error.message}")
    }

    @Test
    fun aChainAndAStyleChangingFromAToBInOnePassSettleAtB() = runComposeSwingTest {
        var value by mutableStateOf("a")
        setContent {
            Label(
                "x",
                modifier =
                    SwingModifier.then(Value(KEY, value)).styleable(TestLabelKind) { OneValueProperty.provide(value) },
            )
        }
        awaitIdle()
        value = "b"
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("b") { getClientProperty(KEY) }
    }

    @Test
    fun aStandingConflictWhoseOtherDeclarationChangesValueIsReportedAgain() = runComposeSwingTest {
        var style by mutableStateOf("b")
        val first =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label(
                        "x",
                        modifier =
                            SwingModifier
                                .then(Value(KEY, "a"))
                                .styleable(TestLabelKind) { DescriptorProperty.provide(style) },
                    )
                }
            }
        assertConflictNames(first)
        style = "c"

        val error = assertFailsWith<IllegalStateException> { awaitIdle() }

        assertConflictNames(error)
    }

    @Test
    fun aStandingConflictSettlingAgainOnTheSameValuesIsReportedOnce() = runComposeSwingTest {
        var chain by mutableStateOf("a")
        var style by mutableStateOf("b")
        val first =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label(
                        "x",
                        modifier =
                            SwingModifier
                                .then(Value(KEY, chain))
                                .styleable(TestLabelKind) { OneValueProperty.provide(style) },
                    )
                }
            }
        assertConflictNames(first)
        chain = "b"
        style = "a"

        val again = runCatching { awaitIdle() }.exceptionOrNull()

        assertNull(again, "the conflict stands on the same two values, so it is not reported again: $again")
    }

    @Test
    fun aConflictAPassClearsBeforeTheCompositionSettlesIsNotReported() = runComposeSwingTest {
        var chain by mutableStateOf("a")
        var style by mutableStateOf("a")
        setContent {
            SideEffect { if (style != chain) style = chain }
            Label(
                "x",
                modifier =
                    SwingModifier.then(Value(KEY, chain)).styleable(TestLabelKind) { OneValueProperty.provide(style) },
            )
        }
        awaitIdle()
        chain = "b"
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("b") { getClientProperty(KEY) }
    }

    @Test
    fun equalDeclarationsInOneChainAndOneStyleCoalesce() = runComposeSwingTest {
        setContent {
            Label(
                "x",
                modifier =
                    SwingModifier
                        .then(Value(KEY, "a"))
                        .then(Value(KEY, "a"))
                        .styleable(TestLabelKind) {
                            OtherValueProperty.provide("c")
                            OtherValueProperty.provide("c")
                        },
            )
        }
        awaitIdle()

        onNodeOfType<JLabel>().assertProperty("c") { getClientProperty(OTHER) }
    }

    @Test
    fun twoKeysWhoseRefusingElementsShareANameAreEachReported() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label(
                        "x",
                        modifier =
                            SwingModifier
                                .then(Value(KEY, "a"))
                                .then(Value(OTHER, "a"))
                                .styleable(TestLabelKind) {
                                    OneValueProperty.provide("b")
                                    OtherValueProperty.provide("b")
                                },
                    )
                }
                awaitIdle()
            }

        assertEquals(1, error.suppressedExceptions.size, "one report per key: ${error.suppressedExceptions}")
    }

    private fun assertConflictNames(error: IllegalStateException) {
        assertTrue(KEY in error.message.orEmpty(), "the error names the declaration: ${error.message}")
        assertTrue("declared twice" in error.message.orEmpty(), "the error says why: ${error.message}")
    }

    private class Value(
        private val slot: String,
        private val value: String,
    ) : SwingModifier.NodeElement<JLabel, Value.Node>() {
        override val targetType: Class<JLabel> get() = JLabel::class.java

        override val key: Any get() = slot

        override val name: String get() = KEY

        override val allowsRedeclaration: Boolean get() = false

        override fun create(): Node = Node()

        override fun update(node: Node) {
            node.component.putClientProperty(slot, value)
        }

        override fun equals(other: Any?): Boolean = other is Value && other.slot == slot && other.value == value

        override fun hashCode(): Int = 31 * slot.hashCode() + value.hashCode()

        class Node : SwingModifier.ComponentNode<JLabel>()
    }

    companion object {
        private const val KEY = "oneValue"
        private const val OTHER = "otherValue"

        @JvmStatic
        fun refusals(): List<Arguments> = listOf(
            Arguments.of("two in one chain", SwingModifier.then(Value(KEY, "a")).then(Value(KEY, "b"))),
            Arguments.of(
                "two in one style",
                SwingModifier.styleable(TestLabelKind) {
                    OneValueProperty.provide("a")
                    OneValueProperty.provide("b")
                },
            ),
            Arguments.of(
                "descriptor after the element in a style",
                SwingModifier.styleable(TestLabelKind) {
                    OneValueProperty.provide("a")
                    DescriptorProperty.provide("b")
                },
            ),
            Arguments.of(
                "element after the descriptor in a style",
                SwingModifier.styleable(TestLabelKind) {
                    DescriptorProperty.provide("b")
                    OneValueProperty.provide("a")
                },
            ),
            Arguments.of(
                "style after the chain",
                SwingModifier.then(Value(KEY, "a")).styleable(TestLabelKind) { OneValueProperty.provide("b") },
            ),
            Arguments.of(
                "chain after a style",
                SwingModifier.styleable(TestLabelKind) { OneValueProperty.provide("a") }.then(Value(KEY, "b")),
            ),
            Arguments.of(
                "two styles",
                SwingModifier
                    .styleable(TestLabelKind) { OneValueProperty.provide("a") }
                    .styleable(TestLabelKind) { OneValueProperty.provide("b") },
            ),
        )

        private val OneValueProperty = stylePropertyOf<String> { Value(KEY, it) }
        private val OtherValueProperty = stylePropertyOf<String> { Value(OTHER, it) }

        private val DescriptorProperty =
            stylePropertyOf(
                ComponentPropertyDescriptor<JLabel, Any?>(
                    KEY,
                    read = { it.getClientProperty(KEY) },
                    write = { label, value -> label.putClientProperty(KEY, value) },
                ),
            )
    }
}
