package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import kotlinx.coroutines.DisposableHandle
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.RootPaneContainer

/**
 * The [CompositionContext] every composition in this window shares, created on the first call and the
 * same one for as long as anything composes under it.
 *
 * Content mounted under it recomposes together with the rest of this window's content, on one
 * recomposition scope whose frame-driven work is paced by the display the window is on. The context ends
 * with the last content composed under it, and with the window; a call after that hands out a fresh one.
 *
 * Pass it as the parent context of a mount to join this window's composition - which is what a mount on
 * a container already under this window resolves to on its own. Creating is what this is for: a caller
 * asks to compose into the answer, so a context taken and held across a moment when the window composes
 * nothing is the one case where the answer goes stale.
 *
 * A window whose content composes under a composition declared elsewhere drives none of its own and is
 * refused here, because a context created for it would drive nothing: a window declared inside
 * `application { }` runs as part of that application's composition, whose recomposer is
 * [org.jetbrains.compose.swing.window.ApplicationScope.recomposer].
 *
 * Must be called on the Event Dispatch Thread.
 */
internal fun Window.compositionContext(): CompositionContext {
    checkEventDispatchThread()
    swingRecomposerOrNull()?.let { return it.recomposer }
    check(!composesUnderAForeignComposition()) {
        "'${javaClass.name}' composes its content under a composition declared elsewhere, so it drives no " +
            "composition of its own, and a context created here would drive nothing. A window declared " +
            "inside application { } runs on the application's recomposer: ask ApplicationScope for it."
    }
    return getOrCreateRecomposer().recomposer
}

/**
 * Whether this window's content pane carries the [COMPOSITION_KEY] stamp of a composition that hosts
 * this window - what a window declared inside `application { }` is given, so its content joins the
 * composition that declared it. A window mounted with `setContent` carries no such stamp: its content
 * resolves its parent from the window, and the window's own recomposer is stamped on the root pane.
 */
private fun Window.composesUnderAForeignComposition(): Boolean =
    ((this as? RootPaneContainer)?.contentPane as? JComponent)?.get(COMPOSITION_KEY) != null

/**
 * A window's [SwingRecomposer], held by the listener that reaps its content when the window is disposed.
 *
 * A window is asked for its recomposer through the registration it already keeps: the listener that
 * reaps its content is the same one that names it, so which recomposer a window has is answered by the
 * window itself rather than tracked anywhere else, and a window that is garbage collected takes its
 * recomposer with it.
 *
 * Reaping the window's content is what normally ends its recomposer; it is disposed here as well, for
 * the window that took a context and mounted nothing under it: no registration was ever withdrawn to end
 * that one. This runs only for a window disposed while it was displayable; one already past that is
 * answered where it is created, by [getOrCreateRecomposer]. Disposing is idempotent, so both routes are
 * safe.
 */
private class WindowRecomposerHolder(
    val recomposer: SwingRecomposer,
) : WindowAdapter() {
    override fun windowClosed(e: WindowEvent) {
        disposeContentCompositionsIn(e.window)
        recomposer.dispose()
    }
}

/**
 * Disposes every `setContent` content composition registered on [window]'s component tree. Runs before
 * the window's recomposer is disposed, because cancelling a recomposer disposes none of the compositions
 * composing under it - left alone, each would keep its remember observers unforgotten and its snapshot
 * observer registered, holding the closed window's hierarchy for the life of the process.
 *
 * The walk reaches the root pane's menu bar with everything else, because [javax.swing.JRootPane]
 * keeps it in the layered pane. Content whose container has left the tree is reached through the
 * recomposer it registered with instead - see [SwingRecomposer.registerContentComposition] - and each
 * handle is safe to dispose from both sides.
 *
 * Every handle is collected before any is disposed: disposing a content composition removes its composed
 * subtree from the tree being walked.
 */
internal fun disposeContentCompositionsIn(window: Window) {
    val standing = mutableListOf<DisposableHandle>()

    fun collect(component: Component) {
        component.contentCompositionHandleOrNull()?.let(standing::add)
        if (component is Container) {
            for (child in component.components) collect(child)
        }
    }
    collect(window)
    standing.forEach(DisposableHandle::dispose)
}

/**
 * Returns the live [SwingRecomposer] already created for this window, or `null` if none exists yet or
 * the one it was given has ended. Does NOT create one. EDT-only.
 */
internal fun Window.swingRecomposerOrNull(): SwingRecomposer? =
    recomposerHolderOrNull()?.recomposer?.takeIf { !it.isDisposed }

/**
 * The listener naming the recomposer this window was given, whether or not that recomposer is still
 * live. A window whose recomposer has ended keeps it until the next one replaces it.
 */
private fun Window.recomposerHolderOrNull(): WindowRecomposerHolder? =
    windowListeners.firstNotNullOfOrNull { it as? WindowRecomposerHolder }

/**
 * The [Window] whose own shared composition scope [context] is, or `null` when [context] is something
 * else - a context published by a host composition, or one created for a component.
 *
 * This answers from [context] alone, so a mount handed a window's context states that window even while
 * the container it composes into hangs off no window at all. The answer is read back off the windows
 * themselves, so it costs a pass over the windows this application has open. EDT-only.
 */
internal fun windowOwning(context: CompositionContext): Window? =
    Window.getWindows().firstOrNull { it.swingRecomposerOrNull()?.recomposer === context }

/**
 * Returns this window's single [SwingRecomposer], creating it (recomposer + frame clock + scope) on
 * first call and memoizing it on the window, so every content composition in one window recomposes on
 * one recomposer and one frame clock.
 *
 * On creation its recomposer is also published as the window's [COMPOSITION_KEY]
 * [androidx.compose.runtime.CompositionContext] on the [javax.swing.JRootPane] (when present), so
 * descendant `setContent` calls resolving via [findParentCompositionContext] share this same scope. A
 * [WindowAdapter.windowClosed] listener is registered once that reaps the content compositions standing
 * in the window when it is disposed.
 *
 * Nobody is handed this recomposer, so it ends itself once the last content composition registered with
 * it is gone: a window closing reaps those and so ends it, and a window emptied while it stays open ends
 * it just the same - content mounted there next is given a fresh one.
 *
 * EDT-only.
 */
internal fun Window.getOrCreateRecomposer(): SwingRecomposer {
    swingRecomposerOrNull()?.let { return it }

    // Cleared when the recomposer ends, so the stamp stands exactly as long as what it names: a mount
    // resolving its parent up the Swing tree must never reach an ended context.
    val stampedOn = (this as? RootPaneContainer)?.rootPane
    val created = SwingRecomposer.forWindow(this) { stampedOn?.setCompositionContext(null) }
    stampedOn?.setCompositionContext(created.recomposer)
    // The listener naming a recomposer that has ended is replaced, rather than left standing beside a
    // second one answering the same close.
    recomposerHolderOrNull()?.let(::removeWindowListener)
    addWindowListener(WindowRecomposerHolder(created))
    // A window that is not displayable posts no windowClosed - Window.dispose() posts it only while the
    // peer stands - so the listener just registered would never run, and a recomposer nothing composes
    // under would hold its clock and its toolkit subscription for the life of the process. Asked here, it
    // ends itself on the next turn unless something registers with it in this one, which is what mounting
    // content does before this turn is over.
    if (!isDisplayable) created.disposeIfUnused()

    return created
}
