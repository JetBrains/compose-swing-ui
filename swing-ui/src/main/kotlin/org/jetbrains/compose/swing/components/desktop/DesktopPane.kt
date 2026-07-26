@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.core.SlotAttachment
import org.jetbrains.compose.swing.core.SlotNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener
import java.beans.PropertyVetoException
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.event.InternalFrameListener

/**
 * A composable wrapper for `JDesktopPane` hosting internal-frame children declared in [block].
 *
 * Declare the frames you need; each `internalFrame(...)` becomes a `JInternalFrame` floating on the
 * desktop with its own title, position, size, and window controls. Frames are **dynamic**: adding or
 * removing an `internalFrame(...)` adds or removes the frame it identifies (see [DesktopPaneScope]), and
 * a frame's title, controls, and bounds update on recomposition.
 *
 * The close control is **controlled**: activating it invokes the frame's `onClose` rather than closing
 * the frame on its own. Remove the frame from the composition in response to actually close it.
 *
 * A frame declared with plain `bounds` sits where the declaration puts it and, for as long as its
 * declarations go on naming one identity (see [DesktopPaneScope]), stays wherever the user leaves it.
 * Declare it with an [InternalFrameState] instead to make its geometry and its window state two-way:
 * assigning to the state moves, resizes, iconifies or maximizes the frame, and the user dragging the
 * frame, pulling its border or activating its iconify, maximize or restore control writes the new value
 * back into the state.
 *
 * A frame's window transitions (opened, closing, closed, activated, deactivated, and iconified and
 * deiconified again) are among the events the `internalFrame` overloads taking an [InternalFrameListener]
 * deliver.
 *
 * ```
 * val editor = rememberInternalFrameState(bounds = Rectangle(0, 0, 300, 200))
 * DesktopPane {
 *     internalFrame(title = "Editor", state = editor) { Editor() }
 *     internalFrame(title = "Console", bounds = Rectangle(40, 40, 300, 200)) { Console() }
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the underlying `JDesktopPane`
 * @param block declares the internal frames; see [DesktopPaneScope]
 */
@Composable
public fun DesktopPane(
    modifier: SwingModifier = SwingModifier,
    block: DesktopPaneScope.() -> Unit,
) {
    // Collected fresh on every pass, so a frame the caller stops declaring is uninstalled (see SwingNode).
    val scope = DesktopPaneScopeImpl().apply(block)

    SwingNode(
        factory = { JDesktopPane() },
        update = {
            applyModifier(modifier)
        },
        content = {
            scope.frames.forEach { frame ->
                key(frame.identity) {
                    val attachment = remember { internalFrameAttachment() }
                    SlotNode(attachment) {
                        InternalFrame(frame)
                    }
                }
            }
        },
    )
}

/**
 * Which window controls an internal frame shows. Every control defaults to off, matching a freshly
 * constructed `JInternalFrame`.
 *
 * @property closable whether the frame shows a close control
 * @property resizable whether the frame can be resized
 * @property maximizable whether the frame can be maximized
 * @property iconifiable whether the frame can be iconified
 */
public class InternalFrameControls(
    public val closable: Boolean = false,
    public val resizable: Boolean = false,
    public val maximizable: Boolean = false,
    public val iconifiable: Boolean = false,
)

/**
 * Declarative internal frames of a [DesktopPane]. Each [internalFrame] call appends one frame, in call
 * order.
 *
 * A frame keeps the `JInternalFrame` it was realized as - and with it everything the user has done to
 * that frame - for as long as the declarations it appears in name one identity. A frame declared with an
 * [InternalFrameState] is identified by that state instance. A frame declared with plain `bounds` is
 * identified by the `key` it is given, and by its position among the declared frames when it is given
 * none; so in a list frames are added to and removed from, give each frame a key of its own, and the
 * frames declared after a removed one stay the frames the user left behind.
 */
