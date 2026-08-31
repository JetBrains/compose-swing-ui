package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end tests for [ScrollPane]'s region installation, asserting against the rendered
 * [JScrollPane]'s actual structure: the region a child declares on its own modifier must reach its
 * dedicated slot (`viewport.view`, `rowHeader`/`columnHeader`, `getCorner`), replacing a region's child
 * must replace its content, and removing a child must clear the corresponding JScrollPane slot with no
 * leftover.
 *
 * The pane holds every child in a region of its own, so a child that names none and a second child
 * naming a region already spoken for are both refused, each with a message the caller can act on.
 */
class ScrollPaneRegionTest {
    private fun labelTextOf(component: Component?): String? = (component as? JLabel)?.text

    @Test
    fun everyRegionAppendsToTheChainWithoutRepeatingIt() {
        with(ScrollPaneScopeImpl()) {
            assertDeclaredChainCarriedOnce { viewport(unitIncrement = UNIT_INCREMENT) }
            assertDeclaredChainCarriedOnce { rowHeader() }
            assertDeclaredChainCarriedOnce { columnHeader() }
            assertDeclaredChainCarriedOnce { corner(JScrollPane.UPPER_LEADING_CORNER) }
        }
    }

    @Test
    fun contentReachesTheCentralViewport() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Label(text = "body", modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("body", labelTextOf(pane.viewport.view), "content should reach the central viewport")
    }

