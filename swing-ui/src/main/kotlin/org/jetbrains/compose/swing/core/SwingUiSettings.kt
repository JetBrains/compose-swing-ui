package org.jetbrains.compose.swing.core

import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi

/**
 * What this library reads from system properties to configure itself for a whole process.
 *
 * Each setting is read once, when it is first used, and kept for the JVM's lifetime: a property set
 * after that is not picked up. A property that is absent, or that carries a value outside what the
 * setting accepts, leaves it at its default - a malformed flag reconfigures nothing and never stops an
 * application from starting.
 *
 * A process-wide setting is the coarsest of the ways to state one of these. Anything stated closer wins:
 * a [MotionDurationScale] on the context a composition's effects run in overrides [motionDurationScale]
 * for that composition, whether it comes from [SwingRecomposer.create] or from the test harness.
 */
@InternalSwingUiApi
public object SwingUiSettings {
    /** The system property [motionDurationScale] is read from. */
    public const val MOTION_DURATION_SCALE_PROPERTY: String = "compose.swing.motionDurationScale"

    /**
     * What every animation's duration is multiplied by, for a user who wants motion slowed down or
     * switched off across a whole application.
     *
     * Read from [MOTION_DURATION_SCALE_PROPERTY], which accepts a finite, non-negative number: `1` (the
     * default) plays motion at the speed it was written at, `0` ends each animation on its next frame,
     * and a larger value draws motion out. A scale rather than an on/off flag, so "half speed" is as
     * sayable as "no motion at all".
     */
    public val motionDurationScale: MotionDurationScale by
        lazy { motionDurationScaleOf(System.getProperty(MOTION_DURATION_SCALE_PROPERTY)) }
}

/**
 * The scale the given property value states, or a scale of `1` for a value this setting does not accept
 * - absent, unparseable, negative, or not finite.
 */
@VisibleForTesting
internal fun motionDurationScaleOf(propertyValue: String?): MotionDurationScale {
    val factor = propertyValue?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: 1f
    return ProcessMotionDurationScale(factor)
}

/** The [MotionDurationScale] a system property states, fixed for the life of the process. */
private class ProcessMotionDurationScale(
    override val scaleFactor: Float,
) : MotionDurationScale