public interface DesktopPaneScope {
    /**
     * Declares one internal frame.
     *
     * @param title the text shown in the frame's title bar
     * @param bounds the frame's position and size within the desktop
     * @param key what identifies this frame among the declared frames; see [DesktopPaneScope]
     * @param controls which window controls the frame shows
     * @param onClose callback invoked when the user activates the frame's close control; remove the
     *   frame from the composition in response to actually close it
     * @param modifier the [SwingModifier] applied to the frame
     * @param content the composable shown in the frame's body
     */
    @Suppress("LongParameterList")
    // Independent declarative aspects of one frame - title, geometry, identity, controls, close
    // behaviour, appearance, body - each named at the call site, with no cohesive object among them.
    public fun internalFrame(
        title: String,
        bounds: Rectangle,
        key: Any? = null,
        controls: InternalFrameControls = InternalFrameControls(),
        onClose: () -> Unit = {},
        modifier: SwingModifier = SwingModifier,
        content: @Composable () -> Unit,
    )

    /**
     * Declares one internal frame driven by a raw [InternalFrameListener] instead of an `onClose`
     * lambda. The [internalFrameListener] is attached as-is and removed on the same instance; pass a
     * stable instance (e.g. `remember {}`) to avoid churn. Use this overload to observe the full set of
     * internal-frame events (opened, closing, closed, iconified, deiconified, activated, deactivated);
     * the close control stays controlled, so remove the frame from the composition to actually close it.
     *
     * @param title the text shown in the frame's title bar
     * @param bounds the frame's position and size within the desktop
     * @param internalFrameListener the listener notified of the frame's window events
     * @param key what identifies this frame among the declared frames; see [DesktopPaneScope]
     * @param controls which window controls the frame shows
     * @param modifier the [SwingModifier] applied to the frame
     * @param content the composable shown in the frame's body
     */
    @Suppress("LongParameterList")
    // Independent declarative aspects of one frame with no cohesive object among them, the raw listener
    // standing in for the close callback.
    public fun internalFrame(
        title: String,
        bounds: Rectangle,
        internalFrameListener: InternalFrameListener,
        key: Any? = null,
        controls: InternalFrameControls = InternalFrameControls(),
        modifier: SwingModifier = SwingModifier,
        content: @Composable () -> Unit,
    )

    /**
     * Declares one internal frame whose geometry and window state are driven by the hoisted [state].
     * Assigning to [InternalFrameState.bounds] moves and resizes the frame, assigning to
     * [InternalFrameState.iconified] iconifies or deiconifies it and assigning to
     * [InternalFrameState.maximized] maximizes or restores it, and a user dragging its title bar, pulling
     * its border or activating its iconify, maximize or restore control writes the new value back into
     * [state].
     *
     * @param title the text shown in the frame's title bar
     * @param state the hoistable, observable geometry and window state of the frame
     * @param controls which window controls the frame shows
     * @param onClose callback invoked when the user activates the frame's close control; remove the
     *   frame from the composition in response to actually close it
     * @param modifier the [SwingModifier] applied to the frame
     * @param content the composable shown in the frame's body
     */
    @Suppress("LongParameterList")
    // Independent declarative aspects of one frame with no cohesive object among them, the hoisted state
    // standing in for its bounds.
    public fun internalFrame(
        title: String,
        state: InternalFrameState,
        controls: InternalFrameControls = InternalFrameControls(),
        onClose: () -> Unit = {},
        modifier: SwingModifier = SwingModifier,
        content: @Composable () -> Unit,
    )

    /**
     * Declares one internal frame whose geometry and window state are driven by the hoisted [state],
     * and whose window events reach a raw [InternalFrameListener] instead of an `onClose` lambda. The
     * [internalFrameListener] is attached as-is and removed on the same instance; pass a stable
     * instance (e.g. `remember {}`) to avoid churn.
     *
     * @param title the text shown in the frame's title bar
     * @param state the hoistable, observable geometry and window state of the frame
     * @param internalFrameListener the listener notified of the frame's window events
     * @param controls which window controls the frame shows
     * @param modifier the [SwingModifier] applied to the frame
     * @param content the composable shown in the frame's body
     */
    @Suppress("LongParameterList")
    // Independent declarative aspects of one frame with no cohesive object among them, hoisted state and
    // raw listener in place of its bounds and its close callback.
    public fun internalFrame(
        title: String,
        state: InternalFrameState,
        internalFrameListener: InternalFrameListener,
        controls: InternalFrameControls = InternalFrameControls(),
        modifier: SwingModifier = SwingModifier,
        content: @Composable () -> Unit,
    )
}

