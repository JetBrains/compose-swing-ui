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

class ComposableCoroutineCreationDetectorTest {
    @Test
    fun errors() {
        val findings = RuntimeRuleLint.lint("CoroutineCreationDuringComposition", errorsSource)
        assertEquals(21, findings.size)
        assertFinding(findings[0], 11, "async")
        assertFinding(findings[1], 12, "launch")
        assertFinding(findings[2], 13, "launchIn")
        assertFinding(findings[3], 17, "async")
        assertFinding(findings[4], 18, "launch")
        assertFinding(findings[5], 19, "launchIn")
        assertFinding(findings[6], 23, "async")
        assertFinding(findings[7], 24, "launch")
        assertFinding(findings[8], 25, "launchIn")
        assertFinding(findings[9], 34, "async")
        assertFinding(findings[10], 35, "launch")
        assertFinding(findings[11], 36, "launchIn")
        assertFinding(findings[12], 39, "async")
        assertFinding(findings[13], 40, "launch")
        assertFinding(findings[14], 41, "launchIn")
        assertFinding(findings[15], 47, "async")
        assertFinding(findings[16], 48, "launch")
        assertFinding(findings[17], 49, "launchIn")
        assertFinding(findings[18], 53, "async")
        assertFinding(findings[19], 54, "launch")
        assertFinding(findings[20], 55, "launchIn")
    }

    @Test
    fun noErrors() {
        assertEquals(0, RuntimeRuleLint.lint("CoroutineCreationDuringComposition", noErrorsSource).size)
    }

    @Test
    fun resolvedCallsAndCompositionBoundaries() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch as start

            fun launch() {}

            @Composable
            fun Example(scope: CoroutineScope) {
                launch()
                scope.start {}
                LaunchedEffect(Unit) { scope.start {} }
                val callback = { scope.start {} }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("CoroutineCreationDuringComposition", source)
        assertEquals(1, findings.size)
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals("start", source.substring(location.start, location.end))
        assertEquals(
            "Calls to launch should happen inside a LaunchedEffect and not composition",
            findings.single().message,
        )
    }

    @Test
    fun anonymousFunctionsWithComposablePropertyTypes() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch

            val scope = CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)
            val content: @Composable () -> Unit = fun() { scope.launch {} }
            val ordinary: () -> Unit = fun() { scope.launch {} }

            fun example() {
                val local: @Composable () -> Unit = fun() { scope.launch {} }
                val plain: () -> Unit = fun() { scope.launch {} }
                val outer: @Composable () -> Unit = fun() {
                    fun nested() { scope.launch {} }
                }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("CoroutineCreationDuringComposition", source)
        assertEquals(2, findings.size)
        listOf(6, 10).forEachIndexed { index, line ->
            val finding = findings[index]
            val location = finding.entity.location.text
            assertEquals(line, source.substring(0, location.start).count { it == '\n' } + 1)
            assertEquals("launch", source.substring(location.start, location.end))
            assertEquals("Calls to launch should happen inside a LaunchedEffect and not composition", finding.message)
        }
    }

    private fun assertFinding(
        finding: Finding,
        line: Int,
        name: String,
    ) {
        val location = finding.entity.location.text
        assertEquals(line, errorsSource.substring(0, location.start).count { it == '\n' } + 1)
        assertEquals(name, errorsSource.substring(location.start, location.end))
        assertEquals("Calls to $name should happen inside a LaunchedEffect and not composition", finding.message)
    }
}

private val errorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import kotlinx.coroutines.*
    import kotlinx.coroutines.flow.*

    val scope = CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)

    @Composable
    fun Test() {
        scope.async {}
        scope.launch {}
        flowOf(Unit).launchIn(scope)
    }

    val lambda = @Composable {
        scope.async {}
        scope.launch {}
        flowOf(Unit).launchIn(scope)
    }

    val lambda2: @Composable () -> Unit = {
        scope.async {}
        scope.launch {}
        flowOf(Unit).launchIn(scope)
    }

    @Composable
    fun LambdaParameter(content: @Composable () -> Unit) {}

    @Composable
    fun Test2() {
        LambdaParameter(content = {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        })
        LambdaParameter {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        }
    }

    fun test3() {
        val localLambda1 = @Composable {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        }

        val localLambda2: @Composable () -> Unit = {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        }
    }
    """.trimIndent()

private val noErrorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import kotlinx.coroutines.*
    import kotlinx.coroutines.flow.*

    val scope = CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)

    fun test() {
        scope.async {}
        scope.launch {}
        flowOf(Unit).launchIn(scope)
    }

    val lambda = {
        scope.async {}
        scope.launch {}
        flowOf(Unit).launchIn(scope)
    }

    val lambda2: () -> Unit = {
        scope.async {}
        scope.launch {}
        flowOf(Unit).launchIn(scope)
    }

    fun lambdaParameter(action: () -> Unit) {}

    fun test2() {
        lambdaParameter(action = {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        })
        lambdaParameter {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        }
    }

    fun test3() {
        val localLambda1 = {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        }

        val localLambda2: () -> Unit = {
            scope.async {}
            scope.launch {}
            flowOf(Unit).launchIn(scope)
        }
    }
    """.trimIndent()
