@file:OptIn(ExperimentalContracts::class)

package org.jetbrains.compose.swing.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Walks this list by index and calls [action] for each item in [range], the whole list by default,
 * without the iterator `forEach` allocates.
 *
 * For a list this library builds and knows to be random-access. On a list a caller hands in, which may be
 * a linked one, reading by index is what costs.
 */
internal inline fun <T> List<T>.fastForEach(
    range: IntRange = indices,
    action: (item: T) -> Unit,
) {
    contract { callsInPlace(action) }
    for (index in range) {
        action(get(index))
    }
}

/** [fastForEach] with the index of each item. */
internal inline fun <T> List<T>.fastForEachIndexed(
    range: IntRange = indices,
    action: (index: Int, item: T) -> Unit,
) {
    contract { callsInPlace(action) }
    for (index in range) {
        action(index, get(index))
    }
}
