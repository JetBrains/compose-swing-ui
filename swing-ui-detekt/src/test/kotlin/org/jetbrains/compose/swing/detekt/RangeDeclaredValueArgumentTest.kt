// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RangeDeclaredValueArgumentTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun lint(statement: String): List<Pair<String, String>> {
        val source =
            """
            package sample
            import androidx.annotation.FloatRange
            import androidx.annotation.IntRange
            fun setLevel(@IntRange(from = 0, to = 10) level: Int) = Unit
            fun setAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Double) = Unit
            fun setOpen(@FloatRange(from = 0.0, fromInclusive = false) alpha: Double) = Unit
            @IntRange(from = -10, to = -1) fun previousLevel(): Int = -5
            @IntRange(from = 0, to = 100) fun percent(): Int = 5
            @IntRange(from = -5, to = 100) fun wide(): Int = 5
            @IntRange(from = 2, to = 8) fun inside(): Int = 5
            @IntRange(from = 0) fun unbounded(): Int = 5
            @FloatRange(from = -1.0, to = 1.0) fun signed(): Double = 0.0
            @FloatRange(from = 0.0, to = 1.0) fun unit(): Double = 0.5
            fun plain(): Int = 5
            class Holder {
                @IntRange(from = -10, to = -1) val depth: Int = -5
                @get:IntRange(from = -10, to = -1) val getterDepth: Int get() = -5
                @IntRange(from = 2, to = 8) val safeDepth: Int = 5
            }
            fun caller(holder: Holder, flag: Boolean, n: Int, @IntRange(from = -10, to = -1) param: Int) {
                $statement
            }
            """.trimIndent()
        val findings = subject.lintWithContext(environment, source)
        return findings.map {
            source.substring(it.entity.location.text.start, it.entity.location.text.end) to it.message
        }
    }

    @ParameterizedTest(name = "reports {0}")
    @CsvSource(
        delimiterString = " => ",
        value = [
            "'setLevel(previousLevel())' => 'previousLevel()' => 'as low as -10'",
            "'setLevel(percent())' => 'percent()' => 'as high as 100'",
            "'setLevel(wide())' => 'wide()' => 'as low as -5 and as high as 100'",
            "'setLevel(unbounded())' => 'unbounded()' => 'unbounded above'",
            "'setLevel(holder.depth)' => 'holder.depth' => 'as low as -10'",
            "'setLevel(holder.getterDepth)' => 'holder.getterDepth' => 'as low as -10'",
            "'setLevel(param)' => 'param' => 'as low as -10'",
        ],
    )
    fun `reports an IntRange argument whose declared range can leave the parameter range`(
        statement: String,
        reported: String,
        leak: String,
    ) {
        val findings = lint(statement)

        assertEquals(listOf(reported), findings.map { it.first }, statement)
        val name = reported.substringAfterLast('.').substringBefore('(')
        val message = findings.single().second
        val expected = "Value $name can be $leak, outside the declared range, which requires \u2265 0 and \u2264 10."
        assertTrue(message.contains(expected), message)
    }

    @ParameterizedTest(name = "reports {0}")
    @CsvSource(
        delimiterString = " => ",
        value = [
            "'setAlpha(signed())' => 'signed()' => 'as low as -1.0' => '\u2265 0.0 and \u2264 1.0'",
            "'setOpen(unit())' => 'unit()' => 'as low as 0.0' => '> 0.0'",
        ],
    )
    fun `reports a FloatRange argument whose declared range can leave the parameter range`(
        statement: String,
        reported: String,
        leak: String,
        requirement: String,
    ) {
        val findings = lint(statement)

        assertEquals(listOf(reported), findings.map { it.first }, statement)
        val name = reported.substringBefore('(')
        val message = findings.single().second
        val expected = "Value $name can be $leak, outside the declared range, which requires $requirement."
        assertTrue(message.contains(expected), message)
    }

    @ParameterizedTest(name = "passes {0}")
    @CsvSource(
        "'setLevel(inside())'",
        "'setAlpha(unit())'",
        "'setLevel(plain())'",
        "'setLevel(holder.safeDepth)'",
        "'setLevel(if (flag) 0 else 1)'",
        "'setLevel(if (flag) { 0 } else { 1 })'",
        "'setLevel(when (n) { 0 -> 1; else -> 2 })'",
        "'setLevel(if (flag) throw IllegalStateException() else 1)'",
    )
    fun `does not report an argument whose value fits the parameter range`(statement: String) {
        assertEquals(emptyList(), lint(statement), statement)
    }
}
