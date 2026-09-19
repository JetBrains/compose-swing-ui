package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposableSwingModifierFactoryTest {
    private fun lint(source: String) =
        ComposableSwingModifierFactory(Config.empty).lint(sourceWithLibrarySwingModifierImport(source))

    @Test
    fun `reports a composable extension on the chain`() {
        val findings =
            lint(
                """
                package sample

                @Composable
                fun SwingModifier.highlighted(): SwingModifier = this
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("build state private to each component with a `ComponentNode`"))
    }

    @Test
    fun `reports a composable property getter returning the chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                @get:Composable
                val SwingModifier.highlighted: SwingModifier
                    get() = this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a plain property getter returning the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                val SwingModifier.highlighted: SwingModifier
                    get() = this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports an annotation declared directly on a property getter`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                val SwingModifier.highlighted: SwingModifier
                    @Composable get() = this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a composable property returning an unrelated chain type`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @get:Composable
                val highlighted: other.SwingModifier
                    get() = other.SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a composable function returning a chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                @Composable
                fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a composable function returning an aliased chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                @Composable
                fun highlighted(): Chain = Chain
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a plain factory`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.highlighted(): SwingModifier = this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a composable that does not build a chain`() {
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
    fun `does not report a remember function returning what a factory takes`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun rememberHighlights(): Highlights = Highlights()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a composable on the chain that answers with something else`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun SwingModifier.elements(): List<SwingModifier.InspectableElement> = emptyList()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an inferred non-chain return type`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                @Composable
                fun SwingModifier.value() = 42
                """.trimIndent(),
            ).size,
        )
    }
}
