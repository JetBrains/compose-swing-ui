// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.ToolTipElement
import java.awt.event.MouseEvent

/**
 * Sets `toolTipText` - the tooltip the component shows wherever the pointer rests on it; `null` clears
 * it. Requires a `JComponent` target.
 *
 * A component has one tooltip, so the last `toolTip` in a chain owns it, whichever of the two forms
 * declared it. For a tooltip that belongs to a place within the component - the cell, the node or the
 * shape under the pointer - declare the per-location form instead.
 *
 * @param text the tooltip for the whole component; `ToolTipManager` decides when it appears and how long
 *   it stays.
 * @return this chain with the tooltip declared on it.
 * @see javax.swing.JComponent.setToolTipText
 */
@Deprecated("Set it in the component's style: style = { toolTip(text) }")
public fun SwingModifier.toolTip(text: @Nls String?): SwingModifier =
    this then ToolTipElement(text = text, textAt = null)

/**
 * Sets the tooltip per pointer location: [text] is asked for the tooltip belonging to the place the
 * pointer is over, and answers `null` where the component has none there. Requires a `JComponent`
 * target.
 *
 * [text] is called with the [MouseEvent] the toolkit delivers, as the pointer travels the component and
 * ahead of the tooltip for that place being shown; its answer becomes the component's tooltip text from
 * then on. It runs on every pointer move, not only when a tooltip is about to appear, so an expensive
 * hit test behind it is paid continuously.
 *
 * The component is kept under `ToolTipManager` for as long as this declaration is in the chain, so a
 * component whose tooltip is declared this way alone shows one all the same.
 *
 * @param text answers the tooltip of the place the event points at; it runs on the event dispatch thread,
 *   so it has to answer without blocking.
 * @return this chain with the per-location tooltip declared on it.
 * @see javax.swing.JComponent.setToolTipText
 */
@Deprecated("Set it in the component's style: style = { toolTip(text) }")
public fun SwingModifier.toolTip(text: (event: MouseEvent) -> @Nls String?): SwingModifier =
    this then ToolTipElement(text = null, textAt = text)
