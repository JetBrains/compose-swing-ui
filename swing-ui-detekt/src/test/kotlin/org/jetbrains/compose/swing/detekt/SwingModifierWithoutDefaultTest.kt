// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.test.lint
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class SwingModifierWithoutDefaultTest {
    private fun lint(source: String) =
        SwingModifierWithoutDefault(Config.empty).lint(sourceWithLibrarySwingModifierImport(source))

    @Test
    fun `does not report an unrelated qualified chain type`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: other.SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated qualified chain type with its own default`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun Label(modifier: other.SwingModifier = other.SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a public composable whose chain parameter has no default`() {
        val findings =
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertEquals(
            "The `modifier` parameter has no default. A caller that has no modifiers to apply must still " +
                "pass one. Add `= SwingModifier` as the default.",
            findings.single().message,
        )
    }

    @Test
    fun `does not report a defaulted chain parameter`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: SwingModifier = SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an explicit companion default`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: SwingModifier = SwingModifier.Companion) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a private helper, which is always handed a chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                private fun WidgetNode(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an internal helper`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                internal fun WidgetNode(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an override, whose signature is not its own to change`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                override fun Widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function that is not composable`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fully qualified chain type`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: org.jetbrains.compose.swing.modifier.SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a chain parameter defaulted to something other than the empty chain`() {
        val findings =
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: SwingModifier = SwingModifier.padded()) = Unit
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertEquals(
            "The `modifier` parameter defaults to `SwingModifier.padded()` instead of the empty modifier. " +
                "Every caller that leaves it out gets these modifiers without asking for them. " +
                "Default it to the `SwingModifier` companion.",
            findings.single().message,
        )
    }

    @Test
    fun `does not report a chain parameter defaulted to the fully qualified empty chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun Widget(modifier: SwingModifier = org.jetbrains.compose.swing.modifier.SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a chain parameter defaulted to another type with the same simple name`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import consumer.Other

                @Composable
                fun Label(modifier: SwingModifier = Other.SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `accepts an imported alias for the empty chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                @Composable
                fun Label(modifier: Chain = Chain) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a value that shadows the empty chain companion`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                @Composable
                fun Label(
                    SwingModifier: Any,
                    modifier: SwingModifier = SwingModifier,
                ) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a top-level value that shadows the empty chain companion`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                val SwingModifier: Any = Any()

                @Composable
                fun Label(modifier: SwingModifier = SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a member property that shadows the empty chain companion`() {
        val sources =
            mapOf(
                "class property" to
                    """
                    package sample

                    import org.jetbrains.compose.swing.modifier.SwingModifier

                    class Host {
                        val SwingModifier: Any = Any()

                        @Composable
                        fun Label(modifier: SwingModifier = SwingModifier) = Unit
                    }
                    """.trimIndent(),
                "constructor property" to
                    """
                    package sample

                    import org.jetbrains.compose.swing.modifier.SwingModifier

                    class Host(val SwingModifier: Any) {
                        @Composable
                        fun Label(modifier: SwingModifier = SwingModifier) = Unit
                    }
                    """.trimIndent(),
            )

        for ((declaration, source) in sources) {
            assertEquals(1, lint(source).size, declaration)
        }
    }

    @Test
    fun `does not treat an outer class property as shadowing outside an inner class`() {
        val nestedClass =
            """
            package sample

            import org.jetbrains.compose.swing.modifier.SwingModifier

            class Outer {
                val SwingModifier: Any = Any()

                class Nested {
                    @Composable
                    fun Label(modifier: SwingModifier = SwingModifier) = Unit
                }
            }
            """.trimIndent()
        val innerClass = nestedClass.replace("class Nested", "inner class Nested")
        val nestedClassUnderObject =
            """
            package sample

            import org.jetbrains.compose.swing.modifier.SwingModifier

            class Outer {
                companion object {
                    val SwingModifier: Any = Any()
                }

                class Nested {
                    @Composable
                    fun Label(modifier: SwingModifier = SwingModifier) = Unit
                }
            }
            """.trimIndent()

        assertEquals(0, lint(nestedClass).size, "nested class")
        assertEquals(1, lint(innerClass).size, "inner class")
        assertEquals(1, lint(nestedClassUnderObject).size, "companion property")
    }

    @Test
    fun `reports an imported chain alias without a default`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                @Composable
                fun Label(modifier: Chain) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a composable no caller outside the module can reach`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                internal object Internals {
                    @Composable
                    fun Widget(modifier: SwingModifier) = Unit
                }

                @Composable
                fun Host() {
                    @Composable
                    fun Local(modifier: SwingModifier) = Unit
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function annotated with a qualified annotation named like the Composable alias`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.Composable as Compose

                @other.Compose
                fun Widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function annotated with an unrelated annotation imported under an alias`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import other.Annotation as Compose

                @Compose
                fun Widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    /** `isComposable` is shared by the three rules, and the alias it resolves is the same for each. */
    @TestFactory
    fun `reports a composable declared through an imported alias of Composable`(): List<DynamicTest> =
        listOf(
            Triple(
                SwingModifierWithoutDefault(Config.empty),
                "",
                "fun Widget(modifier: SwingModifier) = Unit",
            ),
            Triple(
                SwingStyleWithoutDefault(Config.empty),
                "import org.jetbrains.compose.swing.style.SwingStyle",
                "fun Button(style: SwingStyle<Any>) = Unit",
            ),
            Triple(
                ComposableSwingModifierFactory(Config.empty),
                "",
                "fun SwingModifier.highlighted(): SwingModifier = this",
            ),
        ).map { (rule: Rule, import, declaration) ->
            dynamicTest(rule.ruleName.value) {
                val source =
                    """
                    package sample

                    import androidx.compose.runtime.Composable as Compose
                    $import

                    @Compose
                    $declaration
                    """.trimIndent()
                assertEquals(1, rule.lint(sourceWithLibrarySwingModifierImport(source)).size)
            }
        }

    @Test
    fun `does not report a function annotated with a class declared in the file that shadows the Composable alias`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.Composable as Compose

                class Host {
                    annotation class Compose

                    @Compose
                    fun Widget(modifier: SwingModifier) = Unit
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a composable when a top-level class named like the Composable alias sits in the file`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import androidx.compose.runtime.Composable as Compose

                annotation class Compose

                @Compose
                fun Widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function annotated with a class in the companion that shadows the Composable alias`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.Composable as Compose

                class Host {
                    @Compose
                    fun Widget(modifier: SwingModifier) = Unit

                    companion object {
                        annotation class Compose
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function in a class nested in the one that declares the shadowing class`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.Composable as Compose

                class Host {
                    annotation class Compose

                    class Inner {
                        @Compose
                        fun Widget(modifier: SwingModifier) = Unit
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a composable when the class named like the Composable alias is nested in an unrelated class`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import androidx.compose.runtime.Composable as Compose

                class Unrelated {
                    annotation class Compose
                }

                @Compose
                fun Widget(modifier: SwingModifier) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not treat a member extension property as shadowing the empty chain companion`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                class Host {
                    val String.SwingModifier: Any get() = Any()

                    @Composable
                    fun Widget(modifier: SwingModifier = SwingModifier) = Unit
                }
                """.trimIndent(),
            ).size,
        )
    }
}
