package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A stock child whose height follows its width, in a container whose parent measures it. */
class StockChildGrantedWidthTest {
    @Test
    fun aContainerMeasuredByAParentThatDoesNotPlaceItLeavesItsStockChildWhereItWas() {
        val children: Map<String, Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>> =
            mapOf(
                "filling the width" to ({ ResizeCountingTextArea() } to { SwingModifier.fillMaxWidth() }),
                "at a fixed height" to
                    ({ ResizeCountingTextArea() } to { SwingModifier.fillMaxWidth().height(SLOT_HEIGHT / 4) }),
                "with paint outsets" to
                    ({ DecoratedTextArea(WRAPPING_TEXT) } to { SwingModifier.fillMaxWidth().shadow(16, Color.BLACK) }),
            )
        for ((name, child) in children) {
            val (create, childModifier) = child
            runComposeSwingTest {
                val area = create()
                var width by mutableIntStateOf(WIDTH)
                var placed by mutableStateOf(true)
                setContent {
                    borderLayoutSlot(BorderLayout.CENTER)(width) { modifier ->
                        Layout(
                            content = {
                                Box(modifier = SwingModifier.testTag("box")) {
                                    SwingNode(factory = { area }, modifier = childModifier())
                                }
                            },
                            modifier = modifier.testTag("parent"),
                            measurePolicy =
                                MeasurePolicy { measurables, constraints ->
                                    val placeables = measurables.map { it.measure(constraints) }
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        if (placed) placeables.forEach { it.place(0, 0) }
                                    }
                                },
                        )
                    }
                }
                settleWithPaint()
                placed = false
                awaitIdle()
                val box = onNodeWithTag("box").fetch<JComponent>()
                val parent = onNodeWithTag("parent").fetch<JComponent>()
                val bounds = area.bounds

                width = WIDTH - 60
                awaitIdle()

                assertEquals(bounds, area.bounds, "$name: measured at another width by a parent placing nothing")
                assertTrue(box.isValid && parent.isValid, "$name: and the measure leaves both containers valid")
            }
        }
    }

    @Test
    fun stockChildrenReuseAnUnchangedGrantAndApplyANewWidthOnce() {
        for (foreignPanel in listOf(false, true)) {
            runComposeSwingTest {
                var sibling by mutableStateOf("Before")
                var width by mutableIntStateOf(WIDTH)
                var resizes = 0
                val child: JComponent =
                    if (foreignPanel) {
                        CountingSwingPanel(JPanel().apply { repeat(500) { add(JLabel("Counted")) } })
                    } else {
                        ResizeCountingTextArea()
                    }
                child.addComponentListener(
                    object : ComponentAdapter() {
                        override fun componentResized(event: ComponentEvent) {
                            resizes++
                        }
                    },
                )
                setContent {
                    borderLayoutSlot(if (foreignPanel) BorderLayout.NORTH else BorderLayout.CENTER)(width) { modifier ->
                        Column(modifier = modifier.testTag("column")) {
                            SwingNode(factory = { child }, modifier = SwingModifier.fillMaxWidth())
                            Label(sibling)
                        }
                    }
                }
                settleWithPaint()
                val bounds = child.bounds
                (child as? CountingSwingPanel)?.layouts = 0
                resizes = 0
                (child as? ResizeCountingTextArea)?.resizes = 0
                withRecordedRepaints { recorder ->
                    sibling = "After"
                    awaitIdle()
                    assertFalse(onNodeWithTag("column").fetch<JComponent>() in recorder.relayouts)
                }
                assertEquals(bounds, child.bounds, "same grant preserves bounds")
                assertEquals(0, resizes, "same grant sends no resize event")
                assertEquals(
                    0,
                    (child as? ResizeCountingTextArea)?.resizes ?: (child as CountingSwingPanel).layouts,
                    "same grant does no child work",
                )
                (child as? CountingSwingPanel)?.queries = 0
                width = WIDTH + 80
                awaitIdle()
                assertEquals(width, child.width, "new grant reaches the child")
                if (foreignPanel) {
                    assertEquals(1, resizes, "new grant resizes once")
                    assertEquals(1, (child as CountingSwingPanel).layouts, "new grant lays the foreign panel out once")
                    assertEquals(3, child.queries, "width read, height read and layout each read preferences once")
                }
            }
        }
    }
}

private const val WIDTH = 320
