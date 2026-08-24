package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JTabbedPane
import javax.swing.event.ChangeListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Every declared aspect of a [TabbedPane] and of the tabs it hosts is composition state: it reaches
 * the live [JTabbedPane] when it is declared, follows a later value, and is taken back off when the
 * declaration returns to its default or drops to `null`.
 */
class TabbedPaneReactivityTest {
    /** A pane wide enough for its whole tab strip, so a click aimed at a tab has something to land on. */
    private val roomForTheStrip: SwingModifier = SwingModifier.preferredSize(600, 400)

    private fun icon(): Icon = ImageIcon(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))

    /** A listener that records the index the pane reports on every selection change. */
    private fun recordingListener(into: MutableList<Int>): ChangeListener =
        ChangeListener { event -> into += (event.source as JTabbedPane).selectedIndex }

    @Test
    fun theTabStripIsDrawnWhereTabPlacementSaysAndFollowsIt() = runComposeSwingTest {
        var placement by mutableIntStateOf(JTabbedPane.TOP)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}, tabPlacement = placement) {
                Label("g", SwingModifier.tab("General"))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(JTabbedPane.TOP, pane.tabPlacement, "the strip should start where it is declared")

        placement = JTabbedPane.LEFT
        awaitIdle()
        assertEquals(JTabbedPane.LEFT, pane.tabPlacement, "the strip should move to the new placement")

        placement = JTabbedPane.TOP
        awaitIdle()
        assertEquals(JTabbedPane.TOP, pane.tabPlacement, "the strip should move back to the first placement")
    }

    @Test
    fun theTabStripHandlesOverflowTheWayTabLayoutPolicySays() = runComposeSwingTest {
        var policy by mutableIntStateOf(JTabbedPane.WRAP_TAB_LAYOUT)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}, tabLayoutPolicy = policy) {
                Label("g", SwingModifier.tab("General"))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(JTabbedPane.WRAP_TAB_LAYOUT, pane.tabLayoutPolicy, "the strip should start on the declared policy")

        policy = JTabbedPane.SCROLL_TAB_LAYOUT
        awaitIdle()
        assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, pane.tabLayoutPolicy, "the strip should adopt the new policy")

        policy = JTabbedPane.WRAP_TAB_LAYOUT
        awaitIdle()
        assertEquals(JTabbedPane.WRAP_TAB_LAYOUT, pane.tabLayoutPolicy, "the strip should return to the first policy")
    }

    @Test
    fun aTabsIconFollowsItsDeclarationAndGoesAwayWithIt() = runComposeSwingTest {
        val first = icon()
        val second = icon()
        var declared by mutableStateOf<Icon?>(null)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("General", icon = declared))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertNull(pane.getIconAt(0), "a tab declared without an icon should carry none")

        declared = first
        awaitIdle()
        assertSame(first, pane.getIconAt(0), "the declared icon should reach the tab")

        declared = second
        awaitIdle()
        assertSame(second, pane.getIconAt(0), "a new icon should replace the previous one")

        declared = null
        awaitIdle()
        assertNull(pane.getIconAt(0), "dropping the icon should leave the tab without one")
    }

    @Test
    fun aTabsTooltipFollowsItsDeclarationAndGoesAwayWithIt() = runComposeSwingTest {
        var tooltip by mutableStateOf<String?>(null)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("General", tooltip = tooltip))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertNull(pane.getToolTipTextAt(0), "a tab declared without a tooltip should carry none")

        tooltip = "explains the tab"
        awaitIdle()
        assertEquals("explains the tab", pane.getToolTipTextAt(0), "the declared tooltip should reach the tab")

        tooltip = "explains it better"
        awaitIdle()
        assertEquals("explains it better", pane.getToolTipTextAt(0), "a new tooltip should replace the previous one")

        tooltip = null
        awaitIdle()
        assertNull(pane.getToolTipTextAt(0), "dropping the tooltip should leave the tab without one")
    }

    @Test
    fun aTabsTitleAndEnabledFlagFollowEveryDeclaredValue() = runComposeSwingTest {
        var title by mutableStateOf("General")
        var enabled by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab(title, enabled = enabled))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals("General", pane.getTitleAt(0), "the tab should start with its declared title")
        assertTrue(pane.isEnabledAt(0), "the tab should start enabled")

        title = "Advanced"
        enabled = false
        awaitIdle()
        assertEquals("Advanced", pane.getTitleAt(0), "the tab should adopt the new title")
        assertEquals(false, pane.isEnabledAt(0), "the tab should adopt the new enabled flag")

        title = "General"
        enabled = true
        awaitIdle()
        assertEquals("General", pane.getTitleAt(0), "the tab should return to its first title")
        assertTrue(pane.isEnabledAt(0), "the tab should be selectable again")
    }

    @Test
    fun aTabsMnemonicAndItsExplicitIndexBothReachTheTabOnTheFirstPass() = runComposeSwingTest {
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                // "Settings" underlines its first 't' (index 2) on its own; the explicit index names the
                // second one instead, so which of the two the tab shows is what the assertion tells apart.
                Label("g", SwingModifier.tab("Settings", mnemonic = KeyEvent.VK_T, displayedMnemonicIndex = 3))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(KeyEvent.VK_T, pane.getMnemonicAt(0), "the declared mnemonic should reach the tab")
        assertEquals(
            3,
            pane.getDisplayedMnemonicIndexAt(0),
            "the explicit index should override the character the mnemonic underlines on its own",
        )
    }

    @Test
    fun withdrawingTheExplicitIndexBringsBackTheMnemonicsOwnComputedIndex() = runComposeSwingTest {
        var displayedMnemonicIndex by mutableStateOf<Int?>(3)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label(
                    "g",
                    SwingModifier.tab(
                        "Settings",
                        mnemonic = KeyEvent.VK_T,
                        displayedMnemonicIndex = displayedMnemonicIndex,
                    ),
                )
            }
        }
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(3, pane.getDisplayedMnemonicIndexAt(0), "the tab should start on the explicit index")

        displayedMnemonicIndex = null
        awaitIdle()

        assertEquals(
            2,
            pane.getDisplayedMnemonicIndexAt(0),
            "withdrawing the explicit index should restore the character the mnemonic itself underlines",
        )
        assertEquals(KeyEvent.VK_T, pane.getMnemonicAt(0), "the mnemonic itself should be left untouched")
    }

    @Test
    fun changingTheMnemonicKeepsAnExplicitIndexItDidNotMove() = runComposeSwingTest {
        var mnemonic by mutableIntStateOf(-1)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("Settings", mnemonic = mnemonic, displayedMnemonicIndex = 3))
            }
        }
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(3, pane.getDisplayedMnemonicIndexAt(0), "the tab should start on the declared explicit index")

        mnemonic = KeyEvent.VK_T
        awaitIdle()

        assertEquals(KeyEvent.VK_T, pane.getMnemonicAt(0), "the tab should adopt the new mnemonic")
        assertEquals(
            3,
            pane.getDisplayedMnemonicIndexAt(0),
            "an explicit index the declaration did not move should not be wiped by the mnemonic's own recompute",
        )
    }

    @Test
    fun changingTheTitleAloneKeepsAnExplicitIndexAndWithdrawingItStillRestoresTheComputedOne() = runComposeSwingTest {
        var title by mutableStateOf("Save")
        var displayedMnemonicIndex by mutableStateOf<Int?>(1)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label(
                    "g",
                    SwingModifier.tab(
                        title,
                        mnemonic = KeyEvent.VK_S,
                        displayedMnemonicIndex = displayedMnemonicIndex,
                    ),
                )
            }
        }
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(1, pane.getDisplayedMnemonicIndexAt(0), "the tab should start on the declared explicit index")

        title = "Save As"
        awaitIdle()

        assertEquals("Save As", pane.getTitleAt(0), "the tab should adopt the new title")
        assertEquals(
            1,
            pane.getDisplayedMnemonicIndexAt(0),
            "a title-only change should not let the title's own recompute overwrite the declared index",
        )

        displayedMnemonicIndex = null
        awaitIdle()

        assertEquals(
            0,
            pane.getDisplayedMnemonicIndexAt(0),
            "withdrawing the explicit index should still restore the character the mnemonic itself underlines",
        )
    }

    @Test
    fun aTitleShrinkingBelowAStandingExplicitIndexUnderlinesNoneInsteadOfThrowing() = runComposeSwingTest {
        var title by mutableStateOf("Save As")
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab(title, mnemonic = KeyEvent.VK_S, displayedMnemonicIndex = 5))
            }
        }
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(5, pane.getDisplayedMnemonicIndexAt(0), "the tab should start on the declared explicit index")

        title = "Save"
        awaitIdle()

        assertEquals("Save", pane.getTitleAt(0), "the tab should adopt the new, shorter title")
        assertEquals(
            -1,
            pane.getDisplayedMnemonicIndexAt(0),
            "an explicit index the shrunk title no longer has should underline none rather than throw",
        )
    }

    @Test
    fun aTabsBackgroundAndForegroundReachTheTabOnTheFirstPassAndFollowAChangedDeclaration() = runComposeSwingTest {
        // Declared with real colors from the first composition, so the assertions tell a genuine insertTab-time
        // background/foreground apart from the pane's own default, which an undeclared tab reads back as too.
        var background by mutableStateOf<Color?>(Color.RED)
        var foreground by mutableStateOf<Color?>(Color.WHITE)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("General", background = background, foreground = foreground))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        val paneBackground = pane.background
        val paneForeground = pane.foreground
        assertEquals(
            Color.RED,
            pane.getBackgroundAt(0),
            "the declared background should reach the tab on the first pass",
        )
        assertEquals(
            Color.WHITE,
            pane.getForegroundAt(0),
            "the declared foreground should reach the tab on the first pass",
        )

        background = Color.BLUE
        foreground = Color.BLACK
        awaitIdle()
        assertEquals(Color.BLUE, pane.getBackgroundAt(0), "a new background should replace the previous one")
        assertEquals(Color.BLACK, pane.getForegroundAt(0), "a new foreground should replace the previous one")

        background = null
        foreground = null
        awaitIdle()
        assertEquals(paneBackground, pane.getBackgroundAt(0), "dropping the background should restore the pane's own")
        assertEquals(paneForeground, pane.getForegroundAt(0), "dropping the foreground should restore the pane's own")
    }

    @Test
    fun aSelectionChangeReachesTheLatestDeclaredCallbackExactlyOnce() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var origin by mutableStateOf("first")
        var selected by mutableIntStateOf(0)
        setContent {
            // The marker is read while composing, so a new one recomposes the pane with a callback built
            // around it; a callback from an earlier composition keeps reporting the marker it saw.
            val declaredBy = origin
            TabbedPane(
                selectedIndex = selected,
                onSelectedIndexChange = {
                    reported += "$declaredBy:$it"
                    selected = it
                },
                modifier = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        // The callback the latest composition declared is the one that fires, and it fires once -
        // a recomposition neither leaves the previous lambda attached nor adds a second registration.
        origin = "second"
        awaitIdle()
        onNodeOfType<JTabbedPane>().performTabClick(1)

        assertEquals(listOf("second:1"), reported, "the recomposed callback should report the change once")
        assertEquals(1, selected, "the reported index should be the one the caller's state took on")
    }

    @Test
    fun aSelectionChangeReachesTheLatestDeclaredChangeListener() = runComposeSwingTest {
        val firstEvents = mutableListOf<Int>()
        val secondEvents = mutableListOf<Int>()
        var useSecond by mutableStateOf(false)
        setContent {
            val first = remember { recordingListener(firstEvents) }
            val second = remember { recordingListener(secondEvents) }
            TabbedPane(
                selectedIndex = 0,
                changeListener = if (useSecond) second else first,
                modifier = roomForTheStrip,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
                Label("3", SwingModifier.tab("Three"))
            }
        }

        onNodeOfType<JTabbedPane>().performTabClick(1)
        assertEquals(listOf(1), firstEvents, "the declared listener should be notified once per change")

        useSecond = true
        awaitIdle()
        onNodeOfType<JTabbedPane>().performTabClick(2)
        assertEquals(listOf(2), secondEvents, "the newly declared listener should take over")
        assertEquals(listOf(1), firstEvents, "the replaced listener should no longer be notified")
    }

    @Test
    fun theChangeListenerOverloadFollowsItsDeclaredStripValues() = runComposeSwingTest {
        var placement by mutableIntStateOf(JTabbedPane.TOP)
        var policy by mutableIntStateOf(JTabbedPane.WRAP_TAB_LAYOUT)
        var selected by mutableIntStateOf(0)
        setContent {
            val listener = remember { ChangeListener { } }
            TabbedPane(
                selectedIndex = selected,
                changeListener = listener,
                tabPlacement = placement,
                tabLayoutPolicy = policy,
            ) {
                Label("1", SwingModifier.tab("One"))
                Label("2", SwingModifier.tab("Two"))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(JTabbedPane.TOP, pane.tabPlacement, "the strip should start where it is declared")
        assertEquals(JTabbedPane.WRAP_TAB_LAYOUT, pane.tabLayoutPolicy, "the strip should start on the declared policy")
        assertEquals(0, pane.selectedIndex, "the pane should open on the declared index")

        placement = JTabbedPane.BOTTOM
        policy = JTabbedPane.SCROLL_TAB_LAYOUT
        selected = 1
        awaitIdle()

        assertEquals(JTabbedPane.BOTTOM, pane.tabPlacement, "the strip should move to the new placement")
        assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, pane.tabLayoutPolicy, "the strip should adopt the new policy")
        assertEquals(1, pane.selectedIndex, "the pane should follow the controlled index")
    }

    @Test
    fun aTabsBodyFollowsTheContentItIsDeclaredWith() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label(caption, SwingModifier.tab("General"))
            }
        }

        val body = onNodeOfType<JLabel>().fetch()
        assertEquals("first", body.text, "the tab should start with its declared body")

        caption = "second"
        awaitIdle()
        assertEquals("second", body.text, "the body should follow its declaration")
        assertEquals(1, onNodeOfType<JTabbedPane>().fetch().tabCount, "the tab itself should stay in the strip")
    }

    @Test
    fun aTabsHeaderGoesAwayWhenItIsNoLongerDeclared() = runComposeSwingTest {
        var withHeader by mutableStateOf(true)
        setContent {
            TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                Label("g", SwingModifier.tab("General", header = if (withHeader) ({ Label("custom") }) else null))
            }
        }

        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertTrue(pane.getTabComponentAt(0) != null, "a declared header should render the tab")

        withHeader = false
        awaitIdle()
        assertNull(pane.getTabComponentAt(0), "dropping the header should restore the default tab rendering")
        assertEquals("General", pane.getTitleAt(0), "the tab should keep its title once the header is gone")

        withHeader = true
        awaitIdle()
        assertTrue(pane.getTabComponentAt(0) != null, "redeclaring the header should render the tab again")
    }
}
