package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.defaults.DefaultForeground
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OwnedPartDeclarationReplacementTest {
    @Test
    fun replacingViewportHelpersKeepsTheViewportAndAppliesTheirDeclarations() = runComposeSwingTest {
        var detailed by mutableStateOf(true)
        var declared by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (declared) {
                    if (detailed) DetailedViewport() else SummaryViewport()
                }
            }
        }
        val pane = onNodeOfType<JScrollPane>().fetch()
        val viewport = pane.viewport
        val raw = JScrollPane()

        for (choice in listOf(true, false, true)) {
            detailed = choice
            awaitIdle()

            assertSame(viewport, pane.viewport, "the pane should keep its physical viewport")
            assertEquals(if (choice) "Detailed" else "Summary", (viewport.view as JLabel).text)
            assertEquals(if (choice) Color.RED else Color.BLUE, viewport.background)
            for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
                assertEquals(
                    if (choice) 17 else 31,
                    bar.getUnitIncrement(1),
                    "the helper declares both unit increments",
                )
                assertEquals(
                    if (choice) 130 else 70,
                    bar.getBlockIncrement(1),
                    "the helper declares both page increments",
                )
            }
        }

        val view = viewport.view
        declared = false
        awaitIdle()

        assertSame(viewport, pane.viewport, "removal should keep the pane's own viewport")
        assertNull(viewport.view, "removal should clear the composed view")
        assertNull(view.parent, "the composed child should leave the viewport")
        assertEquals(raw.viewport.background, viewport.background, "removal should restore the viewport background")
        for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            assertEquals(1, bar.getUnitIncrement(1), "removal should withdraw the declared unit increment")
        }
    }

    @Test
    fun replacingScrollbarHelpersKeepsBothBarsAndAppliesTheirModifiers() = runComposeSwingTest {
        var detailed by mutableStateOf(true)
        var declared by mutableStateOf(true)
        setContent {
            ScrollPane {
                Viewport { Label("Body") }
                if (declared) {
                    if (detailed) DetailedScrollbars() else SummaryScrollbars()
                }
            }
        }
        val pane = onNodeOfType<JScrollPane>().fetch()
        val vertical = pane.verticalScrollBar
        val horizontal = pane.horizontalScrollBar
        val earlierVertical = JScrollPane().verticalScrollBar.background
        val earlierHorizontal = JScrollPane().horizontalScrollBar.background

        for (choice in listOf(true, false, true)) {
            detailed = choice
            awaitIdle()

            assertSame(vertical, pane.verticalScrollBar, "the pane should keep its physical vertical scroll bar")
            assertSame(horizontal, pane.horizontalScrollBar, "the pane should keep its physical horizontal scroll bar")
            assertEquals(if (choice) Color.RED else Color.BLUE, vertical.background)
            assertEquals(if (choice) Color.RED else Color.BLUE, horizontal.background)
        }

        declared = false
        awaitIdle()

        assertEquals(earlierVertical, vertical.background, "removal should restore the vertical bar background")
        assertEquals(earlierHorizontal, horizontal.background, "removal should restore the horizontal bar background")
        assertSame(pane, vertical.parent, "the pane should still own its vertical bar")
        assertSame(pane, horizontal.parent, "the pane should still own its horizontal bar")
    }

    @Test
    fun withdrawingIncrementsDuringAHelperReplacementReconfiguresFreshBars() = runComposeSwingTest {
        var detailed by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (detailed) {
                    DetailedViewport()
                    DetailedScrollbars()
                } else {
                    SummaryViewport(unitIncrement = null, blockIncrement = null)
                    SummaryScrollbars()
                }
            }
        }
        val pane = onNodeOfType<JScrollPane>().fetch()
        val earlierVertical = pane.verticalScrollBar
        val earlierHorizontal = pane.horizontalScrollBar
        val raw = JScrollPane()

        detailed = false
        awaitIdle()

        assertNotSame(
            earlierVertical,
            pane.verticalScrollBar,
            "withdrawing increments should install a fresh vertical bar",
        )
        assertNotSame(
            earlierHorizontal,
            pane.horizontalScrollBar,
            "withdrawing increments should install a fresh horizontal bar",
        )
        assertEquals(
            raw.verticalScrollBar.background,
            earlierVertical.background,
            "the earlier vertical bar should be restored",
        )
        assertEquals(
            raw.horizontalScrollBar.background,
            earlierHorizontal.background,
            "the earlier horizontal bar should be restored",
        )
        for (bar in listOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            assertEquals(Color.BLUE, bar.background, "the replacement helper should configure the fresh bar")
            assertEquals(1, bar.getUnitIncrement(1), "the fresh bar should ask the content for its unit increment")
        }
        assertEquals(pane.viewport.extentSize.height, pane.verticalScrollBar.getBlockIncrement(1))
        assertEquals(pane.viewport.extentSize.width, pane.horizontalScrollBar.getBlockIncrement(1))

        val vertical = pane.verticalScrollBar
        val horizontal = pane.horizontalScrollBar
        detailed = true
        awaitIdle()

        assertSame(
            horizontal,
            pane.horizontalScrollBar,
            "declaring increments again should keep the fresh horizontal bar",
        )
        for (bar in listOf(vertical, horizontal)) {
            assertEquals(Color.RED, bar.background, "the returning helper should configure the current bar")
            assertEquals(17, bar.getUnitIncrement(1), "the returning helper should set the unit increment")
            assertEquals(130, bar.getBlockIncrement(1), "the returning helper should set the page increment")
        }
    }

    @Test
    fun aBarFirstDeclaredAsIncrementsAreWithdrawnConfiguresTheFreshBar() {
        for (part in listOf("vertical", "horizontal")) {
            for (barFirst in listOf(false, true)) {
                runComposeSwingTest {
                    var unitIncrement by mutableStateOf<Int?>(5)
                    var declared by mutableStateOf(false)
                    setContent {
                        ScrollPane {
                            if (barFirst && declared) SummaryPart(part)
                            Viewport(unitIncrement = unitIncrement) { Label("Body") }
                            if (!barFirst && declared) SummaryPart(part)
                        }
                    }
                    val pane = onNodeOfType<JScrollPane>().fetch()
                    val earlier = pane.bar(part)

                    unitIncrement = null
                    declared = true
                    awaitIdle()

                    val case = "$part, bar declared ${if (barFirst) "before" else "after"} the viewport"
                    assertNotSame(earlier, pane.bar(part), "$case: withdrawing should install a fresh bar")
                    assertEquals(
                        Color.BLUE,
                        pane.bar(part).background,
                        "$case: the declaration configures the fresh bar",
                    )
                    assertEquals(1, pane.bar(part).getUnitIncrement(1), "$case: the fresh bar asks the content")
                    assertEquals(
                        JScrollPane().bar(part).background,
                        earlier.background,
                        "$case: the replaced bar keeps nothing of the declaration",
                    )
                }
            }
        }
    }

    @Test
    fun aBarFirstDeclaredAtASecondWithdrawalConfiguresTheBarItInstalls() {
        for (part in listOf("vertical", "horizontal")) {
            runComposeSwingTest {
                var unitIncrement by mutableStateOf<Int?>(5)
                var declared by mutableStateOf(false)
                setContent {
                    ScrollPane {
                        Viewport(unitIncrement = unitIncrement) { Label("Body") }
                        if (declared) SummaryPart(part)
                    }
                }
                val pane = onNodeOfType<JScrollPane>().fetch()
                unitIncrement = null
                awaitIdle()
                val installed = pane.bar(part)
                unitIncrement = 5
                awaitIdle()

                unitIncrement = null
                declared = true
                awaitIdle()

                assertNotSame(installed, pane.bar(part), "$part: withdrawing again should install another bar")
                assertEquals(Color.BLUE, pane.bar(part).background, "$part: the declaration configures the newest bar")
                assertEquals(
                    JScrollPane().bar(part).background,
                    installed.background,
                    "$part: the bar replaced again keeps nothing of the declaration",
                )
            }
        }
    }

    @Test
    fun movedDeclarationsReadTheirNewCompositionLocalsAndDefaults() = runComposeSwingTest {
        var detailed by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (detailed) {
                    CompositionLocalProvider(
                        LocalPartBackground provides Color.RED,
                        LocalPartText provides "Detailed",
                    ) {
                        ProvideComponentDefaults(DefaultForeground provides Color.GREEN) { InheritedParts() }
                    }
                } else {
                    CompositionLocalProvider(
                        LocalPartBackground provides Color.BLUE,
                        LocalPartText provides "Summary",
                    ) {
                        ProvideComponentDefaults(DefaultForeground provides Color.YELLOW) { InheritedParts() }
                    }
                }
            }
        }
        val pane = onNodeOfType<JScrollPane>().fetch()
        val viewport = pane.viewport
        val vertical = pane.verticalScrollBar
        val horizontal = pane.horizontalScrollBar

        for (choice in listOf(true, false, true)) {
            detailed = choice
            awaitIdle()

            assertSame(viewport, pane.viewport, "a provider change should keep the viewport")
            assertSame(vertical, pane.verticalScrollBar, "a provider change should keep the vertical bar")
            assertSame(horizontal, pane.horizontalScrollBar, "a provider change should keep the horizontal bar")
            val view = viewport.view as JLabel
            assertEquals(if (choice) "Detailed" else "Summary", view.text, "content should read its current local")
            for (component in listOf(viewport, vertical, horizontal)) {
                assertEquals(
                    if (choice) Color.RED else Color.BLUE,
                    component.background,
                    "the modifier should read its current local",
                )
                assertEquals(
                    if (choice) Color.GREEN else Color.YELLOW,
                    component.foreground,
                    "the node should inherit current defaults",
                )
            }
            assertEquals(
                if (choice) Color.GREEN else Color.YELLOW,
                view.foreground,
                "content should inherit current defaults",
            )
        }
    }

    @Test
    fun twoPanesKeepIndependentPartDeclarations() = runComposeSwingTest {
        var firstDetailed by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (firstDetailed) DetailedViewport() else SummaryViewport()
            }
            ScrollPane { DetailedViewport() }
        }
        val panes = onAllNodesOfType<JScrollPane>().fetchAll()
        assertEquals(2, panes.size)
        val first = panes[0]
        val second = panes[1]
        val firstViewport = first.viewport
        val secondViewport = second.viewport
        assertNotSame(firstViewport, secondViewport, "each pane should own its viewport")

        firstDetailed = false
        awaitIdle()

        assertSame(firstViewport, first.viewport, "the switching pane should keep its viewport")
        assertSame(secondViewport, second.viewport, "the other pane should keep its viewport")
        assertEquals("Summary", (first.viewport.view as JLabel).text, "only the first pane should switch")
        assertEquals("Detailed", (second.viewport.view as JLabel).text, "the other pane should keep its content")
        assertEquals(31, first.verticalScrollBar.getUnitIncrement(1))
        assertEquals(17, second.verticalScrollBar.getUnitIncrement(1))
        assertEquals(Color.BLUE, first.viewport.background)
        assertEquals(Color.RED, second.viewport.background)
    }

    @Test
    fun twoDeclarationsOfAnyOwnedPartAreRefused() {
        for ((part, call) in listOf(
            "viewport" to "Viewport { }",
            "vertical" to "VerticalScrollbar()",
            "horizontal" to "HorizontalScrollbar()",
        )) {
            runComposeSwingTest {
                val message =
                    failureOf {
                        setContent {
                            ScrollPane {
                                DeclarePart(part)
                                DeclarePart(part)
                            }
                        }
                        awaitIdle()
                    }
                assertTrue("$call is declared twice at once in one JScrollPane" in message, "$part: $message")
            }
        }
    }

    @Test
    fun anAdditionalDeclarationAfterASuccessfulSwitchIsRefused() {
        for ((part, call) in listOf(
            "viewport" to "Viewport { }",
            "vertical" to "VerticalScrollbar()",
            "horizontal" to "HorizontalScrollbar()",
        )) {
            runComposeSwingTest {
                var detailed by mutableStateOf(true)
                var duplicate by mutableStateOf(false)
                setContent {
                    ScrollPane {
                        if (detailed) DetailedPart(part) else SummaryPart(part)
                        if (duplicate) DeclarePart(part)
                    }
                }
                detailed = false
                awaitIdle()
                val message =
                    failureOf {
                        duplicate = true
                        awaitIdle()
                    }
                assertTrue("$call is declared twice at once in one JScrollPane" in message, "$part: $message")
            }
        }
    }
}

