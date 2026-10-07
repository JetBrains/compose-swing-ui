package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A stock child whose height follows its width, in a container whose parent measures it. */
class StockChildGrantedWidthTest {
    @Test
    fun aContainerMeasuredByAParentThatDoesNotPlaceItLeavesItsStockChildWhereItWas() {
        val children: Map<String, Pair<() -> JTextArea, ConstrainedScope.() -> SwingModifier>> =
            mapOf(
                "filling the width" to ({ ResizeCountingTextArea() } to { SwingModifier.fillMaxWidth() }),
                "at a fixed height" to
                    ({ ResizeCountingTextArea() } to { SwingModifier.fillMaxWidth().height(HEIGHT / 4) }),
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
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                        Layout(
                            content = {
                                Box(modifier = SwingModifier.testTag("box")) {
                                    SwingNode(factory = { area }, modifier = childModifier())
                                }
                            },
                            modifier = SwingModifier.center().testTag("parent"),
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
    fun aStockPanelGrantedTheWidthItHoldsIsNotLaidOutAgainAndAWidthChangeLaysItOutOnce() =
        runComposeSwingTest {
            var label by mutableStateOf("Before")
            var width by mutableIntStateOf(WIDTH)
            val panel = LayoutCountingPanel()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, HEIGHT)) {
                    Column(modifier = SwingModifier.north()) {
                        SwingNode(factory = { panel }, modifier = SwingModifier.fillMaxWidth())
                        Label(label)
                    }
                }
            }
            settleWithPaint()
            panel.layouts = 0

            label = "After"
            awaitIdle()
            assertEquals(0, panel.layouts, "a pass granting the panel the width it holds lays it out no more")

            width = WIDTH + 80
            awaitIdle()
            assertEquals(1, panel.layouts, "and a pass granting it another width lays it out once")
        }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 600
    }
}

/** A panel of many children counting how often it is laid out. */
private class LayoutCountingPanel : JPanel(BorderLayout()) {
    var layouts = 0

    init {
        add(JPanel().apply { repeat(500) { add(JLabel("Counted")) } })
    }

    override fun doLayout() {
        layouts++
        super.doLayout()
    }
}
