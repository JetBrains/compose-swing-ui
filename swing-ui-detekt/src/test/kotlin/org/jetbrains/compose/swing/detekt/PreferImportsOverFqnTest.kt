// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PreferImportsOverFqnTest {
    private fun lint(code: String) =
        rule().lintWithContext(
            environment,
            code,
            """
            package org.jetbrains.compose.swing.node

            public fun SwingNode(update: () -> Unit = {}) {}
            """.trimIndent(),
        )

    private fun rule(): AnalysisComposeSwingRuleAdapter {
        val factory = ComposeSwingInternalRuleSetProvider().instance().rules.getValue(RuleName("PreferImportsOverFqn"))
        return assertIs<AnalysisComposeSwingRuleAdapter>(factory(Config.empty))
    }

    private companion object {
        private val environment = createEnvironment()
    }

    @Test
    fun `reports direct FQN in type reference`() {
        val findings =
            lint(
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
            lint(
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
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.sample.Widget

                class Demo(val value: Widget)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report an unresolved qualified call`() {
        val findings =
            lint(
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
            lint(
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
            lint(
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
            lint(
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
            lint(
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
            lint(
                """
                package sample

                class Demo(val value: org.jetbrains.compose.swing.sample.Widget?)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a package-qualified call`() {
        val findings =
            lint(
                """
                package sample

                fun show() {
                    org.jetbrains.compose.swing.node.SwingNode()
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf("Use import instead of direct FQN: org.jetbrains.compose.swing.node.SwingNode"),
            findings.map { it.message },
        )
    }

    @Test
    fun `reports a package-qualified enum or property reference once`() {
        val findings =
            lint(
                """
                package sample

                val color = java.awt.Color.RED
                val mode = java.util.concurrent.TimeUnit.SECONDS
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                "Use import instead of direct FQN: java.awt.Color",
                "Use import instead of direct FQN: java.util.concurrent.TimeUnit",
            ),
            findings.map { it.message },
        )
    }

    @Test
    fun `reports a package-qualified call inside a lambda`() {
        val findings =
            lint(
                """
                package sample

                fun run(block: () -> Unit) = block()

                fun show() {
                    run { org.jetbrains.compose.swing.node.SwingNode() }
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf("Use import instead of direct FQN: org.jetbrains.compose.swing.node.SwingNode"),
            findings.map { it.message },
        )
    }

    @Test
    fun `does not report a chain rooted at a value`() {
        val findings =
            lint(
                """
                package sample

                object Registry { val items = listOf(1) }

                class Holder(val items: List<Int>) {
                    fun size(other: Holder) = items.size + this.items.size + other.items.size + Registry.items.size
                }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report a qualified name in an import directive`() {
        val findings =
            lint(
                """
                package sample

                import java.awt.Color
                import org.jetbrains.compose.swing.node.SwingNode

                val color = Color.RED
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report a package-qualified call whose package declares a same-named extension`() {
        val findings =
            rule().lintWithContext(
                environment,
                """
                package sample

                fun show() {
                    org.jetbrains.compose.swing.scoped.Reveal()
                }
                """.trimIndent(),
                """
                package org.jetbrains.compose.swing.scoped

                public class Scope

                public fun Reveal() {}

                public fun Scope.Reveal() {}
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `reports a package-qualified property whose package declares a same-named extension`() {
        val findings =
            rule().lintWithContext(
                environment,
                """
                package sample

                fun show() = org.jetbrains.compose.swing.scoped.status
                """.trimIndent(),
                """
                package org.jetbrains.compose.swing.scoped

                public val status: Int = 1

                public fun String.status() {}
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }
}
