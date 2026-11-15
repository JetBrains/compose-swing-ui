// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.jetbrains.compose.swing.rules.androidx.isTestSourcePath
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RangeArgumentTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun lint(content: String) = subject.lintWithContext(environment, content)

    @Test
    fun `reports a positional argument below the declared IntRange`() {
        val findings =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(from = 0, to = 10) level: Int) = Unit
                fun caller() = setLevel(-1)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("Value -1"))
    }

    @Test
    fun `reports a range inherited from an overridden interface parameter`() {
        val findings =
            lint(
                """
                import androidx.annotation.FloatRange
                interface Scope {
                    fun weight(@FloatRange(from = 0.0, fromInclusive = false) value: Float)
                }
                object ScopeInstance : Scope {
                    override fun weight(value: Float) = Unit
                }
                fun caller() = with(ScopeInstance) { weight(-1f); weight(1f) }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("Value -1.0"))
    }

    @Test
    fun `reports a named argument above the declared IntRange`() {
        val findings =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(from = 0, to = 10) level: Int) = Unit
                fun caller() = setLevel(level = 11)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("Value 11"))
    }

    @ParameterizedTest(name = "{0} is a test source: {1}")
    @CsvSource(
        "/project/src/test/kotlin/Sample.kt, true",
        "/project/src/jvmTest/kotlin/Sample.kt, true",
        "/project/src/main/kotlin/Sample.kt, false",
        "/home/src/test/repo/module/src/main/kotlin/Sample.kt, false",
    )
    fun `recognizes a source file by the src directory nearest to it`(
        path: String,
        expected: Boolean,
    ) {
        assertEquals(expected, path.isTestSourcePath(), path)
    }

    @Test
    fun `does not report a bound the annotation leaves open`() {
        val openUpper =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(from = 0) level: Int) = Unit
                fun caller() = setLevel(1_000_000)
                """.trimIndent(),
            )
        val openLower =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(to = 10) level: Int) = Unit
                fun caller() = setLevel(-1_000_000)
                """.trimIndent(),
            )

        assertEquals(0, openUpper.size)
        assertEquals(0, openLower.size)
    }
}
