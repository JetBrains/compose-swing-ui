package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A [ScrollPane]'s scrollbar policies and the regions its children declare are composition state: a
 * value changed after the first composition reaches the live [JScrollPane], and changing it back
 * reaches it again.
 */
class ScrollPaneReactivityTest {
    private fun labelTextOf(component: Component?): String? = (component as? JLabel)?.text

    @Test
    fun theVerticalScrollbarPolicyFollowsEveryDeclaredValue() = runComposeSwingTest {
        var policy by mutableIntStateOf(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED)
        setContent {
            ScrollPane(verticalScrollbar = policy) {
                Label(text = "body", modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            pane.verticalScrollBarPolicy,
            "the pane should start on the declared policy",
        )

        policy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        awaitIdle()
        assertEquals(
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            pane.verticalScrollBarPolicy,
            "the pane should adopt the always-shown policy",
        )

        policy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
        awaitIdle()
        assertEquals(
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            pane.verticalScrollBarPolicy,
            "the pane should adopt the never-shown policy",
        )

        policy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        awaitIdle()
        assertEquals(
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            pane.verticalScrollBarPolicy,
            "the pane should return to the first policy",
        )
    }

    @Test
    fun theHorizontalScrollbarPolicyFollowsEveryDeclaredValue() = runComposeSwingTest {
        var policy by mutableIntStateOf(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED)
        setContent {
            ScrollPane(horizontalScrollbar = policy) {
                Label(text = "body", modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
            pane.horizontalScrollBarPolicy,
            "the pane should start on the declared policy",
        )

        policy = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        awaitIdle()
        assertEquals(
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            pane.horizontalScrollBarPolicy,
            "the pane should adopt the always-shown policy",
        )

        policy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        awaitIdle()
        assertEquals(
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
            pane.horizontalScrollBarPolicy,
            "the pane should adopt the never-shown policy",
        )

        policy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        awaitIdle()
        assertEquals(
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
            pane.horizontalScrollBarPolicy,
            "the pane should return to the first policy",
        )
    }

    @Test
    fun aCornerMovesToTheCornerItIsDeclaredUnder() = runComposeSwingTest {
        var cornerKey by mutableStateOf(JScrollPane.UPPER_LEFT_CORNER)
        setContent {
            ScrollPane {
                Label(text = "body", modifier = SwingModifier.viewport())
                // A fresh node per corner, since a slot is read when the child arrives in the pane.
                key(cornerKey) { Label(text = "badge", modifier = SwingModifier.corner(cornerKey)) }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("badge", labelTextOf(pane.getCorner(JScrollPane.UPPER_LEFT_CORNER)), "the declared corner")

        cornerKey = JScrollPane.LOWER_RIGHT_CORNER
        awaitIdle()
        assertEquals(
            "badge",
            labelTextOf(pane.getCorner(JScrollPane.LOWER_RIGHT_CORNER)),
            "the corner should move to the newly declared key",
        )
        assertNull(pane.getCorner(JScrollPane.UPPER_LEFT_CORNER), "the corner it left should be cleared")

        cornerKey = JScrollPane.UPPER_LEFT_CORNER
        awaitIdle()
        assertEquals(
            "badge",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_LEFT_CORNER)),
            "the corner should move back to the first key",
        )
        assertNull(pane.getCorner(JScrollPane.LOWER_RIGHT_CORNER), "the corner it left should be cleared again")
    }

    @Test
    fun eachSlotFollowsTheContentItIsDeclaredWith() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent {
            ScrollPane {
                Label(text = "body $caption", modifier = SwingModifier.viewport())
                Label(text = "rows $caption", modifier = SwingModifier.rowHeader())
                Label(text = "cols $caption", modifier = SwingModifier.columnHeader())
                Label(
                    text = "corner $caption",
                    modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("body first", labelTextOf(pane.viewport.view), "the content should start as declared")
        assertEquals("rows first", labelTextOf(pane.rowHeader?.view), "the row header should start as declared")
        assertEquals("cols first", labelTextOf(pane.columnHeader?.view), "the column header should start as declared")
        assertEquals(
            "corner first",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER)),
            "the corner should start as declared",
        )

        caption = "second"
        awaitIdle()

        assertEquals("body second", labelTextOf(pane.viewport.view), "the content should follow its declaration")
        assertEquals("rows second", labelTextOf(pane.rowHeader?.view), "the row header should follow its declaration")
        assertEquals(
            "cols second",
            labelTextOf(pane.columnHeader?.view),
            "the column header should follow its declaration",
        )
        assertEquals(
            "corner second",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER)),
            "the corner should follow its declaration",
        )
    }
}
