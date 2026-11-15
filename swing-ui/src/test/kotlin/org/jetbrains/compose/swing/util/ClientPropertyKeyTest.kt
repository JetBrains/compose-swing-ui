// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.modifier.accessibility.LabelForFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.ClientPropertyFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.ClientPropertyParameters
import org.jetbrains.compose.swing.fixtures.modifier.appearance.TestTagFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.testTagOrNull
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.beans.PropertyChangeEvent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins what a [Key] is worth over the raw string its [Key.name] would otherwise store a client property
 * under: a slot only the declaration that owns it addresses, cleared by assigning `null`, read back at
 * the type it was declared with, and firing its change event under that name.
 */
class ClientPropertyKeyTest {
    @Test
    fun aRawStringWriteUnderTheTagsNameDoesNotReachTheSlotTheTagKeyOwns() = runComposeSwingTest {
        setContent {
            Label("tagged", modifier = SwingModifier.testTag("the-tag"))
            Label("spoofer", modifier = SwingModifier.clientProperty(TEST_TAG_PROPERTY_NAME, "the-tag"))
        }
        assertEquals(
            "tagged",
            onNodeWithTag("the-tag").fetch<JLabel>().text,
            "a tag query should resolve the component the library tagged",
        )
        assertNull(
            onNodeWithText("spoofer").fetch<JLabel>().testTagOrNull(),
            "a raw-string write under the tag's name should leave the tag slot empty",
        )

        val outsider = JPanel()
        outsider.putClientProperty(TEST_TAG_PROPERTY_NAME, "the-tag")
        assertNull(outsider.testTagOrNull(), "a third-party client property should not read back as a tag")
    }

    @Test
    fun theTagsOwnKeyCarriesTheNameARawStringWouldHaveToCollideWith() = runComposeSwingTest {
        val events = mutableListOf<PropertyChangeEvent>()
        setContent {
            SwingNode(
                factory = { JPanel().also { panel -> panel.addPropertyChangeListener { events += it } } },
                modifier = SwingModifier.testTag("the-tag"),
            )
        }

        val fired = events.map { it.propertyName }
        assertTrue(
            TEST_TAG_PROPERTY_NAME in fired,
            "the tag must be stored under $TEST_TAG_PROPERTY_NAME, which is the name a raw-string write " +
                "has to collide with, but the tagged component fired $fired",
        )
    }

    @Test
    fun twoKeysSharingANameAddressTwoDifferentSlots() {
        val first = Key<String>("org.jetbrains.compose.swing.test.shared")
        val second = Key<String>("org.jetbrains.compose.swing.test.shared")
        val component = JPanel()

        component[first] = "written"

        assertEquals("written", component[first], "the key written under should read its value back")
        assertNull(component[second], "a second key of the same name should address a slot of its own")
    }

    @Test
    fun assigningNullClearsTheProperty() {
        val key = Key<String>("org.jetbrains.compose.swing.test.cleared")
        val component = JPanel()
        component[key] = "written"

        component[key] = null

        assertNull(component[key], "assigning null should clear what the key reads back")
        assertNull(component.getClientProperty(key), "assigning null should clear the client property itself")
    }

    @Test
    fun aWriteFiresAPropertyChangeNamedAfterTheKey() {
        val key = Key<String>("org.jetbrains.compose.swing.test.watched")
        val component = JPanel()
        val events = mutableListOf<PropertyChangeEvent>()
        component.addPropertyChangeListener { events += it }

        component[key] = "written"

        val event = events.single()
        assertEquals(key.name, event.propertyName, "a write should fire its event under the key's name")
        assertEquals("written", event.newValue, "the event should carry the value written")
    }

    @Test
    fun aKeyReadsItsValueBackAtTheTypeItWasDeclaredWith() {
        val text = Key<String>("org.jetbrains.compose.swing.test.text")
        val count = Key<Int>("org.jetbrains.compose.swing.test.count")
        val component = JPanel()

        component[text] = "written"
        component[count] = 42

        val readText: String? = component[text]
        val readCount: Int? = component[count]
        assertEquals("written", readText, "a String key should read its value back as a String")
        assertEquals(42, readCount, "an Int key should read its value back as an Int")
    }

    @TestFactory
    fun eachAlternativeReachesBothArms(): List<DynamicTest> = listOf(
        reachesBothArms(
            ClientPropertyFixture,
            composed = { onNodeOfType<JLabel>() },
            expected = { listOf(it.value, null) },
            composedValues = { listOf(getClientProperty(it.key), getClientProperty(it.replacedKey())) },
            rawValues = { listOf(getClientProperty(it.key), getClientProperty(it.replacedKey())) },
        ),
        reachesBothArms(
            TestTagFixture,
            composed = { onNodeOfType<JLabel>() },
            expected = { listOf(it.tag) },
            composedValues = { listOf(testTagOrNull()) },
            rawValues = { listOf(getClientProperty("testTag")) },
        ),
        reachesBothArms(
            LabelForFixture,
            composed = { onNodeWithText("Caption").assertIsOfType<JLabel>() },
            expected = { listOf(if (it.alternateTarget) "Second target" else "First target") },
            composedValues = { listOf(labelFor?.accessibleContext?.accessibleName) },
            rawValues = {
                val caption = components.filterIsInstance<JLabel>().single { label -> label.text == "Caption" }
                listOf(caption.labelFor?.accessibleContext?.accessibleName)
            },
        ),
    )

    @TestFactory
    fun rawSwing() = ClientPropertyFixture.rawSwingTests()

    @TestFactory
    fun counts() = ClientPropertyFixture.countTests()
}

/** The name the test tag's own key carries, which a third party could store a raw string under. */
private const val TEST_TAG_PROPERTY_NAME = "org.jetbrains.compose.swing.testTag"

/** The key [ClientPropertyFixture] stores under when these parameters do not use it. */
private fun ClientPropertyParameters.replacedKey(): String =
    if (key == ClientPropertyFixture.initial.key) "alternate" else ClientPropertyFixture.initial.key

/**
 * Walks [fixture] from its initial parameters to each alternative and back, and checks after every step that both
 * arms carry the [expected] values, which the component tree comparison does not read.
 */
private fun <P : Any, C : Component> reachesBothArms(
    fixture: ComposableFixture<P, C>,
    composed: ComposeSwingTest.() -> SwingNodeInteraction<JLabel>,
    expected: (P) -> List<Any?>,
    composedValues: JLabel.(P) -> List<Any?>,
    rawValues: C.(P) -> List<Any?>,
): DynamicTest {
    val name = fixture::class.simpleName
    return dynamicTest(name) {
        runComposeSwingTest {
            var parameters by mutableStateOf(fixture.initial)
            setContent { fixture.Content(parameters) }
            val raw = fixture.createRawSwing()
            fixture.updateRawSwing(raw, fixture.initial)
            for (alternative in fixture.alternatives) {
                for (step in listOf(fixture.initial, alternative, fixture.initial)) {
                    parameters = step
                    awaitIdle()
                    fixture.updateRawSwing(raw, step)
                    composed().assertProperty(expected(step), "the composed arm of $name should carry $step") {
                        composedValues(step)
                    }
                    assertEquals(expected(step), raw.rawValues(step), "the raw arm of $name should carry $step")
                }
            }
        }
    }
}
