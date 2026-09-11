package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.util.DeferredAction
import org.jetbrains.compose.swing.util.Key
import org.jetbrains.compose.swing.util.get
import org.jetbrains.compose.swing.util.set
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JComponent
import javax.swing.RootPaneContainer
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
 * The composition a `setContent` nests into: the [context] its content composes under, the [window]
 * whose shared composition that context is, and the [recomposer] driving the content.
 *
 * [window] is `null` for a context published by a host composition, which hands the window down through
 * its own [androidx.compose.runtime.CompositionLocal]s instead.
 *
 * [recomposer] travels with the resolution rather than being looked up again from the window, because a
 * window with no root pane has nowhere to keep one and so answers for none: the content would otherwise
 * register with nothing, and such a recomposer would be reaped as unused or outlive its content. It is
 * `null` where the content composes under no window's recomposer at all.
 */
internal class ParentComposition(
    val context: CompositionContext,
    val window: Window?,
    val recomposer: SwingRecomposer?,
)

/**
 * Resolves what a `setContent` on [component] with no parent named should nest into **right now**, or
 * `null` if it cannot be resolved yet - the container has no host stamp and no window ancestor, so the
 * content must wait to compose until the container takes a place that answers.
 *
 * Resolution order: first an existing host discovered up the Swing tree - a stamped host composition, or
 * content composed over [component] - then the owning window's shared recomposer. Those two are the
 * whole order: a recomposer created for a component is reached only by passing its context in.
 */
internal fun resolveParentOrNull(component: Component): ParentComposition? {
    val window = component.ownerWindowOrNull()
    val shared = window?.swingRecomposerOrNull()
    val host = component.findParentCompositionContext()
    // The recomposer driving that host, which is the window's only where the window shares one.
    val parentRecomposer = host?.let { component.enclosingContentRecomposerOrNull() ?: shared }
    return when {
        // A window stamps its own shared scope on its root pane, so the walk reaches a window's context
        // as readily as a host composition's; which of the two it found is what names the window.
        host != null -> ParentComposition(host, window.takeIf { shared?.recomposer === host }, parentRecomposer)

        window != null -> window.getOrCreateRecomposer().let { ParentComposition(it.recomposer, window, it) }

        else -> null
    }
}

/**
 * Mounts a `setContent` on [component] as soon as the context it nests into can be resolved, and
 * returns a [DisposableHandle] over the (possibly still pending) content composition.
 *
 * If the context resolves immediately (a host stamp or content composed up the tree, or the owning
 * window's recomposer), [compose] runs synchronously here. Otherwise composing is **deferred** until
 * [component] takes a place in the Swing tree that resolves one, at which point [compose] runs.
 *
 * The returned handle is idempotent: disposing before the content composes removes the listener and
 * mounts nothing; disposing after it disposes the content composition.
 */
internal fun mountWhenParentResolves(
    component: JComponent,
    compose: (ParentComposition, State<Window?>) -> SwingContentComposition,
): DisposableHandle =
    ContentCompositionState(component, standsOnItsOwnRecomposer = false, compose).also { it.start(namedParent = null) }

/**
 * Mounts a `setContent` on [component] under the [namedParent] its caller supplied, composing on the
 * call, and returns a [DisposableHandle] over that content composition.
 *
 * The handle is idempotent and disposes this content's composition, never [namedParent].
 */
