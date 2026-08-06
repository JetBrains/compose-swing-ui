package org.jetbrains.compose.swing.modifier.datatransfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.constants.TransferAction
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import java.awt.Point
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DragSource
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.TransferHandler
import kotlin.math.abs

/*
 * Data-transfer SwingModifiers - drag-and-drop and system-clipboard support. Drag source, drop
 * target, and clipboard copy/cut/paste can all be declared on the same component and coexist. The set
 * of allowed operations is a [TransferAction] `TransferHandler` action bit-mask.
 */

/**
 * Makes the component a drag SOURCE that exports a [Transferable] when dragged.
 *
 * [exportedActions] declares which operations the source offers (a [TransferAction] `TransferHandler`
 * action bit-mask, e.g. `TransferHandler.COPY`, `TransferHandler.COPY_OR_MOVE`); a drop is permitted
 * only for an operation also accepted by the target. [transferable] is invoked when a drag begins on
 * the component and returns the data to transfer, or `null` to start no drag.
 *
 * The outcome of every export - the completed action, including a `MOVE` whose data the source
 * should remove - is reported through [onExportDone].
 *
 * Declaring a source takes over the component's export: [transferable] and [exportedActions] are what
 * it exports and what it offers, in place of whatever it exported on its own. Its import is left
 * alone - without a [dropTarget] or a [clipboard] on the same component, a drop or a paste is handled
 * exactly as the component handles it.
 *
 * Requires a [JComponent] target. Composes with [dropTarget] and [clipboard] on the same component:
 * all three configure one underlying transfer handler, so a component can be a drag source, a drop
 * target, and clipboard-enabled at once. [transferable] and [exportedActions] are read on each drag,
 * so passing fresh values across recompositions takes effect immediately.
 *
 * @see javax.swing.TransferHandler.createTransferable
 */
public fun SwingModifier.draggable(
    @TransferAction exportedActions: Int,
    transferable: (component: JComponent) -> Transferable?,
): SwingModifier = this then DraggableElement(exportedActions, transferable)

/**
 * Makes the component a drop TARGET that imports a dropped [Transferable].
 *
 * [acceptedActions] declares which operations the target accepts; a drop whose operation is not among
 * them is rejected before [onDrop]. [canImport] gates a drop by its offered [DataFlavor]s - it
 * receives the dragged data's flavors and returns whether the drop may proceed; the default accepts
 * any flavor, so gating is opt-in. [onDrop] is invoked on an accepted drop with the dropped
 * [Transferable] and returns whether the import succeeded.
 *
 * Declaring a drop takes over the component's import: [canImport] and [onDrop] decide every drop and
 * paste that reaches it, in place of whatever it imported on its own. Without a [draggable] or a
 * [clipboard] on the same component, the actions it offers as a source stay the ones it offers on its
 * own.
 *
 * Requires a [JComponent] target. Composes with [draggable] and [clipboard] on the same component.
 * [onDrop], [canImport], and [acceptedActions] are read on each drop, so passing fresh values across
 * recompositions takes effect immediately.
 *
 * @see javax.swing.TransferHandler.importData
 */
public fun SwingModifier.dropTarget(
    @TransferAction acceptedActions: Int,
    onDrop: (transferable: Transferable) -> Boolean,
    canImport: (flavors: List<DataFlavor>) -> Boolean = { true },
): SwingModifier = this then DropTargetElement(acceptedActions, onDrop, canImport)

