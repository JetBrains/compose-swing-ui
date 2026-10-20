package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertTrue(findings.single().message.contains("`modifier` parameter has no default"))
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
        assertTrue(findings.single().message.contains("defaults to `SwingModifier.padded()`, not the empty chain"))
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
}
