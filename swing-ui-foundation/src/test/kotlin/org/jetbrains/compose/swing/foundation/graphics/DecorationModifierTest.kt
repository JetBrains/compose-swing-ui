package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.lastElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Behavioral tests for [decoration]: what a chain declares reaches the component, in
 * declaration order, and a declaration whose values are unchanged leaves the component's painting alone.
 *
 * Painting is forced against an off-screen raster, so what is asserted is what the component would show.
 */
@Composable
private fun Box(
    modifier: SwingModifier = SwingModifier,
    content: @Composable () -> Unit = {},
) {
    SwingNode(
        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
        modifier = modifier,
        content = content,
    )
}

class DecorationModifierTest {
    @Test
    fun declarationsCombineInOrderRatherThanTheLastOneWinning() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier =
                        decorated {
                            SwingModifier
                                .testTag("decorated-panel")
                                .opaque(false)
                                .cut()
                                .preferredSize(Dimension(64, 64))
                                .fill(Color.BLUE)
                        },
                ) { Label(text = "child") }
                Box(
                    modifier =
                        decorated {
                            SwingModifier
                                .testTag("reversed-panel")
                                .opaque(false)
                                .fill(Color.BLUE)
                                .preferredSize(Dimension(64, 64))
                                .cut()
                        },
                ) { Label(text = "child") }
            }

            val clipOutside = onNodeWithTag("decorated-panel").captureToImage()
            val backgroundOutside = onNodeWithTag("reversed-panel").captureToImage()

            assertEquals(
                0,
                clipOutside.getRGB(0, 0),
                "A clip declared first is outermost, and elements of other kinds standing between the two " +
                    "change nothing: the chain is still the clip around the background.",
            )
            assertEquals(
                Color.BLUE.rgb,
                backgroundOutside.getRGB(0, 0),
                "Declared the other way round the background is outermost and fills the corners.",
            )
        }

    @Test
    fun aDecorationKeepsItsPlaceWhenAnotherKindOfSlotIsDeclaredBeforeIt() =
        runComposeSwingTest {
            var clipFirst by mutableStateOf(false)
            setContent {
                val leading =
                    if (clipFirst) {
                        decorated { SwingModifier.cut() }
                    } else {
                        SwingModifier.mouseListener { }
                    }
                Box(
                    modifier =
                        decorated {
                            leading
                                .testTag("decorated-panel")
                                .opaque(false)
                                .preferredSize(Dimension(64, 64))
                                .fill(Color.BLUE)
                        },
                ) {
                    Label(text = "child")
                }
            }
            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag("decorated-panel").captureToImage().getRGB(0, 0),
                "With no clip declared the background fills the corners.",
            )

            clipFirst = true
            awaitIdle()

            assertEquals(
                0,
                onNodeWithTag("decorated-panel").captureToImage().getRGB(0, 0),
                "A decoration taking the place a listener held stands where it is declared - before the " +
                    "background - rather than after the declarations that were already standing.",
            )
        }

    @Test
    fun aDeclarationWhoseValuesAreUnchangedDoesNotRepaint() =
        runComposeSwingTest {
            var generation by mutableIntStateOf(0)
            setContent {
                Box(
                    modifier =
                        decorated {
                            SwingModifier
                                .testTag("decorated-panel")
                                .name("generation-$generation")
                                .cut(8)
                        },
                ) { Label(text = "child") }
            }
            val panel = onNodeWithTag("decorated-panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                generation = 1
                awaitIdle()

                assertEquals("generation-1", panel.name, "The pass that changed the chain elsewhere was applied.")
                assertEquals(
                    0,
                    recorder.repaintsOf(panel),
                    "A clip rebuilt from unchanged values is the same declaration, so the component is left alone.",
                )

                generation = 2
                awaitIdle()

                assertEquals(
                    0,
                    recorder.repaintsOf(panel),
                    "Nothing about the decoration changed on the second pass either.",
                )
            }
        }

    @Test
    fun changingWhatADecorationDeclaresRepaints() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(8)
            setContent {
                Box(modifier = decorated { SwingModifier.testTag("decorated-panel").cut(radius) }) {
                    Label(text = "child")
                }
            }
            val panel = onNodeWithTag("decorated-panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                radius = 20
                awaitIdle()

                recorder.assertAskedToRepaint(panel, "a decoration declaring a different shape")
            }
        }

    @Test
    fun changingOnlyWhatTheStepsPaintDoesNotLayTheComponentOutAgain() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(8)
            setContent {
                Box(modifier = decorated { SwingModifier.testTag("decorated-panel").cut(radius) }) {
                    Label(text = "child")
                }
            }
            val panel = onNodeWithTag("decorated-panel").fetch<JComponent>()
            withRecordedRepaints { recorder ->
                radius = 20
                awaitIdle()

                assertEquals(
                    0,
                    recorder.relayoutsOver(panel),
                    "A clip reserves nothing, so a different shape repaints the component without laying it out again.",
                )
            }
        }

    @Test
    fun removingOneDeclarationLeavesTheOthersStanding() =
        runComposeSwingTest {
            var clipped by mutableStateOf(true)
            setContent {
                val base = SwingModifier.testTag("decorated-panel").opaque(false)
                val clip = decorated { if (clipped) base.cut() else base }
                Box(modifier = decorated { clip.fill(Color.BLUE) }) { Label(text = "child") }
            }
            assertEquals(
                0,
                onNodeWithTag("decorated-panel").captureToImage().getRGB(0, 0),
                "The clip stands while it is declared.",
            )

            clipped = false
            awaitIdle()

            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag("decorated-panel").captureToImage().getRGB(0, 0),
                "A declaration that leaves the chain takes its own decoration with it and leaves the rest standing.",
            )
        }

    @Test
    fun aDecorationOnAComponentThatPaintsThroughNoneIsRefused() =
        runComposeSwingTest {
            val sibling = AttachRecordingElement()
            val failure =
                assertFailsWith<IllegalStateException>(
                    "A stock widget paints through no decoration, so the declaration cannot be applied.",
                ) {
                    setContent {
                        Button(
                            text = "press",
                            onClick = {},
                            modifier = decorated { SwingModifier.then(sibling).cut() },
                        )
                    }
                }

            assertEquals(
                "Modifier element decoration requires a ${Decoratable::class.java.name} target, but the " +
                    "component is a ${JButton::class.java.name}",
                failure.message,
                "The library's own target-type check refuses the mismatch, naming the decoration element " +
                    "and the component it was handed.",
            )
            assertFalse(
                sibling.attached,
                "The whole chain is narrowed before any of it is created, so a sibling declared before the " +
                    "decoration never attaches either.",
            )
        }

    @Test
    fun aDecorationElementDescribesItselfByTheDecoratorItDeclares() {
        val decorator = Fill(Color.RED)

        val declared = decorated { SwingModifier.decoration(decorator) }.lastElement()

        assertEquals(
            "decoration" to mapOf("decorator" to decorator),
            declared.name to declared.declaredValues,
            "a decoration element must report the decorator it declares",
        )
    }
}

/** Declares a node recording whether it ever attached, additive so it takes its own place in the chain. */
private class AttachRecordingElement : SwingModifier.NodeElement<Component, AttachRecordingElement.Node>() {
    var attached: Boolean = false

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): Node = Node(onAttached = { attached = true })

    override fun update(node: Node) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)

    class Node(
        private val onAttached: () -> Unit,
    ) : SwingModifier.ComponentNode<Component>() {
        override fun onAttach() = onAttached()
    }
}