/**
 * A frame's per-composition appearance snapshot: its title, the geometry it declares, which window
 * controls it shows, and the modifier applied to it.
 */
private class InternalFrameMetadata(
    val title: String,
    val declared: DeclaredFrameState,
    val controls: InternalFrameControls,
    val modifier: SwingModifier,
)

/**
 * The geometry and window state one composition declares for a frame.
 *
 * [source] is the hoisted state the values were read from, and is what makes them two-way: the user's own
 * moves, resizes, iconifications and maximizations are written back into it. A frame declared with plain
 * bounds names no source, so it is placed by its declaration and nothing observes where it ends up.
 */
private class DeclaredFrameState(
    val bounds: Rectangle,
    val iconified: Boolean,
    val maximized: Boolean,
    val source: InternalFrameState?,
) {
    constructor(state: InternalFrameState) : this(state.bounds, state.iconified, state.maximized, state)

    constructor(bounds: Rectangle) : this(bounds, iconified = false, maximized = false, source = null)
}

/**
 * One declared internal frame: its [metadata] snapshot for this composition, the [identity] deciding which
 * realized `JInternalFrame` a later composition's declaration lands on - see [DesktopPaneScope] for the
 * rule it expresses - plus its body composable. Exactly one of [onClose]/[rawListener] is set: the
 * `onClose` overload supplies the controlled close callback (a stable adapter is built in
 * [InternalFrame]), the raw overload supplies the listener instance directly.
 */
private class InternalFrameDeclaration(
    val metadata: InternalFrameMetadata,
    val identity: Any,
    val onClose: (() -> Unit)?,
    val rawListener: InternalFrameListener?,
    val content: @Composable () -> Unit,
)

/**
 * The identity of a frame declared with neither a hoisted state nor a key: the position it was declared
 * at. It is a type of its own so that a caller's key can never be taken for a position, whatever the
 * caller keys frames by.
 */
private class PositionalFrameIdentity(
    private val index: Int,
) {
    override fun equals(other: Any?): Boolean = other is PositionalFrameIdentity && other.index == index

    override fun hashCode(): Int = index
}

private class DesktopPaneScopeImpl : DesktopPaneScope {
    val frames: MutableList<InternalFrameDeclaration> = ArrayList()

    override fun internalFrame(
        title: String,
        bounds: Rectangle,
        key: Any?,
        controls: InternalFrameControls,
        onClose: () -> Unit,
        modifier: SwingModifier,
        content: @Composable () -> Unit,
    ) {
        addFrame(
            metadata = InternalFrameMetadata(title, DeclaredFrameState(bounds), controls, modifier),
            key = key,
            content = content,
            onClose = onClose,
            rawListener = null,
        )
    }

    override fun internalFrame(
        title: String,
        bounds: Rectangle,
        internalFrameListener: InternalFrameListener,
        key: Any?,
        controls: InternalFrameControls,
        modifier: SwingModifier,
        content: @Composable () -> Unit,
    ) {
        addFrame(
            metadata = InternalFrameMetadata(title, DeclaredFrameState(bounds), controls, modifier),
            key = key,
            content = content,
            onClose = null,
            rawListener = internalFrameListener,
        )
    }

    override fun internalFrame(
        title: String,
        state: InternalFrameState,
        controls: InternalFrameControls,
        onClose: () -> Unit,
        modifier: SwingModifier,
        content: @Composable () -> Unit,
    ) {
        addFrame(
            metadata = InternalFrameMetadata(title, DeclaredFrameState(state), controls, modifier),
            key = null,
            content = content,
            onClose = onClose,
            rawListener = null,
        )
    }

    override fun internalFrame(
        title: String,
        state: InternalFrameState,
        internalFrameListener: InternalFrameListener,
        controls: InternalFrameControls,
        modifier: SwingModifier,
        content: @Composable () -> Unit,
    ) {
        addFrame(
            metadata = InternalFrameMetadata(title, DeclaredFrameState(state), controls, modifier),
            key = null,
            content = content,
            onClose = null,
            rawListener = internalFrameListener,
        )
    }

