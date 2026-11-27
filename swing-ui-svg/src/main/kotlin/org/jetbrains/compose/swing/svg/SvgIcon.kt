// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("SvgKt")

package org.jetbrains.compose.swing.svg

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.renderer.NullPlatformSupport
import com.github.weisj.jsvg.renderer.awt.AwtComponentPlatformSupport
import com.github.weisj.jsvg.view.ViewBox
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon

/**
 * Remembers an [Icon] that draws [document] at [size].
 *
 * Use it with Swing icon style setters or tab headers. It stays sharp on a scaled display and
 * draws the start of the document's timeline. Use [SvgImage] for playback.
 *
 * @param document the SVG to draw.
 * @param size the icon's size. Defaults to the document's intrinsic size.
 * @param antialias whether to smooth shape edges. Defaults to `true`.
 */
@Composable
public fun rememberSvgIcon(
    document: SVGDocument,
    size: Dimension = document.intrinsicSize(),
    antialias: Boolean = true,
): Icon {
    val width = size.width
    val height = size.height
    return remember(document, width, height, antialias) { SvgIcon(document, width, height, antialias) }
}

/** The [Icon] that [rememberSvgIcon] returns. */
@VisibleForTesting
internal class SvgIcon(
    private val document: SVGDocument,
    private val width: Int,
    private val height: Int,
    private val antialias: Boolean,
) : Icon {
    override fun getIconWidth(): Int = width

    override fun getIconHeight(): Int = height

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        document.draw(
            g,
            // The component supplies the font family of text and is the observer of images the document refers to.
            if (c == null) NullPlatformSupport.INSTANCE else AwtComponentPlatformSupport(c),
            ViewBox(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()),
            timeMillis = 0L,
            antialias = antialias,
        )
    }
}
