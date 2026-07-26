package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the node type an interaction carries: which queries name one, which steps keep it, and what
 * each of them then resolves to. Every case here is as much a compile-time claim as a runtime one -
 * the declared types of the values it fetches are the assertion, and a step that stopped carrying
 * the type would not compile.
 */
class NodeTypedInteractionTest {
    @Test
    fun aTypedFinderFetchesItsOwnTypeWithoutNamingItAgain() = runComposeSwingTest {
        setContent { Label(text = "typed") }

        val label: JLabel = onNodeOfType<JLabel>().fetch()

        assertEquals("typed", label.text, "the fetched node should be the label in the tree")
    }

    @Test
    fun aTypedFinderKeepsItsTypeAcrossAssertionsAndActions() = runComposeSwingTest {
        setContent { Button(text = "press", onClick = {}) }

        // Every assertion returns the interaction it was called on, so the type survives a chain and
        // the fetch at its end still needs no type argument.
        val button: JButton = onNodeOfType<JButton>().assertIsEnabled().assertIsDisplayed().fetch()

        assertEquals("press", button.text, "the chain should still target the button")
    }

    @Test
    fun anUntypedFinderFetchesTheTypeNamedAtTheFetch() = runComposeSwingTest {
        setContent { Button(text = "save", onClick = {}) }

        val button: JButton = onNodeWithText("save").fetch<JButton>()
        val component: Component = onNodeWithText("save").fetch()

        assertSame(component, button, "both forms should resolve the same node")
    }

    @Test
    fun aFetchNamingATypeTheNodeIsNotNamesBothTypes() = runComposeSwingTest {
        setContent { Label(text = "not a button") }

        val failure = assertFailsWith<AssertionError> { onNodeWithText("not a button").fetch<JButton>() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("is a JLabel"), "the failure should name the actual type: $message")
        assertTrue(message.contains("expected a JButton"), "the failure should name the wanted type: $message")
    }

    @Test
    fun aTypedInteractionStandsInForAnUntypedOne() = runComposeSwingTest {
        setContent { Label(text = "covariant") }

        // The type parameter is covariant, so a helper that takes or returns the Component-typed form
        // accepts an interaction from any typed finder.
        val untyped: SwingNodeInteraction<Component> = onNodeOfType<JLabel>()

        untyped.assertTextEquals("covariant")
    }

    @Test
    fun navigationTargetsANodeOfAnyType() = runComposeSwingTest {
        setContent {
            BoxPanel(modifier = SwingModifier.name("box")) {
                Label(text = "child")
            }
        }

        // A step lands on whatever the tree holds there, which the query cannot know, so it targets a
        // Component and the type is named at the fetch that needs one.
        val parent: Component = onNodeOfType<JLabel>().onParent().fetch()
        val panel: JPanel = onNodeOfType<JLabel>().onParent().fetch<JPanel>()

        assertSame(parent, panel, "both forms should resolve the same parent")
        assertEquals("box", panel.name, "the step should have landed on the enclosing panel")
    }

    @Test
    fun aTypedCollectionFetchesItsOwnTypeWithoutNamingItAgain() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "first")
                Label(text = "second")
            }
        }

        val labels: List<JLabel> = onAllNodesOfType<JLabel>().fetchAll()

        assertEquals(listOf("first", "second"), labels.map { it.text }, "both labels should be fetched")
    }

    @Test
    fun aTypedCollectionHandsItsTypeToEverySingleNodeStep() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "first")
                Label(text = "second")
            }
        }

        val indexed: JLabel = onAllNodesOfType<JLabel>()[1].fetch()
        val first: JLabel = onAllNodesOfType<JLabel>().onFirst().fetch()
        val last: JLabel = onAllNodesOfType<JLabel>().onLast().fetch()
        val filtered: JLabel = onAllNodesOfType<JLabel>().filter(SwingMatcher.hasText("first")).onFirst().fetch()
        val single: JLabel = onAllNodesOfType<JLabel>().filterToOne(SwingMatcher.hasText("second")).fetch()

        assertEquals("second", indexed.text, "the indexed step should target the second label")
        assertEquals("first", first.text, "the first step should target the first label")
        assertEquals("second", last.text, "the last step should target the second label")
        assertEquals("first", filtered.text, "filtering should keep the type of what it filters")
        assertEquals("second", single.text, "filterToOne should keep the type of what it filters")
    }

    @Test
    fun anUntypedCollectionFetchesTheTypeNamedAtTheFetch() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "row")
                Label(text = "row")
            }
        }

        val named: List<JLabel> = onAllNodesWithText("row").fetchAll<JLabel>()
        val components: List<Component> = onAllNodesWithText("row").fetchAll()

        assertEquals(components, named, "both forms should resolve the same nodes")
    }
}