    private fun addFrame(
        metadata: InternalFrameMetadata,
        key: Any?,
        content: @Composable () -> Unit,
        onClose: (() -> Unit)?,
        rawListener: InternalFrameListener?,
    ) {
        frames.add(
            InternalFrameDeclaration(
                metadata = metadata,
                identity = metadata.declared.source ?: key ?: PositionalFrameIdentity(frames.size),
                onClose = onClose,
                rawListener = rawListener,
                content = content,
            ),
        )
    }
}

/**
 * Hosts one [JInternalFrame] on the host [JDesktopPane]: adds it on install and detaches it by identity
 * on uninstall so removing an earlier frame never invalidates a later frame's uninstall.
 *
 * An iconified frame stands on the desktop as its desktop icon, and the icon is placed wherever the look
 * and feel keeps it rather than beside the frame, so the uninstall takes the icon off whatever holds it
 * as well as the frame off the desktop. Otherwise a frame that leaves the composition while iconified
 * leaves its icon behind.
 */
private fun internalFrameAttachment(): SlotAttachment =
    SlotAttachment { host, component, _ ->
        host as JDesktopPane
        host.add(component)
        return@SlotAttachment {
            host.remove(component)
            val icon = (component as JInternalFrame).desktopIcon
            icon.parent?.remove(icon)
        }
    }

/**
 * One `JInternalFrame` node: builds the frame visible, installs the declaration's window-event handling,
 * applies its title, controls, geometry and window state reactively, and writes the user's own moves,
 * resizes, iconifications and maximizations back into the declared state. Hosts the declared body as
 * composable content.
 */
@Composable
private fun InternalFrame(frame: InternalFrameDeclaration) {
    // The onClose overload routes the close control through a stable adapter that fires the latest
    // callback on internalFrameClosing (the close operation stays do-nothing, so the frame is only
    // closed by being removed from the composition). The raw overload uses the supplied listener
    // instance directly.
    val onClose = rememberUpdatedState(frame.onClose)
    val listener =
        remember(frame.rawListener) {
            frame.rawListener ?: object : InternalFrameAdapter() {
                override fun internalFrameClosing(event: InternalFrameEvent) {
                    onClose.value?.invoke()
                }
            }
        }
    val metadata = frame.metadata
    val declared = metadata.declared

    val applied = remember { AppliedFrameState() }

    // The write-back listeners are installed once per frame, while the state they write into is declared
    // afresh on every composition: they reach it through a handle the recomposition refreshes, so a
    // caller that hoists its frame state somewhere else keeps receiving what the user does next.
    val target = rememberUpdatedState(declared.source)
    val geometryWriteBack = remember { frameGeometryWriteBack(applied, target) }
    val iconWriteBack = remember { frameIconWriteBack(target) }
    val maximumWriteBack = remember { frameMaximumWriteBack(applied, target) }
    val attachSync = remember { frameStateAttachSync(applied, target) }

    SwingNode(
        factory = {
            JInternalFrame(
                metadata.title,
                metadata.controls.resizable,
                metadata.controls.closable,
                metadata.controls.maximizable,
                metadata.controls.iconifiable,
            ).apply {
                // A JInternalFrame is constructed hidden and the close control closes it on its own;
                // make it visible and leave the close control controlled by the declaration instead.
                bounds = declared.bounds
                // The placement is stamped like every later one (see the update block), so the
                // notifications it provokes cannot undo a move declared before they are delivered.
                applied.bounds = bounds
                defaultCloseOperation = JInternalFrame.DO_NOTHING_ON_CLOSE
                isVisible = true
            }
        },
        update = {
            set(metadata.title) { this.title = it }
            set(metadata.controls.closable) { this.isClosable = it }
            set(metadata.controls.resizable) { this.isResizable = it }
            set(metadata.controls.maximizable) { this.isMaximizable = it }
            set(metadata.controls.iconifiable) { this.isIconifiable = it }
            // The geometry is stamped before it is pushed, because a frame reports its moves and resizes
            // asynchronously: by the time such a notification is delivered the state may already hold a
            // newer value, and the frame - which still carries the older geometry - would hand that
            // older geometry back over it. The stamp is what tells the write-back that the geometry the
            // frame is reporting is the one this apply put there, so the newer declaration stands.
            update(declared.bounds) { value -> applyBounds(value, applied) }
            // A window-state transition takes the frame off the desktop or spreads it across the desktop,
            // either of which needs a desktop. This update block runs before the desktop takes the frame,
            // so the first application is left to [attachSync], which performs it the moment the desktop
            // does; by the time a later declaration arrives here the frame is attached and takes it
            // directly. Maximizing comes before iconifying, so a frame declared as both ends up an icon
            // that restores to the whole desktop, as both declarations ask.
            set(declared.maximized) { value -> applyMaximized(value, applied, target.value) }
            set(declared.iconified) { value -> applyIconified(value, applied, target.value) }
            val stateChannels =
                if (declared.source == null) {
                    SwingModifier
                } else {
                    SwingModifier
                        .componentListener(geometryWriteBack)
                        .propertyChangeListener(JInternalFrame.IS_ICON_PROPERTY, iconWriteBack)
                        .propertyChangeListener(JInternalFrame.IS_MAXIMUM_PROPERTY, maximumWriteBack)
                        .hierarchyListener(attachSync)
                }
            applyModifier(metadata.modifier.then(stateChannels).internalFrameListener(listener))
        },
        content = { frame.content() },
    )
}

