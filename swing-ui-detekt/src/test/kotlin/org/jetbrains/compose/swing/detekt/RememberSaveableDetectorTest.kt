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

import kotlin.test.Test
import kotlin.test.assertEquals

class RememberSaveableDetectorTest {
    @Test
    fun saverPassedToVarargs() {
        val source =
            """
            package test
            import androidx.compose.runtime.*
            import androidx.compose.runtime.saveable.*
            class Foo
            object FooSaver : Saver<Foo, Any> by Saver(save = { it }, restore = { it as Foo })
            class FooSaver2 : Saver<Foo, Any> by Saver(save = { it }, restore = { it as Foo })
            val fooSaver3 = object : Saver<Foo, Any> by Saver(save = { it }, restore = { it as Foo }) {}
            val fooSaver4 = FooSaver2()
            @Composable
            fun test() {
                val foo = rememberSaveable(FooSaver) { Foo() }
                val mutableStateFoo = rememberSaveable(FooSaver) { mutableStateOf(Foo()) }
                val foo2 = rememberSaveable(FooSaver2()) { Foo() }
                val mutableStateFoo2 = rememberSaveable(FooSaver2()) { mutableStateOf(Foo()) }
                val foo3 = rememberSaveable(fooSaver3) { Foo() }
                val mutableStateFoo3 = rememberSaveable(fooSaver3) { mutableStateOf(Foo()) }
                val foo4 = rememberSaveable(fooSaver4) { Foo() }
                val mutableStateFoo4 = rememberSaveable(fooSaver4) { mutableStateOf(Foo()) }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RememberSaveableSaverParameter", source)
        assertEquals(8, findings.size)
        listOf("FooSaver", "FooSaver", "FooSaver2()", "FooSaver2()", "fooSaver3", "fooSaver3", "fooSaver4", "fooSaver4")
            .forEachIndexed { index, expected ->
                val finding = findings[index]
                val location = finding.entity.location.text
                assertEquals(expected, source.substring(location.start, location.end))
                assertEquals(11 + index, source.substring(0, location.start).count { it == '\n' } + 1)
                assertEquals(
                    "Passing `Saver` instance to vararg `inputs`",
                    finding.message,
                )
            }
    }

    @Test
    fun noErrors() {
        val source =
            """
            package test
            import androidx.compose.runtime.*
            import androidx.compose.runtime.saveable.*
            class Foo
            object FooSaver : Saver<Foo, Any> by Saver(save = { it }, restore = { it as Foo })
            class FooSaver2 : Saver<Foo, Any> by Saver(save = { it }, restore = { it as Foo })
            val fooSaver3 = object : Saver<Foo, Any> by Saver(save = { it }, restore = { it as Foo }) {}
            val fooSaver4 = FooSaver2()
            @Composable
            fun test() {
                val foo = rememberSaveable(saver = FooSaver) { Foo() }
                val mutableStateFoo = rememberSaveable(stateSaver = FooSaver) { mutableStateOf(Foo()) }
                val foo2 = rememberSaveable(saver = FooSaver2()) { Foo() }
                val mutableStateFoo2 = rememberSaveable(stateSaver = FooSaver2()) { mutableStateOf(Foo()) }
                val foo3 = rememberSaveable(saver = fooSaver3) { Foo() }
                val mutableStateFoo3 = rememberSaveable(stateSaver = fooSaver3) { mutableStateOf(Foo()) }
                val foo4 = rememberSaveable(saver = fooSaver4) { Foo() }
                val mutableStateFoo4 = rememberSaveable(stateSaver = fooSaver4) { mutableStateOf(Foo()) }
                val fooVarargs = rememberSaveable(Any(), FooSaver, Any()) { Foo() }
                val mutableStateFooVarargs = rememberSaveable(Any(), FooSaver, Any()) { mutableStateOf(Foo()) }
            }
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("RememberSaveableSaverParameter", source).size)
    }
}
