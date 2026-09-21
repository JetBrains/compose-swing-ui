// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JComponent
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/** Runs [gate] and asserts it fails with the restore check's report of an [UnrestoredName]. */
inline fun assertReportsTheUnrestoredName(gate: () -> Unit) {
    val message = assertFailsWith<AssertionError>(block = gate).message.orEmpty()
    assertContains(message, "departing unrestoredName declaration")
    assertContains(message, "left <left-behind>")
}

/**
 * A declaration that writes the component's name and, on removal, leaves what it wrote standing - the
 * defect the restore check exists to catch. It carries nothing, so one declaration of it is equal to
 * every other.
 */
class UnrestoredName : SwingModifier.NodeElement<JComponent, UnrestoredName.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val name: String get() = "unrestoredName"

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.name = "left-behind"
    }

    override fun equals(other: Any?): Boolean = other is UnrestoredName

    override fun hashCode(): Int = javaClass.hashCode()

    /**
     * The node that hands [UnrestoredName] the component it writes to. It holds no state beyond that
     * component, so a test can read the name left on the component after the declaration is removed.
     */
    class Node : SwingModifier.ComponentNode<JComponent>()
}
