package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.awt.Rectangle
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val PANEL_WIDTH = 300
private const val PANEL_HEIGHT = 200
private const val CONTENT_WIDTH = 80
private const val CONTENT_HEIGHT = 30
private const val PANEL_TAG = "panel"

/**
 * The fill a child declares to whichever container honors it. [Row], [Column] and [Box] fold it into the
 * constraint they already build, and their own tests pin that; what this pins is the vocabulary itself,
 * as a container outside this module gets it: the builder inherited from [FillWidthScope] /
 * [FillHeightScope], and the declaration read back through [ParentFill].
 */
class FillScopeTest {
    @Test
    fun aContainerReadingParentFillLaysAFillingChildOutAtItsOwnExtent() =
        runComposeSwingTest {
            setContent {
                FillPanel {
                    Content(SwingModifier.fillHeight())
                }
            }

            assertEquals(
                Rectangle(0, 0, CONTENT_WIDTH, PANEL_HEIGHT),
                panel().components.single().bounds,
                "the child should take the container's height and the width it prefers",
            )
        }

    @Test
    fun aChildFillingBothAxesTakesTheWholeContainer() =
        runComposeSwingTest {
            setContent {
                FillPanel {
                    Content(SwingModifier.fillWidth().fillHeight())
                }
            }

            assertEquals(
                Rectangle(0, 0, PANEL_WIDTH, PANEL_HEIGHT),
                panel().components.single().bounds,
                "a child declaring both fills should take the whole container",
            )
        }

    @Test
    fun aFillingChildIsHeldToAMaximumSizeItDeclares() =
        runComposeSwingTest {
            setContent {
                FillPanel {
                    Content(SwingModifier.fillWidth().maximumSize(PANEL_WIDTH / 2, PANEL_HEIGHT))
                }
            }

            assertEquals(
                PANEL_WIDTH / 2,
                panel().components.single().width,
                "the fill should stop at the maximum size the child declares",
            )
        }

    @Test
    fun aChildDeclaringNoFillKeepsTheExtentItPrefers() =
        runComposeSwingTest {
            setContent {
                FillPanel {
                    Content(SwingModifier)
                }
            }

            assertEquals(
                Rectangle(0, 0, CONTENT_WIDTH, CONTENT_HEIGHT),
                panel().components.single().bounds,
                "a child that declares nothing should be laid out at the extent it prefers",
            )
        }

    @Test
    fun aChainDeclaringAFillAndAnotherScopesConstraintIsRefused() {
        val fillFirst = with(RowScopeImpl) { with(PlainFillScope) { SwingModifier.fillWidth() }.weight(1f) }
        val weightFirst = with(PlainFillScope) { with(RowScopeImpl) { SwingModifier.weight(1f) }.fillWidth() }

        for (declared in listOf(fillFirst, weightFirst)) {
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    runComposeSwingTest {
                        setContent {
                            Row {
                                Label("placed", modifier = declared)
                            }
                        }
                    }
                }

            assertTrue(
                "declares parts of two kinds" in failure.message.orEmpty(),
                "the refusal should name both scopes the modifier declared to: ${failure.message}",
            )
        }
    }

    @Test
    fun testFillWidthInspectableValue() {
        val declared = with(PlainFillScope) { SwingModifier.fillWidth() }

        assertEquals("fillWidth", declared.lastElement().name, "fillWidth must report itself under its own name")
    }

    @Test
    fun testFillHeightInspectableValue() {
        val declared = with(PlainFillScope) { SwingModifier.fillHeight() }

        assertEquals("fillHeight", declared.lastElement().name, "fillHeight must report itself under its own name")
    }

    private fun ComposeSwingTest.panel(): JComponent = onNodeWithTag(PANEL_TAG).fetch<JComponent>()
}

/** A container outside any of this module's own, offering the fill and nothing else. */
private object PlainFillScope :
    FillWidthScope,
    FillHeightScope

/**
 * The container [PlainFillScope] belongs to: it reads what each child declared through [ParentFill], as
 * a container in another module does, and places every child at the container's own origin.
 */
@Composable
private fun FillPanel(content: @Composable PlainFillScope.() -> Unit) {
    SwingNode(
        factory = { JPanel(FillLayout()) },
        modifier = SwingModifier.testTag(PANEL_TAG).preferredSize(PANEL_WIDTH, PANEL_HEIGHT),
        content = { PlainFillScope.content() },
    )
}

@Composable
private fun Content(modifier: SwingModifier) {
    Label("content", modifier = modifier.preferredSize(CONTENT_WIDTH, CONTENT_HEIGHT))
}

/** Lays each child out at the extent it prefers, and at the container's own along an axis it fills. */
private class FillLayout : LayoutManager2 {
    private val declared = IdentityHashMap<Component, ParentFill>()

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        when (constraints) {
            null -> declared.remove(component)
            is ParentFill -> declared[component] = constraints
            else -> throw IllegalArgumentException("cannot add to layout: $constraints")
        }
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    override fun removeLayoutComponent(component: Component) {
        declared.remove(component)
    }

    override fun invalidateLayout(target: Container): Unit = Unit

    override fun preferredLayoutSize(parent: Container): Dimension = Dimension(PANEL_WIDTH, PANEL_HEIGHT)

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension(0, 0)

    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float = 0f

    override fun getLayoutAlignmentY(target: Container): Float = 0f

    override fun layoutContainer(parent: Container) {
        for (index in 0 until parent.componentCount) {
            val child = parent.getComponent(index)
            val fill = declared[child]
            val preferred = child.preferredSize
            val maximum = if (child.isMaximumSizeSet) child.maximumSize else null
            val width = if (fill?.fillsWidth == true) parent.width else preferred.width
            val height = if (fill?.fillsHeight == true) parent.height else preferred.height
            child.setBounds(
                0,
                0,
                minOf(width, maximum?.width ?: width),
                minOf(height, maximum?.height ?: height),
            )
        }
    }
}

/** The last entry of this modifier that describes itself - the one the builder under test declared. */
private fun SwingModifier.lastElement(): SwingModifier.InspectableElement =
    foldIn<SwingModifier.InspectableElement?>(null) { last, element ->
        element as? SwingModifier.InspectableElement ?: last
    } ?: error("the modifier declares nothing that describes itself")
