package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.DecorationModifierNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaintOutsetsDuringSwingValidationTest {
    @Test
    fun aDecorationChangedAfterASwingParentSizesItsChildIsMeasuredAgain() =
        runComposeSwingTest {
            val step = ChangingOutsetsElement()
            val flow = ChangingOutsetsFlowLayout()
            setContent {
                SwingNode(
                    factory = { JPanel(flow) },
                    modifier = SwingModifier.testTag("parent").preferredSize(200, 200),
                ) {
                    SwingNode(
                        factory = { Card() },
                        modifier = SwingModifier.testTag("card").paintOutsets(PaintOutsets.None).then(step),
                    )
                }
            }
            val parent = onNodeWithTag("parent").fetch<JComponent>()
            val card = onNodeWithTag("card").fetch<JComponent>()
            assertEquals(Dimension(128, 88), card.size)
            flow.afterLayout = {
                assertTrue(Thread.holdsLock(parent.treeLock), "the decoration changes during Swing validation")
                step.node.reach = 12
                step.node.invalidateDecoration()
                assertEquals(Dimension(128, 88), card.size, "the running pass already sized the child")
            }

            parent.revalidate()
            awaitIdle()

            assertEquals(Insets(12, 12, 12, 12), card.insets)
            assertEquals(Dimension(144, 104), card.size, "the next validation sizes the child by its new insets")
        }
}

private class ChangingOutsetsFlowLayout : FlowLayout(FlowLayout.LEADING, 0, 0) {
    var afterLayout: (() -> Unit)? = null

    override fun layoutContainer(target: Container) {
        super.layoutContainer(target)
        val action = afterLayout
        afterLayout = null
        action?.invoke()
    }
}

private class ChangingOutsetsElement : SwingModifier.NodeElement<Card, ChangingOutsetsNode>() {
    val node = ChangingOutsetsNode()

    override val targetType: Class<Card> get() = Card::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "changingOutsets"

    override fun create(): ChangingOutsetsNode = node

    override fun update(node: ChangingOutsetsNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class ChangingOutsetsNode : DecorationModifierNode<Card>() {
    var reach: Int = 4

    override val outsets: Insets get() = Insets(reach, reach, reach, reach)

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) = content(graphics, width, height)
}
