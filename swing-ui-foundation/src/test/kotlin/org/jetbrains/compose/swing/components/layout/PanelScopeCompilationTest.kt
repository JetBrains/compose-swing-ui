package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [PanelScope] as the only scope in reach inside a panel's content. A child declares its placement
 * to the container that lays it out, so a declaration meant for an enclosing row or column must not be
 * readable by a child of a panel nested in one - the panel's layout manager would never see it, and the
 * child would sit wherever that manager put it with the declaration silently doing nothing.
 *
 * A caller cannot be shown a rejected program by a running test, so these compile one with the official
 * Kotlin compiler driven in-process and read the diagnostics it emits.
 */
class PanelScopeCompilationTest {
    @Test
    fun aRowsDeclarationInsideANestedPanelDoesNotCompile() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "PanelScopeSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.components.layout.Panel
                import org.jetbrains.compose.swing.foundation.layout.Row
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @androidx.compose.runtime.Composable
                fun Nested() {
                    Row {
                        Panel {
                            Label("Details", modifier = SwingModifier.weight(1f))
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "a row's weight declared on a child of a panel must be rejected, output was:\n${result.output}",
        )
        assertTrue(
            result.errors().any { "weight" in it },
            "the rejection must name the declaration it is about, output was:\n${result.output}",
        )
    }

    /**
     * The panel itself is the row's child, so the row's declaration belongs on the panel's own modifier
     * and is written outside the content lambda, where the row's scope is still the innermost one.
     */
    @Test
    fun aRowsDeclarationOnTheNestedPanelItselfCompiles() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "PanelScopeSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.components.layout.Panel
                import org.jetbrains.compose.swing.foundation.layout.Row
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @androidx.compose.runtime.Composable
                fun OnThePanel() {
                    Row {
                        Panel(modifier = SwingModifier.weight(1f)) {
                            Label("Details")
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "a row's weight declared on the panel it lays out must compile, output was:\n${result.output}",
        )
    }

    /** What the panel's own layout reads is declared by its own scope, which the marker leaves in reach. */
    @Test
    fun thePanelsOwnScopeResolvesInsideARow() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "PanelScopeSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.components.layout.Panel
                import org.jetbrains.compose.swing.components.layout.PanelLayout
                import org.jetbrains.compose.swing.foundation.layout.Row
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @androidx.compose.runtime.Composable
                fun Bordered() {
                    Row {
                        Panel(PanelLayout.Border()) {
                            Label("Title", modifier = SwingModifier.north())
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "a border panel's own region declaration must compile inside a row, output was:\n${result.output}",
        )
    }

    /** A row nested inside a panel is the innermost scope in its own content, and hands it back. */
    @Test
    fun aRowsDeclarationResolvesAgainInARowNestedInAPanel() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "PanelScopeSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.components.layout.Panel
                import org.jetbrains.compose.swing.foundation.layout.Row
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @androidx.compose.runtime.Composable
                fun Regained() {
                    Panel {
                        Row {
                            Label("Details", modifier = SwingModifier.weight(1f))
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "a row inside a panel must hand its own scope to its content, output was:\n${result.output}",
        )
    }

    private companion object {
        /**
         * Resolves the compiler plugin classpath once at startup, so a test task that does not hand the
         * harness one reports a single failure here rather than the same failure in every case below.
         */
        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }
    }
}
