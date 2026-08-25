package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
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
 * `null` if it cannot be resolved yet - the container has no host stamp and no window ancestor, so the
 * mount must be deferred until it takes a place that answers.
 *
 * Resolution order: first an existing host discovered up the Swing tree - a stamped host composition, or
 * content composed over [component] - then the owning window's shared recomposer. Those two are the
 * whole order: a recomposer created for a component is reached only by passing its context in, so every
 * content composition a window can account for shares that window's one recomposer and one frame clock.
 */
internal fun resolveParentOrNull(component: Component): MountParent? {
    val window = component.ownerWindowOrNull()
    val host = component.findParentCompositionContext()
    return when {
        // A window stamps its own shared scope on its root pane, so the walk reaches a window's context
        // as readily as a host composition's; which of the two it found is what names the window.
        host != null -> MountParent(host, window.takeIf { it?.swingRecomposerOrNull()?.recomposer === host })

        window != null -> MountParent(window.getOrCreateRecomposer().recomposer, window)

        else -> null
    }
}

/**
 * Mounts a `setContent` on [component] as soon as the context it nests into can be resolved, and
 * returns a [DisposableHandle] over the (possibly still pending) mount.
 *
 * If the context resolves immediately (a host stamp or content composed up the tree, or the owning
 * window's recomposer), [compose] runs synchronously here. Otherwise the mount is **deferred** until
 * [component] takes a place in the Swing tree that resolves one, at which point [compose] runs.
 *
 * The returned handle is idempotent: disposing before the mount removes the listener and mounts
 * nothing; disposing after it disposes the mount.
 */
internal fun mountWhenParentResolves(
    component: Component,
    compose: (MountParent, State<Window?>) -> SwingContentComposition,
): DisposableHandle =
    SetContentMountState(component, standsOnItsOwnRecomposer = false, compose).also { it.start(namedParent = null) }

/**
 * Mounts a `setContent` on [component] under the [namedParent] its caller supplied, composing on the
 * call, and returns a [DisposableHandle] over that mount.
 *
 * The handle is idempotent and disposes what the mount owns: this content's composition, never
 * [namedParent].
 */
internal fun mountUnderNamedParent(
    component: Component,
    namedParent: CompositionContext,
    compose: (MountParent, State<Window?>) -> SwingContentComposition,
): DisposableHandle {
    check(!namedParent.hasEnded) {
        "The composition context named for ${component.javaClass.name} belongs to a recomposer that " +
            "has ended, so content composed under it would never recompose. Name a live one, or name no " +
            "parent at all to join the composition this container's own place resolves to."
    }
    val parent = MountParent(namedParent, windowOwning(namedParent))
    // A recomposer built for a component is a Recomposer itself, and is owned by no window. A context taken
    // from inside a live composition is that composition's own child and is torn down with it, and a
    // window's recomposer ends with its window, so content named either of those follows windows like any
    // other.
    val standsOnItsOwnRecomposer = namedParent is Recomposer && parent.window == null
    return SetContentMountState(component, standsOnItsOwnRecomposer, compose).also { it.start(parent) }
}

/**
 * Whether this context is a recomposer that has been cancelled, so nothing composed under it would
 * recompose again.
 *
 * Only a [Recomposer] answers anything here. A context published by a live composition is that
 * composition's own child and carries no state of its own.
 *
 * A cancelled recomposer reports [ShuttingDown][Recomposer.State.ShuttingDown] or
 * [ShutDown][Recomposer.State.ShutDown], reached by cancelling or by its effect job completing, whether
 * or not it ever recomposed. A recomposer built but never started is
 * [Inactive][Recomposer.State.Inactive], which is a live one.
 *
 * The states are named rather than compared by order, so a state added between them cannot change what
 * this answers.
 */
private val CompositionContext.hasEnded: Boolean
    get() =
        when ((this as? Recomposer)?.currentState?.value) {
            Recomposer.State.ShutDown, Recomposer.State.ShuttingDown -> true
            else -> false
        }

/**
 * The phase a `setContent` mount is in.
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
 * container, through the [ContentCompositionRegistration] it keeps there.
 *
 * Lives only on the EDT, so its fields need no synchronization. All phase transitions go through this
 * object, so no caller can drive it into an illegal state, and [dispose] leaves it [MountPhase.Disposed]
 * with no listener installed.
 *
 * @param component the container the content is composed into, and whose place in the tree is watched.
 * @param standsOnItsOwnRecomposer whether the content composes on a recomposer of its own rather than
 *   inside another composition. Fixed for the life of the mount, because it states what the caller named
 *   rather than where the container hangs.
 * @param compose creates and starts the [SwingContentComposition] under a parent context.
 */
