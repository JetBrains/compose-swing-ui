// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertTrue

/** A [Layout] panel moved between compositions keeps measuring once the composition it left is disposed. */
class ConstrainedPanelMovedContentTest {
    @Test
    fun aPanelMovedToAnotherCompositionUnderAnEnclosingNodeKeepsMeasuringOnceTheOldOneIsDisposed() =
        runComposeSwingTest {
            val width = mutableIntStateOf(10)
            val policy = CountingWidthPolicy(width)
            var inOuter by mutableStateOf(false)
            var hosted by mutableStateOf(true)
            lateinit var content: @Composable () -> Unit
            setContent {
                content =
                    remember {
                        movableContentOf { SwingNode(factory = { JPanel(BorderLayout()) }) { Layout(policy) } }
                    }
                if (hosted) NestedContentHost(layout = BorderLayout()) { if (!inOuter) content() }
                if (inOuter) content()
            }
            awaitIdle()
            inOuter = true
            awaitIdle()
            hosted = false
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
