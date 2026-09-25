package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A `paintOutsets` value declared on a component or provided through [DefaultPaintOutsets]: one per component, taking
 * effect under a Foundation parent and left out under any other.
 */
class PaintOutsetsDeclarationTest {
    @Test
    fun aProvidedValueReachesEveryFieldOfTheSubtreeUntilWithdrawn() =
        runComposeSwingTest {
            val value = RecordingPaintOutsets(PaintOutsets.FullInsets)
            var provided by mutableStateOf<PaintOutsets?>(value)
            setContent {
                Column {
                    ProvideComponentDefaults(DefaultPaintOutsets provides provided) {
                        Column {
                            LinedField("first")
                            Row { LinedField("nested") }
                            ProvideComponentDefaults(DefaultPaintOutsets provides null) { LinedField("masked") }
                        }
                    }
                    LinedField("outside")
                }
            }
            val first = onNodeWithTag("first").fetch<JComponent>()
            val nested = onNodeWithTag("nested").fetch<JComponent>()
            assertEquals(-LINE, first.layoutBounds.x)
            assertEquals(-LINE, nested.layoutBounds.x)
            assertEquals(
                0,
                onNodeWithTag("masked").fetch().layoutBounds.x,
                "a nested provides null keeps the full bounds",
            )
            assertEquals(
                0,
                onNodeWithTag("outside").fetch().layoutBounds.x,
                "a sibling outside the provision is unchanged",
            )

            value.asked.clear()
            first.revalidate()
            nested.revalidate()
            awaitIdle()
            assertTrue(value.asked.containsAll(listOf(first, nested)), "each layout asks the value about its fields")

            provided = null
            awaitIdle()

            assertEquals(0, first.layoutBounds.x, "a withdrawn value leaves the full bounds")
            assertEquals(0, nested.layoutBounds.x, "in a nested row too")
            value.asked.clear()
            first.revalidate()
            nested.revalidate()
            awaitIdle()
            assertEquals(emptySet(), value.asked, "a withdrawn value is asked about no component")
        }

    @Test
    fun aValueProvidedOverSwingParentsAndRegionsIsLeftOutThere() =
        runComposeSwingTest {
            var provided by mutableStateOf<PaintOutsets?>(PaintOutsets.FullInsets)
            setContent {
                ProvideComponentDefaults(DefaultPaintOutsets provides provided) {
                    Column { LinedField("foundation") }
                    Panel(PanelLayout.Border()) { LinedField("border", SwingModifier.center()) }
                    Panel(PanelLayout.Box()) { LinedField("box") }
                    ScrollPane {
                        Viewport {
                            LinedField("viewport", SwingModifier)
                        }
                    }
                    SplitPane {
                        LinedField("first", SwingModifier.first())
                        LinedField("second", SwingModifier.second())
                    }
                    TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                        LinedField("tab", SwingModifier.tab("tab"))
                    }
                    Layer(onPaint = { _, _, _, paintView -> paintView() }) { LinedField("layer") }
                }
            }
            val foundation = onNodeWithTag("foundation").fetch()
            val leftOut = listOf("border", "box", "viewport", "first", "second", "tab", "layer")
            val placed = leftOut.associateWith { onNodeWithTag(it).fetch().bounds }
            assertEquals(-LINE, foundation.x, "the Foundation child takes the value")

