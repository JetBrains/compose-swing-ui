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

class ComposableStateFlowValueDetectorTest {
    @Test
    fun errors() {
        val findings = RuntimeRuleLint.lint("StateFlowValueCalledInComposition", errorsSource)
        assertEquals(14, findings.size)
        assertFinding(findings[0], 18)
        assertFinding(findings[1], 19)
        assertFinding(findings[2], 23)
        assertFinding(findings[3], 24)
        assertFinding(findings[4], 28)
        assertFinding(findings[5], 29)
        assertFinding(findings[6], 38)
        assertFinding(findings[7], 39)
        assertFinding(findings[8], 42)
        assertFinding(findings[9], 43)
        assertFinding(findings[10], 49)
        assertFinding(findings[11], 50)
        assertFinding(findings[12], 54)
        assertFinding(findings[13], 55)
    }

    @Test
    fun noErrors() {
        assertEquals(0, RuntimeRuleLint.lint("StateFlowValueCalledInComposition", noErrorsSource).size)
    }

    @Test
    fun composableGetters() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import kotlinx.coroutines.flow.MutableStateFlow

            val flow = MutableStateFlow(1)
            val direct: Int
                @Composable get() = flow.value
            @get:Composable
            val targeted: Int
                get() = flow.value
            val ordinary: Int
                get() = flow.value
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("StateFlowValueCalledInComposition", source)
        assertEquals(2, findings.size)
        findings.zip(listOf(6, 9)).forEach { (finding, line) ->
            val location = finding.entity.location.text
            assertEquals(line, source.substring(0, location.start).count { it == '\n' } + 1)
            assertEquals("value", source.substring(location.start, location.end))
            assertEquals("StateFlow.value should not be called within composition", finding.message)
        }
    }

    @Test
    fun unrelatedValueAndNestedLambda() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import kotlinx.coroutines.flow.MutableStateFlow

            class Other { val value = 1 }
            val flow = MutableStateFlow(1)
            @Composable fun test() {
                Other().value
                val callback = { flow.value }
                flow.value
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("StateFlowValueCalledInComposition", source)
        assertEquals(1, findings.size)
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals(9, source.substring(0, location.start).count { it == '\n' } + 1)
        assertEquals("value", source.substring(location.start, location.end))
    }

    private fun assertFinding(
        finding: Finding,
        line: Int,
    ) {
        val location = finding.entity.location.text
        assertEquals(line, errorsSource.substring(0, location.start).count { it == '\n' } + 1)
        assertEquals("value", errorsSource.substring(location.start, location.end))
        assertEquals("StateFlow.value should not be called within composition", finding.message)
    }
}

private val errorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import kotlinx.coroutines.flow.*

    val stateFlow: StateFlow<Boolean> = object : StateFlow<Boolean> {
        override val value = true
    }

    class TestFlow : StateFlow<Boolean> {
        override val value = true
    }

    val testFlow = TestFlow()

    @Composable
    fun Test() {
        stateFlow.value
        testFlow.value
    }

    val lambda = @Composable {
        stateFlow.value
        testFlow.value
    }

    val lambda2: @Composable () -> Unit = {
        stateFlow.value
        testFlow.value
    }

    @Composable
    fun LambdaParameter(content: @Composable () -> Unit) {}

    @Composable
    fun Test2() {
        LambdaParameter(content = {
            stateFlow.value
            testFlow.value
        })
        LambdaParameter {
            stateFlow.value
            testFlow.value
        }
    }

    fun test3() {
        val localLambda1 = @Composable {
            stateFlow.value
            testFlow.value
        }

        val localLambda2: @Composable () -> Unit = {
            stateFlow.value
            testFlow.value
        }
    }
    """.trimIndent()

private val noErrorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import kotlinx.coroutines.flow.*

    val stateFlow: StateFlow<Boolean> = object : StateFlow<Boolean> {
        override val value = true
    }

    class TestFlow : StateFlow<Boolean> {
        override val value = true
    }

    val testFlow = TestFlow()

    fun test() {
        stateFlow.value
        testFlow.value
    }

    val lambda = {
        stateFlow.value
        testFlow.value
    }

    val lambda2: () -> Unit = {
        stateFlow.value
        testFlow.value
    }

    fun lambdaParameter(action: () -> Unit) {}

    fun test2() {
        lambdaParameter(action = {
            stateFlow.value
            testFlow.value
        })
        lambdaParameter {
            stateFlow.value
            testFlow.value
        }
    }

    fun test3() {
        val localLambda1 = {
            stateFlow.value
            testFlow.value
        }

        val localLambda2: () -> Unit = {
            stateFlow.value
            testFlow.value
        }
    }
    """.trimIndent()