/**
 * Enables system-clipboard copy/cut export and paste import on the component, over the same transfer
 * handler [draggable] and [dropTarget] use.
 *
 * Copy and cut export the value [transferable] produces to the system clipboard; copy completes as a
 * `TransferHandler.COPY` and cut as a `TransferHandler.MOVE`, the action reported through
 * [onExportDone], where a source implementing cut semantics removes the moved data. Paste reads the
 * system clipboard and, when [canImport] accepts the available flavors, hands the clipboard
 * [Transferable] to [onPaste], which returns whether the import succeeded. [canImport] defaults to
 * accepting any flavor.
 *
 * Declaring a clipboard takes over both directions: copy and cut export [transferable] and a paste
 * imports through [onPaste], in place of whatever the component exported and imported on its own.
 *
 * When [bindKeys] is `true` (the default), the platform copy/cut/paste keystrokes (the menu-shortcut
 * modifier with `C`/`X`/`V`) are bound on the component so the standard shortcuts trigger these
 * operations while it is focused; pass `false` to enable the operations without installing key
 * bindings (e.g. to drive them from your own menu). Requires a [JComponent] target. All callbacks are
 * read live, so passing fresh lambdas across recompositions takes effect immediately.
 *
 * Pass a [handle] from [rememberClipboardHandle] to also trigger copy/cut/paste programmatically. The
 * handle binds to the component this modifier is applied to, so [ClipboardHandle.copy],
 * [ClipboardHandle.cut] and [ClipboardHandle.paste] drive these same operations from an event handler
 * (e.g. a menu item) without the caller ever touching the [JComponent].
 *
 * @see javax.swing.TransferHandler.exportToClipboard
 */
public fun SwingModifier.clipboard(
    transferable: (component: JComponent) -> Transferable?,
    onPaste: (transferable: Transferable) -> Boolean,
    canImport: (flavors: List<DataFlavor>) -> Boolean = { true },
    bindKeys: Boolean = true,
    handle: ClipboardHandle? = null,
): SwingModifier =
    (this then ClipboardElement(transferable, onPaste, canImport, bindKeys))
        .binding(JComponent::class.java, handle, ClipboardHandle::bind, ClipboardHandle::unbind)

/**
 * Registers a callback told the outcome of every export a [draggable] or [clipboard] source declared
 * on this component produces: once a drag ends or a clipboard copy/cut completes, [onExportDone]
 * receives the exported data and the [TransferAction] that occurred -
 * `TransferHandler.COPY`, `TransferHandler.MOVE`, or `TransferHandler.NONE` when nothing was
 * transferred, in which case the data is whatever the export offered, or `null` where it produced none.
 * A source offering `MOVE` implements move semantics here: on a reported `MOVE` it removes the moved
 * data. With no callback registered a completed export removes nothing, like
 * `TransferHandler.exportDone` itself.
 *
 * Declaring [onExportDone] alone, with neither a [draggable] nor a [clipboard] source on the same
 * component, has nothing of its own to export through this seam: the component's own copy and cut keep
 * running unclaimed, and [onExportDone] is not invoked for them. A drag it starts is not claimed either,
 * but is not left alone the way copy and cut are: the drag machinery re-reads the component's transfer
 * handler once it recognizes the gesture, finds no declared transferable there, and completes as
 * `TransferHandler.NONE` - a completion [onExportDone] does receive, even though no drag actually ran.
 *
 * Requires a [JComponent] target. Composes with [draggable] and [clipboard] on the same component:
 * the component's data-transfer modifiers configure one underlying transfer handler with a single
 * export-completion seam, so the callback observes every export a declared source on this component
 * produces, regardless of which modifier declared that source. [onExportDone] is read on each export,
 * so passing a fresh value across recompositions takes effect immediately.
 *
 * @see javax.swing.TransferHandler.exportDone
 */
public fun SwingModifier.onExportDone(
    onExportDone: (component: JComponent, data: Transferable?, action: Int) -> Unit,
): SwingModifier = this then ExportDoneElement(onExportDone)

/**
 * A programmatic trigger for the clipboard copy/cut/paste of the component a [clipboard] modifier binds
 * it to. Obtain one from [rememberClipboardHandle], pass it to `clipboard(handle = ...)`, then call
 * [copy]/[cut]/[paste] from an event handler (e.g. a menu item) to drive the same operations the bound
 * keystrokes do - without ever holding the underlying [JComponent].
 *
 * The handle captures the component (and its transfer handler) the modifier binds it to; while unbound -
 * before the modifier applies or after it leaves the chain - [copy] and [cut] are no-ops and [paste]
 * returns `false`.
 */
