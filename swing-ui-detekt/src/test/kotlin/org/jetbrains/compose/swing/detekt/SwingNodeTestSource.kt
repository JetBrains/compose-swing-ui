// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Finding

/** The `Composable` annotation of the Compose runtime. */
internal const val COMPOSABLE_STUBS = """
package androidx.compose.runtime

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPE,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class Composable
"""

/** `SwingNodeUpdater`, with the `set` and `update` writes the update rules look for, and the `init` and `reconcile`. */
internal const val NODE_UPDATER_STUBS = """
package org.jetbrains.compose.swing.node

import java.awt.Component

public class SwingNodeUpdater<T : Component> {
    public fun <V> set(value: V, block: T.(V) -> Unit): Unit = Unit

    public fun <V> update(value: V, block: T.(V) -> Unit): Unit = Unit

    public fun init(block: T.() -> Unit): Unit = Unit

    public fun reconcile(block: T.() -> Unit): Unit = Unit
}

public fun <T : Component> SwingNodeUpdater<T>.reconcileWithChildren(
    block: ReconcileWithChildrenScope<T>.() -> Unit,
): Unit = Unit

public sealed interface ReconcileWithChildrenScope<out T : Component> {
    public val component: T

    public fun restoreDeclaredPlacement()
}
"""

/** `SwingNode` and `MenuNode` with an `update` lambda on a `SwingNodeUpdater`. Used with [NODE_UPDATER_STUBS]. */
internal const val UPDATING_NODE_STUBS = """
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import java.awt.Component

@Composable
public fun <T : Component> SwingNode(factory: () -> T, update: SwingNodeUpdater<T>.() -> Unit = {}) {}

@Composable
public fun <T : Component> MenuNode(factory: () -> T, update: SwingNodeUpdater<T>.() -> Unit = {}) {}
"""

/** The source each finding points at, read off the offsets because the PSI is gone once the lint returns. */
internal fun List<Finding>.textsIn(source: String): List<String> =
    map { source.substring(it.entity.location.text.start, it.entity.location.text.end) }

/** The text a finding covers, with ` #n` appended when the text occurs more than once: its n-th occurrence. */
internal fun positionOf(
    source: String,
    start: Int,
    end: Int,
): String {
    val text = source.substring(start, end)
    val starts =
        generateSequence(source.indexOf(text)) {
            source.indexOf(text, it + 1).takeIf { next ->
                next >= 0
            }
        }
    return if (starts.count() > 1) "$text #${starts.indexOf(start) + 1}" else text
}
