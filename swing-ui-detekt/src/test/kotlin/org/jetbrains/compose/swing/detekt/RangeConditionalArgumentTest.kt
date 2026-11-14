// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RangeConditionalArgumentTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun reportedArms(statement: String): List<String> {
        val source =
            """
            package sample
            import androidx.annotation.IntRange
            fun setLevel(@IntRange(from = 0) level: Int) = Unit
            fun caller(flag: Boolean, other: Boolean, n: Int) {
                $statement
            }
            """.trimIndent()
        return subject.lintWithContext(environment, source).map {
            source.substring(it.entity.location.text.start, it.entity.location.text.end)
        }
    }

    @ParameterizedTest(name = "reports {1} in {0}")
    @CsvSource(
        delimiterString = " => ",
        value = [
            "'setLevel(if (flag) -1 else 1)' => '-1'",
            "'setLevel(if (flag) -1 else -2)' => '-1|-2'",
            "'setLevel(if (flag) { -3 } else { 1 })' => '-3'",
            "'setLevel(if (flag) 1 else if (other) -4 else 2)' => '-4'",
            "'setLevel(if (flag) (if (other) -5 else 2) else 3)' => '-5'",
            "'setLevel(when (n) { 0 -> -6; 1 -> 2; else -> 3 })' => '-6'",
            "'setLevel(when { flag -> 1; else -> -7 })' => '-7'",
        ],
    )
    fun `reports the offending arm of a conditional argument`(
        statement: String,
        arms: String,
    ) {
        assertEquals(arms.split('|'), reportedArms(statement), statement)
    }
}
