// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.Color
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.assertEquals

/** `onPlaced` reports in the layout coordinates of a parent whose shadow moves its layout origin. */
class ShadowedParentOnPlacedTest {
    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource(
        "StockLabel, ShadowedBox",
        "StockLabel, ShadowedLayout",
        "ShadowedBox, ShadowedBox",
        "ShadowedBox, ShadowedLayout",
    )
    fun placementReportsStayInTheLayoutCoordinatesOfAShadowedParent(
        child: ReportedChild,
        parent: ShadowedParent,
    ) = runComposeSwingTest {
        val reported = mutableListOf<Rectangle>()
        val reporting = SwingModifier.onPlaced { reported += it }
        val reportingChild: @Composable (SwingModifier) -> Unit = { placed ->
            when (child) {
                ReportedChild.StockLabel -> {
                    Label(text = "child", modifier = placed.then(reporting), style = { preferredSize(40, 20) })
                }

                ReportedChild.ShadowedBox -> {
                    Box(
                        modifier =
                            placed
                                .styleable { preferredSize(40, 20) }
                                .then(reporting)
                                .shadow(3, Color.BLACK),
                    ) {}
                }
            }
        }

        setContent {
            Box {
                when (parent) {
                    ShadowedParent.ShadowedBox -> {
                        Box(modifier = SwingModifier.testTag("parent").shadow(6, Color.BLACK)) {
                            reportingChild(SwingModifier.padding(start = 5, top = 7))
                        }
                    }

                    ShadowedParent.ShadowedLayout -> {
                        Layout(
                            content = { reportingChild(SwingModifier) },
                            modifier = SwingModifier.testTag("parent").shadow(6, Color.BLACK),
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(100, 40) { placeable.place(5, 7) }
                            },
                        )
                    }
                }
            }
        }
        awaitIdle()

        onAllNodesOfType<JComponent>()
            .filterToOne(SwingMatcher.hasTestTag("parent"))
            .assertProperty(true, "the shadow moves the parent's layout origin off the origin of its bounds") {
                paintOutsets.left > 0
            }
        assertEquals(Rectangle(5, 7, 40, 20), reported.last(), "the report must name where the policy put the child")
    }

    enum class ReportedChild {
        StockLabel,
        ShadowedBox,
    }

    enum class ShadowedParent {
        ShadowedBox,
        ShadowedLayout,
    }
}
