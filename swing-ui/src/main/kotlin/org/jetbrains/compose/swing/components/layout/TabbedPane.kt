@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.constants.TabLayoutPolicy
import org.jetbrains.compose.swing.constants.TabPlacement
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.ChildPlacement
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
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
 * @param onSelectedIndexChange callback invoked with the index of the tab the user selects, and with the
 *   tab the pane is left on where [selectedIndex] names no tab of the strip
 * @param modifier the [SwingModifier] applied to the underlying `JTabbedPane`
 * @param tabPlacement where the tab strip is drawn
 * @param tabLayoutPolicy how the tab strip handles overflow
 * @param content the composable content of the pane, one child per tab; see [TabbedPaneScope]
 * @see javax.swing.JTabbedPane
 */
@Composable
public fun TabbedPane(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
    @TabPlacement tabPlacement: Int = JTabbedPane.TOP,
    @TabLayoutPolicy tabLayoutPolicy: Int = JTabbedPane.WRAP_TAB_LAYOUT,
    content: @Composable TabbedPaneScope.() -> Unit,
) {
    TabbedPaneImpl(
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

/**
 * The `JTabbedPane` every [TabbedPane] overload renders, with [changeListener] already carrying whichever
 * selection channel the overload driving it uses.
 */
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
    // A JTabbedPane changes its selection on its own whenever the strip changes: the first tab to arrive
    // becomes the selection, and removing the selected tab falls back to a neighbor. Each of those changes
    // fires the very change event a click fires, so the writes that add and remove tabs run as this
    // wrapper's own, and the mirror is what tells that apart from the user's own selection.
    val mirror = rememberMirrorState(selectedIndex)
    // Subscribed here so that a change away from the declaration invalidates on its own instead of waiting
    // for an unrelated recomposition to notice it. What the pane is left on is nothing this body reads
    // from the mirror: the settle below reads the selection off the pane.
    mirror.subscribe()
    // Captured here in the composable body: a header cannot be an applier node of the pane (see
    // TabHeaderComposition in TabbedPaneScope), so this context is threaded to it explicitly instead of
    // being inherited through the node tree.
    val headerParentContext = rememberCompositionContext()
    // Remembered with the pane: a tab's declaration is built against these as that tab's modifier is
    // built, so both outlive the pass that declared it.
    val scope = remember(mirror, headerParentContext) { TabbedPaneScopeImpl(mirror, headerParentContext) }

    // The tab the caller has been told the pane is on: the one it declared and the pane took, or the one
    // the pane was left on and the callback was handed. Every selection that reaches the caller is
    // recorded here, so a pane holding anything else is holding a selection the caller has not heard of.
    val reportedSelection = remember { intArrayOf(NO_TAB) }
    val onUserSelection: JTabbedPane.(ChangeEvent) -> Unit = { event ->
        // Only a change of the user's is theirs to be told about, and only one they were told about
        // belongs in the record - a change the pane made under one of this wrapper's own writes is
        // settled below, which is where the record catches up with it.
        mirror.report(this.selectedIndex) { current ->
            reportedSelection[0] = current
            changeListener.stateChanged(event)
        }
    }

    SwingNode(
        factory = { JTabbedPane() },
        update = {
            applyMirror(mirror)
            set(tabPlacement) { this.tabPlacement = it }
            set(tabLayoutPolicy) { this.tabLayoutPolicy = it }
            applyModifier(modifier.changeListener(JTabbedPane::class, onUserSelection))

            // A tab becomes a page of the pane only once the runtime has applied the content this block
            // declares, so a selection written here would be written against the strip the pass before it
            // left behind. Settled at the end of the change pass instead, which is what has one pass
            // declare a tab and put the pane on it. The same settle runs again on every later pass that
            // changes the strip: a tab arriving is what can turn a standing declaration into one the pane
            // can honor, and a tab leaving is what drops the pane onto a neighbor nobody declared.
            settleWithChildren {
                settleSelection(this, selectedIndex, mirror, reportedSelection, changeListener)
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
 * A declaration the pane can honor is written and read back as one settlement of [mirror]'s, so neither
 * the caller nor the composition hears the wrapper's own write back: the caller does not get its own
 * declaration as an interaction, and the mirror does not schedule a pass to answer a change it just made.
 * A declaration the pane cannot honor - an index past the strip, which is what dropping the declared tab
 * leaves behind - is a selection the caller believes stands while the pane sits on the neighbor it fell
 * back on. That tab is nothing the composition asked for, so the caller is handed it rather than left
 * with a selection the strip lost. This runs on every pass that changes the declaration or the strip, so
 * updating [reported] is what keeps a standing fallback from being reported again on every repeat settle
 * - a declaration the pane can honor already updates it to the pane's post-write value, so only this
 * separate, deliberately-lagging record catches the fallback case.
 *
 * The fallback reaches [listener] directly rather than from inside a write, so it runs contained the same
 * way: a throw out of it is reported rather than left to end the composition applying this pass.
 */
private fun settleSelection(
    pane: JTabbedPane,
    selectedIndex: Int,
    mirror: MirrorState<Int>,
    reported: IntArray,
    listener: ChangeListener,
) {
    if (selectedIndex == NO_TAB || selectedIndex in 0 until pane.tabCount) {
        mirror.settle {
            if (pane.selectedIndex != selectedIndex) mirror.write { pane.selectedIndex = selectedIndex }
            answered(pane.selectedIndex)
        }
        reported[0] = pane.selectedIndex
        return
    }
    if (pane.selectedIndex == reported[0]) return
    reported[0] = pane.selectedIndex
    dispatchToCaller { listener.stateChanged(ChangeEvent(pane)) }
}
