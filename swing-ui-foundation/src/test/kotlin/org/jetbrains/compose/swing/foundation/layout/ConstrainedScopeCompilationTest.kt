package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.assertCompiled
import org.jetbrains.compose.swing.assertRejected
import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

/**
 * Pins where a [ConstrainedScope] member resolves: in the content of a container that offers
 * [ConstrainedScope], and nowhere else.
 *
 * A caller cannot be shown a rejected program by a running test, so these compile one with the official
 * Kotlin compiler driven in-process and read the diagnostics it emits.
 */
class ConstrainedScopeCompilationTest {
    @Test
    fun constrainedScopeMembersResolveInRowColumnBoxAndLayoutContent() {
        compile(
            """
            @Composable
            fun Constrained() {
                Row { Label("Row", modifier = SwingModifier.padding(4).offset(x = 4)) }
                Column { Label("Column", modifier = SwingModifier.padding(4).offset(x = 4)) }
                Box { Label("Box", modifier = SwingModifier.padding(4).offset(x = 4)) }
                Layout(measurePolicy = { _, _ -> layout(0, 0) {} }) {
                    Label("Layout", modifier = SwingModifier.padding(4).offset(x = 4))
                }
            }
            """,
        ).assertCompiled()
    }

    @Test
    fun constrainedScopeMembersDoNotResolveOutsideConstrainedContent() {
        compile(
            """
            @Composable
            fun Root() {
                Label("Root", modifier = SwingModifier.padding(4))
            }
            """,
        ).assertRejected(listOf("padding"))
        compile(
            """
            @Composable
            fun Root() {
                Label("Root", modifier = SwingModifier.fillMaxWidth())
            }
            """,
        ).assertRejected(listOf("fillMaxWidth"))
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

        fun compile(declarations: String) =
            InProcessCompilerHarness.compileSnippet(
                "ConstrainedScopeSnippet.kt",
                """
                import androidx.compose.runtime.Composable
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.foundation.layout.*
                import org.jetbrains.compose.swing.modifier.SwingModifier

                """.trimIndent() + declarations.trimIndent(),
            )
    }
}
