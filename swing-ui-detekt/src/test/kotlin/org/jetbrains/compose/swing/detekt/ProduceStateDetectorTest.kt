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

class ProduceStateDetectorTest {
    @Test
    fun errors() {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*

            @Composable
            fun Test() {
                produceState(true, true) {
                    val foo = value
                }
                produceState(true, true) {
                    awaitDispose { }
                }
                produceState(true, true) {
                    doSomethingWithState()
                }
                produceState(true, true) {
                    doSomethingElseWithState(this)
                }
            }

            fun <T> State<T>.doSomethingWithState() {}
            fun <T> doSomethingElseWithState(state: State<T>) {}
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("ProduceStateDoesNotAssignValue", source)
        assertEquals(4, findings.size)
        assertFinding(source, findings[0], 7)
        assertFinding(source, findings[1], 10)
        assertFinding(source, findings[2], 13)
        assertFinding(source, findings[3], 16)
    }

    @Test
    fun noErrors() {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*

            @Composable
            fun Test() {
                produceState(true, true) { value = true }
                produceState(true, true) { this.value = true }
                produceState("Hi", "Any?") { value += ", world" }
                produceState(1, true) { --value }
                produceState(1, true) { ++value }
                produceState(1, true) { value-- }
                produceState(1, true) { value++ }
                produceState(true, true) { doSomethingWithScope() }
                produceState(true, true) { doSomethingElseWithScope(this) }
                produceState(true, true) { doSomethingWithState() }
                produceState(true, true) { doSomethingElseWithState(this) }
            }

            fun <T> MutableState<T>.doSomethingWithState() {}
            fun <T> doSomethingElseWithState(state: MutableState<T>) {}
            fun <T> ProduceStateScope<T>.doSomethingWithScope() {}
            fun <T> doSomethingElseWithScope(scope: ProduceStateScope<T>) {}
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("ProduceStateDoesNotAssignValue", source).size)
    }

    private fun assertFinding(
        source: String,
        finding: Finding,
        line: Int,
    ) {
        assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
        assertEquals("produceState calls should assign `value` inside the producer lambda", finding.message)
    }
}
