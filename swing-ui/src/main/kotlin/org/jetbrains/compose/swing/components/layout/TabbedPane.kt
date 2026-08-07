@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.TabLayoutPolicy
import org.jetbrains.compose.swing.constants.TabPlacement
import org.jetbrains.compose.swing.core.dispatchToCaller
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.LocalSlotAttachment
import org.jetbrains.compose.swing.node.LocalSwingConstraint
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SlotNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * A composable wrapper for `JTabbedPane` with declarative, dynamic tabs.
 *
 * Declare tabs in [block]; each `tab(...)` becomes a titled tab hosting its body composable. Tabs are
 * **dynamic**: adding or removing a `tab(...)` in the composition adds or removes the matching tab, and
 * a tab's title/icon/tooltip/enabled update on recomposition. The selected tab is controlled via
 * [selectedIndex], and [onSelectedIndexChange] reports the tab the user selects. A tab may also carry a
 * `header` composable that renders the tab in the strip in place of its title and icon.
 *
 * [selectedIndex] is `-1` for no selected tab, the value a `JTabbedPane` itself holds while none is
 * selected. Any other value has to name a tab that is there: where it names none - dropping the selected
 * tab without moving the index leaves it naming none - the pane falls back on a neighbor and reports that
 * tab through [onSelectedIndexChange], since it is the tab the pane is on and not the one declared.
 *
 * ```
 * TabbedPane(selectedIndex = sel, onSelectedIndexChange = { sel = it }) {
 *     tab("General") { GeneralPanel() }
 *     tab("Advanced", enabled = false) { AdvancedPanel() }
 *     tab("Console", header = { Label("Console") }) { ConsolePanel() }
 * }
 * ```
 *
 * @param selectedIndex the index of the selected tab, or `-1` for none (controlled)
 * @param modifier the [SwingModifier] applied to the underlying `JTabbedPane`
 * @param onSelectedIndexChange callback invoked with the index of the tab the user selects, and with the
 *   tab the pane is left on where [selectedIndex] names no tab of the strip
 * @param tabPlacement where the tab strip is drawn
 * @param tabLayoutPolicy how the tab strip handles overflow
 * @param block declares the tabs; see [TabbedPaneScope]
 * @see javax.swing.JTabbedPane
 */
