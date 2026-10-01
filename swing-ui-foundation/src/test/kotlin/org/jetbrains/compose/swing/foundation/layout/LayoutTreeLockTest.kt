package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertTrue

class LayoutTreeLockTest {
    @Test
    fun nestedAndDirectMeasurementRunWithTheComponentTreeLockHeld() =
        runComposeSwingTest {
            val treeLockHeld = ArrayList<Boolean>()
            val child = TreeLockRecordingPanel(treeLockHeld)
            setContent {
                Layout(
                    content = { SwingNode(factory = { child }) },
                    measurePolicy = { measurables, _ ->
                        val width = measurables.single().maxIntrinsicWidth(Int.MAX_VALUE)
                        layout(width, 20) {
                            measurables.single().measure(Constraints()).place(0, 0)
                        }
                    },
                    modifier = SwingModifier.testTag("layout"),
                )
            }
            assertTrue(treeLockHeld.isNotEmpty())
            assertTrue(treeLockHeld.all { it }, "nested policy measurement holds the component tree lock")

            treeLockHeld.clear()
            val constrainable = onNodeWithTag("layout").fetch<ConstrainedPanel>()
            constrainable.policyLayout.measurables.invalidate()
            constrainable.measure(Constraints())

            assertTrue(treeLockHeld.isNotEmpty())
            assertTrue(treeLockHeld.all { it }, "direct constrained measurement holds the component tree lock")
        }

    @Test
    fun directAlignmentLineReplayRunsWithTheComponentTreeLockHeld() =
        runComposeSwingTest {
            val treeLockHeld = ArrayList<Boolean>()
            lateinit var panel: ConstrainedPanel
            val line =
                HorizontalAlignmentLine { first, second ->
                    treeLockHeld += Thread.holdsLock(panel.treeLock)
                    minOf(first, second)
                }
            setContent {
                Layout(
                    content = {
                        repeat(2) { position ->
                            Layout(measurePolicy = { _, _ -> layout(10, 10, mapOf(line to position)) {} })
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeables = measurables.map { it.measure(Constraints()) }
                        layout(20, 10) {
                            placeables.forEachIndexed { index, placeable -> placeable.place(index * 10, 0) }
                        }
                    },
                    modifier = SwingModifier.testTag("line-layout"),
                )
            }
            panel = onNodeWithTag("line-layout").fetch<ConstrainedPanel>()

            panel.alignmentLines

            assertTrue(treeLockHeld.isNotEmpty())
            assertTrue(treeLockHeld.all { it }, "alignment-line replay holds the component tree lock")
        }
}

private class TreeLockRecordingPanel(
    private val treeLockHeld: MutableList<Boolean>,
) : JPanel() {
    override fun getPreferredSize(): Dimension {
        treeLockHeld += Thread.holdsLock(treeLock)
        return Dimension(20, 20)
    }
}
