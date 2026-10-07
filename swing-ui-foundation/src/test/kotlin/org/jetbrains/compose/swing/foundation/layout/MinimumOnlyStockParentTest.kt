package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** A container under a stock parent that reads its minimum size alone, and grants it a width of its own. */
class MinimumOnlyStockParentTest {
    @Test
    fun aContainerAParentGrantsAWidthAfterReadingItsMinimumSizeAloneTakesTheMinimumHeightForThatWidthAfterOnePaint() {
        for (granted in listOf(WIDER, 60)) {
            runComposeSwingTest {
                setContent {
                    SwingNode(
                        factory = { JPanel(MinimumGrantingLayout(granted)) },
                        modifier = SwingModifier.preferredSize(2 * WIDER, WIDER),
                    ) {
                        Layout(
                            measurePolicy = HalfWidthMinimumPolicy,
                            modifier = SwingModifier.testTag("box"),
                        )
                    }
                }
                val box = onNodeWithTag("box").fetch<JComponent>()
                awaitIdle()

                assertEquals(
                    Dimension(granted, PREFERRED_WIDTH / 2),
                    box.size,
                    "$granted wide: the parent grants the width at the minimum height at the preferred width",
                )

                settleWithPaint()

                assertEquals(
                    Dimension(granted, granted / 2),
                    box.size,
                    "$granted wide: the paint finds the minimum height at the width",
                )
            }
        }
    }

    private companion object {
        const val WIDER = 300
        const val PREFERRED_WIDTH = 100
        const val MINIMUM_WIDTH = 50
    }

    /** Lays its one child out at the [granted] width and the minimum height it reads, and reads no other size. */
    private class MinimumGrantingLayout(
        private val granted: Int,
    ) : LayoutManager {
        override fun layoutContainer(parent: Container) {
            val child = parent.getComponent(0)
            child.setBounds(0, 0, granted, child.minimumSize.height)
        }

        override fun preferredLayoutSize(parent: Container): Dimension = parent.preferredSize

        override fun minimumLayoutSize(parent: Container): Dimension = parent.minimumSize

        override fun addLayoutComponent(
            name: String?,
            component: Component,
        ) = Unit

        override fun removeLayoutComponent(component: Component) = Unit
    }

    /** Prefers [PREFERRED_WIDTH] and can shrink to [MINIMUM_WIDTH], with a minimum height of half the width. */
    private object HalfWidthMinimumPolicy : MeasurePolicy {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult = layout(constraints.maxWidth, constraints.maxWidth / 2) {}

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = MINIMUM_WIDTH

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurables: List<IntrinsicMeasurable>,
            height: Int,
        ): Int = PREFERRED_WIDTH

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = width / 2

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurables: List<IntrinsicMeasurable>,
            width: Int,
        ): Int = width / 2
    }
}
