package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
                style = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(2)

        onNodeOfType<JTabbedPane>().assertProperty(2, "the clicked tab is selected") { selectedIndex }
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
                style = roomForTheStrip,
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
            TabbedPane(selectedIndex = 1, onSelectedIndexChange = { reported += it }, style = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        onNodeOfType<JTabbedPane>().assertProperty(1, "the strip stays put") { selectedIndex }
        assertEquals(emptyList(), reported, "clicking the tab already selected reports nothing")
    }

    @Test
    fun clickingADisabledTabChangesNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { reported += it }, style = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two", enabled = false))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        onNodeOfType<JTabbedPane>().assertProperty(0, "the strip stays put") { selectedIndex }
        assertEquals(emptyList(), reported, "a disabled tab cannot be selected by clicking it")
    }

    @Test
    fun clickingATabOfADisabledPaneChangesNothing() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        setContent {
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = { reported += it },
                style = {
                    preferredSize(600, 400)
                    enabled(false)
                },
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)

        onNodeOfType<JTabbedPane>().assertProperty(0, "the strip stays put") { selectedIndex }
        assertEquals(emptyList(), reported, "a disabled pane does not report a click")
    }

    @Test
    fun aClickDrivesTheDeclaredSelectionThroughTheCaller() = runComposeSwingTest {
        var selected by mutableIntStateOf(0)
        setContent {
            Panel {
                // Rendered from the caller's state alone, so its text can only be the text of a frame
                // sent after the click: the strip's own index reads the same with or without one.
                Label(text = "showing tab $selected")
                TabbedPane(
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    style = roomForTheStrip,
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
        onNodeOfType<JTabbedPane>().assertProperty(1, "and settle the strip there") { selectedIndex }
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
                style = roomForTheStrip,
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
        onNodeOfType<JTabbedPane>().assertProperty(
            2,
            "the strip should settle on the tab the caller declared",
        ) { selectedIndex }
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
                modifier = SwingModifier.mouseListener(recorder),
                style = roomForTheStrip,
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("indicesOutsideTheStrip")
    fun clickingAnIndexOutsideTheStripFailsReadably(index: Int) = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = { }, style = roomForTheStrip) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        val failure = assertFailsWith<AssertionError> { onNodeOfType<JTabbedPane>().performTabClick(index) }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("has 2 tab(s)"), "the failure should quantify the strip: $message")
        assertTrue(message.contains("index $index"), "the failure should name the index asked for: $message")
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
                style = roomForTheStrip,
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

    companion object {
        // The harness root lays a child out at its preferred size, which is not necessarily wide enough
        // for a tabbed pane's whole tab strip; a tab the strip does not show has no position to click.
        private val roomForTheStrip = SwingStyle<ComponentStyleScope> { preferredSize(600, 400) }

        // Comfortably more tabs than one run of the strip shows at that width.
        private const val TABS_BEYOND_ONE_RUN: Int = 40

        @JvmStatic
        fun indicesOutsideTheStrip(): List<Named<Int>> = listOf(
            Named.of("below the strip", -1),
            Named.of("past the strip", 2),
        )
    }
}
