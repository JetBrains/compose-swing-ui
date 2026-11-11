// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferImportsOverFqnTest {
    @Test
    fun `reports direct FQN in type reference`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                class Demo(val value: org.jetbrains.compose.swing.sample.Widget)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertEquals(
            "Use import instead of direct FQN: org.jetbrains.compose.swing.sample.Widget",
            findings.single().message,
        )
    }

    @Test
    fun `reports direct FQN for a nested type reference`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                class Demo(val value: java.util.Map.Entry<String, String>)
                """.trimIndent(),
            )

        assertEquals(
            listOf("Use import instead of direct FQN: java.util.Map.Entry"),
            findings.map { it.message },
        )
    }

    @Test
    fun `does not report when type is imported`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                import org.jetbrains.compose.swing.sample.Widget

                class Demo(val value: Widget)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not inspect expression call chains`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                fun serialize(model: Any, output: java.io.File) {
                    sample.serialization.ModelSerializer.serialize(model, output)
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf("Use import instead of direct FQN: java.io.File"),
            findings.map { it.message },
        )
    }

    @Test
    fun `does not report regular member access chain`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                fun path(project: Any) {
                    project.toString()
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report member chains rooted at a value`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                fun serialize(namespace: Namespace) {
                    namespace.Serializer.serialize()
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report FQN text inside string literals`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                val value = "org.jetbrains.compose.swing.sample.Widget"
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `supports suppression by rule id`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                @Suppress("PreferImportsOverFqn")
                class Demo(val value: org.jetbrains.compose.swing.sample.Widget)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports a fully qualified nullable type`() {
        val findings =
            PreferImportsOverFqn(Config.empty).lint(
                """
                package sample

                class Demo(val value: org.jetbrains.compose.swing.sample.Widget?)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }
}
