@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.BorderElement
import org.jetbrains.compose.swing.style.BorderSpec
import java.awt.Color
import java.awt.Insets
import javax.swing.border.Border

/**
 * Sets `border`; `null` removes the border. Requires a `JComponent` target.
 *
 * The border the caller passes is compared by identity, so one built inline is a new border on every
 * recomposition and is written again each time; hoist it into a `remember` to leave the component's
 * border alone.
 *
 * A component carries one border, so every border a chain declares - this one, [lineBorder] and
 * [emptyBorder] alike - competes for it and the last one wins. Removing the declaration puts back the
 * border the component carried before.
 *
 * The look and feel of a standard component, other than a panel or a label, paints its own border and
 * may draw over or ignore one declared here; put such a component in a panel and declare the border
 * on the panel instead.
 *
 * @param border the border drawn around the content and counted into the component's insets, so declaring
 *   one leaves the content less room than the component's bounds.
 * @return this chain with the border declared on it.
 * @see javax.swing.JComponent.setBorder
 */
@Deprecated("Set it in the component's style: style = { border(border) }")
public fun SwingModifier.border(border: Border?): SwingModifier = this then BorderElement(BorderSpec.Instance(border))

/**
 * Sets a line border in [color], [thickness] thick. The border is rebuilt only when [color] or
 * [thickness] changes, so a chain that recomposes often leaves the component's border alone. See
 * [border] for the one border a chain declares.
 *
 * @param color the color of the line drawn just inside the component's edges.
 * @param thickness how wide the line is on each side, 1 by default.
 * @return this chain with the line border declared on it.
 * @see javax.swing.BorderFactory.createLineBorder
 */
@Deprecated("Set it in the component's style: style = { lineBorder(color, thickness) }")
public fun SwingModifier.lineBorder(
    color: Color,
    thickness: Int = 1,
): SwingModifier = this then BorderElement(BorderSpec.Line(color, thickness))

/**
 * Sets an invisible border of [all] on every side. See [emptyBorder] (the four-side form).
 *
 * @param all the space reserved on each of the four sides, taken out of the area the content is laid out
 *   in.
 * @return this chain with the empty border declared on it.
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
@Deprecated("Set it in the component's style: style = { emptyBorder(all) }")
public fun SwingModifier.emptyBorder(all: Int): SwingModifier =
    this then BorderElement(BorderSpec.Empty(all, all, all, all))

/**
 * Sets an invisible border of [top], [left], [bottom] and [right] - the space a component
 * keeps around itself. [margin] is the space a button or a text component keeps inside its border. The
 * border is rebuilt only when those values change. See [border] for the one border a chain
 * declares.
 *
 * @param top the space reserved above the content.
 * @param left the space reserved to the left of the content.
 * @param bottom the space reserved below the content.
 * @param right the space reserved to the right of the content.
 * @return this chain with the empty border declared on it.
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
@Deprecated("Set it in the component's style: style = { emptyBorder(top, left, bottom, right) }")
public fun SwingModifier.emptyBorder(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
): SwingModifier = this then BorderElement(BorderSpec.Empty(top, left, bottom, right))

/**
 * Sets an invisible border occupying [insets]. See [emptyBorder] (the four-side form).
 *
 * @param insets the space to reserve on each side, read out of the object as the chain is built.
 * @return this chain with the empty border declared on it.
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
@Deprecated("Set it in the component's style: style = { emptyBorder(insets) }")
public fun SwingModifier.emptyBorder(insets: Insets): SwingModifier =
    this then BorderElement(BorderSpec.Empty(insets.top, insets.left, insets.bottom, insets.right))