    @Test
    fun headersAndCornerReachTheirDedicatedSlots() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Label(text = "body", modifier = SwingModifier.viewport())
                Label(text = "rows", modifier = SwingModifier.rowHeader())
                Label(text = "cols", modifier = SwingModifier.columnHeader())
                Label(text = "corner", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("body", labelTextOf(pane.viewport.view), "content should reach the central viewport")
        assertEquals("rows", labelTextOf(pane.rowHeader?.view), "rowHeader should reach the row header slot")
        assertEquals("cols", labelTextOf(pane.columnHeader?.view), "columnHeader should reach the column header slot")
        assertEquals(
            "corner",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER)),
            "corner should reach the upper-trailing corner slot",
        )
    }

    @Test
    fun redeclaringContentReplacesTheView() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            ScrollPane {
                Label(text = label, modifier = SwingModifier.viewport())
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("first", labelTextOf(pane.viewport.view), "the viewport should start with the first content")

        label = "second"
        awaitIdle()

        // The single viewport view now reflects the new content; the viewport itself is reused.
        assertEquals("second", labelTextOf(pane.viewport.view), "redeclaring content should replace the viewport view")
    }

    @Test
    fun removingARegionClearsTheJScrollPaneSlot() = runComposeSwingTest {
        var showHeaders by mutableStateOf(true)
        setContent {
            ScrollPane {
                Label(text = "body", modifier = SwingModifier.viewport())
                if (showHeaders) {
                    Label(text = "rows", modifier = SwingModifier.rowHeader())
                    Label(text = "cols", modifier = SwingModifier.columnHeader())
                    Label(text = "corner", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertNotNull(pane.rowHeader, "row header should be installed before removal")
        assertNotNull(pane.columnHeader, "column header should be installed before removal")
        assertNotNull(
            pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER),
            "corner should be installed before removal",
        )

        showHeaders = false
        awaitIdle()

        // Uninstall must release each host slot entirely (not leave an empty header viewport / corner).
        assertNull(pane.rowHeader, "row header slot leaked")
        assertNull(pane.columnHeader, "column header slot leaked")
        assertNull(pane.getCorner(JScrollPane.UPPER_TRAILING_CORNER), "corner slot leaked")
        // Content is untouched.
        assertEquals("body", labelTextOf(pane.viewport.view), "content should survive removing the headers")
    }

    @Test
    fun removingContentClearsTheViewportView() = runComposeSwingTest {
        var showContent by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (showContent) {
                    Label(text = "body", modifier = SwingModifier.viewport())
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val viewportBefore = pane.viewport
        assertEquals("body", labelTextOf(pane.viewport.view), "the viewport should start holding the content")

        showContent = false
        awaitIdle()

        // The constructor-wired viewport stays (Swing owns it) but holds nothing.
        assertSame(viewportBefore, pane.viewport, "viewport instance must be reused")
        assertNull(pane.viewport.view, "viewport view leaked after content removal")
    }

    @Test
    fun theRegionTravelsWithTheChildThatDeclaresIt() = runComposeSwingTest {
        var asHeader by mutableStateOf(false)
        setContent {
            ScrollPane {
                // A fresh node per region, since a region is read when the child arrives in the pane.
                key(asHeader) {
                    val placement = if (asHeader) SwingModifier.rowHeader() else SwingModifier.viewport()
                    Label(text = "moving", modifier = placement)
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("moving", labelTextOf(pane.viewport.view), "the child should start in the viewport")
        assertNull(pane.rowHeader, "row header installed though the child declared the viewport")

        asHeader = true
        awaitIdle()

        assertEquals("moving", labelTextOf(pane.rowHeader?.view), "the child should follow the region it declares")
        assertNull(pane.viewport.view, "the viewport should release the child that now declares the row header")
    }

    @Test
    fun contentThatStopsDeclaringHowItScrollsBecomesTheViewportViewItself() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(UNIT_INCREMENT)
        setContent {
            ScrollPane {
                Label(text = "body", modifier = SwingModifier.viewport(unitIncrement = unitIncrement))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val content = onNodeOfType<JLabel>().fetch()
        assertNotSame(content, pane.viewport.view, "a declared answer hosts the content in a body")
        assertSame(pane.viewport.view, content.parent, "the declared content is that body's own child")

        unitIncrement = null
        awaitIdle()

        assertSame(content, pane.viewport.view, "content that answers nothing is the viewport's view as it stands")

        unitIncrement = UNIT_INCREMENT
        awaitIdle()

        assertSame(pane.viewport.view, content.parent, "a declared answer hosts the content in a body again")
    }

    @Test
    fun aChildNamingNoRegionIsRefused() = runComposeSwingTest {
        // A JScrollPane reaches its children through its own setters, and ScrollPaneLayout is no
        // LayoutManager2, so a child merely added to the pane would be held with no bounds and painted
        // by nobody. It is refused instead.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    ScrollPane {
                        Label(text = "loose")
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue("names none" in message, "the refusal should say the child named no region: $message")
        assertTrue("JScrollPane" in message, "the refusal should name the host that holds the regions: $message")
        assertTrue(
            "SwingModifier.viewport()" in message,
            "the refusal should name a builder that would place the child: $message",
        )
    }

    @Test
    fun twoChildrenNamingOneRegionAreRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    ScrollPane {
                        Label(text = "body", modifier = SwingModifier.viewport())
                        Label(text = "rows", modifier = SwingModifier.rowHeader())
                        Label(text = "more rows", modifier = SwingModifier.rowHeader())
                    }
                }
                awaitIdle()
            }

        assertTrue(
            "two children declare SwingModifier.rowHeader()" in failure.message.orEmpty(),
            "the refusal should name the region two children declared: ${failure.message}",
        )
    }

    @Test
    fun eachCornerIsARegionOfItsOwn() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Label(text = "body", modifier = SwingModifier.viewport())
                Label(text = "upper", modifier = SwingModifier.corner(JScrollPane.UPPER_LEFT_CORNER))
                Label(text = "lower", modifier = SwingModifier.corner(JScrollPane.LOWER_RIGHT_CORNER))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            "upper",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_LEFT_CORNER)),
            "the upper-left corner should hold the child that declared it",
        )
        assertEquals(
            "lower",
            labelTextOf(pane.getCorner(JScrollPane.LOWER_RIGHT_CORNER)),
            "the lower-right corner should hold the child that declared it",
        )
    }

    @Test
    fun cornersSpelledEitherWayAreFourRegionsOfTheirOwn() = runComposeSwingTest {
        // A corner key naming its corner outright and the leading or trailing key resolving to that same
        // corner are one region, so the four corners stay four regions whichever way each child spells
        // the corner it fills. A pane whose orientation is unset runs left to right, so leading is left.
        setContent {
            ScrollPane {
                Label(text = "upper leading", modifier = SwingModifier.corner(JScrollPane.UPPER_LEADING_CORNER))
                Label(text = "upper trailing", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
                Label(text = "lower left", modifier = SwingModifier.corner(JScrollPane.LOWER_LEFT_CORNER))
                Label(text = "lower right", modifier = SwingModifier.corner(JScrollPane.LOWER_RIGHT_CORNER))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            "upper leading",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_LEFT_CORNER)),
            "the upper-leading child should hold the upper-left corner",
        )
        assertEquals(
            "upper trailing",
            labelTextOf(pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER)),
            "the upper-trailing child should hold the upper-right corner",
        )
        assertEquals(
            "lower left",
            labelTextOf(pane.getCorner(JScrollPane.LOWER_LEFT_CORNER)),
            "the lower-left corner should hold the child that declared it",
        )
        assertEquals(
            "lower right",
            labelTextOf(pane.getCorner(JScrollPane.LOWER_RIGHT_CORNER)),
            "the lower-right corner should hold the child that declared it",
        )
    }

    @Test
    fun twoChildrenSpellingOneCornerDifferentlyLeaveThePaneToAnswer() = runComposeSwingTest {
        // Which physical corner a trailing key names is the pane's answer, resolved against its own
        // component orientation, so these two spellings are one square here and two under a pane running
        // right to left. The region is the key each child spelled, and `setCorner` settles the collision
        // the way it settles one for any caller: the later child is the one showing.
        setContent {
            ScrollPane {
                Label(text = "outright", modifier = SwingModifier.corner(JScrollPane.UPPER_RIGHT_CORNER))
                Label(text = "resolved", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals(
            "resolved",
            (pane.getCorner(JScrollPane.UPPER_RIGHT_CORNER) as JLabel).text,
            "the later of two children reaching for one corner is the one the pane shows",
        )
    }

    @Test
    fun twoChildrenSpellingOneCornerAlikeAreRefused() = runComposeSwingTest {
        // One key is one region whatever the pane's orientation, so this collision is the library's to
        // refuse rather than the pane's to settle.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    ScrollPane {
                        Label(text = "first", modifier = SwingModifier.corner(JScrollPane.UPPER_RIGHT_CORNER))
                        Label(text = "second", modifier = SwingModifier.corner(JScrollPane.UPPER_RIGHT_CORNER))
                    }
                }
                awaitIdle()
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "two children declare SwingModifier.corner(UPPER_RIGHT_CORNER)" in message,
            "the refusal should name the corner both children reached for: $message",
        )
    }

    @Test
    fun swappingWhichComposableFillsARegionKeepsFillingIt() = runComposeSwingTest {
        var alternate by mutableStateOf(false)
        setContent {
            ScrollPane {
                // Two declarations of the same region, one at a time. The pass that swaps them may hold
                // both children in the viewport while it runs, in whichever order the incoming child
                // arrives and the outgoing one leaves; one region, one child, is what it settles at.
                if (alternate) {
                    Label(text = "second", modifier = SwingModifier.viewport())
                } else {
                    Label(text = "first", modifier = SwingModifier.viewport())
                }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        assertEquals("first", labelTextOf(pane.viewport.view), "the viewport should start with the first branch")

        alternate = true
        awaitIdle()

        assertEquals("second", labelTextOf(pane.viewport.view), "the branch now declared should fill the viewport")

        alternate = false
        awaitIdle()

        assertEquals("first", labelTextOf(pane.viewport.view), "swapping back should fill the viewport again")
    }
}

/** An arrow-button step distinct from every default, so the answer in force is unambiguous. */
private const val UNIT_INCREMENT: Int = 17
