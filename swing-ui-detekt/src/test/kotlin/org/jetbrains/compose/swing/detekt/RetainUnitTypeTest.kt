/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.compose.swing.detekt

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals

class RetainUnitTypeTest {
    @Test
    fun reportsUnitCalculations() {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.retain.retain

            @Composable
            fun Test() {
                val foo = retain { Unit }
                val bar = retain<Unit> { }
                val baz = retain { noop() }
            }

            fun noop() {}
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RetainUnitType", source)
        assertEquals(3, findings.size)
        findings.forEachIndexed { index, finding ->
            assertEquals(8 + index, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            assertEquals("`retain` calls must not return `Unit`.", finding.message)
        }
    }

    @Test
    fun resolvesAliasesAndIgnoresOtherFunctions() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.retain.retain as keep

            fun retain(block: () -> Unit) = block()

            @Composable
            fun example() {
                retain { }
                keep { check(true) }
                keep { 42 }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RetainUnitType", source)
        assertEquals(1, findings.size)
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals("keep", source.substring(location.start, location.end))
    }

    private fun lintBody(body: String) =
        RuntimeRuleLint.lint(
            "RetainUnitType",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.retain.retain
            @Composable
            fun test() {
                $body
            }
            """.trimIndent(),
        )

    @Test
    fun `a calculation held in a variable that returns Unit is reported`() {
        assertEquals(1, lintBody("val block = { check(true) }\n    retain(calculation = block)").size)
    }

    @Test
    fun `a calculation returning a value is not reported`() {
        assertEquals(0, lintBody("retain { 42 }").size)
        assertEquals(0, lintBody("retain(1) { \"text\" }").size)
    }

    @Test
    fun `a keyed retain returning Unit is reported`() {
        assertEquals(1, lintBody("retain(1) { check(true) }").size)
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("unitCalculations")
    fun `a calculation that returns Unit is reported`(
        ruleId: String,
        qualifiedName: String,
        call: String,
    ) {
        val source =
            """
            import androidx.compose.runtime.Composable
            import $qualifiedName

            fun noop() {}

            @Composable
            fun example() {
                ${qualifiedName.substringAfterLast('.')}$call
            }
            """.trimIndent()
        assertEquals(1, RuntimeRuleLint.lint(ruleId, source).size, "$ruleId$call")
    }

    companion object {
        @JvmStatic
        fun unitCalculations(): List<Arguments> =
            listOf(
                "RememberReturnType" to "androidx.compose.runtime.remember",
                "RetainUnitType" to "androidx.compose.runtime.retain.retain",
            ).flatMap { (ruleId, qualifiedName) ->
                listOf(" { }", "(1) { }", " { noop() }", "(1) { noop() }", " { val x = 1; noop() }")
                    .map { Arguments.of(ruleId, qualifiedName, it) }
            }
    }
}
