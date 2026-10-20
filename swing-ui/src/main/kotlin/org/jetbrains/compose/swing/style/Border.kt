@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.WriteMode
import java.awt.Color
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * Sets `border`; `null` removes the border.
 *
 * The border is compared by identity, so one built inside the style is a new border on every run of the style
 * and is written again each time; build it once outside the style to leave the component's border alone.
 *
 * A component carries one border, so every border a style sets - this one, [lineBorder] and [emptyBorder]
 * alike - competes for it and the last one wins. A style that stops setting a border puts back the border the
 * component carried before.
 *
 * The look and feel of a standard component, other than a panel or a label, paints its own border and may draw
 * over or ignore one set here; put such a component in a panel and set the border on the panel instead.
 *
 * @param border the border drawn around the content and counted into the component's insets, so setting one
 *   leaves the content less room than the component's bounds.
 * @see javax.swing.JComponent.setBorder
 */
public fun SwingComponentStyleScope.border(border: Border?): Unit = BorderProperty.provide(BorderSpec.Instance(border))

/**
 * Sets a line border in [color], [thickness] thick. The border is rebuilt only when [color] or [thickness]
 * changes. See [border] for the one border a style sets.
 *
 * @param color the color of the line drawn just inside the component's edges.
 * @param thickness how wide the line is on each side, 1 by default.
 * @see javax.swing.BorderFactory.createLineBorder
 */
public fun SwingComponentStyleScope.lineBorder(
    color: Color,
    thickness: Int = 1,
): Unit = BorderProperty.provide(BorderSpec.Line(color, thickness))

/**
 * Sets an invisible border of [all] on every side. See [emptyBorder] (the four-side form).
 *
 * @param all the space reserved on each of the four sides, taken out of the area the content is laid out in.
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
public fun SwingComponentStyleScope.emptyBorder(all: Int): Unit = emptyBorder(all, all, all, all)

/**
 * Sets an invisible border of [top], [left], [bottom] and [right] - the space a component keeps around itself.
 * `margin` is the space a button or a text component keeps inside its border. The border is rebuilt only when
 * those values change. See [border] for the one border a style sets.
 *
 * @param top the space reserved above the content.
 * @param left the space reserved to the left of the content.
 * @param bottom the space reserved below the content.
 * @param right the space reserved to the right of the content.
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
public fun SwingComponentStyleScope.emptyBorder(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
): Unit = BorderProperty.provide(BorderSpec.Empty(top, left, bottom, right))

/**
 * Sets an invisible border occupying [insets]. See [emptyBorder] (the four-side form).
 *
 * @param insets the space to reserve on each side, read out of the object as the style runs.
 * @see javax.swing.BorderFactory.createEmptyBorder
 */
public fun SwingComponentStyleScope.emptyBorder(insets: Insets): Unit =
    emptyBorder(insets.top, insets.left, insets.bottom, insets.right)

internal val BorderProperty = stylePropertyOf { spec: BorderSpec -> BorderElement(spec) }

/**
 * What a declaration sets the border to be. Equal declarations are reapplied when another write replaces
 * the declared border.
 */
internal sealed interface BorderSpec {
    fun resolve(): Border?

    /**
     * A border the caller built. Two are equal only when they are the same object: an `equals` the caller wrote
     * could call two distinct borders equal and leave the component holding the wrong one.
     */
    class Instance(
        private val border: Border?,
    ) : BorderSpec {
        override fun resolve(): Border? = border

        override fun equals(other: Any?): Boolean = other is Instance && border === other.border

        override fun hashCode(): Int = System.identityHashCode(border)
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
 * Backs every border declaration with one slot: each builder produces this same element, so they share the slot
 * `border` describes.
 *
 * Two elements declaring the same [BorderSpec] are equal, so a declaration rebuilt from unchanged values is the
 * same declaration and the slot is left as it stands.
 */
internal data class BorderElement(
    private val spec: BorderSpec,
) : SwingModifier.NodeElement<JComponent, BorderElement.Node>() {
    override val name: String get() = "border"

    override val declaredValues: Map<String, Any?> get() = mapOf("border" to spec)
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.apply(spec)
    }

    override fun update(
        node: Node,
        mode: WriteMode,
    ): Boolean {
        node.apply(spec, mode)
        return node.shouldAutoInvalidate
    }

    class Node : SwingModifier.ComponentNode<JComponent>() {
        private var captured: PropertyCaptures.Capture<*, *>? = null
        private var border: Border? = null

        // The spec of the border this node wrote, or null while the component holds the original.
        private var applied: BorderSpec? = null

        override fun onAttach() {
            captured =
                checkNotNull(holder?.modifierState).captures().capture(
                    "border",
                    component,
                    { it.border },
                    { component, value -> component.border = value },
                )
        }

        /** Writes [spec]; [mode] says whether an equal [spec] is written again. */
        fun apply(
            spec: BorderSpec,
            mode: WriteMode = WriteMode.AdoptEqual,
        ) {
            val held = applied
            if (held != null && !mode.writes(held, spec) && component.border === border) return
            border = spec.resolve()
            applied = spec
            component.border = border
        }

        override fun onDetach() {
            applied = null
            captured?.release()
            captured = null
            border = null
        }
    }
}
