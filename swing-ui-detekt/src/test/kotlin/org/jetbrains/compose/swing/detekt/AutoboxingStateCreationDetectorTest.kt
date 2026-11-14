/*
 * Copyright 2023 The Android Open Source Project
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
import kotlin.test.assertEquals

class AutoboxingStateCreationDetectorTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `reports mutableStateOf only where a primitive state class is a drop-in`(
        label: String,
        type: String,
        body: String,
        reported: Boolean,
    ) {
        val source = "package test\n\nimport androidx.compose.runtime.*\n\n$body"
        val findings = RuntimeRuleLint.lint("MutableStateAutoboxing", source)

        val texts = findings.map { source.substring(it.entity.location.text.start, it.entity.location.text.end) }
        assertEquals(if (reported) listOf("mutableStateOf") else emptyList(), texts, label)
        findings.forEach {
            assertEquals(
                "This state stores a boxed `$type`. Boxing wraps primitive values in objects and can add allocations " +
                    "when the state changes. Use `mutable${type}StateOf` to store the primitive directly.",
                it.message,
                label,
            )
        }
    }

    companion object {
        private val samples = mapOf("Int" to "42", "Long" to "0xABCDEF1234", "Float" to "1.5f", "Double" to "1.024")

        @JvmStatic
        fun cases(): List<Arguments> =
            samples.flatMap { (type, value) ->
                reportedForms(type, value).map { (label, body) -> Arguments.of("$type: $label", type, body, true) } +
                    cleanForms(type, value).map { (label, body) -> Arguments.of("$type: $label", type, body, false) }
            }

        private fun reportedForms(
            type: String,
            value: String,
        ): List<Pair<String, String>> =
            listOf(
                "explicit type argument" to "fun f() { val state = mutableStateOf<$type>($value) }",
                "inferred type" to "fun f() { val state = mutableStateOf($value) }",
                "fully qualified type argument" to "fun f() { val state = mutableStateOf<kotlin.$type>($value) }",
                "delegate with explicit type argument" to "fun f() { var state by mutableStateOf<$type>($value) }",
                "delegate with inferred type" to "fun f() { var state by mutableStateOf($value) }",
                "delegate with an internal setter and a parameter value" to
                    """
                    class Test(initialValue: $type = $value) {
                        var state by mutableStateOf(initialValue)
                            private set
                    }
                    """.trimIndent(),
                "delegate typed by the property" to "fun f() { var state: $type by mutableStateOf($value) }",
                "explicit structural equality policy" to
                    "fun f() { val state = mutableStateOf($value, structuralEqualityPolicy()) }",
            )

        private fun cleanForms(
            type: String,
            value: String,
        ): List<Pair<String, String>> =
            listOf(
                "nullable delegate type" to "fun f() { var state: $type? by mutableStateOf($value) }",
                "non-structural equality policy" to
                    "fun f() { val state = mutableStateOf($value, neverEqualPolicy()) }",
                "nullable type argument" to "fun f() { val state = mutableStateOf<$type?>($value) }",
                "nullable declared state type" to
                    "fun f() { val state: MutableState<$type?> = mutableStateOf($value) }",
                "nullable cast value" to "fun f() { val state = mutableStateOf($value as $type?) }",
            )
    }
}
