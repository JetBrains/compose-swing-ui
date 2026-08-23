package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.Nls

/** The items [declared] names, held as a list of this composition's own. */
@Composable
internal fun <T> rememberDeclaredList(declared: List<T>): List<T> = rememberDeclaredCopy(declared) { it.toList() }

/** The labels [declared] names, held as a map of this composition's own. */
@Composable
internal fun rememberDeclaredLabels(declared: Map<Int, @Nls String>?): Map<Int, @Nls String>? =
    rememberDeclaredCopy(declared) { it?.toMap() }

/**
 * [declared] as a collection of this composition's own: the one held since the last pass where the
 * declaration still equals it, and otherwise a fresh [copy] of it, held in its place.
 *
 * The held collection is what the next pass's declaration is compared against: a caller may keep the values
 * in a collection of their own and mutate that collection in place, and a comparison against the caller's
 * collection would be that collection against itself and never find a difference. Reading the values while
 * composing - to compare them, and to copy them where they differ - is what makes this composition one of
 * the collection's readers, so a caller keeping them in a snapshot collection has an in-place mutation
 * invalidate this composition. The read has to stay here rather than move into the apply block that
 * consumes them: an apply block reads outside this composition, and the subscription would be lost.
 *
 * The same instance stands for as long as the declared values do, so a pass that changes nothing about them
 * copies nothing. A `null` declaration is held as itself: there are no values to read and none to copy.
 */
@Composable
private fun <C> rememberDeclaredCopy(
    declared: C,
    copy: (C) -> C,
): C {
    val held = remember { arrayOfNulls<Any>(1) }

    // The cell carries only what this function put there: a copy it returned, or the null that stands for
    // a null declaration and for the pass before the first one.
    @Suppress("UNCHECKED_CAST")
    val standing = held[0] as C
    return if (standing == declared) {
        standing
    } else {
        copy(declared).also { held[0] = it }
    }
}
