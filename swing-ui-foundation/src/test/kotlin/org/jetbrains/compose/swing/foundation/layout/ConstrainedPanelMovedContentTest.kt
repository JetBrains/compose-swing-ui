package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.setContent as setSwingContent

/** A [Layout] panel moved between compositions keeps measuring once the composition it left is disposed. */
class ConstrainedPanelMovedContentTest {
    @Test
    fun aPanelMovedToAnotherCompositionUnderAnEnclosingNodeKeepsMeasuringOnceTheOldOneIsDisposed() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val policy = CountingWidthPolicy(width)
            var inOuter by mutableStateOf(false)
            val host = JPanel(BorderLayout())
            lateinit var context: CompositionContext
            lateinit var content: @Composable () -> Unit
            setContent {
                context = rememberCompositionContext()
                content =
                    remember {
                        movableContentOf { SwingNode(factory = { JPanel(BorderLayout()) }) { Layout(policy) } }
                    }
                SwingNode(factory = { host })
                if (inOuter) content()
            }
            awaitIdle()
            val nested = host.setSwingContent(parent = context) { if (!inOuter) content() }
            awaitIdle()
            inOuter = true
            awaitIdle()
            nested.dispose()
            awaitIdle()
            val measures = policy.measures

            width.intValue = 30
            awaitIdle()

            assertTrue(policy.measures > measures, "a changed policy read measures the panel again")
        }

    /** Takes the width it reads as the extent of the panel, 10 tall, and counts its measures. */
    private class CountingWidthPolicy(
        private val width: MutableIntState,
    ) : MeasurePolicy {
        var measures = 0
            private set

        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints,
        ): MeasureResult {
            measures++
            val extent = width.intValue
            val placeables = measurables.map { it.measure(Constraints(extent, extent, 10, 10)) }
            return layout(extent, 10) { placeables.forEach { it.place(0, 0) } }
        }
    }
}