@Stable
public class ClipboardHandle internal constructor(
    // The clipboard copy/cut/paste act on, resolved on each operation and `null` where the environment
    // has none, which leaves every operation a no-op.
    private val resolveClipboard: () -> Clipboard?,
) {
    // The component the clipboard modifier bound this handle to, or null while unbound. Copy/cut/paste
    // operate on it through the transfer handler that same modifier installed.
    private var component: JComponent? = null

    /**
     * Copies to the system clipboard: exports the value the bound component's [clipboard] modifier
     * produces as a `TransferHandler.COPY`. A no-op while the handle is unbound or no clipboard is
     * reachable; a clipboard that refuses the value completes the export as `TransferHandler.NONE`.
     *
     * @see javax.swing.TransferHandler.exportToClipboard
     */
    public fun copy() {
        export(TransferHandler.COPY)
    }

    /**
     * Cuts to the system clipboard: exports the value the bound component's [clipboard] modifier
     * produces as a `TransferHandler.MOVE`, reported through the component's [onExportDone] so the
     * source can remove the moved data. A no-op while the handle is unbound or no clipboard is
     * reachable; a clipboard that refuses the value reports `TransferHandler.NONE` in place of the move.
     *
     * @see javax.swing.TransferHandler.exportToClipboard
     */
    public fun cut() {
        export(TransferHandler.MOVE)
    }

    /**
     * Pastes from the system clipboard: reads the clipboard and, when the bound component's [clipboard]
     * modifier's `canImport` accepts the flavors, imports the contents through that modifier's
     * `onPaste`. Returns whether the import succeeded; returns `false` while the handle is unbound, or
     * while no clipboard is reachable or its contents cannot be read.
     *
     * @see javax.swing.TransferHandler.importData
     */
    public fun paste(): Boolean {
        val component = component ?: return false
        val handler = component.transferHandler
        val contents = resolveClipboard()?.contentsOrNull()
        return handler != null &&
            contents != null &&
            handler.importData(TransferHandler.TransferSupport(component, contents))
    }

    private fun export(action: Int) {
        val component = component ?: return
        val clipboard = resolveClipboard() ?: return
        try {
            component.transferHandler?.exportToClipboard(component, clipboard, action)
        } catch (_: IllegalStateException) {
            // A clipboard another application holds open refuses to take the value, which
            // TransferHandler.exportToClipboard reports through exportDone as a completed export that
            // transferred nothing before it throws. The outcome has already reached the component.
        }
    }

    /** Binds the handle to [target] so copy/cut/paste act on it; called by the [clipboard] modifier. */
    internal fun bind(target: JComponent) {
        component = target
    }

    /** Unbinds [target] if it is the currently bound component, leaving a handle bound elsewhere intact. */
    internal fun unbind(target: JComponent) {
        if (component === target) component = null
    }
}

/**
 * Creates and remembers a [ClipboardHandle] to drive a component's clipboard copy/cut/paste
 * programmatically. Pass it to `clipboard(handle = ...)` to bind it to that component, then call
 * [ClipboardHandle.copy]/[ClipboardHandle.cut]/[ClipboardHandle.paste] from an event handler.
 */
@Composable
public fun rememberClipboardHandle(): ClipboardHandle = remember { ClipboardHandle { systemClipboard } }

private class DraggableElement(
    @param:TransferAction private val exportedActions: Int,
    private val transferable: (JComponent) -> Transferable?,
) : SwingModifier.Element<JComponent, DraggableElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.config = SourceConfig(exportedActions, transferable)
        node.apply()
    }

    class Node : SwingModifier.Node<JComponent>() {
        var config: SourceConfig? = null
        private var gesture: DragGesture? = null

        override fun onAttach() {
            // A generic component has no built-in drag gesture, so installing only the TransferHandler
            // would never start a drag. Install a mouse gesture that, past the platform threshold, asks
            // the handler to export the drag - once per node, removed on detach.
            val gesture = DragGesture(component)
            component.addMouseListener(gesture)
            component.addMouseMotionListener(gesture)
            this.gesture = gesture
        }

        fun apply() {
            val config = config ?: return
            val handler = installedHandler(component)
            // Claim the slot once, then refresh the config under this node's ownership: the config is
            // a fresh value each recomposition, so it is not a stable ownership key - the node is.
            if (!handler.source.set(this, config)) {
                handler.source.install(this, config)
            }
        }

        override fun onDetach() {
            gesture?.let {
                component.removeMouseListener(it)
                component.removeMouseMotionListener(it)
            }
            gesture = null
            clearSlotIfOwned(component, this) { it.source }
            uninstallIfEmpty(component)
        }
    }
}

