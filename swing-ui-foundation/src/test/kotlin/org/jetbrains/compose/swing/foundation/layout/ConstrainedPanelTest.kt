// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.foundation.graphics.SharedInsetsBorder
import org.jetbrains.compose.swing.foundation.graphics.spill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onDescendants
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import java.util.stream.Stream
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.Scrollable
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConstrainedPanelTest {
    @Test
    fun aPanelIsAnAccessiblePanelCarryingTheNameSetOnIt() =
        runComposeSwingTest {
            setContent {
                Box(
                    modifier = SwingModifier.testTag("container-under-test").styleable { accessibleName("Toolbar") },
                ) {}
            }

            onNodeWithTag("container-under-test")
                .assert(SwingMatcher.hasAccessibleRole(AccessibleRole.PANEL))
                .assert(SwingMatcher.hasAccessibleName("Toolbar"))
        }

    @Test
    fun aPanelListsItsAccessibleChildrenInDeclarationOrder() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag("container-under-test")) {
                    Label("first")
                    Label("second")
                    Label("third")
                }
            }

            onNodeWithTag("container-under-test").assertProperty(
                listOf("first", "second", "third"),
                "A panel lists its children to assistive technologies in the order they are declared.",
            ) { accessibleChildNames(accessibleContext) }
        }

    @Test
    fun aGlassPaneDoesNotAppearAmongAccessibleChildren() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("container-under-test")) {
                    Box(
                        modifier =
                            SwingModifier
                                .placementLayer { scaleX = 2f }
                                .styleable {
                                    accessibleName("child")
                                    preferredSize(20, 20)
                                },
                    )
                }
            }

            onAllNodesOfType<ConstrainedPanel>()
                .filterToOne(SwingMatcher.hasTestTag("container-under-test"))
                .assertProperty(true, "A panel holding a scaled child has a glass pane.") { glassPane != null }
                .assertProperty(listOf("child")) { accessibleChildNames(accessibleContext) }
        }

    @Test
    fun aGlassPaneCoversThePanelWhenAttachedAndResized() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("container-under-test").styleable { preferredSize(40, 50) }) {
                    Box(modifier = SwingModifier.placementLayer { scaleX = 2f }.styleable { preferredSize(20, 20) })
                }
            }

            val panel =
                onAllNodesOfType<ConstrainedPanel>().filterToOne(SwingMatcher.hasTestTag("container-under-test"))
            panel
                .assertProperty(Dimension(40, 50)) { size }
                .assertProperty(Rectangle(0, 0, 40, 50)) { glassPane?.bounds }

            val resized = panel.fetch()
            val glass = resized.glassPane
            resized.setBounds(3, 4, 60, 70)
            panel
                .assertProperty(glass) { glassPane }
                .assertProperty(Rectangle(0, 0, 60, 70)) { glassPane?.bounds }
        }

    @ParameterizedTest
    @MethodSource("scaledChildSteps")
    fun theTreeStaysValidWhileAScaledChildOrItsWidthChanges(case: ScaledChildCase) =
        runComposeSwingTest {
            var width by mutableIntStateOf(case.width)
            var scale by mutableFloatStateOf(case.scale)
            setContent {
                if (case.stockParent) {
                    Panel(PanelLayout.Border(), style = { preferredSize(width, 600) }) {
                        Box(modifier = SwingModifier.testTag("host").north()) {
                            Box(modifier = SwingModifier.fillMaxWidth().placementLayer { scaleX = scale }) {
                                Label("Scaled")
                            }
                        }
                    }
                } else {
                    Box(modifier = SwingModifier.testTag("host")) {
                        Box {
                            Box(modifier = SwingModifier.placementLayer { scaleX = scale }) {
                                Box(modifier = SwingModifier.styleable { preferredSize(width, 20) })
                            }
                        }
                    }
                }
            }
            val host = onNodeWithTag("host").fetch<JComponent>()

            for (next in case.scales) {
                scale = next
                awaitIdle()
                assertTreeValid("the components left invalid at a scale of $next")
                assertEquals(0, cyclesUntilStable(host), "a paint asks for no validation at a scale of $next")
            }
            for (next in case.widths) {
                width = next
                awaitIdle()
                assertTreeValid("the components left invalid at a width of $next")
                assertEquals(0, cyclesUntilStable(host), "a paint asks for no validation at a width of $next")
            }
        }

    // A stock parent sizes the panel by a width, and a Foundation parent fits it to new paint outsets.
    class ScaledChildCase(
        val name: String,
        val stockParent: Boolean,
        val scale: Float,
        val width: Int,
        val scales: List<Float>,
        val widths: List<Int>,
    )

    @Test
    fun aPanelListsAChildRaisedByZIndexAfterTheSiblingsItPaintsOver() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("container-under-test")) {
                    Label("raised", SwingModifier.zIndex(1f))
                    Label("second")
                    Label("third")
                }
            }

            onNodeWithTag("container-under-test")
                .assertProperty(
                    listOf("second", "third", "raised"),
                    "A panel lists its children in paint order, the child painted last listed last.",
                ) { accessibleChildNames(accessibleContext) }
                .assertProperty(listOf(0, 1, 2), "Each child reports the index its panel lists it at.") {
                    List(accessibleContext.accessibleChildrenCount) {
                        accessibleContext.getAccessibleChild(it).accessibleContext.accessibleIndexInParent
                    }
                }
        }

    @Test
    fun aPanelReportsAStateChangeToAssistiveTechnologies() =
        runComposeSwingTest {
            var enabled by mutableStateOf(true)
            setContent {
                Box(modifier = SwingModifier.testTag("container-under-test").styleable { enabled(enabled) }) {}
            }
            val lost = mutableListOf<Any?>()
            onNodeWithTag("container-under-test").fetch().accessibleContext.addPropertyChangeListener {
                if (it.propertyName == AccessibleContext.ACCESSIBLE_STATE_PROPERTY && it.newValue == null) {
                    lost += it.oldValue
                }
            }

            enabled = false
            awaitIdle()

            assertEquals(
                listOf<Any?>(AccessibleState.ENABLED),
                lost,
                "A panel disabled after being enabled reports losing the ENABLED state.",
            )
        }

    @Test
    fun aPanelHoldingNoScrollableScrollsByLinesAndPagesOfItsViewport() =
        runComposeSwingTest {
            setContent {
                ScrollPane(style = { preferredSize(60, 50) }) {
                    Viewport {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("container-under-test")
                                    .styleable { preferredSize(100, 80) },
                        ) {}
                    }
                }
            }

            val panel =
                onAllNodesOfType<ConstrainedPanel>().filterToOne(SwingMatcher.hasTestTag("container-under-test"))
            panel
                .assertProperty(false, "Overlapping children must repaint each other.") { isOptimizedDrawingEnabled }
                .assertProperty(
                    Dimension(100, 80),
                    "The viewport reports the size of the box it scrolls.",
                ) { preferredScrollableViewportSize }
            val live = panel.fetch()
            assertEquals(
                live.getFontMetrics(live.font).height,
                live.getScrollableUnitIncrement(Rectangle(0, 0, 10, 10), SwingConstants.VERTICAL, 1),
                "an arrow button moves the pane by a line of the panel's font",
            )
            panel.assertProperty(25, "a page down is the visible height") {
                getScrollableBlockIncrement(Rectangle(0, 0, 50, 25), SwingConstants.VERTICAL, 1)
            }
            panel.assertProperty(50, "a page across is the visible width") {
                getScrollableBlockIncrement(Rectangle(0, 0, 50, 25), SwingConstants.HORIZONTAL, 1)
            }
            panel.assertProperty(
                Dimension(100, 80),
                "a viewport smaller than the panel leaves it at its preferred size, so the pane scrolls it",
            ) { size }
        }

    @Test
    fun aPanelHoldingOneScrollableServesAPaneOnItsBehalf() =
        runComposeSwingTest {
            setContent {
                ScrollPane(style = { preferredSize(100, 80) }) {
                    Viewport {
                        Box {
                            TextArea(value = "word ".repeat(200), onValueChange = {}, lineWrap = true)
                        }
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>()
            val live = pane.fetch()
            val area = onNodeOfType<JTextArea>().fetch()
            pane
                .assertProperty(
                    area.getScrollableUnitIncrement(live.viewport.viewRect, SwingConstants.VERTICAL, 1),
                    "an arrow button moves the pane by a line of the text area the panel holds",
                ) { verticalScrollBar.getUnitIncrement(1) }
                .assertProperty(
                    false,
                    "content taking the viewport's width needs no scroll bar",
                ) { horizontalScrollBar.isVisible }
            val viewportWidth = pane.fetch().viewport.width
            pane.assertProperty(viewportWidth, "the panel is laid out at the viewport's width") { viewport.view.width }
        }

    @Test
    fun aPanelHoldingOneScrollablePagesAndTakesTheViewportHeightAsItsContentAsks() =
        runComposeSwingTest {
            val content =
                object : JComponent(), Scrollable {
                    override fun getPreferredSize() = Dimension(50, 320)

                    override fun getPreferredScrollableViewportSize() = Dimension(50, 40)

                    override fun getScrollableUnitIncrement(
                        visibleRect: Rectangle,
                        orientation: Int,
                        direction: Int,
                    ) = 3

                    override fun getScrollableBlockIncrement(
                        visibleRect: Rectangle,
                        orientation: Int,
                        direction: Int,
                    ) = 7

                    override fun getScrollableTracksViewportWidth() = false

                    override fun getScrollableTracksViewportHeight() = true
                }
            setContent {
                ScrollPane(style = { preferredSize(100, 80) }) {
                    Viewport {
                        Box {
                            SwingNode(factory = { content })
                        }
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>()
            pane.assertProperty(Dimension(50, 40)) {
                assertIs<Scrollable>(viewport.view).preferredScrollableViewportSize
            }
            pane
                .assertProperty(7, "a page is the content's own page") { verticalScrollBar.getBlockIncrement(1) }
                .assertProperty(
                    false,
                    "content taking the viewport's height needs no scroll bar",
                ) { verticalScrollBar.isVisible }
            val viewportHeight = pane.fetch().viewport.height
            pane.assertProperty(viewportHeight, "the panel is laid out at the viewport's height") {
                viewport.view.height
            }
        }

    @Test
    fun aPanelHoldingSeveralChildrenServesAPaneAsAPanelOfItsOwn() =
        runComposeSwingTest {
            setContent {
                ScrollPane(style = { preferredSize(400, 80) }) {
                    Viewport {
                        Column {
                            Box(modifier = SwingModifier.styleable { preferredSize(100, 80) }) {}
                            Box(modifier = SwingModifier.styleable { preferredSize(200, 240) }) {}
                        }
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>()
            pane.assertProperty(
                Dimension(200, 320),
                "the pane sizes its viewport by the whole panel, not by one of its children",
            ) { viewport.preferredSize }
            val viewportWidth = pane.fetch().viewport.width
            pane.assertProperty(viewportWidth, "the panel is laid out at the viewport's width") { viewport.view.width }
        }

    @Test
    fun aBorderSharingItsInsetsKeepsThemWhenTheBoxFillsInsetsItIsHanded() =
        runComposeSwingTest {
            val border = SharedInsetsBorder()
            setContent {
                Box {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("box")
                                .spill(Insets(8, 8, 8, 8))
                                .styleable {
                                    preferredSize(Dimension(64, 48))
                                    border(border)
                                },
                    )
                }
            }
            val box = onAllNodesOfType<JComponent>().filterToOne(SwingMatcher.hasTestTag("box"))

            box.assertProperty(Insets(10, 10, 10, 10)) { getInsets(Insets(0, 0, 0, 0)) }

            assertEquals(Insets(2, 2, 2, 2), border.own, "the border's own insets are read, never written")
        }

    companion object {
        @JvmStatic
        fun scaledChildSteps(): Stream<Arguments> =
            Stream
                .of(
                    ScaledChildCase(
                        "a child starts scaling under a stock parent, then the parent narrows",
                        stockParent = true,
                        scale = 1f,
                        width = 320,
                        scales = listOf(2f, 3f, 1f, 2f, 0.5f),
                        widths = listOf(260, 200),
                    ),
                    ScaledChildCase(
                        "a scaled panel's content widens under a Foundation parent that fits it to new paint outsets",
                        stockParent = false,
                        scale = 2f,
                        width = 20,
                        scales = emptyList(),
                        widths = listOf(50, 25, 20, 60),
                    ),
                ).map { Arguments.of(Named.of(it.name, it)) }
    }
}

private fun accessibleChildNames(context: AccessibleContext): List<String?> =
    List(context.accessibleChildrenCount) { context.getAccessibleChild(it).accessibleContext.accessibleName }

private fun ComposeSwingTest.assertTreeValid(message: String) {
    onRoot().assertProperty(null, message) { takeUnless { isValid } }
    val descendants = onRoot().onDescendants()
    repeat(descendants.fetchSize()) { descendants[it].assertProperty(null, message) { takeUnless { isValid } } }
}
