// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.PropertyCaptures
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.runReportingUncaught
import java.awt.Color
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * Sets the component's border.
 * `null` removes it.
 *
 * Keep the border instance stable by creating it outside the style: borders compare by identity, so one
 * built inside the style is a new border on every run.
 * The last border declaration wins across [border], [lineBorder] and [emptyBorder].
 * A style that stops setting a border restores the component's previous border.
 *
 * A standard component other than a panel or label may draw over or ignore this border.
 * For such a component, set the border on a surrounding panel.
 *
 * @param border the border around the content.
 *   Its insets reduce the space available for the content.
 * @see javax.swing.JComponent.setBorder
 */
public fun SwingComponentStyleScope.border(border: Border?): Unit = BorderProperty.provide(BorderSpec.Instance(border))

/**
 * Sets a line border in [color], [thickness] thick.
 * See [border] for precedence and restoration.
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
 * Sets an invisible border with the specified space on each side.
 * `margin` is the space a button or text component keeps inside its border.
 * See [border] for precedence and restoration.
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

        /** Writes [spec] unless the component still carries the border built from an equal spec. */
        fun apply(spec: BorderSpec) {
            val held = applied
            if (held == spec && component.border === border) return
            border = spec.resolve()
            applied = spec
            runReportingUncaught { component.border = border }
        }

        override fun onDetach() {
            applied = null
            captured?.release()
            captured = null
            border = null
        }
    }
}