internal fun mountUnderNamedParent(
    component: JComponent,
    namedParent: CompositionContext,
    compose: (ParentComposition, State<Window?>) -> SwingContentComposition,
): DisposableHandle {
    require(!namedParent.hasEnded) {
        "The composition context named for ${component.javaClass.name} belongs to a recomposer that " +
            "has ended, so content composed under it would never recompose. Name a live one, or name no " +
            "parent at all to join the composition this container's own place resolves to."
    }
    val owning = windowOwning(namedParent)
    val kept = owning?.swingRecomposerOrNull() ?: libraryKeptRecomposerDriving(namedParent)
    // A recomposer this library does not keep is one its caller created and owns: they end it themselves,
    // so this content registers with none and stays on it wherever its container goes. A context taken from
    // inside a live composition is that composition's own child and is torn down with it, and a recomposer
    // this library keeps ends with the window it was created for, so content named either of those follows
    // windows like any other.
    val standsOnItsOwnRecomposer = namedParent is Recomposer && kept == null
    // What the content registers with. The context's own recomposer where this library keeps it, then the
    // one driving the content this container hangs inside, and last the one its window shares - the same
    // order [resolveParentOrNull] takes, because a window with no root pane shares none.
    val recomposer =
        if (standsOnItsOwnRecomposer) {
            null
        } else {
            kept
                ?: component.enclosingContentRecomposerOrNull()
                ?: component.ownerWindowOrNull()?.swingRecomposerOrNull()
        }
    val parent = ParentComposition(namedParent, owning, recomposer)
    return ContentCompositionState(component, standsOnItsOwnRecomposer, compose).also { it.start(parent) }
}

/**
 * The [SwingRecomposer] this library keeps whose recomposer is [context], where no window answers for it -
 * the recomposers created for a window with no root pane, which has nowhere to keep one and so shares
 * none.
 *
 * Read back off the content standing in those windows rather than from a table, so it costs a walk over
 * them. A context that is no [Recomposer] is none of these and is answered without the walk.
 */
private fun libraryKeptRecomposerDriving(context: CompositionContext): SwingRecomposer? {
    if (context !is Recomposer) return null
    return Window
        .getWindows()
        .filterNot { it is RootPaneContainer }
        .firstNotNullOfOrNull { it.mountedRecomposerDriving(context) }
}

/**
 * The [SwingRecomposer] the content composed on this component or on anything under it composes under,
 * if it is [context]'s.
 */
private fun Component.mountedRecomposerDriving(context: CompositionContext): SwingRecomposer? =
    contentCompositionOrNull()?.parentRecomposer?.takeIf { it.recomposer === context }
        ?: (this as? Container)?.components?.firstNotNullOfOrNull { it.mountedRecomposerDriving(context) }

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
    get() {
        val recomposer = this as? Recomposer ?: return false
        return when (recomposer.currentState.value) {
            Recomposer.State.ShutDown, Recomposer.State.ShuttingDown -> true
            else -> false
        }
    }

/**
 * The phase a `setContent` is in.
 *
 * A `setContent` is exactly one of these at any time:
 *  - [Pending] - nothing composed yet, the parent still to come;
 *  - [Mounted] - a parent is known and the composition is live;
 *  - [Disposed] - torn down; terminal, reached at most once.
 *
 * Modeling the phase explicitly (rather than a `disposed` flag plus the implicit
 * "composition != null => mounted" invariant) lets the transitions reject illegal moves
 * structurally - e.g. a hierarchy event that fires after disposal cannot compose anything.
 */
private enum class Phase { Pending, Mounted, Disposed }

/**
 * The lifecycle state machine backing a `setContent`, whichever way it comes by the parent it
 * composes under: a caller who names one, or the container's own place in the Swing tree.
 *
 * A content composition watches the window its container is in, and composes the content again - under
 * the composition that window shares - once the container ends up in another one. The window it is in
 * is the container's own, or, while the container hangs off none, the window whose shared composition
 * the content was given to compose under. Every other move leaves the composition exactly where it is:
 * a container in no window has arrived nowhere, so one taken out of a window on its way to another is
 * not torn down midway, and one that arrives in the window it was already in - the place a cell
 * renderer takes to be painted - has not moved at all. Content that stood in no window at all adopts the
 * first one it reaches without composing again.
 *
 * While it is live, the context its content composes under is published to whatever is mounted inside
 * its container, as [publishedContext]. This state stands on the container from [start] to [dispose],
 * which is what answers whether a container already holds one.
 *
 * Lives only on the EDT, so its fields need no synchronization. All phase transitions go through this
 * object, so no caller can drive it into an illegal state, and [dispose] leaves it [Phase.Disposed]
 * with no listener installed.
 *
 * @param component the container the content is composed into, and whose place in the tree is watched.
 * @param standsOnItsOwnRecomposer whether the content composes on a recomposer of its own rather than
 *   inside another composition. Fixed for the life of the content composition, because it states what
 *   the caller named rather than where the container hangs.
 * @param compose creates and starts the [SwingContentComposition] under a parent context.
 */
