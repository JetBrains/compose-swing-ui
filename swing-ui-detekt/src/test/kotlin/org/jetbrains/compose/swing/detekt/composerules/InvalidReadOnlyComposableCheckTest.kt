// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Assertions use kotlin.test in place of AssertJ.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.composerules.InvalidReadOnlyComposable
import org.jetbrains.compose.swing.rules.composerules.InvalidReadOnlyComposableCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvalidReadOnlyComposableCheckTest {
    private val rule = InvalidReadOnlyComposable.toDetektRule(Config.empty)

    @Test
    fun `reports read only composable function that calls non read only composable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example(): Int {
                EmitsContent()
                return 42
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(11, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports read only composable function that reads non read only composable property`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val emitsContent: Unit
                @Composable
                get() {
                }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                emitsContent
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(12, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports read only composable function that reads qualified non read only composable property`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            object Content {
                val emitsContent: Unit
                    @Composable
                    get() {
                    }
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                Content.emitsContent
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(14, 21),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report read only composable function that reads read only composable property`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val currentValue: Int
                @ReadOnlyComposable
                @Composable
                get() = 42

            @ReadOnlyComposable
            @Composable
            fun Example(): Int = currentValue
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports read only composable function that calls composable lambda parameter`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun Example(content: @Composable () -> Unit) {
                content()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(7, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report read only composable function that only calls read only composables`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @ReadOnlyComposable
            @Composable
            fun localContext(): String = "context"

            @ReadOnlyComposable
            @Composable
            fun Example(): String = localContext()
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report composable function without read only annotation`() {
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
    fun `does not report read only composable function without composable calls`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            fun currentValue(): Int = 42

            @ReadOnlyComposable
            @Composable
            fun Example(): Int = currentValue()
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report non read only composable call inside deferred lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example(): () -> Unit = {
                EmitsContent()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports non read only composable call inside eager stdlib scope lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example(): Unit = run {
                EmitsContent()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(11, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports non read only composable call inside labeled eager stdlib scope lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example(): Unit = run label@{
                EmitsContent()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(11, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports non read only composable call inside repeat lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                repeat(1) {
                    EmitsContent()
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(12, 17),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports non read only composable call inside collection forEach lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example(items: List<Int>) {
                items.forEach {
                    EmitsContent()
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(12, 17),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report non read only composable call inside custom deferred run lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            class DeferredRunner {
                fun run(block: () -> Unit) = block
            }

            @ReadOnlyComposable
            @Composable
            fun Example(runner: DeferredRunner): () -> Unit = runner.run {
                EmitsContent()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report non read only composable call inside custom deferred forEach lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun EmitsContent() {
            }

            class DeferredItems {
                fun forEach(block: () -> Unit) = block
            }

            @ReadOnlyComposable
            @Composable
            fun Example(items: DeferredItems): () -> Unit = items.forEach {
                EmitsContent()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }
}
