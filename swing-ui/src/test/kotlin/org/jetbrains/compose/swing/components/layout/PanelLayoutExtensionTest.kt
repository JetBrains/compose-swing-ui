package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * A [PanelLayout] written outside the library, with a [PanelScope] of its own, lays out a [Panel] the way
 * the built-in layouts do.
 */
class PanelLayoutExtensionTest {
    @Test
    fun aLayoutOfYourOwnInstallsItsManagerAndPlacesTheChildrenItsScopeNames() = runComposeSwingTest {
        var gap by mutableIntStateOf(3)
        setContent {
            Panel(Stacked(gap)) {
                Label("top", modifier = SwingModifier.top())
                Label("body")
            }
        }

        val panel = onNodeWithText("top").onParent().fetch<JPanel>()
        val layout = assertIs<BorderLayout>(panel.layout, "the panel should hold the layout's manager")
        assertEquals(3, layout.vgap, "the gap first declared")
        assertSame(
            onNodeWithText("top").fetch<JLabel>(),
            layout.getLayoutComponent(BorderLayout.NORTH),
            "the scope's placement call should reach the manager",
        )
        assertSame(
            onNodeWithText("body").fetch<JLabel>(),
            layout.getLayoutComponent(BorderLayout.CENTER),
            "a child naming no placement should stand where the manager puts it",
        )

        gap = 7
        awaitIdle()

        assertSame(panel, onNodeWithText("top").onParent().fetch(), "a parameter change should keep the panel")
        assertSame(layout, panel.layout, "a parameter change should keep the manager")
        assertEquals(7, layout.vgap, "the gap declared later")
        assertSame(
            onNodeWithText("top").fetch<JLabel>(),
            layout.getLayoutComponent(BorderLayout.NORTH),
            "the placement the manager holds for a child should survive the parameter change",
        )
    }

    @Test
    fun aManagerIsKeptAcrossLayoutsOfYourOwnOfTheSameClassAndReplacedByASubclass() = runComposeSwingTest {
        var layout by mutableStateOf<PanelLayout<PanelScope>>(Docked)
        setContent { Panel(layout) { Label("a") } }
        val panel = onNodeWithText("a").onParent().fetch<JPanel>()
        val docked = assertIs<BorderLayout>(panel.layout)

        layout = DockedAgain
        awaitIdle()

        assertSame(docked, panel.layout, "a layout of your own using the same manager class should keep the manager")

        layout = Rounded
        awaitIdle()

        assertIs<RoundedBorderLayout>(panel.layout, "a subclass of the standing manager's class should be installed")
        assertNotSame(docked, panel.layout, "a subclass of the standing manager's class should replace it")
    }

    @Test
    fun aNewLayoutKindOfYourOwnKeepsThePanelAndBuildsItsChildrenAnew() = runComposeSwingTest {
        var overlay by mutableStateOf(false)
        setContent {
            Panel(if (overlay) Overlay else PanelLayout.Flow()) { Label("a") }
        }
        val panel = onNodeWithText("a").onParent().fetch<JPanel>()
        val flowedChild = onNodeWithText("a").fetch<JLabel>()
        assertIs<FlowLayout>(panel.layout, "the panel should start under the layout declared first")

        overlay = true
        awaitIdle()

        val overlaidChild = onNodeWithText("a").fetch<JLabel>()
        assertSame(panel, overlaidChild.parent, "a new layout kind should keep the panel")
        assertIs<OverlayLayout>(panel.layout, "the panel should hold the layout declared later")
        assertNotSame(flowedChild, overlaidChild, "a new layout kind should build the children anew")
        assertEquals(1, panel.componentCount, "the child built for the earlier layout should be gone")
    }