internal class ContentCompositionState(
    private val component: JComponent,
    private val standsOnItsOwnRecomposer: Boolean,
    private val compose: (ParentComposition, State<Window?>) -> SwingContentComposition,
) : DisposableHandle {
    private var phase = Phase.Pending

    /** The live composition; held only while [Phase.Mounted]. */
    private var composition: SwingContentComposition? = null

    /**
     * The window stated to the content, and the record of which window this composition is in: the
     * container's own, or the window whose shared composition the content was given while the container
     * hangs off none. Content that inherited a window from the composition it joined reads that one
     * instead. Observable, so where no window could be named, the first one the container reaches is
     * stated without the content being composed again.
     */
    private val statedWindow = mutableStateOf<Window?>(null)

    /**
     * The listener watching where [component] hangs. Installed by [start] and removed by [dispose].
     */
    private val registration = ContentCompositionRegistration { placeChanged() }

    /**
     * The context this content composes under, published to whatever is mounted inside [component].
     * `null` while the content is still waiting for a parent, while its container moves between windows,
     * and once it is disposed.
     */
    var publishedContext: CompositionContext? = null
        private set

    /**
     * The window recomposer this composition is registered with while its content composes under it, or
     * `null` while it composes under no window's; see [SwingRecomposer.registerContentComposition].
     */
    private var registeredRecomposer: SwingRecomposer? = null

    private var parentContext: CompositionContext? = null

    /**
     * The window recomposer this content belongs to while it composes under [parentContext], whether or
     * not the registration is standing right now. A rejoin that puts the container back where it was
     * takes the registration up again with this rather than by asking the window, which answers nothing
     * for a window that shares no recomposer. A content composition nesting into this one registers with
     * it as well.
     */
    var parentRecomposer: SwingRecomposer? = null
        private set

    private val deferredRejoin = DeferredAction(::rejoin)

    /**
     * Watches where [component] hangs and composes the content under the parent there is now: the one
     * the caller named, or the one [component]'s place resolves to. A container whose place resolves to
     * nothing composes nothing yet and waits for one that does.
     *
     * A container already carrying a live `setContent` is refused, whether its content has composed or is
     * still waiting for a parent: content still waiting composes the moment the container reaches a place
     * that resolves one, so accepting a second would compose two of them into the container then.
     * Presence is judged by the state stored on the container from [start] to [dispose] rather than by
     * the context it publishes, which content still waiting does not carry yet and live content withdraws
     * mid-move. A container whose content composition was disposed, or threw on its first pass, takes
     * content again.
     *
     * @param namedParent the parent the caller named, or `null` to resolve one from where [component]
     *   hangs in the Swing tree. Taken as a parameter rather than held as a field, so neither the
     *   context nor the window it names outlives the call that uses them.
     */
    fun start(namedParent: ParentComposition?) {
        check(component[CONTENT_COMPOSITION_KEY] == null) {
            "${component.javaClass.name} already carries a live setContent, whether or not its content " +
                "has composed yet. A container is asked once for the composition its contents nest into, " +
                "and two content compositions cannot both be that answer - dispose the first one's " +
                "handle before setting content here again."
        }
        component[CONTENT_COMPOSITION_KEY] = this
        component.addHierarchyListener(registration)
        val parent = namedParent ?: resolveParentOrNull(component)
        if (parent != null) composeUnder(parent)
    }

    /**
     * Takes [component]'s new place in the Swing tree: content still waiting for a parent retries the
     * resolution, live content follows the window it is in now, and disposed content composes nothing.
     */
    private fun placeChanged() {
        when (phase) {
            Phase.Pending -> resolveParentOrNull(component)?.let(::composeUnder)
            Phase.Mounted -> followWindow()
            Phase.Disposed -> Unit
        }
    }

    /** Composes the content under [parent], and records which window that leaves this composition in. */
    private fun composeUnder(parent: ParentComposition) {
        // Stated before the content composes, so content standing under a window has it on its first
        // pass. The container's own window comes first: a parent published by a host composition names
        // none, and a container already hanging in a window would otherwise fall back on nothing, with
        // no later move to adopt one on.
        statedWindow.value = component.ownerWindowOrNull() ?: parent.window
        // Published before the content composes for the same reason, and the same reason the applier
        // stamps a node on its way down: the content composes inside compose(), so a setContent that
        // pass makes on a container of this content has to find this context already standing.
        this.parentContext = parent.context
        publishedContext = parent.context
        // Recorded with the context and for the same reason: content this pass mounts inside this
        // container reads what it registers with off this content composition as well.
        if (!standsOnItsOwnRecomposer) parentRecomposer = parent.recomposer
        // What was registered above is withdrawn before a failure reaches whoever composed.
        composition = disposingOnFailure(::dispose) { compose(parent, statedWindow) }
        phase = Phase.Mounted
        // Content standing on a recomposer of its own registers with none: its caller owns that one, so
        // no window's carries this content. A window whose tree its container stands in still ends
        // it when that window closes, reached by the walk over that tree - the peers the content is
        // composed into are being destroyed with the window.
        // Taken up only once the content has composed: a disposed recomposer refuses a registration by
        // disposing this content, which must not find a composition half built.
        if (!standsOnItsOwnRecomposer) registerWith(parent.recomposer)
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
     * Content already standing in another window composes again under what that place resolves to: a
     * live composition's parent context is fixed at construction, so composing again is what joins the
     * window the container is now in and keeps its content recomposing with the rest of that window.
     *
     * Content standing in no window adopts the one it arrives in instead, having no window's composition
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
                // move that can register content composed while its container hung in no window -
                // under a caller-named context that is no window's own, which named no recomposer either.
                parentRecomposer = arrivedIn.swingRecomposerOrNull()
                registerWith(parentRecomposer)
            }

            mountedIn !== arrivedIn -> {
                // Queued before the withdrawals below, which run on the spot, so that the turn it takes
                // is queued ahead of the one they schedule: a window's recomposer ends once its last
                // content composition is gone, deferred by a turn, and one this content is only passing
                // out of must hear the rejoin's answer before it counts itself unused.
                deferredRejoin.schedule()
                // Withdrawn on the spot: AWT delivers a parent change to a container's descendants before
                // the container itself, so content nested inside this is asked where it hangs while this
                // container still names the composition it is leaving. Answering nothing sends that walk on
                // to where it really hangs now, and the rejoin publishes the new answer.
                publishedContext = null
                // Withdrawn with it: the window this content is leaving may close before the queued rejoin
                // runs, and its teardown must not dispose content standing in another window by then. The
                // rejoin registers with whatever recomposer the new place resolves to.
                registerWith(null)
            }
        }
    }

    /**
     * Composes the content again under what [component]'s place resolves to now, or takes the
     * registration the move withdrew up again where the move was undone before this ran.
     */
    private fun rejoin() {
        val parent = parentToRejoin()
        if (parent != null) {
            composition?.dispose()
            composition = null
            composeUnder(parent)
        } else if (phase == Phase.Mounted) {
            // Nothing to compose again under: the container is back in the window it stood in, or out
            // of every window. Its composition stays the one it had, whose window still ends it, so the
            // registration withdrawn for the move is taken up again.
            publishedContext = parentContext
            registerWith(parentRecomposer)
        }
    }

    /**
     * The parent [component] is to compose again under, or `null` where there is nothing to compose
     * again. Where the container stands is read here rather than carried over from when the rejoin was
     * queued: it may since have been disposed, taken out of every window, or put back in the one it was
     * already in.
     */
    private fun parentToRejoin(): ParentComposition? {
        val arrivedIn = component.ownerWindowOrNull()
        val moved = phase == Phase.Mounted && arrivedIn != null && arrivedIn !== statedWindow.value
        return if (moved) resolveParentOrNull(component) else null
    }

    override fun dispose() {
        checkEventDispatchThread()
        if (phase == Phase.Disposed) return
        phase = Phase.Disposed
        component.removeHierarchyListener(registration)
        component[CONTENT_COMPOSITION_KEY] = null
        publishedContext = null
        parentContext = null
        parentRecomposer = null
        registerWith(null)
        composition?.dispose()
        composition = null
        statedWindow.value = null
    }
}

