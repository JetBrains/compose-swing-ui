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
 *
 * The functions in this file are forked from
 * compose/ui/ui-util/src/commonMain/kotlin/androidx/compose/ui/util/ListUtils.kt in AndroidX's
 * ui-util; see this module's META-INF/NOTICE for the synced version.
 */

@file:JvmMultifileClass
@file:JvmName("ListUtilsKt")
@file:OptIn(ExperimentalContracts::class)

package org.jetbrains.compose.swing.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Returns the sum of all values produced by [selector] function applied to each element in the
 * list.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastSumBy(selector: (T) -> Int): Int {
    contract { callsInPlace(selector) }
    var sum = 0
    fastForEach { element -> sum += selector(element) }
    return sum
}

/**
 * Accumulates value starting with [initial] value and applying [operation] from left to right to
 * current accumulator value and each element.
 *
 * Returns the specified [initial] value if the collection is empty.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastFold(
    initial: R,
    operation: (acc: R, T) -> R,
): R {
    contract { callsInPlace(operation) }
    var accumulator = initial
    fastForEach { e -> accumulator = operation(accumulator, e) }
    return accumulator
}

/**
 * Accumulates value starting with the first element and applying [operation] from left to right to
 * current accumulator value and each element.
 *
 * Throws an exception if this collection is empty. If the collection can be empty in an expected
 * way, please use [reduceOrNull] instead. It returns `null` when its receiver is empty.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 *
 * @param [operation] function that takes current accumulator value and an element, and calculates
 *   the next accumulator value.
 * @throws UnsupportedOperationException if this collection is empty
 */
public inline fun <S, T : S> List<T>.fastReduce(operation: (acc: S, T) -> S): S {
    contract { callsInPlace(operation) }
    if (isEmpty()) throw UnsupportedOperationException("Empty collection can't be reduced.")
    var accumulator: S = first()
    for (i in 1..lastIndex) {
        accumulator = operation(accumulator, get(i))
    }
    return accumulator
}

/**
 * Returns the first element yielding the largest value of the given function or `null` if there are
 * no elements.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R : Comparable<R>> List<T>.fastMaxBy(selector: (T) -> R): T? {
    contract { callsInPlace(selector) }
    if (isEmpty()) return null
    var maxElem = get(0)
    var maxValue = selector(maxElem)
    for (i in 1..lastIndex) {
        val e = get(i)
        val v = selector(e)
        if (maxValue < v) {
            maxElem = e
            maxValue = v
        }
    }
    return maxElem
}

/**
 * Returns the largest value among all values produced by selector function applied to each element
 * in the collection or null if there are no elements.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R : Comparable<R>> List<T>.fastMaxOfOrNull(selector: (T) -> R): R? {
    contract { callsInPlace(selector) }
    if (isEmpty()) return null
    var maxValue = selector(get(0))
    for (i in 1..lastIndex) {
        val v = selector(get(i))
        if (v > maxValue) maxValue = v
    }
    return maxValue
}

/**
 * Returns the largest value among all values produced by selector function applied to each element
 * in the collection or [defaultValue] if there are no elements.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R : Comparable<R>> List<T>.fastMaxOfOrDefault(
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

/**
 * Returns the first element yielding the smallest value of the given function or `null` if there are
 * no elements.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R : Comparable<R>> List<T>.fastMinByOrNull(selector: (T) -> R): T? {
    contract { callsInPlace(selector) }
    if (isEmpty()) return null
    var minElem = get(0)
    var minValue = selector(minElem)
    for (i in 1..lastIndex) {
        val e = get(i)
        val v = selector(e)
        if (minValue > v) {
            minElem = e
            minValue = v
        }
    }
    return minElem
}