/**
 * The drag gesture a [draggable] component installs: tracks the press point and, once the pointer
 * moves past the platform drag threshold, hands the drag to the component's [TransferHandler] via
 * [TransferHandler.exportAsDrag]. The handler then pulls the [Transferable] from its source slice, so
 * the same exporter drives both drag and clipboard while only a real drag fires this path.
 */
private class DragGesture(
    private val component: JComponent,
) : MouseAdapter() {
    private var origin: Point? = null

    override fun mousePressed(event: MouseEvent) {
        origin = event.point
    }

    override fun mouseReleased(event: MouseEvent) {
        origin = null
    }

    override fun mouseDragged(event: MouseEvent) {
        val start = origin ?: return
        val threshold = DragSource.getDragThreshold()
        if (abs(event.x - start.x) < threshold && abs(event.y - start.y) < threshold) return
        origin = null
        // exportAsDrag verifies the requested action against getSourceActions before starting; pass
        // the source's own offered action so it matches what the handler reports.
        component.transferHandler?.let { handler ->
            handler.exportAsDrag(component, event, handler.getSourceActions(component))
        }
    }
}

private class DropTargetElement(
    @param:TransferAction private val acceptedActions: Int,
    private val onDrop: (Transferable) -> Boolean,
    private val canImport: (List<DataFlavor>) -> Boolean,
) : SwingModifier.Element<JComponent, DropTargetElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.config = DropConfig(acceptedActions, onDrop, canImport)
        node.apply()
    }

    class Node : SwingModifier.Node<JComponent>() {
        var config: DropConfig? = null

        fun apply() {
            val config = config ?: return
            val handler = installedHandler(component)
            // Claim the slot once, then refresh the config under this node's ownership.
            if (!handler.drop.set(this, config)) {
                handler.drop.install(this, config)
            }
        }

        override fun onDetach() {
            clearSlotIfOwned(component, this) { it.drop }
            uninstallIfEmpty(component)
        }
    }
}

private class ClipboardElement(
    private val transferable: (JComponent) -> Transferable?,
    private val onPaste: (Transferable) -> Boolean,
    private val canImport: (List<DataFlavor>) -> Boolean,
    private val bindKeys: Boolean,
) : SwingModifier.Element<JComponent, ClipboardElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node(bindKeys)

    override fun update(node: Node) {
        node.transferable = transferable
        node.onPaste = onPaste
        node.canImport = canImport
        node.apply()
    }

    class Node(
        private val bindKeys: Boolean,
    ) : SwingModifier.Node<JComponent>() {
        var transferable: (JComponent) -> Transferable? = { null }
        var onPaste: (Transferable) -> Boolean = { false }
        var canImport: (List<DataFlavor>) -> Boolean = { true }

        private var keysBound = false

        fun apply() {
            val component = component
            val handler = installedHandler(component)
            // Clipboard export reuses the drag source's exporter (copy/cut both produce the same
            // value); declaring COPY_OR_MOVE lets exportToClipboard run for either action.
            val sourceConfig = SourceConfig(TransferHandler.COPY_OR_MOVE) { transferable(it) }
            // Refresh the slot if we still own it; otherwise claim it only while it is free, so a
            // sibling draggable's export is left intact.
            if (!handler.source.set(this, sourceConfig) && handler.source.value == null) {
                handler.source.install(this, sourceConfig)
            }
            val dropConfig =
                DropConfig(TransferHandler.COPY_OR_MOVE, { onPaste(it) }, { canImport(it) })
            if (!handler.drop.set(this, dropConfig)) {
                handler.drop.install(this, dropConfig)
            }
            if (bindKeys && !keysBound) {
                bindClipboardKeys(component)
                keysBound = true
            }
        }

        override fun onDetach() {
            val component = component
            clearSlotIfOwned(component, this) { it.source }
            clearSlotIfOwned(component, this) { it.drop }
            if (keysBound) unbindClipboardKeys(component)
            keysBound = false
            uninstallIfEmpty(component)
        }
    }
}

