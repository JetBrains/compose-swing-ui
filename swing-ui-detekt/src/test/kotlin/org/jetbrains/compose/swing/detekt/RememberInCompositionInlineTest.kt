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

class RememberInCompositionInlineTest {
    @Test
    fun notRemembered_inlineFunctions() {
        val findings = RuntimeRuleLint.lint("RememberInComposition", source)
        assertEquals(24, findings.size)
        val expectedLines =
            listOf(
                13,
                14,
                15,
                23,
                24,
                25,
                33,
                34,
                35,
                48,
                49,
                50,
                57,
                58,
                59,
                69,
                70,
                71,
                79,
                80,
                81,
                92,
                93,
                94,
            )
        findings.zip(expectedLines).forEach { (finding, line) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
        }
    }
}

private val source =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.*

    inline fun <T> runBlock(block: () -> T) {}
    inline fun <T> runBlockCrossinline(crossinline block: () -> T) {}
    inline fun <T> runBlockNoinline(noinline block: () -> T) {}
    @Composable fun <T> runBlockComposable(block : () -> T) {}
    fun <T> runBlockNotInline(block: () -> T) {}

    @Composable
    fun Test(bar: Bar) {
        val foo = true.let { Foo() }
        val foo2 = listOf(1).map { Foo() }
        val foo3 = runBlock { Foo() }
        val foo4 = runBlockCrossinline { Foo() }
        val foo5 = runBlockNoinline { Foo() }
        val foo6 = runBlockComposable { Foo() }
        val foo7 = runBlockNotInline { Foo() }
    }

    val lambda = @Composable { bar: Bar ->
        val foo = true.let { Foo() }
        val foo2 = listOf(1).map { Foo() }
        val foo3 = runBlock { Foo() }
        val foo4 = runBlockCrossinline { Foo() }
        val foo5 = runBlockNoinline { Foo() }
        val foo6 = runBlockComposable { Foo() }
        val foo7 = runBlockNotInline { Foo() }
    }

    val lambda2: @Composable (bar: Bar) -> Unit = { bar ->
        val foo = true.let { Foo() }
        val foo2 = listOf(1).map { Foo() }
        val foo3 = runBlock { Foo() }
        val foo4 = runBlockCrossinline { Foo() }
        val foo5 = runBlockNoinline { Foo() }
        val foo6 = runBlockComposable { Foo() }
        val foo7 = runBlockNotInline { Foo() }
    }

    @Composable
    fun LambdaParameter(content: @Composable () -> Unit) {}

    @Composable
    fun Test2(bar: Bar) {
        LambdaParameter(content = {
            val foo = true.let { Foo() }
            val foo2 = listOf(1).map { Foo() }
            val foo3 = runBlock { Foo() }
            val foo4 = runBlockCrossinline { Foo() }
            val foo5 = runBlockNoinline { Foo() }
            val foo6 = runBlockComposable { Foo() }
            val foo7 = runBlockNotInline { Foo() }
        })
        LambdaParameter {
            val foo = true.let { Foo() }
            val foo2 = listOf(1).map { Foo() }
            val foo3 = runBlock { Foo() }
            val foo4 = runBlockCrossinline { Foo() }
            val foo5 = runBlockNoinline { Foo() }
            val foo6 = runBlockComposable { Foo() }
            val foo7 = runBlockNotInline { Foo() }
        }
    }

    fun test3(bar: Bar) {
        val localLambda1 = @Composable {
            val foo = true.let { Foo() }
            val foo2 = listOf(1).map { Foo() }
            val foo3 = runBlock { Foo() }
            val foo4 = runBlockCrossinline { Foo() }
            val foo5 = runBlockNoinline { Foo() }
            val foo6 = runBlockComposable { Foo() }
            val foo7 = runBlockNotInline { Foo() }
        }

        val localLambda2: @Composable () -> Unit = {
            val foo = true.let { Foo() }
            val foo2 = listOf(1).map { Foo() }
            val foo3 = runBlock { Foo() }
            val foo4 = runBlockCrossinline { Foo() }
            val foo5 = runBlockNoinline { Foo() }
            val foo6 = runBlockComposable { Foo() }
            val foo7 = runBlockNotInline { Foo() }
        }
    }

    @Composable
    fun Test4(bar: Bar) {
        val localObject = object {
            val foo = true.let { Foo() }
            val foo2 = listOf(1).map { Foo() }
            val foo3 = runBlock { Foo() }
            val foo4 = runBlockCrossinline { Foo() }
            val foo5 = runBlockNoinline { Foo() }
            val foo6 = runBlockComposable { Foo() }
            val foo7 = runBlockNotInline { Foo() }
        }
    }
    """.trimIndent() + "\n\n" + RememberInCompositionFixtures.source
