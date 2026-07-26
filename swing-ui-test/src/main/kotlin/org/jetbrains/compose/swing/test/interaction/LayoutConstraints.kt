package org.jetbrains.compose.swing.test.interaction

import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

/**
 * Returns why [component] is not placed under [expected] by its parent's layout manager, or null when
 * it is. [node] names the query the message is phrased for. Must be called on the EDT.
 *
 * Each manager is asked for the placement it holds per child and reports without side effect: a region
 * for a [BorderLayout], a [GridBagConstraints] for a [GridBagLayout]. Any other manager is named in the
 * message, so the reader can tell the manager's own limit from a placement that simply differs.
 */
internal fun layoutConstraintMismatch(
    node: String,
    component: Component,
    expected: Any,
): String? {
    val parent = component.parent ?: return "Node '$node' has no parent container."
    return when (val layout = parent.layout) {
        is BorderLayout -> {
            layout.regionMismatch(node, component, expected)
        }

        is GridBagLayout -> {
            layout.cellMismatch(node, component, expected)
        }

        null -> {
            "Node '$node' parent has no layout manager, so it places no child under a constraint."
        }

        else -> {
            "Node '$node' parent is laid out by a ${layout.javaClass.simpleName}, " +
                "which reports no per-child layout constraint."
        }
    }
}

/** Why [component] is not in the [expected] region of this layout, or null when it is. */
private fun BorderLayout.regionMismatch(
    node: String,
    component: Component,
    expected: Any,
): String? {
    val actual = getConstraints(component)
    return if (actual == expected) {
        null
    } else {
        "Node '$node' is placed in the ${actual ?: "unconstrained"} region of its BorderLayout " +
            "parent, expected $expected."
    }
}

/** Why [component] is not placed under the [expected] cell constraints, or null when it is. */
private fun GridBagLayout.cellMismatch(
    node: String,
    component: Component,
    expected: Any,
): String? {
    if (expected !is GridBagConstraints) {
        return "Node '$node' is placed by a GridBagLayout, which places each child under " +
            "GridBagConstraints; the expected $expected is a ${expected.javaClass.simpleName}."
    }
    val actual = getConstraints(component)
    return if (actual.placesAs(expected)) {
        null
    } else {
        "Node '$node' is placed at ${actual.render()}, expected ${expected.render()}."
    }
}

/**
 * Whether these constraints place a child in the same cell, with the same space and the same padding,
 * as [other]. GridBagConstraints carries no equality of its own, and a layout manager answers with a
 * copy of what it was handed, so placement is compared field by field.
 */
private fun GridBagConstraints.placesAs(other: GridBagConstraints): Boolean =
    gridx == other.gridx && gridy == other.gridy &&
        gridwidth == other.gridwidth && gridheight == other.gridheight &&
        weightx == other.weightx && weighty == other.weighty &&
        anchor == other.anchor && fill == other.fill &&
        insets == other.insets && ipadx == other.ipadx && ipady == other.ipady

/** These constraints as one readable line, since GridBagConstraints renders none of its own. */
private fun GridBagConstraints.render(): String =
    "gridx=$gridx, gridy=$gridy, gridwidth=$gridwidth, gridheight=$gridheight, " +
        "weightx=$weightx, weighty=$weighty, anchor=$anchor, fill=$fill, " +
        "insets=[${insets.top},${insets.left},${insets.bottom},${insets.right}], " +
        "ipadx=$ipadx, ipady=$ipady"
