/*
 * Copyright 2020 The Android Open Source Project
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

import dev.detekt.api.Finding
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposableLambdaParameterDetectorTest {
    @Test
    fun incorrectNaming() {
        check("fun Button(foo: Int, text: @Composable () -> Unit) {}", listOf("text" to 3), emptyList())
    }

    @Test
    fun notTrailing() {
        check("fun Button(content: @Composable () -> Unit, foo: Int) {}", emptyList(), listOf("content" to 3))
    }

    @Test
    fun incorrectNamingAndNotTrailing() {
        check("fun Button(text: @Composable () -> Unit, foo: Int) {}", listOf("text" to 3), listOf("text" to 3))
    }

    @Test
    fun lambdaParameterWithReceiver() {
        check("fun Button(text: @Composable String.() -> Unit) {}", listOf("text" to 3), emptyList())
    }

    @Test
    fun multipleParameters() {
        check("fun Button(text: @Composable () -> Unit, icon: @Composable () -> Unit, foo: Int) {}")
    }

    @Test
    fun nullableComposableLambdas() {
        check(
            """
            fun Button(text: @Composable (() -> Unit)?, foo: Int) {}
            @Composable
            fun Button2(text: (@Composable () -> Unit)?, foo: Int) {}
            """.trimIndent(),
            listOf("text" to 3, "text" to 5),
            listOf("text" to 3, "text" to 5),
        )
    }

    @Test
    fun receiverScopedComposableFunction() {
        check("fun String.Button(foo: Int) {}")
    }

    @Test
    fun composableLambdaWithParameter() {
        check("fun Button(foo: @Composable (Int, Boolean) -> Unit) {}")
    }

    @Test
    fun nonComposableLambda() {
        check("fun Button(text: () -> Unit, foo: Int) {}")
    }

    private fun check(
        declaration: String,
        naming: List<Pair<String, Int>> = emptyList(),
        position: List<Pair<String, Int>> = emptyList(),
    ) {
        val source = "import androidx.compose.runtime.Composable\n@Composable\n$declaration"
        assertReports(
            source,
            "ComposableLambdaParameterNaming",
            naming,
            "Composable lambda parameter should be named `content`",
        )
        assertReports(
            source,
            "ComposableLambdaParameterPosition",
            position,
            "Composable lambda parameter should be the last parameter so it can be used as a trailing lambda",
        )
    }

    private fun assertReports(
        source: String,
        rule: String,
        expected: List<Pair<String, Int>>,
        message: String,
    ) {
        val findings = RuntimeRuleLint.lint(rule, source)
        assertEquals(expected.size, findings.size)
        expected.forEachIndexed { index, (name, line) -> assertFinding(source, findings[index], name, line, message) }
    }

    private fun assertFinding(
        source: String,
        finding: Finding,
        name: String,
        line: Int,
        message: String,
    ) {
        val location = finding.entity.location.text
        assertEquals(line, source.substring(0, location.start).count { it == '\n' } + 1)
        assertEquals(name, source.substring(location.start, location.end))
        assertEquals(message, finding.message)
    }
}
