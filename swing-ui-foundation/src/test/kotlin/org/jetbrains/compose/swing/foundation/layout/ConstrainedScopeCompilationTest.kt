package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

/**
 * Pins where a layout modifier of the caller's own compiles when it is built on [ConstrainedScope]'s `layout`
 * members: in the content of a container that offers [ConstrainedScope], and nowhere else.
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
                Row {
                    Label("Row", modifier = SwingModifier.halfWidth().shifted())
                    Label(
                        "Lambda",
                        modifier =
                            SwingModifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            },
                    )
                }
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
        compile(
            """
            $HALF_WIDTH

            @Composable
            fun Root() {
                Label("Root", modifier = SwingModifier.layout(HalfWidth))
            }
            """,
        ).assertRejected(listOf("ConstrainedScope", "HalfWidth"))
    }

    private companion object {
        /**
         * A layout modifier of the caller's own declared through the `layout` member, and one written as a
         * lambda through the `layout` extension.
         */
        const val HALF_WIDTH =
            """
            class HalfWidthNode : LayoutModifierNode() {
                override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth / 2))
                    return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
            }

            object HalfWidth : LayoutModifierNodeElement<HalfWidthNode>() {
                override fun create(): HalfWidthNode = HalfWidthNode()
                override fun update(node: HalfWidthNode) = Unit
                override fun equals(other: Any?): Boolean = this === other
                override fun hashCode(): Int = 0
            }

            context(scope: ConstrainedScope)
            fun SwingModifier.halfWidth(): SwingModifier = with(scope) { layout(HalfWidth) }

            context(scope: ConstrainedScope)
            fun SwingModifier.shifted(): SwingModifier =
                layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(4, 0) }
                }
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
