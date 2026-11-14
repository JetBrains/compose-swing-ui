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

import kotlin.test.Test
import kotlin.test.assertEquals

class RememberInCompositionRememberedTest {
    @Test
    fun rememberedInsideComposableBody() {
        val findings = RuntimeRuleLint.lint("RememberInComposition", source)
        assertEquals(0, findings.size)
    }
}

private val source =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.*

    @Composable
    fun Test(bar: Bar) {
        val test = remember { test() }
        val foo = remember { Foo() }
        val fooValue = remember {
            val foo = Foo()
            foo.value
        }
        val barCalculateValue = remember(bar) {
            bar.calculateValue()
        }
        val barValue = remember(bar) {
            bar.value
        }
        val barImplCalculateValue = remember {
            val barImpl = BarImpl()
            barImpl.calculateValue()
        }
        val barImplValue = remember {
            val barImpl = BarImpl()
            barImpl.value
        }
    }

    val lambda = @Composable { bar: Bar ->
        val test = remember { test() }
        val foo = remember { Foo() }
        val fooValue = remember {
            val foo = Foo()
            foo.value
        }
        val barCalculateValue = remember(bar) {
            bar.calculateValue()
        }
        val barValue = remember(bar) {
            bar.value
        }
        val barImplCalculateValue = remember {
            val barImpl = BarImpl()
            barImpl.calculateValue()
        }
        val barImplValue = remember {
            val barImpl = BarImpl()
            barImpl.value
        }
    }

    val lambda2: @Composable (bar: Bar) -> Unit = { bar ->
        val test = remember { test() }
        val foo = remember { Foo() }
        val fooValue = remember {
            val foo = Foo()
            foo.value
        }
        val barCalculateValue = remember(bar) {
            bar.calculateValue()
        }
        val barValue = remember(bar) {
            bar.value
        }
        val barImplCalculateValue = remember {
            val barImpl = BarImpl()
            barImpl.calculateValue()
        }
        val barImplValue = remember {
            val barImpl = BarImpl()
            barImpl.value
        }
    }

    @Composable
    fun LambdaParameter(content: @Composable () -> Unit) {}

    @Composable
    fun Test2(bar: Bar) {
        LambdaParameter(content = {
            val test = remember { test() }
            val foo = remember { Foo() }
            val fooValue = remember {
                val foo = Foo()
                foo.value
            }
            val barCalculateValue = remember(bar) {
                bar.calculateValue()
            }
            val barValue = remember(bar) {
                bar.value
            }
            val barImplCalculateValue = remember {
                val barImpl = BarImpl()
                barImpl.calculateValue()
            }
            val barImplValue = remember {
                val barImpl = BarImpl()
                barImpl.value
            }
        })
        LambdaParameter {
            val test = remember { test() }
            val foo = remember { Foo() }
            val fooValue = remember {
                val foo = Foo()
                foo.value
            }
            val barCalculateValue = remember(bar) {
                bar.calculateValue()
            }
            val barValue = remember(bar) {
                bar.value
            }
            val barImplCalculateValue = remember {
                val barImpl = BarImpl()
                barImpl.calculateValue()
            }
            val barImplValue = remember {
                val barImpl = BarImpl()
                barImpl.value
            }
        }
    }

    fun test3(bar: Bar) {
        val localLambda1 = @Composable {
            val test = remember { test() }
            val foo = remember { Foo() }
            val fooValue = remember {
                val foo = Foo()
                foo.value
            }
            val barCalculateValue = remember(bar) {
                bar.calculateValue()
            }
            val barValue = remember(bar) {
                bar.value
            }
            val barImplCalculateValue = remember {
                val barImpl = BarImpl()
                barImpl.calculateValue()
            }
            val barImplValue = remember {
                val barImpl = BarImpl()
                barImpl.value
            }
        }

        val localLambda2: @Composable () -> Unit = {
            val test = remember { test() }
            val foo = remember { Foo() }
            val fooValue = remember {
                val foo = Foo()
                foo.value
            }
            val barCalculateValue = remember(bar) {
                bar.calculateValue()
            }
            val barValue = remember(bar) {
                bar.value
            }
            val barImplCalculateValue = remember {
                val barImpl = BarImpl()
                barImpl.calculateValue()
            }
            val barImplValue = remember {
                val barImpl = BarImpl()
                barImpl.value
            }
        }
    }
    """.trimIndent() + "\n\n" + RememberInCompositionFixtures.source
