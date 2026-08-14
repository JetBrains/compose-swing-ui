package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for the structural matchers, which express a node's place in the tree instead
 * of its own state. Each case builds a tree holding both a component the matcher must match and one
 * it must not, in the same shape, so a matcher that walks the wrong direction fails here.
 */
class StructuralMatcherTest {
    /**
     * Composes the tree these tests match against.
     *
     * ```
     * root
     *   box
     *     left  -> alpha, beta
     *     right -> gamma
     * ```
     */
    private fun ComposeSwingTest.setNestedContent() {
        setContent {
            BoxPanel(modifier = SwingModifier.name("box")) {
                FlowPanel(modifier = SwingModifier.name("left")) {
                    Label(text = "alpha")
                    Label(text = "beta")
                }
                FlowPanel(modifier = SwingModifier.name("right")) {
                    Label(text = "gamma")
                }
            }
        }
    }

    @Test
    fun hasParentMatchesOnlyTheDirectParent() = runComposeSwingTest {
        setNestedContent()

        onAllNodes(
            SwingMatcher.isOfType<JLabel>() and SwingMatcher.hasParent(SwingMatcher.hasName("left")),
        ).assertCountEquals(2)
        onNode(
            SwingMatcher.isOfType<JLabel>() and SwingMatcher.hasParent(SwingMatcher.hasName("right")),
        ).assertTextEquals("gamma")
        // The enclosing panel is an ancestor of the labels, not their parent.
        onAllNodes(
            SwingMatcher.isOfType<JLabel>() and SwingMatcher.hasParent(SwingMatcher.hasName("box")),
        ).assertCountEquals(0)
        // A node with no parent never matches, whatever the matcher describes.
        onRoot().assert(!SwingMatcher.hasParent(SwingMatcher.isOfType<JPanel>()))
    }

    @Test
    fun hasAnyChildMatchesTheDirectChildrenOnly() = runComposeSwingTest {
        setNestedContent()

        onNode(SwingMatcher.hasAnyChild(SwingMatcher.hasText("gamma"))).assert(SwingMatcher.hasName("right"))
        // "alpha" sits two levels below the box, so the box has no such child.
        onAllNodes(SwingMatcher.hasAnyChild(SwingMatcher.hasText("alpha"))).assertCountEquals(1)
        onAllNodes(SwingMatcher.hasAnyChild(SwingMatcher.hasText("absent"))).assertCountEquals(0)
    }

    @Test
    fun hasAnySiblingMatchesTheOtherChildrenOfTheParent() = runComposeSwingTest {
        setNestedContent()

        onNode(SwingMatcher.hasText("alpha") and SwingMatcher.hasAnySibling(SwingMatcher.hasText("beta")))
            .assertExists()
        onNode(SwingMatcher.hasName("left") and SwingMatcher.hasAnySibling(SwingMatcher.hasName("right")))
            .assertExists()
        // The only child of its parent has no sibling, and a node is never its own sibling.
        onAllNodes(
            SwingMatcher.hasText("gamma") and SwingMatcher.hasAnySibling(SwingMatcher.isOfType<JLabel>()),
        ).assertCountEquals(0)
        onAllNodes(
            SwingMatcher.hasText("alpha") and SwingMatcher.hasAnySibling(SwingMatcher.hasText("alpha")),
        ).assertCountEquals(0)
        onRoot().assert(!SwingMatcher.hasAnySibling(SwingMatcher.isOfType<JPanel>()))
    }

    @Test
    fun hasAnyAncestorMatchesAtEveryDepthAbove() = runComposeSwingTest {
        setNestedContent()

        // Scoping a tree-wide query to one subtree is what this matcher is for.
        assertEquals(3, onAllNodes(SwingMatcher.isOfType<JLabel>()).fetchSize(), "three labels in the whole tree")
        onAllNodes(
            SwingMatcher.isOfType<JLabel>() and SwingMatcher.hasAnyAncestor(SwingMatcher.hasName("left")),
        ).assertCountEquals(2)
        // The whole chain counts, not just the parent: every label descends from the box.
        onAllNodes(
            SwingMatcher.isOfType<JLabel>() and SwingMatcher.hasAnyAncestor(SwingMatcher.hasName("box")),
        ).assertCountEquals(3)
        onAllNodes(SwingMatcher.hasAnyAncestor(SwingMatcher.hasName("absent"))).assertCountEquals(0)
        onRoot().assert(!SwingMatcher.hasAnyAncestor(SwingMatcher.isOfType<JPanel>()))
    }

    @Test
    fun hasAnyDescendantMatchesAtEveryDepthBelow() = runComposeSwingTest {
        setNestedContent()

        // The panel holding the label matches, and so does everything above it.
        onAllNodes(SwingMatcher.hasAnyDescendant(SwingMatcher.hasText("gamma"))).assertCountEquals(2)
        onNode(
            SwingMatcher.isOfType<JPanel>() and
                SwingMatcher.hasAnyDescendant(SwingMatcher.hasText("gamma")) and
                SwingMatcher.hasParent(SwingMatcher.hasName("box")),
        ).assert(SwingMatcher.hasName("right"))
        // A leaf has no descendants at all.
        onAllNodes(
            SwingMatcher.hasText("gamma") and SwingMatcher.hasAnyDescendant(SwingMatcher.isOfType<JLabel>()),
        ).assertCountEquals(0)
    }

    @Test
    fun structuralMatchersNameTheNestedMatcherInTheirDescription() = runComposeSwingTest {
        setNestedContent()

        // A failure message is only actionable if it spells out the whole composed query.
        assertEquals(
            "hasAnyAncestor(hasName(\"left\"))",
            SwingMatcher.hasAnyAncestor(SwingMatcher.hasName("left")).description,
            "the structural matcher should carry the nested matcher's description",
        )
        assertEquals(
            "hasParent(hasAnyChild(hasText(\"alpha\")))",
            SwingMatcher.hasParent(SwingMatcher.hasAnyChild(SwingMatcher.hasText("alpha"))).description,
            "nesting should compose descriptions all the way down",
        )
    }
}
