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
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test
import kotlin.test.assertEquals

class FrequentlyChangingValueDetectorTest {
    @Test
    fun errors() {
        checkBoth(
            errorsSource,
            listOf(
                7,
                9,
                10,
                11,
                13,
                14,
                18,
                20,
                21,
                22,
                24,
                25,
                29,
                31,
                32,
                33,
                35,
                36,
                45,
                47,
                48,
                49,
                51,
                52,
                55,
                57,
                58,
                59,
                61,
                62,
                68,
                70,
                71,
                72,
                74,
                75,
                79,
                81,
                82,
                83,
                85,
                86,
                94,
                95,
                97,
            ),
        )
    }

    @Test
    fun errors_getterInheritanceHierarchy() {
        checkBoth(inheritanceSource, listOf(12, 13))
    }

    @Test
    fun noErrors() {
        checkBoth(noErrorsSource, emptyList())
    }

    @Test
    fun explicitComposableLambdaRespectsNestedBoundaries() {
        checkBoth(
            """
            package fixture

            import androidx.compose.runtime.*

            fun deferred(block: () -> Unit) {}

            val content = @Composable {
                run { test() }
                deferred { test() }
                fun local() { test() }
            }
            """.trimIndent(),
            listOf(8),
        )
    }

    @ParameterizedTest(name = "declaration in source: {0}")
    @CsvSource("true", "false")
    fun composableGettersRespectNestedBoundaries(declarationInSource: Boolean) {
        val source =
            if (declarationInSource) {
                RememberInCompositionFixtures.composableGetters("fixture") +
                    "\n\n@androidx.compose.runtime.annotation.FrequentlyChangingValue\nfun test(): Int = 5"
            } else {
                RememberInCompositionFixtures.composableGetters("fixture").replace(
                    "import androidx.compose.runtime.*",
                    "import androidx.compose.runtime.*; import androidx.compose.runtime.foo.*",
                )
            }
        val findings = RuntimeRuleLint.lint("FrequentlyChangingValue", source)
        assertEquals(2, findings.size)
        findings.zip(listOf(9, 10)).forEach { (finding, line) ->
            assertEquals(
                "Reading a value annotated with @FrequentlyChangingValue inside composition",
                finding.message,
            )
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
        }
    }

    private fun checkBoth(
        source: String,
        lines: List<Int>,
    ) {
        listOf(
            source + "\n" + definitions,
            source.replace(
                "import androidx.compose.runtime.*",
                "import androidx.compose.runtime.*; import androidx.compose.runtime.foo.*",
            ),
        ).forEach { fixture ->
            val findings = RuntimeRuleLint.lint("FrequentlyChangingValue", fixture)
            assertEquals(lines.size, findings.size)
            findings.zip(lines).forEach { (finding, line) ->
                assertEquals(
                    "Reading a value annotated with @FrequentlyChangingValue inside composition",
                    finding.message,
                )
                assertEquals(line, fixture.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            }
        }
    }
}

private val errorsSource =
    """
    package fixture

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
    """.trimIndent()

private val inheritanceSource =
    """
    package fixture

    import androidx.compose.runtime.*

    @Composable
    fun Test(
        topLevel: TopLevelWithoutAnnotation,
        innerLevel: InnerLevelWithAnnotation,
        lowerLevel: LowerLevelWithoutAnnotation
    ) {
        topLevel.value
        innerLevel.value
        lowerLevel.value
    }
    """.trimIndent()

private val noErrorsSource =
    """
    package fixture

    import androidx.compose.runtime.*

    fun test(bar: Bar) {
        test()
        val foo = Foo()
        val fooValue = foo.value
        bar.calculateValue()
        val barValue = bar.value
        val barImpl = BarImpl()
        barImpl.calculateValue()
        val barImplValue = barImpl.value
    }

    val lambda = { bar: Bar ->
        test()
        val foo = Foo()
        val fooValue = foo.value
        bar.calculateValue()
        val barValue = bar.value
        val barImpl = BarImpl()
        barImpl.calculateValue()
        val barImplValue = barImpl.value
    }

    val lambda2: (bar: Bar) -> Unit = { bar ->
        test()
        val foo = Foo()
        val fooValue = foo.value
        bar.calculateValue()
        val barValue = bar.value
        val barImpl = BarImpl()
        barImpl.calculateValue()
        val barImplValue = barImpl.value
    }

    fun lambdaParameter(content: () -> Unit) {}

    fun test2(bar: Bar) {
        lambdaParameter(content = {
            test()
            val foo = Foo()
            val fooValue = foo.value
            bar.calculateValue()
            val barValue = bar.value
            val barImpl = BarImpl()
            barImpl.calculateValue()
            val barImplValue = barImpl.value
        })
        lambdaParameter {
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
        val localLambda1 = {
            test()
            val foo = Foo()
            val fooValue = foo.value
            bar.calculateValue()
            val barValue = bar.value
            val barImpl = BarImpl()
            barImpl.calculateValue()
            val barImplValue = barImpl.value
        }

        val localLambda2: () -> Unit = {
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

    fun test4(bar: Bar) {
        val localObject = object {
            val foo = Foo()
            val fooValue = foo.value
            val barValue = bar.value
            val barImpl = BarImpl()
            val barImplValue = barImpl.value
        }
    }
    """.trimIndent()

private val definitions =
    """
    @androidx.compose.runtime.annotation.FrequentlyChangingValue
    fun test(): Int = 5

    class Foo {
        @get:androidx.compose.runtime.annotation.FrequentlyChangingValue
        var value: Int = 5
    }

    interface Bar {
        @androidx.compose.runtime.annotation.FrequentlyChangingValue
        fun calculateValue(): Int

        @get:androidx.compose.runtime.annotation.FrequentlyChangingValue
        var value: Int
    }

    class BarImpl : Bar {
        override fun calculateValue(): Int = 5
        override var value: Int = 5
    }

    interface TopLevelWithoutAnnotation {
        var value: Int
    }

    abstract class InnerLevelWithAnnotation : TopLevelWithoutAnnotation {
        @get:androidx.compose.runtime.annotation.FrequentlyChangingValue
        override var value: Int = 5
    }

    class LowerLevelWithoutAnnotation : InnerLevelWithAnnotation() {
        override var value: Int = 5
    }
    """.trimIndent()
