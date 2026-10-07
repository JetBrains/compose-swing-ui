package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferredThenMinimumStockParentTest {
    @Test
    fun aMinimumReadAtAnotherWidthKeepsTheStandingPreferredHeightForTheFirstMeasure() =
        runComposeSwingTest {
            val measuredHeights = mutableListOf<Int>()
            setContent {
                SwingNode(
                    factory = { JPanel(PreferredThenMinimumLayout()) },
                    modifier = SwingModifier.preferredSize(400, 400),
                ) {
                    Layout(
                        measurePolicy =
                            object : MeasurePolicy {
                                override fun MeasureScope.measure(
                                    measurables: List<Measurable>,
                                    constraints: Constraints,
                                ): MeasureResult {
                                    measuredHeights.add(constraints.maxHeight)
                                    return layout(constraints.maxWidth, constraints.maxHeight) {}
                                }

                                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                                    measurables: List<IntrinsicMeasurable>,
                                    height: Int,
                                ): Int = 50

                                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                                    measurables: List<IntrinsicMeasurable>,
                                    height: Int,
                                ): Int = 100

                                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                                    measurables: List<IntrinsicMeasurable>,
                                    width: Int,
                                ): Int = width / 2

                                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                                    measurables: List<IntrinsicMeasurable>,
                                    width: Int,
                                ): Int = 2 * width
                            },
                    )
                }
            }
            awaitIdle()

            assertEquals(400, measuredHeights.first())
        }

    private class PreferredThenMinimumLayout : LayoutManager {
        override fun layoutContainer(parent: Container) {
            val child = parent.getComponent(0)
            val preferred = child.preferredSize
            if (child.width == 0) assertEquals(Dimension(100, 200), preferred)
            child.setSize(300, preferred.height)
            assertEquals(Dimension(50, 150), child.minimumSize)
            child.setBounds(0, 0, 200, preferred.height)
        }

        override fun preferredLayoutSize(parent: Container): Dimension = parent.preferredSize

        override fun minimumLayoutSize(parent: Container): Dimension = parent.minimumSize

        override fun addLayoutComponent(
            name: String?,
            component: Component,
        ) = Unit

        override fun removeLayoutComponent(component: Component) = Unit
    }
}
