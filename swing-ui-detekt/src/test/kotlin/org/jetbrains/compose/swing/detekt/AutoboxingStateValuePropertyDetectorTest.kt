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

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoboxingStateValuePropertyDetectorTest {
    @Test
    fun testReadAutoboxingPropertyAsVariableAssignment() {
        assertFinding("val value = state.value", "Reading")
    }

    @Test
    fun testTrivialAssignAutoboxingProperty() {
        assertFinding("state.value = 42", "Assigning")
    }

    @Test
    fun testCompoundAssignAutoboxingProperty() {
        assertFinding("state.value += 42", "Assigning")
    }

    @Test
    fun testCompoundAssignAutoboxingPropertyInExtensionReceiver() {
        val source =
            """
            import androidx.compose.runtime.mutableIntStateOf

            class StateWrapper {
                val state = mutableIntStateOf(0)
            }

            fun StateWrapper.valueAssignment() {
                state.value += 42
            }
            """.trimIndent()
        assertFindingIn(source, "Assigning", 8)
    }

    @Test
    fun parenthesizedAssignmentUsesWriteWording() {
        assertFinding("(state.value) = 42", "Assigning")
    }

    @Test
    fun incrementUsesWriteWording() {
        assertFinding("state.value++", "Assigning")
    }

    @Test
    fun ignoresSpecializedAndUnannotatedProperties() {
        val source =
            """
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.mutableStateOf

            fun valueAssignment() {
                val state = mutableIntStateOf(0)
                val generic = mutableStateOf(0)
                state.intValue = generic.value
            }
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("AutoboxingStateValueProperty", source).size)
    }

    private fun assertFinding(
        statement: String,
        access: String,
    ) {
        val source =
            """
            import androidx.compose.runtime.mutableIntStateOf

            fun valueAssignment() {
                val state = mutableIntStateOf(0)
                $statement
            }
            """.trimIndent()
        assertFindingIn(source, access, 5)
    }

    private fun assertFindingIn(
        source: String,
        access: String,
        line: Int,
    ) {
        val findings = RuntimeRuleLint.lint("AutoboxingStateValueProperty", source)
        assertEquals(1, findings.size)
        val finding = findings.single()
        assertEquals(
            "$access `value` will cause an autoboxing operation. Use `intValue` to avoid unnecessary allocations.",
            finding.message,
        )
        val location = finding.entity.location.text
        assertEquals(line, source.substring(0, location.start).count { it == '\n' } + 1)
        assertEquals("value", source.substring(location.start, location.end))
    }
}
