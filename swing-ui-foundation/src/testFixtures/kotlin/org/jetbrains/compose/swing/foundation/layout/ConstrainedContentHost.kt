// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode

/**
 * A [ConstrainedPanel] that owns a nested composition of [content], the way a component that hosts a nested
 * composition owns it in production.
 *
 * The content is mounted with [ConstrainedPanel.setContent] under [parent] while [mounted] holds, once per parent, and
 * the previous mount is disposed when [parent] changes. When [mounted] turns false the mount's handle is disposed
 * while the panel stays declared, and the content is mounted again when it turns true. A new [content] lambda
 * recomposes the mounted content rather than mounting it again. Leaving the composition disposes the mount. A `null`
 * [parent] mounts under the composition the panel's place in the Swing tree resolves to. A new [measurePolicy] builds a
 * new panel; `null` builds the panel with its default policy.
 */
@Composable
public fun ConstrainedContentHost(
    modifier: SwingModifier = SwingModifier,
    parent: CompositionContext? = rememberCompositionContext(),
    measurePolicy: MeasurePolicy? = null,
    mounted: Boolean = true,
    content:
        @Composable @SwingComposable
        ConstrainedScope.() -> Unit,
) {
    val latest = rememberUpdatedState(content)
    key(measurePolicy) {
        SwingNode(
            factory = {
                if (measurePolicy == null) MountingConstrainedPanel() else MountingConstrainedPanel(measurePolicy)
            },
            modifier = modifier,
            update = {
                set(mounted to parent) { (on, under) ->
                    if (on) mountUnder(under) { latest.value(this) } else unmount()
                }
            },
            onRelease = { unmount() },
        )
    }
}

private class MountingConstrainedPanel : ConstrainedPanel {
    constructor() : super()

    constructor(measurePolicy: MeasurePolicy) : super(measurePolicy)

    private var mount: DisposableHandle? = null

    fun mountUnder(
        parent: CompositionContext?,
        content:
            @Composable @SwingComposable
            ConstrainedScope.() -> Unit,
    ) {
        mount?.dispose()
        mount = setContent(parent, content)
    }

    fun unmount() {
        mount?.dispose()
        mount = null
    }
}
