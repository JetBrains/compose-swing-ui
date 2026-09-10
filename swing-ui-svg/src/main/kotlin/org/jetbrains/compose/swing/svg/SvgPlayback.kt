// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg

import com.github.weisj.jsvg.SVGDocument
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.observeReads

internal fun SwingModifier.svgPlayback(
    document: SVGDocument,
    state: SvgAnimationState?,
): SwingModifier = if (state == null) this else this then SvgPlaybackElement(document, state)

private data class SvgPlaybackElement(
    val document: SVGDocument,
    val state: SvgAnimationState,
) : SwingModifier.NodeElement<SvgSurface, SvgPlaybackNode>() {
    override val targetType: Class<SvgSurface> get() = SvgSurface::class.java
    override val name: String get() = "svgPlayback"

    override fun create(): SvgPlaybackNode = SvgPlaybackNode(document, state)

    override fun update(node: SvgPlaybackNode) {
        node.update(document, state)
    }
}

/** Runs the timeline of its component's animation state while that state is playing. */
private class SvgPlaybackNode(
    private var document: SVGDocument,
    private var state: SvgAnimationState,
) : SwingModifier.ComponentNode<SvgSurface>(),
    ObserverModifierNode {
    private var driver: Job? = null

    override fun onAttach() {
        onObservedReadsChanged()
    }

    override fun onDetach() {
        stopPlayback()
    }

    fun update(
        document: SVGDocument,
        state: SvgAnimationState,
    ) {
        if (document === this.document && state === this.state) return
        this.document = document
        this.state = state
        stopPlayback()
        onObservedReadsChanged()
    }

    override fun onObservedReadsChanged() {
        var playing = false
        observeReads { playing = state.isPlaying }
        if (!playing) {
            stopPlayback()
        } else if (driver == null) {
            val animation = document.animation()
            driver = coroutineScope.launch { state.drive(animation) }
        }
    }

    private fun stopPlayback() {
        driver?.cancel()
        driver = null
    }
}
