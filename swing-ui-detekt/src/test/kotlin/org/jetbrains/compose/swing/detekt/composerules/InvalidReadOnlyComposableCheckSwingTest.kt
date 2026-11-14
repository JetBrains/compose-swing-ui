// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.composerules.InvalidReadOnlyComposable
import org.jetbrains.compose.swing.rules.composerules.InvalidReadOnlyComposableCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals

class InvalidReadOnlyComposableCheckSwingTest {
    private val rule = InvalidReadOnlyComposable.toDetektRule(Config.empty)

    private fun lint(
        @Language("kotlin") code: String,
    ): List<Finding> = rule.lintWithAnalysisApi(codeWithFakeCompose(code))

    private fun assertSingleFindingAt(
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
        assertEquals(InvalidReadOnlyComposableCheck.INVALID_READ_ONLY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports only the first of two non read only composable calls`() {
        val findings =
            lint(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                EmitsContent()
                EmitsContent()
            }
            """,
            )

        assertSingleFindingAt(findings, SourceLocation(11, 13))
    }

    @Test
    fun `reports only the property read when a non read only composable call follows it`() {
        val findings =
            lint(
                """
            val emitsContent: Unit
                @Composable
                get() {
                }

            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                emitsContent
                EmitsContent()
            }
            """,
            )

        assertSingleFindingAt(findings, SourceLocation(16, 13))
    }

    @Test
    fun `reports only the call when a non read only composable property read follows it`() {
        val findings =
            lint(
                """
            val emitsContent: Unit
                @Composable
                get() {
                }

            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                EmitsContent()
                emitsContent
            }
            """,
            )

        assertSingleFindingAt(findings, SourceLocation(16, 13))
    }

    @Test
    fun `reports only the first of two non read only composable property reads`() {
        val findings =
            lint(
                """
            val emitsContent: Unit
                @Composable
                get() {
                }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                emitsContent
                emitsContent
            }
            """,
            )

        assertSingleFindingAt(findings, SourceLocation(12, 13))
    }

    @Test
    fun `ignores a non read only composable call made by a local function`() {
        val findings =
            lint(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                @Composable
                fun nested() {
                    EmitsContent()
                }
            }
            """,
            )

        assertEquals(emptyList(), findings)
    }

    @Test
    fun `ignores a non read only composable call made by an accessor of a nested object`() {
        val findings =
            lint(
                """
            @Composable
            fun EmitsContent() {
            }

            @ReadOnlyComposable
            @Composable
            fun Example() {
                object {
                    val nested: Unit
                        @Composable
                        get() {
                            EmitsContent()
                        }
                }
            }
            """,
            )

        assertEquals(emptyList(), findings)
    }

    @ParameterizedTest(name = "accepts {0}")
    @MethodSource(
        "org.jetbrains.compose.swing.detekt.composerules." +
            "InvalidReadOnlyComposableCheckSwingTest\$AcceptedAccessors#cases",
    )
    fun `does not report an accessor that is not a read only composable getter`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), lint(code), label)
    }

    object AcceptedAccessors {
        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "a setter",
                    """
                    @Composable
                    fun EmitsContent() {
                    }

                    var value: Int = 0
                        get() = field
                        set(newValue) {
                            field = newValue
                        }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a getter that is not composable",
                    """
                    @Composable
                    fun EmitsContent() {
                    }

                    val value: Int
                        @ReadOnlyComposable
                        get() = 42
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a composable getter that is not read only",
                    """
                    @Composable
                    fun currentValue(): Int = 42

                    val value: Int
                        @Composable
                        get() = currentValue()
                    """.trimIndent(),
                ),
            )
    }
}