    @Test
    fun aParameterTheManagerMeasuresWithRevalidatesAndOneItPlacesWithOnlyLaysOut() = runComposeSwingTest {
        var inset by mutableIntStateOf(0)
        var shift by mutableIntStateOf(0)
        setContent {
            Panel(Shifted(inset, shift), SwingModifier.preferredSize(200, 40)) { Label("child") }
        }
        val panel = onNodeWithText("child").onParent().fetch<JPanel>()
        val child = onNodeWithText("child").fetch<JLabel>()
        val manager = panel.layout

        withRecordedRepaints { recorded ->
            val before = child.x
            shift = 7
            awaitIdle()

            assertSame(manager, panel.layout, "a parameter change should keep the manager")
            assertEquals(before + 7, child.x, "a placement parameter should move the child")
            recorded.assertAskedForNoLayout(panel, "a placement parameter")
        }
        withRecordedRepaints { recorded ->
            val before = child.x
            inset = 11
            awaitIdle()

            assertEquals(before + 11, child.x, "a measured parameter should move the child")
            recorded.assertAskedForLayout(panel, "a measured parameter")
        }
    }
}

/** The placements [Stacked] offers. */
private sealed interface StackedScope : PanelScope {
    /** Places the child across the top of the container. */
    fun SwingModifier.top(): SwingModifier
}

private object StackedScopeImpl : StackedScope {
    override fun SwingModifier.top(): SwingModifier = layoutConstraint(BorderLayout.NORTH)
}

/** A body under a top child, [gap] apart. */
private data class Stacked(
    val gap: Int,
) : PanelLayout<StackedScope>(StackedScopeImpl) {
    override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
        updater.installManager({ BorderLayout(0, gap) }) { manager ->
            if (manager.vgap != gap) {
                manager.vgap = gap
                revalidate()
            }
        }
    }
}

private object PlainScope : PanelScope

/** Lays the children over one another, under a manager the library does not model. */
private object Overlay : PanelLayout<PanelScope>(PlainScope) {
    override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
        updater.installManager({ OverlayLayout(this) })
    }
}

/** Lays the children out under a plain `BorderLayout`. */
private object Docked : PanelLayout<PanelScope>(PlainScope) {
    override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
        updater.installManager({ BorderLayout() })
    }
}

/** Another layout of your own, under the same manager class as [Docked]. */
private object DockedAgain : PanelLayout<PanelScope>(PlainScope) {
    override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
        updater.installManager({ BorderLayout() })
    }
}

private class RoundedBorderLayout : BorderLayout()

/** A layout of your own whose manager is a subclass of [Docked]'s manager class. */
private object Rounded : PanelLayout<PanelScope>(PlainScope) {
    override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
        updater.installManager({ RoundedBorderLayout() })
    }
}

/** Places the children [inset] plus [shift] from the left edge; only [inset] is part of what it measures. */
private class ShiftedLayout : LayoutManager {
    var inset = 0
    var shift = 0

    override fun addLayoutComponent(
        name: String?,
        comp: Component,
    ) = Unit

    override fun removeLayoutComponent(comp: Component) = Unit

    override fun preferredLayoutSize(parent: Container): Dimension =
        parent.getComponent(0).preferredSize.let { Dimension(2 * inset + it.width, it.height) }

    override fun minimumLayoutSize(parent: Container): Dimension = preferredLayoutSize(parent)

    override fun layoutContainer(parent: Container) {
        for (child in parent.components) {
            child.setBounds(inset + shift, 0, child.preferredSize.width, child.preferredSize.height)
        }
    }
}

/** A layout of your own that asks for the pass each of its two parameters needs. */
private data class Shifted(
    val inset: Int,
    val shift: Int,
) : PanelLayout<PanelScope>(PlainScope) {
    override fun <C : Container> installOn(updater: SwingNodeUpdater<C>) {
        updater.installManager({ ShiftedLayout() }) { manager ->
            val measured = manager.inset != inset
            val placed = manager.shift != shift
            manager.inset = inset
            manager.shift = shift
            if (measured) {
                revalidate()
            } else if (placed) {
                doLayout()
            }
        }
    }
}
