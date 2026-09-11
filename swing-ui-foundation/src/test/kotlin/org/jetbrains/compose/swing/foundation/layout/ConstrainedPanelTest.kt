package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ConstrainedPanelTest {
    @Test
    fun aPanelIsAnAccessiblePanelCarryingTheNameSetOnIt() =
        runComposeSwingTest {
            setContent {
                Box(modifier = SwingModifier.testTag("container-under-test").accessibleName("Toolbar")) {}
            }

            val context = onNodeWithTag("container-under-test").fetch().accessibleContext

            assertEquals(AccessibleRole.PANEL, context.accessibleRole, "A panel reports the accessible role PANEL.")
            assertEquals("Toolbar", context.accessibleName, "A panel reports the name declared on it.")
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

            assertEquals(
                listOf("first", "second", "third"),
                accessibleChildNames(onNodeWithTag("container-under-test").fetch().accessibleContext),
                "A panel lists its children to assistive technologies in the order they are declared.",
            )
        }

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

            val context = onNodeWithTag("container-under-test").fetch().accessibleContext
            assertEquals(
                listOf("second", "third", "raised"),
                accessibleChildNames(context),
                "A panel lists its children in paint order, the child painted last listed last.",
            )
            assertEquals(
                listOf(0, 1, 2),
                List(context.accessibleChildrenCount) {
                    context.getAccessibleChild(it).accessibleContext.accessibleIndexInParent
                },
                "Each child reports the index its panel lists it at.",
            )
        }

    @Test
    fun aPanelReportsAStateChangeToAssistiveTechnologies() =
        runComposeSwingTest {
            var enabled by mutableStateOf(true)
            setContent {
                Box(modifier = SwingModifier.testTag("container-under-test").enabled(enabled)) {}
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
                ScrollPane(modifier = SwingModifier.preferredSize(60, 50)) {
                    Box(modifier = SwingModifier.viewport().testTag("container-under-test").preferredSize(100, 80)) {}
                }
            }

            val panel = assertIs<ConstrainedPanel>(onNodeWithTag("container-under-test").fetch())

            assertFalse(
                panel.isOptimizedDrawingEnabled,
                "Overlapping children must repaint each other.",
            )
            assertEquals(
                Dimension(100, 80),
                panel.preferredScrollableViewportSize,
                "The viewport reports the size of the box it scrolls.",
            )
            assertEquals(
                panel.getFontMetrics(panel.font).height,
                panel.getScrollableUnitIncrement(Rectangle(0, 0, 10, 10), SwingConstants.VERTICAL, 1),
                "an arrow button moves the pane by a line of the panel's font",
            )
            assertEquals(
                25,
                panel.getScrollableBlockIncrement(Rectangle(0, 0, 50, 25), SwingConstants.VERTICAL, 1),
                "a page down is the visible height",
            )
            assertEquals(
                50,
                panel.getScrollableBlockIncrement(Rectangle(0, 0, 50, 25), SwingConstants.HORIZONTAL, 1),
                "a page across is the visible width",
            )
            assertEquals(
                Dimension(100, 80),
                panel.size,
                "a viewport smaller than the panel leaves it at its preferred size, so the pane scrolls it",
            )
        }

    @Test
    fun aPanelHoldingOneScrollableAnswersAPaneOnItsBehalf() =
        runComposeSwingTest {
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(100, 80)) {
                    Box(modifier = SwingModifier.viewport()) {
                        TextArea(value = "word ".repeat(200), onValueChange = {}, lineWrap = true)
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>().fetch()
            val area = onNodeOfType<JTextArea>().fetch()
            assertEquals(
                area.getScrollableUnitIncrement(pane.viewport.viewRect, SwingConstants.VERTICAL, 1),
                pane.verticalScrollBar.getUnitIncrement(1),
                "an arrow button moves the pane by a line of the text area the panel holds",
            )
            assertFalse(pane.horizontalScrollBar.isVisible, "content taking the viewport's width needs no scroll bar")
            assertEquals(pane.viewport.width, pane.viewport.view.width, "the panel is laid out at the viewport's width")
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
                ScrollPane(modifier = SwingModifier.preferredSize(100, 80)) {
                    Box(modifier = SwingModifier.viewport()) {
                        SwingNode(factory = { content })
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>().fetch()
            val panel = assertIs<Scrollable>(pane.viewport.view)
            assertEquals(content.preferredScrollableViewportSize, panel.preferredScrollableViewportSize)
            assertEquals(7, pane.verticalScrollBar.getBlockIncrement(1), "a page is the content's own page")
            assertFalse(pane.verticalScrollBar.isVisible, "content taking the viewport's height needs no scroll bar")
            assertEquals(
                pane.viewport.height,
                pane.viewport.view.height,
                "the panel is laid out at the viewport's height",
            )
        }

    @Test
    fun aPanelHoldingSeveralChildrenAnswersAPaneAsAPanelOfItsOwn() =
        runComposeSwingTest {
            setContent {
                ScrollPane(modifier = SwingModifier.preferredSize(400, 80)) {
                    Column(modifier = SwingModifier.viewport()) {
                        Box(modifier = SwingModifier.preferredSize(100, 80)) {}
                        Box(modifier = SwingModifier.preferredSize(200, 240)) {}
                    }
                }
            }

            val pane = onNodeOfType<JScrollPane>().fetch()
            val view = pane.viewport.view
            assertEquals(
                view.preferredSize,
                pane.viewport.preferredSize,
                "the pane sizes its viewport by the whole panel, not by one of its children",
            )
            assertEquals(pane.viewport.width, view.width, "the panel is laid out at the viewport's width")
        }
}

private fun accessibleChildNames(context: AccessibleContext): List<String?> =
    List(context.accessibleChildrenCount) { context.getAccessibleChild(it).accessibleContext.accessibleName }
