/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.graphics.drawscope.ContentDrawScope in AndroidX's
 * ui-graphics; see this module's META-INF/NOTICE for the synced version. The interface KDoc
 * rewords upstream's drawContent contract; CanvasContentDrawScope is this project's own
 * implementation.
 */

package org.jetbrains.compose.swing.foundation.graphics.drawscope

import java.awt.Dimension
import java.awt.Graphics2D

/**
 * The receiver scope of a draw node, which draws between other operations on the same surface. If
 * [drawContent] is not called, what the node wraps is not drawn.
 */
@DrawScopeMarker
public sealed interface ContentDrawScope : DrawScope {
    /** Draws what this node wraps: the rest of the component's modifier chain, then the component itself. */
    public fun drawContent()
}

/**
 * The [ContentDrawScope] one draw node is handed on every draw. [drawing] points it at the graphics, size and
 * content of one call. A draw nested inside another puts back everything the outer one held, and the outermost
 * keeps its size for the next call to compare against; no graphics outlives the call.
 *
 * [drawContent] paints at the extent [drawing] was handed, not at [size], which `inset` narrows for the rest of
 * a block.
 */
internal class CanvasContentDrawScope :
    CanvasDrawScope(),
    ContentDrawScope {
    private var content: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }

    /** The width the last outermost [drawing] was handed, which [drawContent] paints the content at; -1 before one. */
    var contentWidth: Int = -1
        private set

    /** The height the last outermost [drawing] was handed; see [contentWidth]. */
    var contentHeight: Int = -1
        private set

    override fun drawContent() {
        check(contentWidth >= 0) { "This DrawScope is used outside the draw it was handed to." }
        content(graphics, contentWidth, contentHeight)
    }

    inline fun drawing(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        noinline content: (Graphics2D, Int, Int) -> Unit,
        block: () -> Unit,
    ) {
        val previousGraphics = drawingGraphics
        val previousSize = size
        val previousContent = this.content
        val previousWidth = contentWidth
        val previousHeight = contentHeight
        drawingGraphics = graphics
        if (size.width != width || size.height != height) size = Dimension(width, height)
        contentWidth = width
        contentHeight = height
        this.content = content
        try {
            block()
        } finally {
            drawingGraphics = previousGraphics
            this.content = previousContent
            if (previousGraphics != null) {
                size = previousSize
                contentWidth = previousWidth
                contentHeight = previousHeight
            }
        }
    }
}
