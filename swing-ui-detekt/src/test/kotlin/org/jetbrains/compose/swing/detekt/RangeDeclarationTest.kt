// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RangeDeclarationTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun lint(content: String) = subject.lintWithContext(environment, content)

    @Test
    fun `reports a FloatRange violation honoring exclusivity at either bound`() {
        val belowExclusiveLower =
            lint(
                """
                package sample
                import androidx.annotation.FloatRange
                fun setAlpha(@FloatRange(from = 0.0, fromInclusive = false) alpha: Double) = Unit
                fun caller() = setAlpha(0.0)
                """.trimIndent(),
            )
        val aboveInclusiveUpper =
            lint(
                """
                package sample
                import androidx.annotation.FloatRange
                fun setAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Double) = Unit
                fun caller() = setAlpha(1.5)
                """.trimIndent(),
            )

        assertEquals(1, belowExclusiveLower.size)
        assertTrue(belowExclusiveLower.single().message.contains("Value 0.0"))
        assertEquals(1, aboveInclusiveUpper.size)
        assertTrue(aboveInclusiveUpper.single().message.contains("Value 1.5"))
    }

    @Test
    fun `reports an infix argument outside its declared range`() {
        val findings =
            lint(
                """
                import androidx.annotation.IntRange
                infix fun String.at(@IntRange(from = 0) time: Int) = Unit
                fun caller() { "value" at -1; "value" at 1 }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("Value -1"))
    }

    @Test
    fun `does not report an argument that is not a compile-time constant`() {
        val findings =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(from = 0, to = 10) level: Int) = Unit
                fun caller(x: Int) = setLevel(x)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports an IntRange declaration whose from is greater than its to`() {
        val findings =
            lint(
                """
                package sample
                import androidx.annotation.IntRange
                fun setLevel(@IntRange(from = 10, to = 0) level: Int) = Unit
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("holds no value"))
    }

    @Test
    fun `ignores an unrelated annotation with the same name`() {
        val findings =
            lint(
                """
                package sample
                annotation class IntRange(val from: Long, val to: Long)
                fun setLevel(@IntRange(from = 10, to = 0) level: Int) = Unit
                fun caller() = setLevel(-1)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }
}
