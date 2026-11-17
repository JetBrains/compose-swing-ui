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
 * Returns a list containing the results of applying the given [transform] function to each element
 * in the original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastMap(transform: (T) -> R): List<R> {
    contract { callsInPlace(transform) }
    val target = ArrayList<R>(size)
    fastForEach { target += transform(it) }
    return target
}

/**
 * Applies the given [transform] function to each element of the original collection and appends the
 * results to the given [destination].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R, C : MutableCollection<in R>> List<T>.fastMapTo(
    destination: C,
    transform: (T) -> R,
): C {
    contract { callsInPlace(transform) }
    fastForEach { item -> destination.add(transform(item)) }
    return destination
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element
 * in the original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastMapIndexed(transform: (index: Int, T) -> R): List<R> {
    contract { callsInPlace(transform) }
    val target = ArrayList<R>(size)
    fastForEachIndexed { index, e -> target += transform(index, e) }
    return target
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element
 * in the original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastMapIndexedNotNull(transform: (index: Int, T) -> R?): List<R> {
    contract { callsInPlace(transform) }
    val target = ArrayList<R>(size)
    fastForEachIndexed { index, e -> transform(index, e)?.let { target += it } }
    return target
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element
 * in the original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastMapNotNull(transform: (T) -> R?): List<R> {
    contract { callsInPlace(transform) }
    val target = ArrayList<R>(size)
    fastForEach { e -> transform(e)?.let { target += it } }
    return target
}

/**
 * Returns a single list of all elements yielded from results of [transform] function being invoked
 * on each element of original collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastFlatMap(transform: (T) -> Iterable<R>): List<R> {
    contract { callsInPlace(transform) }
    val target = ArrayList<R>(size)
    fastForEach { e ->
        val list = transform(e)
        target.addAll(list)
    }
    return target
}
