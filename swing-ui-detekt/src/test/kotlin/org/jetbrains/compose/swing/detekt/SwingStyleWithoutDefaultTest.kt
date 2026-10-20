package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwingStyleWithoutDefaultTest {
    private fun lint(source: String) = SwingStyleWithoutDefault(Config.empty).lint(source.trimIndent())

    @TestFactory
    fun validStyles(): List<DynamicTest> =
        listOf(
            "empty style" to
                source(
                    """
                    import org.jetbrains.compose.swing.style.ButtonScope
                    @Composable fun Button(style: SwingStyle<ButtonScope> = SwingStyle) = Unit
                    """,
                ),
            "nullable style" to source("@Composable fun Button(style: SwingStyle<Any>? = SwingStyle) = Unit"),
            "qualified style" to
                source(
                    """
                    @Composable fun Button(
                        style: org.jetbrains.compose.swing.style.SwingStyle<Any> =
                            org.jetbrains.compose.swing.style.SwingStyle,
                    ) = Unit
                    """,
                    imports = "",
                ),
            "import alias" to
                source(
                    "@Composable fun Button(style: Style<Any> = Style) = Unit",
                    imports = "import org.jetbrains.compose.swing.style.SwingStyle as Style",
                ),
            "unrelated types" to
                source(
                    """
                    @Composable fun Button(style: other.SwingStyle<Any>) = Unit
                    @Composable fun Label(style: SwingStyle<Any>) = Unit
                    """,
                    imports = "",
                ),
            "private and internal helpers" to
                source(
                    """
                    @Composable private fun ButtonNode(style: SwingStyle<Any>) = Unit
                    @Composable internal fun LabelNode(style: SwingStyle<Any>) = Unit
                    """,
                ),
            "override" to source("@Composable override fun Button(style: SwingStyle<Any>) = Unit"),
            "noncomposable" to source("fun Button(style: SwingStyle<Any>) = Unit"),
        ).map { (name, source) ->
            dynamicTest(name) {
                assertEquals(0, lint(source).size)
            }
        }

    @TestFactory
    fun invalidStyles(): List<DynamicTest> =
        listOf(
            Triple(
                "multiple parameters",
                2,
                "@Composable fun Pane(style: SwingStyle<Any>, headerStyle: SwingStyle<Any>) = Unit",
            ),
            Triple(
                "unrelated default",
                1,
                """
                import consumer.Other
                @Composable fun Button(style: SwingStyle<Any> = Other.SwingStyle) = Unit
                """,
            ),
            Triple(
                "shadowed companion",
                1,
                "@Composable fun Button(SwingStyle: Any, style: SwingStyle<Any> = SwingStyle) = Unit",
            ),
        ).map { (name, count, declaration) ->
            dynamicTest(name) {
                assertEquals(count, lint(source(declaration)).size)
            }
        }

    @Test
    fun `reports a public composable whose style parameter has no default`() {
        val findings =
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.style.SwingStyle

                @Composable
                fun Button(style: SwingStyle<Any>) = Unit
                """,
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("`style` parameter has no default"))
        assertTrue(findings.single().message.contains("Add `= SwingStyle`"))
    }

    @Test
    fun `reports a style parameter with a wrong default`() {
        val findings =
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.style.SwingStyle

                @Composable
                fun Button(style: SwingStyle<Any> = SwingStyle { }) = Unit
                """,
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("defaults to `SwingStyle { }`, not the empty style"))
    }

    private fun source(
        declaration: String,
        imports: String = "import org.jetbrains.compose.swing.style.SwingStyle",
    ): String =
        """
        package sample
        $imports
        $declaration
        """.trimIndent()
}
