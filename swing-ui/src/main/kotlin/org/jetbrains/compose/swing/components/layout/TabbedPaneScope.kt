package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.wrongSlotHost
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * The receiver of a [TabbedPane]'s content, through which a child declares the tab it is the page of.
 *
 * A pane offers one region, the tab, and [tab] is the call that fills it. Every child of a pane is one
 * tab's body and names that region on its own `modifier`; a child that names none is refused as it
 * arrives, since a pane reaches its pages through `insertTab` and has no indexed children to add it to.
 * The region holds as many children as the content declares, each its own tab, in the order they are
 * emitted:
 *
 * ```
 * TabbedPane(selectedIndex = sel, onSelectedIndexChange = { sel = it }) {
 *     Column(SwingModifier.tab("General")) { GeneralSettings() }
 *     Column(SwingModifier.tab("Advanced", enabled = false)) { AdvancedSettings() }
 * }
 * ```
 *
 * @see javax.swing.JTabbedPane
 */
public sealed interface TabbedPaneScope {
    /**
     * Makes the child the body of a tab titled [title], at the position it is emitted in.
     *
     * A [header] takes over what the tab strip renders for this tab. [title] and [icon] keep their
     * meaning either way: they name the tab for accessibility and remain the values recomposition
     * writes, so a tab that renders only a header is still named.
     *
     * [mnemonic] selects the tab from the keyboard together with the look and feel's mouseless modifier,
     * and the tab underlines the first character of [title] that key names. [displayedMnemonicIndex]
     * takes that choice over where another character is to carry the underline; give each tab of one pane
     * a mnemonic of its own, since the pane picks among tabs sharing one arbitrarily.
     *
     * The tab keeps what its body remembers, and the components that body is realized as, for as long as
     * the child holding this declaration keeps its composition identity - which is the position it is
     * emitted in unless [androidx.compose.runtime.key] gives it one of its own. A child emitted ahead of
     * an unkeyed body therefore hands it the tab already standing in that position.
     *
     * @param title the tab's title
     * @param icon the tab's icon, or `null` for none
     * @param tooltip the tab's tooltip, or `null` for none
     * @param enabled whether the tab can be selected
     * @param mnemonic the key code selecting this tab, as a `java.awt.event.KeyEvent` `VK_` constant, or
     *   `-1` for none
     * @param displayedMnemonicIndex the index into [title] of the character the tab underlines, or `null`
     *   to underline the one [mnemonic] names; `-1`, or an index [title] has since outgrown, underlines
     *   none
     * @param background the color the tab itself is drawn in, or `null` for the pane's own
     * @param foreground the color the tab's title is drawn in, or `null` for the pane's own
     * @param header the composable rendered in the tab strip in place of [title] and [icon], or `null`
     *   to let the tab strip render them itself
     * @see javax.swing.JTabbedPane.insertTab
     */
    @Suppress("LongParameterList")
    // One parameter per independent declarative aspect of a tab, all but title optional and named at the
    // call site.
    public fun SwingModifier.tab(
        title: @Nls String,
        icon: Icon? = null,
        tooltip: @Nls String? = null,
        enabled: Boolean = true,
        mnemonic: Int = NO_MNEMONIC,
        displayedMnemonicIndex: Int? = null,
        background: Color? = null,
        foreground: Color? = null,
        header: (@Composable () -> Unit)? = null,
    ): SwingModifier
}

/**
 * The [TabbedPaneScope] one [TabbedPane] hands its content, holding what every tab of that pane is
 * declared against: the mirror [mirror] the pane's selection is settled through, since joining and
 * leaving the strip both change it, and the composition [headerContext] a declared header renders as a
 * child of. It is remembered alongside the pane, so both outlive the pass that declared a tab.
 */
internal class TabbedPaneScopeImpl(
    private val mirror: MirrorState<Int>,
    private val headerContext: CompositionContext,
) : TabbedPaneScope {
    @Suppress("LongParameterList")
    // One parameter per independent declarative aspect of a tab, as [TabbedPaneScope.tab] declares them.
    override fun SwingModifier.tab(
        title: @Nls String,
        icon: Icon?,
        tooltip: @Nls String?,
        enabled: Boolean,
        mnemonic: Int,
        displayedMnemonicIndex: Int?,
        background: Color?,
        foreground: Color?,
        header: (@Composable () -> Unit)?,
    ): SwingModifier {
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
        // The slot creates the tab and takes it away again; the element carries every later declaration to
        // the tab the slot created, and writes nothing for a tab redeclared unchanged.
        return this
            .slot(TAB_SLOT_NAME, TabAttachment(metadata, header, headerContext, mirror))
            .then(TabElement(metadata, header, headerContext))
    }
}

/**
 * The region of a [TabbedPane] a child fills, written as the call that fills it. It is both what the pane
 * declares as its children's placement and what every child names, so an error about a misplaced child
 * prints the very call a caller writes.
 */
internal const val TAB_SLOT_NAME: String = "SwingModifier.tab(title)"

