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
 * Resolution order: first an existing host discovered up the Swing tree - a stamped host composition, or
 * an island standing over [component] - then the owning window's shared recomposer. Those two are the
 * whole order: a runtime created for a component is reached only by passing its context in, so every
 * island a window can account for shares that window's one recomposer and one frame clock.
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
 * If the context resolves immediately (a host stamp or an island up the tree, or the owning window's
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
 * A live mount publishes the context its content composes under to whatever is mounted inside its
 * container, through the [IslandMountRegistration] it keeps there.
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

    /**
     * This mount's registration on [component]: the listener watching where the container hangs, and the
     * context it publishes to whatever is mounted inside that container. Installed by [start] and removed
     * by [dispose].
     */
    private val registration = IslandMountRegistration { placeChanged() }

    /**
     * Set while a rejoin is queued, so the several parent changes one move fires queue at most one.
     */
    private var rejoinScheduled = false

    /**
     * Watches where [component] hangs and composes the content under the parent there is now: the one
     * the caller named, or the one [component]'s place resolves to. A container whose place resolves to
     * nothing composes nothing yet and waits for one that does.
     *
     * A container already carrying a live island is refused. Only a live one stands in the way: a
     * container whose island was disposed takes content again, and one whose mount is still waiting for
     * a parent answers nothing yet.
     */
    fun start() {
        check(component.islandCompositionContextOrNull() == null) {
            "${component.javaClass.name} already holds the content of a live setContent. A container is " +
                "asked once for the composition its contents nest into, and two islands cannot both be " +
                "that answer - dispose the first island's handle before setting content here again."
        }
        component.addHierarchyListener(registration)
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
        // Published before the mount for the same reason, and the same reason the applier stamps a node
        // on its way down: the content composes inside mount(), so a setContent that pass makes on a
        // container of this island has to find this context already standing.
        registration.context = parent.context
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
                // Withdrawn on the spot: AWT delivers a parent change to a container's descendants before
                // the container itself, so an island nested inside this one is asked where it hangs while
                // this mount still names the composition it is leaving. Answering nothing sends that walk
                // on to where it really hangs now, and the rejoin publishes this island's new answer.
                registration.context = null
                scheduleRejoin()
            }
        }
    }

    /** Queues [rejoin] behind the event being dispatched, at most once at a time. */
    private fun scheduleRejoin() {
        if (rejoinScheduled) return
        rejoinScheduled = true
        SwingUtilities.invokeLater(::rejoin)
    }

    /** Composes the content again under what [component]'s place resolves to now. */
    private fun rejoin() {
        // Cleared first: a move made while this one runs must queue one of its own.
        rejoinScheduled = false
        parentToRejoin()?.let { parent ->
            composition?.dispose()
            composeUnder(parent)
        }
    }

    /**
     * The parent [component] is to compose again under, or `null` where there is nothing to compose
     * again. Where the container stands is read here rather than carried over from when the rejoin was
     * queued: it may since have been disposed, taken out of every window, or put back in the one it was
     * already in.
     */
    private fun parentToRejoin(): MountParent? {
        val arrivedIn = component.ownerWindowOrNull()
        val moved = phase == MountPhase.Mounted && arrivedIn != null && arrivedIn !== statedWindow.value
        return if (moved) resolveParentOrNull(component) else null
    }

    override fun dispose() {
        if (phase == MountPhase.Disposed) return
        phase = MountPhase.Disposed
        component.removeHierarchyListener(registration)
        registration.context = null
        composition?.dispose()
        composition = null
        statedWindow.value = null
    }
}

/**
 * The registration a `setContent` mount keeps on its container: the [HierarchyListener] following where
 * that container hangs, carrying the [CompositionContext] the mount's content composes under.
 *
 * A mount is asked what it composes under through the registration it already keeps, the way a window is
 * asked for its runtime through the listener that ends it - so what a container's content nests into is
 * answered by the container itself rather than tracked anywhere else, removing the listener is the whole
 * of withdrawing the answer, and a container that is garbage collected takes its mounts with it. The
 * registration also reaches a [java.awt.Container] that is no [javax.swing.JComponent], which carries no
 * client-property bag at all.
 *
 * [context] is `null` while the mount is still waiting for a parent, and again once it is disposed.
 *
 * @param onPlaceChanged run whenever [HierarchyEvent.PARENT_CHANGED] fires for the container.
 */
private class IslandMountRegistration(
    private val onPlaceChanged: () -> Unit,
) : HierarchyListener {
    var context: CompositionContext? = null

    override fun hierarchyChanged(event: HierarchyEvent) {
        if (event.changeFlags and PARENT_CHANGE_FLAG != 0L) onPlaceChanged()
    }
}

/**
 * The [CompositionContext] the content of a live `setContent` island on this component composes under,
 * or `null` where this component carries no island that has composed yet.
 */
internal fun Component.islandCompositionContextOrNull(): CompositionContext? =
    hierarchyListeners.firstNotNullOfOrNull { (it as? IslandMountRegistration)?.context }

/**
 * Runs [block] on every [HierarchyEvent] fired for [component] whose change flags intersect
 * [changeFlags], for as long as the returned handle is live, so which flags answer where a component
 * stands is named at the call site rather than written into a listener of its own.
 *
 * The listener it installs is anonymous, so this serves a watch that only needs the callback. A watch
 * whose listener must be found again on the component - to be asked what it holds, rather than only to
 * run - installs a named one instead.
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
