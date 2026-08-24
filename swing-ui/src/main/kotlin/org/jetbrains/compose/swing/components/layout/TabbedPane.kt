@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.constants.TabLayoutPolicy
import org.jetbrains.compose.swing.constants.TabPlacement
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.containerListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.ChildPlacement
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ContainerEvent
import java.awt.event.ContainerListener
import javax.swing.JTabbedPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * A composable wrapper for `JTabbedPane` with declarative, dynamic tabs.
 *
 * A pane holds each child as the page of a tab rather than as an indexed child of a layout, so every child
 * of [content] declares the tab it is the body of on its own modifier, with `SwingModifier.tab(...)` - the
 * one region a pane offers (see [TabbedPaneScope]). A child declaring no tab is refused as it arrives,
 * naming the pane and that call. A pane shows one tab per child and any number of them, so two children
 * declaring a tab are two tabs.
 *
 * Tabs are **dynamic**: emitting or dropping a child adds or removes the tab it is the body of, and a tab's
 * title/icon/tooltip/enabled update on recomposition. The selected tab is controlled via [selectedIndex],
 * and [onSelectedIndexChange] reports the tab the user selects. A tab may also carry a `header` composable
 * that renders the tab in the strip in place of its title and icon.
 *
 * [selectedIndex] is `-1` for no selected tab, the value a `JTabbedPane` itself holds while none is
 * selected. Any other value has to name a tab that is there: where it names none - dropping the selected
 * tab without moving the index leaves it naming none - the pane falls back on a neighbor and reports that
 * tab through [onSelectedIndexChange], since it is the tab the pane is on and not the one declared.
 *
 * ```
 * TabbedPane(selectedIndex = sel, onSelectedIndexChange = { sel = it }) {
 *     Column(SwingModifier.tab("General")) { GeneralSettings() }
 *     Column(SwingModifier.tab("Advanced", enabled = false)) { AdvancedSettings() }
 *     Column(SwingModifier.tab("Console", header = { Label("Console") })) { Console() }
 * }
 * ```
 *
 * @param selectedIndex the index of the selected tab, or `-1` for none (controlled)
 * @param modifier the [SwingModifier] applied to the underlying `JTabbedPane`
 * @param onSelectedIndexChange callback invoked with the index of the tab the user selects, and with the
 *   tab the pane is left on where [selectedIndex] names no tab of the strip
 * @param tabPlacement where the tab strip is drawn
 * @param tabLayoutPolicy how the tab strip handles overflow
 * @param content the composable content of the pane, one child per tab; see [TabbedPaneScope]
 * @see javax.swing.JTabbedPane
 */
@Composable
public fun TabbedPane(
    selectedIndex: Int,
    modifier: SwingModifier = SwingModifier,
    onSelectedIndexChange: (Int) -> Unit = {},
    @TabPlacement tabPlacement: Int = JTabbedPane.TOP,
    @TabLayoutPolicy tabLayoutPolicy: Int = JTabbedPane.WRAP_TAB_LAYOUT,
    content: @Composable TabbedPaneScope.() -> Unit,
) {
    TabbedPane(
        selectedIndex = selectedIndex,
        changeListener = { event -> onSelectedIndexChange((event.source as JTabbedPane).selectedIndex) },
        modifier = modifier,
        tabPlacement = tabPlacement,
        tabLayoutPolicy = tabLayoutPolicy,
        content = content,
    )
}

/**
 * A [TabbedPane] driven by a raw [ChangeListener] instead of an `onSelectedIndexChange` lambda. The
 * listener is notified of the tab the user selects, and of the tab the pane is left on where
 * [selectedIndex] names no tab of the strip, reading the new selection off the event's source pane; the
 * latest declared instance is the one notified, so the listener may be declared inline.
 *
 * @param selectedIndex the index of the selected tab, or `-1` for none (controlled)
 * @param changeListener the listener notified when the user selects another tab, and when the pane is
 *   left on a tab [selectedIndex] does not name
 * @param modifier the [SwingModifier] applied to the underlying `JTabbedPane`
 * @param tabPlacement where the tab strip is drawn
 * @param tabLayoutPolicy how the tab strip handles overflow
 * @param content the composable content of the pane, one child per tab; see [TabbedPaneScope]
 * @see javax.swing.JTabbedPane
 */
@Composable
public fun TabbedPane(
    selectedIndex: Int,
    changeListener: ChangeListener,
    modifier: SwingModifier = SwingModifier,
    @TabPlacement tabPlacement: Int = JTabbedPane.TOP,
    @TabLayoutPolicy tabLayoutPolicy: Int = JTabbedPane.WRAP_TAB_LAYOUT,
    content: @Composable TabbedPaneScope.() -> Unit,
) {
    TabbedPaneImpl(
        selectedIndex = selectedIndex,
        modifier = modifier,
        tabPlacement = tabPlacement,
        tabLayoutPolicy = tabLayoutPolicy,
        changeListener = changeListener,
        content = content,
    )
}

