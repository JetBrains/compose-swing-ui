/*
 * Copyright 2023 The Android Open Source Project
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
import kotlin.test.assertEquals

class OpaqueUnitKeyDetectorTest {
    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun withUnitLiteralKey_doesNotError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(Unit) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(Unit) {\n        onDispose {\n            // Do nothing.\n        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(Unit) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\"123\", Unit) { /* Do nothing. */"
                }

                "key" -> {
                    "key(Unit) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            @Composable
            fun Test() {
                CALL_PLACEHOLDER }
            }
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(0, findings.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun withUnitPropertyRead_reportsError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(unitProperty) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(unitProperty) {\n        onDispose {\n            // Do nothing.\n" +
                        "        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(unitProperty) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\"123\", unitProperty) { /* Do nothing. */"
                }

                "key" -> {
                    "key(unitProperty) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val unitProperty = Unit

            @Composable
            fun Test() {
                CALL_PLACEHOLDER }
            }
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(1, findings.size)
        val expectedLine = 9
        assertEquals(
            expectedLine,
            source
                .substring(
                    0,
                    findings
                        .single()
                        .entity.location.text.start,
                ).count { it == '\n' } + 1,
        )
        val argument = if (effect == "key") "keys" else "key1"
        assertEquals("Implicitly passing `Unit` as argument to $argument", findings.single().message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun withUnitFunctionCall_reportsError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(produceUnit()) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(produceUnit()) {\n        onDispose {\n            // Do nothing.\n" +
                        "        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(produceUnit()) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\"123\", produceUnit()) { /* Do nothing. */"
                }

                "key" -> {
                    "key(produceUnit()) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            @Composable
            fun Test() {
                CALL_PLACEHOLDER }
            }

            fun produceUnit() {}
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(1, findings.size)
        val expectedLine = 7
        assertEquals(
            expectedLine,
            source
                .substring(
                    0,
                    findings
                        .single()
                        .entity.location.text.start,
                ).count { it == '\n' } + 1,
        )
        val argument = if (effect == "key") "keys" else "key1"
        assertEquals("Implicitly passing `Unit` as argument to $argument", findings.single().message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun withUnitComposableInvocation_reportsError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(AnotherComposable()) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(AnotherComposable()) {\n        onDispose {\n            // Do nothing.\n" +
                        "        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(AnotherComposable()) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\"123\", AnotherComposable()) { /* Do nothing. */"
                }

                "key" -> {
                    "key(AnotherComposable()) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            @Composable
            fun Test() {
                CALL_PLACEHOLDER }
            }

            @Composable
            fun AnotherComposable() {}
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(1, findings.size)
        val expectedLine = 7
        assertEquals(
            expectedLine,
            source
                .substring(
                    0,
                    findings
                        .single()
                        .entity.location.text.start,
                ).count { it == '\n' } + 1,
        )
        val argument = if (effect == "key") "keys" else "key1"
        assertEquals("Implicitly passing `Unit` as argument to $argument", findings.single().message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun withIfStatementThatReturnsUnit_reportsError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            doSomethingElse()\n        }\n    ) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            doSomethingElse()\n        }\n    ) {\n        onDispose {\n" +
                        "            // Do nothing.\n        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            doSomethingElse()\n        }\n    ) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\n        initialValue = \"123\",\n        if (condition) {\n" +
                        "            doSomething()\n        } else {\n            doSomethingElse()\n        }\n" +
                        "    ) { /* Do nothing. */"
                }

                "key" -> {
                    "key(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            doSomethingElse()\n        }\n    ) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            @Composable
            fun Test(condition: Boolean) {
                CALL_PLACEHOLDER }
            }

            fun doSomething() {}
            fun doSomethingElse() {}
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(1, findings.size)
        val expectedLine = if (effect == "produceState") 9 else 8
        assertEquals(
            expectedLine,
            source
                .substring(
                    0,
                    findings
                        .single()
                        .entity.location.text.start,
                ).count { it == '\n' } + 1,
        )
        val argument = if (effect == "key") "keys" else "key1"
        assertEquals("Implicitly passing `Unit` as argument to $argument", findings.single().message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun withIfStatementCoercedToAny_doesNotReportError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            42\n        }\n    ) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            42\n        }\n    ) {\n        onDispose {\n            // Do nothing.\n" +
                        "        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(\n        if (condition) {\n            doSomething()\n        } else {\n" +
                        "            42\n        }\n    ) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\n        initialValue = \"123\",\n        if (condition) {\n" +
                        "            doSomething()\n        } else {\n            42\n        }\n" +
                        "    ) { /* Do nothing */"
                }

                "key" -> {
                    "key(\n        if (condition) {\n            doSomething()\n        } else {\n            42\n" +
                        "        }\n    ) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            @Composable
            fun Test(condition: Boolean) {
                CALL_PLACEHOLDER }
            }

            fun doSomething() {}
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(0, findings.size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["disposableEffect", "key", "launchedEffect", "produceState", "remember"])
    fun twoKeys_withUnitFunctionCall_reportsError(effect: String) {
        val call =
            when (effect) {
                "remember" -> {
                    "val x = remember(42, produceUnit()) { listOf(1, 2, 3)"
                }

                "disposableEffect" -> {
                    "DisposableEffect(42, produceUnit()) {\n        onDispose {\n            // Do nothing.\n" +
                        "        }\n   "
                }

                "launchedEffect" -> {
                    "LaunchedEffect(42, produceUnit()) {\n        // Do nothing.\n   "
                }

                "produceState" -> {
                    "val x by produceState(\"123\", produceUnit()) { /* Do nothing */"
                }

                "key" -> {
                    "key(42, produceUnit()) {\n        // Do nothing.\n   "
                }

                else -> {
                    error("Unknown effect: $effect")
                }
            }
        val source =
            """
            package test

            import androidx.compose.runtime.*

            @Composable
            fun Test() {
                CALL_PLACEHOLDER }
            }

            fun produceUnit() {}
            """.trimIndent().replace("CALL_PLACEHOLDER", call)
        val findings = RuntimeRuleLint.lint("OpaqueUnitKey", source)
        assertEquals(1, findings.size)
        val expectedLine = 7
        assertEquals(
            expectedLine,
            source
                .substring(
                    0,
                    findings
                        .single()
                        .entity.location.text.start,
                ).count { it == '\n' } + 1,
        )
        val argument =
            when (effect) {
                "key" -> "keys"
                "produceState" -> "key1"
                else -> "key2"
            }
        assertEquals("Implicitly passing `Unit` as argument to $argument", findings.single().message)
    }
}
