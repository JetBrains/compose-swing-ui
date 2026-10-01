package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.OnDemandComposition
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

    @Test
    fun removingTheLastDecorationUnderASwingParentRequestsAnotherValidation() =
        runComposeSwingTest {
            val step = RemovingOutsetsElement()
            val flow = ChangingOutsetsFlowLayout()
            setContent {
                var hasDecoration by remember { mutableStateOf(true) }
                val context = rememberCompositionContext()
                val composition =
                    remember(context) {
                        OnDemandComposition(context) {
                            SwingNode(
                                factory = { Card() },
                                modifier =
                                    if (hasDecoration) {
                                        SwingModifier
                                            .testTag("removing-card")
                                            .paintOutsets(PaintOutsets.None)
                                            .then(step)
                                    } else {
                                        SwingModifier.testTag("removing-card")
                                    },
                            )
                        }
                    }
                DisposableEffect(composition) { onDispose { composition.dispose() } }
                SwingNode(
                    factory = {
                        JPanel(flow).apply {
                            add(checkNotNull(composition.recompose {}))
                            flow.afterLayout = {
                                assertTrue(Thread.holdsLock(treeLock), "the modifier is removed during validation")
                                assertEquals(Dimension(128, 88), getComponent(0).size)
                                composition.recompose { hasDecoration = false }
                            }
                        }
                    },
                    modifier = SwingModifier.preferredSize(200, 200),
                )
            }

            awaitIdle()

            assertTrue(step.node.removedUnderTreeLock, "the final decoration node was removed during validation")
            val card = onNodeWithTag("removing-card").fetch<JComponent>()
            assertEquals(Insets(0, 0, 0, 0), card.insets)
            assertEquals(Dimension(120, 80), card.size, "the next validation sizes the undecorated card")
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

private class RemovingOutsetsElement : SwingModifier.NodeElement<Card, RemovingOutsetsNode>() {
    val node = RemovingOutsetsNode()

    override val targetType: Class<Card> get() = Card::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "removingOutsets"

    override fun create(): RemovingOutsetsNode = node

    override fun update(node: RemovingOutsetsNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class RemovingOutsetsNode : DecorationModifierNode<Card>() {
    override val outsets: Insets get() = Insets(4, 4, 4, 4)

    var removedUnderTreeLock: Boolean = false

    override fun onRemovedFromDecoration() {
        removedUnderTreeLock = Thread.holdsLock(component.treeLock)
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) = content(graphics, width, height)
}
