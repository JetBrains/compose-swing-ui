@file:JvmMultifileClass
@file:JvmName("InteractionTestKt")

package org.jetbrains.compose.swing.test.interaction

import java.awt.Component

/**
 * Casts this component to [T], or fails with a readable type-mismatch message naming the actual and
 * expected types. Shared by the typed `fetch`/`fetchAll` accessors of the node and window
 * interactions; [noun] names the kind of component ("Node", "Window") and [query] the query it came
 * from. A [java.awt.Window] is a [Component], so this single helper covers both families.
 */
@PublishedApi
internal inline fun <reified T : Component> Component.castOrFail(
    noun: String,
    query: String,
): T =
    this as? T
        ?: throw AssertionError(
            "$noun '$query' is a ${this.javaClass.simpleName}, " +
                "expected a ${T::class.java.simpleName}.",
        )
