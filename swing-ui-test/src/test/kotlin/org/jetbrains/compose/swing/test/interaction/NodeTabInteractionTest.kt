package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins what a harness tab click does to a tabbed pane, and what it refuses to do.
 *
 * A click has to reach the pane's own UI, because that is what turns a position on the strip into a
 * selection and publishes it. Where a user's click would achieve nothing the click achieves nothing too;
 * where there is no tab to aim at, the action says so rather than landing on nothing.
 */
class NodeTabInteractionTest {
    @Test
    fun clickingATabSelectsItAndReportsIt() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        var selected by mutableIntStateOf(0)
        setContent {
            // The pane holds the tab the composition declares, so the caller adopts the click for the
            // selection it makes to be the one that stands.
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = {
                    reported += it
                    selected = it
                },
                modifier = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(2)

        assertEquals(2, onNodeOfType<JTabbedPane>().fetch<JTabbedPane>().selectedIndex, "the clicked tab is selected")
        assertEquals(listOf(2), reported, "the selection the click made should reach the callback")
    }

    @Test
    fun clickingATabShowsItsContent() = runComposeSwingTest {
        var selected by mutableIntStateOf(0)
        setContent {
            // The pane holds the tab the composition declares, so the caller adopts the click for the
            // selection it makes to be the one that stands.
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = { selected = it },
                modifier = roomForTheStrip,
            ) {
                Label("first", SwingModifier.tab("One"))
                Label("second", SwingModifier.tab("Two"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        // The pane shows one tab's content at a time, so the content it now shows is the clicked
        // tab's and every other tab's content is hidden with it.
        onNodeWithText("second").assertIsVisible()
        onNodeWithText("first").assertIsNotVisible()
    }

    @Test
    fun clickingTheSelectedTabChangesNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = 1, onSelectedIndexChange = { reported += it }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        assertEquals(1, onNodeOfType<JTabbedPane>().fetch<JTabbedPane>().selectedIndex, "the strip stays put")
        assertEquals(emptyList(), reported, "clicking the tab already selected reports nothing")
    }

    @Test
    fun clickingADisabledTabChangesNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { reported += it }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two", enabled = false))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        assertEquals(0, onNodeOfType<JTabbedPane>().fetch<JTabbedPane>().selectedIndex, "the strip stays put")
        assertEquals(emptyList(), reported, "a disabled tab cannot be selected by clicking it")
    }

    @Test
    fun clickingATabOfADisabledPaneChangesNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = { reported += it },
                modifier = roomForTheStrip.enabled(false),
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        assertEquals(0, onNodeOfType<JTabbedPane>().fetch<JTabbedPane>().selectedIndex, "the strip stays put")
        assertEquals(emptyList(), reported, "a disabled pane does not answer a click")
    }

    @Test
    fun aClickDrivesTheDeclaredSelectionThroughTheCaller() = runComposeSwingTest {
        var selected by mutableIntStateOf(0)
        setContent {
            Column {
                // Rendered from the caller's state alone, so its text can only be the text of a frame
                // the click settled: the strip's own index reads the same with or without one.
                Label(text = "showing tab $selected")
                TabbedPane(
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    modifier = roomForTheStrip,
                ) {
                    Label("first", SwingModifier.tab("One"))
                    Label("second", SwingModifier.tab("Two"))
                }
            }
        }

        // The two-way pattern the callbacks exist for: the click reports the tab, the caller declares it,
        // and the composition settles on the tab the user chose.
        onNodeOfType<JTabbedPane>().performTabClick(1)

        assertEquals(1, selected, "the click should drive the caller's state")
        assertEquals(1, onNodeOfType<JTabbedPane>().fetch<JTabbedPane>().selectedIndex, "and settle the strip there")
        onNodeWithText("showing tab 1").assertExists()
    }