@Composable
private fun TabbedPaneImpl(
    selectedIndex: Int,
    modifier: SwingModifier,
    @TabPlacement tabPlacement: Int,
    @TabLayoutPolicy tabLayoutPolicy: Int,
    changeListener: ChangeListener,
    content: @Composable TabbedPaneScope.() -> Unit,
) {
    // Rejected against the declaration rather than against the strip: an index no pane could ever be on
    // is the caller's mistake to hear about on the pass that makes it, not once a tab happens to arrive.
    require(selectedIndex >= NO_TAB) {
        "TabbedPane selectedIndex must be $NO_TAB for no selected tab or a non-negative tab index, " +
            "but was $selectedIndex"
    }
    // A JTabbedPane moves its selection on its own whenever the strip changes: the first tab to arrive
    // becomes the selection, and removing the selected tab falls back to a neighbor. Each of those moves
    // fires the very change event a click fires, so the writes that add and remove tabs run as this
    // wrapper's own, and the mirror is what tells that apart from the user's own selection.
    val applied = rememberAppliedValue(selectedIndex)
    // Reading the mirror here subscribes this composition to the user moving the pane's own selection, so
    // a move away from the declaration invalidates on its own instead of waiting for an unrelated
    // recomposition to notice it.
    val held = applied.value
    // Captured here in the composable body: a header cannot be an applier node of the pane (see
    // TabHeaderIsland in TabbedPaneScope), so this context is threaded to it explicitly instead of being
    // inherited through the node tree.
    val headerParentContext = rememberCompositionContext()
    // Remembered with the pane: a tab's declaration is built against these as that tab's modifier is
    // built, so both outlive the pass that declared it.
    val scope = remember(applied, headerParentContext) { TabbedPaneScopeImpl(applied, headerParentContext) }

    // The tab the caller has been told the pane is on: the one it declared and the pane took, or the one
    // the pane was left on and the callback was handed. Every selection that reaches the caller is
    // recorded here, so a pane holding anything else is holding a selection the caller has not heard of.
    val reportedSelection = remember { intArrayOf(NO_TAB) }
    val onUserSelection: (ChangeEvent) -> Unit = { event ->
        val current = (event.source as JTabbedPane).selectedIndex
        // Only a move of the user's is theirs to be told about, and only one they were told about
        // belongs in the record - a move the pane made under one of this wrapper's own writes is
        // settled below, which is where the record catches up with it.
        if (applied.observed(current)) {
            reportedSelection[0] = current
            changeListener.stateChanged(event)
        }
    }

    // A tab becomes a page of the pane after this node's update block has run: the runtime applies the
    // content that block declared once it returns. A pass declaring both a tab and the selection naming it
    // therefore has no such tab to select while it settles, so counting the pages the pane gains and loses
    // gives that pass a successor to settle on. Read straight off the pane's own tab count rather than off
    // every child event it fires: the look and feel parents its own children - a tab container, the
    // scroll buttons - to the pane too, and none of those is a page moving.
    var pages by remember { mutableIntStateOf(0) }
    val pageListener =
        remember {
            object : ContainerListener {
                private var lastTabCount = -1

                override fun componentAdded(event: ContainerEvent) = trackTabCount(event)

                override fun componentRemoved(event: ContainerEvent) = trackTabCount(event)

                private fun trackTabCount(event: ContainerEvent) {
                    val tabCount = (event.source as JTabbedPane).tabCount
                    if (tabCount != lastTabCount) {
                        lastTabCount = tabCount
                        pages++
                    }
                }
            }
        }

    SwingNode(
        factory = { JTabbedPane() },
        update = {
            set(tabPlacement) { this.tabPlacement = it }
            set(tabLayoutPolicy) { this.tabLayoutPolicy = it }
            applyModifier(modifier.changeListener(onUserSelection).containerListener(pageListener))

            // The declaration, the selection the pane is really on, and the strip itself move
            // independently, and one settle answers for all three: they are one key. The key skips the first
            // pass, which declares the pane while its strip is still empty: settling there would hand the
            // caller the empty pane's own answer as though its declaration had been refused.
            update(Triple(selectedIndex, held, pages)) {
                settleSelection(this, selectedIndex, applied, reportedSelection, changeListener)
            }
        },
        // A pane holds every child as the page of a tab, through `insertTab` rather than by index, and
        // holds as many of them as the content declares.
        childPlacement = ChildPlacement.OrderedSlots(TAB_SLOT_NAME),
        content = { scope.content() },
    )
}

/** The index a `JTabbedPane` reports when no tab of it is selected. */
private const val NO_TAB = -1

/**
 * Puts [pane] on the tab [selectedIndex] names, and tells [listener] which tab the pane is on instead
 * when that index names no tab of the strip.
 *
 * A declaration the pane can honor is asserted as one of [applied]'s own writes, so the caller does not
 * hear its own declaration back as an interaction. A declaration the pane cannot honor - an index past
 * the strip, which is what dropping the declared tab leaves behind - is a selection the caller believes
 * stands while the pane sits on the neighbor it fell back on. That tab is nothing the composition asked
 * for, so the caller is handed it rather than left with a selection the strip lost. This settles again
 * whenever the declaration, the mirror or the page count moves, so updating [reported] is what keeps a
 * standing fallback from being reported again on every repeat settle - a declaration the pane can honor
 * already updates it to the pane's post-write value, so only this separate, deliberately-lagging record
 * catches the fallback case.
 *
 * The fallback reaches [listener] directly rather than from inside a write, so it runs contained the same
 * way: a throw out of it is reported rather than left to end the composition applying this pass.
 */
private fun settleSelection(
    pane: JTabbedPane,
    selectedIndex: Int,
    applied: AppliedValue<Int>,
    reported: IntArray,
    listener: ChangeListener,
) {
    if (selectedIndex == NO_TAB || selectedIndex in 0 until pane.tabCount) {
        if (pane.selectedIndex != selectedIndex) applied.write { pane.selectedIndex = selectedIndex }
        reported[0] = pane.selectedIndex
        return
    }
    if (pane.selectedIndex == reported[0]) return
    reported[0] = pane.selectedIndex
    dispatchToCaller { listener.stateChanged(ChangeEvent(pane)) }
}
