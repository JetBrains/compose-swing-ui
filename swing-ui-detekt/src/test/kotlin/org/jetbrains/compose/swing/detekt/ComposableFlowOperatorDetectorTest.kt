/*
 * Copyright 2021 The Android Open Source Project
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

class ComposableFlowOperatorDetectorTest {
    @Test
    fun errors() {
        val findings = RuntimeRuleLint.lint("FlowOperatorInvokedInComposition", errorsSource)
        assertEquals(21, findings.size)
        assertFinding(findings[0], 13, "map")
        assertFinding(findings[1], 14, "customOperator")
        assertFinding(findings[2], 15, "drop")
        assertFinding(findings[3], 20, "map")
        assertFinding(findings[4], 21, "customOperator")
        assertFinding(findings[5], 22, "drop")
        assertFinding(findings[6], 27, "map")
        assertFinding(findings[7], 28, "customOperator")
        assertFinding(findings[8], 29, "drop")
        assertFinding(findings[9], 39, "map")
        assertFinding(findings[10], 40, "customOperator")
        assertFinding(findings[11], 41, "drop")
        assertFinding(findings[12], 45, "map")
        assertFinding(findings[13], 46, "customOperator")
        assertFinding(findings[14], 47, "drop")
        assertFinding(findings[15], 54, "map")
        assertFinding(findings[16], 55, "customOperator")
        assertFinding(findings[17], 56, "drop")
        assertFinding(findings[18], 61, "map")
        assertFinding(findings[19], 62, "customOperator")
        assertFinding(findings[20], 63, "drop")
    }

    @Test
    fun noErrors() {
        assertEquals(0, RuntimeRuleLint.lint("FlowOperatorInvokedInComposition", noErrorsSource).size)
    }

    @Test
    fun annotatedLambdaUsesResolvedAnnotationAndStopsAtNestedLambda() {
        val source =
            """
            import androidx.compose.runtime.Composable as Compose
            import kotlinx.coroutines.flow.*

            val flow: Flow<Int> = emptyFlow()
            val content = @Compose {
                flow.drop(1)
                val callback = { flow.drop(1) }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("FlowOperatorInvokedInComposition", source)
        assertEquals(1, findings.size)
        val location = findings[0].entity.location.text
        assertEquals("drop", source.substring(location.start, location.end))
    }

    private fun assertFinding(
        finding: Finding,
        line: Int,
        name: String,
    ) {
        val location = finding.entity.location.text
        assertEquals(line, errorsSource.substring(0, location.start).count { it == '\n' } + 1)
        assertEquals(name, errorsSource.substring(location.start, location.end))
        assertEquals("Flow operator functions should not be invoked within composition", finding.message)
    }
}

private val errorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import kotlinx.coroutines.flow.*

    val emptyFlow: Flow<Unit> = emptyFlow()

    fun <T> Flow<T>.customOperator(param: Boolean): Flow<T> = this

    @Composable
    fun Test() {
        emptyFlow
            .map { true }
            .customOperator(true)
            .drop(0)
    }

    val lambda = @Composable {
        emptyFlow
            .map { true }
            .customOperator(true)
            .drop(0)
    }

    val lambda2: @Composable () -> Unit = {
        emptyFlow
            .map { true }
            .customOperator(true)
            .drop(0)
    }

    @Composable
    fun LambdaParameter(content: @Composable () -> Unit) {}

    @Composable
    fun Test2() {
        LambdaParameter(content = {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        })
        LambdaParameter {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        }
    }

    fun test3() {
        val localLambda1 = @Composable {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        }

        val localLambda2: @Composable () -> Unit = {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        }
    }
    """.trimIndent()

private val noErrorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import kotlinx.coroutines.flow.*

    val emptyFlow: Flow<Unit> = emptyFlow()

    fun <T> Flow<T>.customOperator(param: Boolean): Flow<T> = this

    @Composable
    fun <T> Flow<T>.customComposableOperator(param: Boolean): Flow<T> = this

    fun test() {
        emptyFlow
            .map { true }
            .customOperator(true)
            .drop(0)
    }

    val lambda = {
        emptyFlow
            .map { true }
            .customOperator(true)
            .drop(0)
    }

    val lambda2: () -> Unit = {
        emptyFlow
            .map { true }
            .customOperator(true)
            .drop(0)
    }

    fun lambdaParameter(action: () -> Unit) {}

    fun test2() {
        lambdaParameter(action = {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        })
        lambdaParameter {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        }
    }

    fun test3() {
        val localLambda1 = {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        }

        val localLambda2: () -> Unit = {
            emptyFlow
                .map { true }
                .customOperator(true)
                .drop(0)
        }
    }

    @Composable
    fun test4() {
        emptyFlow
            .customComposableOperator(true)
    }
    """.trimIndent()
