// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import java.awt.Container
import javax.swing.JFrame
import javax.swing.JToolBar
import javax.swing.plaf.basic.BasicToolBarUI

/**
 * The look and feel's own tool bar UI, through which a case makes the moves a drag would make: floating
 * the bar out, and docking it back on an edge of the container it came from.
 */
internal val JToolBar.toolBarUi: BasicToolBarUI
    get() = ui as BasicToolBarUI

/** Whether the bar stands in a window of its own, read the way the wrapper itself reads it. */
internal val JToolBar.isFloatingNow: Boolean
    get() = (ui as? BasicToolBarUI)?.isFloating == true

/**
 * The single [JToolBar] belonging to [frame], wherever it currently stands: a floating bar has left the
 * frame's own tree for a window the look and feel opens, which it owns from [frame].
 *
 * The search stays within [frame] and the windows it owns, so a bar another case left standing in this
 * JVM is never mistaken for this one's.
 */
internal fun toolBarIn(frame: JFrame): JToolBar? {
    val bars = mutableListOf<JToolBar>()

    fun visit(container: Container) {
        for (child in container.components) {
            if (child is JToolBar) bars += child
            if (child is Container) visit(child)
        }
    }
    visit(frame)
    frame.ownedWindows.forEach { visit(it) }
    return bars.firstOrNull()
}
