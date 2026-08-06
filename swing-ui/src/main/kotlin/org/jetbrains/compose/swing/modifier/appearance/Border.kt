@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
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
 * The look and feel of a standard component other than a panel or a label paints that component's own
 * border, and may draw over or ignore one declared here; put such a component in a panel and declare
 * the border on the panel instead.
 *
 * @see javax.swing.JComponent.setBorder
 */
public fun SwingModifier.border(border: Border?): SwingModifier = this then BorderElement(BorderSpec.Instance(border))

/**
 * Sets a line border [thickness] pixels wide in [color]. The border is rebuilt only when [color] or
 * [thickness] changes, so a chain that recomposes often leaves the component's border alone. See
 * [border] for the one border a chain declares.
 *
 * @see javax.swing.BorderFactory.createLineBorder
 */
public fun SwingModifier.lineBorder(
    color: Color,
    thickness: Int = 1,
): SwingModifier = this then BorderElement(BorderSpec.Line(color, thickness))

/**
 * Sets an invisible border [all] pixels wide on every side. See [emptyBorder] (the four-side form).
 *
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
public fun SwingModifier.emptyBorder(all: Int): SwingModifier = emptyBorder(all, all, all, all)

/**
 * Sets an invisible border occupying [top], [left], [bottom] and [right] pixels - the space a component
 * keeps around itself. [margin] is the space a button or a text component keeps inside its border. The
 * border is rebuilt only when those pixel counts change. See [border] for the one border a chain
 * declares.
 *
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
public fun SwingModifier.emptyBorder(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
): SwingModifier = this then BorderElement(BorderSpec.Empty(top, left, bottom, right))

/**
 * Sets an invisible border occupying [insets]. See [emptyBorder] (the four-side form).
 *
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
public fun SwingModifier.emptyBorder(insets: Insets): SwingModifier =
    emptyBorder(insets.top, insets.left, insets.bottom, insets.right)

/**
 * What a chain declared the border to be. Equality decides whether the border is built and written at
 * all, so a declaration made of values - a colour, a count of pixels - survives a recomposition without
 * exchanging the component's border for an equal one.
 */
private sealed interface BorderSpec {
    fun resolve(): Border?

    /** A border the caller built. Two are the same only when they are the same object. */
    data class Instance(
        val border: Border?,
    ) : BorderSpec {
        override fun resolve(): Border? = border
    }

    data class Line(
        val color: Color,
        val thickness: Int,
    ) : BorderSpec {
        override fun resolve(): Border = BorderFactory.createLineBorder(color, thickness)
    }

    data class Empty(
        val top: Int,
        val left: Int,
        val bottom: Int,
        val right: Int,
    ) : BorderSpec {
        override fun resolve(): Border = BorderFactory.createEmptyBorder(top, left, bottom, right)
    }
}

/**
 * Backs every border declaration with one slot: each builder produces this same element, so they share
 * the key that identifies the property and the last declaration in the chain owns the border.
 */
private class BorderElement(
    private val spec: BorderSpec,
) : SwingModifier.Element<JComponent, BorderElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.apply(spec)
    }

    class Node : SwingModifier.Node<JComponent>() {
        private var original: Border? = null
        private var applied: BorderSpec? = null

        override fun onAttach() {
            original = component.border
        }

        /** Writes the border [spec] describes, unless the one already written came from an equal spec. */
        fun apply(spec: BorderSpec) {
            if (spec == applied) return
            applied = spec
            component.border = spec.resolve()
        }

        override fun onDetach() {
            component.border = original
        }
    }
}
