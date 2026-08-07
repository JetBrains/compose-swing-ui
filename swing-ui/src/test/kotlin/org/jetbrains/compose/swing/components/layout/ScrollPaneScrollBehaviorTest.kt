package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.Scrollable
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * How a [ScrollPane] scrolls its content - the pixels an arrow button and a page move it by, and whether
 * the content takes the viewport's width or height in place of its preferred one - is the content's to
 * declare through [ScrollPaneScope.viewport].
 *
 * A declared answer is what the viewport is told, and an undeclared one is the answer a scroll pane
 * gives a view that answers nothing itself. Content that declares none of them is the viewport's view
 * as it stands, so a widget that scrolls by its own rows or lines keeps answering for itself.
 */
class ScrollPaneScrollBehaviorTest {
    /** A visible rectangle to ask the installed [Scrollable] with, as a scrolling viewport would. */
    private val visible = Rectangle(0, 0, VISIBLE_WIDTH, VISIBLE_HEIGHT)

    @Test
    fun declaredAnswersAreTheOnesTheViewportIsGiven() = runComposeSwingTest {
        var unitIncrement by mutableStateOf(FIRST_UNIT_INCREMENT)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                Label(
                    text = "Body",
                    modifier =
                        SwingModifier.preferredSize(CONTENT_SHORT_SIDE, CONTENT_LONG_SIDE).viewport(
                            unitIncrement = unitIncrement,
                            blockIncrement = BLOCK_INCREMENT,
                            tracksViewportWidth = true,
                            tracksViewportHeight = true,
                        ),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val view = assertIs<Scrollable>(pane.viewport.view, "declared answers host the content in a body")
        assertSame(
            pane.viewport.view,
            onNodeWithText("Body").fetch().parent,
            "the declared content is that body's own child",
        )
        assertEquals(
            FIRST_UNIT_INCREMENT,
            view.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1),
            "the declared unit increment is what one arrow button scrolls by",
        )
        assertEquals(
            BLOCK_INCREMENT,
            view.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1),
            "the declared block increment is what one page scrolls by",
        )
        assertTrue(view.scrollableTracksViewportWidth, "the declared width answer is the one given")
        assertTrue(view.scrollableTracksViewportHeight, "the declared height answer is the one given")

        unitIncrement = SECOND_UNIT_INCREMENT
        awaitIdle()

