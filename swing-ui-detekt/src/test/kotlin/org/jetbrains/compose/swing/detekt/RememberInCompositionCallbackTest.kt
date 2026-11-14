// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class RememberInCompositionCallbackTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("reported")
    fun `reports a factory that runs during composition`(body: String) {
        val code = source(body)
        val findings = RuntimeRuleLint.lint("RememberInComposition", code)
        assertEquals(listOf(8), findings.map { lineOf(code, it.entity.location.text.start) }, body)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notReported")
    fun `does not report a factory inside a callback`(body: String) {
        assertEquals(emptyList(), RuntimeRuleLint.lint("RememberInComposition", source(body)), body)
    }

    private fun source(body: String): String =
        """
        import androidx.compose.runtime.Composable

        @Composable fun Button(onClick: () -> Unit) {}
        class Holder { var onClick: () -> Unit = {} }
        val holder = Holder()
        fun later(action: () -> Unit) {}
        val screen: @Composable () -> Unit = {
            $body
        }
        """.trimIndent() + "\n\n" + RememberInCompositionFixtures.source

    private fun lineOf(
        code: String,
        offset: Int,
    ): Int = code.substring(0, offset).count { it == '\n' } + 1

    companion object {
        @JvmStatic
        fun reported(): List<Arguments> =
            listOf(
                Arguments.of("test()"),
                Arguments.of("val made = test()"),
            )

        @JvmStatic
        fun notReported(): List<Arguments> =
            listOf(
                Arguments.of("Button(onClick = { test() })"),
                Arguments.of("Button { test() }"),
                Arguments.of("later { test() }"),
                Arguments.of("val callback = { test() }"),
                Arguments.of("val callback: () -> Unit = { test() }"),
                Arguments.of("missing(onClick = { test() })"),
                Arguments.of("holder.onClick = { test() }"),
                Arguments.of("Button(onClick = fun() { test() })"),
            )
    }
}
