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

class RememberInCompositionNonComposableTest {
    @Test
    fun noErrors() {
        val findings = RuntimeRuleLint.lint("RememberInComposition", source)
        assertEquals(0, findings.size)
    }
}

private val source =
    """
    package androidx.compose.runtime.foo

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
    """.trimIndent() + "\n\n" + RememberInCompositionFixtures.source
