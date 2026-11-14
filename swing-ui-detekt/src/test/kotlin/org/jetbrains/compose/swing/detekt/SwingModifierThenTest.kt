// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class SwingModifierThenTest {
    private fun lint(source: String) =
        composeSwingRule("SwingModifierThen").lint(sourceWithLibrarySwingModifierImport(source))

    @Test
    fun `reports a factory taking its receiver implicitly inside then`() {
        val findings =
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.doubled(): SwingModifier = this.then(padded())
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertEquals(
            "The `padded()` call inside `then` runs on the receiver implicitly. A factory returns its " +
                "receiver's modifiers along with its own, so `then` adds the receiver's modifiers a second " +
                "time. Call the factory on the receiver itself, or on `SwingModifier` inside `then`.",
            findings.single().message,
        )
    }

    @Test
    fun `reports a factory taking its receiver implicitly inside a bare then call`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.doubled(): SwingModifier = then(padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a bare then call shadowed by a local function`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.preserved(): SwingModifier {
                    fun then(chain: SwingModifier): SwingModifier = chain
                    return then(padded())
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a bare then call shadowed by a local property`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.preserved(): SwingModifier {
                    val then: (SwingModifier) -> SwingModifier = { it }
                    return then(padded())
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a factory taking its receiver implicitly inside infix then`() {
        val findings =
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.doubled(): SwingModifier = this then padded()
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertEquals(
            "The `padded()` call inside `then` runs on the receiver implicitly. A factory returns its " +
                "receiver's modifiers along with its own, so `then` adds the receiver's modifiers a second " +
                "time. Call the factory on the receiver itself, or on `SwingModifier` inside `then`.",
            findings.single().message,
        )
    }

    @Test
    fun `reports a property getter taking its receiver implicitly inside then`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                val SwingModifier.doubled: SwingModifier
                    get() = this.then(padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports an explicitly labeled chain receiver inside then`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.doubled(): SwingModifier = run {
                    this@doubled.then(padded())
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report infix then given a chain that starts from the empty one`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.explicit(): SwingModifier = this then SwingModifier.padded()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report chaining directly onto the receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.chained(): SwingModifier = this.padded()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a factory given the empty chain explicitly`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.explicit(): SwingModifier = this.then(SwingModifier.padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report then called on the empty chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.explicit(): SwingModifier = SwingModifier.then(padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report another receiver's then call`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                class Other {
                    fun then(chain: SwingModifier): SwingModifier = chain
                }

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.preserved(other: Other): SwingModifier = other.then(padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report another receiver's infix then call`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                class Other {
                    infix fun then(chain: SwingModifier): SwingModifier = chain
                }

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.preserved(other: Other): SwingModifier = other then padded()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report then given a plain value`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.other(that: SwingModifier): SwingModifier = this.then(that)
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a bare call to a function that is not declared on the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                private fun layerDepth(layer: Int): SwingModifier = SwingModifier

                fun SwingModifier.layer(layer: Int): SwingModifier = this.then(layerDepth(1))
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not guess between same named top level overloads`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this
                fun padded(): SwingModifier = SwingModifier

                fun SwingModifier.doubled(): SwingModifier = this.then(padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not use a nested factory that the call cannot see`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun holder() {
                    fun SwingModifier.padded(): SwingModifier = this
                }

                fun SwingModifier.doubled(): SwingModifier = this.then(padded())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `ignores a nested padded function that hides the extension of the same name`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this

                fun SwingModifier.doubled(): SwingModifier {
                    fun padded(): SwingModifier = SwingModifier
                    return this.then(padded())
                }
                """.trimIndent(),
            ).size,
        )
    }
}
