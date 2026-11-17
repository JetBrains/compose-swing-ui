// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JButton
import kotlin.reflect.KProperty1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * A registration by callback is where the listener it built sits. Two registrations can build their
 * listener the same way and still register it in different places - two bound properties of one
 * component do - so the way it is built does not say where it sits, and reconciling on that alone
 * leaves a declared registration uninstalled. A registration made by callback identity also compares the
 * callbacks a payload holds, one by one, by reference: a bound function reference equal to another by its
 * own `equals` is still a different callback when it runs on a different receiver.
 */
class CallbackRegistrationTest {
    private val builtTheSameWay: (DeclaredCallback<(PropertyChangeEvent) -> Unit>) -> PropertyChangeListener =
        { callback -> PropertyChangeListener { event -> callback.report { it(event) } } }

    private val enabledRegistration =
        ListenerRegistration<JButton, PropertyChangeListener>(
            name = "propertyChangeListener",
            { component, listener -> component.addPropertyChangeListener("enabled", listener) },
            { component, listener -> component.removePropertyChangeListener("enabled", listener) },
        )

    private val onEnabled = CallbackRegistration(builtTheSameWay, enabledRegistration)

    private val onBackground =
        CallbackRegistration(
            builtTheSameWay,
            ListenerRegistration<JButton, PropertyChangeListener>(
                name = "propertyChangeListener",
                { component, listener -> component.addPropertyChangeListener("background", listener) },
                { component, listener -> component.removePropertyChangeListener("background", listener) },
            ),
        )