private class ExportDoneElement(
    private val onExportDone: (JComponent, Transferable?, Int) -> Unit,
) : SwingModifier.Element<JComponent, ExportDoneElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.onExportDone = onExportDone
        node.apply()
    }

    class Node : SwingModifier.Node<JComponent>() {
        var onExportDone: ((JComponent, Transferable?, Int) -> Unit)? = null

        fun apply() {
            val onExportDone = onExportDone ?: return
            val handler = installedHandler(component)
            // Claim the seam once, then refresh the callback under this node's ownership: the
            // callback is a fresh value each recomposition, so it is not a stable ownership key -
            // the node is.
            if (!handler.onExportDone.set(this, onExportDone)) {
                handler.onExportDone.install(this, onExportDone)
            }
        }

        override fun onDetach() {
            clearSlotIfOwned(component, this) { it.onExportDone }
            uninstallIfEmpty(component)
        }
    }
}

private const val COPY_KEY = "swing-ui.clipboard.copy"
private const val CUT_KEY = "swing-ui.clipboard.cut"
private const val PASTE_KEY = "swing-ui.clipboard.paste"

private fun bindClipboardKeys(component: JComponent) {
    val shortcut = menuShortcutMask()
    val inputMap = component.getInputMap(JComponent.WHEN_FOCUSED)
    inputMap.put(KeyStroke.getKeyStroke('C'.code, shortcut), COPY_KEY)
    inputMap.put(KeyStroke.getKeyStroke('X'.code, shortcut), CUT_KEY)
    inputMap.put(KeyStroke.getKeyStroke('V'.code, shortcut), PASTE_KEY)
    val actionMap = component.actionMap
    actionMap.put(COPY_KEY, TransferHandler.getCopyAction())
    actionMap.put(CUT_KEY, TransferHandler.getCutAction())
    actionMap.put(PASTE_KEY, TransferHandler.getPasteAction())
}

private fun unbindClipboardKeys(component: JComponent) {
    val shortcut = menuShortcutMask()
    val inputMap = component.getInputMap(JComponent.WHEN_FOCUSED)
    inputMap.remove(KeyStroke.getKeyStroke('C'.code, shortcut))
    inputMap.remove(KeyStroke.getKeyStroke('X'.code, shortcut))
    inputMap.remove(KeyStroke.getKeyStroke('V'.code, shortcut))
    val actionMap = component.actionMap
    actionMap.remove(COPY_KEY)
    actionMap.remove(CUT_KEY)
    actionMap.remove(PASTE_KEY)
}

/** A drag-source/clipboard-export slice: what to export and which operations it offers. */
internal class SourceConfig(
    val exportedActions: Int,
    val transferable: (JComponent) -> Transferable?,
)

/** A drop-target/clipboard-import slice: which operations and flavors it accepts and how to import. */
internal class DropConfig(
    val acceptedActions: Int,
    val onImport: (Transferable) -> Boolean,
    val canImport: (List<DataFlavor>) -> Boolean,
)

/**
 * The one `TransferHandler` every data-transfer modifier on a component configures. It holds a
 * [source] (drag/clipboard export) slot, a [drop] (drop/clipboard import) slot, and the
 * [onExportDone] export-completion seam, and routes the `TransferHandler` callbacks to whichever are
 * occupied, so drag, drop, and clipboard coexist on a single component. [original] is the handler the
 * component had before this one was installed: it answers the callbacks no slot is occupied for, and
 * is restored once every slot is empty.
 */
internal class SharedTransferHandler : TransferHandler() {
    var original: TransferHandler? = null

    val source = SliceSlot<SourceConfig>()
    val drop = SliceSlot<DropConfig>()
    val onExportDone = SliceSlot<(JComponent, Transferable?, Int) -> Unit>()