private fun JScrollPane.bar(part: String): JScrollBar = if (part ==
    "vertical"
) {
    verticalScrollBar
} else {
    horizontalScrollBar
}

private val LocalPartBackground = compositionLocalOf { Color.BLACK }
private val LocalPartText = compositionLocalOf { "default" }

@Composable
private fun ScrollPaneScope.DetailedViewport() {
    Viewport(
        modifier = SwingModifier.background(Color.RED),
        unitIncrement = 17,
        blockIncrement = 130,
    ) { Label("Detailed") }
}

@Composable
private fun ScrollPaneScope.SummaryViewport(
    unitIncrement: Int? = 31,
    blockIncrement: Int? = 70,
) {
    Viewport(
        modifier = SwingModifier.background(Color.BLUE),
        unitIncrement = unitIncrement,
        blockIncrement = blockIncrement,
    ) {
        Label("Summary")
    }
}

@Composable
private fun ScrollPaneScope.DetailedScrollbars() {
    VerticalScrollbar(modifier = SwingModifier.background(Color.RED))
    HorizontalScrollbar(modifier = SwingModifier.background(Color.RED))
}

@Composable
private fun ScrollPaneScope.SummaryScrollbars() {
    VerticalScrollbar(modifier = SwingModifier.background(Color.BLUE))
    HorizontalScrollbar(modifier = SwingModifier.background(Color.BLUE))
}

