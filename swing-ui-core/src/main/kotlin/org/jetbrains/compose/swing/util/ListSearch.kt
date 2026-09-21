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
 * Returns `true` if all elements match the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastAll(predicate: (T) -> Boolean): Boolean {
    contract { callsInPlace(predicate) }
    fastForEach { if (!predicate(it)) return false }
    return true
}

/**
 * Returns `true` if at least one element matches the given [predicate].
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastAny(predicate: (T) -> Boolean): Boolean {
    contract { callsInPlace(predicate) }
    fastForEach { if (predicate(it)) return true }
    return false
}

/**
 * Returns the first value that [predicate] returns `true` for.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 *
 * @throws [NoSuchElementException] if no such element is found
 */
public inline fun <T> List<T>.fastFirst(predicate: (T) -> Boolean): T {
    contract { callsInPlace(predicate) }
    fastForEach { if (predicate(it)) return it }
    throw NoSuchElementException("Collection contains no element matching the predicate.")
}

/**
 * Returns the first value that [predicate] returns `true` for or `null` if nothing matches.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastFirstOrNull(predicate: (T) -> Boolean): T? {
    contract { callsInPlace(predicate) }
    fastForEach { if (predicate(it)) return it }
    return null
}

/**
 * Returns the last element matching the given [predicate], or `null` if no such element was found.
 *
 * **Do not use for collections that come from public APIs**, since they may not support random
 * access in an efficient way, and this method may actually be a lot slower. Only use for
 * collections that are created by code we control and are known to support random access.
 */
public inline fun <T> List<T>.fastLastOrNull(predicate: (T) -> Boolean): T? {
    contract { callsInPlace(predicate) }
    for (index in indices.reversed()) {
        val item = get(index)
        if (predicate(item)) return item
    }
    return null
}
