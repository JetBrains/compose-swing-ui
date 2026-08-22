package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.compose.swing.withLookAndFeelDefault
import org.jetbrains.compose.swing.withoutLookAndFeelDefault
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JToolBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ToolBar] and [ToolBarSeparator]. They assert what an observer of the live
 * [JToolBar] sees: the declared items - separators among them - become its children in order,
 * orientation, floatable and rollover map through, and items added or removed in the composition
 * appear and disappear from the tool bar.
 */
class ToolBarBehaviorTest {
    @Test
    fun declaredItemsBecomeToolBarChildrenInOrder() = runComposeSwingTest {
        setContent {
            ToolBar {
                Button(text = "New", onClick = {})
                Button(text = "Open", onClick = {})
            }
        }

        val bar = onNodeOfType<JToolBar>()
        bar.onChildren().assertCountEquals(2)
        bar.onChildAt(0).assertTextEquals("New")
        bar.onChildAt(1).assertTextEquals("Open")
    }

    @Test
    fun anUndeclaredToolBarIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { ToolBar() }
        onNodeOfType<JToolBar>().assertTreeMatches(JToolBar())
    }

    @Test
    fun anUndeclaredSeparatorIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { ToolBarSeparator() }
        onNodeOfType<JToolBar.Separator>().assertTreeMatches(JToolBar.Separator())
    }

    @Test
    fun orientationMapsThrough() = runComposeSwingTest {
        var orientation by mutableStateOf(SwingConstants.HORIZONTAL)
        setContent {
            ToolBar(orientation = orientation) {
                Label(text = "Item")
            }
        }

        val bar = onNodeOfType<JToolBar>().fetch()
        assertEquals(SwingConstants.HORIZONTAL, bar.orientation, "the tool bar should start horizontal")

        orientation = SwingConstants.VERTICAL
        awaitIdle()
        assertEquals(SwingConstants.VERTICAL, bar.orientation, "the orientation should map through to vertical")

        orientation = SwingConstants.HORIZONTAL
        awaitIdle()
        assertEquals(SwingConstants.HORIZONTAL, bar.orientation, "the tool bar should turn back to horizontal")
    }

    @Test
    fun floatableMapsThrough() = runComposeSwingTest {
        var floatable by mutableStateOf(true)
        setContent {
            ToolBar(floatable = floatable) {
                Label(text = "Item")
            }
        }

        val bar = onNodeOfType<JToolBar>().fetch()
        assertTrue(bar.isFloatable, "the tool bar should start floatable")

        floatable = false
        awaitIdle()
        assertFalse(bar.isFloatable, "the floatable flag should map through to false")

        floatable = true
        awaitIdle()
        assertTrue(bar.isFloatable, "the tool bar should be floatable again")
    }

    @Test
    fun itemsAddedAndRemovedInCompositionAppearAndDisappear() = runComposeSwingTest {
        var showSecond by mutableStateOf(false)
        setContent {
            ToolBar {
                Button(text = "First", onClick = {})
                if (showSecond) {
                    Button(text = "Second", onClick = {})
                }
            }
        }

        val bar = onNodeOfType<JToolBar>()
        bar.onChildren().assertCountEquals(1)
        bar.onChildAt(0).assertTextEquals("First")

        showSecond = true
        awaitIdle()
        bar.onChildren().assertCountEquals(2)
        bar.onChildAt(0).assertTextEquals("First")
        bar.onChildAt(1).assertTextEquals("Second")

        showSecond = false
        awaitIdle()
        bar.onChildren().assertCountEquals(1)
        bar.onChildAt(0).assertTextEquals("First")
        onNodeWithText("Second").assertDoesNotExist()
    }

    @Test
    fun aSeparatorTakesItsDeclaredPlaceAmongTheItems() = runComposeSwingTest {
        setContent {
            ToolBar {
                Button(text = "New", onClick = {})
                ToolBarSeparator()
                Button(text = "Delete", onClick = {})
            }
        }

        val bar = onNodeOfType<JToolBar>()
        bar.onChildren().assertCountEquals(3)
        bar.onChildAt(0).assertTextEquals("New")
        bar.onChildAt(1).assert(SwingMatcher.isOfType<JToolBar.Separator>())
        bar.onChildAt(2).assertTextEquals("Delete")
    }

    @Test
    fun aDeclaredSeparatorSizeMapsThrough() = runComposeSwingTest {
        var size by mutableStateOf<Dimension?>(null)
        setContent {
            ToolBar {
                ToolBarSeparator(size = size)
            }
        }

        val separator = onNodeOfType<JToolBar.Separator>().fetch()
        // A separator that has never been given a size draws at the size its look and feel asks for,
        // so the oracle is a separator a hand-written tool bar was given the plain Swing way.
        assertEquals(
            swingSeparatorSize(),
            separator.separatorSize,
            "a separator should start at the size its look and feel asks for",
        )

        size = Dimension(12, 12)
        awaitIdle()
        assertEquals(Dimension(12, 12), separator.separatorSize, "the declared size should map through")

        size = Dimension(24, 30)
        awaitIdle()
        assertEquals(Dimension(24, 30), separator.separatorSize, "a new declared size should map through")

        size = Dimension(12, 12)
        awaitIdle()
        assertEquals(Dimension(12, 12), separator.separatorSize, "the size declared again should map through")
    }

    @Test
    fun withdrawingASeparatorSizeGivesTheSizeBackToTheLookAndFeel() = runComposeSwingTest {
        // Driven under a known look and feel, whose separator size is chosen oblong and unlike the
        // declared one: bringing that answer back is what a withdrawal has to do, and an oblong answer
        // also tells apart the two ways round it can be read, which a square one would hide.
        underMetal {
            withLookAndFeelDefault(SEPARATOR_SIZE_KEY, Dimension(7, 19)) {
                var size by mutableStateOf<Dimension?>(Dimension(24, 30))
                setContent {
                    ToolBar {
                        ToolBarSeparator(size = size)
                    }
                }

                val separator = onNodeOfType<JToolBar.Separator>().fetch()
                assertEquals(Dimension(24, 30), separator.separatorSize, "the declared size should map through")

                size = null
                awaitIdle()

                // A separator with no declared size draws at the size its look and feel asks for, so
                // the oracle is a separator a hand-written tool bar was given the plain Swing way.
                val handWrittenSize = swingSeparatorSize()
                assertNotEquals(
                    handWrittenSize?.width,
                    handWrittenSize?.height,
                    "the chosen look-and-feel size should reach the oracle oblong, so that a size read " +
                        "the other way round would not pass for it",
                )
                assertEquals(
                    handWrittenSize,
                    separator.separatorSize,
                    "withdrawing the size should leave the separator sized as its look and feel sizes one",
                )
                assertNotEquals(
                    Dimension(24, 30),
                    separator.separatorSize,
                    "the withdrawn size should be gone rather than left in place",
                )
            }
        }
    }

    @Test
    fun aLookAndFeelThatNamesNoSeparatorSizeLeavesTheSizeInPlace() = runComposeSwingTest {
        underMetal {
            withoutLookAndFeelDefault(SEPARATOR_SIZE_KEY) {
                var size by mutableStateOf<Dimension?>(Dimension(24, 30))
                setContent {
                    ToolBar {
                        ToolBarSeparator(size = size)
                    }
                }

                val separator = onNodeOfType<JToolBar.Separator>().fetch()
                assertEquals(Dimension(24, 30), separator.separatorSize, "the declared size should map through")
                assertNull(
                    swingSeparatorSize(),
                    "the look and feel under test should size no separator of its own, so that a " +
                        "withdrawal has no answer to put back",
                )

                size = null
                awaitIdle()

                assertEquals(
                    Dimension(24, 30),
                    separator.separatorSize,
                    "a look and feel with no size to give should leave the separator at the size it holds",
                )
            }
        }
    }

    @Test
    fun anUndeclaredSeparatorSizeLeavesTheLookAndFeelsChoiceInPlace() = runComposeSwingTest {
        setContent {
            ToolBar {
                ToolBarSeparator()
            }
        }

        val separator = onNodeOfType<JToolBar.Separator>().fetch()

        // A separator declared without a size draws at the size its look and feel asks for, so the
        // oracle is a separator a hand-written tool bar was given the plain Swing way.
        assertEquals(
            swingSeparatorSize(),
            separator.separatorSize,
            "a separator declared without a size should be sized as Swing sizes one",
        )
    }

    @Test
    fun aSeparatorFollowsTheToolBarsOrientation() = runComposeSwingTest {
        var orientation by mutableStateOf(SwingConstants.HORIZONTAL)
        setContent {
            ToolBar(orientation = orientation) {
                ToolBarSeparator()
            }
        }

        val separator = onNodeOfType<JToolBar.Separator>().fetch()
        assertEquals(
            SwingConstants.VERTICAL,
            separator.orientation,
            "a horizontal tool bar should draw its separator down itself",
        )

        orientation = SwingConstants.VERTICAL
        awaitIdle()
        assertEquals(
            SwingConstants.HORIZONTAL,
            separator.orientation,
            "turning the tool bar should turn the separator with it",
        )
    }

    @Test
    fun rolloverMapsThrough() = runComposeSwingTest {
        // Starts at true because a tool bar that was never told either way already reports false, so
        // only an asserted true - and a later flip back to false - shows the declared value arriving.
        var rollover by mutableStateOf(true)
        setContent {
            ToolBar(rollover = rollover) {
                Label(text = "Item")
            }
        }

        val bar = onNodeOfType<JToolBar>().fetch()
        assertTrue(bar.isRollover, "a declared rollover should map through")

        rollover = false
        awaitIdle()
        assertFalse(bar.isRollover, "clearing the declared rollover should map through too")

        rollover = true
        awaitIdle()
        assertTrue(bar.isRollover, "asking for rollover items again should map through")
    }

    @Test
    fun withdrawingARolloverChoiceGivesTheChoiceBackToTheLookAndFeel() = runComposeSwingTest {
        // Driven under a look and feel that asks for rollover item borders, and against a declared
        // choice of not drawing them: bringing that answer back is what a withdrawal has to do, and a
        // bar left at the declared choice draws its items unlike one that was never told.
        underMetal {
            var rollover by mutableStateOf<Boolean?>(false)
            setContent {
                ToolBar(rollover = rollover) {
                    Button(text = "New", onClick = {})
                }
            }

            val composed = onNodeOfType<JButton>().fetch()
            assertFalse(composed.isRolloverEnabled, "the declared choice should reach the bar's items")

            rollover = null
            awaitIdle()

            // A tool bar that was never given a choice draws its items the way its look and feel asks,
            // so the oracle is a tool bar a hand-written form was handed the plain Swing way.
            val handWrittenButton = JButton("New")
            JToolBar().add(handWrittenButton)
            assertTrue(
                handWrittenButton.isRolloverEnabled,
                "the look and feel under test should ask for rollover item borders, so that a withdrawal " +
                    "that did nothing could not pass for one that gave the choice back",
            )
            assertEquals(
                handWrittenButton.isRolloverEnabled,
                composed.isRolloverEnabled,
                "withdrawing the choice should leave the items drawn as its look and feel draws them",
            )
        }
    }

    @Test
    fun aRolloverChoiceGivenBackNoLongerFollowsTheLookAndFeel() = runComposeSwingTest {
        underMetal {
            var rollover by mutableStateOf<Boolean?>(false)
            setContent {
                ToolBar(rollover = rollover) {
                    Button(text = "New", onClick = {})
                }
            }

            rollover = null
            awaitIdle()
            val composed = onNodeOfType<JButton>().fetch()
            assertTrue(composed.isRolloverEnabled, "withdrawing the choice should give it back to the look and feel")

            // Handing the choice back records it on the bar, and a bar's items are drawn the way its
            // look and feel asks only while the bar records no choice, so a look and feel taking over
            // afterwards - here one asking for no rollover item borders - no longer reaches the items.
            // The oracle is a bar a hand-written form was handed the plain Swing way and handed to the
            // next look and feel the same way, which does follow it.
            val handWrittenBar = JToolBar()
            val handWrittenButton = JButton("New")
            handWrittenBar.add(handWrittenButton)

            withLookAndFeelDefault(ROLLOVER_KEY, false) {
                SwingUtilities.updateComponentTreeUI(handWrittenBar)
                SwingUtilities.updateComponentTreeUI(onNodeOfType<JToolBar>().fetch())

                assertFalse(
                    handWrittenButton.isRolloverEnabled,
                    "a bar that records no choice should follow the look and feel taking over, so that " +
                        "one which kept following it could not pass for one that stopped",
                )
                assertTrue(
                    composed.isRolloverEnabled,
                    "a bar given its choice back should keep it where a look and feel asks otherwise",
                )
            }
        }
    }

    @Test
    fun anUnsetRolloverLeavesTheLookAndFeelsChoiceInPlace() = runComposeSwingTest {
        // A tool bar consults its look and feel for rollover item borders only while no choice has been
        // recorded on the bar itself, so the choice is driven here by a look and feel that asks for them
        // and the oracle is a tool bar that was never told either way.
        underMetal {
            setContent {
                ToolBar {
                    Button(text = "New", onClick = {})
                }
            }
            awaitIdle()
            val composed = onNodeOfType<JButton>().fetch()

            val handWrittenButton = JButton("New")
            JToolBar().add(handWrittenButton)

            assertTrue(
                handWrittenButton.isRolloverEnabled,
                "the look and feel default should reach a tool bar that records no choice of its own",
            )
            assertEquals(
                handWrittenButton.isRolloverEnabled,
                composed.isRolloverEnabled,
                "a tool bar declared without a rollover choice should treat its items as Swing does",
            )
            assertEquals(
                JToolBar().isRollover,
                onNodeOfType<JToolBar>().fetch().isRollover,
                "a tool bar declared without a rollover choice should record none, as Swing leaves one " +
                    "that was never told",
            )
        }
    }

    @Test
    fun anUndeclaredRolloverChoiceKeepsFollowingALookAndFeelTakingOver() = runComposeSwingTest {
        underMetal {
            setContent {
                ToolBar {
                    Button(text = "New", onClick = {})
                }
            }
            awaitIdle()
            val composed = onNodeOfType<JButton>().fetch()
            assertTrue(composed.isRolloverEnabled, "the look and feel under test should ask for rollover borders")

            // A bar's items are drawn the way its look and feel asks only while the bar records no
            // choice of its own, so handing the bar to one asking otherwise - here for no rollover item
            // borders - is how a bar that records none is told from one that does.
            withLookAndFeelDefault(ROLLOVER_KEY, false) {
                SwingUtilities.updateComponentTreeUI(onNodeOfType<JToolBar>().fetch())

                assertFalse(
                    composed.isRolloverEnabled,
                    "a bar declared without a rollover choice should draw its items the way the look and " +
                        "feel taking over asks",
                )
            }
        }
    }

    @Test
    fun aButtonAdoptedByTheToolBarCarriesTheToolBarBorder() = runComposeSwingTest {
        setContent {
            ToolBar(floatable = false) {
                Button(text = "New", onClick = {})
            }
        }
        awaitIdle()
        val composed = onNodeOfType<JButton>().fetch()

        // A tool bar restyles every button it adopts, so the oracle is a hand-written tool bar: the
        // composed button must end up with what Swing gives a button added to one directly, and must
        // no longer carry the border a button wears on its own.
        val handWrittenBar = JToolBar().apply { isFloatable = false }
        val handWrittenButton = JButton("New")
        handWrittenBar.add(handWrittenButton)

        assertEquals(
            handWrittenButton.border,
            composed.border,
            "a composed tool bar item should carry the border Swing installs on an adopted button",
        )
        assertEquals(
            handWrittenButton.isRolloverEnabled,
            composed.isRolloverEnabled,
            "a composed tool bar item should match the rollover state Swing gives an adopted button",
        )
        assertNotEquals(
            JButton("New").border,
            composed.border,
            "an adopted button should not keep the border it wears outside a tool bar",
        )
    }
}

/** The look-and-feel default that names the size a tool bar separator draws at. */
private const val SEPARATOR_SIZE_KEY: String = "ToolBar.separatorSize"

/** The look-and-feel default that asks for a tool bar's items to be drawn with rollover borders. */
private const val ROLLOVER_KEY: String = "ToolBar.isRollover"

/**
 * The size Swing gives a tool bar separator that was never given one, read off a separator a
 * hand-written tool bar was handed the plain Swing way. It stands apart from how the library arrives at
 * that size, so it can tell a wrong answer from the right one.
 */
private fun swingSeparatorSize(): Dimension? {
    val handWrittenBar = JToolBar()
    handWrittenBar.addSeparator()
    return (handWrittenBar.getComponent(0) as JToolBar.Separator).separatorSize
}
