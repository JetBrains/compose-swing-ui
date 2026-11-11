// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.AlignmentXDescriptor
import org.jetbrains.compose.swing.style.AlignmentYDescriptor

/**
 * Sets the horizontal alignment along the x axis, where `0.0` aligns to the left, `0.5` centers, and
 * `1.0` aligns to the right. A parent that honors alignment - a vertical `BoxLayout` - lines its
 * children up by this value, so siblings given the same alignment stay in one column.
 *
 * A component holding no alignment of its own reports the one its layout manager derives - a
 * `BoxLayout` derives it from the children present - or the centered default where none is derived.
 * Swing offers no way to hand an alignment back to the layout once one is set, so removing the modifier
 * writes the alignment the layout derives at that moment. Swing also offers no way to ask whether an
 * alignment was ever set, so an alignment a component does hold is restored only where it differs from
 * the one its layout derives as the declaration attaches. One that matches it is read as none.
 *
 * @param value the fraction of the component's width the parent lines its children up on; Swing clamps a
 *   value outside `0.0..1.0` into that range rather than rejecting it.
 * @return this modifier with the horizontal alignment declared on it.
 * @see javax.swing.JComponent.setAlignmentX
 */
@Deprecated("Set it in the component's style: style = { alignmentX(value) }")
public fun SwingModifier.alignmentX(value: Float): SwingModifier =
    property(AlignmentXDescriptor, value, restores = RestorePolicy.None)

/**
 * Sets the vertical alignment along the y axis, where `0.0` aligns to the top, `0.5` centers, and
 * `1.0` aligns to the bottom. A parent that honors alignment - a horizontal `BoxLayout` - lines its
 * children up by this value, so siblings given the same alignment stay on one row.
 *
 * Removal restores the alignment as [alignmentX] describes.
 *
 * @param value the fraction of the component's height the parent lines its children up on; Swing clamps a
 *   value outside `0.0..1.0` into that range rather than rejecting it.
 * @return this modifier with the vertical alignment declared on it.
 * @see javax.swing.JComponent.setAlignmentY
 */
@Deprecated("Set it in the component's style: style = { alignmentY(value) }")
public fun SwingModifier.alignmentY(value: Float): SwingModifier =
    property(AlignmentYDescriptor, value, restores = RestorePolicy.None)
