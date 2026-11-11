// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwingModifierFactoryReturnTypeTest {
    private fun lint(source: String) =
        SwingModifierFactoryReturnType(Config.empty).lint(
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
    fun `does not report a chain extension that returns the chain`() {
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
    fun `does not report a nullable chain extension that returns the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier?.dimmed(): SwingModifier = this ?: SwingModifier
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
    fun `does not report a private chain extension, which is not reachable from outside`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private fun SwingModifier.highlighted(): Highlight = Highlight()
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

class SwingModifierFactoryExtensionFunctionTest {
    private fun lint(source: String) =
        SwingModifierFactoryExtensionFunction(Config.empty).lint(sourceWithLibrarySwingModifierImport(source))

    @Test
    fun `reports a chain factory with no receiver`() {
        val findings =
            lint(
                """
                package sample

                fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("`highlighted` factory is not an extension"))
    }

    @Test
    fun `reports a chain factory whose receiver is not the chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun String.highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a plain extension on the chain`() {
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
    fun `does not report a nullable extension on the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier?.dimmed(): SwingModifier = this ?: SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a function that does not return the chain, which is not a factory candidate`() {
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
    fun `does not report a private function off the chain, which is not reachable from outside`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated qualified return type with the chain simple name`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun highlighted(): sample.modifier.SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated qualified chain receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun Other.SwingModifier.highlighted(): Highlight = Highlight()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a factory returning the library chain without a receiver`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun highlighted(): org.jetbrains.compose.swing.modifier.SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated qualified chain return type`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun highlighted(): Other.SwingModifier = Other.SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a consumer-defined chain type imported under the library name`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import consumer.SwingModifier

                fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a same-file declaration that shadows the library chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                class SwingModifier

                fun highlighted(): SwingModifier = SwingModifier()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `recognizes the chain type beside a value with the same name`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                val SwingModifier: Any = Any()

                fun highlighted(): SwingModifier = error("unused")
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a chain type shadowed by a nested declaration`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier

                class Container {
                    class SwingModifier

                    fun factory(): SwingModifier = SwingModifier()
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a chain type shadowed by a type parameter`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier

                fun <SwingModifier> factory(): SwingModifier = error("unused")
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `recognizes an unambiguous wildcard import of the library chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.*

                fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `explicit library import wins over an unrelated wildcard import`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import java.util.*
                import org.jetbrains.compose.swing.modifier.SwingModifier

                fun padded(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `test fixture adds the library import alongside an unrelated import`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import java.util.*

                fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `recognizes an explicit alias of the library chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                fun padded(): Chain = Chain
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `recognizes the library chain from its own package without an import`() {
        assertEquals(
            1,
            lint(
                """
                package org.jetbrains.compose.swing.modifier

                fun highlighted(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a chain property with no receiver`() {
        val findings =
            lint(
                """
                package sample

                val highlighted: SwingModifier get() = SwingModifier
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("Declare it as `val SwingModifier.highlighted"))
    }

    @Test
    fun `does not report a property extension on the chain`() {
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

                var highlighted: SwingModifier
                    get() = SwingModifier
                    set(_) = Unit
                """.trimIndent(),
            ).size,
        )
    }
}
