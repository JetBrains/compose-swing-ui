package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.core.SwingFrameClock.Companion.displayRefreshRate
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import java.util.WeakHashMap
import javax.swing.JRootPane
import javax.swing.RootPaneContainer

/**
 * Owns the one-per-[Window] composition runtime: a single [Recomposer], the [SwingFrameClock] that
 * drives it, and the [CoroutineScope] they run on. Every island mounted into the same window shares
 * one of these, so two `setContent` calls under one window recompose on one recomposer and one frame
 * clock.
 *
 * Created lazily on the first `setContent` that resolves to the window (see
 * [Window.getOrCreateWindowRecomposer]) and torn down when the window is closed/disposed.
 */
internal class WindowRecomposer private constructor(
    val recomposer: Recomposer,
    private val clock: SwingFrameClock,
    private val scope: CoroutineScope,
    private val window: Window,
    private val refreshRateListener: PropertyChangeListener,
) {
    private var disposed = false

    fun dispose() {
        if (disposed) return
        disposed = true
        window.removePropertyChangeListener(GRAPHICS_CONFIGURATION_PROPERTY, refreshRateListener)
        recomposer.cancel()
        clock.dispose()
        scope.cancel()
    }

    companion object {
        /**
         * The bound property [Window] fires when its [java.awt.GraphicsConfiguration] changes, i.e. when
         * the window moves to a screen device with a potentially different display refresh rate.
         */
        private const val GRAPHICS_CONFIGURATION_PROPERTY: String = "graphicsConfiguration"

        fun create(window: Window): WindowRecomposer {
            GlobalSnapshotManager.ensureStarted()
            val clock = SwingFrameClock(window.displayRefreshRate())
            val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
            val recomposer = Recomposer(scope.coroutineContext)
            scope.launch {
                recomposer.runRecomposeAndApplyChanges()
            }
            // Retime the clock when the window moves to a display with a different refresh rate. Fires on
            // the EDT; SwingFrameClock.setFramesPerSecond early-returns when the cadence is unchanged.
            val refreshRateListener = PropertyChangeListener { clock.setFramesPerSecond(window.displayRefreshRate()) }
            window.addPropertyChangeListener(GRAPHICS_CONFIGURATION_PROPERTY, refreshRateListener)
            return WindowRecomposer(recomposer, clock, scope, window, refreshRateListener)
        }
    }
}

/**
 * Client-property key under which a window's [WindowRecomposer] is memoized on its [JRootPane], so it
 * is created at most once per window. Windows that are not [RootPaneContainer]s fall back to
 * [windowRecomposers].
 */
private const val WINDOW_RECOMPOSER_KEY: String = "org.jetbrains.compose.swing.windowRecomposer"

/**
 * Side table for [Window]s that are not [RootPaneContainer]s and therefore have no [JRootPane] to
 * carry the [WINDOW_RECOMPOSER_KEY] client property. Weakly keyed so a disposed, unreferenced window
 * does not pin its runtime.
 */
private val windowRecomposers = WeakHashMap<Window, WindowRecomposer>()

/**
 * The [JRootPane] that carries this window's recomposer slot, or `null` for a non-[RootPaneContainer]
 * window (which uses the [windowRecomposers] side table instead).
 */
private val Window.recomposerRootPane: JRootPane?
    get() = (this as? RootPaneContainer)?.rootPane

/**
 * Returns the [WindowRecomposer] already created for this window, or `null` if none exists yet. Does
 * NOT create one. EDT-only.
 */
internal fun Window.windowRecomposerOrNull(): WindowRecomposer? {
    val rootPane = recomposerRootPane
    return if (rootPane != null) {
        rootPane.getClientProperty(WINDOW_RECOMPOSER_KEY) as? WindowRecomposer
    } else {
        windowRecomposers[this]
    }
}

/**
 * Returns this window's single [Recomposer], creating the backing [WindowRecomposer] (recomposer +
 * frame clock + scope) on first call and memoizing it on the window.
 *
 * On creation the recomposer is also published as the window's [COMPOSITION_KEY]
 * [androidx.compose.runtime.CompositionContext] on the [JRootPane] (when present), so descendant
 * `setContent` calls resolving via [findParentCompositionContext] share this same scope. A
 * [WindowAdapter.windowClosed] listener is registered once that tears everything down when the window
 * is disposed.
 *
 * EDT-only.
 */
internal fun Window.getOrCreateWindowRecomposer(): Recomposer {
    windowRecomposerOrNull()?.let { return it.recomposer }

    val created = WindowRecomposer.create(this)
    val rootPane = recomposerRootPane
    // Centralized COMPOSITION_KEY publication (see publishCompositionContext); the returned action
    // clears the stamp and is invoked from this window's windowClosed teardown below. The separate
    // WINDOW_RECOMPOSER_KEY memoization is local to this file and stays inline.
    val clearCompositionStamp =
        if (rootPane != null) {
            rootPane.putClientProperty(WINDOW_RECOMPOSER_KEY, created)
            publishCompositionContext(rootPane, created.recomposer)
        } else {
            windowRecomposers[this] = created
            {}
        }

    addWindowListener(
        object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                removeWindowListener(this)
                val pane = recomposerRootPane
                if (pane != null) {
                    pane.putClientProperty(WINDOW_RECOMPOSER_KEY, null)
                    clearCompositionStamp()
                } else {
                    windowRecomposers.remove(this@getOrCreateWindowRecomposer)
                }
                created.dispose()
            }
        },
    )

    return created.recomposer
}
