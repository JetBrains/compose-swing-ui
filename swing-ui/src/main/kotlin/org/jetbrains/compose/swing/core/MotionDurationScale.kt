package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Stable
import kotlin.coroutines.CoroutineContext

/**
 * Scales how long motion takes, for callers who want animations slowed, drawn out or switched off.
 *
 * Installing one on the context a composition's effects run in multiplies the duration of every
 * animation driven from that composition: `1` plays motion at the speed it was written at, a larger
 * factor draws it out, and `0` ends each animation on its next frame, so a viewer is shown the state
 * the motion would have reached without the motion itself.
 *
 * No scale is installed by default and motion plays at the speed it was written at. There is no desktop
 * setting to take one from - AWT surfaces no reduced-motion signal on any platform - so an application
 * offering the preference states it here itself, and a test states it to pin what it observes.
 */
@Stable
public interface MotionDurationScale : CoroutineContext.Element {
    /**
     * The multiplier applied to a motion's duration. Must not be negative. A factor of `10` makes an
     * animation written to run for 100ms run for 1000ms; a factor of `0` ends it on the next frame.
     */
    public val scaleFactor: Float

    override val key: CoroutineContext.Key<*> get() = Key

    /** The context key a [MotionDurationScale] is installed and looked up under. */
    public companion object Key : CoroutineContext.Key<MotionDurationScale>
}