private class SetContentMountState(
    private val component: Component,
    private val standsOnItsOwnRecomposer: Boolean,
    private val compose: (MountParent, State<Window?>) -> SwingContentComposition,
) : DisposableHandle {
    private var phase = MountPhase.Pending

    /** The live composition; held only while [MountPhase.Mounted]. */
    private var composition: SwingContentComposition? = null

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
    private val registration = ContentCompositionRegistration(this) { placeChanged() }

    /**
     * The window recomposer this mount is registered with while its content composes under it, or `null`
     * while it composes under no window's; see [SwingRecomposer.registerContentComposition].
     */
    private var registeredRecomposer: SwingRecomposer? = null

    private var parentContext: CompositionContext? = null

    /**
     * Set while a rejoin is queued, so the several parent changes one move fires queue at most one.
     */
    private var rejoinScheduled = false

    /**
     * Watches where [component] hangs and composes the content under the parent there is now: the one
     * the caller named, or the one [component]'s place resolves to. A container whose place resolves to
     * nothing composes nothing yet and waits for one that does.
     *
     * A container already carrying a live mount is refused, whether that mount has composed or is still
     * waiting for a parent: a pending mount composes the moment the container reaches a place that
     * resolves one, so accepting a second would compose two of them into the container then. Presence is
     * judged by the registration a mount keeps installed from [start] to [dispose] rather than by the
     * context it publishes, which a pending mount does not carry yet and a live one withdraws mid-move.
     * A container whose content composition was disposed takes content again.
     *
     * @param namedParent the parent the caller named, or `null` to resolve one from where [component]
     *   hangs in the Swing tree. Taken as a parameter rather than held as a field, so neither the
     *   context nor the window it names outlives the call that uses them.
     */
    fun start(namedParent: MountParent?) {
        check(component.contentCompositionRegistrationOrNull == null) {
            "${component.javaClass.name} already carries a live setContent, whether or not its content " +
                "has composed yet. A container is asked once for the composition its contents nest into, " +
                "and two content compositions cannot both be that answer - dispose the first one's " +
                "handle before setting content here again."
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
        // on its way down: the content composes inside compose(), so a setContent that pass makes on a
        // container of this content has to find this context already standing.
        this.parentContext = parent.context
        registration.context = parent.context
        composition = compose(parent, statedWindow)
        phase = MountPhase.Mounted
        // The recomposer the content composes under: the window whose own context the parent is, or, for
        // a parent published by a host composition, the recomposer of the window the container hangs in.
        // Content standing on a recomposer of its own registers with none: its caller owns that one, so
        // no window's carries this content. A window whose tree its container stands in still ends
        // it when that window closes, reached by the walk over that tree - the peers the content is
        // composed into are being destroyed with the window.
        if (!standsOnItsOwnRecomposer) {
            registerWith((parent.window ?: component.ownerWindowOrNull())?.swingRecomposerOrNull())
        }
    }

    /**
     * Moves this content composition's registration to [recomposer]; composing again under the same one
     * keeps it.
     */
    private fun registerWith(recomposer: SwingRecomposer?) {
        if (registeredRecomposer === recomposer) return
        registeredRecomposer?.deregisterContentComposition(this)
        // Recorded before registering: a disposed recomposer refuses a registration by disposing this
        // content on the spot, and that disposal must not leave a record of the refused one behind.
        registeredRecomposer = recomposer
        recomposer?.registerContentComposition(this)
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
            // Such a recomposer serves a composition standing outside any window at all, so the window a
            // container hangs in is something its content reads, never what decides which composition it
            // belongs to. Only the window it reads is brought up to date.
            standsOnItsOwnRecomposer -> {
                statedWindow.value = arrivedIn
            }

            mountedIn == null -> {
                statedWindow.value = arrivedIn
                // Adopting keeps the composition rather than composing again, so this is the one
                // move that can register a mount composed while its container hung in no window -
                // under a caller-named context that is no window's own, which named no recomposer either.
                registerWith(arrivedIn.swingRecomposerOrNull())
            }

            mountedIn !== arrivedIn -> {
                // Queued before the withdrawals below, which run on the spot, so that the turn it takes
                // is queued ahead of the one they schedule: a window's recomposer ends once its last
                // content composition is gone, deferred by a turn, and one this content is only passing
                // out of must hear the rejoin's answer before it counts itself unused.
                scheduleRejoin()
                // Withdrawn on the spot: AWT delivers a parent change to a container's descendants before
                // the container itself, so content nested inside this is asked where it hangs while this
                // mount still names the composition it is leaving. Answering nothing sends that walk on
                // to where it really hangs now, and the rejoin publishes the new answer.
                registration.context = null
                // Withdrawn with it: the window this mount is leaving may close before the queued rejoin
                // runs, and its teardown must not dispose content standing in another window by then. The
                // rejoin registers with whatever recomposer the new place resolves to.
                registerWith(null)
            }
        }
    }

    /** Queues [rejoin] behind the event being dispatched, at most once at a time. */
    private fun scheduleRejoin() {
        if (rejoinScheduled) return
        rejoinScheduled = true
        SwingUtilities.invokeLater(::rejoin)
    }

    /**
     * Composes the content again under what [component]'s place resolves to now, or takes the
     * registration the move withdrew up again where the move was undone before this ran.
     */
    private fun rejoin() {
        // Cleared first: a move made while this one runs must queue one of its own.
        rejoinScheduled = false
        val parent = parentToRejoin()
        if (parent != null) {
            composition?.dispose()
            composeUnder(parent)
        } else if (phase == MountPhase.Mounted) {
            // Nothing to compose again under: the container is back in the window it stood in, or out
            // of every window. Its composition stays the one it had, whose window still ends it, so the
            // registration withdrawn for the move is taken up again.
            registration.context = parentContext
            registerWith(statedWindow.value?.swingRecomposerOrNull())
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
        checkEventDispatchThread()
        if (phase == MountPhase.Disposed) return
        phase = MountPhase.Disposed
        component.removeHierarchyListener(registration)
        registration.context = null
        parentContext = null
        registerWith(null)
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
 * asked for its recomposer through the listener that reaps its content - so what a container's content
 * nests into is answered by the container itself rather than tracked anywhere else, removing the listener
 * is the whole of withdrawing the answer, and a container that is garbage collected takes its mounts with
 * it. The registration also reaches a [java.awt.Container] that is no [javax.swing.JComponent], which
 * carries no client-property bag at all.
 *
 * [context] is `null` while the mount is still waiting for a parent, and again once it is disposed.
 *
 * @property handle the handle over the content composition this registration belongs to, so a window
 *   tearing down can dispose what stands in it through the containers they are registered on.
 * @param onPlaceChanged run whenever the container's place changes; see [PLACE_CHANGE_FLAGS].
 */
private class ContentCompositionRegistration(
    val handle: DisposableHandle,
    private val onPlaceChanged: () -> Unit,
) : HierarchyListener {
    var context: CompositionContext? = null

    override fun hierarchyChanged(event: HierarchyEvent) {
        if (event.changeFlags and PLACE_CHANGE_FLAGS != 0L) onPlaceChanged()
    }
}

private val Component.contentCompositionRegistrationOrNull: ContentCompositionRegistration?
    get() = hierarchyListeners.firstNotNullOfOrNull { it as? ContentCompositionRegistration }

/**
 * The [CompositionContext] a live `setContent` on this component composes its content under, or `null`
 * where this component carries nothing that has composed yet.
 */
internal fun Component.contentCompositionContextOrNull(): CompositionContext? =
    contentCompositionRegistrationOrNull?.context

/**
 * The handle of the `setContent` mount registered on this component, pending or live, if any.
 * Disposing it is idempotent, so a caller who also disposes the handle they were returned is unaffected.
 */
internal fun Component.contentCompositionHandleOrNull(): DisposableHandle? =
    contentCompositionRegistrationOrNull?.handle

/**
 * The only [HierarchyEvent] change flag that can put a [Component] under a different window:
 * [HierarchyEvent.PARENT_CHANGED]. Which window a component is in is a question about the Swing tree
 * alone, so a parent change is the whole of what a mount has to watch for.
 */
private const val PLACE_CHANGE_FLAGS: Long = HierarchyEvent.PARENT_CHANGED.toLong()
