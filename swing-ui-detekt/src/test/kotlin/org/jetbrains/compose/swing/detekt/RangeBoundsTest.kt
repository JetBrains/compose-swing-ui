// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RangeBoundsTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun lint(content: String) = subject.lintWithContext(environment, content)

    @Test
    fun `reports a FloatRange declaration excluding its only possible value`() {
        val findings =
            lint(
                """
                package sample
                import androidx.annotation.FloatRange
                fun setAlpha(@FloatRange(from = 1.0, to = 1.0, toInclusive = false) alpha: Double) = Unit
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("holds no value"))
    }

    @Test
    fun `states only the bound the annotation declares`() {
        val openLower =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(to = 10) level: Int) = Unit
                fun caller() = setLevel(11)
                """.trimIndent(),
            )
        val openUpper =
            lint(
                """
                package sample
                import androidx.annotation.FloatRange
                fun setRatio(@FloatRange(from = 0.0, fromInclusive = false) ratio: Double) = Unit
                fun caller() = setRatio(0.0)
                """.trimIndent(),
            )

        assertEquals(
            "Value 11 is outside the declared range, which requires \u2264 10. Pass a value within the range.",
            openLower.single().message,
        )
        assertEquals(
            "Value 0.0 is outside the declared range, which requires > 0.0. Pass a value within the range.",
            openUpper.single().message,
        )
    }

    @Test
    fun `reads bounds given out of declaration order by name`() {
        val findings =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(to = 10, from = 0) level: Int) = Unit
                fun caller() = setLevel(-1)
                """.trimIndent(),
            )

        assertEquals(
            "Value -1 is outside the declared range, which requires \u2265 0 and \u2264 10. " +
                "Pass a value within the range.",
            findings.single().message,
        )
    }

    @Test
    fun `reports an equal float range with an exclusive lower bound`() {
        val findings =
            lint(
                """
                import androidx.annotation.FloatRange
                fun setAlpha(@FloatRange(from = 1.0, to = 1.0, fromInclusive = false) alpha: Double) = Unit
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `compares large integer bounds without rounding`() {
        val findings =
            lint(
                """
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(from = 9_007_199_254_740_993, to = 9_007_199_254_740_992) level: Long) = Unit
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports bounds and exclusivity supplied by constants`() {
        val findings =
            lint(
                """
                import androidx.annotation.IntRange
                import androidx.annotation.FloatRange
                const val LOWER = 11L
                const val UPPER = 10L
                const val EXCLUSIVE = false
                fun setLevel(@IntRange(from = LOWER, to = UPPER) level: Int) = Unit
                fun setAlpha(@FloatRange(from = 1.0, to = 1.0, toInclusive = EXCLUSIVE) alpha: Double) = Unit
                fun validLevel(@IntRange(from = UPPER, to = LOWER) level: Int) = Unit
                fun validAlpha(@FloatRange(from = 0.0, to = 1.0, fromInclusive = EXCLUSIVE) alpha: Double) = Unit
                """.trimIndent(),
            )

        assertEquals(2, findings.size)
        assertTrue(findings.all { it.message.contains("holds no value") })
    }

    @Test
    fun `reports an impossible range using an annotation import alias`() {
        val findings =
            lint(
                """
                import androidx.annotation.IntRange as AllowedRange
                fun setLevel(@AllowedRange(from = 10, to = 0) level: Int) = Unit
                """.trimIndent(),
            )

        assertTrue(findings.single().message.contains("holds no value"))
    }

    @Test
    fun `reports an impossible range using a typealias of the range annotation`() {
        val findings =
            lint(
                """
                import androidx.annotation.IntRange
                typealias AllowedRange = IntRange
                fun setLevel(@AllowedRange(from = 10, to = 0) level: Int) = Unit
                """.trimIndent(),
            )

        assertTrue(findings.single().message.contains("holds no value"))
    }
}
