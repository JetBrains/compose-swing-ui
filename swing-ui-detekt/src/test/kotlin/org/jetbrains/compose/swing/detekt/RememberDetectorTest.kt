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

class RememberDetectorTest {
    @Test
    fun returnsUnit() {
        val source = returnsUnitSource
        val findings = RuntimeRuleLint.lint("RememberReturnType", source)
        assertEquals(20, findings.size)
        assertFinding(source, findings[0], 13)
        assertFinding(source, findings[1], 16)
        assertFinding(source, findings[2], 20)
        assertFinding(source, findings[3], 21)
        assertFinding(source, findings[4], 27)
        assertFinding(source, findings[5], 30)
        assertFinding(source, findings[6], 34)
        assertFinding(source, findings[7], 35)
        assertFinding(source, findings[8], 41)
        assertFinding(source, findings[9], 45)
        assertFinding(source, findings[10], 50)
        assertFinding(source, findings[11], 51)
        assertFinding(source, findings[12], 57)
        assertFinding(source, findings[13], 62)
        assertFinding(source, findings[14], 68)
        assertFinding(source, findings[15], 69)
        assertFinding(source, findings[16], 75)
        assertFinding(source, findings[17], 80)
        assertFinding(source, findings[18], 86)
        assertFinding(source, findings[19], 87)
    }

    @Test
    fun returnsUnit_dueToTypeError() {
        val source = typeErrorSource
        val findings = RuntimeRuleLint.lint("RememberReturnType", source)
        assertEquals(0, findings.size)
    }

    @Test
    fun returnsValue_explicitUnitType() {
        val source = explicitUnitSource
        val findings = RuntimeRuleLint.lint("RememberReturnType", source)
        assertEquals(10, findings.size)
        assertFinding(source, findings[0], 13)
        assertFinding(source, findings[1], 16)
        assertFinding(source, findings[2], 24)
        assertFinding(source, findings[3], 27)
        assertFinding(source, findings[4], 35)
        assertFinding(source, findings[5], 39)
        assertFinding(source, findings[6], 48)
        assertFinding(source, findings[7], 53)
        assertFinding(source, findings[8], 63)
        assertFinding(source, findings[9], 68)
    }

    @Test
    fun noErrors() {
        val source = noErrorsSource
        val findings = RuntimeRuleLint.lint("RememberReturnType", source)
        assertEquals(0, findings.size)
    }

    @Test
    fun resolvesTypesThroughContextParameter() {
        val source = contextParameterSource
        val findings = RuntimeRuleLint.lint("RememberReturnType", source)
        assertEquals(1, findings.size)
        assertFinding(source, findings[0], 11)
    }
}

private fun assertFinding(
    source: String,
    finding: Finding,
    line: Int,
) {
    assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
    assertEquals("`remember` calls must not return `Unit`", finding.message)
}

private val returnsUnitSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember

    class FooState {
        fun update(new: Int) {}
    }

    @Composable
    fun Test() {
        val state = remember { FooState() }
        remember {
            state.update(5)
        }
        val unit = remember {
            state.update(5)
        }
        val unitLambda: () -> Unit = {}
        remember(unitLambda)
        val unit2 = remember(unitLambda)
    }

    @Composable
    fun Test(number: Int) {
        val state = remember { FooState() }
        remember(number) {
            state.update(number)
        }
        val unit = remember(number) {
            state.update(number)
        }
        val unitLambda: () -> Unit = {}
        remember(number, unitLambda)
        val unit2 = remember(number, unitLambda)
    }

    @Composable
    fun Test(number1: Int, number2: Int) {
        val state = remember { FooState() }
        remember(number1, number2) {
            state.update(number1)
            state.update(number2)
        }
        val unit = remember(number1, number2) {
            state.update(number1)
            state.update(number2)
        }
        val unitLambda: () -> Unit = {}
        remember(number1, number2, unitLambda)
        val unit2 = remember(number1, number2, unitLambda)
    }

    @Composable
    fun Test(number1: Int, number2: Int, number3: Int) {
        val state = remember { FooState() }
        remember(number1, number2, number3) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
        val unit = remember(number1, number2, number3) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
        val unitLambda: () -> Unit = {}
        remember(number1, number2, number3, unitLambda)
        val unit2 = remember(number1, number2, number3, unitLambda)
    }

    @Composable
    fun Test(number1: Int, number2: Int, number3: Int, flag: Boolean) {
        val state = remember { FooState() }
        remember(number1, number2, number3, flag) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
        val unit = remember(number1, number2, number3, flag) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
        val unitLambda: () -> Unit = {}
        remember(number1, number2, number3, flag, calculation = unitLambda)
        val unit2 = remember(number1, number2, number3, flag, calculation = unitLambda)
    }
    """.trimIndent()

private val typeErrorSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember

    @Composable
    fun Test() {
        val shouldBeError = remember { Unknown() }
        val stillError = remember {
            val local = Unknown()
            local
        }
        val shouldBeInt = remember { 42 }
        val stillInt = remember {
            val local = Unknown()
            42
        }
    }
    """.trimIndent()

