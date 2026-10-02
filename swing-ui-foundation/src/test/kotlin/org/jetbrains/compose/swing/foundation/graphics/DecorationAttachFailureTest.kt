package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Recomposer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.Graphics2D
import java.util.stream.Stream
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DecorationAttachFailureTest {
    @ParameterizedTest(name = "additive={0}, refused with \"{1}\"")
    @MethodSource("refusals")
    fun aRefusedDecorationNodeThrowsDuringApplyWithoutReporting(
        additive: Boolean,
        expectedMessage: String,
    ) = runSwingTest {
        val parent = Recomposer(coroutineContext)
        val node = Step()
        try {
            val reported =
                reportedUncaught {
                    val failure =
                        assertFailsWith<IllegalStateException> {
                            JPanel().setContent(parent = parent) {
                                SwingNode(
                                    factory = { JButton() },
                                    modifier = SwingModifier.then(Element(node, additive)),
                                )
                            }
                        }
                    assertContains(failure.message.orEmpty(), expectedMessage)
                    assertFalse(node.isAttached)
                }
            assertEquals(emptyList(), reported)
        } finally {
            parent.cancel()
        }
    }

    private class Element<N : SwingModifier.ComponentNode<Component>>(
        private val node: N,
        override val additive: Boolean,
    ) : SwingModifier.NodeElement<Component, N>() {
        override val targetType: Class<Component> get() = Component::class.java

        override fun create(): N = node

        override fun update(node: N) = Unit

        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private class Step : DecorationModifierNode<Component>() {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) = content(graphics, width, height)
    }

    companion object {
        @JvmStatic
        fun refusals(): Stream<Arguments> =
            Stream.of(
                Arguments.of(true, "requires a ${Decoratable::class.java.name} target"),
                Arguments.of(false, "must be created by an additive element"),
            )
    }
}
