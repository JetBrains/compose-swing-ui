// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.VarsWithoutStateBacking
import org.jetbrains.compose.swing.rules.runtime.VarsWithoutStateBackingCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VarsWithoutStateBackingCheckSwingTest {
    private val rule = VarsWithoutStateBacking.toDetektRule(Config.empty)

    @Test
    fun `ignores a local var declared by a plain function inside a composable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example() {
                fun helper() {
                    var count = 0
                    count.toString()
                }
                helper()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports bare local vars in composable lambdas returned by non-composable getters`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Holder {
                val content: @Composable () -> Unit
                    get() = @Composable {
                        var count = 0
                        count.toString()
                    }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(7, 21),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @Test
    fun `reports bare local vars in composable lambdas declared inside setters`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Holder {
                var content: Int = 0
                    set(value) {
                        val body: @Composable () -> Unit = {
                            var count = 0
                            count.toString()
                        }
                        field = value
                    }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "MutableIntState",
            "MutableLongState",
            "MutableFloatState",
            "MutableDoubleState",
        ],
    )
    fun `does not report local vars delegated to primitive mutable compose state`(stateType: String) {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(state: $stateType) {
                var current by state
                current.toString()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report local vars delegated to a subtype of compose state`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class CustomState : MutableState<Int> {
                override var value: Int = 0
            }

            @Composable
            fun Example(state: CustomState) {
                var current by state
                current.toString()
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "run(({ var count = 0; count.toString() }))",
            "run(label@{ var count = 0; count.toString() })",
            "run(@Marker { var count = 0; count.toString() })",
            "run(block = { var count = 0; count.toString() })",
        ],
    )
    fun `reports bare local vars in stdlib scope lambdas passed as wrapped arguments`(call: String) {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            annotation class Marker

            @Composable
            fun Example() {
                $call
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(VarsWithoutStateBackingCheck.VARS_WITHOUT_STATE_BACKING_MESSAGE, findings.single().message)
    }
}
