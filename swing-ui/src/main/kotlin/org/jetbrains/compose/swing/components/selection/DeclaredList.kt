package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The items [declared] names, held as a list of this composition's own.
 *
 * The held list is what the next pass's items are compared against: a caller may keep the items in a list
 * of their own and mutate that list in place, and a comparison against the caller's list would be that
 * list against itself and never find a difference. Reading the items here - to compare them, and to copy
 * them where they differ - is what makes this composition one of the list's readers, so a caller keeping
 * them in a snapshot list has an in-place mutation invalidate this composition.
 *
 * The same instance stands for as long as the declared items do, so a pass that changes nothing about them
 * copies nothing.
 */
@Composable
internal fun <T> rememberDeclaredList(declared: List<T>): List<T> {
    val held = remember { arrayOfNulls<List<T>>(1) }
    val standing = held[0]
    return if (standing != null && standing == declared) {
        standing
    } else {
        declared.toList().also { held[0] = it }
    }
}
