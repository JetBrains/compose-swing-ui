// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RememberMissingStateCreationTest {
    @ParameterizedTest(name = "reports {0} in every composition context")
    @MethodSource("stateCreations")
    fun `reports state created during composition without remember`(
        label: String,
        creation: String,
        declarations: String,
    ) {
        val source = sourceOf(creation, declarations, composable = true)
        val lines = source.lines()

        val reported =
            RuntimeRuleLint
                .lint("RememberMissing", source)
                .map { lineOf(source, it.entity.location.text.start) }

        assertEquals(3, reported.size, label)
        assertEquals(reported.distinct(), reported, label)
        reported.forEach { line -> assertTrue(lines[line - 1].contains("val foo = "), "$label line $line") }
    }

    @ParameterizedTest(name = "accepts {0} outside composition")
    @MethodSource("stateCreations")
    fun `accepts state created outside composition`(
        label: String,
        creation: String,
        declarations: String,
    ) {
        val source = sourceOf(creation, declarations, composable = false)

        assertEquals(emptyList(), RuntimeRuleLint.lint("RememberMissing", source).map { it.message }, label)
    }

    @ParameterizedTest(name = "accepts remembered {0}")
    @MethodSource("stateCreations")
    fun `accepts state created inside remember`(
        label: String,
        creation: String,
        declarations: String,
    ) {
        val source = rememberedSourceOf(creation, declarations)

        assertEquals(emptyList(), RuntimeRuleLint.lint("RememberMissing", source).map { it.message }, label)
    }

    @ParameterizedTest(name = "accepts {0}")
    @MethodSource("uncreatedStates")
    fun `accepts a call that creates no state during composition`(
        label: String,
        declarations: String,
        body: String,
    ) {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*
            import androidx.compose.runtime.remember as held

            $declarations

            @Composable
            fun test() {
                $body
            }
            """.trimIndent()

        assertEquals(emptyList(), RuntimeRuleLint.lint("RememberMissing", source).map { it.message }, label)
    }

    @Test
    fun `states which factory was called without remember`() {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*

            @Composable
            fun test() {
                val foo = mutableStateOf(0)
            }
            """.trimIndent()

        val message = RuntimeRuleLint.lint("RememberMissing", source).single().message

        assertTrue(message.startsWith("This call to `mutableStateOf` creates state"))
        assertTrue(message.endsWith("Call `mutableStateOf` inside `remember`."))
    }

    @Test
    fun `reports a call in a nested composable function once`() {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*

            @Composable
            fun outer() {
                @Composable
                fun inner() {
                    val foo = mutableStateOf(0)
                }
                inner()
            }
            """.trimIndent()

        assertEquals(1, RuntimeRuleLint.lint("RememberMissing", source).size)
    }

    private fun lineOf(
        source: String,
        offset: Int,
    ): Int = source.substring(0, offset).count { it == '\n' } + 1

    companion object {
        @JvmStatic
        fun stateCreations(): Stream<Arguments> =
            Stream.of(
                Arguments.of("mutableStateOf", "mutableStateOf(0)", ""),
                Arguments.of("derivedStateOf", "derivedStateOf { 0 }", ""),
                Arguments.of("mutableStateListOf", "mutableStateListOf<Int>()", ""),
                Arguments.of("mutableStateMapOf", "mutableStateMapOf<Int, Float>()", ""),
                Arguments.of(
                    "a state factory annotated function",
                    "makeMyState()",
                    "@StateFactoryMarker fun makeMyState(): State<Nothing> = throw UnsupportedOperationException()",
                ),
                Arguments.of(
                    "a constructed State",
                    "CustomState<Nothing>()",
                    """
                    class CustomState<T> : State<T> {
                        override val value: T get() = throw UnsupportedOperationException()
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "an anonymous State",
                    "object : State<Int> { override val value: Int get() = 0 }",
                    "",
                ),
            )

        @JvmStatic
        fun uncreatedStates(): Stream<Arguments> =
            Stream.of(
                Arguments.of("an aliased remember", "", "val foo = held { mutableStateOf(0) }"),
                Arguments.of(
                    "a callback that composition does not run",
                    "fun Handler(onClick: () -> Unit) {}",
                    "Handler { val foo = mutableStateOf(0) }",
                ),
                Arguments.of("an effect block", "", "LaunchedEffect(Unit) { val foo = mutableStateOf(0) }"),
                Arguments.of(
                    "a function that is not the runtime's",
                    "fun mutableStateOf(value: Int): Int = value",
                    "val foo = mutableStateOf(0)",
                ),
            )

        private fun sourceOf(
            creation: String,
            declarations: String,
            composable: Boolean,
        ): String {
            val marker = if (composable) "@Composable " else ""
            val functionMarker = if (composable) "@Composable\n" else ""
            return """
                package androidx.compose.runtime.foo

                import androidx.compose.runtime.*
                import androidx.compose.runtime.snapshots.StateFactoryMarker

                ${functionMarker}fun test() {
                    val foo = $creation
                }

                val lambda = $marker{
                    val foo = $creation
                }

                ${functionMarker}fun test4() {
                    val localObject = object {
                        val foo = $creation
                    }
                }

                $declarations
                """.trimIndent()
        }

        private fun rememberedSourceOf(
            creation: String,
            declarations: String,
        ): String =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*
            import androidx.compose.runtime.snapshots.StateFactoryMarker

            @Composable
            fun test() {
                val foo = remember { $creation }
            }

            $declarations
            """.trimIndent()
    }
}
