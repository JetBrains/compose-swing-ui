@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.modifier.listener.componentListener
import org.jetbrains.compose.swing.modifier.listener.hierarchyListener
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.ChildPlacement
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.HierarchyListener
import java.beans.PropertyChangeListener
import java.beans.PropertyVetoException
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.event.InternalFrameListener

/**
 * A composable wrapper for `JDesktopPane` hosting internal-frame children declared in [content].
 *
 * Declare the frames you need; each `InternalFrame(...)` becomes a `JInternalFrame` floating on the
 * desktop with its own title, position, size, and window controls. Frames are **dynamic**: adding or
 * removing an `InternalFrame(...)` adds or removes the frame it identifies (see [DesktopPaneScope]), and
 * a frame's title, controls, and bounds update on recomposition.
 *
 * A frame is the one thing a desktop holds, and it holds as many of them as [content] declares: every
 * child of the pane is an `InternalFrame(...)`, and anything else composed here is refused. Compose it
 * inside a frame's body instead.
 *
 * The close control is **controlled**: activating it invokes the frame's `onClose` rather than closing
 * the frame on its own. Remove the frame from the composition in response to actually close it.
 *
 * A frame declared with plain `bounds` sits where the declaration puts it and, for as long as the
 * composition goes on declaring it in the same place (see [DesktopPaneScope]), stays wherever the user
 * leaves it. Declare it with an [InternalFrameState] instead to make its geometry and its window state
 * two-way: assigning to the state moves, resizes, iconifies or maximizes the frame, and the user dragging
 * the frame, pulling its border or activating its iconify, maximize or restore control writes the new
 * value back into the state.
 *
 * A frame's window transitions (opened, closing, closed, activated, deactivated, and iconified and
 * deiconified again) are among the events the `InternalFrame` overloads taking an [InternalFrameListener]
 * deliver.
 *
 * ```
 * val editor = rememberInternalFrameState(bounds = Rectangle(0, 0, 300, 200))
 * var editorOpen by remember { mutableStateOf(true) }
 * var consoleOpen by remember { mutableStateOf(true) }
 * DesktopPane {
 *     if (editorOpen) {
 *         InternalFrame(title = "Editor", state = editor, onClose = { editorOpen = false }) { Editor() }
 *     }
 *     if (consoleOpen) {
 *         InternalFrame(
 *             title = "Console",
 *             bounds = Rectangle(40, 40, 300, 200),
 *             onClose = { consoleOpen = false },
 *         ) { Console() }
 *     }
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the underlying `JDesktopPane`
 * @param content declares the internal frames; see [DesktopPaneScope]
 * @see javax.swing.JDesktopPane
 */
