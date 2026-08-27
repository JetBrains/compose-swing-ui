package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import java.awt.Component
import java.awt.Frame
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent

/**
 * The [LifecycleOwner] belonging to [component]: the one a root at or above it stamped on the Swing
 * tree, and a freshly minted one following [component] where that walk answers nothing.
 *
 * Only a minted owner is driven by this root and ended at [Lifecycle.State.DESTROYED] as the content
 * leaves the composition. Whether the content reads this owner or one provided above it is the caller's
 * to state.
 */
@Composable
internal fun rememberLifecycleOwner(component: Component): LifecycleOwner =
    remember(component) { LifecycleOwnerResolution(component) }.owner

/**
 * Publishes the [LifecycleOwner] the content around this call reads as [component]'s stamp, so a root
 * mounted at or under it that inherits nothing resolves the same owner. That is what the stamp is for:
 * independent subtrees, each under a runtime of its own, standing in one window and sharing one
 * lifecycle. A root nested in another's composition inherits the owner instead and needs no stamp.
 *
 * The owner is read from inside the provision rather than resolved again, so what is stamped is what the
 * content reads, whether this root stated it or took it from above.
 *
 * The stamp goes up after the composition applies, so the walk a root runs as it composes cannot read
 * back the stamp its own root is about to leave.
 */
@Composable
internal fun PublishLifecycleOwner(component: Component) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(component, owner) {
        val host = component as? JComponent
        host?.setLifecycleOwner(owner)
        onDispose { host?.setLifecycleOwner(null) }
    }
}

/**
 * Sets [owner] as the [LifecycleOwner] that composition roots mounted at or under this component read,
 * in place of one following the component itself, and clears it again when passed `null`.
 *
 * A root resolves an owner in this order: the one the composition it joins carries, then the one this
 * sets, and only where neither answers does it mint an owner following its own container. So a host
 * setting one here decides the lifecycle of every root under it that inherits nothing - which is what a
 * test harness needs, and what a host driving its content's lifecycle itself needs.
 *
 * The owner set here is the caller's: nothing clears it when the content under it leaves, and a root
 * that resolves it neither drives it nor ends it. Clear it from the same teardown that ends it.
 *
 * Marked [InternalSwingUiApi]; it may change or be removed without notice in any release.
 */
@InternalSwingUiApi
public fun JComponent.setLifecycleOwner(owner: LifecycleOwner?) {
    this[LIFECYCLE_OWNER_KEY] = owner
}

/**
 * Client property key under which the [LifecycleOwner] a composition root states is stored, so a root
 * mounted under that one finds the owner the Swing tree around it stands under.
 */
private val LIFECYCLE_OWNER_KEY: Key<LifecycleOwner> = Key("org.jetbrains.compose.swing.lifecycleOwner")

/**
 * Finds the [LifecycleOwner] of the composition root this component stands under, by walking the Swing
 * component tree and reading the [LIFECYCLE_OWNER_KEY] client property off each [JComponent]. It answers
 * `null` where nothing on the way up carries one - for a component in no window, and for a top-level
 * window's content pane, whose ancestors end at a [java.awt.Window] that is no [JComponent].
 *
 * The walk is self-first, as [findParentCompositionContext] is: a component that already carries a live
 * root's owner answers with that owner rather than skipping to the one above it, so a second root
 * mounted on the same component joins the lifecycle of the first instead of minting a rival for it.
 */
@InternalSwingUiApi
public fun Component.findLifecycleOwner(): LifecycleOwner? {
    var current: Component? = this
    while (current != null) {
        if (current is JComponent) {
            current[LIFECYCLE_OWNER_KEY]?.let { return it }
        }
        current = current.parent
    }
    return null
}

/**
 * The [LifecycleOwner] one composition root stands under, and how far that root's ownership of it reaches.
 *
 * The walk runs as the resolution is built and the resolution is remembered against [component] alone, so it runs
 * before a minted owner is published: a root that minted its owner never reads its own stamp back and takes it for
 * one it found above itself, which would leave that owner with nobody to end it.
 *
 * The resolution is what the root remembers, rather than the owner itself: a root drives only the owner it minted,
 * so an owner it resolved from elsewhere is neither installed a second time nor ended as this root leaves the
 * composition - it belongs to the root that minted it and outlives every root that reads it.
 */
private class LifecycleOwnerResolution(
    component: Component,
) : RememberObserver {
    /** The owner the root's content reads. */
    val owner: LifecycleOwner

    /** The owner this root minted and drives; `null` where the root reads an owner it did not mint. */
    private val minted: ComponentLifecycleOwner?

    init {
        val resolved = component.findLifecycleOwner()
        if (resolved != null) {
            owner = resolved
            minted = null
        } else {
            val own = ComponentLifecycleOwner(component)
            owner = own
            minted = own
        }
    }

    override fun onRemembered() {
        minted?.install()
    }

    override fun onForgotten() {
        minted?.destroy()
    }

    override fun onAbandoned() {
        minted?.destroy()
    }
}

