// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.confirmVerified
import io.mockk.mockk
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.event.ContainerListener
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * End-to-end tests for [ScrollPane]'s region installation, asserting against the rendered
 * [JScrollPane]'s actual structure: the content of `Viewport` must be the viewport's view, the region a
 * child declares on its own modifier must reach its dedicated slot (`rowHeader`/`columnHeader`,
 * `getCorner`), replacing a region's child must replace its content, and removing a child must clear the
 * corresponding JScrollPane slot with no leftover.
 *
 * The pane holds every child in a region of its own, so a child that names none and a second child
 * naming a region already spoken for are both refused, each with a message the caller can act on.
 */
class ScrollPaneRegionTest {
    private fun labelTextOf(component: Component?): String? = (component as? JLabel)?.text

    @Test
    fun everyRegionAppendsToTheChainWithoutRepeatingIt() {
        with(ScrollPaneScopeImpl()) {
            assertDeclaredChainCarriedOnce { rowHeader() }
            assertDeclaredChainCarriedOnce { columnHeader() }
            assertDeclaredChainCarriedOnce { corner(JScrollPane.UPPER_LEADING_CORNER) }
        }
    }

    @Test
    fun everyPartReportsAnEventToTheListenerDeclaredOnItOnce() = runComposeSwingTest {
        val presses = mutableMapOf<String, Int>()

        fun counting(part: String) =
            SwingModifier.mouseListener { if (it.id == MouseEvent.MOUSE_PRESSED) presses.merge(part, 1, Int::plus) }
        setContent {
            ScrollPane {
                Viewport(modifier = counting("viewport")) { Label(text = "body") }
                VerticalScrollbar(modifier = counting("vertical"))
                HorizontalScrollbar(modifier = counting("horizontal"))
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        for (part in listOf(pane.viewport, pane.verticalScrollBar, pane.horizontalScrollBar)) {
            part.dispatchEvent(MouseEvent(part, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false))
        }

        assertEquals(mapOf("viewport" to 1, "vertical" to 1, "horizontal" to 1), presses)
    }

    @Test
    fun contentReachesTheCentralViewport() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Viewport { Label(text = "body") }
            }
        }

