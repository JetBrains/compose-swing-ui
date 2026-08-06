package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.DisposableHandle
import java.awt.Component
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.SwingUtilities

/**
 * The top-level [Window] this component is in; a [Window] is its own.
 *
 * The receiver is answered before the ancestor walk because an owned window's Swing parent is the
 * window that owns it: a dialog asked for the window it is in is in itself, not in the frame it hangs
 * off.
 */
internal fun Component.ownerWindowOrNull(): Window? = this as? Window ?: SwingUtilities.getWindowAncestor(this)

/**
 * The composition a mount nests into: the [context] its content composes under, and the [window] whose
 * shared composition that context is.
 *
 * [window] is `null` for a context published by a host composition, which hands the window down through
 * its own [androidx.compose.runtime.CompositionLocal]s instead.
 */
internal class MountParent(
    val context: CompositionContext,
    val window: Window?,
)

/**
 * Resolves what a `setContent` on [component] with no parent named should nest into **right now**, or
 * `null` if it cannot be resolved yet (the container has no host stamp and no window ancestor, so the
 * mount must be deferred until [component] takes a place that answers).
 *
 * Resolution order: first an existing host discovered self-first up the Swing tree, then the owning
 * window's shared recomposer. Those two are the whole order: a runtime created for a component is
 * reached only by passing its context in, so every island a window can account for shares that window's
 * one recomposer and one frame clock.
 */
internal fun resolveParentOrNull(component: Component): MountParent? {
    val window = component.ownerWindowOrNull()
    val host = component.findParentCompositionContext()
    return when {
        // A window stamps its own shared scope on its root pane, so the walk reaches a window's context
        // as readily as a host composition's; which of the two it found is what names the window.
        host != null -> MountParent(host, window.takeIf { it?.recomposerOrNull()?.recomposer === host })

        window != null -> MountParent(window.getOrCreateRecomposer().recomposer, window)

        else -> null
    }
}

/**
 * Mounts a `setContent` on [component] as soon as the context it nests into can be resolved, and
 * returns a [DisposableHandle] over the (possibly still pending) mount.
 *
 * If the context resolves immediately (a host stamp self-first up the tree, or the owning window's
 * recomposer), [mount] runs synchronously here. Otherwise the mount is **deferred** until [component]
 * takes a place in the Swing tree that resolves one, at which point [mount] runs.
 *
 * The returned handle is idempotent: disposing before the mount removes the listener and mounts
 * nothing; disposing after it disposes the mount.
 */
internal fun mountWhenParentResolves(
    component: Component,
    mount: (MountParent, State<Window?>) -> SwingCompositionMount,
): DisposableHandle = SetContentMountState(component, namedParent = null, mount).also { it.start() }

/**
 * Mounts a `setContent` on [component] under the [namedParent] its caller supplied, composing on the
 * call, and returns a [DisposableHandle] over that mount.
 *
 * The handle is idempotent and disposes what the mount owns: this island's composition, never
 * [namedParent].
 */
internal fun mountUnderNamedParent(
    component: Component,
    namedParent: CompositionContext,
    mount: (MountParent, State<Window?>) -> SwingCompositionMount,
): DisposableHandle =
    SetContentMountState(
        component,
        MountParent(namedParent, windowOwning(namedParent)),
        mount,
    ).also { it.start() }

/**
 * The phase a `setContent` mount is in, whichever way it comes by the parent it composes under.
 *
 * A mount is exactly one of these at any time:
 *  - [Pending] - nothing composed yet, the parent still to come;
 *  - [Mounted] - a parent is known and the composition is live;
 *  - [Disposed] - torn down; terminal, reached at most once.
 *
 * Modeling the phase explicitly (rather than a `disposed` flag plus the implicit
 * "composition != null => mounted" invariant) lets the transitions reject illegal moves
 * structurally - e.g. a hierarchy event that fires after disposal cannot compose anything.
 */
private enum class MountPhase { Pending, Mounted, Disposed }

/**
 * The lifecycle state machine backing a `setContent` mount, whichever way it comes by the parent it
 * composes under: a caller who names one, or the container's own place in the Swing tree.
 *
 * A mount watches the window its container is in, and composes the content again - under the
 * composition that window shares - once the container ends up in another one. The window a mount is in
 * is the container's own, or, while the container hangs off none, the window whose shared composition
 * the content was given to compose under. Every other move leaves the composition exactly where it is:
 * a container in no window has arrived nowhere, so one taken out of a window on its way to another is
 * not torn down midway, and one that arrives in the window it was already in - the place a cell
 * renderer takes to be painted - has not moved at all. A mount that stood in no window at all adopts the
 * first one it reaches without composing again.
 *
 * Lives only on the EDT, so its fields need no synchronization. All phase transitions go through this
 * object, so no caller can drive it into an illegal state, and [dispose] leaves it [MountPhase.Disposed]
 * with no listener installed.
 *
 * @param component the container the content is composed into, and whose place in the tree is watched.
 * @param namedParent the context the caller named for the content to compose under, or `null` to
 *   resolve one from where [component] hangs in the Swing tree.
 * @param mount creates and starts the [SwingCompositionMount] under a parent context.
 */
