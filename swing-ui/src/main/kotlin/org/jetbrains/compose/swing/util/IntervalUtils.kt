@file:OptIn(ExperimentalContracts::class)

package org.jetbrains.compose.swing.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Calls [action] with the first and the last index of every run of adjacent indices this array holds,
 * skipping the indices [include] answers `false` for. Runs are found in the order the indices are stored,
 * so an array in increasing order - which is how a selection model reports its rows - yields them in
 * increasing order too.
 *
 * A selection model takes an interval at a time, and a run of adjacent indices is the widest interval its
 * rows can be named by. Both lambdas are inlined, so narrowing the walk costs no filtering collection.
 */
internal inline fun IntArray.forEachInterval(
    include: (Int) -> Boolean = { true },
    action: (start: Int, end: Int) -> Unit,
) {
    contract {
        callsInPlace(include)
        callsInPlace(action)
    }
    var started = false
    var start = 0
    var end = 0
    for (index in this) {
        if (!include(index)) continue
        if (!started) {
            started = true
            start = index
        } else if (index != end + 1) {
            action(start, end)
            start = index
        }
        end = index
    }
    if (started) action(start, end)
}

/** [forEachInterval] over anything that iterates its indices, such as a sorted set. */
internal inline fun Iterable<Int>.forEachInterval(
    include: (Int) -> Boolean = { true },
    action: (start: Int, end: Int) -> Unit,
) {
    contract {
        callsInPlace(include)
        callsInPlace(action)
    }
    var started = false
    var start = 0
    var end = 0
    for (index in this) {
        if (!include(index)) continue
        if (!started) {
            started = true
            start = index
        } else if (index != end + 1) {
            action(start, end)
            start = index
        }
        end = index
    }
    if (started) action(start, end)
}
