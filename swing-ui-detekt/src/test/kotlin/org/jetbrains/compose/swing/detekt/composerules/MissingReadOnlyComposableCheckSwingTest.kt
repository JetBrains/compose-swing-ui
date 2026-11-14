// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.composerules.MissingReadOnlyComposable
import org.jetbrains.compose.swing.rules.composerules.MissingReadOnlyComposableCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class MissingReadOnlyComposableCheckSwingTest {
    private val rule = MissingReadOnlyComposable.toDetektRule(Config.empty)

    private fun messages(
        @Language("kotlin") code: String,
    ): List<String> =
        rule
            .lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                    @ReadOnlyComposable
                    @Composable
                    fun currentValue(): Int = 42

                    @ReadOnlyComposable
                    @Composable
                    fun currentName(): String = "a"

                    fun twice(x: Int): Int = x * 2

                    $code
                    """,
                ),
            ).map { it.message }

    @ParameterizedTest(name = "does not report read only usage with {0}")
    @ValueSource(strings = ["n++", "n--", "++n", "--n"])
    fun `does not report read only usage with increment or decrement`(statement: String) {
        val messages =
            messages(
                """
                @Composable
                fun Example(): Int {
                    var n = currentValue()
                    $statement
                    return n
                }
                """,
            )

        assertEquals(emptyList(), messages)
    }

    @Test
    fun `reports read only usage with unary minus`() {
        val messages =
            messages(
                """
                @Composable
                fun Example(): Int = -currentValue()
                """,
            )

        assertEquals(listOf(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE), messages)
    }

    @ParameterizedTest(name = "reports read only usage with {0}")
    @ValueSource(strings = ["===", "!=="])
    fun `reports read only usage with identity comparison`(operator: String) {
        val messages =
            messages(
                """
                @Composable
                fun Example(other: String): Boolean = currentName() $operator other
                """,
            )

        assertEquals(listOf(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE), messages)
    }

    @ParameterizedTest(name = "does not report read only usage with library call taking {0}")
    @ValueSource(strings = ["::twice", "(::twice)", "fun(x: Int) = x", "{ it }", "label@{ it }"])
    fun `does not report read only usage with library call taking a function argument`(argument: String) {
        val messages =
            messages(
                """
                @Composable
                fun Example(): Int {
                    currentValue()
                    return sequenceOf(1).map($argument).sum()
                }
                """,
            )

        assertEquals(emptyList(), messages)
    }

    @Test
    fun `reports read only usage inside eager lambda passed in parentheses`() {
        val messages =
            messages(
                """
                @Composable
                fun Example(): Int = currentValue().let({ it + 1 })
                """,
            )

        assertEquals(listOf(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE), messages)
    }

    @Test
    fun `reports read only usage when a nested local function mutates state`() {
        val messages =
            messages(
                """
                @Composable
                fun Example(): Int {
                    fun reset() {
                        var n = 1
                        n = 0
                    }
                    return currentValue()
                }
                """,
            )

        assertEquals(listOf(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE), messages)
    }

    @Test
    fun `reports read only usage when a nested accessor mutates state`() {
        val messages =
            messages(
                """
                @Composable
                fun Example(): Int {
                    val counter =
                        object {
                            val next: Int
                                get() {
                                    var n = 0
                                    n++
                                    return n
                                }
                        }
                    return currentValue()
                }
                """,
            )

        assertEquals(listOf(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE), messages)
    }
}