/**
 * The values that are currently in sync between an [InternalFrameState] and its realized
 * `JInternalFrame`.
 *
 * Both the state-to-frame apply and the frame-to-state write-back update this holder, which is what
 * keeps the two directions from fighting: a `JInternalFrame` reports a move or a resize asynchronously,
 * so the geometry such a notification carries can be older than the state that has since been declared,
 * and only the frame itself knows the difference between the geometry an apply gave it and the geometry
 * a user's drag gave it. Recording what was last applied here supplies that difference.
 *
 * [windowStateApplied] records that the frame has been given a window-state declaration at all, which is
 * how the attach-time application tells a frame that never received one from a frame whose later
 * transitions the node's update block is already driving.
 */
private class AppliedFrameState {
    var bounds: Rectangle? = null
    var windowStateApplied: Boolean = false
}

/**
 * A listener that writes the user's own moves and resizes of an internal frame back into the state
 * [target] currently points at, keeping [applied] equal to the value it hands over. A frame reporting
 * the geometry that was last applied to it is reporting that apply and not a drag, so the state - which
 * may already hold a newer value - is left alone.
 *
 * A `JInternalFrame` is dragged and resized through its desktop manager, which repositions and resizes
 * it as an ordinary component: the move and the resize are reported as component events and never as
 * internal-frame events, so this is the channel that carries them.
 *
 * A maximized frame's geometry is the desktop's own rather than anything the user chose for the frame, and
 * the state carries the geometry a restore is to return the frame to - so the desktop-filling geometry, and
 * every later one a maximized frame takes, is left out of it.
 */
private fun frameGeometryWriteBack(
    applied: AppliedFrameState,
    target: State<InternalFrameState?>,
): ComponentListener =
    object : ComponentAdapter() {
        override fun componentMoved(event: ComponentEvent) = writeBounds(event)

        override fun componentResized(event: ComponentEvent) = writeBounds(event)

        private fun writeBounds(event: ComponentEvent) {
            val state = target.value ?: return
            val frame = event.component as JInternalFrame
            val live = frame.bounds
            if (frame.isMaximum || applied.bounds == live) return
            applied.bounds = live
            state.bounds = live
        }
    }

/**
 * Moves and resizes [this] frame to [bounds], recording it in [applied] as the geometry this apply gave the
 * frame.
 *
 * A maximized frame stands on the whole desktop instead of on the geometry it is given, so the value is
 * recorded and reaches the frame when it is restored.
 */
private fun JInternalFrame.applyBounds(
    bounds: Rectangle,
    applied: AppliedFrameState,
) {
    applied.bounds = Rectangle(bounds)
    if (!isMaximum) {
        this.bounds = bounds
    }
}

/**
 * Performs a window-state [transition] on [this] frame if the frame's place in the hierarchy is one that
 * can take it, stamping the application in [applied].
 *
 * Both window states need the desktop to hold the frame already: iconifying moves the frame's icon onto
 * the desktop in the frame's place, and maximizing spreads the frame across the desktop. A frame the
 * desktop holds neither as itself nor as its icon merely records the value while staying in full view, so
 * the transition waits for [frameStateAttachSync] to perform it once the desktop takes the frame.
 */