    @Test
    fun theStripSettlesOnTheTabTheCallerDeclaresNotTheOneClicked() = runComposeSwingTest {
        var selected by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = {
                    reported += it
                    selected = 2
                },
                modifier = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        // A caller free to declare a tab other than the one hit is what tells the click's own change
        // apart from the settled one: the strip ends on the declaration, never on the click.
        onNodeOfType<JTabbedPane>().performTabClick(1)

        assertEquals(listOf(1), reported, "the click should report the tab it hit")
        assertEquals(
            2,
            onNodeOfType<JTabbedPane>().fetch<JTabbedPane>().selectedIndex,
            "the strip should settle on the tab the caller declared",
        )
    }

    @Test
    fun aClickIsAWholePrimaryButtonGesture() = runComposeSwingTest {
        val heard = mutableListOf<String>()

        fun MouseEvent.describe(name: String): String {
            val leftButton = SwingUtilities.isLeftMouseButton(this)
            val leftButtonDown = modifiersEx and InputEvent.BUTTON1_DOWN_MASK != 0
            return "$name button1=$leftButton down=$leftButtonDown clicks=$clickCount"
        }

        val recorder =
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    heard += e.describe("pressed")
                }

                override fun mouseReleased(e: MouseEvent) {
                    heard += e.describe("released")
                }

                override fun mouseClicked(e: MouseEvent) {
                    heard += e.describe("clicked")
                }
            }
        setContent {
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = { },
                modifier = roomForTheStrip.mouseListener(recorder),
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        // A whole gesture of the primary button, because a look and feel may act on any part of it - the
        // strip's own selection happens on the press under one and on the release under another - and a
        // listener the caller declared on the pane hears the same click a user would make, down to the
        // button being reported as held only while it is.
        assertEquals(
            listOf(
                "pressed button1=true down=true clicks=1",
                "released button1=true down=false clicks=1",
                "clicked button1=true down=false clicks=1",
            ),
            heard,
            "the pane should hear one whole primary-button click",
        )
    }

    @Test
    fun clickingAnIndexOutsideTheStripFailsReadably() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { }, modifier = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        val failure = assertFailsWith<AssertionError> { onNodeOfType<JTabbedPane>().performTabClick(2) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("has 2 tab(s)"), "the failure should quantify the strip: $message")
        assertTrue(message.contains("index 2"), "the failure should name the index asked for: $message")
    }

    @Test
    fun clickingANodeWithNoTabsFailsReadably() = runComposeSwingTest {
        setContent { Label("plain") }

        val failure = assertFailsWith<AssertionError> { onNodeOfType<JLabel>().performTabClick(0) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("JLabel"), "the failure should name the type found: $message")
        assertTrue(message.contains("JTabbedPane"), "the failure should name the type expected: $message")
    }

    @Test
    fun clickingATabScrolledOutOfTheStripFailsReadably() = runComposeSwingTest {
        setContent {
            // A scrolling strip shows one run of tabs at a time, so with far more tabs than fit, the last
            // one is outside the visible run and has no position on screen to aim at.
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = { },
                modifier = roomForTheStrip,
                tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT,
            ) {
                repeat(TABS_BEYOND_ONE_RUN) { index -> Label("$index", SwingModifier.tab("Tab number $index")) }
            }
        }

        val failure =
            assertFailsWith<AssertionError> {
                onNodeOfType<JTabbedPane>().performTabClick(TABS_BEYOND_ONE_RUN - 1)
            }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("occupies no position"), "the failure should say why: $message")
        assertTrue(message.contains("into the visible run"), "the failure should say what to do: $message")
    }

    private companion object {
        // The harness root lays a child out at its preferred size, which is not necessarily wide enough
        // for a tabbed pane's whole tab strip; a tab the strip does not show has no position to click.
        val roomForTheStrip: SwingModifier = SwingModifier.preferredSize(600, 400)

        // Comfortably more tabs than one run of the strip shows at that width.
        const val TABS_BEYOND_ONE_RUN: Int = 40
    }
}
