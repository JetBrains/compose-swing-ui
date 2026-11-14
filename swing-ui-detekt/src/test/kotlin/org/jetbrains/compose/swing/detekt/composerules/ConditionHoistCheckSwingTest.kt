// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Finding
import dev.detekt.test.TestConfig
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.composerules.ConditionHoist
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConditionHoistCheckSwingTest {
    private val rule =
        ConditionHoist.toDetektRule(
            TestConfig("contentEmitters" to listOf("StringScoped", "GenericScoped")),
        )

    @Test
    fun `does not check the body of a property setter`() {
        val findings =
            lint(
                """
                var flag: Boolean = false

                var stored: @Composable () -> Unit = {}

                var panel: Int
                    get() = 0
                    set(value) {
                        stored = {
                            Column {
                                if (flag) {
                                    Text("a")
                                }
                            }
                        }
                    }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `ignores an abstract composable member of an interface`() {
        val findings =
            lint(
                """
                interface Screen {
                    @Composable
                    fun Render()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty())
    }

    @ParameterizedTest(name = "layout content: {0}")
    @ValueSource(
        strings = [
            "if (flag) { listOf(\"a\") }",
            "if (flag) { SideEffect {} }",
            "if (flag) { remember { \"a\" } }",
            "if (flag) { Text(\"a\") } else { SideEffect {} }",
            "if (flag) { Text(\"a\") } else { val computed = listOf(\"work\") }",
            "if (flag) { Text(\"a\") }; if (!flag) { Text(\"b\") }",
            "Text(\"a\")",
            "if (flag) { @Composable fun Inner() { Text(\"a\") } }",
            "if (flag) { class Inner { @Composable fun Render() { Text(\"a\") } } }",
        ],
    )
    fun `does not report layout content that is not a single conditional composable child`(content: String) {
        val findings = lint(contentFunction(content))

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports conditional whose branch calls a non composable function before the composable child`() {
        val findings = lint(contentFunction("if (flag) { listOf(\"a\"); Text(\"a\") }"))

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports layout whose content lambda is passed as a named argument`() {
        val findings =
            lint(
                """
                @Composable
                fun Content(flag: Boolean) {
                    Column(content = { if (flag) { Text("a") } })
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @ParameterizedTest(name = "condition: {0}")
    @ValueSource(strings = ["length > 0", "toInt() > 0", "this@StringScoped.isNotEmpty()"])
    fun `does not report conditional that uses a library receiver of the layout scope`(condition: String) {
        val findings =
            lint(
                """
                @Composable
                fun Content() {
                    StringScoped {
                        if ($condition) {
                            Text("a")
                        }
                    }
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty())
    }

    @ParameterizedTest(name = "condition: {0}")
    @ValueSource(strings = ["isReady()", "ready", "Flags.on", "other.length > 0"])
    fun `reports scoped layout conditional that only uses declarations unrelated to the receiver`(condition: String) {
        val findings =
            lint(
                """
                fun isReady(): Boolean = true

                val ready: Boolean = true
                val other: String = ""

                object Flags {
                    val on: Boolean = true
                }

                @Composable
                fun Content() {
                    StringScoped {
                        if ($condition) {
                            Text("a")
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports scoped layout conditional when the receiver is a type parameter`() {
        val findings =
            lint(
                """
                @Composable
                fun <T> Content(flag: Boolean) {
                    GenericScoped<T> {
                        if (flag) {
                            Text("a")
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports conditional of a composable member of a local class only once`() {
        val findings =
            lint(
                """
                @Composable
                fun Content(flag: Boolean) {
                    class Inner {
                        @Composable
                        fun Render() {
                            Column {
                                if (flag) {
                                    Text("a")
                                }
                            }
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    private fun contentFunction(content: String): String =
        """
        @Composable
        fun Content(flag: Boolean) {
            Column {
                $content
            }
        }
        """.trimIndent()

    private fun lint(body: String): List<Finding> =
        rule.lintWithAnalysisApi(
            """
            package com.example

            import org.jetbrains.compose.swing.foundation.layout.Column
            import com.example.compose.fake.Composable
            import com.example.compose.fake.GenericScoped
            import com.example.compose.fake.StringScoped
            import com.example.compose.fake.SideEffect
            import com.example.compose.fake.remember
            import org.jetbrains.compose.swing.components.Label as Text

            $body
            """.trimIndent(),
            codeWithFakeCompose(
                """
                @Composable
                fun StringScoped(content: @Composable String.() -> Unit) {
                    "".content()
                }

                @Composable
                fun <T> GenericScoped(content: @Composable T.() -> Unit) {
                }
                """.trimIndent(),
            ),
            conditionHoistFoundationLayout,
            conditionHoistLabel,
        )
}

internal val conditionHoistFoundationLayout =
    """
    package org.jetbrains.compose.swing.foundation.layout

    import com.example.compose.fake.Composable

    object Modifier
    interface ColumnScope

    @Composable
    inline fun Column(
        modifier: Modifier = Modifier,
        crossinline content: @Composable ColumnScope.() -> Unit,
    ) {
    }
    """.trimIndent()

internal val conditionHoistLabel =
    """
    package org.jetbrains.compose.swing.components

    import com.example.compose.fake.Composable

    @Composable
    fun Label(text: String) {
    }
    """.trimIndent()
