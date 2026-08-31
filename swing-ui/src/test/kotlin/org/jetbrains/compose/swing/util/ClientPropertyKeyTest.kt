package org.jetbrains.compose.swing.util

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.testTagOrNull
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
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
}

/** The name the test tag's own key carries, which a third party could store a raw string under. */
private const val TEST_TAG_PROPERTY_NAME = "org.jetbrains.compose.swing.testTag"
