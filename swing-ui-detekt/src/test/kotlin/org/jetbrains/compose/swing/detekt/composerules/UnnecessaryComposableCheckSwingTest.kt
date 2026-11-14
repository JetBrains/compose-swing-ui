// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.SourceLocation
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.composerules.UnnecessaryComposable
import org.jetbrains.compose.swing.rules.composerules.UnnecessaryComposableCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnnecessaryComposableCheckSwingTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource(
        "org.jetbrains.compose.swing.detekt.composerules.UnnecessaryComposableCheckSwingTest\$ReportedCases#reported",
    )
    fun `reports a composable declaration whose body only looks like composition use`(
        label: String,
        code: String,
    ) {
        val findings = lint(code)

        assertEquals(1, findings.size, label)
        assertEquals(UnnecessaryComposableCheck.UNNECESSARY_COMPOSABLE_MESSAGE, findings.single().message, label)
    }

    @ParameterizedTest(name = "accepts {0}")
    @MethodSource(
        "org.jetbrains.compose.swing.detekt.composerules.UnnecessaryComposableCheckSwingTest\$AcceptedCases#accepted",
    )
    fun `does not report a declaration the rule must accept`(
        label: String,
        code: String,
    ) {
        assertTrue(lint(code).isEmpty(), label)
    }

    @Test
    fun `reports the composable getter of a class that is not open`() {
        val findings =
            lint(
                """
                class Holder {
                    val value: Int
                        @Composable
                        get() = 42
                }
                """,
            )

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(5, 9),
            findings
                .single()
                .entity.location.source,
        )
    }

    @Test
    fun `reports a composable function whose default parameter value does not use composition`() {
        val findings =
            lint(
                """
                @Composable
                fun Example(count: Int = 1): Int = 42
                """,
            )

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(3, 5),
            findings
                .single()
                .entity.location.source,
        )
    }

    @Test
    fun `reports a composable function that calls an unresolved function`() {
        val findings =
            lint(
                """
                @Composable
                fun Example(): Int = missing()
                """,
                allowCompilationErrors = true,
            )

        assertEquals(1, findings.size)
        assertEquals(UnnecessaryComposableCheck.UNNECESSARY_COMPOSABLE_MESSAGE, findings.single().message)
    }

    @Test
    fun `accepts an unfinished composable function that lacks a body`() {
        val findings =
            lint(
                """
                @Composable
                fun Example(): Int
                """,
                allowCompilationErrors = true,
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports a composable whose only composition use sits in a local helper`() {
        val findings =
            lint(
                """
                @Composable
                @ReadOnlyComposable
                fun Content(): Int = 1

                @Composable
                fun Example(): Int {
                    @Composable
                    fun helper(): Int = Content()
                    return 42
                }
                """,
            )

        assertEquals(1, findings.size)
        assertEquals(UnnecessaryComposableCheck.UNNECESSARY_COMPOSABLE_MESSAGE, findings.single().message)
        assertEquals(
            SourceLocation(7, 1),
            findings
                .single()
                .entity.location.source,
        )
    }

    @Test
    fun `reports a composable whose only composition use sits in an object expression accessor`() {
        val findings =
            lint(
                """
                @Composable
                @ReadOnlyComposable
                fun Content(): Int = 1

                @Composable
                fun Example(): Any {
                    return object {
                        val value: Int
                            @Composable
                            get() = Content()
                    }
                }
                """,
            )

        assertEquals(1, findings.size)
        assertEquals(UnnecessaryComposableCheck.UNNECESSARY_COMPOSABLE_MESSAGE, findings.single().message)
        assertEquals(
            SourceLocation(7, 1),
            findings
                .single()
                .entity.location.source,
        )
    }

    private fun lint(
        code: String,
        allowCompilationErrors: Boolean = false,
    ): List<Finding> =
        UnnecessaryComposable
            .toDetektRule(Config.empty)
            .lintWithAnalysisApi(
                codeWithFakeCompose(code.trimIndent()),
                allowCompilationErrors = allowCompilationErrors,
            )

    object ReportedCases {
        @JvmStatic
        fun reported(): List<Arguments> = reportedSelectors() + reportedReads()

        private fun reportedSelectors(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a safe call on a value that is not a state",
                    """
                    @Composable
                    fun Example(text: String?): Int? = text?.length
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a value selector on a receiver that is not a state",
                    """
                    class Box(val value: Int)

                    @Composable
                    fun Example(box: Box): Int = box.value
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a current selector on a receiver that is not a composition local",
                    """
                    class Box(val current: Int)

                    @Composable
                    fun Example(box: Box): Int = box.current
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a selector that is a call",
                    """
                    @Composable
                    fun Example(text: String): Int = text.length.toString().length
                    """.trimIndent(),
                ),
            )

        private fun reportedReads(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a read of a delegated property whose delegate is not a state",
                    """
                    @Composable
                    fun Example(): Int {
                        val count by lazy { 1 }
                        return count
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a read of a local property without a delegate",
                    """
                    @Composable
                    fun Example(): Int {
                        val count = 1
                        return count
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a read of a property of a class",
                    """
                    class Box(val size: Int)

                    @Composable
                    fun Example(box: Box): Int = box.size
                    """.trimIndent(),
                ),
            )
    }

    object AcceptedCases {
        @JvmStatic
        fun accepted(): List<Arguments> = acceptedContracts() + acceptedReads()

        private fun acceptedContracts(): List<Arguments> = acceptedGetters() + acceptedAccessorShapes()

        private fun acceptedGetters(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a function that is not composable",
                    """
                    fun Example(): Int = 42
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a getter that is not composable",
                    """
                    val value: Int
                        get() = 42
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a read only composable getter",
                    """
                    val value: Int
                        @Composable
                        @ReadOnlyComposable
                        get() = 42
                    """.trimIndent(),
                ),
            )

        private fun acceptedAccessorShapes(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a setter",
                    """
                    class Holder {
                        var value: Int = 0
                            set(newValue) {
                                field = newValue
                            }
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a getter declared in an interface",
                    """
                    interface Holder {
                        val value: Int
                            @Composable
                            get() = 42
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a getter of an open property",
                    """
                    open class Holder {
                        open val value: Int
                            @Composable
                            get() = 42
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a getter without a body",
                    """
                    class Holder {
                        val value: Int = 42
                            @Composable
                            get
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a getter with an empty block",
                    """
                    class Holder {
                        val value: Unit
                            @Composable
                            get() {}
                    }
                    """.trimIndent(),
                ),
            )

        private fun acceptedReads(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a current read on a receiver typed as the composition local itself",
                    """
                    @Composable
                    fun Example(local: CompositionLocal<Int>): Int = local.current
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a state read assigned to another variable",
                    """
                    @Composable
                    fun Example(state: State<Int>) {
                        var copy = 0
                        copy = state.value
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a delegated read of a class that implements the state interface",
                    """
                    class Counter : State<Int> {
                        override val value: Int = 1
                    }

                    @Composable
                    fun Example(): Int {
                        val count by Counter()
                        return count
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a composable call inside a lambda passed as a plain argument to a scope function",
                    """
                    @Composable
                    @ReadOnlyComposable
                    fun Content() = Unit

                    @Composable
                    fun Example(): Unit = 1.let({ Content() })
                    """.trimIndent(),
                ),
            )
    }
}
