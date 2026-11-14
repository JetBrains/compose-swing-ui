// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwingModifierFactoryReturnTypeTest {
    private fun lint(source: String) =
        composeSwingRule("SwingModifierFactoryReturnType").lint(
            sourceWithLibrarySwingModifierImport(source),
        )

    @Test
    fun `reports a chain extension that returns the element it builds`() {
        val findings =
            lint(
                """
                package sample

                fun SwingModifier.highlighted(): Highlight = Highlight()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("returns `Highlight`"))
    }

    @Test
    fun `accepts valid and unrelated modifier declarations across rules`() {
        val sources =
            listOf(
                """
                package sample

                fun SwingModifier.highlighted(): SwingModifier = this
                """.trimIndent(),
                """
                package sample

                private fun SwingModifier.highlighted(): Highlight = Highlight()
                """.trimIndent(),
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this
                fun then(chain: SwingModifier): SwingModifier = chain

                fun preserved(): SwingModifier = then(padded())
                """.trimIndent(),
            )
        listOf("SwingModifierFactoryReturnType", "SwingModifierThen").forEach { rule ->
            sources.forEach { source ->
                val findings = composeSwingRule(rule).lint(sourceWithLibrarySwingModifierImport(source))
                assertEquals(0, findings.size, "$rule reported on:\n$source")
            }
        }
    }

    @Test
    fun `does not report a nullable chain extension that returns the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier?.dimmed(
                    fallback: SwingModifier,
                ): SwingModifier = this ?: fallback
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function without a chain receiver, which is not a factory candidate`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun highlighted(): Highlight = Highlight()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function with an inferred return type`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded() = PadElement()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fully qualified element return type`() {
        val findings =
            lint(
                """
                package sample

                fun SwingModifier.highlighted(): sample.elements.Highlight = Highlight()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports a chain extension property that returns the element it builds`() {
        val findings =
            lint(
                """
                package sample

                val SwingModifier.highlighted: Highlight get() = Highlight()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("returns `Highlight`"))
    }

    @Test
    fun `does not report a chain extension property that returns the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                val SwingModifier.highlighted: SwingModifier get() = this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a var property, which is not a factory candidate`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                var SwingModifier.highlighted: Highlight
                    get() = Highlight()
                    set(_) = Unit
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a stored property with no getter, which is not a factory candidate`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                val highlighted: Highlight = Highlight()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a member property, which is not a factory candidate`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                class Widget {
                    val SwingModifier.highlighted: Highlight get() = Highlight()
                }
                """.trimIndent(),
            ).size,
        )
    }
}