private class SetContentMountState(
    private val component: Component,
    private val namedParent: MountParent?,
    private val mount: (MountParent, State<Window?>) -> SwingCompositionMount,
) : DisposableHandle {
    private var phase = MountPhase.Pending

    /** The live composition; held only while [MountPhase.Mounted]. */
    private var composition: SwingCompositionMount? = null

    /**
     * The window this mount states to its content, and its record of which window it is in: the
     * container's own, or the window whose shared composition the content was given while the container
     * hangs off none. Content that inherited a window from the composition it joined reads that one
     * instead. Observable, so a mount that could name no window states the first one it reaches without
     * the content being composed again.
     */
    private val statedWindow = mutableStateOf<Window?>(null)

    /** Handle removing the listener watching [component]'s place; held until [dispose]. */
    private var placeChanges: DisposableHandle? = null

    /**
     * Watches where [component] hangs and composes the content under the parent there is now: the one
     * the caller named, or the one [component]'s place resolves to. A container whose place resolves to
     * nothing composes nothing yet and waits for one that does.
     */
    fun start() {
        placeChanges = onPlaceChanged(component, PARENT_CHANGE_FLAG) { placeChanged() }
        val parent = namedParent ?: resolveParentOrNull(component)
        if (parent != null) composeUnder(parent)
    }

    /**
     * Takes [component]'s new place in the Swing tree: a mount still waiting for a parent retries the
     * resolution, a live one follows the window it is in now, and a disposed one composes nothing.
     */
    private fun placeChanged() {
        when (phase) {
            MountPhase.Pending -> resolveParentOrNull(component)?.let(::composeUnder)
            MountPhase.Mounted -> followWindow()
            MountPhase.Disposed -> Unit
        }
    }

    /** Composes the content under [parent], and records which window that leaves this mount in. */
    private fun composeUnder(parent: MountParent) {
        // Stated before the mount, so content standing under a window has it on its first pass. The
        // container's own window comes first: a parent published by a host composition names none, and a
        // container already hanging in a window would otherwise fall back on nothing, with no later move
        // to adopt one on.
        statedWindow.value = component.ownerWindowOrNull() ?: parent.window
        composition = mount(parent, statedWindow)
        phase = MountPhase.Mounted
    }

    /**
     * Follows [component] into the window its place is in now.
     *
     * A mount already standing in another window composes its content again under what that place resolves
     * to: a live composition's parent context is fixed at construction, so composing again is what joins
     * the window the container is now in and keeps its content recomposing with the rest of that window.
     *
     * A mount standing in no window adopts the one it arrives in instead, having no window's composition
     * to leave. Content given a caller's own composition to compose under - a page built before it is
     * added anywhere - stands under no window until its container reaches one. The composition it was
     * given is the one its caller chose, so it is published the window rather than composed again under
     * another: the page reads the window it is in and keeps everything it remembered.
     */
    private fun followWindow() {
        val arrivedIn = component.ownerWindowOrNull() ?: return
        val mountedIn = statedWindow.value
        when {
            mountedIn == null -> {
                statedWindow.value = arrivedIn
            }

            mountedIn !== arrivedIn -> {
                val resolved = resolveParentOrNull(component) ?: return
                composition?.dispose()
                statedWindow.value = arrivedIn
                composition = mount(resolved, statedWindow)
            }
        }
    }

    override fun dispose() {
        if (phase == MountPhase.Disposed) return
        phase = MountPhase.Disposed
        placeChanges?.dispose()
        placeChanges = null
        composition?.dispose()
        composition = null
        statedWindow.value = null
    }
}

/**
 * Runs [block] on every [HierarchyEvent] fired for [component] whose change flags intersect
 * [changeFlags], for as long as the returned handle is live. Shared by every watch of where a
 * component stands in the Swing tree, so which flags answer that question is named once at each call
 * site rather than the listener plumbing repeated around a different answer.
 *
 * @return a [DisposableHandle] that removes the listener. Disposing is idempotent.
 */
internal fun onPlaceChanged(
    component: Component,
    changeFlags: Long,
    block: () -> Unit,
): DisposableHandle {
    val listener =
        HierarchyListener { event ->
            if (event.changeFlags and changeFlags != 0L) block()
        }
    component.addHierarchyListener(listener)
    return DisposableHandle { component.removeHierarchyListener(listener) }
}

/**
 * The only [HierarchyEvent] change flag that can put a [Component] under a different window:
 * [HierarchyEvent.PARENT_CHANGED]. Which window a component is in is a question about the Swing tree
 * alone, so a parent change is the whole of what a mount has to watch for.
 */
private const val PARENT_CHANGE_FLAG: Long = HierarchyEvent.PARENT_CHANGED.toLong()
