// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Finding
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RestrictedApiAccessTest {
    private val env: KotlinEnvironmentContainer by lazy { createEnvironment() }

    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("RestrictedApi"))

    private fun lint(source: String): List<Finding> = subject.lintWithContext(env, source)

    @ParameterizedTest(name = "{0}")
    @MethodSource("accesses")
    fun `reports the accessor an expression actually invokes`(
        label: String,
        statement: String,
        expected: List<String>,
    ) {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.TESTS

                class Holder {
                    @set:RestrictTo(TESTS)
                    var writeOnly: Int = 0

                    @get:RestrictTo(TESTS)
                    var readOnly: Int = 0

                    @get:RestrictTo(TESTS)
                    @set:RestrictTo(TESTS)
                    var both: Int = 0
                }

                class Inner {
                    var value: Int = 0
                }

                @get:RestrictTo(TESTS)
                val restricted: Inner = Inner()

                @get:RestrictTo(TESTS)
                val nullableRestricted: Inner? = null

                fun use(holder: Holder, nullable: Holder?) {
                    $statement
                }
                """.trimIndent(),
            )

        assertEquals(expected, findings.map { it.message }, label)
    }

    @Test
    fun `reports a call from a function restricted to a different scope`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.SUBCLASSES
                import androidx.annotation.RestrictTo.Scope.TESTS

                @RestrictTo(TESTS)
                fun helper() {}

                @RestrictTo(SUBCLASSES)
                fun wrapper() {
                    helper()
                }
                """.trimIndent(),
            )

        assertEquals(listOf("helper can only be called from tests"), findings.map { it.message })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testRestrictedWrappers")
    fun `does not report a test-restricted use inside a declaration restricted to tests`(
        label: String,
        wrapper: String,
    ) {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.SUBCLASSES
                import androidx.annotation.RestrictTo.Scope.TESTS

                @RestrictTo(TESTS)
                fun helper() {}

                $wrapper
                """.trimIndent(),
            )

        assertEquals(emptyList(), findings.map { it.message }, label)
    }

    companion object {
        private fun access(
            label: String,
            statement: String,
            vararg expected: String,
        ): Arguments = Arguments.of(label, statement, expected.toList())

        @JvmStatic
        fun accesses(): List<Arguments> {
            val writeOnly = "Holder.writeOnly can only be accessed from tests"
            val readOnly = "Holder.readOnly can only be accessed from tests"
            val both = "Holder.both can only be accessed from tests"
            val restricted = "restricted can only be accessed from tests"
            return listOf(
                access("assignment to a restricted setter", "holder.writeOnly = 1", writeOnly),
                access("safe-call assignment to a restricted setter", "nullable?.writeOnly = 2", writeOnly),
                access("postfix increment through a restricted setter", "holder.writeOnly++", writeOnly),
                access("prefix decrement through a restricted setter", "--holder.writeOnly", writeOnly),
                access("compound assignment through a restricted setter", "holder.writeOnly += 1", writeOnly),
                access("read of a property whose setter is restricted", "val read = holder.writeOnly"),
                access("assignment to a property whose getter is restricted", "holder.readOnly = 1"),
                access("read of a restricted getter", "val read = holder.readOnly", readOnly),
                access("postfix increment through a restricted getter", "holder.readOnly++", readOnly),
                access("compound assignment through a restricted getter", "holder.readOnly += 1", readOnly),
                access("assignment to a member of a restricted getter's value", "restricted.value = 1", restricted),
                access(
                    "safe-call assignment to a restricted value member",
                    "nullableRestricted?.value = 2",
                    "nullableRestricted can only be accessed from tests",
                ),
                access("increment of a member of a restricted getter's value", "restricted.value++", restricted),
                access("a getter and a setter both restricted, assignment", "holder.both = 1", both),
                access("a getter and a setter both restricted, increment", "holder.both++", both),
            )
        }

        @JvmStatic
        fun testRestrictedWrappers(): List<Arguments> =
            listOf(
                Arguments.of("a function", "@RestrictTo(TESTS)\nfun wrapper() {\n    helper()\n}"),
                Arguments.of(
                    "a member function of an unannotated class",
                    "class Host {\n    @RestrictTo(TESTS)\n    fun wrapper() {\n        helper()\n    }\n}",
                ),
                Arguments.of(
                    "a member of a restricted class",
                    "@RestrictTo(TESTS)\nclass Host {\n    fun wrapper() {\n        helper()\n    }\n}",
                ),
                Arguments.of(
                    "a function whose restriction also names another scope",
                    "@RestrictTo(TESTS, SUBCLASSES)\nfun wrapper() {\n    helper()\n}",
                ),
                Arguments.of("a restricted property initializer", "@RestrictTo(TESTS)\nval wrapped: Unit = helper()"),
            )
    }
}