/** The key code a `JTabbedPane` tab carries while no key selects it. */
private const val NO_MNEMONIC: Int = -1

/** The displayed-mnemonic index a `JTabbedPane` tab carries while none of its title is underlined. */
private const val NO_INDEX: Int = -1

/**
 * What the strip renders for one tab and whether that tab selects, as one composition declared it.
 *
 * Every aspect is written whenever any of them changes, in the one order that leaves each of them holding
 * what it declares: a pane recomputes the character a tab underlines from the tab's title on both
 * `setTitleAt` and `setMnemonicAt`, so the mnemonic is written after the title and an explicit
 * [displayedMnemonicIndex] after the mnemonic. Withdrawing that explicit index is what hands the underline
 * back to the character the mnemonic itself names.
 */
@Suppress("LongParameterList")
// One field per aspect [TabbedPaneScope.tab] declares, in the same one-to-one correspondence: grouping
// any of them here would introduce a shape the declaration itself does not have.
private data class TabMetadata(
    val title: @Nls String,
    val icon: Icon?,
    val tooltip: @Nls String?,
    val enabled: Boolean,
    val mnemonic: Int,
    val displayedMnemonicIndex: Int?,
    val background: Color?,
    val foreground: Color?,
) {
    /** Writes this declaration onto the tab [pane] holds at [index]. */
    fun applyTo(
        pane: JTabbedPane,
        index: Int,
    ) {
        pane.setTitleAt(index, title)
        pane.setIconAt(index, icon)
        pane.setToolTipTextAt(index, tooltip)
        pane.setEnabledAt(index, enabled)
        pane.setMnemonicAt(index, mnemonic)
        // A `JTabbedPane` refuses any index the title has outgrown, so a shrunk title falls back to none
        // underlined instead of throwing out of this update pass.
        if (displayedMnemonicIndex != null) {
            val bounded = if (displayedMnemonicIndex in title.indices) displayedMnemonicIndex else NO_INDEX
            pane.setDisplayedMnemonicIndexAt(index, bounded)
        }
        pane.setBackgroundAt(index, background)
        pane.setForegroundAt(index, foreground)
    }
}

/**
 * Hosts one child as a page of the pane, through `insertTab` at the composition index the applier hands
 * over, and takes it out again by component identity - `remove(component)`, which the pane resolves to the
 * tab's current position - so removing an earlier tab first never invalidates a later tab's removal.
 *
 * The tab is created here carrying everything the declaration names, since this is the first moment it
 * exists (see [TabElement]). A declared [header] is rendered by a composition installed as the tab's own
 * component, for the same reason.
 *
 * Joining and leaving the strip both change a pane's selection - the first tab to arrive becomes the
 * selection, and removing the selected tab falls back on a neighbor - so each runs as one of [mirror]'s
 * own writes.
 *
 * A fresh instance is built for every pass, so the identity comparison
 * [org.jetbrains.compose.swing.modifier.layout.SlotElement] makes on the attachment never holds even
 * where nothing about the tab changed - unlike the corner and header slots, which hand out one
 * attachment per slot. The cost is a chain re-diff on every pass, not a wrong result: install only ever
 * runs once, when the slot first attaches.
 */
private class TabAttachment(
    val metadata: TabMetadata,
    val header: (@Composable () -> Unit)?,
    val headerContext: CompositionContext,
    val mirror: MirrorState<Int>,
) : SlotAttachment {
    override fun install(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit {
        val pane = tabHost(host)
        mirror.write {
            pane.insertTab(metadata.title, metadata.icon, component, metadata.tooltip, index)
            metadata.applyTo(pane, pane.indexOfComponent(component))
        }
        if (header != null) pane.renderTab(pane.indexOfComponent(component), header, headerContext)
        return {
            pane.releaseHeaderOf(component)
            mirror.write { pane.remove(component) }
        }
    }
}

/**
 * Carries one tab's declaration to the tab that child is already the page of, so a recomposition changing
 * what the strip renders for it reaches the tab: a tab redeclared unchanged is not written again, and any
 * change addresses the tab by its live position and so stays correct however the tabs around it have
 * shifted.
 *
 * The pass that first declares a tab has none to write to - a node's update changes run between the
 * applier's top-down and bottom-up passes, and the bottom-up pass is what attaches the page - so it writes
 * nothing and [TabAttachment] creates the tab with that first declaration instead. Every later pass runs
 * with the tab on the strip.
 *
 * Not a data class: the [header] the node renders in a composition of its own and the [headerContext] that
 * composition joins are registered instances, compared by identity.
 */
private class TabElement(
    val metadata: TabMetadata,
    val header: (@Composable () -> Unit)?,
    val headerContext: CompositionContext,
) : SwingModifier.NodeElement<Component, TabNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): TabNode = TabNode()

    override fun update(node: TabNode): Unit = node.declare(metadata, header, headerContext)

    override fun equals(other: Any?): Boolean =
        other is TabElement &&
            metadata == other.metadata &&
            header === other.header &&
            headerContext === other.headerContext

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + System.identityHashCode(header)
        result = 31 * result + System.identityHashCode(headerContext)
        return result
    }
}

