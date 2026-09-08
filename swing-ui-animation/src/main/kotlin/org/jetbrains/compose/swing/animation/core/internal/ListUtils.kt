/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Extracted subset of androidx.compose.ui.util.ListUtils (the iteration helpers that avoid
// allocating an iterator) for compose-swing-ui's vendored animation-core. Sourced from
// AndroidX (Jetpack Compose 1.12.0), with the declarations narrowed to internal.

package org.jetbrains.compose.swing.animation.core.internal

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Iterates through a [List] using the index and calls [action] for each item. This does not
 * allocate an iterator like [Iterable.forEach].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn") // The opt-in states callsInPlace for this internal inline traversal.
@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastForEach(action: (T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in indices) {
        val item = get(index)
        action(item)
    }
}

/**
 * Returns `true` if at least one element matches the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn") // The opt-in states callsInPlace for this internal inline traversal.
@OptIn(ExperimentalContracts::class)
internal inline fun <T> List<T>.fastAny(predicate: (T) -> Boolean): Boolean {
    contract { callsInPlace(predicate) }
    fastForEach { if (predicate(it)) return true }
    return false
}

/**
 * Accumulates a value starting with [initial] and applying [operation] from left to right to
 * current accumulator value and each element.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn") // The opt-in states callsInPlace for this internal inline traversal.
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> List<T>.fastFold(initial: R, operation: (acc: R, T) -> R): R {
    contract { callsInPlace(operation) }
    var accumulator = initial
    fastForEach { e -> accumulator = operation(accumulator, e) }
    return accumulator
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element
 * in the original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn") // The opt-in states callsInPlace for this internal inline traversal.
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R> List<T>.fastMap(transform: (T) -> R): List<R> {
    contract { callsInPlace(transform) }
    val target = ArrayList<R>(size)
    fastForEach { target += transform(it) }
    return target
}

/**
 * Returns the largest value among all values produced by selector function applied to each element
 * in the collection or [defaultValue] if there are no elements.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
@Suppress("BanInlineOptIn") // The opt-in states callsInPlace for this internal inline traversal.
@OptIn(ExperimentalContracts::class)
internal inline fun <T, R : Comparable<R>> List<T>.fastMaxOfOrDefault(
    defaultValue: R,
    selector: (T) -> R,
): R {
    contract { callsInPlace(selector) }
    if (isEmpty()) return defaultValue
    var maxValue = selector(get(0))
    for (i in 1..lastIndex) {
        val v = selector(get(i))
        if (v > maxValue) maxValue = v
    }
    return maxValue
}
