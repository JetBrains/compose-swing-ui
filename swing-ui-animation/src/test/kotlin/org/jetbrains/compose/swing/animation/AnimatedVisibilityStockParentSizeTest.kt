package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/** An [AnimatedVisibility] under a stock parent, which asks its sizes with no constraints. */
class AnimatedVisibilityStockParentSizeTest {
    /**
     * A container that starts to shrink answers the content's own size before its first layout, which has no extent for
     * the ratio. androidx measures the content with the constraints its parent hands the container, on every frame, so
     * it lays the content out at the width the parent asks and answers the ratio's height. Aligning with androidx turns
     * this test red.
     */
    @Test
    fun `under a stock parent a container that starts to shrink answers the content's own size first`() {
        val exits =
            mapOf(
                "shrinkVertically" to shrinkVertically(),
                "shrinkOut" to shrinkOut(),
            )
        for ((kind, exit) in exits) {
            runComposeSwingTest {
                mainClock.autoAdvance = false
                val parent = AnsweredSizesLayout()
                setContent {
                    SwingNode(factory = { JPanel(parent) }) {
                        val state = remember { MutableTransitionState(true).apply { targetState = false } }
                        AnimatedVisibility(
                            visibleState = state,
                            modifier = SwingModifier.testTag(CONTAINER),
                            enter = EnterTransition.None,
                            exit = exit,
                        ) {
                            Label("Preview", modifier = SwingModifier.testTag(CONTENT).aspectRatio(RATIO))
                        }
                    }
                }
                awaitIdle()

                assertEquals(
                    onNodeWithTag(CONTENT).fetch<JComponent>().preferredSize,
                    parent.answers.first(),
                    "$kind: the first size the parent read",
                )
            }
        }
    }

    /**
     * A container shrinking under a parent that grants no width lays the content out at its own width, whatever width
     * the parent asked. androidx forwards an intrinsic query and measures the content with the constraints its parent
     * hands the container, on every frame, and an earlier query does not seed a later measurement. So the content is
     * laid out at the width the parent grants, which is none. Aligning with androidx turns this test red.
     */
    @Test
    fun `under a stock parent that grants no width the content keeps its own width whatever the parent asked`() {
        val asks =
            mapOf(
                "a width of its own after none" to
                    Pair({ child: Component -> askNoWidthThenWidth(child) }, PREFERRED_WIDTH),
                "no width after a width of its own" to
                    Pair({ child: Component -> askWidthThenNoWidth(child) }, PREFERRED_WIDTH),
            )
        for ((kind, askAndWidth) in asks) {
            val (ask, expectedWidth) = askAndWidth
            runComposeSwingTest {
                mainClock.autoAdvance = false
                val layout =
                    object : LayoutManager {
                        override fun layoutContainer(parent: Container) {
                            val child = parent.getComponent(0)
                            ask(child)
                            child.setBounds(0, 0, 0, child.preferredSize.height)
                        }

                        override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

                        override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

                        override fun addLayoutComponent(
                            name: String?,
                            component: Component,
                        ) = Unit

                        override fun removeLayoutComponent(component: Component) = Unit
                    }
                setContent {
                    SwingNode(factory = { JPanel(layout) }) {
                        val state = remember { MutableTransitionState(true).apply { targetState = false } }
                        AnimatedVisibility(
                            visibleState = state,
                            modifier = SwingModifier.testTag(CONTAINER),
                            enter = EnterTransition.None,
                            exit = shrinkOut(),
                        ) {
                            Label(
                                "Preview",
                                modifier =
                                    SwingModifier
                                        .testTag(CONTENT)
                                        .fillMaxWidth()
                                        .aspectRatio(RATIO)
                                        .preferredSize(PREFERRED_WIDTH, 20),
                            )
                        }
                    }
                }
                awaitIdle()

                assertEquals(
                    expectedWidth,
                    onNodeWithTag(CONTENT).fetch<JComponent>().width,
                    "$kind: the zero grant leaves the content at its own width",
                )
            }
        }
    }
}

/** Reads [child]'s preferred size with no width, then at [NARROW]. */
private fun askNoWidthThenWidth(child: Component) {
    child.preferredSize
    child.setSize(NARROW, 0)
    child.preferredSize
}

/** Reads [child]'s preferred size at [NARROW], then with no width. */
private fun askWidthThenNoWidth(child: Component) {
    child.setSize(NARROW, 0)
    child.preferredSize
    child.setSize(0, 0)
    child.preferredSize
}

/** A flow layout recording the size it reads for its child before each of its own sizes and layouts. */
private class AnsweredSizesLayout : FlowLayout() {
    val answers = mutableListOf<Dimension>()

    override fun preferredLayoutSize(target: Container): Dimension {
        record(target)
        return super.preferredLayoutSize(target)
    }

    override fun layoutContainer(target: Container) {
        record(target)
        super.layoutContainer(target)
    }

    private fun record(target: Container) {
        if (target.componentCount > 0) answers += target.getComponent(0).preferredSize
    }
}

private const val RATIO = 16f / 9f
private const val PREFERRED_WIDTH = 320
private const val NARROW = 160
private const val CONTENT = "content"
private const val CONTAINER = "container"
