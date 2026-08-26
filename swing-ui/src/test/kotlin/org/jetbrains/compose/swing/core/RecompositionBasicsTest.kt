package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Core recomposition behaviors driven through the real composition + frame-clock + apply pipeline:
 * state-driven text updates, conditional child insertion/removal, and keyed reordering without
 * dropping component identity.
 *
 * What each case costs is read off the sections this library reports: a change pass names itself, and
 * so does each kind of container churn the applier drives. Component identity survives a child being
 * taken out and put back just as well as it survives a move, so the churn is the only thing that says
 * which of the two carried a case - and an unchanged tree is the only evidence that a recomposition
 * cost nothing at all.
 */
class RecompositionBasicsTest : TracedTest() {
    @Test
    fun stateChangeUpdatesLabelText() = runComposeSwingTest {
        var name by mutableStateOf("world")
        setContent {
            Label(text = "Hello, $name")
        }

        val label = onNodeOfType<JLabel>()
        label.assertTextEquals("Hello, world")

        name = "compose"
        awaitIdle()

        label.assertTextEquals("Hello, compose")
    }

    @Test
    fun aRecompositionThatWritesNoWidgetPropertyDrivesNoChangePass() = runComposeSwingTest {
        var tick by mutableStateOf(0)
        var compositions = 0
        setContent {
            compositions++
            // Recomputed on every recomposition and equal every time, so the node's update block runs
            // and finds the widget already holding what it declares.
            val declared = if (tick >= 0) SETTLED_TEXT else "never"
            SwingNode(factory = { JLabel() }, update = { set(declared) { text = it } })
        }
        awaitIdle()

        val label = onNodeOfType<JLabel>()
        label.assertTextEquals(SETTLED_TEXT)
        val composedBefore = compositions
        tracer.clear()

        tick += 1
        awaitIdle()

        assertTrue(
            compositions > composedBefore,
            "the case needs the write to have recomposed the content; nothing recomposed",
        )
        label.assertTextEquals(SETTLED_TEXT)
        assertEquals(
            emptyList(),
            tracer.passes(),
            "a recomposition whose declarations all come out equal should drive no change pass at all, " +
                "so an animation frame that moves nothing costs nothing downstream: ${tracer.sections}",
        )
    }

    @Test
    fun conditionalChildIsAddedAndRemoved() = runComposeSwingTest {
        var visible by mutableStateOf(false)
        setContent {
            BoxPanel {
                Label(text = "always")
                if (visible) Label(text = "conditional")
            }
        }

        val always = onNodeWithText("always")
        val conditional = onNodeWithText("conditional")

        always.assertExists()
        conditional.assertDoesNotExist()
        tracer.clear()

        visible = true
        awaitIdle()
        conditional.assertExists()
        assertEquals(
            listOf(listOf("insert", "attach")),
            tracer.passes(),
            "a child appearing should cost one pass that takes exactly one node in and gives it its " +
                "place, leaving its siblings' attachments alone: ${tracer.sections}",
        )
        tracer.clear()

        visible = false
        awaitIdle()
        conditional.assertDoesNotExist()
        // The unconditional sibling is unaffected by the add/remove churn.
        always.assertExists()
        assertEquals(
            listOf(listOf("remove")),
            tracer.passes(),
            "a child disappearing should cost one pass that removes it and nothing else: ${tracer.sections}",
        )
    }

    @Test
    fun keyedListReordersWithoutLosingComponents() = runComposeSwingTest {
        val items = mutableStateListOf("a", "b", "c")
        setContent {
            BoxPanel {
                for (item in items) {
                    key(item) { Label(text = item) }
                }
            }
        }

        assertLabelsInOrder("a", "b", "c")
        // The live instances, so the reorder can be shown to move rather than rebuild them.
        val a = onNodeWithText("a").fetch<JLabel>()
        val b = onNodeWithText("b").fetch<JLabel>()
        val c = onNodeWithText("c").fetch<JLabel>()
        tracer.clear()

        // Reverse the list: same keys, new order. Keyed children keep their component instances.
        items.clear()
        items.addAll(listOf("c", "b", "a"))
        awaitIdle()

        assertLabelsInOrder("c", "b", "a")
        assertSame(a, onNodeWithText("a").fetch<JLabel>(), "\"a\" must be moved across the reorder, not recreated")
        assertSame(b, onNodeWithText("b").fetch<JLabel>(), "\"b\" must be moved across the reorder, not recreated")
        assertSame(c, onNodeWithText("c").fetch<JLabel>(), "\"c\" must be moved across the reorder, not recreated")

        // How many moves a reversal takes is the composer's own diff, not this library's contract; that
        // every one of them is a move is.
        val churn = tracer.passes().single()
        assertTrue(
            "move" in churn,
            "a reorder should reach the host through the applier's move path: ${tracer.sections}",
        )
        assertEquals(
            emptyList(),
            churn.filterNot { it == "move" },
            "a reorder must not detach and re-add its children - that keeps their instances but drops " +
                "the constraint and the depth each of them was given: ${tracer.sections}",
        )
    }

    @Test
    fun addingListItemKeepsExistingComponentIdentity() = runComposeSwingTest {
        val items = mutableStateListOf("x", "y")
        setContent {
            BoxPanel {
                for (item in items) {
                    key(item) { Label(text = item) }
                }
            }
        }
        val xBefore = onNodeWithText("x").fetch<JLabel>()
        tracer.clear()

        items.add("z")
        awaitIdle()

        assertLabelsInOrder("x", "y", "z")
        assertSame(
            xBefore,
            onNodeWithText("x").fetch<JLabel>(),
            "the existing keyed item \"x\" must survive a sibling being added",
        )
        assertEquals(
            listOf(listOf("insert", "attach")),
            tracer.passes(),
            "a keyed list should grow in place: one node taken in, one given its place, and every " +
                "standing sibling left where it stands: ${tracer.sections}",
        )
    }

    @Test
    fun removingAListItemLeavesTheKeyedSiblingsAroundItInPlace() = runComposeSwingTest {
        val items = mutableStateListOf("x", "y", "z")
        setContent {
            BoxPanel {
                for (item in items) {
                    key(item) { Label(text = item) }
                }
            }
        }
        val xBefore = onNodeWithText("x").fetch<JLabel>()
        val zBefore = onNodeWithText("z").fetch<JLabel>()
        tracer.clear()

        items.remove("y")
        awaitIdle()

        assertLabelsInOrder("x", "z")
        assertSame(xBefore, onNodeWithText("x").fetch<JLabel>(), "the sibling before the dropped item must stand")
        assertSame(zBefore, onNodeWithText("z").fetch<JLabel>(), "the sibling after the dropped item must stand")
        assertEquals(
            listOf(listOf("remove")),
            tracer.passes(),
            "dropping a keyed item out of the middle should cost one remove and nothing else - the " +
                "siblings that outlive it are neither re-added nor reordered: ${tracer.sections}",
        )
    }

    /** Asserts the composition holds exactly the labels reading [texts], in that order. */
    private fun ComposeSwingTest.assertLabelsInOrder(vararg texts: String) {
        val labels = onAllNodesOfType<JLabel>()
        labels.assertCountEquals(texts.size)
        texts.forEachIndexed { index, text ->
            labels[index].assertTextEquals(text)
        }
    }

    private companion object {
        /** What the widget already holds while the composition goes on declaring it. */
        const val SETTLED_TEXT = "constant"
    }
}