@Composable
public fun TabbedPane(
    selectedIndex: Int,
    modifier: SwingModifier = SwingModifier,
    onSelectedIndexChange: (Int) -> Unit = {},
    @TabPlacement tabPlacement: Int = JTabbedPane.TOP,
    @TabLayoutPolicy tabLayoutPolicy: Int = JTabbedPane.WRAP_TAB_LAYOUT,
    block: TabbedPaneScope.() -> Unit,
) {
    val callback = rememberUpdatedState(onSelectedIndexChange)
    val listener = remember { ChangeListener { event -> callback.value((event.source as JTabbedPane).selectedIndex) } }
    TabbedPane(
        selectedIndex = selectedIndex,
        changeListener = listener,
        modifier = modifier,
        tabPlacement = tabPlacement,
        tabLayoutPolicy = tabLayoutPolicy,
        block = block,
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
 * @param block declares the tabs; see [TabbedPaneScope]
 * @see javax.swing.JTabbedPane
 */
@Composable
public fun TabbedPane(
    selectedIndex: Int,
    changeListener: ChangeListener,
    modifier: SwingModifier = SwingModifier,
    @TabPlacement tabPlacement: Int = JTabbedPane.TOP,
    @TabLayoutPolicy tabLayoutPolicy: Int = JTabbedPane.WRAP_TAB_LAYOUT,
    block: TabbedPaneScope.() -> Unit,
) {
    TabbedPaneImpl(
        selectedIndex = selectedIndex,
        modifier = modifier,
        tabPlacement = tabPlacement,
        tabLayoutPolicy = tabLayoutPolicy,
        changeListener = changeListener,
        block = block,
    )
}

@Composable
private fun TabbedPaneImpl(
    selectedIndex: Int,
    modifier: SwingModifier,
    @TabPlacement tabPlacement: Int,
    @TabLayoutPolicy tabLayoutPolicy: Int,
    changeListener: ChangeListener,
    block: TabbedPaneScope.() -> Unit,
) {
    // Collected fresh on every pass, so a tab the caller stops declaring loses its page (see SwingNode).
    val scope = TabbedPaneScopeImpl().apply(block)
    // The pane the node holds, taken from the node on every pass (see SwingNode): tab metadata, headers
    // and the selection are all written from outside the pane's own node.
    val livePane = remember { arrayOfNulls<JTabbedPane>(1) }
    // Captured here in the composable body, so every tab header composes as a CHILD of the composition
    // enclosing this pane and sees the state and CompositionLocals hoisted around it. A header cannot be
    // an applier node of the pane (see TabHeaderHost), so it needs this context threaded to it
    // explicitly rather than inheriting one through the node tree.
    val headerParentContext = rememberCompositionContext()

    // A JTabbedPane moves its selection on its own whenever the strip changes: the first tab to arrive
    // becomes the selection, and removing the selected tab falls back to a neighbor. Each of those moves
    // fires the very change event a click fires, so the writes that add and remove tabs run as this
    // wrapper's own, and the mirror is what tells that apart from the user's own selection.
    val applied = rememberAppliedValue(selectedIndex)
    // The listener is attached once per pane and reaches the declared one through a handle each
    // composition refreshes, so a newly declared listener takes over without detaching and reattaching.
    val declaredListener = rememberUpdatedState(changeListener)
    // The tab the caller has been told the pane is on: the one it declared and the pane took, or the one
    // the pane was left on and the callback was handed. Every selection that reaches the caller is
    // recorded here, so a pane holding anything else is holding a selection the caller has not heard of.
    val reportedSelection = remember { intArrayOf(NO_TAB) }
    val listener =
        remember {
            ChangeListener { event ->
                val current = (event.source as JTabbedPane).selectedIndex
                // Only a move of the user's is theirs to be told about, and only one they were told about
                // belongs in the record - a move the pane made under one of this wrapper's own writes is
                // settled below, which is where the record catches up with it.
                if (applied.observed(current)) {
                    reportedSelection[0] = current
                    declaredListener.value.stateChanged(event)
                }
            }
        }

    SwingNode(
        factory = { JTabbedPane() },
        update = {
            reconcile { livePane[0] = this }
            set(tabPlacement) { this.tabPlacement = it }
            set(tabLayoutPolicy) { this.tabLayoutPolicy = it }
            applyModifier(modifier.changeListener(listener))
        },
        content = {
            scope.tabs.forEachIndexed { index, tab ->
                // Keyed by position, the identity a container's content gives its children (see SwingNode).
                key(index) {
                    Tab(
                        tab = tab,
                        headerParentContext = headerParentContext,
                        applied = applied,
                    )
                }
            }
        },
    )

    // Reading the mirror here subscribes this composition to the user moving the pane's own selection, so
    // a move away from the declaration invalidates on its own instead of waiting for an unrelated
    // recomposition to notice it. The settle effect below still has to run on every composition regardless:
    // an effect ordered ahead of it in the same pass can still move the pane between this read and the
    // moment that effect runs, and only settling unconditionally catches a move landing in that window.
    applied.current

    // Settle the selection once this composition's changes have reached the component tree. The tabs are
    // emitted in `content`, which the runtime applies after the node's update block, so a tab added by
    // the same composition is not yet a page of the pane while that block runs and there would be
    // nothing to select. Running on every composition also re-settles the selection after the strip
    // itself changed.
    SideEffect {
        val pane = livePane[0]
        if (pane != null) settleSelection(pane, selectedIndex, applied, reportedSelection, declaredListener.value)
    }
}

/** The index a `JTabbedPane` reports when no tab of it is selected. */
private const val NO_TAB = -1

/**
 * Puts [pane] on the tab [selectedIndex] names, and tells [listener] which tab the pane is on instead
 * when that index names no tab of the strip.
 *
 * A declaration the pane can honour is asserted as one of [applied]'s own writes, so the caller does not
 * hear its own declaration back as an interaction; [applied] mirrors whatever the pane is left on
 * afterwards, which is what invalidates this composition again the moment the user moves away from the
 * declaration. A declaration the pane cannot honour - an index past the strip, which is what dropping the
 * declared tab leaves behind - is a selection the caller believes stands while the pane sits on the
 * neighbour it fell back on. That tab is nothing the composition asked for, so the caller is handed it
 * rather than left with a selection the strip lost. [reported] carries the tab the caller has last been
 * told about, which is what keeps one fallback from being reported again on every later composition - a
 * declaration the pane can honour already updates [reported] to the pane's post-write value, so only this
 * separate, deliberately-lagging record catches the fallback case.
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
    require(selectedIndex >= NO_TAB) {
        "TabbedPane selectedIndex must be $NO_TAB for no selected tab or a non-negative tab index, " +
            "but was $selectedIndex"
    }
    if (selectedIndex == NO_TAB || selectedIndex in 0 until pane.tabCount) {
        if (pane.selectedIndex != selectedIndex) applied.write { pane.selectedIndex = selectedIndex }
        reported[0] = pane.selectedIndex
        return
    }
    if (pane.selectedIndex == reported[0]) return
    reported[0] = pane.selectedIndex
    dispatchToCaller { listener.stateChanged(ChangeEvent(pane)) }
}

/**
 * Hosts one declared [tab] as a page of the pane: its body panel is installed as the tab's component
 * through the slot attachment, the body composable fills that panel, and the tab's metadata is written
 * onto the strip on recomposition. A declared header is rendered by [TabHeaderHost] as a child
 * composition of [headerParentContext]. Joining and leaving the strip can move the pane's selection, so
 * the attachment performs both as [applied]'s own writes; a tab's metadata writes cannot move the
 * selection and are left plain.
 */
@Composable
private fun Tab(
    tab: TabDeclaration,
    headerParentContext: CompositionContext,
    applied: AppliedValue<Int>,
) {
    val metadata = tab.metadata
    // The attachment captures only the install-time title/icon/tooltip; later edits flow through the
    // body node's update block below, so the remembered attachment never needs to see fresh metadata.
    val attachment = remember { tabAttachment(metadata, applied) }
    // The tab's body host, taken from the body node itself on every pass: a header is installed against
    // this component's live tab position, and re-filling the cell each pass keeps it pointing at the
    // component the node actually holds.
    val bodyHost = remember { arrayOfNulls<Component>(1) }
    SlotNode(attachment) {
        // The body host IS the tab's component. Reading indexOfComponent(this) off the pane gives its
        // live position, so metadata writes stay correct even after earlier tabs are added or removed.
        // Routing metadata through this update block keeps it driven by recomposition rather than a
        // side effect.
        //
        // On the FIRST update the slot is not yet attached (the applier inserts the component after
        // these fixups run), so there is no tab to address and the writes skip - the attachment applied
        // the initial metadata at insertTab time. Every later recomposition runs with the tab attached,
        // so changed values land.
        SwingNode(
            factory = { JPanel(BorderLayout()) },
            update = {
                // Locating the tab is a linear scan of the pane's pages, so it is resolved on demand by
                // the first metadata write of the pass that needs it and shared with the rest: a pass
                // that changes no metadata scans not at all, and one that changes any scans once. The
                // index is the same for every field, since none of these writes moves a tab. The locals
                // are fresh for each pass, so a tab that has shifted meanwhile is addressed where it now
                // is. `pane` is non-null exactly while the body panel is an attached tab, which is the
                // single guard the writes need. The panel's own `parent` IS the pane holding it once
                // attached, so no separate lookup is needed to find it.
                //
                // That guard stays INSIDE each set block rather than around the set calls: set() must
                // visit its slot on every pass to stay positionally aligned, so the calls themselves
                // remain unconditional and only the write is conditional.
                var pane: JTabbedPane? = null
                var at = -1
                var resolved = false
                val resolvePane = { body: Component ->
                    if (!resolved) {
                        resolved = true
                        val owner = body.parent as? JTabbedPane
                        at = owner?.indexOfComponent(body) ?: -1
                        pane = if (at >= 0) owner else null
                    }
                    pane
                }
                reconcile { bodyHost[0] = this }
                set(metadata.title) { resolvePane(this)?.setTitleAt(at, it) }
                set(metadata.icon) { resolvePane(this)?.setIconAt(at, it) }
                set(metadata.tooltip) { resolvePane(this)?.setToolTipTextAt(at, it) }
                set(metadata.enabled) { resolvePane(this)?.setEnabledAt(at, it) }
                // The underline follows the title together with both mnemonic values: setTitleAt itself
                // recomputes the underline from the title, so a title-only change must also re-run this
                // write or the declared index would be silently lost to that recomputation. Setting the
                // mnemonic recomputes the underline from the title, so an explicit index is written after
                // it, and withdrawing that index is what re-runs the mnemonic to get the computed
                // underline back.
                val mnemonicKey = Triple(metadata.title, metadata.mnemonic, metadata.displayedMnemonicIndex)
                set(mnemonicKey) { (_, mnemonic, index) ->
                    val host = resolvePane(this)
                    host?.setMnemonicAt(at, mnemonic)
                    if (index != null) host?.setDisplayedMnemonicIndexAt(at, index)
                }
                set(metadata.background) { resolvePane(this)?.setBackgroundAt(at, it) }
                set(metadata.foreground) { resolvePane(this)?.setForegroundAt(at, it) }
            },
            content = { tab.content() },
        )
    }
    val header = tab.header
    if (header != null) {
        TabHeaderHost(
            bodyHost = bodyHost,
            parentContext = headerParentContext,
            header = header,
        )
    }
}

/**
 * Renders one tab's [header] in the tab strip, in place of the title and icon the strip would draw.
 *
 * The header lives in a panel installed as that tab's component, and [header] is composed into the panel
 * as a child composition of [parentContext] - deliberately NOT as a node of the pane's applier: the
 * pane's child order is what assigns tab indices, so a second node per tab would misplace every later
 * tab. Parenting the child composition to the enclosing one keeps the header's state and
 * [androidx.compose.runtime.CompositionLocal]s in scope, and [header] is read through the latest state so
 * a recomposed header re-renders in place instead of remounting.
 *
 * The install runs from an effect because that is the first point at which the tab exists: the tab is
 * created when the applier attaches [bodyHost]'s component, which happens while the composition's changes
 * are applied, before effects run. Addressing the tab by that component's live position keeps the header
 * on its own tab however the strip has shifted meanwhile.
 */
@Composable
private fun TabHeaderHost(
    bodyHost: Array<Component?>,
    parentContext: CompositionContext,
    header: @Composable () -> Unit,
) {
    val currentHeader = rememberUpdatedState(header)
    // A gapless leading flow, so the strip shows exactly what the header emits at its own preferred size
    // and the look and feel's tab insets are the only padding around it. This panel exists solely to hold
    // the header - it is never a page of the pane, which is what setTabComponentAt rejects.
    val host = remember { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } }

    DisposableEffect(host) {
        val pane = bodyHost[0]?.parent as? JTabbedPane
        val at = pane?.indexOfComponent(bodyHost[0]) ?: -1
        if (at >= 0) pane?.setTabComponentAt(at, host)
        val handle =
            host.setContentAsInteropHost(parentContext) {
                // The header island joins the composition enclosing the pane, so it would otherwise
                // inherit the slot attachment/constraint of whatever hosts the TabbedPane itself (e.g. a
                // ScrollPane viewport, which would try to install the header's node with `host as
                // JScrollPane`). Reset both: the header's nodes are ordinary children of this panel.
                CompositionLocalProvider(
                    LocalSlotAttachment provides null,
                    LocalSwingConstraint provides null,
                ) {
                    currentHeader.value()
                }
            }
        onDispose {
            handle.dispose()
            // A removed tab releases its tab component along with its page, so only a header dropped from
            // a tab that is still there has to be taken back off, restoring the default rendering.
            val index = pane?.indexOfTabComponent(host) ?: -1
            if (index >= 0) pane?.setTabComponentAt(index, null)
        }
    }
}

/** A tab's per-composition appearance snapshot: what the strip renders for it and whether it selects. */
@Suppress("LongParameterList")
// One field per aspect [TabbedPaneScope.tab] declares, in the same one-to-one correspondence: grouping
// any of them here would introduce a shape the declaration itself does not have.
private class TabMetadata(
    val title: @Nls String,
    val icon: Icon?,
    val tooltip: @Nls String?,
    val enabled: Boolean,
    val mnemonic: Int,
    val displayedMnemonicIndex: Int?,
    val background: Color?,
    val foreground: Color?,
)

/**
 * One declared tab: its [metadata] snapshot for this composition, the [header] that renders it in the
 * strip when the caller supplies one, plus its body composable.
 */
private class TabDeclaration(
    val metadata: TabMetadata,
    val header: (@Composable () -> Unit)?,
    val content: @Composable () -> Unit,
)

private class TabbedPaneScopeImpl : TabbedPaneScope {
    val tabs: MutableList<TabDeclaration> = ArrayList()

    override fun tab(
        title: @Nls String,
        icon: Icon?,
        tooltip: @Nls String?,
        enabled: Boolean,
        mnemonic: Int,
        displayedMnemonicIndex: Int?,
        background: Color?,
        foreground: Color?,
        header: (@Composable () -> Unit)?,
        content: @Composable () -> Unit,
    ) {
        val metadata =
            TabMetadata(
                title = title,
                icon = icon,
                tooltip = tooltip,
                enabled = enabled,
                mnemonic = mnemonic,
                displayedMnemonicIndex = displayedMnemonicIndex,
                background = background,
                foreground = foreground,
            )
        tabs.add(TabDeclaration(metadata, header, content))
    }
}

/**
 * Builds a [SlotAttachment] that hosts one tab's body panel in the pane it installs on, via `insertTab`.
 *
 * Install creates the tab at the applier-supplied composition index with its initial title/icon/
 * tooltip/enabled - the slot is physically attached *after* the body node's first `update` runs, so
 * the initial metadata cannot be applied by that update (the tab does not exist yet) and is set here
 * instead. Uninstall detaches by component identity (`remove(component)`, which the pane resolves to
 * the tab's current position), so removing an earlier tab first never invalidates a later tab's
 * uninstall. On every *subsequent* recomposition the body node's `update` block re-applies any changed
 * metadata, addressing the tab by its live `indexOfComponent`.
 *
 * Both the install and the uninstall can move the pane's selection - the first tab to arrive becomes the
 * selection, and removing the selected tab falls back to a neighbour - so each runs as one of [applied]'s
 * own writes.
 */
private fun tabAttachment(
    metadata: TabMetadata,
    applied: AppliedValue<Int>,
): SlotAttachment =
    SlotAttachment { host, component, index ->
        host as JTabbedPane
        applied.write {
            host.insertTab(metadata.title, metadata.icon, component, metadata.tooltip, index)
            val at = host.indexOfComponent(component)
            host.setEnabledAt(at, metadata.enabled)
            host.setMnemonicAt(at, metadata.mnemonic)
            metadata.displayedMnemonicIndex?.let { host.setDisplayedMnemonicIndexAt(at, it) }
            host.setBackgroundAt(at, metadata.background)
            host.setForegroundAt(at, metadata.foreground)
        }
        // Detach by component, not by the install-time index: an earlier sibling may have been
        // removed first, shifting this tab down. JTabbedPane.remove(component) resolves the live
        // position via indexOfComponent and calls removeTabAt, releasing the page entirely.
        return@SlotAttachment { applied.write { host.remove(component) } }
    }
