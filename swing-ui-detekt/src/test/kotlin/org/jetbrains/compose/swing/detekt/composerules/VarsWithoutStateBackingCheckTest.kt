// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Assertions use kotlin.test in place of AssertJ.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.VarsWithoutStateBacking
import org.jetbrains.compose.swing.rules.runtime.VarsWithoutStateBackingCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VarsWithoutStateBackingCheckTest {
    private val rule = VarsWithoutStateBacking.toDetektRule(Config.empty)

    @Test
    fun `reports bare local vars in composable functions`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example() {
                var count = 0
                if (count == 0) {
                    var label = "empty"
                    println(label)
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(2, findings.size)
        assertEquals(SourceLocation(6, 13), findings[0].entity.location.source)
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings[0].message)
        assertEquals(SourceLocation(8, 17), findings[1].entity.location.source)
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings[1].message)
    }

    @Test
    fun `reports bare local vars in composable lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Row(content: @Composable () -> Unit) {
                content()
            }

            @Composable
            fun Example() {
                Row {
                    var count = 0
                    println(count)
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
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports bare local vars in top-level composable lambda properties`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val Content: @Composable () -> Unit = {
                var count = 0
                println(count)
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(5, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports bare local vars in eager lambdas inside composable functions`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example() {
                run {
                    var count = 0
                    println(count)
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(7, 17),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports bare local vars in composable getters`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            val value: Int
                @Composable
                get() {
                    var count = 0
                    return count
                }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(7, 17),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report local vars in non-composable lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Button(onClick: () -> Unit, content: @Composable () -> Unit) {
                content()
            }

            @Composable
            fun Example() {
                Button(
                    onClick = {
                        var count = 0
                        println(count)
                    },
                ) {
                    println("content")
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report local vars in stdlib builder lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun BuildStringExample(): String = buildString {
                var first = true
                append(first)
            }

            @Composable
            fun BuildListExample(): List<String> = buildList {
                var item = "value"
                add(item)
            }

            @Composable
            fun BuildSetExample(): Set<String> = buildSet {
                var item = "value"
                add(item)
            }

            @Composable
            fun BuildMapExample(): Map<String, String> = buildMap {
                var key = "key"
                put(key, "value")
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report local vars in non-composable getters`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example() {
                val value: Int
                    get() {
                        var count = 0
                        return count
                    }
                println(value)
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report member vars in anonymous objects`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example() {
                val holder = object {
                    var count = 0
                }
                println(holder.count)
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report local vars in non-composable inline composable lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            inline fun <T> inlineRemember(calculation: () -> T): T = calculation()

            @Composable
            fun Example() {
                val values = inlineRemember {
                    var index = 0
                    List(3) { index++ }
                }
                println(values)
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports local vars in composable lambdas nested in skipped lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            inline fun <T> inlineRemember(calculation: () -> T): T = calculation()

            @Composable
            fun Example() {
                val content = inlineRemember {
                    @Composable {
                        var count = 0
                        println(count)
                    }
                }
                content()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(11, 21),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports non-compose delegated local vars`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            import kotlin.reflect.KProperty

            class NonComposeDelegate {
                operator fun getValue(thisRef: Any?, property: KProperty<*>): Int = 0
                operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) = Unit
            }

            @Composable
            fun Example() {
                var count by NonComposeDelegate()
                count = 1
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(13, 13),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `does not report compose state delegated local vars`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(state: MutableState<Int>) {
                var count by state
                count = 1
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report local vars outside composable functions`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            fun Example() {
                var count = 0
                if (count == 0) {
                    var label = "empty"
                    println(label)
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }
}
