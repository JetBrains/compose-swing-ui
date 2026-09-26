package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.core.SwingContentComposition
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.core.disposingOnFailure
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.composed
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.RootSlotPolicy
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.checkRootShowsOneChild
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.Color
import java.awt.Component
import java.awt.Container
import javax.swing.Icon
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
@LayoutScopeMarker
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
     * @return this chain with the tab declared on it.
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
 * The [TabbedPaneScope] one [TabbedPane] hands its content, holding the mirror [mirror] the pane's
 * selection is settled through. It is remembered alongside the pane, so a tab's declaration keeps using
 * the same mirror through every pass that declares it.
 */
internal class TabbedPaneScopeImpl(
    private val mirror: MirrorState<Int>,
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
        // Standard tabs stay ordinary modifier metadata. Only a declared header needs a composed modifier
        // and the nested composition it creates.
        return if (header == null) {
            tab(metadata)
        } else {
            tabWithHeader(metadata, header)
        }
    }

    /** Adds the page's tab slot and the node that keeps its metadata current. */
    private fun SwingModifier.tab(
        metadata: TabMetadata,
        header: TabHeaderBinding? = null,
    ): SwingModifier =
        this
            .slot(TabbedPaneParentProtocol, TAB_SLOT_NAME, TabAttachment(metadata, header, mirror))
            .then(TabElement(metadata, header))

    /**
     * Builds the header beside the page's own modifier nodes, so the header composition belongs to the
     * page's composition identity rather than to a synthetic tab body.
     */
    private fun SwingModifier.tabWithHeader(
        metadata: TabMetadata,
        header: @Composable () -> Unit,
    ): SwingModifier =
        composed {
            val parentContext = rememberCompositionContext()
            val currentHeader = rememberUpdatedState(header)
            val binding = remember(parentContext) { TabHeaderBinding() }
            DisposableEffect(binding, parentContext) {
                val composition =
                    DirectTabHeaderComposition(
                        parent = parentContext,
                        content = { currentHeader.value() },
                        onApplied = binding::show,
                        onRemoved = binding::clearIfCurrent,
                    )
                onDispose {
                    binding.dispose()
                    composition.dispose()
                }
            }
            tab(metadata, binding)
        }
}

/**
 * The region of a [TabbedPane] a child fills, written as the call that fills it. It is both what the pane
 * declares as its children's placement and what every child names, so an error about a misplaced child
 * prints the very call a caller writes.
 */
internal const val TAB_SLOT_NAME: String = "SwingModifier.tab(title)"

private val TabbedPaneParentProtocol = parentProtocolOf("JTabbedPane slot") { it is JTabbedPane }

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
 * A [header] has a composition of its own, owned by the composed modifier that declared it. This attachment
 * only lets that composition find the page's live tab; it neither creates nor disposes the composition.
 */
private class TabAttachment(
    val metadata: TabMetadata,
    val header: TabHeaderBinding?,
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
        header?.attachTo(pane, component)
        return { mirror.write { pane.remove(component) } }
    }
}

/**
 * Carries one tab's declaration to the tab that child is already the page of, so a recomposition changing
 * its metadata reaches the tab at its live position.
 */
private class TabElement(
    val metadata: TabMetadata,
    val header: TabHeaderBinding?,
) : SwingModifier.NodeElement<Component, TabNode>() {
    override val name: String get() = "tab"

    override val declaredValues: Map<String, Any?> get() = mapOf("tab" to metadata)
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): TabNode = TabNode()

    override fun update(node: TabNode): Unit = node.declare(metadata, header)

    override fun equals(other: Any?): Boolean =
        other is TabElement && metadata == other.metadata && header === other.header

    override fun hashCode(): Int = 31 * metadata.hashCode() + System.identityHashCode(header)
}

