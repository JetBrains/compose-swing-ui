// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Assertions use kotlin.test in place of AssertJ.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.MissingReadOnlyComposable
import org.jetbrains.compose.swing.rules.runtime.MissingReadOnlyComposableCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.
@Suppress("LargeClass")
class MissingReadOnlyComposableCheckTest {
    private val rule = MissingReadOnlyComposable.toDetektRule(Config.empty)

    @Test
    fun `reports composable function that only calls read only composables`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun currentValue(): Int = 42

            @Composable
            fun Example(): Int = currentValue()
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 9),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports composable getter that only calls read only composables`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun currentValue(): Int = 42

            val value: Int
                @Composable
                get() = currentValue()
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(9, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports composable function that reads read only composable property`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val currentValue: Int
                @ReadOnlyComposable
                @Composable
                get() = 42

            @Composable
            fun Example(): Int = currentValue
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(9, 9),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports composable function that reads chained read only composable property`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val currentValue: Pair<Int, Int>
                @ReadOnlyComposable
                @Composable
                get() = 1 to 2

            @Composable
            fun Example(): Int = currentValue.first
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(9, 9),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report composable function that reads chained non read only composable property`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val currentValue: Pair<Int, Int>
                @Composable
                get() = 1 to 2

            @Composable
            fun Example(): Int = currentValue.first
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports composable function that only reads composition local current`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCount = compositionLocalOf { 0 }

            @Composable
            fun Example(): Int = LocalCount.current
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(6, 9),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report composition local current read with ordinary receiver call`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            fun localWithSideEffects(): CompositionLocal<Int> = compositionLocalOf { 0 }

            @Composable
            fun Example(): Int = localWithSideEffects().current
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composition local current read with ordinary operator receiver call`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Locals {
                operator fun get(index: Int): CompositionLocal<Int> = compositionLocalOf { index }
            }

            @Composable
            fun Example(locals: Locals): Int = locals[0].current
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function without read only usage`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(): Int = 42
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function that calls non read only composable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @Composable
            fun Example() {
                EmitsContent()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function that calls composable lambda parameter`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(content: @Composable () -> Unit) {
                content()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function with read only usage and ordinary call`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCount = compositionLocalOf { 0 }

            fun trackMetric() {
            }

            @Composable
            fun Example(): Int {
                trackMetric()
                return LocalCount.current
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function with read only usage and ordinary custom getter read`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCount = compositionLocalOf { 0 }

            val sideEffectingValue: Int
                get() = 1

            @Composable
            fun Example(): Int = LocalCount.current + sideEffectingValue
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function with read only usage and delegated property read`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCount = compositionLocalOf { 0 }

            val token by lazy { 1 }

            @Composable
            fun Example(): Int = LocalCount.current + token
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function with read only usage and custom operator call`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCounter = compositionLocalOf { Counter(0) }

            class Counter(val value: Int) {
                operator fun plus(other: Counter): Counter = Counter(value + other.value)
            }

            @Composable
            fun Example(other: Counter): Counter = LocalCounter.current + other
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function with read only usage and mutation`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCount = compositionLocalOf { 0 }

            var count = 0

            @Composable
            fun Example(): Int {
                count += 1
                return LocalCount.current
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report already read only composable function`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun Example(): Int = 42
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report override composable function`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            abstract class Base {
                @Composable
                abstract fun currentValue(): Int
            }

            class Child : Base() {
                @Composable
                override fun currentValue(): Int = 42
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report override composable getter`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val LocalCount = compositionLocalOf { 0 }

            abstract class Base {
                abstract val currentValue: Int
                    @Composable get
            }

            class Child : Base() {
                override val currentValue: Int
                    @Composable get() = LocalCount.current
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable getter that calls non read only composable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            val value: Unit
                @Composable
                get() = EmitsContent()
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports read only usage inside eager stdlib lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun currentValue(): Int = 42

            @Composable
            fun Example(): Int = run {
                currentValue()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 9),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports read only usage inside labeled eager stdlib lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun currentValue(): Int = 42

            @Composable
            fun Example(): Int = run label@{
                currentValue()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 9),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report read only usage inside deferred lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun currentValue(): Int = 42

            @Composable
            fun Example(): () -> Int = {
                currentValue()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report deferred lambda returned from eager stdlib lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun currentValue(): Int = 42

            @Composable
            fun Example(): () -> Int = run {
                {
                    currentValue()
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports read only usage with library call on the result`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(): String = stringResource(1).uppercase()
            """,
            )

        assertSingleFinding(rule.lintWithAnalysisApi(code), SourceLocation(10, 9))
    }

    @Test
    fun `reports read only usage inside let with library call on the result`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(value: String?): String = value?.let { stringResource(1) }.orEmpty()
            """,
            )

        assertSingleFinding(rule.lintWithAnalysisApi(code), SourceLocation(10, 9))
    }

    @Test
    fun `reports read only usage with elvis operator`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(value: String?): String = value ?: stringResource(1)
            """,
            )

        assertSingleFinding(rule.lintWithAnalysisApi(code), SourceLocation(10, 9))
    }

    @Test
    fun `reports read only usage with library binary operators`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(count: Int, other: String?): String =
                if (count > 0 && other != null) stringResource(1) + count else stringResource(2)
            """,
            )

        assertSingleFinding(rule.lintWithAnalysisApi(code), SourceLocation(10, 9))
    }

    @Test
    fun `reports read only usage with data class equality`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            data class Token(val id: Int)

            @Composable
            fun Example(token: Token, other: Token): String =
                if (token == other) stringResource(1) else stringResource(2)
            """,
            )

        assertSingleFinding(rule.lintWithAnalysisApi(code), SourceLocation(12, 9))
    }

    @Test
    fun `reports read only usage with null and identity checks on type with custom equals`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            class Token(val id: Int) {
                override fun equals(other: Any?): Boolean = other is Token && other.id == id

                override fun hashCode(): Int = id
            }

            @Composable
            fun Example(token: Token?, other: Token): String =
                if (token != null && token !== other) stringResource(1) else stringResource(2)
            """,
            )

        assertSingleFinding(rule.lintWithAnalysisApi(code), SourceLocation(16, 9))
    }

    @Test
    fun `does not report read only usage with custom equals`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            class Token(val id: Int) {
                override fun equals(other: Any?): Boolean = other is Token && other.id == id

                override fun hashCode(): Int = id
            }

            @Composable
            fun Example(token: Token, other: Token): String =
                if (token == other) stringResource(1) else stringResource(2)
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    @Test
    fun `does not report read only usage with non read only composable call`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Text(text: String) {
            }

            @Composable
            fun Example() {
                Text(stringResource(1))
            }
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    @Test
    fun `does not report read only usage with remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(): String {
                val prefix = remember { "prefix" }
                return prefix + stringResource(1)
            }
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    @Test
    fun `does not report read only usage inside non inline library lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(): Pair<String, Lazy<Int>> = stringResource(1) to lazy { 1 }
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    @Test
    fun `does not report read only usage with function type invocation`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(onRead: () -> Unit): String {
                onRead()
                return stringResource(1)
            }
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    @Test
    fun `does not report read only usage with local var mutation`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            @Composable
            fun Example(): String {
                var text = stringResource(1)
                text = text.trim()
                return text
            }
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    @Test
    fun `does not report read only usage with non read only composable operator`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            $STRING_RESOURCE

            class Label(val text: String)

            @Composable
            operator fun Label.plus(other: String): Label = Label(text + other)

            @Composable
            fun Example(label: Label): Label = label + stringResource(1)
            """,
            )

        assertTrue(rule.lintWithAnalysisApi(code).isEmpty())
    }

    private fun assertSingleFinding(
        findings: List<Finding>,
        location: SourceLocation,
    ) {
        assertEquals(1, findings.size)
        assertEquals(
            location,
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    private companion object {
        const val STRING_RESOURCE = """
            @ReadOnlyComposable
            @Composable
            fun stringResource(id: Int, vararg formatArgs: Any): String = id.toString()
        """
    }
}
