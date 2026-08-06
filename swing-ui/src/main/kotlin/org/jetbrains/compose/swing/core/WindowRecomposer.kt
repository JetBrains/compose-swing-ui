package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.RootPaneContainer

/**
 * The [CompositionContext] every composition in this window shares, created on the first call and the
 * same one on every call after it.
 *
 * Content mounted under it recomposes together with the rest of this window's content, on one
 * recomposition scope paced by the display the window is on. The window owns the context and tears it
 * down when it is disposed.
 *
 * Pass it as the parent context of a mount to join this window's composition - which is what a mount
 * on a container already under this window resolves to on its own.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
public fun Window.compositionContext(): CompositionContext {
    checkEventDispatchThread()
    return getOrCreateRecomposer().recomposer
}

/**
 * A window's [SwingRecomposer], held by the listener that tears it down when the window is
 * disposed.
 *
 * A window is asked for its runtime through the registration it already keeps: the listener that ends
 * the runtime is the same one that names it, so which runtime a window has is answered by the window
 * itself rather than tracked anywhere else. Removing the listener is therefore the whole of releasing
 * the runtime, and a window that is garbage collected takes its runtime with it.
 */
private class WindowRecomposerHolder(
    val runtime: SwingRecomposer,
    private val stampedOn: JComponent?,
) : WindowAdapter() {
    override fun windowClosed(e: WindowEvent) {
        e.window.removeWindowListener(this)
        stampedOn?.setCompositionContext(null)
        runtime.dispose()
    }
}

/**
 * Returns the [SwingRecomposer] already created for this window, or `null` if none exists yet.
 * Does NOT create one. EDT-only.
 */
internal fun Window.recomposerOrNull(): SwingRecomposer? =
    windowListeners.firstNotNullOfOrNull { (it as? WindowRecomposerHolder)?.runtime }

/**
 * The [Window] whose own shared composition scope [context] is, or `null` when [context] is something
 * else - a context published by a host composition, or a root created for a component.
 *
 * This answers from [context] alone, so a mount handed a window's context states that window even while
 * the container it composes into hangs off no window at all. The answer is read back off the windows
 * themselves, so it costs a pass over the windows this application has open. EDT-only.
 */
internal fun windowOwning(context: CompositionContext): Window? =
    Window.getWindows().firstOrNull { it.recomposerOrNull()?.recomposer === context }

/**
 * Returns this window's single [SwingRecomposer], creating it (recomposer + frame clock +
 * scope) on first call and memoizing it on the window, so every island in one window recomposes on one
 * recomposer and one frame clock.
 *
 * On creation the runtime's recomposer is also published as the window's [COMPOSITION_KEY]
 * [androidx.compose.runtime.CompositionContext] on the [javax.swing.JRootPane] (when present), so
 * descendant `setContent` calls resolving via [findParentCompositionContext] share this same scope. A
 * [WindowAdapter.windowClosed] listener is registered once that tears everything down when the window
 * is disposed: a window is the one host with a permanent "finished" lifecycle event, which is what
 * lets it own its runtime rather than hand a disposal to a caller.
 *
 * EDT-only.
 */
internal fun Window.getOrCreateRecomposer(): SwingRecomposer {
    recomposerOrNull()?.let { return it }

    val created = SwingRecomposer.create(this)
    // The holder clears this stamp from the window's teardown, so it stands exactly as long as the
    // recomposer behind it.
    val stampedOn = (this as? RootPaneContainer)?.rootPane
    stampedOn?.setCompositionContext(created.recomposer)
    addWindowListener(WindowRecomposerHolder(created, stampedOn))

    return created
}
