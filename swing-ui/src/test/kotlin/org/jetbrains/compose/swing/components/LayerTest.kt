package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.interaction.performMouseMove
import org.jetbrains.compose.swing.test.interaction.performMousePress
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.JScrollPane
import javax.swing.plaf.LayerUI
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
    fun theChildDeclaringTheViewBecomesTheLayersView() = runComposeSwingTest {
        setContent {
            Layer(ui = remember { LayerUI<Component>() }) {
                Label(text = "body", modifier = SwingModifier.view())
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val label = onNodeOfType<JLabel>().fetch()
        assertSame(label, layer.view, "the child declaring the view region should be the layer's view")
        assertSame(layer, label.parent, "and the layer should hold it")
    }

    @Test
    fun removingTheViewChildReleasesTheSlot() = runComposeSwingTest {
        var present by mutableStateOf(true)
        setContent {
            Layer(ui = remember { LayerUI<Component>() }) {
                if (present) Label(text = "body", modifier = SwingModifier.view())
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
                    Label(text = "second", modifier = SwingModifier.view())
                } else {
                    Label(text = "first", modifier = SwingModifier.view())
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
    fun aChildNamingNoRegionIsRefused() = runComposeSwingTest {
        // A layer reaches its two children through setters of its own and refuses an indexed add
        // outright, so a child naming no region would be held by nothing.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Layer(ui = remember { LayerUI<Component>() }) {
                        Label(text = "loose")
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue("names none" in message, "the refusal should say the child named no region: $message")
        assertTrue("JLayer" in message, "the refusal should name the host that holds the regions: $message")
        assertTrue(
            "SwingModifier.view()" in message && "GlassPane { }" in message,
            "the refusal should name both builders that would place the child: $message",
        )
    }

    @Test
    fun aLayerOverATableKeepsTheTablesAnswersAboutItsOwnScrolling() = runComposeSwingTest {
        // The whole reason the view is a slot: a JLayer forwards the five Scrollable methods to its view,
        // so the pane goes on scrolling by the table's rows. A container holding the table answers for
        // itself instead, and the table's answers never reach the pane.
        setContent {
            Panel(PanelLayout.Box()) {
                ScrollPane(modifier = SwingModifier.testTag(LAYERED_PANE)) {
                    Layer(
                        modifier = SwingModifier.viewport(),
                        onPaint = { _, _, _, paintView -> paintView() },
                    ) {
                        Table(model = rows(), rowHeight = ROW_HEIGHT, modifier = SwingModifier.view())
                    }
                }
                ScrollPane(modifier = SwingModifier.testTag(BARE_PANE)) {
                    Table(model = rows(), rowHeight = ROW_HEIGHT, modifier = SwingModifier.viewport())
                }
                ScrollPane(modifier = SwingModifier.testTag(WRAPPED_PANE)) {
                    Panel(PanelLayout.Box(), SwingModifier.viewport()) {
                        Table(model = rows(), rowHeight = ROW_HEIGHT)
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
    fun aGlassPanePaintsOverTheViewAndTheLayerKeepsThePaneItHadBefore() = runComposeSwingTest {
        var showPane by mutableStateOf(false)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Canvas(modifier = SwingModifier.view().preferredSize(SIZE)) { g, width, height ->
                    g.color = VIEW_COLOR
                    g.fillRect(0, 0, width, height)
                }
                if (showPane) {
                    GlassPane {
                        Canvas { g, width, height ->
                            g.color = PANE_COLOR
                            g.fillRect(0, 0, width, height)
                        }
                    }
                }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val carried = assertNotNull(layer.glassPane, "a layer builds a glass pane of its own in its constructor")
        assertFalse(carried.isVisible, "the pane a layer builds for itself starts hidden")
        assertEquals(VIEW_COLOR.rgb, centerPixelOf(layer), "with no pane declared the view is what shows")

        showPane = true
        awaitIdle()

        val declared = assertNotNull(layer.glassPane, "the declaration should install a pane of its own")
        assertNotSame(carried, declared, "the declared pane should displace the one the layer carried")
        assertFalse(declared.isOpaque, "the pane must be transparent where its content paints nothing")
        assertTrue(declared.isVisible, "an installed pane starts hidden, so the declaration must show it")
        assertEquals(PANE_COLOR.rgb, centerPixelOf(layer), "the pane's content must paint over the view")

        showPane = false
        awaitIdle()

        // setGlassPane(null) would empty the slot, and a layer builds its own pane only in its
        // constructor - so what an outgoing declaration puts back is the pane it displaced.
        assertSame(carried, layer.glassPane, "the layer should carry the pane it had before the declaration")
        assertFalse(carried.isVisible, "and it should be shown as it was shown, which is not at all")
        assertEquals(VIEW_COLOR.rgb, centerPixelOf(layer), "the view shows again once the pane is gone")
    }

    @Test
    fun swappingWhichDeclarationFillsTheGlassPaneStillRestoresTheLayersOwnPane() = runComposeSwingTest {
        // A pass that swaps one declaration for another installs the arriving pane before the outgoing
        // one is taken out, so the pane put back at the end has to be the layer's own rather than the
        // dead panel of whichever declaration came first.
        var present by mutableStateOf(true)
        var alternate by mutableStateOf(false)
        setContent {
            Layer(onPaint = { _, _, _, paintView -> paintView() }) {
                Label(text = "body", modifier = SwingModifier.view())
                if (present) {
                    if (alternate) {
                        GlassPane { Label(text = "second") }
                    } else {
                        GlassPane { Label(text = "first") }
                    }
                }
            }
        }

        val layer = onNodeOfType<JLayer<*>>().fetch()
        val first = assertNotNull(layer.glassPane, "the first declaration should fill the slot")

        alternate = true
        awaitIdle()

        val second = assertNotNull(layer.glassPane, "the branch now declared should fill the slot")
        assertNotSame(first, second, "each declaration builds a pane of its own")
        assertTrue(second.isVisible, "the pane now declared should be shown")

        present = false
        awaitIdle()

        val restored = assertNotNull(layer.glassPane, "the layer should be left with a pane")
        assertNotSame(first, restored, "the pane put back must not be the first declaration's dead panel")
        assertNotSame(second, restored, "nor the second's")
        assertFalse(restored.isVisible, "the layer's own pane comes back hidden, as it was")
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
                Label(text = "body", modifier = SwingModifier.view())
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
                Label(text = "body", modifier = SwingModifier.view())
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
                Label(text = "body", modifier = SwingModifier.view())
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
                    Label(text = "body", modifier = SwingModifier.view())
                }
                Layer(
                    ui = remember { LayerUI<Component>() },
                    eventMask = mask,
                    modifier = SwingModifier.testTag(RAW_LAYER),
                ) {
                    Label(text = "body", modifier = SwingModifier.view())
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
                    Label(text = "body", modifier = SwingModifier.view().preferredSize(VIEW_SIZE))
                }
                Layer(ui = remember { LayerUI<Component>() }, modifier = SwingModifier.testTag(RAW_LAYER)) {
                    Label(text = "body", modifier = SwingModifier.view().preferredSize(VIEW_SIZE))
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
                Label(text = "body", modifier = SwingModifier.view())
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
                Label(text = "body", modifier = SwingModifier.view())
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

    /** Gives [component] and everything under it the bounds a paint pass reads. */
    private fun layOut(component: JComponent) {
        component.size = SIZE
        layOutTree(component)
    }

    /** The color [component] shows in its middle, rasterized off-screen at [SIZE]. */
    private fun centerPixelOf(component: JComponent): Int {
        layOut(component)
        val image = BufferedImage(SIZE.width, SIZE.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            component.paint(graphics)
        } finally {
            graphics.dispose()
        }
        return image.getRGB(SIZE.width / 2, SIZE.height / 2)
    }

    /** Runs each container's layout, top down, giving every child the bounds a paint pass reads. */
    private fun layOutTree(container: Container) {
        container.doLayout()
        for (child in container.components) {
            if (child is Container) layOutTree(child)
        }
    }

    private fun rows(): DefaultTableModel =
        DefaultTableModel(arrayOf(arrayOf<Any>("a"), arrayOf<Any>("b"), arrayOf<Any>("c")), arrayOf<Any>("col"))

    private companion object {
        const val LAYERED_PANE = "layered-pane"
        const val BARE_PANE = "bare-pane"
        const val WRAPPED_PANE = "wrapped-pane"
        const val CALLBACK_LAYER = "callback-layer"
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
