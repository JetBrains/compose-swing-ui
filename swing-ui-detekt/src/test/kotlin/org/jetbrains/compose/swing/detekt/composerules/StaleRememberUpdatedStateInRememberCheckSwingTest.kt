// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.composerules.StaleRememberUpdatedStateInRemember
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaleRememberUpdatedStateInRememberCheckSwingTest {
    private val rule = StaleRememberUpdatedStateInRemember.toDetektRule(Config.empty)

    @Test
    fun `reports a stale read inside a remember calculation passed as a parenthesized lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember({
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        assertSingleStaleRead(rule.lintWithAnalysisApi(code), line = 8, column = 24)
    }

    @Test
    fun `reports a stale read when rememberSaveable has a named key argument`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = rememberSaveable(key = "holder", init = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        assertSingleStaleRead(rule.lintWithAnalysisApi(code), line = 8, column = 24, cacheFunction = "rememberSaveable")
    }

    @Test
    fun `reports a stale read when the key array is mutated with set before remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                keys.set(0, {})
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        assertSingleStaleRead(rule.lintWithAnalysisApi(code), line = 10, column = 24)
    }

    @Test
    fun `reports a stale read inside a composable lambda of a non composable function`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            fun host(content: @Composable () -> Unit) = Unit

            fun setup(onDismiss: () -> Unit) {
                host {
                    val latestOnDismiss by rememberUpdatedState(onDismiss)
                    val holder = remember {
                        Holder(latestOnDismiss)
                    }
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        assertSingleStaleRead(rule.lintWithAnalysisApi(code), line = 10, column = 28)
    }

    @ParameterizedTest
    @MethodSource("delegatedKeysUnrelatedToSource")
    fun `reports a delegated read when remember is keyed by a value unrelated to the source`(body: String) {
        assertSingleStaleRead(lintExample(body))
    }

    @ParameterizedTest
    @MethodSource("stateValueKeysUnrelatedToSource")
    fun `reports a state value read when remember is keyed by a value unrelated to the source`(body: String) {
        assertSingleStaleRead(lintExample(body))
    }

    @Test
    fun `reports a delegated read when the source is a call and the key is a variable`() {
        val body =
            """
            val latestKey by rememberUpdatedState(createKey())
            val box = remember(settings) {
                Box(latestKey)
            }
            """.trimIndent()

        assertSingleStaleRead(lintExample(body))
    }

    @ParameterizedTest
    @MethodSource("spreadKeysWithUnknownContents")
    fun `reports a stale read when the spread key array contents are unknown`(body: String) {
        assertSingleStaleRead(lintExample(body))
    }

    @ParameterizedTest
    @MethodSource("calculationsThatAreNotLambdaLiterals")
    fun `does not report when the remember calculation is not a lambda literal`(body: String) {
        assertTrue(lintExample(body).isEmpty())
    }

    @ParameterizedTest
    @MethodSource("statementsAfterRemember")
    fun `does not report when the key array is mutated only after remember`(statement: String) {
        assertTrue(lintExample(keyArrayBody(before = "", after = statement)).isEmpty())
    }

    @ParameterizedTest
    @MethodSource("statementsThatDoNotMutateTheKeyArray")
    fun `does not report when statements before remember do not mutate the key array`(statement: String) {
        assertTrue(lintExample(keyArrayBody(before = statement, after = "")).isEmpty())
    }

    @Test
    fun `does not report when a local function declared before remember mutates the key array`() {
        val statement = "fun mutateKeys() {\n    sourceKeys[0] = {}\n}"

        assertTrue(lintExample(keyArrayBody(before = statement, after = "")).isEmpty())
    }

    @Test
    fun `does not report a state object used for something other than its value`() {
        val body =
            """
            val latestOnDismiss = rememberUpdatedState(onDismiss)
            val holder = remember {
                Box(latestOnDismiss.toString())
            }
            """.trimIndent()

        assertTrue(lintExample(body).isEmpty())
    }

    @Test
    fun `does not report a plain property copy that is not a rememberUpdatedState`() {
        val body =
            """
            val inner = settings.inner
            val holder = remember {
                Box(inner)
            }
            """.trimIndent()

        assertTrue(lintExample(body).isEmpty())
    }

    @Test
    fun `does not report a read inside a lambda passed to a supertype constructor`() {
        val body =
            """
            val latestOnDismiss by rememberUpdatedState(onDismiss)
            val holder = remember {
                object : Base({ latestOnDismiss() }) {}
            }
            """.trimIndent()

        assertTrue(lintExample(body).isEmpty())
    }

    private fun lintExample(body: String): List<Finding> {
        val source =
            listOf(
                "@Composable",
                "fun Example(onDismiss: () -> Unit, other: State<Int>, " +
                    "settings: Settings, slots: Slots, keys: Array<Any?>) {",
                body.prependIndent("    "),
                "}",
                "",
                listOf(
                    "class Holder(val onDismiss: () -> Unit)",
                    "class Box(val key: Any)",
                    "class Inner(val name: String)",
                    "class Settings(val inner: Inner, val size: Int) { fun compute(): Int = size }",
                    "class Slots(val items: Array<() -> Unit>)",
                    "open class Base(val action: () -> Unit)",
                    "fun createKey(): Any = Unit",
                    "fun createKeys(): Array<Any?> = arrayOf()",
                    "fun makeSettings(): Settings = Settings(Inner(\"\"), 0)",
                    "fun set(index: Int, value: () -> Unit) = Unit",
                    "val sharedKeys: Array<Any?> = arrayOf(Unit)",
                ).joinToString("\n"),
            ).joinToString("\n")
        return rule.lintWithAnalysisApi(codeWithFakeCompose(source))
    }

    private fun assertSingleStaleRead(
        findings: List<Finding>,
        line: Int? = null,
        column: Int? = null,
        cacheFunction: String = "remember",
    ) {
        val finding = findings.single()
        assertEquals(
            expectedStaleReadMessage(cacheFunction),
            finding.message,
        )
        if (line != null && column != null) {
            assertEquals(SourceLocation(line, column), finding.entity.location.source)
        }
    }

    companion object {
        private fun keyArrayBody(
            before: String,
            after: String,
        ): String =
            listOf(
                "val latestOnDismiss by rememberUpdatedState(onDismiss)",
                "val sourceKeys = arrayOf(onDismiss)",
                before,
                "val holder = remember(keys = sourceKeys, calculation = {",
                "    Holder(latestOnDismiss)",
                "})",
                after,
            ).joinToString("\n")

        @JvmStatic
        fun delegatedKeysUnrelatedToSource(): List<String> =
            listOf(
                "createKey()",
                "Int.MAX_VALUE",
                "settings.compute()",
                "makeSettings().size",
                "makeSettings().inner.name",
            ).map { key ->
                """
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember($key) {
                    Holder(latestOnDismiss)
                }
                """.trimIndent()
            }

        @JvmStatic
        fun stateValueKeysUnrelatedToSource(): List<String> =
            listOf(
                "settings",
                "other.value",
                "settings.inner.name",
                "latestOnDismiss.toString()",
            ).map { key ->
                """
                val latestOnDismiss = rememberUpdatedState(onDismiss)
                val holder = remember($key) {
                    Holder(latestOnDismiss.value)
                }
                """.trimIndent()
            }

        @JvmStatic
        fun spreadKeysWithUnknownContents(): List<String> =
            listOf(
                "keys",
                "sharedKeys",
                "createKeys()",
            ).map { keys ->
                """
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember(*$keys) {
                    Holder(latestOnDismiss)
                }
                """.trimIndent()
            }

        @JvmStatic
        fun calculationsThatAreNotLambdaLiterals(): List<String> =
            listOf(
                "calculation",
                "calculation = calculation",
            ).map { arguments ->
                """
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val calculation = { Holder(latestOnDismiss) }
                val holder = remember($arguments)
                """.trimIndent()
            }

        @JvmStatic
        fun statementsAfterRemember(): List<String> =
            listOf(
                "sourceKeys[0] = {}",
                "sourceKeys.set(0, {})",
            )

        @JvmStatic
        fun statementsThatDoNotMutateTheKeyArray(): List<String> =
            listOf(
                "val other = arrayOf(onDismiss)\nother[0] = {}",
                "var count = 0\ncount += 1",
                "var count = 0\ncount = 1",
                "slots.items[0] = {}",
                "val other = arrayOf(onDismiss)\nother.set(0, {})",
                "slots.items.set(0, {})",
                "sourceKeys.size.toString()",
                "sourceKeys.first().toString()",
                "set(0, {})",
            )
    }
}
