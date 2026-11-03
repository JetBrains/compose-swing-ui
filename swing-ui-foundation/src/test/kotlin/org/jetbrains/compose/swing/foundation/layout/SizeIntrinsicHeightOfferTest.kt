// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import javax.swing.JTextArea

/** A fixed width declared on a wrapping text child keeps its intrinsic heights at that width, under a stock parent. */
class SizeIntrinsicHeightOfferTest {
    @ParameterizedTest
    @MethodSource("declarations")
    fun aDeclaredWidthReachesTheIntrinsicHeightsOfAColumnInAStockPanel(declaration: Declaration) =
        runComposeSwingTest {
            val prompt = "Add a dark mode toggle to the settings screen."
            // The width at which the whole text fits one line.
            val width = JTextArea(prompt).preferredSize.width
            setContent {
                Box {
                    Column(modifier = SwingModifier.fillMaxWidth()) {
                        Panel(PanelLayout.Border()) {
                            Column {
                                TextArea(
                                    value = prompt,
                                    onValueChange = {},
                                    modifier = declaration.modifier(this, width),
                                    lineWrap = true,
                                    wrapStyleWord = true,
                                )
                            }
                        }
                    }
                }
            }
            settleWithPaint()
            onNodeOfType<JTextArea>().assertProperty(
                declaration.placedWidth(width),
                "the text is placed at its declared width",
            ) { this.width }
            val wrappedHeight =
                JTextArea(prompt)
                    .apply {
                        lineWrap = true
                        wrapStyleWord = true
                        setSize(declaration.placedWidth(width), Short.MAX_VALUE.toInt())
                    }.preferredSize.height
            onNodeOfType<JTextArea>().assertProperty(
                wrappedHeight,
                "the text's height at the width it is placed at",
            ) { height }
            val panel = onNodeOfType<JTextArea>().onParent().onParent()
            panel.assertProperty(wrappedHeight, "the stock panel is allocated its child's height") { height }
            panel.assertProperty(
                wrappedHeight,
                "the maximum intrinsic height reads the text at the width it is placed at",
            ) { preferredSize.height }
            panel.assertProperty(
                wrappedHeight,
                "the minimum intrinsic height reads the text at the width it is placed at",
            ) { minimumSize.height }
        }

    class Declaration(
        val modifier: ConstrainedScope.(Int) -> SwingModifier,
        val placedWidth: (textWidth: Int) -> Int,
    )

    private companion object {
        @JvmStatic
        fun declarations(): Stream<Arguments> =
            Stream
                .of(
                    Named.of("width", Declaration({ SwingModifier.width(it) }, { it })),
                    Named.of("widthIn(min)", Declaration({ SwingModifier.widthIn(min = it) }, { it })),
                    Named.of(
                        "requiredWidthIn(max)",
                        Declaration({ SwingModifier.requiredWidthIn(max = it / 4) }, { it / 4 }),
                    ),
                ).map { Arguments.of(it) }
    }
}
