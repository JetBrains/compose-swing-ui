// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SizeArgumentTest {
    private val environment = createEnvironment()
    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("ArgumentOutsideDeclaredRange"))

    private fun lint(statement: String): List<Pair<String, String>> {
        val source =
            """
            package sample
            import androidx.annotation.Size
            fun setCode(@Size(5) code: String) = Unit
            fun setName(@Size(min = 2) name: String) = Unit
            fun setTag(@Size(max = 3) tag: String) = Unit
            fun setPairs(@Size(multiple = 2) pairs: IntArray) = Unit
            fun setRow(@Size(min = 1, max = 3) row: Array<String>) = Unit
            fun setItems(@Size(2) items: List<String>) = Unit
            fun setUnique(@Size(2) items: Set<String>) = Unit
            fun <T> setCounted(@Size(2) values: T) = Unit
            const val FOUR = "1234"
            fun caller(flag: Boolean, text: String, values: IntArray, many: List<String>) {
                $statement
            }
            """.trimIndent()
        return subject.lintWithContext(environment, source).map {
            source.substring(it.entity.location.text.start, it.entity.location.text.end) to it.message
        }
    }

    @ParameterizedTest(name = "reports {0}")
    @CsvSource(
        delimiterString = " => ",
        value = [
            "'setCode(\"1234\")' => '\"1234\"' => 'Size 4'",
            "'setCode(FOUR)' => 'FOUR' => 'Size 4'",
            "'setCode(if (flag) \"12345\" else \"1\")' => '\"1\"' => 'Size 1'",
            "'setName(\"a\")' => '\"a\"' => 'at least 2'",
            "'setTag(\"abcd\")' => '\"abcd\"' => 'at most 3'",
            "'setPairs(intArrayOf(1, 2, 3))' => 'intArrayOf(1, 2, 3)' => 'multiple of 2'",
            "'setRow(arrayOf())' => 'arrayOf()' => 'at least 1'",
            "'setRow(arrayOf(\"a\", \"b\", \"c\", \"d\"))' => 'arrayOf(\"a\", \"b\", \"c\", \"d\")' => 'at most 3'",
            "'setItems(listOf(\"a\"))' => 'listOf(\"a\")' => 'Size 1'",
            "'setItems(listOf(\"a\", \"b\", \"c\"))' => 'listOf(\"a\", \"b\", \"c\")' => 'Size 3'",
            "'setItems(mutableListOf(\"a\"))' => 'mutableListOf(\"a\")' => 'Size 1'",
            "'setItems(emptyList())' => 'emptyList()' => 'Size 0'",
            "'setUnique(setOf(\"a\", \"b\", \"c\"))' => 'setOf(\"a\", \"b\", \"c\")' => 'Size 3'",
            "'setUnique(setOf(\"a\"))' => 'setOf(\"a\")' => 'Size 1'",
            "'setUnique(setOf(\"a\", \"a\", \"a\"))' => 'setOf(\"a\", \"a\", \"a\")' => 'Size 1'",
            "'setUnique(mutableSetOf(\"a\", \"b\", \"c\"))' => 'mutableSetOf(\"a\", \"b\", \"c\")' => 'Size 3'",
            "'setUnique(emptySet())' => 'emptySet()' => 'Size 0'",
            "'setItems(arrayListOf(\"a\"))' => 'arrayListOf(\"a\")' => 'Size 1'",
            "'setRow(emptyArray())' => 'emptyArray()' => 'at least 1'",
            "'setCounted(booleanArrayOf(true))' => 'booleanArrayOf(true)' => 'Size 1'",
            "'setCounted(byteArrayOf(1))' => 'byteArrayOf(1)' => 'Size 1'",
            "'setCounted(charArrayOf(Char.MIN_VALUE))' => 'charArrayOf(Char.MIN_VALUE)' => 'Size 1'",
            "'setCounted(shortArrayOf(1))' => 'shortArrayOf(1)' => 'Size 1'",
            "'setCounted(longArrayOf(1))' => 'longArrayOf(1)' => 'Size 1'",
            "'setCounted(floatArrayOf(1f))' => 'floatArrayOf(1f)' => 'Size 1'",
            "'setCounted(doubleArrayOf(1.0))' => 'doubleArrayOf(1.0)' => 'Size 1'",
        ],
    )
    fun `reports a value whose known size breaks the declared size`(
        statement: String,
        reported: String,
        message: String,
    ) {
        val findings = lint(statement)

        assertEquals(listOf(reported), findings.map { it.first }, statement)
        assertTrue(findings.single().second.contains(message), findings.single().second)
    }

    @ParameterizedTest(name = "passes {0}")
    @CsvSource(
        "'setCode(\"12345\")'",
        "'setCode(text)'",
        "'setName(\"ab\")'",
        "'setName(FOUR)'",
        "'setTag(\"abc\")'",
        "'setPairs(intArrayOf())'",
        "'setPairs(intArrayOf(1, 2))'",
        "'setPairs(intArrayOf(*values))'",
        "'setPairs(values)'",
        "'setRow(arrayOf(\"a\"))'",
        "'setRow(arrayOf(*arrayOf(\"a\", \"b\", \"c\", \"d\")))'",
        "'setItems(listOf(\"a\", \"b\"))'",
        "'setItems(listOf(*arrayOf(\"a\")))'",
        "'setItems(many)'",
        "'setUnique(setOf(\"a\", text, \"c\"))'",
        "'setCode(if (flag) \"12345\" else text)'",
    )
    fun `does not report a value whose size is unknown or satisfies the declared size`(statement: String) {
        assertEquals(emptyList(), lint(statement), statement)
    }
}
