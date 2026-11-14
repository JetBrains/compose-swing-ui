// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RangeOperatorArgumentTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun lint(statement: String) =
        subject.lintWithContext(
            environment,
            """
            package sample
            import androidx.annotation.IntRange
            class Table {
                operator fun get(@IntRange(from = 0) index: Int) = 0
                operator fun set(@IntRange(from = 0) index: Int, value: Int) = Unit
                operator fun plus(@IntRange(from = 0) amount: Int) = this
                operator fun contains(@IntRange(from = 0) element: Int) = true
                operator fun invoke(@IntRange(from = 0) index: Int) = 0
            }
            class Buffer {
                operator fun plusAssign(@IntRange(from = 0) amount: Int) = Unit
            }
            fun caller(table: Table, buffer: Buffer) {
                var local = table
                $statement
            }
            """.trimIndent(),
        )

    @ParameterizedTest(name = "reports {0}")
    @CsvSource(
        "'table[-1]'",
        "'table[-1] = 0'",
        "'table[-1] += 1'",
        "'buffer += -1'",
        "'table + -1'",
        "'local += -1'",
        "'-1 in table'",
        "'table(-1)'",
    )
    fun `reports a constant below the declared range at every call form`(statement: String) {
        val findings = lint(statement)

        assertEquals(1, findings.size, statement)
        findings.forEach { assertEquals(true, it.message.contains("Value -1"), statement) }
    }

    @ParameterizedTest(name = "passes {0}")
    @CsvSource(
        "'table[0]'",
        "'table[0] = -1'",
        "'table + 1'",
        "'local += 1'",
        "'buffer += 1'",
        "'table[0] += 1'",
        "'0 in table'",
        "'table(0)'",
    )
    fun `does not report a constant within the declared range at any call form`(statement: String) {
        assertEquals(0, lint(statement).size, statement)
    }
}
