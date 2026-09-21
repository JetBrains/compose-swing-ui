// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.GLASS_PANE_DECLARATION
import org.jetbrains.compose.swing.components.LayerScope
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.components.ownedGlassPaneDeclaration
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import javax.swing.JInternalFrame

/**
 * The receiver of an internal frame's content, declared through [DesktopPaneScope.InternalFrame].
 *
 * The frame's content lands in the frame's content pane, and [GlassPane] declares the frame's glass pane.
 *
 * ```
 * InternalFrame(title = "Editor", bounds = Rectangle(24, 24, 320, 200), onClose = { open = false }) {
 *     Editor()
 *     if (saving) {
 *         GlassPane(PanelLayout.GridBag) { ProgressBar(value = 0, indeterminate = true) }
 *     }
 * }
 * ```
 *
 * @see javax.swing.JInternalFrame
 */
@LayoutScopeMarker
public sealed interface InternalFrameScope {
    /**
     * Shows the frame's own glass pane, holding [content] under the pane's own `FlowLayout`: a
     * [PanelLayout.Flow] with its defaults.
     *
     * @param modifier the [SwingModifier] applied to the glass pane
     * @param style the [SwingStyle] applied to the glass pane
     * @param content the composable content the glass pane shows over the frame
     * @see javax.swing.JInternalFrame.getGlassPane
     */
    @Composable
    public fun GlassPane(
        modifier: SwingModifier = SwingModifier,
        style: SwingStyle<ContainerStyleScope> = SwingStyle,
        content: @Composable PanelScope.() -> Unit,
    )

    /**
     * Shows the frame's own glass pane, holding [content] under [layout].
     *
     * The pane covers the frame below its title bar and is transparent where [content] paints nothing. A
     * click over part of [content] that listens goes there, and a click anywhere else goes on to the
     * frame's content underneath. [modifier] and [style] apply to the pane itself: a mouse listener in [modifier]
     * keeps the frame's content out of reach, and a cursor in [style] shows over the frame below its title bar.
     *
     * The pane is shown, hidden and refused as [LayerScope.GlassPane] describes, with a frame in place of
     * a layer.
     *
     * @param layout the layout the pane's children are laid out under; see [PanelLayout]
     * @param modifier the [SwingModifier] applied to the glass pane
     * @param style the [SwingStyle] applied to the glass pane
     * @param content the composable content the glass pane shows over the frame, with [layout]'s scope as
     *   its receiver
     * @see javax.swing.JInternalFrame.getGlassPane
     */
    @Composable
    public fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier = SwingModifier,
        style: SwingStyle<ContainerStyleScope> = SwingStyle,
        content: @Composable S.() -> Unit,
    )
}

/** The [InternalFrameScope] one internal frame hands its content, remembered alongside the frame. */
internal class InternalFrameScopeImpl : InternalFrameScope {
    private val glassPane = ownedGlassPaneDeclaration(JInternalFrame::getGlassPane, GLASS_PANE_DECLARATION)

    @Composable
    override fun GlassPane(
        modifier: SwingModifier,
        style: SwingStyle<ContainerStyleScope>,
        content: @Composable PanelScope.() -> Unit,
    ) = glassPane.GlassPane(modifier, style, content)

    @Composable
    override fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier,
        style: SwingStyle<ContainerStyleScope>,
        content: @Composable S.() -> Unit,
    ) = glassPane.GlassPane(layout, modifier, style, content)
}
