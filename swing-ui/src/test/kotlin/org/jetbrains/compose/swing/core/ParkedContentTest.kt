package org.jetbrains.compose.swing.core

import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.firstLabelText
import org.jetbrains.compose.swing.components.selection.stampCell
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Content the composition parks - an inactive [ReusableContentHost], or a [ReusableContent] whose key
 * changes - leaves the Swing tree: its component is detached from its parent, so nothing paints it and
 * it takes no space in its parent's layout. A parked component is never driven again; reactivation
 * builds a fresh component from the node's own factory and drives it exactly as any freshly composed
 * node is driven, wholly apart from the parked one.
 *
 * Content [androidx.compose.runtime.movableContentOf] relocates, rather than parks, keeps the component
 * it was realized as - see [contentMovedToAnotherHostIsShownThere].
 *
 * A parked component is unaddressable by test tag: it is detached from the tree a tag lookup walks. So
 * every component here is fetched while the composition still drives it, and the tag is fetched again
 * once the composition drives a component there again.
 */
class ParkedContentTest {
    @Test
    fun parkingDetachesTheComponentAndReactivatingBuildsAFreshOne() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    Label(text = "body", modifier = SwingModifier.testTag(BODY))
                }
            }
        }

        val body = onNodeWithTag(BODY).fetch<JLabel>()
        assertTrue(body.isVisible, "A component the composition drives is shown.")

        active = false
        awaitIdle()

        onNodeWithTag(BODY).assertDoesNotExist()
        assertNull(body.parent, "A parked component is detached from the tree.")

        active = true
        awaitIdle()

        val reactivated = onNodeWithTag(BODY).fetch<JLabel>()
        assertNotSame(body, reactivated, "reactivation builds a fresh component rather than reusing the parked one")
        assertTrue(reactivated.isVisible, "The fresh component is shown, as any freshly composed one is.")
        assertNull(body.parent, "The parked component stays detached; it is never driven again.")
    }

    @Test
    fun aReactivatedNodeShowsWhatItsFreshFactoryBuilds() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    SwingNode(
                        factory = { JLabel("body").apply { isVisible = false } },
                        update = { applyModifier(SwingModifier.testTag(BODY)) },
                    )
                }
            }
        }

        val body = onNodeWithTag(BODY).fetch<JLabel>()
        assertTrue(!body.isVisible, "The component the factory built is hidden and no chain declares otherwise.")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val reactivated = onNodeWithTag(BODY).fetch<JLabel>()
        assertTrue(
            !reactivated.isVisible,
            "Reactivation builds a fresh component from the same factory, which hides it the same way.",
        )
    }

    @Test
    fun aKeyChangeBuildsAFreshComponentThatIsShown() = runComposeSwingTest {
        var reuseKey by mutableStateOf(0)
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContent(reuseKey) {
                    Label(text = "body $reuseKey", modifier = SwingModifier.testTag(BODY))
                }
            }
        }

        val body = onNodeWithTag(BODY).fetch<JLabel>()

        reuseKey = 1
        awaitIdle()

        val replacement = onNodeWithTag(BODY).fetch<JLabel>()
        assertNotSame(body, replacement, "a key change builds a fresh component rather than reusing the old one")
        assertTrue(replacement.isVisible, "the fresh component built for the new key is shown")
    }

    @Test
    fun aKeyChangeWhileParkedIsShownByTheFreshComponentOnceReactivated() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var reuseKey by mutableStateOf(0)
        setContent {
            BoxPanel {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    ReusableContent(reuseKey) {
                        Label(text = "body $reuseKey", modifier = SwingModifier.testTag(BODY))
                    }
                }
            }
        }

        val body = onNodeWithTag(BODY).fetch<JLabel>()

        active = false
        awaitIdle()
        onNodeWithTag(BODY).assertDoesNotExist()

        reuseKey = 1
        active = true
        awaitIdle()

        val reactivated = onNodeWithTag(BODY).fetch<JLabel>()
        assertNotSame(body, reactivated, "reactivation builds a fresh component for the key the composition holds")
        assertTrue(reactivated.isVisible, "the fresh component is shown once the composition drives it again")
    }

    @Test
    fun contentMovedToAnotherHostIsShownThere() = runComposeSwingTest {
        var inFirst by mutableStateOf(true)
        setContent {
            val content = remember { movableContentOf { Label(text = "body", modifier = SwingModifier.testTag(BODY)) } }
            BoxPanel {
                FlowPanel(modifier = SwingModifier.testTag(FIRST)) {
                    if (inFirst) content()
                }
                FlowPanel(modifier = SwingModifier.testTag(SECOND)) {
                    if (!inFirst) content()
                }
            }
        }

        val body = onNodeWithTag(BODY).fetch<JLabel>()

        inFirst = false
        awaitIdle()

        val second = onNodeWithTag(SECOND).fetch<Component>()
        assertSame(
            body,
            onNodeWithTag(BODY).fetch<JLabel>(),
            "The move keeps the component the content was realized as.",
        )
        assertSame(second, body.parent, "The moved component is held by the host it arrived at.")
        assertTrue(body.isVisible, "Content the composition drives at its new host is shown there.")
    }

    @Test
    fun aReactivatedIndexedHostShowsExactlyWhatTheCompositionDeclares() = runComposeSwingTest {
        var active by mutableStateOf(true)
        setContent {
            BoxPanel(modifier = SwingModifier.testTag(HOST)) {
                Label(text = "anchor")
                ReusableContentHost(active = active) {
                    Label(text = "parked")
                }
                if (!active) {
                    Label(text = "placeholder")
                }
            }
        }

        val host = onNodeWithTag(HOST).fetch<JComponent>()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertEquals(
            listOf("anchor", "parked"),
            host.components.filterIsInstance<JLabel>().map { it.text },
            "an indexed host shows exactly what the composition declares once parked content reactivates",
        )
    }

    @Test
    fun aReactivatedScrollPaneViewportShowsWhatTheCompositionDeclaresThere() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var body by mutableStateOf("first")
        setContent {
            ScrollPane {
                ReusableContentHost(active = active) {
                    Label(text = body, modifier = SwingModifier.viewport())
                }
                if (!active) {
                    Label(text = "placeholder", modifier = SwingModifier.viewport())
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("first", (pane.viewport.view as JLabel).text, "the driven child fills the region")

        active = false
        awaitIdle()

        body = "revived"
        active = true
        awaitIdle()

        assertEquals(
            "revived",
            (pane.viewport.view as JLabel).text,
            "the viewport holds the fresh component reactivation built, carrying the current declaration",
        )
    }

    @Test
    fun aSiblingArrivingAfterAChildWasParkedIsPlacedAmongTheChildrenReallyThere() = runComposeSwingTest {
        var parked by mutableStateOf(false)
        var extra by mutableStateOf(false)
        setContent {
            BoxPanel(modifier = SwingModifier.testTag(HOST)) {
                ReusableContentHost(active = !parked) { Label(BODY) }
                if (extra) Label(FIRST)
            }
        }

        // Parking detaches the component but leaves the holder standing where the composition put it, so
        // the sibling composed after it is at index 1 with only index 0 to take in the container.
        parked = true
        awaitIdle()

        extra = true
        awaitIdle()

        val panel = onNodeWithTag(HOST).fetch<JComponent>()
        assertEquals(1, panel.componentCount, "the parked child left, so the arriving one is alone")
        assertEquals(FIRST, (panel.getComponent(0) as JLabel).text, "and it is the one composed second")
    }

    @Test
    fun aRelocatedRegionChildFillsTheRegionAParkedSiblingGaveUp() = runComposeSwingTest {
        // A Slots host (a ScrollPane's viewport) holding a parked sibling that named the same region: the
        // parked holder's declared region survives deactivation while its installed one does not (see
        // SwingNodeHolder.onDeactivate), so a permanent mismatch must not be read as one to restore.
        var parked by mutableStateOf(false)
        var inTarget by mutableStateOf(false)
        setContent {
            val moved = remember { movableContentOf<SwingModifier> { modifier -> Label("moved", modifier) } }
            FlowPanel {
                ScrollPane(modifier = SwingModifier.testTag(TARGET)) {
                    ReusableContentHost(active = !parked) { Label("parked", SwingModifier.viewport()) }
                    if (inTarget) moved(SwingModifier.viewport())
                }
                ScrollPane {
                    if (!inTarget) moved(SwingModifier.viewport())
                }
            }
        }

        val target = onNodeWithTag(TARGET).fetch<JScrollPane>()
        assertSame(
            onNodeWithText("parked").fetch(),
            target.viewport.view,
            "the driven child fills the region",
        )

        parked = true
        inTarget = true
        awaitIdle()
        // The one-child-per-region refusal, if the parked holder were wrongly reinstalled, is raised a
        // turn after the pass that filled the region.
        awaitIdle()

        assertSame(
            onNodeWithText("moved").fetch(),
            target.viewport.view,
            "the relocated child fills the region the parked sibling gave up, undisturbed by the parked " +
                "holder's stale mismatch",
        )
    }

    @Test
    fun aParkedTopLevelChildIsNotCountedAgainstACellsOneRootSlot() = runComposeSwingTest {
        // A composable cell mounts its own composition through a single root slot, so its content is held
        // to one top-level child. A parked one gave that slot up as it deactivated and stands in the
        // root's children only until the composition drops it; counting it would refuse a cell that shows
        // exactly one child.
        var parked by mutableStateOf(false)
        val model = DefaultComboBoxModel(arrayOf("Red"))
        setContent {
            ComboBox(model = model) { item ->
                ReusableContentHost(active = !parked) { Label("parked-$item") }
                if (parked) Label(item)
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertEquals(
            "parked-Red",
            combo.stampCell(index = 0).firstLabelText(),
            "the cell shows what the island's active content composes",
        )
        awaitIdle()

        parked = true
        awaitIdle()
        val restamped = combo.stampCell(index = 0)
        // The refusal, if the parked content still counted against the slot, is raised a turn after the
        // pass that filled it.
        awaitIdle()

        assertEquals(
            "Red",
            restamped.firstLabelText(),
            "the cell shows the fresh content once the parked one has given the slot up",
        )
    }

    private companion object {
        const val BODY = "body-under-test"
        const val FIRST = "first-host"
        const val SECOND = "second-host"
        const val HOST = "host-under-test"
        const val TARGET = "target-host"
    }
}
