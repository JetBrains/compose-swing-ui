package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Pins the compile-time scoping of styles: a nested style reaches only the scopes its key covers, only a scope
 * that holds children provides one, and a provided style covers the key's whole scope. A third-party widget
 * builds its scope, key and kind on public API only.
 *
 * Each snippet compiles in-process against a widget hierarchy it declares itself: a leaf scope, a label-like and
 * a button-like scope under it, a panel-like scope that provides nested styles, and a third-party rating widget.
 */
class StyleCompilationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("snippets")
    fun aSnippetCompilesExactlyWhenItsScopeAllowsIt(
        program: String,
        rejectedWith: List<String>?,
    ) {
        val result = compile(program)
        if (rejectedWith == null) result.assertCompiled() else result.assertRejected(rejectedWith)
    }

    private companion object {
        private fun case(
            name: String,
            program: String,
            rejectedWith: List<String>? = null,
        ): Arguments = Arguments.of(Named.of(name, program), rejectedWith)

        @JvmStatic
        fun snippets(): List<Arguments> = listOf(
            case(
                "a third-party widget is styled through public API only",
                """
                @Composable
                fun RatedSection() {
                    Panel(style = { provideNestedStyle(Ratings) { stars(5) } }) { Rating(3) }
                }
                """,
            ),
            case(
                "a label scope applies a key that covers it",
                """
                private val LabelKind =
                    SwingStyleKind<LabelScope, Unit>({ base, _ -> object : LabelScope, ComponentStyleScope by base {} }) {
                        applyNestedStyle(AllWidgets)
                        applyNestedStyle(Labels)
                    }
                """,
            ),
            case(
                "a label scope cannot apply the key of an unrelated scope",
                """
                private val LabelKind =
                    SwingStyleKind<LabelScope, Unit>({ base, _ -> object : LabelScope, ComponentStyleScope by base {} }) {
                        applyNestedStyle(Buttons)
                    }
                """,
                listOf("NestedStyleKey<in LabelScope>"),
            ),
            case(
                "a panel scope provides a nested style",
                """
                val PanelStyle = SwingStyle<PanelScope> { provideNestedStyle(Labels) {} }
                """,
            ),
            case(
                "a leaf scope cannot provide a nested style",
                """
                val LabelStyle = SwingStyle<LabelScope> { provideNestedStyle(Labels) {} }
                """,
                listOf("provideNestedStyle"),
            ),
            case(
                "a provider gives a key the style of the key's whole scope",
                """
                val Everything = SwingStyle<ComponentStyleScope> {}
                val PanelStyle = SwingStyle<PanelScope> { provideNestedStyle(AllWidgets, Everything) }
                """,
            ),
            case(
                "a provider cannot narrow the scope of a key",
                """
                val FiveStars = SwingStyle<RatingStyleScope> { stars(5) }
                val PanelStyle = SwingStyle<PanelScope> { provideNestedStyle(AllWidgets, FiveStars) }
                """,
                listOf("SwingStyle<ComponentStyleScope>"),
            ),
        )

        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }

        /** Compiles [program] beside the widget hierarchy every snippet shares. */
        fun compile(program: String) = InProcessCompilerHarness.compileSnippet(
            "StyleSnippet.kt",
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
            import org.jetbrains.compose.swing.modifier.SwingModifier
            import org.jetbrains.compose.swing.node.SwingNode
            import org.jetbrains.compose.swing.style.*
            import javax.swing.JComponent
            import javax.swing.JPanel

            interface WidgetScope : ComponentStyleScope
            interface LabelScope : WidgetScope
            interface ButtonScope : WidgetScope
            interface PanelScope : WidgetScope, NestedStyleProviderScope

            val AllWidgets: NestedStyleKey<ComponentStyleScope> = NestedStyleKey("AllWidgets")
            val Labels: NestedStyleKey<LabelScope> = NestedStyleKey("Labels")
            val Buttons: NestedStyleKey<ButtonScope> = NestedStyleKey("Buttons")

            private val PanelKind =
                SwingStyleKind<PanelScope, Unit>({ base, _ -> object : PanelScope, NestedStyleProviderScope by base {} })

            @Composable
            fun Panel(style: SwingStyle<PanelScope> = SwingStyle, content: @Composable () -> Unit) =
                SwingNode(factory = ::JPanel, modifier = SwingModifier.styleable(style, PanelKind), content = content)

            class RatingComponent(var stars: Int = 0) : JComponent()

            private val StarsProperty =
                stylePropertyOf(ComponentPropertyDescriptor<RatingComponent, Int>("stars", { it.stars }, { c, v -> c.stars = v }))

            interface RatingStyleScope : WidgetScope

            fun RatingStyleScope.stars(count: Int) = StarsProperty.provide(count)

            val Ratings: NestedStyleKey<RatingStyleScope> = NestedStyleKey("Ratings")

            private val RatingKind =
                SwingStyleKind<RatingStyleScope, Unit>({ base, _ -> object : RatingStyleScope, ComponentStyleScope by base {} }) {
                    applyNestedStyle(AllWidgets)
                    applyNestedStyle(Ratings)
                }

            @Composable
            fun Rating(value: Int, modifier: SwingModifier = SwingModifier, style: SwingStyle<RatingStyleScope> = SwingStyle) =
                SwingNode(factory = { RatingComponent() }, modifier = modifier.styleable(style, RatingKind), update = { set(value) { stars = it } })

            """.trimIndent() + program.trimIndent(),
        )
    }
}