/**
 * The [LifecycleOwner] of one composition root, driven by where [component] stands in the Swing tree.
 *
 * Three facts decide the state: whether Swing has given [component] a native peer, whether the window
 * it is in is minimized, and whether that window is focused. Detached or minimized is
 * [Lifecycle.State.CREATED], attached to an unminimized window that holds the focus is
 * [Lifecycle.State.RESUMED], and attached without the focus is [Lifecycle.State.STARTED].
 *
 * Attachment is the peer, not visibility: a window packed but never shown is attached, and so is one
 * whose content is hidden behind another. This is the rule Compose Multiplatform's own desktop target
 * applies, where the same three facts decide the same three states.
 *
 * The owner opens on the state its component already stands in, so content reads where it is the
 * first time it composes, and a root mounted while detached opens at [Lifecycle.State.CREATED] - the
 * state an attachment then moves it forward from. [Lifecycle.State.DESTROYED] is terminal and reached
 * once, when the root that minted the owner leaves the composition.
 *
 * Lives only on the Event Dispatch Thread - composition, the AWT listeners it installs, and the
 * registry it drives all run there - so its fields need no synchronization.
 */
private class ComponentLifecycleOwner(
    private val component: Component,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    /** The window whose focus and minimization the state is read from; `null` while detached. */
    private var window: Window? = component.ownerWindowOrNull()

    private var destroyed = false

    /** Handle removing the listener watching [component]'s place; held from [install] to [destroy]. */
    private var placeChanges: DisposableHandle? = null

    private val windowListener =
        object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent) = updateState()

            override fun windowDeiconified(e: WindowEvent) = updateState()

            override fun windowGainedFocus(e: WindowEvent) = updateState()

            override fun windowLostFocus(e: WindowEvent) = updateState()
        }

    init {
        updateState()
    }

    /**
     * Installs the listeners that follow [component]'s place. Called once, by the root that minted the
     * owner, as that root's content enters the composition.
     */
    fun install() {
        placeChanges = onPlaceChanged(component, PLACE_CHANGE_FLAGS) { syncWindow() }
        window?.let(::listenTo)
        // The component may have taken another place between the owner being built and the
        // composition applying; this reconciles the window the listeners were installed on.
        syncWindow()
    }

    /**
     * Re-resolves the window [component] is in, moves the window listeners onto it, and updates the
     * state. A component is moved between containers like any other, so the window whose focus and
     * minimization the state follows is whichever one the component is in now.
     *
     * A destroyed owner has already given up its listeners; AWT can still deliver the hierarchy event
     * that destroyed it to this method afterward, and re-installing listeners here would arm a window
     * the owner will never stop listening to.
     */
    private fun syncWindow() {
        if (destroyed) return
        val resolved = component.ownerWindowOrNull()
        if (resolved !== window) {
            window?.let(::stopListeningTo)
            window = resolved
            resolved?.let(::listenTo)
        }
        updateState()
    }

    private fun listenTo(target: Window) {
        target.addWindowListener(windowListener)
        target.addWindowFocusListener(windowListener)
    }

    private fun stopListeningTo(target: Window) {
        target.removeWindowListener(windowListener)
        target.removeWindowFocusListener(windowListener)
    }

    /**
     * Ends the owner at [Lifecycle.State.DESTROYED] and removes every listener it installed. Idempotent:
     * an event that still reaches a destroyed owner resolves to the state it is already in, and an owner
     * that was minted but never installed - one whose root inherited an owner instead - ends with no
     * listener to give up.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        placeChanges?.dispose()
        placeChanges = null
        window?.let(::stopListeningTo)
        window = null
        updateState()
    }

    private fun updateState() {
        registry.currentState =
            when {
                destroyed -> Lifecycle.State.DESTROYED
                !component.isDisplayable || window.isMinimized() -> Lifecycle.State.CREATED
                window?.isFocused == true -> Lifecycle.State.RESUMED
                else -> Lifecycle.State.STARTED
            }
    }
}

/**
 * The [HierarchyEvent] change flags that can move a component into another window or change whether
 * Swing has given it a native peer. Together they are the whole of what attachment and the owning
 * window turn on.
 */
private const val PLACE_CHANGE_FLAGS: Long =
    (HierarchyEvent.PARENT_CHANGED or HierarchyEvent.DISPLAYABILITY_CHANGED).toLong()

/**
 * Minimization is a [Frame]'s iconified state; a window of any other kind is always shown at full
 * size, and a component in no window at all is answered by its attachment.
 */
private fun Window?.isMinimized(): Boolean {
    val frame = this as? Frame ?: return false
    return frame.extendedState and Frame.ICONIFIED != 0
}