        assertEquals(
            SECOND_UNIT_INCREMENT,
            view.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1),
            "a unit increment declared later is the one given from then on",
        )
    }

    @Test
    fun anUndeclaredAnswerIsTheOneTheScrollPaneGivesOfItsOwn() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                Label(
                    text = "Body",
                    modifier =
                        SwingModifier
                            .preferredSize(CONTENT_SHORT_SIDE, CONTENT_LONG_SIDE)
                            .viewport(unitIncrement = FIRST_UNIT_INCREMENT),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val view = assertIs<Scrollable>(pane.viewport.view, "one declared answer hosts the content in a body")
        assertEquals(
            FIRST_UNIT_INCREMENT,
            view.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1),
            "the one declared answer is still the one given",
        )
        assertEquals(
            visible.height,
            view.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1),
            "an undeclared block increment scrolls a full viewport page down",
        )
        assertEquals(
            visible.width,
            view.getScrollableBlockIncrement(visible, SwingConstants.HORIZONTAL, 1),
            "an undeclared block increment scrolls a full viewport page across",
        )
        assertFalse(view.scrollableTracksViewportWidth, "an undeclared width answer leaves the content its own")
        assertFalse(view.scrollableTracksViewportHeight, "an undeclared height answer leaves the content its own")
    }

    @Test
    fun contentThatDeclaresNoAnswerIsTheViewportsViewItself() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                TextArea(value = "line\n".repeat(LINE_COUNT), modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        assertSame(area, pane.viewport.view, "content that declares no answer is the viewport's view as it stands")
        assertEquals(
            JTextArea().getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1),
            area.getScrollableUnitIncrement(visible, SwingConstants.VERTICAL, 1),
            "the pane scrolls by the line the area answers with",
        )
    }

    @Test
    fun contentThatStartsOrStopsDeclaringAnAnswerIsRehostedWithoutLosingItsPlaceInTheViewport() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(null)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                TextArea(
                    value = "line\n".repeat(LINE_COUNT),
                    modifier = SwingModifier.viewport(unitIncrement = unitIncrement),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        assertSame(area, pane.viewport.view, "content declaring no answer starts as the viewport's own view")

        unitIncrement = FIRST_UNIT_INCREMENT
        awaitIdle()

        assertSame(
            area,
            onNodeOfType<JTextArea>().fetch(),
            "the content is rehosted, not rebuilt, when it starts declaring an answer",
        )
        val body = assertIs<ScrollableBody>(pane.viewport.view, "declaring an answer hosts the content in a body")
        assertSame(body, area.parent, "the content sits under the body that now answers the viewport for it")

        unitIncrement = null
        awaitIdle()

        assertSame(
            area,
            pane.viewport.view,
            "dropping the last declared answer returns the content to being the viewport's own view",
        )
        assertSame(
            area,
            onNodeOfType<JTextArea>().fetch(),
            "the content survives dropping its last declared answer too",
        )
    }

    @Test
    fun declaringNoTrackingIsTheSameAsDeclaringNothing() = runComposeSwingTest {
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                TextArea(
                    value = "line\n".repeat(LINE_COUNT),
                    modifier = SwingModifier.viewport(tracksViewportWidth = false, tracksViewportHeight = false),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val area = onNodeOfType<JTextArea>().fetch()
        assertSame(
            area,
            pane.viewport.view,
            "content laid out at its preferred size is what content answering nothing is laid out at, so " +
                "asking for it is no answer and hosts the content in no body of its own",
        )
    }

    @Test
    fun trackingTheViewportsWidthLaysTheContentOutAtThatWidth() = runComposeSwingTest {
        var tracks by mutableStateOf<Boolean?>(null)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                Label(
                    text = "Body",
                    modifier =
                        SwingModifier
                            .preferredSize(CONTENT_SHORT_SIDE, CONTENT_LONG_SIDE)
                            .viewport(unitIncrement = FIRST_UNIT_INCREMENT, tracksViewportWidth = tracks),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            CONTENT_SHORT_SIDE,
            pane.viewport.view.width,
            "content that leaves the width to itself is laid out at the width it asks for",
        )
        assertTrue(
            pane.viewport.width > CONTENT_SHORT_SIDE,
            "precondition: the viewport is wider than the content asks to be",
        )

        tracks = true
        awaitIdle()

        assertEquals(
            pane.viewport.width,
            pane.viewport.view.width,
            "content that takes the viewport's width is laid out at that width",
        )
    }

    @Test
    fun trackingTheViewportsHeightLaysTheContentOutAtThatHeight() = runComposeSwingTest {
        var tracks by mutableStateOf<Boolean?>(null)
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(PANE_WIDTH, PANE_HEIGHT)) {
                Label(
                    text = "Body",
                    modifier =
                        SwingModifier
                            .preferredSize(CONTENT_LONG_SIDE, CONTENT_SHORT_SIDE)
                            .viewport(unitIncrement = FIRST_UNIT_INCREMENT, tracksViewportHeight = tracks),
                )
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            CONTENT_SHORT_SIDE,
            pane.viewport.view.height,
            "content that leaves the height to itself is laid out at the height it asks for",
        )
        assertTrue(
            pane.viewport.height > CONTENT_SHORT_SIDE,
            "precondition: the viewport is taller than the content asks to be",
        )

        tracks = true
        awaitIdle()

        assertEquals(
            pane.viewport.height,
            pane.viewport.view.height,
            "content that takes the viewport's height is laid out at that height",
        )
    }
}

private const val PANE_WIDTH: Int = 200
private const val PANE_HEIGHT: Int = 100

/** The visible rectangle the tests ask with, sized apart from the pane so a page answer is its own. */
private const val VISIBLE_WIDTH: Int = 160
private const val VISIBLE_HEIGHT: Int = 90

/** The side of the content that fits inside the viewport, so tracking it is a visible change. */
private const val CONTENT_SHORT_SIDE: Int = 50

/** The side of the content that overflows the viewport, so the pane has something to scroll. */
private const val CONTENT_LONG_SIDE: Int = 400

private const val FIRST_UNIT_INCREMENT: Int = 17
private const val SECOND_UNIT_INCREMENT: Int = 33
private const val BLOCK_INCREMENT: Int = 130
private const val LINE_COUNT: Int = 40