/** The tab a component is the page of for as long as its chain declares one. */
private class TabNode : SwingModifier.ComponentNode<Component>() {
    /** Writes [metadata] and associates [header] with this component's live tab, where it has one. */
    fun declare(
        metadata: TabMetadata,
        header: TabHeaderBinding?,
    ) {
        val pane = component.parent as? JTabbedPane ?: return
        val index = pane.indexOfComponent(component)
        if (index < 0) return
        metadata.applyTo(pane, index)
        header?.attachTo(pane, component)
    }
}

/**
 * Couples a direct header root to the page tab it renders for. The binding retains no composition lifecycle:
 * its effect owns that. It only re-installs the current root whenever the page reaches a live tab or the
 * nested composition applies a new root.
 */
private class TabHeaderBinding {
    private var pane: JTabbedPane? = null
    private var page: Component? = null
    private var root: Component? = null

    fun attachTo(
        pane: JTabbedPane,
        page: Component,
    ) {
        this.pane = pane
        this.page = page
        installRoot()
    }

    fun show(root: Component) {
        this.root = root
        installRoot()
    }

    fun clearIfCurrent(root: Component) {
        if (this.root === root) {
            this.root = null
            disposeTabComponent(root)
        }
    }

    fun dispose() {
        val root = root
        if (root != null) {
            disposeTabComponent(root)
        }
        pane = null
        page = null
        this.root = null
    }

    private fun disposeTabComponent(root: Component) {
        val pane = pane ?: return
        val page = page ?: return
        val index = pane.indexOfComponent(page)
        if (index >= 0 && pane.getTabComponentAt(index) === root) {
            pane.setTabComponentAt(index, null)
        }
    }

    private fun installRoot() {
        val pane = pane
        val page = page
        val root = root
        if (pane != null && page != null && root != null) {
            val index = pane.indexOfComponent(page)
            if (index >= 0 && pane.getTabComponentAt(index) !== root) {
                pane.setTabComponentAt(index, root)
            }
        }
    }
}

/**
 * A nested header composition that exposes exactly one root instead of wrapping it in a generated panel.
 * The root-slot check runs after every root-changing apply, including one caused by snapshot invalidation
 * inside [content], when the runtime has finished deactivating a replaced root.
 */
private class DirectTabHeaderComposition(
    parent: CompositionContext,
    private val content: @Composable () -> Unit,
    private val onApplied: (Component) -> Unit,
    private val onRemoved: (Component) -> Unit,
) : DisposableHandle {
    private var current: Component? = null
    private var disposed = false
    private val rootHolder = SwingNodeHolder(Container())
    private val rootSlot =
        SlotAttachment { _, component, _ ->
            current = component
            onApplied(component)
            return@SlotAttachment {
                component.parent?.remove(component)
                if (current === component) {
                    current = null
                    onRemoved(component)
                }
            }
        }
    private val composition: SwingContentComposition

    init {
        checkEventDispatchThread()
        composition =
            SwingContentComposition.nested(parent) { owner ->
                SwingApplier(
                    rootHolder.attachedTo(owner),
                    rootSlot = rootSlot,
                    rootSlotPolicy =
                        RootSlotPolicy {
                            if (!disposed) requireRoot()
                        },
                )
            }
        disposingOnFailure(::disposeComposition) {
            composition.setContent(content)
            requireRoot()
        }
    }

    override fun dispose() {
        checkEventDispatchThread()
        disposeComposition()
    }

    private fun disposeComposition() {
        disposed = true
        composition.dispose()
    }

    private fun requireRoot(): Component {
        try {
            rootHolder.checkRootShowsOneChild()
        } catch (overflow: IllegalStateException) {
            throw IllegalStateException(
                "A custom tab header must emit exactly one direct Component root. " +
                    "Emit one component, wrapping several in a container of your own. " +
                    overflow.message.orEmpty(),
                overflow,
            )
        }
        return checkNotNull(current) {
            "A custom tab header must emit exactly one direct Component root, but emitted none."
        }
    }
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
