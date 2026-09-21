// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class StyleCompilationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("snippets")
    fun styleScopesAreChecked(
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
                "check box accepts checked",
                """@Composable fun StateSnippet() { CheckBox("a", true, {}, style = { checked { font(null) } }) }""",
            ),
            case(
                "menu item accepts checked",
                """
                @Composable fun StateSnippet() {
                    CheckBoxMenuItem("a", true, {}, style = { checked { font(null) } })
                }
                """,
            ),
            case(
                "label rejects checked",
                """@Composable fun StateSnippet() { Label("a", style = { checked { font(null) } }) }""",
                listOf(
                    "candidate 'fun CheckableStyleScope.checked(block: () -> Unit): Unit' " +
                        "is inapplicable because of a receiver type mismatch.",
                ),
            ),
            case(
                "button rejects checked",
                """@Composable fun StateSnippet() { Button("a", {}, style = { checked { font(null) } }) }""",
                listOf(
                    "candidate 'fun CheckableStyleScope.checked(block: () -> Unit): Unit' " +
                        "is inapplicable because of a receiver type mismatch.",
                ),
            ),
            case(
                "radio button accepts selected",
                """
                @Composable fun StateSnippet() {
                    RadioButton("a", true, {}, style = { selected { font(null) } })
                }
                """,
            ),
            case(
                "toggle button accepts selected",
                """
                @Composable fun StateSnippet() {
                    ToggleButton("a", true, {}, style = { selected { font(null) } })
                }
                """,
            ),
            case(
                "check box rejects selected",
                """@Composable fun StateSnippet() { CheckBox("a", true, {}, style = { selected { font(null) } }) }""",
                listOf(
                    "candidate 'fun SelectableStyleScope.selected(block: () -> Unit): Unit' " +
                        "is inapplicable because of a receiver type mismatch.",
                ),
            ),
        )

        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }

        fun compile(program: String) = InProcessCompilerHarness.compileSnippet(
            "StyleSnippet.kt",
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.button.*
            import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
            import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
            import org.jetbrains.compose.swing.modifier.SwingModifier
            import org.jetbrains.compose.swing.node.SwingNode
            import org.jetbrains.compose.swing.style.*
            import org.jetbrains.compose.swing.components.style.*
            import javax.swing.JComponent
            import javax.swing.JPanel

            class RatingComponent(var stars: Int = 0) : JComponent()

            private val StarsProperty =
                stylePropertyOf(ComponentPropertyDescriptor<RatingComponent, Int>("stars", { it.stars }, { c, v -> c.stars = v }))

            interface RatingStyleScope : ComponentStyleScope

            fun RatingStyleScope.stars(count: Int) = StarsProperty.provide(count)

            val Ratings: NestedStyleKey<RatingStyleScope> = NestedStyleKey("Ratings")

            private val RatingKind =
                SwingStyleKind<RatingStyleScope, Unit>({ base, _ -> object : RatingStyleScope, ComponentStyleScope by base {} }) {
                    applyNestedStyle(Ratings)
                }

            @Composable
            fun Rating(value: Int, modifier: SwingModifier = SwingModifier, style: SwingStyle<RatingStyleScope> = SwingStyle) =
                SwingNode(factory = { RatingComponent() }, modifier = modifier.styleable(RatingKind, style), update = { set(value) { stars = it } })

            """.trimIndent() + program.trimIndent(),
        )
    }
}