    /**
     * Whether the component's own handler is the one that exports. `createTransferable` and `exportDone`
     * are `protected`, so they cannot be forwarded to [original] the way the import callbacks are: a
     * component that declared no source would otherwise answer `getSourceActions` on its handler's
     * behalf and then hand back no transferable, and its copy and cut would stop working - even where an
     * [onExportDone] is declared, since it has nothing of its own to export. With no source slot occupied
     * the two public export entry points are given to [original] whole instead.
     *
     * That hand-off is complete for [exportToClipboard]: it calls `createTransferable` and `exportDone`
     * on itself, so [original]'s own `exportDone` answers the export instead of [onExportDone]. It is not
     * complete for [exportAsDrag]: Swing's drag machinery re-reads the component's *installed* transfer
     * handler - still this one - once the gesture is recognized, so the drag finds no transferable here
     * and [onExportDone] receives its `TransferHandler.NONE` completion instead of [original] ever
     * running.
     */
    private val exportsThroughOriginal: TransferHandler?
        get() = if (source.value == null) original else null

    override fun exportToClipboard(
        comp: JComponent,
        clip: Clipboard,
        action: Int,
    ) {
        val owner = exportsThroughOriginal
        if (owner == null) super.exportToClipboard(comp, clip, action) else owner.exportToClipboard(comp, clip, action)
    }

    override fun exportAsDrag(
        comp: JComponent,
        event: InputEvent,
        action: Int,
    ) {
        val owner = exportsThroughOriginal
        if (owner == null) super.exportAsDrag(comp, event, action) else owner.exportAsDrag(comp, event, action)
    }

    override fun getSourceActions(c: JComponent?): Int =
        source.value?.exportedActions ?: original?.getSourceActions(c) ?: NONE

    override fun createTransferable(c: JComponent): Transferable? = source.value?.transferable?.invoke(c)

    // Every export path (drag end, clipboard export, failed export) terminates here; data is null
    // when the action is NONE. The seam is where the component implements MOVE cleanup, so route
    // the completed action to it.
    override fun exportDone(
        source: JComponent,
        data: Transferable?,
        action: Int,
    ) {
        onExportDone.value?.invoke(source, data, action)
    }

    // With a drop slice occupied the declared import decides; with none, the component keeps the
    // import it already had, so a widget that ships one (a text component's paste) still performs it.
    override fun canImport(support: TransferSupport): Boolean {
        val config = drop.value ?: return original?.canImport(support) ?: false
        val actionAccepted = !support.isDrop || (config.acceptedActions and support.dropAction) != 0
        return actionAccepted && config.canImport(support.dataFlavors.asList())
    }

    override fun importData(support: TransferSupport): Boolean {
        val config = drop.value ?: return original?.importData(support) ?: false
        return canImport(support) && config.onImport(support.transferable)
    }
}

/**
 * One capability slot of a [SharedTransferHandler], owned by exactly one node. Ownership is tracked
 * by the identity of the owning node rather than of the value: a node refreshes its value on every
 * recomposition (a fresh value each time), so value identity is not a stable ownership key, whereas
 * the node stays the same instance for as long as it holds the slot. A node claims the slot with
 * [install], refreshes under its own ownership with [set], and releases through [clear], which
 * empties the slot only while that node still owns it - so a slot another node has since taken over
 * is never cleared.
 */
internal class SliceSlot<T : Any> {
    var value: T? = null
        private set

    private var owner: Any? = null

    /** Sets [value] and takes ownership for [owner], displacing any previous owner. */
    fun install(
        owner: Any,
        value: T,
    ) {
        this.value = value
        this.owner = owner
    }

    /**
     * Refreshes the value on behalf of [owner]. Returns whether [owner] still owns the slot (and the
     * value was applied); a caller that gets `false` must re-[install] to reclaim it.
     */
    fun set(
        owner: Any,
        value: T,
    ): Boolean {
        if (this.owner !== owner) return false
        this.value = value
        return true
    }

    /** Empties the slot only if [owner] still owns it. */
    fun clear(owner: Any) {
        if (this.owner !== owner) return
        value = null
        this.owner = null
    }
}
