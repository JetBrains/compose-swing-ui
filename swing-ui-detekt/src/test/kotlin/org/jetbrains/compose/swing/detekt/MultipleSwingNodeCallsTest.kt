// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MultipleSwingNodeCallsTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports each node call after the first`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        val source = "$HEADER\n$code"
        assertEquals(positions, positionsIn(source), label)
    }

    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report a single node call`(
        label: String,
        code: String,
    ) {
        val source = "$HEADER\n$code"
        assertEquals(emptyList(), positionsIn(source), label)
    }

    @Test
    fun `names the callee and the function`() {
        val code = "@Composable fun Show() { SwingNode { JPanel() }; MenuNode { JPanel() } }"

        val finding = lint("$HEADER\n$code").single()

        assertEquals(
            "`Show` builds its layout from raw Swing components. A Swing component placed directly in a layout " +
                "keeps its layout, state and listeners out of the composition's reach. " +
                "Give each component its own composable that calls `MenuNode` once, " +
                "and build `Show` from component composables and the library's layouts.",
            finding.message,
        )
    }

    private fun positionsIn(source: String): List<String> =
        lint(source).map { positionOf(source, it.entity.location.text.start, it.entity.location.text.end) }

    private fun lint(source: String) =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("MultipleSwingNodeCalls"))
            .lintWithContext(
                environment,
                source,
                COMPOSABLE_STUBS,
                """
package org.jetbrains.compose.swing.node

import java.awt.Component

public fun <T : Component> SwingNode(factory: () -> T) {}

public fun <T : Component> SwingNode(factory: () -> T, content: () -> Unit) {}

public fun <T : Component> MenuNode(factory: () -> T) {}

public fun <T : Component> ExistingSwingNode(component: T) {}
""",
                """
package other

import javax.swing.JPanel

fun SwingNode(factory: () -> JPanel) {}
""",
            )

    companion object {
        private val environment = createEnvironment()

        private const val HEADER = """
package sample

import androidx.compose.runtime.Composable
import javax.swing.JPanel
import org.jetbrains.compose.swing.node.ExistingSwingNode
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.SwingNode
"""

        @JvmStatic
        fun reported(): List<Arguments> = reportedInSequence() + reportedNested()

        private fun reportedInSequence(): List<Arguments> =
            listOf(
                Arguments.of(
                    "the second of two sequential calls",
                    """
                    @Composable fun Show() {
                        SwingNode { JPanel() }
                        SwingNode { JPanel() }
                    }
                    """.trimIndent(),
                    listOf("SwingNode { JPanel() } #2"),
                ),
                Arguments.of(
                    "each call after the first of three",
                    """
                    @Composable fun Show() {
                        SwingNode { JPanel() }
                        SwingNode { JPanel() }
                        SwingNode { JPanel() }
                    }
                    """.trimIndent(),
                    listOf("SwingNode { JPanel() } #2", "SwingNode { JPanel() } #3"),
                ),
                Arguments.of(
                    "calls in two when branches",
                    """
                    @Composable fun Show(a: Boolean) {
                        when (a) {
                            true -> SwingNode { JPanel() }
                            false -> MenuNode { JPanel() }
                        }
                    }
                    """.trimIndent(),
                    listOf("MenuNode { JPanel() }"),
                ),
                Arguments.of(
                    "calls in two if branches",
                    """
                    @Composable fun Show(flag: Boolean) {
                        if (flag) SwingNode { JPanel() } else SwingNode { JPanel() }
                    }
                    """.trimIndent(),
                    listOf("SwingNode { JPanel() } #2"),
                ),
            )

        private fun reportedNested(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a call in the content lambda of another node",
                    """
                    @Composable fun Show() {
                        SwingNode({ JPanel() }) {
                            SwingNode { JPanel() }
                        }
                    }
                    """.trimIndent(),
                    listOf("SwingNode { JPanel() }"),
                ),
                Arguments.of(
                    "a MenuNode followed by a SwingNode",
                    """
                    @Composable fun Show() {
                        MenuNode { JPanel() }
                        SwingNode { JPanel() }
                    }
                    """.trimIndent(),
                    listOf("SwingNode { JPanel() }"),
                ),
                Arguments.of(
                    "a composable getter",
                    """
                    val shown: Unit
                        @Composable get() {
                            SwingNode { JPanel() }
                            SwingNode { JPanel() }
                        }
                    """.trimIndent(),
                    listOf("SwingNode { JPanel() } #2"),
                ),
            )

        @JvmStatic
        fun passed(): List<Arguments> =
            listOf(
                Arguments.of("one call", "@Composable fun Show() { SwingNode { JPanel() } }"),
                Arguments.of(
                    "one call inside a loop",
                    """
                    @Composable fun Show() {
                        for (i in 0..2) {
                            SwingNode { JPanel() }
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a call inside an anonymous object",
                    """
                    @Composable fun Show() {
                        SwingNode { JPanel() }
                        val holder = object {
                            fun make() { SwingNode { JPanel() } }
                        }
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "two calls split across two local composable functions",
                    """
                    @Composable fun Show() {
                        @Composable fun First() { SwingNode { JPanel() } }
                        @Composable fun Second() { SwingNode { JPanel() } }
                        First()
                        Second()
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "a function that is not composable",
                    "fun show() { SwingNode { JPanel() }; SwingNode { JPanel() } }",
                ),
                Arguments.of(
                    "an ExistingSwingNode and a SwingNode",
                    "@Composable fun Show() { ExistingSwingNode(JPanel()); SwingNode { JPanel() } }",
                ),
                Arguments.of(
                    "a SwingNode declared in another package",
                    """
                    @Composable fun Show() {
                        SwingNode { JPanel() }
                        other.SwingNode { JPanel() }
                    }
                    """.trimIndent(),
                ),
                Arguments.of(
                    "an unresolved call",
                    "@Composable fun Show() { SwingNode { JPanel() }; Missing { JPanel() } }",
                ),
            )
    }
}
