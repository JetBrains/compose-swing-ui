// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import java.awt.AWTEvent
import java.awt.event.HierarchyEvent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * A text field named `own` that its caller built and keeps in [home], recording what happens to it from then on.
 *
 * [moves] holds the name of the field's parent after each parent change of the field or of an ancestor, `null` for
 * none. [nameWrites] holds each name written, followed by ` in <parent name>` when the field had a parent then.
 */
internal class CallerOwnedField : JTextField() {
    val home = JPanel().apply { name = "home" }
    val moves = ArrayList<String?>()
    val nameWrites = ArrayList<String>()

    init {
        super.setName("own")
        home.add(this)
        enableEvents(AWTEvent.HIERARCHY_EVENT_MASK)
    }

    override fun setName(name: String?) {
        nameWrites += parent?.let { "$name in ${it.name}" } ?: "$name"
        super.setName(name)
    }

    override fun processHierarchyEvent(event: HierarchyEvent) {
        if (event.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() != 0L) moves += parent?.name
        super.processHierarchyEvent(event)
    }
}
