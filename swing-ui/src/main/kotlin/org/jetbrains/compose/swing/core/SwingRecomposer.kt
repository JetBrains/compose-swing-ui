package org.jetbrains.compose.swing.core

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.core.SwingFrameClock.Companion.displayRefreshRate
import java.awt.Component
import java.beans.PropertyChangeListener

/**
 * The runtime a composition is driven by: a single [Recomposer], the frame clock that paces it, and the
 * [CoroutineScope] they run on. Every island nested into one of these recomposes on one recomposer and
 * one frame clock.
 *
 * The clock is cadenced to the display the component the runtime was created for is on, and follows
 * that component across displays: a component reports every [java.awt.GraphicsConfiguration] change,
 * including the ones an ancestor propagates down to it.
 *
 * Content is mounted on the runtime by passing its [compositionContext] as the parent of a mount.
 *
 * The runtime holds a live coroutine scope and a Swing timer, so whoever owns it [dispose]s it.
 *
 * Marked [InternalSwingUiApi]; it may change without notice in any release.
 */
@InternalSwingUiApi
public class SwingRecomposer private constructor(
    internal val recomposer: Recomposer,
    internal val clock: SwingFrameClock,
    private val scope: CoroutineScope,
    private val component: Component,
    private val refreshRateListener: PropertyChangeListener,
) {
    private var disposed = false

    /** The context to pass as the parent of a mount this runtime drives. */
    public val compositionContext: CompositionContext
        get() = recomposer

    /**
     * Cancels the recomposer, stops the clock, and cancels the scope they run on. Idempotent.
     */
    public fun dispose() {
        if (disposed) return
        disposed = true
        component.removePropertyChangeListener(GRAPHICS_CONFIGURATION_PROPERTY, refreshRateListener)
        recomposer.cancel()
        clock.dispose()
        scope.cancel()
    }

    @InternalSwingUiApi
    public companion object {
        /**
         * The bound property a [Component] fires when its [java.awt.GraphicsConfiguration] changes, i.e.
         * when it moves to a screen device with a potentially different display refresh rate.
         */
        private const val GRAPHICS_CONFIGURATION_PROPERTY: String = "graphicsConfiguration"

        /**
         * Starts a runtime paced by the display [component] is on, running at a default cadence while
         * that component is outside any container. This is what serves content built to be read rather
         * than shown, which reaches a composition with no window anywhere in the picture.
         *
         * The runtime is reachable only through the [compositionContext] the returned instance carries:
         * it is left off the component's composition stamp, so a mount that resolves its parent from the
         * Swing tree resolves a host composition or the window's one runtime, and two islands under one
         * window stay on one recomposer and one frame clock.
         *
         * The caller owns the returned runtime and decides when it ends: [dispose] it once the content
         * it drives is torn down. Content that belongs to a window joins that window's own runtime
         * instead.
         *
         * Must be called on the Event Dispatch Thread.
         */
        public fun create(component: Component): SwingRecomposer {
            checkEventDispatchThread()
            GlobalSnapshotManager.ensureStarted()
            val clock = SwingFrameClock(component.displayRefreshRate())
            val scope = CoroutineScope(Dispatchers.Swing + Job() + clock)
            val recomposer = Recomposer(scope.coroutineContext)
            scope.launch {
                recomposer.runRecomposeAndApplyChanges()
            }
            // Retime the clock when the component moves to a display with a different refresh rate. Fires
            // on the EDT; SwingFrameClock.setFramesPerSecond early-returns when the cadence is unchanged.
            val refreshRateListener =
                PropertyChangeListener { clock.setFramesPerSecond(component.displayRefreshRate()) }
            component.addPropertyChangeListener(GRAPHICS_CONFIGURATION_PROPERTY, refreshRateListener)
            return SwingRecomposer(recomposer, clock, scope, component, refreshRateListener)
        }
    }
}
