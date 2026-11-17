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

import androidx.collection.MutableScatterSet
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Returns a list containing only elements matching the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastFilter(predicate: (T) -> Boolean): List<T> {
    contract { callsInPlace(predicate) }
    val target = ArrayList<T>(size)
    fastForEach { if (predicate(it)) target += (it) }
    return target
}

/**
 * Returns a list containing only elements matching the given [predicate], applying the given
 * [transform] function to each element.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, R> List<T>.fastFilteredMap(
    predicate: (T) -> Boolean,
    transform: (T) -> R,
): List<R> {
    contract {
        callsInPlace(predicate)
        callsInPlace(transform)
    }
    val target = ArrayList<R>(size)
    fastForEach { if (predicate(it)) target += transform(it) }
    return target
}

/**
 * Returns a list containing all elements that are not null.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public fun <T : Any> List<T?>.fastFilterNotNull(): List<T> {
    val target = ArrayList<T>(size)
    fastForEach { if ((it) != null) target += (it) }
    return target
}

/**
 * Returns a list containing only elements from the given collection having distinct keys returned
 * by the given [selector] function.
 *
 * The elements in the resulting list are in the same order as they were in the source collection.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T, K> List<T>.fastDistinctBy(selector: (T) -> K): List<T> {
    contract { callsInPlace(selector) }
    val set = MutableScatterSet<K>(size)
    val target = ArrayList<T>(size)
    fastForEach { e ->
        val key = selector(e)
        if (set.add(key)) target += e
    }
    return target
}