/**
 * The [HierarchyListener] a `setContent` keeps on its container, following where that container
 * hangs. It carries no state: what a container's content nests into is read off the container itself,
 * through [CONTENT_COMPOSITION_KEY].
 *
 * @param onPlaceChanged run whenever the container's place changes; see [PLACE_CHANGE_FLAGS].
 */
private class ContentCompositionRegistration(
    private val onPlaceChanged: () -> Unit,
) : HierarchyListener {
    override fun hierarchyChanged(event: HierarchyEvent) {
        if (event.changeFlags and PLACE_CHANGE_FLAGS != 0L) onPlaceChanged()
    }
}

/**
 * The `setContent` standing on a container, from [ContentCompositionState.start] to
 * [ContentCompositionState.dispose]. A key nobody else knows is a key nobody else clears, so what is
 * mounted where survives the generic Swing cleanups - sweeping a component's listeners, say - that know
 * nothing of this library.
 */
private val CONTENT_COMPOSITION_KEY: Key<ContentCompositionState> =
    Key("org.jetbrains.compose.swing.contentComposition")

/**
 * The `setContent` standing on this component, pending or live, or `null` where this component
 * carries none.
 *
 * A component that is no [JComponent] carries no content composition: `setContent` takes a [JComponent].
 * The walk that asks this reaches raw [Component]s all the same.
 */
