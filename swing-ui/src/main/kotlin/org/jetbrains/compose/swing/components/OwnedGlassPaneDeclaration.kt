// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.components.style.GlassPanes
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.ExistingSwingNode
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.apply
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.visible
import java.awt.Component
import java.awt.Container

/**
 * The two `GlassPane` declarations a scope offers for the glass pane its parent builds, made by
 * [ownedGlassPaneDeclaration].
 */
internal class OwnedGlassPaneDeclaration(
    internal val pane: @Composable (PanelLayout<*>, SwingModifier, @Composable () -> Unit) -> Unit,
) {
    internal val defaultLayout: PanelLayout<PanelScope> = PanelLayout.Flow()

    @Composable
    fun GlassPane(
        modifier: SwingModifier,
        style: SwingStyle<ContainerStyleScope>,
        content: @Composable PanelScope.() -> Unit,
    ) {
        GlassPane(defaultLayout, modifier, style, content)
    }

    @Composable
    fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier,
        style: SwingStyle<ContainerStyleScope>,
        content: @Composable S.() -> Unit,
    ) {
        // The forced show runs after the caller's style, so the pane is shown whatever visibility that style sets.
        pane(
            layout,
            modifier.styleable(GlassPaneKind) {
                apply(style)
                visible(true)
            },
        ) { layout.contentScope.content() }
    }
}

/**
 * The [OwnedGlassPaneDeclaration] for the glass pane [claim] returns from a [P]: the caller's modifier applied to
 * the pane, the layout installed on it and the content composed into it, the way a `Panel` declares the panel it
 * builds. The pane is shown while a declaration is composed. A scope holds one for the component it serves, and
 * its declarations share one `movableContentOf`, so that an `if`/`else` selecting between two declarations in
 * the same pass keeps one node, and one claim of the pane.
 *
 * @param claim finds the glass pane on the parent. It may return any component; one that is not a `Container`
 *   is refused when the node is created, naming the parent and what the claim returned.
 * @param declaringCall names the declaration in the refusal of a second one under a parent that uses
 *   indexed children; `null` where the parent's slot names the pane.
 */
internal inline fun <reified P : Component> ownedGlassPaneDeclaration(
    noinline claim: P.() -> Component?,
    declaringCall: String?,
): OwnedGlassPaneDeclaration =
    OwnedGlassPaneDeclaration(
        movableContentOf { layout, modifier, content ->
            ExistingSwingNode<P, _, Container>(
                claim = claim,
                modifier = modifier,
                update = { layout.installOn(this) },
                declaringCall = declaringCall,
            ) {
                key(layout.javaClass) { content() }
            }
        },
    )

/** [ContainerStyleScope] for GlassPane styles. No component state is supplied. */
private val GlassPaneKind =
    nestingStyleKind<ContainerStyleScope, Unit>(
        { base, _ -> object : ContainerStyleScope, NestedStyleProviderScope by base {} },
    ) {
        applyNestedStyle(GlassPanes)
    }
