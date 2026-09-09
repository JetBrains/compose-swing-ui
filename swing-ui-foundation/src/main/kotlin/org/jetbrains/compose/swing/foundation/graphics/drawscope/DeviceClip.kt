@file:JvmMultifileClass
@file:JvmName("GraphicsDrawScopeKt")

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import org.jetbrains.compose.swing.foundation.graphics.IDENTITY
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.AffineTransform

/**
 * The clip of this graphics in device space, where it is held, given the [transform] it currently carries. Read in
 * user space and set back, a clip is rounded through the transform twice and no longer covers exactly what it did.
 */
@PublishedApi
internal fun Graphics2D.deviceClip(transform: AffineTransform): Shape? {
    this.transform = IDENTITY
    val deviceClip = clip
    this.transform = transform
    return deviceClip
}

/** Puts back [transform] and the [deviceClip] read before it changed, exactly. */
@PublishedApi
internal fun Graphics2D.restoreTransformAndClip(
    transform: AffineTransform,
    deviceClip: Shape?,
) {
    this.transform = IDENTITY
    clip = deviceClip
    this.transform = transform
}
