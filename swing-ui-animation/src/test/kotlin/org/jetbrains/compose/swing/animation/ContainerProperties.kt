package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension

// The animated containers take no `style`, so a test that needs a property on the container itself declares it on
// the modifier, through the public `property` seam.

private val ContainerPreferredSize =
    ComponentPropertyDescriptor<Component, Dimension?>(
        name = "preferredSize",
        read = { if (it.isPreferredSizeSet) it.preferredSize else null },
        write = { component, value ->
            component.preferredSize = value
            component.revalidate()
        },
    )

private val ContainerOrientation =
    ComponentPropertyDescriptor<Component, ComponentOrientation>(
        name = "componentOrientation",
        read = { it.componentOrientation },
        write = { component, value ->
            component.componentOrientation = value
            component.revalidate()
            component.repaint()
        },
    )

/** Declares the preferred size of the container itself. */
internal fun SwingModifier.containerPreferredSize(
    width: Int,
    height: Int,
): SwingModifier = property(ContainerPreferredSize, Dimension(width, height))

/** Declares the component orientation of the container itself. */
internal fun SwingModifier.containerOrientation(orientation: ComponentOrientation): SwingModifier =
    property(ContainerOrientation, orientation)
