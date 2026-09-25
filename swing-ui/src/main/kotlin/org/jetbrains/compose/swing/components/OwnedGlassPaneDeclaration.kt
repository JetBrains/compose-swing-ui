package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.node.ExistingSwingNode
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
        content: @Composable PanelScope.() -> Unit,
    ) {
        GlassPane(defaultLayout, modifier, content)
    }

    @Composable
    fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier,
        content: @Composable S.() -> Unit,
    ) {
        pane(layout, modifier) { layout.contentScope.content() }
    }
}

/**
 * The [OwnedGlassPaneDeclaration] for the glass pane [claim] returns from a [P]: the caller's modifier applied to
 * the pane, the layout installed on it and the content composed into it, the way a `Panel` declares the panel it
 * builds. The pane is shown while a declaration is composed. A scope holds one for the component it serves, and
 * its declarations share one `movableContentOf`, so that an `if`/`else` selecting between two declarations in
 * the same pass keeps one node, and one claim of the pane.
 *
 * [claim] may return any component. One that is not a `Container` is refused when the node is created,
 * naming the parent and what the claim returned.
 */
internal inline fun <reified P : Component> ownedGlassPaneDeclaration(
    noinline claim: P.() -> Component?,
): OwnedGlassPaneDeclaration =
    OwnedGlassPaneDeclaration(
        movableContentOf { layout, modifier, content ->
            ExistingSwingNode<P, Container>(
                declaringCall = GLASS_PANE_DECLARATION,
                claim = claim,
                // Last in the chain, so the pane is shown whatever the caller's modifier declares.
                modifier = modifier.visible(true),
                update = { layout.installOn(this) },
                childPlacement = ChildPlacement.Indexed,
            ) {
                key(layout.javaClass) { content() }
            }
        },
    )
