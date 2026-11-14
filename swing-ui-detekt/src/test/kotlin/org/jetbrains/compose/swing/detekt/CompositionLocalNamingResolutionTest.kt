// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositionLocalNamingResolutionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("declarations")
    fun `reports each composition local property whose name lacks the Local prefix once`(
        label: String,
        declarations: String,
        expected: List<String>,
    ) {
        val source = sourceOf(declarations)

        val reported =
            RuntimeRuleLint
                .lint("CompositionLocalNaming", source)
                .map { source.substring(it.entity.location.text.start, it.entity.location.text.end) }

        assertEquals(expected.sorted(), reported.sorted(), label)
    }

    @Test
    fun `reports a resolved property with the message of a property recognized by name`() {
        val source = sourceOf("val FooCompositionLocal = compositionLocalOf { 5 }\nval theme = makeThemeLocal()")

        val messages = RuntimeRuleLint.lint("CompositionLocalNaming", source).map { it.message }

        assertEquals(2, messages.size)
        assertEquals(1, messages.toSet().size)
    }

    companion object {
        @JvmStatic
        fun declarations(): Stream<Arguments> =
            Stream.of(resolvedTypes(), androidxCases(), ignoredDeclarations()).flatMap { it }

        private fun resolvedTypes(): Stream<Arguments> =
            Stream.of(
                Arguments.of("a helper result", "val theme = makeThemeLocal()", listOf("theme")),
                Arguments.of(
                    "a helper result typed by an alias",
                    "val theme: ThemeLocal = makeThemeLocal()",
                    listOf("theme"),
                ),
                Arguments.of(
                    "a delegated helper result",
                    "val theme by lazy { makeThemeLocal() }",
                    listOf("theme"),
                ),
                Arguments.of(
                    "an object member",
                    "object Holder { val theme = makeThemeLocal() }",
                    listOf("theme"),
                ),
                Arguments.of(
                    "a lowercase prefix",
                    "val localTheme = makeThemeLocal()",
                    listOf("localTheme"),
                ),
                Arguments.of(
                    "a lowercase prefix on a factory",
                    "val localTheme = compositionLocalOf { 5 }",
                    listOf("localTheme"),
                ),
                Arguments.of(
                    "a factory call and a helper result together",
                    "val FooCompositionLocal = compositionLocalOf { 5 }\nval theme = makeThemeLocal()",
                    listOf("FooCompositionLocal", "theme"),
                ),
            )

        private fun androidxCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "androidx's unprefixed properties",
                    """
                    val FooCompositionLocal = compositionLocalOf { 5 }

                    object Test {
                        val BarCompositionLocal: CompositionLocal<String?> = staticCompositionLocalOf {
                            null
                        }
                    }

                    class Test2 {
                        companion object {
                            val BazCompositionLocal: ProvidableCompositionLocal<Int> =
                                compositionLocalOf { 0 }
                        }
                    }
                    """.trimIndent(),
                    listOf("FooCompositionLocal", "BarCompositionLocal", "BazCompositionLocal"),
                ),
                Arguments.of(
                    "androidx's prefixed properties",
                    """
                    val LocalFoo = compositionLocalOf { 5 }

                    object Test {
                        val LocalBar: CompositionLocal<String?> = staticCompositionLocalOf { null }
                    }

                    class Test2 {
                        companion object {
                            val LocalBaz: ProvidableCompositionLocal<Int> = compositionLocalOf { 0 }
                        }
                    }
                    """.trimIndent(),
                    emptyList<String>(),
                ),
            )

        private fun ignoredDeclarations(): Stream<Arguments> =
            Stream.of(
                Arguments.of("a prefixed helper result", "val LocalTheme = makeThemeLocal()", emptyList<String>()),
                Arguments.of(
                    "a local property",
                    "fun use() { val theme = makeThemeLocal() }",
                    emptyList<String>(),
                ),
                Arguments.of(
                    "a function parameter",
                    "fun use(theme: CompositionLocal<Theme>) {}",
                    emptyList<String>(),
                ),
                Arguments.of(
                    "a constructor property",
                    "class Holder(val theme: CompositionLocal<Theme>)",
                    emptyList<String>(),
                ),
                Arguments.of("a property of another type", "val theme = Theme()", emptyList<String>()),
                Arguments.of(
                    "a local factory result",
                    "fun use() { val theme = compositionLocalOf { 5 } }",
                    emptyList<String>(),
                ),
                Arguments.of(
                    "a factory of another library",
                    "fun compositionLocalOf(value: () -> Int): Int = value()\nval theme = compositionLocalOf { 42 }",
                    emptyList<String>(),
                ),
            )

        private fun sourceOf(declarations: String): String =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.*

            class Theme
            fun makeThemeLocal(): ProvidableCompositionLocal<Theme> = compositionLocalOf { Theme() }
            typealias ThemeLocal = ProvidableCompositionLocal<Theme>
            """.trimIndent() + "\n\n$declarations\n"
    }
}