@Composable
private fun ScrollPaneScope.InheritedParts() {
    Viewport(
        modifier = SwingModifier.background(LocalPartBackground.current),
    ) { Label(LocalPartText.current) }
    VerticalScrollbar(modifier = SwingModifier.background(LocalPartBackground.current))
    HorizontalScrollbar(modifier = SwingModifier.background(LocalPartBackground.current))
}

@Composable
private fun ScrollPaneScope.DeclarePart(part: String) {
    when (part) {
        "viewport" -> Viewport {}
        "vertical" -> VerticalScrollbar()
        "horizontal" -> HorizontalScrollbar()
    }
}

@Composable
private fun ScrollPaneScope.DetailedPart(part: String) {
    when (part) {
        "viewport" -> DetailedViewport()
        "vertical" -> VerticalScrollbar(modifier = SwingModifier.background(Color.RED))
        "horizontal" -> HorizontalScrollbar(modifier = SwingModifier.background(Color.RED))
    }
}

@Composable
private fun ScrollPaneScope.SummaryPart(part: String) {
    when (part) {
        "viewport" -> SummaryViewport()
        "vertical" -> VerticalScrollbar(modifier = SwingModifier.background(Color.BLUE))
        "horizontal" -> HorizontalScrollbar(modifier = SwingModifier.background(Color.BLUE))
    }
}