private val explicitUnitSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember

    class FooState {
        fun update(new: Int): Boolean = true
    }

    @Composable
    fun Test() {
        val state = remember { FooState() }
        remember<Unit> {
            state.update(5)
        }
        val result = remember<Unit> {
            state.update(5)
        }
    }

    @Composable
    fun Test(number: Int) {
        val state = remember { FooState() }
        remember<Unit>(number) {
            state.update(number)
        }
        val result = remember<Unit>(number) {
            state.update(number)
        }
    }

    @Composable
    fun Test(number1: Int, number2: Int) {
        val state = remember { FooState() }
        remember<Unit>(number1, number2) {
            state.update(number1)
            state.update(number2)
        }
        val result = remember<Unit>(number1, number2) {
            state.update(number1)
            state.update(number2)
        }
    }

    @Composable
    fun Test(number1: Int, number2: Int, number3: Int) {
        val state = remember { FooState() }
        remember<Unit>(number1, number2, number3) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
        val result = remember<Unit>(number1, number2, number3) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
    }

    @Composable
    fun Test(number1: Int, number2: Int, number3: Int, flag: Boolean) {
        val state = remember { FooState() }
        remember<Unit>(number1, number2, number3, flag) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
        val result = remember<Unit>(number1, number2, number3, flag) {
            state.update(number1)
            state.update(number2)
            state.update(number3)
        }
    }
    """.trimIndent()

private val noErrorsSource =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember

    class FooState {
        fun update(new: Int): Boolean = true
    }

    @Composable
    fun Test() {
        val state = remember { FooState() }
        remember {
            state.update(5)
        }
        val result = remember {
            state.update(5)
        }
    }

    @Composable
    fun Test(number: Int) {
        val state = remember { FooState() }
        remember(number) {
            state.update(number)
        }
        val result = remember(number) {
            state.update(number)
        }
    }

    @Composable
    fun Test(number1: Int, number2: Int) {
        val state = remember { FooState() }
        remember(number1, number2) {
            state.update(number)
        }
        val result = remember(number1, number2) {
            state.update(number)
        }
    }

    @Composable
    fun Test(number1: Int, number2: Int, number3: Int) {
        val state = remember { FooState() }
        remember(number1, number2, number3) {
            state.update(number)
        }
        val result = remember(number1, number2, number3) {
            state.update(number)
        }
    }

    @Composable
    fun Test(number1: Int, number2: Int, number3: Int, flag: Boolean) {
        val state = remember { FooState() }
        remember(number1, number2, number3, flag) {
            state.update(number)
        }
        val result = remember(number1, number2, number3, flag) {
            state.update(number)
        }
    }
    """.trimIndent()

private val contextParameterSource =
    """
    package test

    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember

    @Composable
    context(c: FooContext)
    fun foo() {
        val rememberedString = remember { c.value }
        val rememberedContextDerived = remember { bar(c.value) }
        val remembered = remember { c.unitFun() }
    }

    fun bar(input: String) = Any()
    fun bar(input: Int) {}

    data class FooContext(
        val value: String
    ) {
        fun unitFun() {}
    }
    """.trimIndent()
