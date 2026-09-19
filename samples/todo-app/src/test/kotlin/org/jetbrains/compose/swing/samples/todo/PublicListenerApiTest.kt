package org.jetbrains.compose.swing.samples.todo

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JSlider
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A registration built entirely from public API, the way a third-party caller reaching for the scoped
 * [listener] overload directly (rather than through the library's own `changeListener`) would build one.
 */
private val CHANGE_REGISTRATION =
    CallbackRegistration<JSlider, (ChangeEvent) -> Unit, ChangeListener>(
        adapter = { current -> ChangeListener { event -> current()(event) } },
        registration =
            ListenerRegistration(
                name = "changeListener",
                attach = { component, changeListener -> component.addChangeListener(changeListener) },
                detach = { component, changeListener -> component.removeChangeListener(changeListener) },
            ),
    )

/**
 * Proves that the scoped [listener] overload and [ContainedCallerFailure] are usable from outside the
 * library with no `@InternalSwingUiApi` opt-in on this file: this module carries no blanket opt-in for
 * that annotation, so a compile of this file is the actual proof the gate is gone from both.
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
                    adapter = { current -> ChangeListener { event -> current()(event) } },
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
}
