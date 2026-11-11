// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwingModifierUnreferencedReceiverTest {
    private fun lint(source: String) =
        SwingModifierUnreferencedReceiver(Config.empty).lint(sourceWithLibrarySwingModifierImport(source))

    @Test
    fun `does not report an accessor on the chain that answers with something else`() {
        val findings =
            lint(
                """
                package sample

                private fun SwingModifier.elements(): List<SwingModifier.InspectableElement> =
                    foldIn(mutableListOf<SwingModifier.InspectableElement>()) { acc, element ->
                        acc.apply { if (element is SwingModifier.InspectableElement) add(element) }
                    }
                """.trimIndent(),
            )

        assertEquals(0, findings.size, findings.joinToString { it.message })
    }

    @Test
    fun `reports a block body that returns a fresh chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(): SwingModifier {
                    val extra = Highlight()
                    return SwingModifier.then(extra)
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fresh chain in an explicit return branch`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(condition: Boolean): SwingModifier {
                    return if (condition) SwingModifier.then(Highlight()) else this
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not treat a block body's final expression as its return value`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.notAFactory() {
                    SwingModifier.then(Highlight())
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fresh chain in a terminal if branch`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(condition: Boolean): SwingModifier =
                    if (condition) SwingModifier.then(Highlight()) else this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fresh chain in a terminal when branch`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(value: Int): SwingModifier =
                    when (value) {
                        0 -> SwingModifier.then(Highlight())
                        else -> this
                    }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fresh chain in a terminal try body`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(): SwingModifier =
                    try {
                        SwingModifier.then(Highlight())
                    } catch (_: Exception) {
                        this
                    }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report terminal control flow that keeps the receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.preserved(condition: Boolean): SwingModifier =
                    if (condition) this else this
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a fresh chain returned only by a nested function`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.preserved(): SwingModifier {
                    fun discarded(): SwingModifier = SwingModifier.then(Highlight())
                    return this
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a fresh chain returned only by a nested anonymous function`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.preserved(): SwingModifier {
                    val discarded = fun(): SwingModifier {
                        return SwingModifier.then(Highlight())
                    }
                    return this
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fresh chain returned non-locally from a lambda`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(): SwingModifier {
                    listOf(1).forEach {
                        return SwingModifier.then(Highlight())
                    }
                    return this
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a chain the receiver opens with infix then`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.highlighted(): SwingModifier = (this then Highlight()) then Border()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a factory that hands the empty chain to an element while chaining onto its receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.boxed(): SwingModifier = then(BoxElement(inner = SwingModifier))
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a fresh chain even where the body names this inside a lambda`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.dropped(): SwingModifier =
                    SwingModifier.then(Highlight()).also { c -> c.apply { this.hashCode() } }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a factory that starts a fresh chain from SwingModifier itself`() {
        val findings =
            lint(
                """
                package sample

                fun SwingModifier.dropped(): SwingModifier = SwingModifier.then(Highlight())
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("`dropped` factory returns a chain built from"))
    }

    @Test
    fun `reports a factory that hands back the empty chain by name`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun SwingModifier.reset(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a factory that hands back an aliased empty chain`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                fun Chain.reset(): Chain = Chain
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a factory that chains directly onto its receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.explicit(): SwingModifier = this.then(Highlight())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a factory delegating through an implicit receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.explicit(): SwingModifier = this.then(Highlight())

                fun SwingModifier.delegating(): SwingModifier = explicit()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a nullable receiver that falls back to the empty chain, since it also names this`() {
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
    fun `does not report a function without a chain receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun dropped(): SwingModifier = SwingModifier
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not infer that an expression body returns the chain`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.hash() = SwingModifier.then(Highlight()).hashCode()
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a property getter that starts a fresh chain from SwingModifier itself`() {
        val findings =
            lint(
                """
                package sample

                val SwingModifier.dropped: SwingModifier get() = SwingModifier.then(Highlight())
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("`dropped` factory returns a chain built from"))
    }

    @Test
    fun `does not report a fresh chain returned only by a nested getter function`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                val SwingModifier.preserved: SwingModifier
                    get() {
                        fun discarded(): SwingModifier = SwingModifier.then(Highlight())
                        return this
                    }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a fresh chain returned only by a nested accessor`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.preserved(): SwingModifier {
                    class Holder {
                        val discarded: SwingModifier
                            get() {
                                return SwingModifier.then(Highlight())
                            }
                    }
                    return this
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a property getter that chains directly onto its receiver`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                val SwingModifier.explicit: SwingModifier get() = this.then(Highlight())
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `recognizes an explicitly labeled factory receiver inside a lambda`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.preserved(): SwingModifier =
                    SwingModifier.then(Highlight()).also { this@preserved.hashCode() }
                """.trimIndent(),
            ).size,
        )
    }
}

class SwingModifierThenTest {
    private fun lint(source: String) =
        SwingModifierThen(Config.empty).lint(sourceWithLibrarySwingModifierImport(source))

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
        assertTrue(findings.single().message.contains("chains the receiver twice"))
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
    fun `does not report a bare then call outside a chain extension`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun SwingModifier.padded(): SwingModifier = this
                fun then(chain: SwingModifier): SwingModifier = chain

                fun preserved(): SwingModifier = then(padded())
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
        assertTrue(findings.single().message.contains("chains the receiver twice"))
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
    fun `does not report a top level factory shadowed by a local function`() {
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
