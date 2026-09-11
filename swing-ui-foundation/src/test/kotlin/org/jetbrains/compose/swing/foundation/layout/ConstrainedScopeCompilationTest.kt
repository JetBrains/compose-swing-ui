package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.assertCompiled
import org.jetbrains.compose.swing.assertRejected
import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

/**
 * Pins where a layout modifier of the caller's own compiles when its factory takes [ConstrainedScope] as a context
 * parameter: in the content of a container that offers [ConstrainedScope], and nowhere else.
 *
 * A caller cannot be shown a rejected program by a running test, so these compile one with the official
 * Kotlin compiler driven in-process and read the diagnostics it emits.
 */
class ConstrainedScopeCompilationTest {
    @Test
    fun aModifierBuiltOnLayoutResolvesInRowColumnBoxAndLayoutContent() {
        compile(
            """
            $HALF_WIDTH

            @Composable
            fun Constrained() {
                Row { Label("Row", modifier = SwingModifier.halfWidth().shifted()) }
                Column { Label("Column", modifier = SwingModifier.halfWidth().shifted()) }
                Box { Label("Box", modifier = SwingModifier.halfWidth().shifted()) }
                Layout(measurePolicy = { _, _ -> layout(0, 0) {} }) {
                    Label("Layout", modifier = SwingModifier.halfWidth().shifted())
                }
            }
            """,
        ).assertCompiled()
    }

    @Test
    fun aModifierBuiltOnLayoutOutsideConstrainedContentDoesNotCompile() {
        compile(
            """
            $HALF_WIDTH

            @Composable
            fun Root() {
                Label("Root", modifier = SwingModifier.halfWidth())
            }
            """,
        ).assertRejected(listOf("ConstrainedScope"))
        compile(
            """
            $HALF_WIDTH

            @Composable
            fun Root() {
                Label("Root", modifier = SwingModifier.shifted())
            }
            """,
        ).assertRejected(listOf("ConstrainedScope"))
    }

    private companion object {
        /**
         * A [LayoutModifier] of the caller's own and a modifier of the caller's own built on `offset`, each behind a
         * factory that takes [ConstrainedScope] as a context parameter.
         */
        const val HALF_WIDTH =
            """
            object HalfWidth : LayoutModifier {
                override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth / 2))
                    return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
            }

            context(_: ConstrainedScope)
            fun SwingModifier.halfWidth(): SwingModifier = this then HalfWidth

            context(_: ConstrainedScope)
            fun SwingModifier.shifted(): SwingModifier = offset(x = 4)
            """

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
