// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.apply
import org.jetbrains.compose.swing.style.location
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.size
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnPlacedBoundsTest {
    /**
     * A component moved out of a shadowed box to a parent that keeps its Swing bounds still reports: the box placed
     * it off its Swing origin by the shadow's outsets, and the new parent does not.
     */
    @Test
    fun movedContentKeepingItsSwingBoundsReportsItsNewPlacement() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            val report: (Rectangle) -> Unit = { reported += it }
            var outside by mutableStateOf<Rectangle?>(null)

            setContent {
                val moving =
                    remember {
                        movableContentOf<SwingModifier, SwingStyle<LabelStyleScope>> { modifier, style ->
                            Label(text = "child", modifier = modifier.onPlaced(report), style = style)
                        }
                    }
                LayeredPane {
                    Box(
                        modifier =
                            SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).styleable {
                                size(50, 50)
                                location(0, 0)
                            },
                    ) {
                        Box(modifier = SwingModifier.shadow(4, Color.BLACK)) {
                            if (outside == null) moving(SwingModifier, SwingStyle { preferredSize(10, 10) })
                        }
                    }
                    outside?.let {
                        moving(
                            SwingModifier.layer(JLayeredPane.DEFAULT_LAYER),
                            SwingStyle {
                                location(it.x, it.y)
                                size(it.width, it.height)
                            },
                        )
                    }
                }
            }
            awaitIdle()
            assertEquals(Rectangle(0, 0, 10, 10), reported.last(), "where the box's policy placed it")
            onNodeOfType<JLabel>().fetch().bounds.let { bounds ->
                assertTrue(
                    bounds.x > 0 && bounds.y > 0,
                    "the shadow's outsets move it off the box's Swing origin: $bounds",
                )
                reported.clear()

                outside = bounds
                awaitIdle()

                onNodeOfType<JLabel>().assertProperty(bounds, "the component keeps its Swing bounds") { this.bounds }
                assertEquals(
                    listOf(bounds),
                    reported,
                    "a component moved to a parent that does not place it by paint outsets",
                )
            }
        }

    @Test
    fun aChildOfADecoratedBorderLayoutPanelReportsWhereBorderLayoutPlacedIt() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel(BorderLayout()) },
                        modifier = SwingModifier.shadow(8, Color.BLACK),
                    ) {
                        Label(
                            text = "child",
                            modifier = SwingModifier.onPlaced { reported += it },
                            style = { preferredSize(40, 30) },
                        )
                    }
                }
            }
            awaitIdle()

            val outsets = blurOutsets(8)
            assertEquals(
                Rectangle(outsets, outsets, 40, 30),
                reported.last(),
                "where BorderLayout placed it, inside the panel's insets, and not shifted by its paint outsets again",
            )
        }

    @Test
    fun aDecoratedPanelLeavingABoxForTheSameSwingBoundsReportsItsNewLayoutBounds() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var outside by mutableStateOf<Rectangle?>(null)
            setContent {
                val moving =
                    remember {
                        movableContentOf<SwingStyle<ComponentStyleScope>> { style ->
                            SwingNode(
                                factory = { DecoratedPanel(BorderLayout()) },
                                modifier =
                                    SwingModifier
                                        .testTag("panel")
                                        .shadow(8, Color.BLACK)
                                        .onPlaced { reported += it }
                                        .styleable {
                                            preferredSize(40, 30)
                                            apply(style)
                                        },
                            )
                        }
                    }
                SwingNode(factory = { JLayeredPane() }) {
                    Box(
                        modifier =
                            SwingModifier.styleable {
                                size(100, 100)
                                location(0, 0)
                            },
                    ) {
                        if (outside == null) moving(SwingStyle)
                    }
                    outside?.let {
                        moving(
                            SwingStyle {
                                location(it.x, it.y)
                                size(it.width, it.height)
                            },
                        )
                    }
                }
            }
            awaitIdle()
            onNodeWithTag("panel").fetch<JComponent>().bounds.let { bounds ->
                assertEquals(
                    Rectangle(bounds.x + blurOutsets(8), bounds.y + blurOutsets(8), 40, 30),
                    reported.last(),
                    "onPlaced reports the layout bounds, inside the shadow's outsets",
                )
                reported.clear()

                outside = bounds
                awaitIdle()

                assertEquals(
                    bounds,
                    onNodeWithTag("panel").fetch<JComponent>().bounds,
                    "the panel keeps its Swing bounds",
                )
                assertEquals(listOf(bounds), reported, "its layout bounds are now its bounds, reported once")
            }
        }

    @Test
    fun aMoveIsReportedAsAPlacementAndNotAsAnExtent() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val extents = mutableListOf<Dimension>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .onPlaced { placements += it }
                                .onSizeChanged { extents += it },
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
                    )
                }
            }
            awaitIdle()
            placements.clear()
            extents.clear()

            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                placements,
                "a move changes where a component is, so it reports",
            )
            assertEquals(emptyList(), extents, "a move leaves the extent alone, so the extent report stays silent")
        }

    @Test
    fun aResizeIsReportedAsAPlacementToo() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var wide by mutableStateOf(true)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .onPlaced { reported += it },
                        style = {
                            size(if (wide) 100 else 40, 10)
                            location(0, 0)
                        },
                    )
                }
            }
            awaitIdle()
            reported.clear()

            wide = false
            awaitIdle()

            assertEquals(
                listOf(Rectangle(0, 0, 40, 10)),
                reported,
                "a resize is a placement, so it reports through it too",
            )
        }

    @Test
    fun declaringTheReportTwiceReportsTwice() =
        runComposeSwingTest {
            val first = mutableListOf<Rectangle>()
            val second = mutableListOf<Rectangle>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .onPlaced { first += it }
                                .onPlaced { second += it },
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
                    )
                }
            }
            awaitIdle()
            first.clear()
            second.clear()

            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                first,
                "each declaration is its own slot and reports on its own",
            )
            assertEquals(listOf(Rectangle(20, 0, 10, 10)), second, "the second declaration reports the same placement")
        }
}
