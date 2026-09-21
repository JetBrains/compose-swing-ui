// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test

/**
 * Behavioral tests for the actual-size styles (`size`/`width`/`height`). They set the component's
 * live size (`setSize`), which a layout manager would override on its next pass, so each test hosts the
 * styled child in a [JLayeredPane] - a parent with no layout manager - where the set size persists.
 * The assertions read what an observer of the live Swing component sees: its `getSize`/`getWidth`/
 * `getHeight`.
 */
class SizeStyleTest {
    @TestFactory
    fun settersWriteRewriteAndRestore(): List<DynamicTest> = setterCaseTests(
        listOf(
            layoutCase("size", Dimension(1, 2), Dimension(3, 4), { it.size }) { size(it) },
            layoutCase("size(width, height)", Dimension(1, 2), Dimension(3, 4), { it.size }) {
                size(it.width, it.height)
            },
            layoutCase("width", 1, 2, { it.width }) { width(it) },
            layoutCase("height", 1, 2, { it.height }) { height(it) },
            layoutCase("preferredSize", Dimension(1, 2), Dimension(3, 4), ::declaredPreferredSize) {
                preferredSize(it)
            },
            layoutCase("preferredSize(width, height)", Dimension(1, 2), Dimension(3, 4), ::declaredPreferredSize) {
                preferredSize(it.width, it.height)
            },
            layoutCase("minimumSize", Dimension(1, 2), Dimension(3, 4), ::declaredMinimumSize) { minimumSize(it) },
            layoutCase("minimumSize(width, height)", Dimension(1, 2), Dimension(3, 4), ::declaredMinimumSize) {
                minimumSize(it.width, it.height)
            },
            layoutCase("maximumSize", Dimension(1, 2), Dimension(3, 4), ::declaredMaximumSize) { maximumSize(it) },
            layoutCase("maximumSize(width, height)", Dimension(1, 2), Dimension(3, 4), ::declaredMaximumSize) {
                maximumSize(it.width, it.height)
            },
        ),
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizeCases")
    fun aSizeDeclarationSetsTheActualSize(
        message: String,
        style: SwingStyle<LabelStyleScope>,
        expected: Dimension,
    ) = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(text = "child", modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER), style = style)
            }
        }
        onNodeOfType<JLabel>().assertProperty(expected, message) { size }
    }

    @Test
    fun droppingAWholeSizeAndThenAnAxisLeavesTheChildWhereItStood() = runComposeSwingTest {
        // The axis slot joins the modifier over a width the size slot has already written, so what it puts
        // back has to be the width the child came with rather than the one it read on the way in.
        var declared by mutableStateOf(0)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = onDefaultLayer,
                    style = {
                        if (declared == 1) size(120, 40)
                        if (declared == 1 || declared == 2) width(10)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Dimension(0, 0)) { size }

        declared = 1
        awaitIdle()
        child.assertProperty(Dimension(10, 40), "the width is declared last and wins its axis") { size }

        // The whole size goes first, leaving the axis standing on what that size wrote.
        declared = 2
        awaitIdle()
        declared = 3
        awaitIdle()

        child.assertProperty(
            Dimension(0, 0),
            "the child is left at the size it stood at before either declaration",
        ) { size }
    }

    @Test
    fun removingTheSizeStyleRestoresTheOriginalSize() = runComposeSwingTest {
        var sized by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = onDefaultLayer,
                    style = { if (sized) size(120, 40) },
                )
            }
        }
        val sizedComponent = onNodeOfType<JLabel>()
        // The size the component had before the modifier captured it - its pre-modifier original.
        sizedComponent.assertProperty(Dimension(120, 40), "size should set the actual size while applied") { size }

        sized = false
        awaitIdle()
        // The size declaration is gone, so the component returns to the size it had before it.
        val restored = onNodeOfType<JLabel>()
        restored.assertProperty(
            Dimension(0, 0),
            "removing the size style restores the component's original size",
        ) { size }
    }

    @Test
    fun addingTheSizeLeavesTheAxisAnotherStyleDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(false)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = onDefaultLayer,
                    style = {
                        if (whole) size(120, 40)
                        width(10)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(10, "the declared width is what the child carries") { width }

        // The size arrives ahead of the width declaration and writes the axis it declares, so the
        // later declaration is written again over it.
        whole = true
        awaitIdle()

        child.assertProperty(10, "the declared width stands after the size arrives under it") { width }
        child.assertProperty(40, "the size reaches the axis it owns") { height }
    }

    @Test
    fun droppingTheSizeLeavesTheAxisAnotherStyleDeclares() = runComposeSwingTest {
        var whole by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = onDefaultLayer,
                    style = {
                        if (whole) size(120, 40)
                        width(10)
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Dimension(10, 40), "the later width wins its axis, height stays from size") { size }

        whole = false
        awaitIdle()

        // Restoring a size puts both axes back, width included, so the modifier declaring that axis
        // alone writes it again on the same pass.
        child.assertProperty(Dimension(10, 0), "the declared width stands after the size is dropped") { size }
    }

    @Test
    fun sizeReactsToStateChangeAcrossRecomposition() = runComposeSwingTest {
        var wide by mutableStateOf(true)
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = { size(if (wide) 120 else 60, 40) },
                )
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(
            Dimension(120, 40),
            "size should set the actual size before the state changes",
        ) { size }

        wide = false
        awaitIdle()
        label.assertProperty(
            Dimension(60, 40),
            "size should react to the state change",
        ) { size }
    }

    @Test
    fun theLastPlaceAKeyIsDeclaredAtIsWhereItStands() = runComposeSwingTest {
        setContent {
            LayeredPane {
                Label(
                    text = "child",
                    modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        width(10)
                        size(20, 30)
                        width(5)
                    },
                )
            }
        }
        onNodeOfType<JLabel>().assertProperty(
            Dimension(5, 30),
            "a key declared again later should stand where it was declared last",
        ) { size }
    }

    @Test
    fun swappingTwoDeclarationsOfOneAxisWritesTheOneNowDeclaredLast() = runComposeSwingTest {
        var sizeFirst by mutableStateOf(true)
        setContent {
            LayeredPane {
                val onDefaultLayer = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                Label(
                    text = "child",
                    modifier = onDefaultLayer,
                    style = {
                        if (sizeFirst) {
                            size(20, 30)
                            width(10)
                            foreground(Color.RED)
                            background(Color.BLUE)
                        } else {
                            width(10)
                            size(20, 30)
                            background(Color.BLUE)
                            foreground(Color.RED)
                        }
                    },
                )
            }
        }
        val child = onNodeOfType<JLabel>()
        child.assertProperty(Dimension(10, 30), "the width is declared last and wins its axis") { size }

        // No declaration changed, so nothing but their order says which one stands.
        sizeFirst = false
        awaitIdle()

        child.assertProperty(Dimension(20, 30), "the size is declared last now and wins the width axis") { size }
    }

    companion object {
        @JvmStatic
        fun sizeCases(): List<Arguments> = listOf(
            Arguments.of(
                Named.of("size", "size should set the component's actual size"),
                SwingStyle<LabelStyleScope> { size(120, 40) },
                Dimension(120, 40),
            ),
            Arguments.of(
                Named.of("Dimension overload", "the Dimension overload should set the actual size"),
                SwingStyle<LabelStyleScope> { size(Dimension(90, 30)) },
                Dimension(90, 30),
            ),
            Arguments.of(
                Named.of("width keeps height", "width must keep the current height"),
                SwingStyle<LabelStyleScope> {
                    size(50, 33)
                    width(150)
                },
                Dimension(150, 33),
            ),
            Arguments.of(
                Named.of("height keeps width", "height must keep the current width"),
                SwingStyle<LabelStyleScope> {
                    size(44, 50)
                    height(55)
                },
                Dimension(44, 55),
            ),
            Arguments.of(
                Named.of("width then height", "width+height combine into a full size"),
                SwingStyle<LabelStyleScope> {
                    width(10)
                    height(20)
                },
                Dimension(10, 20),
            ),
            Arguments.of(
                Named.of("height then width", "distinct axes combine regardless of order"),
                SwingStyle<LabelStyleScope> {
                    height(20)
                    width(10)
                },
                Dimension(10, 20),
            ),
            Arguments.of(
                Named.of("size after width", "later size wins the width axis"),
                SwingStyle<LabelStyleScope> {
                    width(10)
                    size(20, 30)
                },
                Dimension(20, 30),
            ),
            Arguments.of(
                Named.of("width after size", "later width wins the width axis, height stays from size"),
                SwingStyle<LabelStyleScope> {
                    size(20, 30)
                    width(10)
                },
                Dimension(10, 30),
            ),
        )
    }
}

private fun declaredPreferredSize(component: Component): Dimension? =
    if (component.isPreferredSizeSet) component.preferredSize else null

private fun declaredMinimumSize(component: Component): Dimension? =
    if (component.isMinimumSizeSet) component.minimumSize else null

private fun declaredMaximumSize(component: Component): Dimension? =
    if (component.isMaximumSizeSet) component.maximumSize else null
