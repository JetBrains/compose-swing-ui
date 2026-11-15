// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.UnnecessaryLaunchedEffect
import org.jetbrains.compose.swing.rules.runtime.UnnecessaryLaunchedEffectCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnnecessaryLaunchedEffectCheckSwingTest {
    private val rule = UnnecessaryLaunchedEffect.toDetektRule(Config.empty)

    @Test
    fun `allows a block passed as a non-lambda argument`() {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                import kotlinx.coroutines.CoroutineScope

                @Composable
                fun Example(block: suspend CoroutineScope.() -> Unit) {
                    LaunchedEffect(Unit, block)
                }
                """,
                ),
            )

        assertTrue(findings.isEmpty())
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "LaunchedEffect(Unit, ({ consume() }))",
            "LaunchedEffect(Unit, block@{ consume() })",
        ],
    )
    fun `reports effect lambdas wrapped in parentheses or labels`(call: String) {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                fun consume() = Unit

                @Composable
                fun Example() {
                    $call
                }
                """,
                ),
            )

        assertEquals(1, findings.size)
        assertEquals(UnnecessaryLaunchedEffectCheck.UnnecessaryLaunchedEffect, findings.single().message)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "first == true && second",
            "first == true || second",
            "first ?: second",
            "first !== second",
        ],
    )
    fun `reports effects that only evaluate operators that are not function calls`(expression: String) {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                @Composable
                fun Example(first: Boolean?, second: Boolean) {
                    LaunchedEffect(Unit) {
                        val result = $expression
                    }
                }
                """,
                ),
            )

        assertEquals(1, findings.size)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "val first = items[0]; consume(first)",
            "val negated = -count; consume(negated)",
            "for (item in items) { consume(item) }",
            "val (first, second) = pair; consume(first); consume(second)",
            "val lazyValue by lazy { count }; consume(lazyValue)",
        ],
    )
    fun `reports effects whose convention calls are not suspending`(statements: String) {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                fun consume(value: Int) = Unit

                @Composable
                fun Example(items: List<Int>, count: Int, pair: Pair<Int, Int>) {
                    LaunchedEffect(Unit) {
                        $statements
                    }
                }
                """,
                ),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports invoking a function type value that is not suspending`() {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                @Composable
                fun Example(callback: () -> Unit) {
                    LaunchedEffect(Unit) {
                        callback()
                    }
                }
                """,
                ),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports constructor calls and calls that match no configured name or receiver type`() {
        val configuredRule =
            UnnecessaryLaunchedEffect.toDetektRule(
                TestConfig(
                    "allowedCallNames" to listOf("com.example.compose.fake.keep"),
                    "allowedCallReceiverTypes" to listOf("com.example.compose.fake.Keeper"),
                ),
            )
        val findings =
            configuredRule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                class Plain
                class Keeper { fun act() = Unit }
                class Other { fun act() = Unit }

                fun skip() = Unit
                fun keep() = Unit

                @Composable
                fun Example(other: Other) {
                    LaunchedEffect(Unit) { Plain() }
                    LaunchedEffect(Unit) { skip() }
                    LaunchedEffect(Unit) { other.act() }
                    LaunchedEffect(Unit) { keep() }
                }
                """,
                ),
            )

        assertEquals(3, findings.size)
    }

    @Test
    fun `reports local extension functions whose receiver is not a CoroutineScope`() {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                fun consume(value: Int) = Unit

                @Composable
                fun Example() {
                    LaunchedEffect(Unit) {
                        fun Int.twice(): Int = this * 2
                        consume(2.twice())
                    }
                }
                """,
                ),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports callable references to suspend functions that are never invoked`() {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                suspend fun load() = Unit

                @Composable
                fun Example() {
                    LaunchedEffect(Unit) {
                        val reference = ::load
                    }
                }
                """,
                ),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `allows a configured call in a local function invoked through a reassigned function value alias`() {
        val configuredRule =
            UnnecessaryLaunchedEffect.toDetektRule(
                TestConfig("allowedCallNames" to listOf("com.example.compose.fake.keep")),
            )
        val findings =
            configuredRule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                fun keep() = Unit

                @Composable
                fun Example() {
                    LaunchedEffect(Unit) {
                        fun helper() {
                            keep()
                        }
                        val a = ::helper
                        var b: () -> Unit = {}
                        b = a
                        b()
                    }
                }
                """,
                ),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `allows a for loop in an unused local function whose iterator takes a CoroutineScope context argument`() {
        val findings =
            rule.lintWithAnalysisApi(
                codeWithFakeCompose(
                    """
                import kotlinx.coroutines.CoroutineScope

                class Source

                context(scope: CoroutineScope)
                operator fun Source.iterator(): Iterator<Int> = emptyList<Int>().iterator()

                @Composable
                fun Example(scope: CoroutineScope) {
                    LaunchedEffect(Unit) {
                        fun drain() {
                            with(scope) {
                                for (item in Source()) {
                                }
                            }
                        }
                    }
                }
                """,
                ),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports only the effect without a suspend call at its own line`() {
        val code =
            codeWithFakeCompose(
                """
                fun consume() = Unit
                suspend fun load() = Unit

                @Composable
                fun Example() {
                    LaunchedEffect(Unit) { load() }
                    LaunchedEffect(Unit) { consume() }
                }
                """,
            )
        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        val expectedLine = code.lines().indexOfLast { line -> line.contains("consume() }") } + 1
        assertEquals(
            expectedLine,
            findings
                .single()
                .entity.location.source.line,
        )
    }
}
