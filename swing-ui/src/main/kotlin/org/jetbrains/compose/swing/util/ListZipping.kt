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
 * Returns a list of values built from the elements of `this` collection and the [other] collection
 * with the same index using the provided [transform] function applied to each pair of elements. The
 * returned list has length of the shortest collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R, V> List<T>.fastZip(
    other: List<R>,
    transform: (a: T, b: R) -> V,
): List<V> {
    contract { callsInPlace(transform) }
    val minSize = minOf(size, other.size)
    val target = ArrayList<V>(minSize)
    for (i in 0 until minSize) {
        target += (transform(get(i), other[i]))
    }
    return target
}

/**
 * Returns a list containing the results of applying the given [transform] function to each pair of
 * two adjacent elements in this collection.
 *
 * The returned list is empty if this collection contains less than two elements.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastZipWithNext(transform: (T, T) -> R): List<R> {
    contract { callsInPlace(transform) }
    if (size <= 1) return emptyList()
    val result = mutableListOf<R>()
    var current = get(0)
    // `until` as we don't want to invoke this for the last element, since that won't have a `next`
    for (i in 0 until lastIndex) {
        val next = get(i + 1)
        result.add(transform(current, next))
        current = next
    }
    return result
}
