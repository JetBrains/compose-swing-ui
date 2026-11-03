// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * A panel that owns a nested composition: [content] is composed into it under [parent] for as long as the host
 * stays in the composition, the way a component hosting an editor or a popup owns its own composition.
 *
 * The content mounts when the host's node first updates and again only when [parent] changes, so a new [content]
 * lambda recomposes the mounted content instead of remounting it. The mount is disposed when [parent] changes and
 * when the host leaves the composition.
 *
 * @param modifier the modifier of the host panel; tag it to tell several hosts apart.
 * @param layout the layout manager of the host panel, or `null` to keep the panel's default.
 * @param parent the composition context the nested composition joins; defaults to the context of the host's own place.
 * @param content the content composed into the host panel.
 */
@Composable
public fun NestedContentHost(
    modifier: SwingModifier = SwingModifier,
    layout: LayoutManager? = null,
    parent: CompositionContext = rememberCompositionContext(),
    content: @Composable () -> Unit,
) {
    val current = rememberUpdatedState(content)
    SwingNode(
        factory = { NestedContentPanel() },
        modifier = modifier,
        update = {
            set(layout) { if (it != null) this.layout = it }
            set(parent) { mount(it, current) }
        },
        onRelease = { dispose() },
    )
}

private class NestedContentPanel :
    JPanel(),
    DisposableHandle {
    private var mounted: DisposableHandle? = null

    fun mount(
        parent: CompositionContext,
        content: State<@Composable () -> Unit>,
    ) {
        dispose()
        mounted = setContent(parent = parent) { content.value() }
    }

    override fun dispose() {
        mounted?.dispose()
        mounted = null
    }
}
