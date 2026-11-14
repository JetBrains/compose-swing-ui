/*
 * Copyright 2025 The Android Open Source Project
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
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class RememberInCompositionDetectorTest {
    @Test
    fun notRemembered() {
        val findings = RuntimeRuleLint.lint("RememberInComposition", source)
        assertEquals(53, findings.size)
        findings.zip(expectedLines).forEach { (finding, line) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
        }
    }

    @Test
    fun `reports a call in an anonymous function with a composable property type`() {
        val code =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.annotation.RememberInComposition

            @RememberInComposition
            fun Foo(): Int = 0

            val f: @Composable () -> Int = fun(): Int = Foo()
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RememberInComposition", code)

        assertEquals(1, findings.size)
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals("Foo", code.substring(location.start, location.end))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "run(fun() { Foo() })",
            "val local = object { init { Foo() } }",
            "Slot(fun() { Foo() })",
        ],
    )
    fun `reports a call in an anonymous function or an object initializer run during composition`(statement: String) {
        val code =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.annotation.RememberInComposition

            @RememberInComposition
            fun Foo(): Int = 0

            @Composable
            fun Slot(content: @Composable () -> Unit) {}

            @Composable
            fun Screen() {
                $statement
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RememberInComposition", code)

        assertEquals(1, findings.size, statement)
    }
}

private val expectedLines =
    listOf(
        7,
        8,
        9,
        10,
        11,
        13,
        14,
        18,
        19,
        20,
        21,
        22,
        24,
        25,
        29,
        30,
        31,
        32,
        33,
        35,
        36,
        45,
        46,
        47,
        48,
        49,
        51,
        52,
        55,
        56,
        57,
        58,
        59,
        61,
        62,
        68,
        69,
        70,
        71,
        72,
        74,
        75,
        79,
        80,
        81,
        82,
        83,
        85,
        86,
        93,
        94,
        95,
        97,
    )

private val source =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.*

    @Composable
    fun Test(bar: Bar) {
        test()
        val foo = Foo()
        val fooValue = foo.value
        bar.calculateValue()
        val barValue = bar.value
        val barImpl = BarImpl()
        barImpl.calculateValue()
        val barImplValue = barImpl.value
    }

    val lambda = @Composable { bar: Bar ->
        test()
        val foo = Foo()
        val fooValue = foo.value
        bar.calculateValue()
        val barValue = bar.value
        val barImpl = BarImpl()
        barImpl.calculateValue()
        val barImplValue = barImpl.value
    }

    val lambda2: @Composable (bar: Bar) -> Unit = { bar ->
        test()
        val foo = Foo()
        val fooValue = foo.value
        bar.calculateValue()
        val barValue = bar.value
        val barImpl = BarImpl()
        barImpl.calculateValue()
        val barImplValue = barImpl.value
    }

    @Composable
    fun LambdaParameter(content: @Composable () -> Unit) {}

    @Composable
    fun Test2(bar: Bar) {
        LambdaParameter(content = {
            test()
            val foo = Foo()
            val fooValue = foo.value
            bar.calculateValue()
            val barValue = bar.value
            val barImpl = BarImpl()
            barImpl.calculateValue()
            val barImplValue = barImpl.value
        })
        LambdaParameter {
            test()
            val foo = Foo()
            val fooValue = foo.value
            bar.calculateValue()
            val barValue = bar.value
            val barImpl = BarImpl()
            barImpl.calculateValue()
            val barImplValue = barImpl.value
        }
    }

    fun test3(bar: Bar) {
        val localLambda1 = @Composable {
            test()
            val foo = Foo()
            val fooValue = foo.value
            bar.calculateValue()
            val barValue = bar.value
            val barImpl = BarImpl()
            barImpl.calculateValue()
            val barImplValue = barImpl.value
        }

        val localLambda2: @Composable () -> Unit = {
            test()
            val foo = Foo()
            val fooValue = foo.value
            bar.calculateValue()
            val barValue = bar.value
            val barImpl = BarImpl()
            barImpl.calculateValue()
            val barImplValue = barImpl.value
        }
    }

    @Composable
    fun Test4(bar: Bar) {
        val localObject = object {
            val foo = Foo()
            val fooValue = foo.value
            val barValue = bar.value
            val barImpl = BarImpl()
            val barImplValue = barImpl.value
        }
    }
    """.trimIndent() + "\n\n" + RememberInCompositionFixtures.source
