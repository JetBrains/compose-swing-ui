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
 * Iterates through a [List] using the index and calls [action] for each item. This does not
 * allocate an iterator like [Iterable.forEach].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastForEach(action: (T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in indices) {
        val item = get(index)
        action(item)
    }
}

/**
 * Iterates through a [List] in reverse order using the index and calls [action] for each item. This
 * does not allocate an iterator like [Iterable.forEach].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastForEachReversed(action: (T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in indices.reversed()) {
        val item = get(index)
        action(item)
    }
}

/**
 * Iterates through a [List] using the index and calls [action] for each item. This does not
 * allocate an iterator like [Iterable.forEachIndexed].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastForEachIndexed(action: (Int, T) -> Unit) {
    contract { callsInPlace(action) }
    for (index in indices) {
        val item = get(index)
        action(index, item)
    }
}

/**
 * Calls [action] for the item at each index in [range], reading items by index rather than through an
 * iterator.
 *
 * Use it only for a list this library builds and knows to be random-access.
 */
internal inline fun <T> List<T>.fastForEachIn(
    range: IntRange,
    action: (T) -> Unit,
) {
    for (index in range) {
        action(get(index))
    }
}
