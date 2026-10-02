package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import java.awt.Frame
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent

/**
 * Publishes the [LifecycleOwner] the content around this call reads as what [component] carries, so a root
 * mounted at or under it that inherits nothing resolves the same owner. That is what the publish is for:
 * independent subtrees, each driven by a recomposer of its own, standing in one window and sharing
 * one lifecycle. A root nested in another's composition inherits the owner instead and publishes nothing.
 *
 * The owner is read from inside the provision rather than resolved again, so what is published is what the
 * content reads, whether this root stated it or took it from above.
 *
 * The publish goes up after the composition applies, so the walk a root runs as it composes cannot read
 * back what its own root is about to leave.
 *
 * Disposal puts back the owner the host carried before, unless someone else has set one since.
 */
@Composable
internal fun PublishLifecycleOwner(component: Component) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(component, owner) {
        val host = component as? JComponent
        val previous = host?.get(LIFECYCLE_OWNER_KEY)
        host?.setLifecycleOwner(owner)
        onDispose {
            if (host != null && host[LIFECYCLE_OWNER_KEY] === owner) {
                host.setLifecycleOwner(previous)
            }
        }
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
 *
 * @param owner the [LifecycleOwner] to set, or `null` to clear the one set here.
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
 * The walk runs as the resolution is built, so it runs before a minted owner is published: a root that minted its
 * owner never reads what it published back and takes it for one it found above itself, which would leave that owner
 * with nobody to end it.
 *
 * A root drives only the owner it minted, so an owner it resolved from elsewhere is neither installed a second
 * time nor ended as this root leaves the composition - it belongs to the root that minted it and outlives every
 * root that reads it.
 */
internal class LifecycleOwnerResolution(
    private val component: Component,
) {
    /** The owner the root's content reads. */
    val owner: LifecycleOwner

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

    /** Whether the root minted its owner, and so has to be told where its component goes. */
    val drives: Boolean
        get() = minted != null

    /** Starts the minted owner following its component's window; a resolved owner is already followed. */
    fun install() {
        minted?.install()
    }

    /** Re-reads the window the minted owner follows; told of the events matching [LIFECYCLE_PLACE_CHANGE_FLAGS]. */
    fun placeChanged() {
        minted?.syncWindow()
    }

    /**
     * Ends the minted owner and removes what it installed; a resolved owner is left to the root that minted it.
     * What an observer of the owner throws as it ends propagates, as it does from [LifecycleRegistry]; a failure
     * of the library's own cleanup is thrown too, see [ComponentLifecycleOwner.destroy]. The component stops
     * publishing the minted owner, which a failed disposal of the content leaves published; a listener of the
     * host throwing as it stops does not keep the owner from ending.
     */
    fun end() {
        runEveryStep(
            {
                if (minted != null && component is JComponent && component[LIFECYCLE_OWNER_KEY] === minted) {
                    component.setLifecycleOwner(null)
                }
            },
            { minted?.destroy() },
        )
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
internal class ComponentLifecycleOwner(
    private val component: Component,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    /** The window whose focus and minimization the state is read from; `null` while detached. */
    private var window: Window? = component.ownerWindowOrNull()

    private var destroyed = false

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
     * Installs the window listeners. Called once, by the root that minted the owner, as that root's content
     * enters the composition; whoever calls it tells [syncWindow] of each event matching
     * [LIFECYCLE_PLACE_CHANGE_FLAGS] on [component].
     */
    fun install() {
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
    fun syncWindow() {
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
        runEveryStep(
            { target.removeWindowListener(windowListener) },
            { target.removeWindowFocusListener(windowListener) },
        )
    }

    /**
     * Ends the owner at [Lifecycle.State.DESTROYED] and removes every listener it installed. Idempotent:
     * an event that still reaches a destroyed owner resolves to the state it is already in, and an owner
     * that was minted but never installed - one whose root inherited an owner instead - ends with no
     * listener to give up.
     *
     * The owner reaches [Lifecycle.State.DESTROYED] even when removing a listener throws, and that failure is
     * thrown after it. What an observer throws propagates, as it does from [LifecycleRegistry].
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        try {
            window?.let(::stopListeningTo)
        } finally {
            window = null
            updateState()
        }
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
 * Swing has given it a native peer: the flags that put it under another window, [PLACE_CHANGE_FLAGS], and a
 * change of its displayability. Together they are the whole of what attachment and the owning window turn on.
 */
internal const val LIFECYCLE_PLACE_CHANGE_FLAGS: Long =
    PLACE_CHANGE_FLAGS or HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()

/**
 * Minimization is a [Frame]'s iconified state; a window of any other kind is always shown at full
 * size, and a component in no window at all is answered by its attachment.
 */
private fun Window?.isMinimized(): Boolean {
    val frame = this as? Frame ?: return false
    return frame.extendedState and Frame.ICONIFIED != 0
}
