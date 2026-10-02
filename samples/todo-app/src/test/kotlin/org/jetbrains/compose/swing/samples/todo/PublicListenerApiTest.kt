package org.jetbrains.compose.swing.samples.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.reconcileWithChildren
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.CardLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A registration built entirely from public API, the way a third-party caller reaching for the scoped
 * [listener] overload directly (rather than through the library's own `changeListener`) would build one.
 */
private val CHANGE_REGISTRATION =
    CallbackRegistration<JSlider, (ChangeEvent) -> Unit, ChangeListener>(
        adapter = { callback -> ChangeListener { event -> callback.report { it(event) } } },
        registration =
            ListenerRegistration(
                name = "changeListener",
                attach = { component, changeListener -> component.addChangeListener(changeListener) },
                detach = { component, changeListener -> component.removeChangeListener(changeListener) },
            ),
    )

@Composable
private fun CardStack(
    selected: Int,
    onSelectionLoss: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val reported = remember { intArrayOf(-1) }
    SwingNode(
        factory = { JPanel(CardLayout()) },
        update = {
            reconcileWithChildren {
                val last = component.componentCount - 1
                if (selected > last && reported[0] != last) {
                    reported[0] = last
                    report { onSelectionLoss(last) }
                }
            }
        },
        content = content,
    )
}

/**
 * Exercises the public scoped [listener] overload from outside the library without an
 * `@InternalSwingUiApi` opt-in. The tests check the receiver the listener runs with, that a supertype
 * registration attaches to a subtype target, and that a throw from a scoped listener's callback or from a
 * node's own report is reported at the next gate while the composition keeps applying passes.
 */
class PublicListenerApiTest {
    @Test
    fun theScopedListenerOverloadRunsOnEventWithTheSourceAsReceiver() =
        runComposeSwingTest {
            val value = mutableStateOf(50)
            var seenSource: JSlider? = null
            var seenValue: Int? = null
            setContent {
                Slider(
                    value = value.value,
                    onValueChange = {},
                    modifier =
                        SwingModifier.listener(
                            targetType = JSlider::class,
                            registration = CHANGE_REGISTRATION,
                            onEvent = { _: ChangeEvent ->
                                seenSource = this
                                seenValue = this.value
                            },
                        ),
                )
            }
            value.value = 75
            awaitIdle()

            assertEquals(75, seenValue)
            assertEquals(75, seenSource?.value)
        }

    @Test
    fun aSupertypeRegistrationWorksForASubtypeTarget() =
        runComposeSwingTest {
            val componentRegistration =
                CallbackRegistration<Component, (ChangeEvent) -> Unit, ChangeListener>(
                    adapter = { callback -> ChangeListener { event -> callback.report { it(event) } } },
                    registration =
                        ListenerRegistration(
                            name = "changeListener",
                            attach = {
                                component,
                                changeListener,
                                ->
                                (component as JSlider).addChangeListener(changeListener)
                            },
                            detach = {
                                component,
                                changeListener,
                                ->
                                (component as JSlider).removeChangeListener(changeListener)
                            },
                        ),
                )
            var fired = false
            setContent {
                Slider(
                    value = 50,
                    onValueChange = {},
                    modifier =
                        SwingModifier.listener(
                            targetType = JSlider::class,
                            registration = componentRegistration,
                            onEvent = { fired = true },
                        ),
                )
            }
            val slider = onNodeOfType<JSlider>().fetch()
            slider.value = 75
            awaitIdle()
            assertTrue(fired, "supertype registration must fire on subtype target")
        }

    @Test
    fun aThrowingNodeReportFailsTheNextGate() =
        runComposeSwingTest {
            val boom = IllegalStateException("boom")
            var cards by mutableStateOf(3)
            var text by mutableStateOf("a")
            val lost = mutableListOf<Int>()
            setContent {
                CardStack(
                    selected = 2,
                    onSelectionLoss = {
                        lost += it
                        throw boom
                    },
                ) {
                    repeat(cards) { Label(if (it == 0) text else "card $it") }
                }
            }
            val label = onNodeWithText("a").fetch<JLabel>()

            cards = 1
            assertSame(boom, assertFailsWith<IllegalStateException> { awaitIdle() })
            assertEquals(listOf(0), lost)

            text = "b"
            awaitIdle()
            assertEquals("b", label.text, "the composition applies the pass after the throw")
        }

    @Test
    fun aThrowingScopedListenerFailsTheNextGate() =
        runComposeSwingTest {
            val boom = IllegalStateException("boom")
            var value by mutableStateOf(1)
            val seen = mutableListOf<Int>()
            setContent {
                Slider(
                    value = value,
                    onValueChange = {},
                    modifier =
                        SwingModifier.listener(
                            targetType = JSlider::class,
                            registration = CHANGE_REGISTRATION,
                            onEvent = { _: ChangeEvent ->
                                seen += this.value
                                if (this.value == 2) throw boom
                            },
                        ),
                )
            }
            val slider = onNodeOfType<JSlider>().fetch()

            value = 2
            assertSame(boom, assertFailsWith<IllegalStateException> { awaitIdle() })
            assertEquals(2, slider.value, "the write the listener was notified from ran to its end")

            value = 3
            awaitIdle()
            assertEquals(3, slider.value, "the composition applies the pass after the throw")
            assertEquals(listOf(2, 3), seen)
        }
}
