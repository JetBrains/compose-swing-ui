@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.components.SyntheticIds
import org.jetbrains.compose.swing.core.SlotAttachment
import org.jetbrains.compose.swing.core.SlotNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import java.awt.Rectangle
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
 * removing an `internalFrame(...)` adds or removes the matching frame, and a frame's title, controls,
 * and bounds update on recomposition.
 *
 * The close control is **controlled**: activating it invokes the frame's `onClose` rather than closing
 * the frame on its own. Remove the frame from the composition in response to actually close it.
 *
 * ```
 * DesktopPane {
 *     internalFrame(title = "Editor", bounds = Rectangle(0, 0, 300, 200)) { Editor() }
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
    // Collect the frame declarations fresh on every composition so a frame the caller stops declaring
    // (e.g. behind an `if`) drops out of `content`, and the applier uninstalls it via the slot
    // mechanism. A remembered, mutated scope would retain the stale declaration. Each declaration is
    // stamped with a synthetic id from a monotonic counter at declaration time.
    val scope = DesktopPaneScopeImpl().apply(block)
    val desktop = remember { JDesktopPane() }

    SwingNode(
        factory = { desktop },
        update = {
            applyModifier(modifier)
        },
        content = {
            scope.frames.forEach { frame ->
                // key() gives each frame a composition identity by its synthetic id rather than its
                // list position, so the frame keeps its slot — and the state inside it — even if the
                // surrounding declarations shift around it.
                key(frame.id) {
                    val attachment = remember(desktop) { internalFrameAttachment() }
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
 */
public interface DesktopPaneScope {
    /**
     * Declares one internal frame.
     *
     * @param title the text shown in the frame's title bar
     * @param bounds the frame's position and size within the desktop
     * @param controls which window controls the frame shows
     * @param onClose callback invoked when the user activates the frame's close control; remove the
     *   frame from the composition in response to actually close it
     * @param content the composable shown in the frame's body
     */
    public fun internalFrame(
        title: String,
        bounds: Rectangle,
        controls: InternalFrameControls = InternalFrameControls(),
        onClose: () -> Unit = {},
        content: @Composable () -> Unit,
    )

    /**
     * Declares one internal frame driven by a raw [InternalFrameListener] instead of an `onClose`
     * lambda. The [internalFrameListener] is attached as-is and removed on the same instance; pass a
     * stable instance (e.g. `remember {}`) to avoid churn. Use this overload to observe the full set
     * of internal-frame events (opened, closing, closed, iconified, activated); the close control
     * stays controlled, so remove the frame from the composition to actually close it.
     *
     * @param title the text shown in the frame's title bar
     * @param bounds the frame's position and size within the desktop
     * @param internalFrameListener the listener notified of the frame's window events
     * @param controls which window controls the frame shows
     * @param content the composable shown in the frame's body
     */
    public fun internalFrame(
        title: String,
        bounds: Rectangle,
        internalFrameListener: InternalFrameListener,
        controls: InternalFrameControls = InternalFrameControls(),
        content: @Composable () -> Unit,
    )
}

/** A frame's per-composition appearance snapshot: its title, bounds, and which window controls it shows. */
private class InternalFrameMetadata(
    val title: String,
    val bounds: Rectangle,
    val controls: InternalFrameControls,
)

/**
 * One declared internal frame: a synthetic [id] stable across recompositions, its [metadata] snapshot
 * for this composition, plus its body composable. Exactly one of [onClose]/[rawListener] is set: the
 * `onClose` overload supplies the controlled close callback (a stable adapter is built in
 * [InternalFrame]), the raw overload supplies the listener instance directly.
 */
private class InternalFrameDeclaration(
    val id: Int,
    val metadata: InternalFrameMetadata,
    val onClose: (() -> Unit)?,
    val rawListener: InternalFrameListener?,
    val content: @Composable () -> Unit,
)

private class DesktopPaneScopeImpl : DesktopPaneScope {
    val frames: MutableList<InternalFrameDeclaration> = ArrayList()

    private val ids = SyntheticIds()

    override fun internalFrame(
        title: String,
        bounds: Rectangle,
        controls: InternalFrameControls,
        onClose: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        addFrame(
            metadata = InternalFrameMetadata(title = title, bounds = bounds, controls = controls),
            content = content,
            onClose = onClose,
            rawListener = null,
        )
    }

    override fun internalFrame(
        title: String,
        bounds: Rectangle,
        internalFrameListener: InternalFrameListener,
        controls: InternalFrameControls,
        content: @Composable () -> Unit,
    ) {
        addFrame(
            metadata = InternalFrameMetadata(title = title, bounds = bounds, controls = controls),
            content = content,
            onClose = null,
            rawListener = internalFrameListener,
        )
    }

    private fun addFrame(
        metadata: InternalFrameMetadata,
        content: @Composable () -> Unit,
        onClose: (() -> Unit)?,
        rawListener: InternalFrameListener?,
    ) {
        frames.add(
            InternalFrameDeclaration(
                id = ids.next(),
                metadata = metadata,
                onClose = onClose,
                rawListener = rawListener,
                content = content,
            ),
        )
    }
}

/**
 * Hosts one [JInternalFrame] in [desktop]: adds it on install and detaches it by identity on uninstall
 * so removing an earlier frame never invalidates a later frame's uninstall.
 */
private fun internalFrameAttachment(): SlotAttachment =
    SlotAttachment { host, component, _ ->
        host as JDesktopPane
        host.add(component)
        return@SlotAttachment { host.remove(component) }
    }

/**
 * One `JInternalFrame` node: builds the frame visible, installs the declaration's window-event
 * handling, and applies its title/controls/bounds reactively. Hosts the declared body as composable
 * content.
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
                bounds = metadata.bounds
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
            update(metadata.bounds) { this.bounds = it }
            applyModifier(SwingModifier.internalFrameListener(listener))
        },
        content = { frame.content() },
    )
}
