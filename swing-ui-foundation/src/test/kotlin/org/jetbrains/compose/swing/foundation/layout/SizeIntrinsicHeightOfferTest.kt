package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.util.stream.Stream
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.assertEquals

/** A fixed width declared on a wrapping text child keeps its intrinsic heights at that width, under a stock parent. */
class SizeIntrinsicHeightOfferTest {
    @ParameterizedTest
    @MethodSource("declarations")
    fun aDeclaredWidthReachesTheIntrinsicHeightsOfAColumnInAStockPanel(declaration: Declaration) =
        runComposeSwingTest {
            val prompt = "Add a dark mode toggle to the settings screen."
            val area =
                JTextArea(prompt).apply {
                    lineWrap = true
                    wrapStyleWord = true
                }
            // The width at which the whole text fits one line.
            val width = JTextArea(prompt).preferredSize.width
            val panel = JPanel(BorderLayout())
            setContent {
                Box {
                    Column(modifier = SwingModifier.fillMaxWidth()) {
                        SwingNode(factory = { panel }) {
                            Column {
                                SwingNode(factory = { area }, modifier = declaration.modifier(this, width))
                            }
                        }
                    }
                }
            }
            settleWithPaint()
            assertEquals(declaration.placedWidth(width), area.width, "the text is placed at its declared width")
            val wrappedHeight =
                JTextArea(prompt)
                    .apply {
                        lineWrap = true
                        wrapStyleWord = true
                        setSize(declaration.placedWidth(width), Short.MAX_VALUE.toInt())
                    }.preferredSize.height
            assertEquals(
                wrappedHeight,
                area.height,
                "the text's height at the width it is placed at",
            )
            assertEquals(area.height, panel.height, "the stock panel is allocated its child's height")
            assertEquals(
                area.height,
                panel.preferredSize.height,
                "the maximum intrinsic height reads the text at the width it is placed at",
            )
            assertEquals(
                area.height,
                panel.minimumSize.height,
                "the minimum intrinsic height reads the text at the width it is placed at",
            )
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