        onNodeOfType<JScrollPane>().assertProperty(
            "body",
            "content should reach the central viewport",
        ) { labelTextOf(viewport.view) }
    }

    @Test
    fun headersAndCornerReachTheirDedicatedSlots() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Viewport { Label(text = "body") }
                Label(text = "rows", modifier = SwingModifier.rowHeader())
                Label(text = "cols", modifier = SwingModifier.columnHeader())
                Label(text = "corner", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
            }
        }

        onNodeOfType<JScrollPane>()
            .assertProperty("body", "content should reach the central viewport") { labelTextOf(viewport.view) }
            .assertProperty("rows", "rowHeader should reach the row header slot") { labelTextOf(rowHeader?.view) }
            .assertProperty("cols", "columnHeader should reach the column header slot") {
                labelTextOf(columnHeader?.view)
            }.assertProperty("corner", "corner should reach the upper-trailing corner slot") {
                labelTextOf(getCorner(JScrollPane.UPPER_TRAILING_CORNER))
            }
    }

    @Test
    fun redeclaringContentReplacesTheView() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            ScrollPane {
                Viewport { Label(text = label) }
            }
        }

        onNodeOfType<JScrollPane>().assertProperty(
            "first",
            "the viewport should start with the first content",
        ) { labelTextOf(viewport.view) }

        label = "second"
        awaitIdle()

        // The single viewport view now reflects the new content; the viewport itself is reused.
        onNodeOfType<JScrollPane>().assertProperty(
            "second",
            "redeclaring content should replace the viewport view",
        ) { labelTextOf(viewport.view) }
    }

    @Test
    fun replacingTheContentRepaintsOnlyInsideTheViewport() = runComposeSwingTest {
        var content by mutableStateOf("first")
        setContent {
            ScrollPane(style = { preferredSize(200, 100) }) {
                Viewport { key(content) { Label(text = content) } }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val first = pane.viewport.view
        // A viewport forwards its repaint to the scroll pane, clipped to its own bounds.
        withRecordedRepaints { recorded ->
            content = "second"
            awaitIdle()

            assertNotSame(first, pane.viewport.view, "the viewport shows the new content")
            val paneRepaints = recorded.dirtyRegionsOf(pane)
            assertTrue(paneRepaints.isNotEmpty(), "the viewport that took a new view repaints")
            assertTrue(
                paneRepaints.all { pane.viewport.bounds.contains(it) },
                "replacing the content repaints the viewport alone, not the pane around it: " +
                    "$paneRepaints outside ${pane.viewport.bounds}",
            )
        }
    }

    @Test
    fun removingContentClearsTheViewportView() = runComposeSwingTest {
        var showContent by mutableStateOf(true)
        setContent {
            ScrollPane {
                if (showContent) {
                    Viewport { Label(text = "body") }
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
                if (asHeader) {
                    Label(text = "moving", modifier = SwingModifier.rowHeader())
                } else {
                    Viewport { Label(text = "moving") }
                }
            }
        }

        onNodeOfType<JScrollPane>()
            .assertProperty("moving", "the child should start in the viewport") { labelTextOf(viewport.view) }
            .assertProperty(null, "row header installed though the child declared the viewport") { rowHeader }

        asHeader = true
        awaitIdle()

        onNodeOfType<JScrollPane>()
            .assertProperty("moving", "the child should follow the region it declares") { labelTextOf(rowHeader?.view) }
            .assertProperty(null, "the viewport should release the child that now declares the row header") {
                viewport.view
            }
    }

    @Test
    fun contentIsTheViewportViewWhateverIncrementsItDeclares() = runComposeSwingTest {
        var unitIncrement by mutableStateOf<Int?>(17)
        setContent {
            ScrollPane {
                Viewport(unitIncrement = unitIncrement) { Label(text = "body") }
            }
        }

        val pane = onNodeOfType<JScrollPane>().fetch()
        val content = onNodeOfType<JLabel>().fetch()
        assertSame(content, pane.viewport.view, "content declaring an increment is the viewport's view")

        unitIncrement = null
        awaitIdle()

        assertSame(content, pane.viewport.view, "content declaring none is the viewport's view")

        unitIncrement = 17
        awaitIdle()

        assertSame(content, pane.viewport.view, "content declaring one again stays the viewport's view")
    }

    @Test
    fun aChildNamingNoRegionIsRefused() = runComposeSwingTest {
        // A JScrollPane reaches its children through its own setters, and ScrollPaneLayout is no
        // LayoutManager2, so a child merely added to the pane would be held with no bounds and painted
        // by nobody. It is refused instead.
        assertFailsWith<IllegalStateException>("the refusal should say the child named no region") {
            setContent {
                ScrollPane {
                    Label(text = "loose")
                }
            }
        }
    }

    @Test
    fun twoChildrenNamingOneRegionAreRefused() = runComposeSwingTest {
        assertFailsWith<IllegalStateException>("the refusal should name the region two children declared") {
            setContent {
                ScrollPane {
                    Viewport { Label(text = "body") }
                    Label(text = "rows", modifier = SwingModifier.rowHeader())
                    Label(text = "more rows", modifier = SwingModifier.rowHeader())
                }
            }
            awaitIdle()
        }
    }

    @Test
    fun twoViewportsAreRefusedAsTwoClaimsOfOneViewport() = runComposeSwingTest {
        assertFailsWith<IllegalStateException>("two Viewport { } declarations must be refused") {
            setContent {
                ScrollPane {
                    Viewport { Label(text = "body") }
                    Viewport {}
                }
            }
            awaitIdle()
        }
    }

    @Test
    fun eachCornerIsARegionOfItsOwn() = runComposeSwingTest {
        setContent {
            ScrollPane {
                Viewport { Label(text = "body") }
                Label(text = "upper", modifier = SwingModifier.corner(JScrollPane.UPPER_LEFT_CORNER))
                Label(text = "lower", modifier = SwingModifier.corner(JScrollPane.LOWER_RIGHT_CORNER))
            }
        }

        onNodeOfType<JScrollPane>()
            .assertProperty("upper", "the upper-left corner should hold the child that declared it") {
                labelTextOf(getCorner(JScrollPane.UPPER_LEFT_CORNER))
            }.assertProperty("lower", "the lower-right corner should hold the child that declared it") {
                labelTextOf(getCorner(JScrollPane.LOWER_RIGHT_CORNER))
            }
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

        onNodeOfType<JScrollPane>()
            .assertProperty("upper leading", "the upper-leading child should hold the upper-left corner") {
                labelTextOf(getCorner(JScrollPane.UPPER_LEFT_CORNER))
            }.assertProperty("upper trailing", "the upper-trailing child should hold the upper-right corner") {
                labelTextOf(getCorner(JScrollPane.UPPER_RIGHT_CORNER))
            }.assertProperty("lower left", "the lower-left corner should hold the child that declared it") {
                labelTextOf(getCorner(JScrollPane.LOWER_LEFT_CORNER))
            }.assertProperty("lower right", "the lower-right corner should hold the child that declared it") {
                labelTextOf(getCorner(JScrollPane.LOWER_RIGHT_CORNER))
            }
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

        onNodeOfType<JScrollPane>().assertProperty(
            "resolved",
            "the later of two children reaching for one corner is the one the pane shows",
        ) { labelTextOf(getCorner(JScrollPane.UPPER_RIGHT_CORNER)) }
    }

    @Test
    fun twoChildrenSpellingOneCornerAlikeAreRefused() = runComposeSwingTest {
        // One key is one region whatever the pane's orientation, so this collision is the library's to
        // refuse rather than the pane's to settle.
        assertFailsWith<IllegalStateException>("two children spelling one corner alike must be refused") {
            setContent {
                ScrollPane {
                    Label(text = "first", modifier = SwingModifier.corner(JScrollPane.UPPER_RIGHT_CORNER))
                    Label(text = "second", modifier = SwingModifier.corner(JScrollPane.UPPER_RIGHT_CORNER))
                }
            }
            awaitIdle()
        }
    }

    @Test
    fun swappingWhichComposableFillsARegionKeepsFillingIt() = runComposeSwingTest {
        var alternate by mutableStateOf(false)
        setContent {
            ScrollPane {
                // Two views of the one viewport, one at a time. The pass that swaps them may hold both
                // children in the viewport while it runs, in whichever order the incoming child arrives
                // and the outgoing one leaves; one viewport, one view, is what it settles at.
                Viewport {
                    if (alternate) {
                        Label(text = "second")
                    } else {
                        Label(text = "first")
                    }
                }
            }
        }

        onNodeOfType<JScrollPane>().assertProperty("first", "the viewport should start with the first branch") {
            labelTextOf(viewport.view)
        }

        alternate = true
        awaitIdle()

        onNodeOfType<JScrollPane>().assertProperty("second", "the branch now declared should fill the viewport") {
            labelTextOf(viewport.view)
        }

        alternate = false
        awaitIdle()

        onNodeOfType<JScrollPane>().assertProperty("first", "swapping back should fill the viewport again") {
            labelTextOf(viewport.view)
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("regionsOnPanes")
    fun aRegionGivesBackWhatItReplacedWhenItsChildLeaves(
        region: PaneRegion,
        ownParts: Boolean,
    ) = runComposeSwingTest {
        var filled by mutableStateOf(false)
        setContent {
            ScrollPane(factory = { paneWith(ownParts) }) {
                Viewport { Label(text = "Body") }
                if (filled) Label(text = "child", modifier = region.declare(this))
            }
        }
        val pane = onNodeOfType<JScrollPane>()

        pane.assertProperty(if (ownParts) region.own else null, "before the child fills it") { region.textAt(this) }
        filled = true
        awaitIdle()
        pane.assertProperty("child", "while the child fills it") { region.textAt(this) }
        filled = false
        awaitIdle()
        pane.assertProperty(if (ownParts) region.own else null, "after the child leaves") { region.textAt(this) }
        region.viewport?.let { viewportOf ->
            pane.assertProperty(
                if (ownParts) OwnHeaderViewport::class else null,
                "in the header slot the pane held before",
            ) { viewportOf(this)?.let { it::class } }
        }
        pane.assertProperty(ownParts to "Body", "the content stays in the viewport the pane's factory built") {
            (viewport is OwnViewport) to labelTextOf(viewport.view)
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("regionsOnPanes")
    fun aReplacementArrivingBeforeTheOutgoingChildLeavesDoesNotBringItBack(
        region: PaneRegion,
        ownParts: Boolean,
    ) = runComposeSwingTest {
        var stage by mutableStateOf(0)
        setContent {
            ScrollPane(factory = { paneWith(ownParts) }) {
                Viewport { Label(text = "Body") }
                // Emitted before the first child, the second is applied while the first still stands.
                if (stage == 1) Label(text = "second", modifier = region.declare(this))
                if (stage == 0) Label(text = "first", modifier = region.declare(this))
            }
        }
        val pane = onNodeOfType<JScrollPane>()

        stage = 1
        awaitIdle()
        pane.assertProperty("second", "the replacement fills the region") { region.textAt(this) }
        stage = 2
        awaitIdle()
        pane.assertProperty(if (ownParts) region.own else null, "the pane's own part, not the first child") {
            region.textAt(this)
        }
        region.viewport?.let { viewportOf ->
            pane.assertProperty(
                if (ownParts) OwnHeaderViewport::class else null,
                "in the pane's own header slot",
            ) { viewportOf(this)?.let { it::class } }
        }
    }

    @ParameterizedTest(name = "{0} {1}, {2}")
    @MethodSource("cornerKeysUnderOrientations")
    fun aCornerGivesBackThePhysicalCornerItsKeyResolvedToUnderTheOrientationAtInstall(
        key: String,
        orientation: ComponentOrientation,
        physical: String,
        ownParts: Boolean,
    ) = runComposeSwingTest {
        var filled by mutableStateOf(false)
        setContent {
            ScrollPane(factory = { paneWith(ownParts) }, style = { componentOrientation(orientation) }) {
                Viewport { Label(text = "Body") }
                if (filled) Label(text = "child", modifier = SwingModifier.corner(key))
            }
        }
        val pane = onNodeOfType<JScrollPane>()

        filled = true
        awaitIdle()
        pane.assertProperty("child", "the child fills the corner the key resolves to") {
            labelTextOf(getCorner(physical))
        }
        filled = false
        awaitIdle()
        pane.assertProperty(
            if (ownParts) {
                listOf("own upper left", "own upper right", "own lower left", "own lower right")
            } else {
                listOf(null, null, null, null)
            },
            "each corner is the pane's own after the child leaves",
        ) {
            listOf(
                JScrollPane.UPPER_LEFT_CORNER,
                JScrollPane.UPPER_RIGHT_CORNER,
                JScrollPane.LOWER_LEFT_CORNER,
                JScrollPane.LOWER_RIGHT_CORNER,
            ).map { labelTextOf(getCorner(it)) }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allRegions")
    fun aChildOfAnInnerPaneNamingTheOuterScopesRegionIsRefused(region: PaneRegion) = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    ScrollPane {
                        val outer = this
                        Viewport {
                            ScrollPane {
                                Viewport { Label(text = "Inner body") }
                                Label(text = "child", modifier = region.declare(outer))
                            }
                        }
                    }
                }
                awaitIdle()
            }

        val message = failure.message.orEmpty()
        assertTrue(region.call in message, "the refusal should name the region call that was misplaced: $message")
        assertTrue(
            "Use the scope of the enclosing ScrollPane" in message,
            "the refusal should say which scope to use: $message",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("headerRegions")
    fun aHeaderGivenBackIsNotReparentedWithinItsPane(region: PaneRegion) = runComposeSwingTest {
        val listener = mockk<ContainerListener>(relaxed = true)
        var filled by mutableStateOf(false)
        setContent {
            ScrollPane(factory = { paneWith(ownParts = true).also { it.addContainerListener(listener) } }) {
                Viewport { Label(text = "Body") }
                if (filled) Label(text = "child", modifier = region.declare(this))
            }
        }

        filled = true
        awaitIdle()
        filled = false
        awaitIdle()

        confirmVerified(listener)
    }

    companion object {
        private val headers =
            listOf(
                PaneRegion(
                    "row header",
                    "SwingModifier.rowHeader()",
                    "own rows",
                    { SwingModifier.rowHeader() },
                    { it.rowHeader?.view },
                    { it.rowHeader },
                ),
                PaneRegion(
                    "column header",
                    "SwingModifier.columnHeader()",
                    "own columns",
                    { SwingModifier.columnHeader() },
                    { it.columnHeader?.view },
                    { it.columnHeader },
                ),
            )

        private val regions =
            headers +
                listOf(
                    JScrollPane.UPPER_LEFT_CORNER to "own upper left",
                    JScrollPane.UPPER_LEADING_CORNER to "own upper left",
                    JScrollPane.UPPER_RIGHT_CORNER to "own upper right",
                    JScrollPane.UPPER_TRAILING_CORNER to "own upper right",
                    JScrollPane.LOWER_LEFT_CORNER to "own lower left",
                    JScrollPane.LOWER_LEADING_CORNER to "own lower left",
                    JScrollPane.LOWER_RIGHT_CORNER to "own lower right",
                    JScrollPane.LOWER_TRAILING_CORNER to "own lower right",
                ).map { (corner, own) ->
                    PaneRegion(
                        corner,
                        "SwingModifier.corner($corner)",
                        own,
                        { SwingModifier.corner(corner) },
                        { it.getCorner(corner) },
                    )
                }

        private val panes = listOf(Named.of("stock pane", false), Named.of("pane with its own parts", true))

        @JvmStatic
        fun allRegions(): List<PaneRegion> = regions

        @JvmStatic
        fun headerRegions(): List<PaneRegion> = headers

        @JvmStatic
        fun regionsOnPanes(): List<Arguments> =
            regions.flatMap { region -> panes.map { Arguments.of(Named.of(region.name, region), it) } }

        @JvmStatic
        fun cornerKeysUnderOrientations(): List<Arguments> = listOf(
            Triple(JScrollPane.UPPER_LEADING_CORNER, JScrollPane.UPPER_LEFT_CORNER, JScrollPane.UPPER_RIGHT_CORNER),
            Triple(JScrollPane.UPPER_TRAILING_CORNER, JScrollPane.UPPER_RIGHT_CORNER, JScrollPane.UPPER_LEFT_CORNER),
            Triple(JScrollPane.LOWER_LEADING_CORNER, JScrollPane.LOWER_LEFT_CORNER, JScrollPane.LOWER_RIGHT_CORNER),
            Triple(JScrollPane.LOWER_TRAILING_CORNER, JScrollPane.LOWER_RIGHT_CORNER, JScrollPane.LOWER_LEFT_CORNER),
        ).flatMap { (key, leftToRight, rightToLeft) ->
            listOf(
                Triple(ComponentOrientation.LEFT_TO_RIGHT, "left-to-right", leftToRight),
                Triple(ComponentOrientation.RIGHT_TO_LEFT, "right-to-left", rightToLeft),
            ).flatMap { (orientation, name, physical) ->
                panes.map { Arguments.of(key, Named.of(name, orientation), physical, it) }
            }
        }
    }

    /** A region a child fills, the [call] that names it, the [own] text a pane builds there, and where to read it. */
    class PaneRegion(
        val name: String,
        val call: String,
        val own: String,
        val declare: ScrollPaneScope.() -> SwingModifier,
        val read: (JScrollPane) -> Component?,
        val viewport: ((JScrollPane) -> JViewport?)? = null,
    ) {
        override fun toString() = name

        fun textAt(pane: JScrollPane): String? = (read(pane) as? JLabel)?.text
    }

    private fun paneWith(ownParts: Boolean): JScrollPane = JScrollPane().apply {
        if (ownParts) {
            setViewport(OwnViewport())
            setRowHeader(OwnHeaderViewport())
            setColumnHeader(OwnHeaderViewport())
            setCorner(JScrollPane.UPPER_LEFT_CORNER, JLabel("own upper left"))
            setCorner(JScrollPane.UPPER_RIGHT_CORNER, JLabel("own upper right"))
            setCorner(JScrollPane.LOWER_LEFT_CORNER, JLabel("own lower left"))
            setCorner(JScrollPane.LOWER_RIGHT_CORNER, JLabel("own lower right"))
            setRowHeaderView(JLabel("own rows"))
            setColumnHeaderView(JLabel("own columns"))
        }
    }
}

private class OwnViewport : JViewport()

private class OwnHeaderViewport : JViewport()