            for ((next, x) in listOf(TwoPixels to -2, null to 0)) {
                provided = next
                awaitIdle()

                assertEquals(x, foundation.x, "$next: the Foundation child takes the new value")
                assertEquals(
                    placed,
                    leftOut.associateWith { onNodeWithTag(it).fetch().bounds },
                    "$next: a child of any other parent keeps the bounds that parent gives it",
                )
            }
        }

    @Test
    fun aMeasuringParentThatIsNotAFoundationContainerNeverReceivesTheDeclaration() =
        runComposeSwingTest {
            var provided by mutableStateOf(PaintOutsets.FullInsets)
            val layout = OwnElementsLayout()
            setContent {
                ProvideComponentDefaults(DefaultPaintOutsets provides provided) {
                    SwingNode(factory = { JPanel(layout) }) {
                        LinedField("inheriting")
                        LinedField("declaring", SwingModifier.paintOutsets(TwoPixels) then OwnElement)
                    }
                }
            }
            val inheriting = onNodeWithTag("inheriting").fetch()
            val declaring = onNodeWithTag("declaring").fetch()
            val declarations = layout.declarations

            provided = TwoPixels
            awaitIdle()

            assertEquals(emptyList(), layout.declared[inheriting], "a provided value is left out")
            assertEquals(listOf<ParentLayoutElement>(OwnElement), layout.declared[declaring], "and a declared one")
            assertEquals(declarations, layout.declarations, "a changed value declares nothing again")
            assertEquals(Rectangle(Point(0, 0), inheriting.preferredSize), inheriting.bounds, "the parent's own bounds")
            assertEquals(Rectangle(Point(inheriting.width, 0), declaring.preferredSize), declaring.bounds)
        }

    @Test
    fun anExplicitValueReplacesTheProvidedOneAtItsOwnPosition() =
        runComposeSwingTest {
            val provided = RecordingPaintOutsets(TwoPixels)
            setContent {
                ProvideComponentDefaults(DefaultPaintOutsets provides provided) {
                    Column {
                        Label("label", modifier = SwingModifier.testTag("label"))
                        LinedField("inheriting")
                        LinedField("field", SwingModifier.width(200).paintOutsets(PaintOutsets.FullInsets))
                    }
                }
            }
            val label = onNodeWithTag("label").fetch()
            val inheriting = onNodeWithTag("inheriting").fetch<JComponent>()
            val field = onNodeWithTag("field").fetch<JComponent>()
            assertEquals(label.x, inheriting.x + 2, "a field declaring nothing takes the provided value")
            assertEquals(label.x, field.x + LINE, "counted once, the explicit value")
            assertEquals(200 + 2 * LINE, field.width, "inside the width, where it is declared")
            assertTrue(inheriting in provided.asked, "the provided value is asked about the field that inherits it")
            assertFalse(field in provided.asked, "and never about the one declaring its own")
        }

    @Test
    fun aFieldMovedToAPanelTakesItsFullBounds() =
        runComposeSwingTest {
            var inColumn by mutableStateOf(true)
            val field = movableContentOf { LinedField("field") }
            setContent {
                ProvideComponentDefaults(DefaultPaintOutsets provides PaintOutsets.FullInsets) {
                    Column { if (inColumn) field() }
                    Panel(PanelLayout.Flow(hgap = 0, vgap = 0)) { if (!inColumn) field() }
                }
            }
            val moved = onNodeWithTag("field").fetch()
            assertEquals(-LINE, moved.x)

            inColumn = false
            awaitIdle()

            assertSame(moved, onNodeWithTag("field").fetch())
            assertEquals(moved.preferredSize, moved.size, "a panel gives it its full bounds")
            assertTrue(moved.parent.layout is FlowLayout)
            assertEquals(0, moved.x)

            inColumn = true
            awaitIdle()

            assertSame(moved, onNodeWithTag("field").fetch())
            assertEquals(-LINE, moved.x, "back in the column its insets are paint outsets again")
        }

    @Test
    fun aPressOnTheBandPastARowReachesTheFieldUnlessTheRowClips() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var presses = 0
            var clipped by mutableStateOf(false)
            setWindowContent {
                Box {
                    Box(modifier = SwingModifier.padding(40)) {
                        Row(modifier = SwingModifier.testTag("row").let { if (clipped) it.clipToBounds() else it }) {
                            LinedField(
                                "field",
                                SwingModifier
                                    .paintOutsets(PaintOutsets.FullInsets)
                                    .mouseListener(onMousePressed = { presses++ }),
                            )
                        }
                    }
                }
            }
            val row = windowNode("row")
            val band = SwingUtilities.convertPoint(row, row.paintOutsets.left - 2, 10, frame())

            click(band)
            assertEquals(1, presses, "a press on the band past the row reaches the field")

            clipped = true
            awaitIdle()
            click(band)
            assertEquals(1, presses, "a press the row's clip cuts away misses")
        }

    @Test
    fun overlappingFieldsGiveThePressToTheOneOnTop() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var spacing by mutableIntStateOf(0)
            val pressed = mutableListOf<String>()
            setWindowContent {
                Box {
                    Column(
                        modifier = SwingModifier.padding(40),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        for (tag in listOf("upper", "lower")) {
                            LinedField(
                                tag,
                                SwingModifier
                                    .paintOutsets(PaintOutsets.FullInsets)
                                    .mouseListener(onMousePressed = { pressed += tag }),
                            )
                        }
                    }
                }
            }
            click(bottomOf("upper"))
            spacing = 2 * LINE
            awaitIdle()
            click(bottomOf("upper"))
            assertEquals(listOf("lower", "upper"), pressed, "the later field is on top where they overlap")
        }

    private fun ComposeSwingTest.bottomOf(tag: String): Point {
        val field = windowNode(tag)
        return SwingUtilities.convertPoint(field, field.width / 2, field.height - 2, frame())
    }
}

/** A declaration only an [OwnElementsLayout] parent reads. */
private data object OwnElement : ParentLayoutElement {
    override val parentProtocol: ParentProtocol =
        parentProtocolOf("own elements parent") { it.layout is OwnElementsLayout }
}

/**
 * A measuring manager that is not Foundation's: it takes only its own elements, and places its children as a flow
 * layout does.
 */
private class OwnElementsLayout :
    FlowLayout(LEADING, 0, 0),
    MeasurementLayoutManager {
    val declared = HashMap<Component, List<ParentLayoutElement>>()

    var declarations = 0
        private set

    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        require(elements.all { it === OwnElement }) { "OwnElementsLayout reads only its own elements: $elements" }
        declared[component] = elements
        declarations++
    }

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ): Unit = Unit

    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float = 0.5f

    override fun getLayoutAlignmentY(target: Container): Float = 0.5f

    override fun invalidateLayout(target: Container): Unit = Unit
}
