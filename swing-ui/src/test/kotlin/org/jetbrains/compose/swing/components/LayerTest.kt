package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.interaction.performMouseMove
import org.jetbrains.compose.swing.test.interaction.performMousePress
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.plaf.LayerUI
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Layer]: the two regions a `JLayer` holds its children in, the delegate and the
 * event mask the raw overload declares, and the event callbacks the other one runs. What a paint
 * callback shows is in [LayerCallbackTest], and what a declared glass pane answers for is in
 * [LayerGlassPaneTest].
 */
class LayerTest {
    @Test
    fun theChildNamingNoRegionBecomesTheLayersView() = runComposeSwingTest {
        setContent {
            Layer(ui = remember { LayerUI<Component>() }) {
                Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val label = onNodeOfType<JLabel>().fetch()
        assertSame(label, layer.view, "the child naming no region should be the layer's view")
        assertSame(layer, label.parent, "and the layer should hold it")
    }

    @Test
    fun removingTheViewChildReleasesTheSlot() = runComposeSwingTest {
        var present by mutableStateOf(true)
        setContent {
            Layer(ui = remember { LayerUI<Component>() }) {
                if (present) Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val label = onNodeOfType<JLabel>().fetch()

        present = false
        awaitIdle()

        // A layer does not override remove(int), so taking the child out by index would detach it while
        // getView() went on naming it. The slot is what is released.
        assertNull(layer.view, "the view slot should be empty once the child that filled it is gone")
        assertNull(label.parent, "and the child should be detached from the layer")
    }

    @Test
    fun swappingWhichComposableFillsTheViewKeepsFillingIt() = runComposeSwingTest {
        var alternate by mutableStateOf(false)
        setContent {
            Layer(ui = remember { LayerUI<Component>() }) {
                // Two declarations of one region, one at a time: the pass that swaps them may hold both
                // children while it runs, and one child in the slot is what it settles at.
                if (alternate) {
                    Label(text = "second")
                } else {
                    Label(text = "first")
                }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        assertEquals("first", (layer.view as JLabel).text, "the view should start with the first branch")

        alternate = true
        awaitIdle()

        assertEquals("second", (layer.view as JLabel).text, "the branch now declared should fill the view")
    }

    @Test
    fun twoChildrenNamingNoRegionAreRefused() = runComposeSwingTest {
        // A layer shows one view, so a second child that names no region has no place to be.
        val message =
            failureOf {
                setContent {
                    Layer(ui = remember { LayerUI<Component>() }) {
                        Label(text = "one")
                        Table(model = rows())
                    }
                }
                awaitIdle()
            }
        assertTrue("shows one unnamed child as its content" in message, "the refusal should say why: $message")
        assertTrue("JLayer" in message, "the refusal should name the layer: $message")
        assertTrue("JLabel" in message && "JTable" in message, "the refusal should name both children: $message")
    }

    @Test
    fun contentMovedBetweenAPanelAndALayerLandsInBoth() = runComposeSwingTest {
        var inLayer by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf { Table(model = rows(), rowHeight = ROW_HEIGHT) } }
            Panel(PanelLayout.Box()) {
                Panel(modifier = SwingModifier.testTag(PANEL)) { if (!inLayer) content() }
                Layer(ui = remember { LayerUI<Component>() }, modifier = SwingModifier.testTag(RAW_LAYER)) {
                    if (inLayer) content()
                }
            }
        }
        val table = onNodeOfType<JTable>().fetch()
        val panel = onNodeWithTag(PANEL).fetch<JPanel>()
        val layer = onNodeWithTag(RAW_LAYER).fetch<JLayer<*>>()
        assertSame(panel, table.parent, "the content starts in the panel, added by index")
        assertNull(layer.view, "the layer starts with no view")

        inLayer = true
        awaitIdle()
        assertSame(table, layer.view, "the content moved to the layer should be its view")
        assertEquals(0, panel.componentCount, "the panel should have given the content up")

        inLayer = false
        awaitIdle()
        assertNull(layer.view, "the layer should be released of the view that left")
        assertSame(panel, table.parent, "the content moved back to the panel should be held by index")
    }

    @Test
    fun aLayerOverATableKeepsTheTablesAnswersAboutItsOwnScrolling() = runComposeSwingTest {
        // The whole reason the view is a slot: a JLayer forwards the five Scrollable methods to its view,
        // so the pane goes on scrolling by the table's rows. A container holding the table answers for
        // itself instead, and the table's answers never reach the pane.
        setContent {
            Panel(PanelLayout.Box()) {
                ScrollPane(modifier = SwingModifier.testTag(LAYERED_PANE)) {
                    Viewport {
                        Layer(
                            onPaint = { _, _, _, paintView -> paintView() },
                        ) {
                            Table(model = rows(), rowHeight = ROW_HEIGHT)
                        }
                    }
                }
                ScrollPane(modifier = SwingModifier.testTag(BARE_PANE)) {
                    Viewport { Table(model = rows(), rowHeight = ROW_HEIGHT) }
                }
                ScrollPane(modifier = SwingModifier.testTag(WRAPPED_PANE)) {
                    Viewport {
                        Panel(PanelLayout.Box()) {
                            Table(model = rows(), rowHeight = ROW_HEIGHT)
                        }
                    }
                }
            }
        }

        val layered = onNodeWithTag(LAYERED_PANE).fetch<JScrollPane>().verticalScrollBar.getUnitIncrement(1)
        val bare = onNodeWithTag(BARE_PANE).fetch<JScrollPane>().verticalScrollBar.getUnitIncrement(1)
        val wrapped = onNodeWithTag(WRAPPED_PANE).fetch<JScrollPane>().verticalScrollBar.getUnitIncrement(1)

        assertEquals(ROW_HEIGHT, bare, "a table in a pane scrolls by one of its own rows")
        assertEquals(bare, layered, "a table under a layer must go on scrolling the pane by its own rows")
        assertNotEquals(
            layered,
            wrapped,
            "the control: a Panel is not Scrollable, so a pane over one falls back to its own default " +
                "increment - which is what the layer's view slot preserves the table from",
        )
    }

    @Test
    fun aGlassPanePaintsOverTheViewUntilItLeaves() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Canvas(modifier = SwingModifier.preferredSize(SIZE)) { g, width, height ->
                    g.color = VIEW_COLOR
                    g.fillRect(0, 0, width, height)
                }
                if (showPane) {
                    GlassPane(PanelLayout.Border()) {
                        Canvas { g, width, height ->
                            g.color = PANE_COLOR
                            g.fillRect(0, 0, width, height)
                        }
                    }
                }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val own = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own in its constructor")
        assertEquals(
            VIEW_COLOR.rgb,
            onNodeOfType<JLayer<*>>().captureToImage().getRGB(SIZE.width / 2, SIZE.height / 2),
            "with no pane declared the view is what shows",
        )

        showPane = true
        awaitIdle()

        assertSame(own, layer.glassPane, "the declaration should show the layer's own pane")
        assertFalse(own.isOpaque, "the pane must be transparent where its content paints nothing")
        assertEquals(
            PANE_COLOR.rgb,
            onNodeOfType<JLayer<*>>().captureToImage().getRGB(SIZE.width / 2, SIZE.height / 2),
            "the pane's content must paint over the view",
        )

        showPane = false
        awaitIdle()

        assertSame(own, layer.glassPane, "the layer should keep its own pane")
        assertFalse(own.isVisible, "hidden again once the declaration leaves")
        assertEquals(
            VIEW_COLOR.rgb,
            onNodeOfType<JLayer<*>>().captureToImage().getRGB(SIZE.width / 2, SIZE.height / 2),
            "the view shows again once the pane is gone",
        )
    }

    @Test
    fun aDeclaredMaskStandsAfterTheDelegateItWasDeclaredBesideIsReplaced() = runComposeSwingTest {
        // The two writes are one declaration because a delegate sets its own mask from installUI: a mask
        // written before the delegate is installed is overwritten by it. Declared here beside a delegate
        // that does exactly that, the declared mask is the one that must stand.
        val plain = LayerUI<Component>()
        val opinionated = MaskSettingLayerUI(AWTEvent.MOUSE_EVENT_MASK)
        var installed by mutableStateOf(plain)
        setContent {
            Layer(ui = installed, eventMask = AWTEvent.MOUSE_WHEEL_EVENT_MASK) {
                Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        assertSame(plain, layer.ui, "the declared delegate should be installed")
        assertEquals(AWTEvent.MOUSE_WHEEL_EVENT_MASK, layer.layerEventMask, "the declared mask should be written")

        installed = opinionated
        awaitIdle()

        assertSame(opinionated, layer.ui, "the delegate now declared should be installed")
        assertEquals(1, opinionated.installs, "and installed once")
        assertEquals(
            AWTEvent.MOUSE_WHEEL_EVENT_MASK,
            layer.layerEventMask,
            "the declared mask must be written after the delegate is installed, so it stands over the " +
                "mask that delegate sets for itself",
        )
    }

    @Test
    fun replacingAnEqualButDistinctDelegateInstallsTheNewOne() = runComposeSwingTest {
        val first = EqualLayerUI()
        val second = EqualLayerUI()
        var installed by mutableStateOf<LayerUI<Component>>(first, referentialEqualityPolicy())
        setContent {
            Layer(ui = installed, eventMask = AWTEvent.MOUSE_EVENT_MASK) {
                Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        assertSame(first, layer.ui, "the first delegate should be installed")
        assertEquals(1, first.installs, "the first delegate should be installed once")

        installed = second
        awaitIdle()

        assertSame(second, layer.ui, "a distinct delegate must replace an equal one")
        assertEquals(1, first.uninstalls, "the replaced delegate should be uninstalled")
        assertEquals(1, second.installs, "the new delegate should be installed once")
        assertEquals(
            AWTEvent.MOUSE_EVENT_MASK,
            layer.layerEventMask,
            "the value-equal mask should still be written after delegate replacement",
        )
    }

    @Test
    fun changingOnlyTheMaskLeavesTheDelegateInstalled() = runComposeSwingTest {
        // Installing a delegate uninstalls and reinstalls whether or not it is the one already in place,
        // so a mask change alone must not reach setUI.
        val delegate = MaskSettingLayerUI(ownMask = null)
        var mask by mutableStateOf(AWTEvent.MOUSE_EVENT_MASK)
        setContent {
            Layer(ui = delegate, eventMask = mask) {
                Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        assertSame(delegate, layer.ui, "the declared delegate should be installed")
        assertEquals(1, delegate.installs, "installed once")

        mask = AWTEvent.MOUSE_MOTION_EVENT_MASK
        awaitIdle()

        assertEquals(AWTEvent.MOUSE_MOTION_EVENT_MASK, layer.layerEventMask, "the new mask should be written")
        assertSame(delegate, layer.ui, "the delegate should be the one that was there")
        assertEquals(1, delegate.installs, "and it should not have been installed again")
        assertEquals(0, delegate.uninstalls, "nor uninstalled")
    }

    @Test
    fun withdrawingTheMaskHandsItBackToTheDelegate() = runComposeSwingTest {
        val opinionated = MaskSettingLayerUI(AWTEvent.MOUSE_MOTION_EVENT_MASK)
        var mask by mutableStateOf<Long?>(AWTEvent.MOUSE_WHEEL_EVENT_MASK)
        setContent {
            Panel(PanelLayout.Box()) {
                Layer(ui = opinionated, eventMask = mask, modifier = SwingModifier.testTag(OPINIONATED_LAYER)) {
                    Label(text = "body")
                }
                Layer(
                    ui = remember { LayerUI<Component>() },
                    eventMask = mask,
                    modifier = SwingModifier.testTag(RAW_LAYER),
                ) {
                    Label(text = "body")
                }
            }
        }

        val withOwnMask = onNodeWithTag(OPINIONATED_LAYER).fetch<JLayer<*>>()
        val withoutOwnMask = onNodeWithTag(RAW_LAYER).fetch<JLayer<*>>()
        assertEquals(AWTEvent.MOUSE_WHEEL_EVENT_MASK, withOwnMask.layerEventMask, "the declared mask should be written")

        mask = null
        awaitIdle()

        assertEquals(
            AWTEvent.MOUSE_MOTION_EVENT_MASK,
            withOwnMask.layerEventMask,
            "withdrawing the mask should let the delegate set its own again",
        )
        assertSame(opinionated, withOwnMask.ui, "the delegate should stay the one declared")
        assertEquals(
            0L,
            withoutOwnMask.layerEventMask,
            "a delegate setting no mask should leave the layer observing nothing",
        )
    }

    @Test
    fun aLayerAlwaysCarriesADelegateAndTakesItsPreferredSizeFromItsView() = runComposeSwingTest {
        // Without a delegate a layer paints nothing, lays its view out never, and answers 0x0 for itself,
        // so the view's own preferred size never reaches the parent. Both overloads must leave one in.
        setContent {
            Panel(PanelLayout.Box()) {
                Layer(
                    modifier = SwingModifier.testTag(CALLBACK_LAYER),
                    onPaint = { _, _, _, paintView -> paintView() },
                ) {
                    Label(text = "body", modifier = SwingModifier.preferredSize(VIEW_SIZE))
                }
                Layer(ui = remember { LayerUI<Component>() }, modifier = SwingModifier.testTag(RAW_LAYER)) {
                    Label(text = "body", modifier = SwingModifier.preferredSize(VIEW_SIZE))
                }
            }
        }

        for (tag in listOf(CALLBACK_LAYER, RAW_LAYER)) {
            val layer = onNodeWithTag(tag).fetch<JLayer<*>>()
            assertNotNull(layer.ui, "$tag should carry a delegate")
            assertEquals(VIEW_SIZE, layer.preferredSize, "$tag should answer for its view rather than collapse")
        }
    }

    @Test
    fun eachDeclaredCallbackRunsForTheEventsItNames() = runComposeSwingTest {
        val seen = mutableListOf<String>()
        setContent {
            Layer(
                onMouseEvent = { seen += "mouse" },
                onMouseMotionEvent = { seen += "motion" },
                onMouseWheelEvent = { seen += "wheel" },
            ) {
                Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>()
        layer.performMousePress()
        layer.performMouseMove(Point(1, 1))
        layer.performMouseWheel(rotation = 1)

        assertEquals(
            listOf("mouse", "motion", "wheel"),
            seen,
            "each event should reach the callback declared for the events it belongs to",
        )
    }

    @Test
    fun anEventCallbackReadsWhatTheDeclarationHoldsRightNow() = runComposeSwingTest {
        // The event callbacks change nothing that is on screen already, so a pass writing fresh ones
        // costs a field write and installs nothing: what a later event runs is what the last pass wrote.
        val declared = mutableIntStateOf(1)
        var seen = 0
        setContent {
            val value = declared.intValue
            Layer(onMouseEvent = { seen = value }) {
                Label(text = "body")
            }
        }

        val layer = onNodeOfType<JLayer<*>>()
        val delegate = layer.fetch().ui
        layer.performMousePress()
        assertEquals(1, seen, "the callback the first pass declared should run")

        declared.intValue = 2
        awaitIdle()
        layer.performMousePress()

        assertEquals(2, seen, "a later event should run the callback the last pass declared")
        assertSame(delegate, layer.fetch().ui, "and the delegate should not have been installed again")
    }

    private fun rows(): DefaultTableModel =
        DefaultTableModel(arrayOf(arrayOf<Any>("a"), arrayOf<Any>("b"), arrayOf<Any>("c")), arrayOf<Any>("col"))

    private companion object {
        const val LAYERED_PANE = "layered-pane"
        const val BARE_PANE = "bare-pane"
        const val WRAPPED_PANE = "wrapped-pane"
        const val CALLBACK_LAYER = "callback-layer"
        const val PANEL = "plain-panel"
        const val RAW_LAYER = "raw-layer"
        const val OPINIONATED_LAYER = "opinionated-layer"

        /** A row tall enough that no font's line height could be mistaken for it. */
        const val ROW_HEIGHT = 37

        val SIZE = Dimension(120, 80)
        val VIEW_SIZE = Dimension(140, 90)
        val VIEW_COLOR: Color = Color.RED
        val PANE_COLOR: Color = Color.BLUE
    }
}

/**
 * A delegate that counts its own installs, and sets a mask of its own from `installUI` the way a
 * `LayerUI` that owns the mask does.
 */
private class MaskSettingLayerUI(
    private val ownMask: Long?,
) : LayerUI<Component>() {
    var installs: Int = 0
        private set

    var uninstalls: Int = 0
        private set

    override fun installUI(c: JComponent) {
        super.installUI(c)
        installs++
        if (ownMask != null) (c as JLayer<*>).layerEventMask = ownMask
    }

    override fun uninstallUI(c: JComponent) {
        super.uninstallUI(c)
        uninstalls++
    }
}

/** A delegate whose equality intentionally hides identity, as a value-like UI might. */
private class EqualLayerUI : LayerUI<Component>() {
    var installs: Int = 0
        private set

    var uninstalls: Int = 0
        private set

    override fun installUI(c: JComponent) {
        super.installUI(c)
        installs++
    }

    override fun uninstallUI(c: JComponent) {
        super.uninstallUI(c)
        uninstalls++
    }

    override fun equals(other: Any?): Boolean = other is EqualLayerUI

    override fun hashCode(): Int = EqualLayerUI::class.hashCode()
}