private inline fun JInternalFrame.applyWindowState(
    applied: AppliedFrameState,
    transition: () -> Unit,
) {
    if (parent == null && desktopIcon.parent == null) return
    applied.windowStateApplied = true
    transition()
}

/**
 * Maximizes or restores [this] frame to match [maximized].
 *
 * A `PropertyVetoException` is a vetoing listener of the caller's own refusing the transition. The frame
 * keeps the state it was in, so [state] is resynchronized from it and goes on reporting what the frame
 * actually is.
 */
private fun JInternalFrame.applyMaximized(
    maximized: Boolean,
    applied: AppliedFrameState,
    state: InternalFrameState?,
) = applyWindowState(applied) {
    try {
        isMaximum = maximized
    } catch (_: PropertyVetoException) {
        state?.maximized = isMaximum
    }
}

/**
 * Iconifies or deiconifies [this] frame to match [iconified].
 *
 * A `PropertyVetoException` is a vetoing listener of the caller's own refusing the transition. The frame
 * keeps the state it was in, so [state] is resynchronized from it and goes on reporting what the frame
 * actually is.
 */
private fun JInternalFrame.applyIconified(
    iconified: Boolean,
    applied: AppliedFrameState,
    state: InternalFrameState?,
) = applyWindowState(applied) {
    try {
        isIcon = iconified
    } catch (_: PropertyVetoException) {
        state?.iconified = isIcon
    }
}

/**
 * A listener that writes an internal frame's iconification and deiconification into the state [target]
 * currently points at. A frame publishes this transition as it happens rather than afterwards, so an
 * apply's own echo carries the value the state already holds and changes nothing, while the user's
 * title-bar control carries a value the state has yet to learn.
 */
private fun frameIconWriteBack(target: State<InternalFrameState?>): PropertyChangeListener =
    PropertyChangeListener { event ->
        val state = target.value ?: return@PropertyChangeListener
        val live = event.newValue as? Boolean ?: return@PropertyChangeListener
        state.iconified = live
    }

/**
 * A listener that writes an internal frame's maximization and restoration into the state [target] currently
 * points at, and puts a restored frame back on the geometry that state carries, keeping [applied] equal to
 * the geometry it hands over.
 *
 * A restore is where the geometry the state carries becomes the frame's again. Placing the frame here rather
 * than leaving it wherever it comes back on covers every restore, the user's control as much as a declared
 * one, and it is the state - not the frame - that knows where the frame is to return to: a desktop manager
 * takes a frame back to the geometry it held when it was maximized, and a frame maximized before its desktop
 * was laid out held none.
 */
private fun frameMaximumWriteBack(
    applied: AppliedFrameState,
    target: State<InternalFrameState?>,
): PropertyChangeListener =
    PropertyChangeListener { event ->
        val state = target.value ?: return@PropertyChangeListener
        val live = event.newValue as? Boolean ?: return@PropertyChangeListener
        state.maximized = live
        if (!live) (event.source as JInternalFrame).applyBounds(state.bounds, applied)
    }

/**
 * A listener that gives a frame the window state its composition declared, as soon as the frame's place in
 * the hierarchy is one that can take it - the transitions the node's update block cannot perform, because
 * that block runs while the frame has no desktop yet.
 *
 * Only the first declaration is applied here. From then on the frame has a desktop and the update block
 * drives it, while this listener keeps quiet: a deiconification puts the frame back on the desktop, which
 * is itself a change of place, and re-applying a declaration the write-back has not caught up with yet
 * would undo the very transition the user just made.
 */
private fun frameStateAttachSync(
    applied: AppliedFrameState,
    target: State<InternalFrameState?>,
): HierarchyListener =
    HierarchyListener { event ->
        if (applied.windowStateApplied) return@HierarchyListener
        val frame = event.component as? JInternalFrame ?: return@HierarchyListener
        val state = target.value ?: return@HierarchyListener
        frame.applyMaximized(state.maximized, applied, state)
        frame.applyIconified(state.iconified, applied, state)
    }
