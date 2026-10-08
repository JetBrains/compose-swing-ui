package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferredThenMinimumStockParentTest {
    @Test
    fun aContainerIsMeasuredAtItsGrantThenAtItsHeightAfterParentValidation() =
        runComposeSwingTest {
            val measuredHeights = mutableListOf<Int>()
            setStockContent(
                JPanel(
                    ScriptedStockLayout { child, _ ->
                        val first = child.width == 0
                        val preferred = child.preferredSize
                        if (first) assertEquals(Dimension(100, 200), preferred)
                        child.setSize(300, preferred.height)
                        assertEquals(Dimension(50, 50), child.minimumSize)
                        child.setBounds(0, 0, 200, preferred.height)
                    },
                ),
                width = 400,
                height = 400,
            ) {
                Layout(
                    ScriptedMeasurePolicy(
                        minimumWidth = { 50 },
                        preferredWidth = { 100 },
                        minimumHeight = { it / 2 },
                        preferredHeight = { 2 * it },
                        measureSize = {
                            measuredHeights.add(it.maxHeight)
                            Dimension(it.maxWidth, it.maxHeight)
                        },
                    ),
                )
            }
            awaitIdle()

            assertEquals(200, measuredHeights.first(), "the first measure is at the bounds the parent gave")

            val host = onNodeWithTag("host").fetch<JComponent>()
            host.revalidate()
            root.validate()
            awaitIdle()

            assertEquals(
                400,
                measuredHeights.last(),
                "explicit parent validation uses the height for its current width",
            )
        }

    @Test
    fun aContainersMinimumSizeAfterATemporaryWidthMatchesAWrappingTextArea() {
        val area = readsOf(inContainer = false)
        val container = readsOf(inContainer = true)

        lateinit var natural: Dimension
        runComposeSwingTest {
            val reference = wrappingTextArea()
            reference.setSize(reference.preferredSize)
            natural = reference.preferredSize
        }
        assertEquals(
            List(3) { natural },
            container.first(),
            "a container reports its first layout at its natural width",
        )
        assertEquals(area.drop(1), container.drop(1), "what a column and a wrapping text area report and are given")
    }

    /**
     * The sizes each layout reads from a wrapping text area, in a `Column` where [inContainer] holds, as the preferred
     * size, the minimum size before it sets another width and the minimum size after, then the bounds it gave.
     */
    private fun readsOf(inContainer: Boolean): List<Any> {
        lateinit var reads: List<Any>
        runComposeSwingTest {
            val sizePreferences = mutableListOf<List<Dimension>>()
            val layout =
                ScriptedStockLayout { child, _ ->
                    val preferred = child.preferredSize
                    val before = child.minimumSize
                    child.setSize(300, preferred.height)
                    sizePreferences += listOf(preferred, before, child.minimumSize)
                    child.setBounds(0, 0, 200, preferred.height)
                }
            setWrappingContent(layout, inContainer, width = 400, height = 400)
            awaitIdle()
            val host = onNodeWithTag("host").fetch<JComponent>()
            host.revalidate()
            root.validate()
            awaitIdle()
            val bounds = onNodeWithTag("host").fetch<JComponent>().getComponent(0).bounds
            reads = sizePreferences + listOf(bounds)
        }
        return reads
    }
}
