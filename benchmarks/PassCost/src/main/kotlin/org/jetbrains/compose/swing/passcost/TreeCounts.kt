package org.jetbrains.compose.swing.passcost

import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider

/** How many labels the composed [root] holds, at any depth. */
internal fun labelCount(root: Container): Int = componentsOfType(root, JLabel::class.java).size

/** How many panels the composed [root] holds, at any depth. */
internal fun panelCount(root: Container): Int = componentsOfType(root, JPanel::class.java).size

/** How many of the panels the composed [root] holds carry the name [name], at any depth. */
internal fun panelsNamed(
    root: Container,
    name: String,
): Int = componentsOfType(root, JPanel::class.java).count { it.name == name }

/** The values of the sliders the composed [root] holds, at any depth. */
internal fun sliderValues(root: Container): List<Int> = componentsOfType(root, JSlider::class.java).map { it.value }

/** The texts of the labels the composed [root] holds, at any depth. */
internal fun labelTexts(root: Container): List<String> = componentsOfType(root, JLabel::class.java).map { it.text }

/** The one component of [type] the composed [root] holds; raises where it holds any other number. */
internal fun <T : Component> singleOfType(
    root: Container,
    type: Class<T>,
): T {
    val found = componentsOfType(root, type)
    check(found.size == 1) { "the tree holds ${found.size} of ${type.simpleName} where one was composed" }
    return found.first()
}

/** Every component of [type] the composed [root] holds, at any depth, in the order a walk reaches them. */
private fun <T : Component> componentsOfType(
    root: Container,
    type: Class<T>,
): List<T> = buildList { collectOfType(root, type, this) }

private fun <T : Component> collectOfType(
    root: Container,
    type: Class<T>,
    into: MutableList<T>,
) {
    for (child in root.components) {
        if (type.isInstance(child)) into.add(type.cast(child))
        if (child is Container) collectOfType(child, type, into)
    }
}