    @Test
    fun aCallbackDeclaredOnAnotherRegistrationIsAddedThere() = runComposeSwingTest {
        var onBackgroundNow by mutableStateOf(false)
        val seen = mutableListOf<String>()
        setContent {
            Panel {
                Button(
                    text = "declared",
                    onClick = { },
                    modifier =
                        SwingModifier.listener(
                            { event: PropertyChangeEvent -> seen += event.propertyName },
                            if (onBackgroundNow) onBackground else onEnabled,
                        ),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        assertEquals(1, button.getPropertyChangeListeners("enabled").size, "declared on the enabled registration")
        assertEquals(0, button.getPropertyChangeListeners("background").size, "and on no other")

        onBackgroundNow = true
        awaitIdle()

        assertEquals(
            1,
            button.getPropertyChangeListeners("background").size,
            "a registration declared in its place is the one the callback is added through",
        )
        assertEquals(
            0,
            button.getPropertyChangeListeners("enabled").size,
            "and it left the registration it was declared on before",
        )

        button.background = Color.RED
        button.isEnabled = false
        assertEquals(listOf("background"), seen, "so only the declared registration reaches the callback")
    }

    @Test
    fun aFreshCallbackOnTheSameRegistrationLeavesTheListenerWhereItIs() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Panel {
                // A lambda written here is a new object on every pass; the registration is not, and it
                // is the registration that says where the listener sits.
                Button(
                    text = label,
                    onClick = { },
                    modifier = SwingModifier.listener({ _: PropertyChangeEvent -> }, onEnabled),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        val registered = button.getPropertyChangeListeners("enabled").single()

        label = "second"
        awaitIdle()

        assertSame(
            registered,
            button.getPropertyChangeListeners("enabled").single(),
            "a recomposition declaring the same registration registers nothing again",
        )
    }

    @Test
    fun oneRegistrationServesEveryDeclarationOfTheSameBoundProperty() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            Panel {
                Button(text = label, onClick = { }, modifier = SwingModifier.propertyChangeListener("enabled") { })
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        val registered = button.getPropertyChangeListeners("enabled").single()

        label = "second"
        awaitIdle()

        assertSame(
            registered,
            button.getPropertyChangeListeners("enabled").single(),
            "the property name names one registration rather than a new one per pass",
        )
    }

    @Test
    fun aCallbackDeclaredOnAnotherBoundPropertyIsRegisteredThere() = runComposeSwingTest {
        var onBackgroundNow by mutableStateOf(false)
        setContent {
            Panel {
                Button(
                    text = "declared",
                    onClick = { },
                    modifier =
                        SwingModifier.propertyChangeListener(
                            if (onBackgroundNow) "background" else "enabled",
                        ) { },
                )
            }
        }
        val button = onNodeOfType<JButton>()
        button.assertProperty(1, "declared on the enabled property") { getPropertyChangeListeners("enabled").size }

        onBackgroundNow = true
        awaitIdle()

        button.assertProperty(
            1,
            "a different property name moves the registration to that property",
        ) { getPropertyChangeListeners("background").size }
        button.assertProperty(0, "leaving the one it was on") { getPropertyChangeListeners("enabled").size }
    }

    private class ListenerCallbacks(
        val onEnabled: (() -> Unit)?,
        val onOther: (() -> Unit)? = null,
    )

    /** Equal to every other `HitRecorder` by its own `equals`, as a data class is, yet recording on its own. */
    private data class HitRecorder(
        val tag: String = "recorder",
    ) {
        var hits = 0

        fun record() {
            hits++
        }
    }

    private val buildListener: (DeclaredCallback<ListenerCallbacks>) -> PropertyChangeListener =
        { callback -> PropertyChangeListener { callback.report { callbacks -> callbacks.onEnabled?.invoke() } } }

    private val byBoth =
        CallbackRegistration.byCallbackIdentity(
            buildListener,
            enabledRegistration,
            ListenerCallbacks::onEnabled,
            ListenerCallbacks::onOther,
        )

    private val byFirst =
        CallbackRegistration.byCallbackIdentity(
            buildListener,
            enabledRegistration,
            ListenerCallbacks::onEnabled,
        )

    private val bySecond =
        CallbackRegistration.byCallbackIdentity(
            buildListener,
            enabledRegistration,
            ListenerCallbacks::onOther,
        )

    @Test
    fun anEqualLookingBoundReferenceToAnotherReceiverUpdatesTheCallback() = runComposeSwingTest {
        val first = HitRecorder()
        val second = HitRecorder()
        assertEquals(first::record, second::record, "the references are equal by their own equals")
        var useSecond by mutableStateOf(false)
        setContent {
            Panel {
                Button(
                    text = "declared",
                    onClick = { },
                    modifier =
                        SwingModifier.listener(
                            JButton::class,
                            ListenerCallbacks(onEnabled = if (useSecond) second::record else first::record),
                            byBoth,
                        ),
                )
            }
        }
        val button = onNodeOfType<JButton>().fetch<JButton>()
        val installed = button.getPropertyChangeListeners("enabled").single()
        button.isEnabled = false
        assertEquals(1, first.hits)

        useSecond = true
        awaitIdle()
        button.isEnabled = true

        assertEquals(1, first.hits, "the replaced callback no longer runs")
        assertEquals(1, second.hits, "the callback declared in its place does")
        assertSame(installed, button.getPropertyChangeListeners("enabled").single(), "without registering again")
    }

    private class EqualityCase(
        val name: String,
        val first: Any,
        val second: Any,
        val equal: Boolean,
    )

    private fun declare(
        callbacks: ListenerCallbacks,
        registration: CallbackRegistration<JButton, ListenerCallbacks, PropertyChangeListener> = byBoth,
    ) = SwingModifier.listener(JButton::class, callbacks, registration)

    private fun identityRegistration(vararg properties: KProperty1<ListenerCallbacks, Function<*>?>) =
        CallbackRegistration.byCallbackIdentity<JButton, ListenerCallbacks, PropertyChangeListener>(
            buildListener,
            enabledRegistration,
            *properties,
        )

    @Test
    fun declarationsAndRegistrationsAreEqualByTheCallbacksAndPropertiesTheyHold() {
        val recorder = HitRecorder()
        val reference = recorder::record
        val cases =
            listOf(
                EqualityCase(
                    "payloads holding the same references",
                    declare(ListenerCallbacks(reference)),
                    declare(ListenerCallbacks(reference)),
                    equal = true,
                ),
                EqualityCase(
                    "a payload holding another reference to the same receiver",
                    declare(ListenerCallbacks(reference)),
                    declare(ListenerCallbacks(recorder::record)),
                    equal = false,
                ),
                EqualityCase(
                    "payloads both holding an absent callback",
                    declare(ListenerCallbacks(reference, null)),
                    declare(ListenerCallbacks(reference, null)),
                    equal = true,
                ),
                EqualityCase(
                    "an absent callback against a present one",
                    declare(ListenerCallbacks(reference, null)),
                    declare(ListenerCallbacks(reference, reference)),
                    equal = false,
                ),
                EqualityCase("registrations reading different properties", byBoth, byFirst, equal = false),
                EqualityCase("registrations reading different single properties", byFirst, bySecond, equal = false),
                EqualityCase(
                    "declarations of the same payload by registrations reading different properties",
                    declare(ListenerCallbacks(reference), byBoth),
                    declare(ListenerCallbacks(reference), byFirst),
                    equal = false,
                ),
                EqualityCase(
                    "registrations reading the same properties in the same order",
                    byBoth,
                    identityRegistration(ListenerCallbacks::onEnabled, ListenerCallbacks::onOther),
                    equal = true,
                ),
                EqualityCase(
                    "registrations reading the same properties in another order",
                    byBoth,
                    identityRegistration(ListenerCallbacks::onOther, ListenerCallbacks::onEnabled),
                    equal = false,
                ),
            )

        for (case in cases) {
            if (case.equal) {
                assertEquals(case.first, case.second, case.name)
                assertEquals(case.first.hashCode(), case.second.hashCode(), "${case.name}: the hash codes agree")
            } else {
                assertNotEquals(case.first, case.second, case.name)
            }
        }
    }

    @Test
    fun anEmptyPropertyListIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            CallbackRegistration.byCallbackIdentity<JButton, ListenerCallbacks, PropertyChangeListener>(
                buildListener,
                enabledRegistration,
            )
        }
    }
}