/**
 * The tab a component is the page of for as long as its chain declares one. It holds the composition a
 * declared header renders in, so withdrawing the declaration takes that composition back off the strip and
 * the tab is rendered by the pane itself again.
 */
private class TabNode : SwingModifier.Node<Component>() {
    private var rendering: TabHeaderComposition? = null

    /** Writes [metadata] and the [header] declaration onto this component's tab, where it has one. */
    fun declare(
        metadata: TabMetadata,
        header: (@Composable () -> Unit)?,
        headerContext: CompositionContext,
    ) {
        val pane = component.parent as? JTabbedPane ?: return
        val index = pane.indexOfComponent(component)
        if (index < 0) return
        metadata.applyTo(pane, index)
        declareHeader(pane, index, header, headerContext)
    }

    /**
     * Renders the tab at [index] with [header], reusing the composition already rendering it - which is
     * what re-renders a changed header in place - and taking that composition off the strip where the
     * declaration names no header at all.
     */
    private fun declareHeader(
        pane: JTabbedPane,
        index: Int,
        header: (@Composable () -> Unit)?,
        headerContext: CompositionContext,
    ) {
        val rendered = pane.tabCompositionAt(index)
        if (header == null) {
            rendered?.let {
                it.dispose()
                pane.setTabComponentAt(index, null)
            }
            rendering = null
            return
        }
        rendering = rendered?.also { it.render(header) } ?: pane.renderTab(index, header, headerContext)
    }

    override fun onDetach() {
        val pane = component.parent as? JTabbedPane
        val index = pane?.indexOfComponent(component) ?: -1
        val rendered = if (index >= 0) pane?.tabCompositionAt(index) else null
        // The composition the pane still renders where the tab is standing, and otherwise the one this node
        // put there: a tab that has already left the strip took its own tab component with it, and the
        // composition rendering it is disposed here rather than left composing.
        (rendered ?: rendering)?.dispose()
        if (rendered != null) pane?.setTabComponentAt(index, null)
        rendering = null
    }
}

/** Renders the tab at [index] with [header], in place of the title and icon the strip would draw. */
private fun JTabbedPane.renderTab(
    index: Int,
    header: @Composable () -> Unit,
    headerContext: CompositionContext,
): TabHeaderComposition = TabHeaderComposition(headerContext, header).also { setTabComponentAt(index, it) }

/** The composition rendering the tab at [index], or `null` where the pane renders that tab itself. */
private fun JTabbedPane.tabCompositionAt(index: Int): TabHeaderComposition? =
    getTabComponentAt(index) as? TabHeaderComposition

/**
 * Disposes the composition rendering the tab [component] is the page of, where that tab renders through
 * one. A tab releases its tab component along with its page, so this runs before the page is taken out.
 */
private fun JTabbedPane.releaseHeaderOf(component: Component) {
    val index = indexOfComponent(component)
    if (index >= 0) tabCompositionAt(index)?.dispose()
}

/**
 * The pane a tab is a page of, taken from the container the declaring child is held by.
 *
 * A tab belongs to the pane whose content declares it, so a child that reaches [TabbedPaneScope.tab] from
 * further down - a container of its own stands between it and the pane - names a tab of something that has
 * no tabs, and is stopped here rather than left half-installed.
 */
private fun tabHost(host: Container): JTabbedPane =
    host as? JTabbedPane ?: error(wrongSlotHost(host, JTabbedPane::class.java, TAB_SLOT_NAME))

/**
 * The composition one tab's header renders in: a gapless leading flow, so the strip shows exactly what the
 * header composes at its own preferred size and the look and feel's tab insets are the only padding around
 * it. The panel is the tab's own component and never a page of the pane, which is what `setTabComponentAt`
 * rejects.
 *
 * The header composes as a child composition of [parentContext] - deliberately NOT as a node of the pane's
 * applier: the pane's child order is what assigns tab indices, so a second node per tab would misplace
 * every later tab. Parenting it to the composition enclosing the pane keeps the header's state and
 * [androidx.compose.runtime.CompositionLocal]s in scope, and the header it renders is composition state of
 * its own, so a redeclared header re-renders in place instead of remounting.
 */
private class TabHeaderComposition(
    parentContext: CompositionContext,
    header: @Composable () -> Unit,
) : JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) {
    private val declared = mutableStateOf(header)
    private var mounted: DisposableHandle? = null

    init {
        isOpaque = false
        mounted = setContentAsInteropHost(parentContext) { declared.value() }
    }

    /** Renders [header] in place of the one this composition holds. */
    fun render(header: @Composable () -> Unit) {
        declared.value = header
    }

    /** Disposes this composition. Calling it again is a no-op. */
    fun dispose() {
        mounted?.dispose()
        mounted = null
    }
}