@Composable
public fun DesktopPane(
    modifier: SwingModifier = SwingModifier,
    content: @Composable DesktopPaneScope.() -> Unit,
) {
    // Remembered with the pane: it holds the hoisted states the pane's frames currently drive.
    val scope = remember { DesktopPaneScopeImpl() }

    SwingNode(
        factory = { JDesktopPane() },
        update = {
            applyModifier(modifier)
        },
        // A desktop holds frames and nothing else, as many of them as the composition declares, in the
        // order it declares them - so anything else composed here is refused rather than left standing
        // on the desktop unseen.
        childPlacement = ChildPlacement.OrderedSlots(FRAME_REGION),
        content = { scope.content() },
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
 * @see javax.swing.JInternalFrame.setClosable
 * @see javax.swing.JInternalFrame.setResizable
 * @see javax.swing.JInternalFrame.setMaximizable
 * @see javax.swing.JInternalFrame.setIconifiable
 */
public class InternalFrameControls(
    public val closable: Boolean = false,
    public val resizable: Boolean = false,
    public val maximizable: Boolean = false,
    public val iconifiable: Boolean = false,
)

/**
 * The receiver of a [DesktopPane]'s content, through which a frame floating on that desktop is declared.
 * Each [InternalFrame] call declares one frame, in call order, and a frame is the only child a desktop
 * takes: anything else composed in the content names no place on the desktop and is refused.
 *
 * A frame keeps the `JInternalFrame` it was realized as - and with it everything the user has done to that
 * frame - for as long as the composition goes on declaring it in the same place: a frame declared at a
 * place of its own keeps its window however the frames declared around it come and go. Frames declared
 * from one place, as a loop over a list declares them, are told apart by the position they are declared
 * in, so wrap each in [androidx.compose.runtime.key] to give it an identity of its own; the frames
 * declared after a removed one then stay the frames the user left behind.
 *
 * One [InternalFrameState] drives one frame: a state driving two frames at once would receive both
 * frames' write-backs, each undoing what the other's user interaction just wrote, so the second frame to
 * take a state stops the composition instead.
 *
 * @see javax.swing.JInternalFrame
 */
public sealed interface DesktopPaneScope {
    /**
     * Declares one internal frame.
     *
     * @param title the text shown in the frame's title bar
     * @param bounds the frame's position and size within the desktop
     * @param onClose callback invoked when the user activates the frame's close control; remove the
     *   frame from the composition in response to actually close it
     * @param modifier the [SwingModifier] applied to the frame
     * @param controls which window controls the frame shows
     * @param content the composable shown in the frame's body
     * @see javax.swing.JInternalFrame
     */
    @Composable
    public fun InternalFrame(
        title: @Nls String,
        bounds: Rectangle,
        onClose: () -> Unit,
        modifier: SwingModifier = SwingModifier,
        controls: InternalFrameControls = InternalFrameControls(),
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
     * @param modifier the [SwingModifier] applied to the frame
     * @param controls which window controls the frame shows
     * @param content the composable shown in the frame's body
     * @see javax.swing.JInternalFrame
     */
    @Composable
    public fun InternalFrame(
        title: @Nls String,
        bounds: Rectangle,
        internalFrameListener: InternalFrameListener,
        modifier: SwingModifier = SwingModifier,
        controls: InternalFrameControls = InternalFrameControls(),
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
     * @param state the hoistable, observable geometry and window state of the frame, driving this frame
     *   alone
     * @param onClose callback invoked when the user activates the frame's close control; remove the
     *   frame from the composition in response to actually close it
     * @param modifier the [SwingModifier] applied to the frame
     * @param controls which window controls the frame shows
     * @param content the composable shown in the frame's body
     * @see javax.swing.JInternalFrame
     */
    @Composable
    public fun InternalFrame(
        title: @Nls String,
        state: InternalFrameState,
        onClose: () -> Unit,
        modifier: SwingModifier = SwingModifier,
        controls: InternalFrameControls = InternalFrameControls(),
        content: @Composable () -> Unit,
    )

    /**
     * Declares one internal frame whose geometry and window state are driven by the hoisted [state],
     * and whose window events reach a raw [InternalFrameListener] instead of an `onClose` lambda. The
     * [internalFrameListener] is attached as-is and removed on the same instance; pass a stable
     * instance (e.g. `remember {}`) to avoid churn.
     *
     * @param title the text shown in the frame's title bar
     * @param state the hoistable, observable geometry and window state of the frame, driving this frame
     *   alone
     * @param internalFrameListener the listener notified of the frame's window events
     * @param modifier the [SwingModifier] applied to the frame
     * @param controls which window controls the frame shows
     * @param content the composable shown in the frame's body
     * @see javax.swing.JInternalFrame
     */
    @Composable
    public fun InternalFrame(
        title: @Nls String,
        state: InternalFrameState,
        internalFrameListener: InternalFrameListener,
        modifier: SwingModifier = SwingModifier,
        controls: InternalFrameControls = InternalFrameControls(),
        content: @Composable () -> Unit,
    )
}

/**
 * The [DesktopPaneScope] one [DesktopPane] hands its content. It is remembered alongside the pane, and
 * holds the hoisted states that pane's frames currently drive: a frame takes its state while it stands
 * and gives it back when it leaves, so a state a frame drops is free for the next frame to take.
 */
private class DesktopPaneScopeImpl : DesktopPaneScope {
    private val states: MutableSet<InternalFrameState> = HashSet()

    @Composable
    override fun InternalFrame(
        title: @Nls String,
        bounds: Rectangle,
        onClose: () -> Unit,
        modifier: SwingModifier,
        controls: InternalFrameControls,
        content: @Composable () -> Unit,
    ) {
        FrameNode(
            title = title,
            declared = PlacedFrameState(bounds),
            controls = controls,
            onClose = onClose,
            rawListener = null,
            modifier = modifier,
            content = content,
        )
    }

    @Composable
    override fun InternalFrame(
        title: @Nls String,
        bounds: Rectangle,
        internalFrameListener: InternalFrameListener,
        modifier: SwingModifier,
        controls: InternalFrameControls,
        content: @Composable () -> Unit,
    ) {
        FrameNode(
            title = title,
            declared = PlacedFrameState(bounds),
            controls = controls,
            onClose = null,
            rawListener = internalFrameListener,
            modifier = modifier,
            content = content,
        )
    }

    @Composable
    override fun InternalFrame(
        title: @Nls String,
        state: InternalFrameState,
        onClose: () -> Unit,
        modifier: SwingModifier,
        controls: InternalFrameControls,
        content: @Composable () -> Unit,
    ) {
        StateClaim(state)
        FrameNode(
            title = title,
            declared = HoistedFrameState(state),
            controls = controls,
            onClose = onClose,
            rawListener = null,
            modifier = modifier,
            content = content,
        )
    }

    @Composable
    override fun InternalFrame(
        title: @Nls String,
        state: InternalFrameState,
        internalFrameListener: InternalFrameListener,
        modifier: SwingModifier,
        controls: InternalFrameControls,
        content: @Composable () -> Unit,
    ) {
        StateClaim(state)
        FrameNode(
            title = title,
            declared = HoistedFrameState(state),
            controls = controls,
            onClose = null,
            rawListener = internalFrameListener,
            modifier = modifier,
            content = content,
        )
    }

    /**
     * The claim the frame being declared here has on [state]: it stands while that frame does, and is
     * given up when the frame leaves or declares a different state. The frames of one pane arrive one by
     * one rather than as a set, so this is where a state handed to a second frame is caught - see
     * [DesktopPaneScope] for what it would cost.
     *
     * A frame gives its state back before the next frame takes one, so a state passing from one frame to
     * another in a single pass passes freely.
     */
    @Composable
    private fun StateClaim(state: InternalFrameState) {
        DisposableEffect(state) {
            val taken = states.add(state)
            require(taken) { "DesktopPane frame state $state is declared by more than one frame" }
            onDispose { states.remove(state) }
        }
    }
}

/**
 * The geometry and window state one composition declares for a frame.
 *
 * [source] is the hoisted state the values come from, and is what makes them two-way: the user's own
 * moves, resizes, iconifications and maximizations are written back into it. A frame declared with plain
 * bounds names no source, so it is placed by its declaration and nothing observes where it ends up.
 *
 * The values stand for what the declaration holds rather than for a reading of it: each is read where the
 * frame it belongs to is composed (see [FrameNode]) and nowhere else.
 */
private sealed interface DeclaredFrameState {
    val bounds: Rectangle
    val iconified: Boolean
    val maximized: Boolean
    val source: InternalFrameState?
}

/** A frame driven by a hoisted [source]: every value it declares is that state's own. */
private class HoistedFrameState(
    override val source: InternalFrameState,
) : DeclaredFrameState {
    override val bounds: Rectangle get() = source.bounds
    override val iconified: Boolean get() = source.iconified
    override val maximized: Boolean get() = source.maximized
}

/** A frame placed on plain [bounds], in the window state a freshly constructed frame is in. */
private class PlacedFrameState(
    override val bounds: Rectangle,
) : DeclaredFrameState {
    override val iconified: Boolean get() = false
    override val maximized: Boolean get() = false
    override val source: InternalFrameState? get() = null
}

/**
 * The `JInternalFrame` node every [InternalFrame] overload renders: it builds the frame visible, installs
 * the declaration's window-event handling, applies its title, controls, geometry and window state
 * reactively, and writes the user's own moves, resizes, iconifications and maximizations back into the
 * declared state. Hosts the declared body as composable content.
 *
 * Exactly one of [onClose]/[rawListener] is set: the `onClose` overloads supply the controlled close
 * callback, which a listener installed here delivers, and the raw overloads supply the listener instance
 * directly.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun FrameNode(
    title: @Nls String,
    declared: DeclaredFrameState,
    controls: InternalFrameControls,
    noinline onClose: (() -> Unit)?,
    rawListener: InternalFrameListener?,
    modifier: SwingModifier,
    noinline content: @Composable () -> Unit,
) {
    // The onClose overload routes the close control through a listener that reads the declared callback
    // when the frame reports closing (the close operation stays do-nothing, so the frame is only closed
    // by being removed from the composition). The raw overload installs the supplied listener instance.
    val closeChannel =
        rawListener?.let { SwingModifier.internalFrameListener(it) }
            ?: SwingModifier.internalFrameListener(onFrameClosing = { onClose?.invoke() })
    // Read here, in the composition of the frame these values belong to: a hoisted state receives the
    // user's every move, resize, iconification and maximization, and reading it here is what keeps each of
    // those recomposing this one frame instead of the desktop and every frame standing on it.
    val declaredBounds = declared.bounds
    val declaredIconified = declared.iconified
    val declaredMaximized = declared.maximized

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
                title,
                controls.resizable,
                controls.closable,
                controls.maximizable,
                controls.iconifiable,
            ).apply {
                // A JInternalFrame is constructed hidden and the close control closes it on its own;
                // make it visible and leave the close control controlled by the declaration instead.
                bounds = declaredBounds
                // The placement is stamped like every later one (see the update block), so the
                // notifications it provokes cannot undo a move declared before they are delivered.
                applied.bounds = bounds
                defaultCloseOperation = JInternalFrame.DO_NOTHING_ON_CLOSE
                isVisible = true
            }
        },
        update = {
            set(title) { this.title = it }
            set(controls.closable) { this.isClosable = it }
            set(controls.resizable) { this.isResizable = it }
            set(controls.maximizable) { this.isMaximizable = it }
            set(controls.iconifiable) { this.isIconifiable = it }
            // The geometry is stamped before it is pushed, because a frame reports its moves and resizes
            // asynchronously: by the time such a notification is delivered the state may already hold a
            // newer value, and the frame - which still carries the older geometry - would hand that
            // older geometry back over it. The stamp is what tells the write-back that the geometry the
            // frame is reporting is the one this apply put there, so the newer declaration stands.
            update(declaredBounds) { value -> applyBounds(value, applied) }
            // A window-state transition takes the frame off the desktop or spreads it across the desktop,
            // either of which needs a desktop. This update block runs before the desktop takes the frame,
            // so the first application is left to [attachSync], which performs it the moment the desktop
            // does; by the time a later declaration arrives here the frame is attached and takes it
            // directly. Maximizing comes before iconifying, so a frame declared as both ends up an icon
            // that restores to the whole desktop, as both declarations ask.
            set(declaredMaximized) { value -> applyMaximized(value, applied, target.value) }
            set(declaredIconified) { value -> applyIconified(value, applied, target.value) }
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
            // The desktop attachment is declared last, so a frame stands on its desktop whatever placement
            // the caller's own modifier names.
            applyModifier(
                modifier
                    .then(stateChannels)
                    .then(closeChannel)
                    .slot(FRAME_REGION, InternalFrameAttachment),
            )
        },
        content = content,
    )
}

/**
 * The region of a `JDesktopPane` a frame fills, written as the call that declares one - see
 * [DesktopPaneScope.InternalFrame].
 */
private const val FRAME_REGION: String = "InternalFrame(...)"

/**
 * Hosts one [JInternalFrame] on the host [JDesktopPane]: adds it on install, at the position among the
 * desktop's frames its declaration order names, and detaches it by identity on uninstall so removing an
 * earlier frame never invalidates a later frame's uninstall.
 *
 * An iconified frame is added as the frame all the same: `DesktopManager` is what stands its icon on the
 * desktop in its place, and installing the icon here instead would leave the desktop holding no frame.
 *
 * An iconified frame stands on the desktop as its desktop icon, and the icon is placed wherever the look
 * and feel keeps it rather than beside the frame, so the uninstall takes the icon off whatever holds it
 * as well as the frame off the desktop. Otherwise a frame that leaves the composition while iconified
 * leaves its icon behind.
 */
private val InternalFrameAttachment =
    SlotAttachment { host, component, index ->
        val desktop =
            host as? JDesktopPane ?: error(wrongSlotHost(host, JDesktopPane::class.java, FRAME_REGION))
        val frame = component as JInternalFrame
        desktop.add(frame)
        desktop.setPosition(frame, index.coerceAtMost(desktop.getIndexOf(frame)))
        return@SlotAttachment {
            desktop.remove(component)
            val icon = frame.desktopIcon
            icon.parent?.remove(icon)
        }
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