internal fun Component.contentCompositionOrNull(): ContentCompositionState? =
    (this as? JComponent)?.get(CONTENT_COMPOSITION_KEY)

/**
 * The [SwingRecomposer] recomposing the content this component hangs inside: the one the nearest live
 * `setContent` above it composes on. `null` where the content around it is a window's own, whose
 * recomposer the window answers with, and where it stands on a recomposer its caller owns.
 *
 * The walk matches [findParentCompositionContext]'s, minus the stamps: a stamp names a context without
 * naming what recomposes it, so a content composition further up answers for a host composition stamped
 * below it. One publishing no context is passed over with its recomposer, so what nested content
 * composes under and what it registers with come off one content composition.
 */
private fun Component.enclosingContentRecomposerOrNull(): SwingRecomposer? =
    generateSequence(parent) { it.parent }
        .firstNotNullOfOrNull { it.contentCompositionOrNull()?.takeIf { state -> state.publishedContext != null } }
        ?.parentRecomposer

/**
 * The only [HierarchyEvent] change flag that can put a [Component] under a different window:
 * [HierarchyEvent.PARENT_CHANGED]. Which window a component is in is a question about the Swing tree
 * alone, so a parent change is the whole of what a content composition has to watch for.
 */
private const val PLACE_CHANGE_FLAGS: Long = HierarchyEvent.PARENT_CHANGED.toLong()
