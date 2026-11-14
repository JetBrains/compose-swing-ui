/*
 * Copyright 2021 The Android Open Source Project
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

package stubs

/** Returns an empty mutable list. */
public fun mutableList(): MutableList<Int> = mutableListOf()

/** An initially empty mutable list. */
public val MutableList: MutableList<Int> = mutableListOf()

/** An initially empty mutable list singleton. */
public object MutableListObject : MutableList<Int> by mutableListOf()

/** An initially empty mutable list implementation. */
public class MutableListSubclass : MutableList<Int> by mutableListOf()

/** Returns an empty mutable set. */
public fun mutableSet(): MutableSet<Int> = mutableSetOf()

/** An initially empty mutable set. */
public val MutableSet: MutableSet<Int> = mutableSetOf()

/** An initially empty mutable set singleton. */
public object MutableSetObject : MutableSet<Int> by mutableSetOf()

/** An initially empty mutable set implementation. */
public class MutableSetSubclass : MutableSet<Int> by mutableSetOf()

/** Returns an empty mutable map. */
public fun mutableMap(): MutableMap<Int, Int> = mutableMapOf()

/** An initially empty mutable map. */
public val MutableMap: MutableMap<Int, Int> = mutableMapOf()

/** An initially empty mutable map singleton. */
public object MutableMapObject : MutableMap<Int, Int> by mutableMapOf()

/** An initially empty mutable map implementation. */
public class MutableMapSubclass : MutableMap<Int, Int> by mutableMapOf()

/** Returns an empty mutable collection. */
public fun mutableCollection(): MutableCollection<Int> = mutableListOf()

/** An initially empty mutable collection. */
public val MutableCollection: MutableCollection<Int> = mutableListOf()

/** An initially empty mutable collection singleton. */
public object MutableCollectionObject : MutableCollection<Int> by mutableListOf()

/** An initially empty mutable collection implementation. */
public class MutableCollectionSubclass : MutableCollection<Int> by mutableListOf()

/** Returns an empty read-only list. */
public fun list(): List<Int> = listOf()

/** An initially empty read-only list. */
public val List: List<Int> = listOf()

/** An initially empty read-only list singleton. */
public object ListObject : List<Int> by listOf()

/** An initially empty read-only list implementation. */
public class ListSubclass : List<Int> by listOf()

/** Returns an empty read-only set. */
public fun set(): Set<Int> = setOf()

/** An initially empty read-only set. */
public val Set: Set<Int> = setOf()

/** An initially empty read-only set singleton. */
public object SetObject : Set<Int> by setOf()

/** An initially empty read-only set implementation. */
public class SetSubclass : Set<Int> by setOf()

/** Returns an empty read-only map. */
public fun map(): Map<Int, Int> = mapOf()

/** An initially empty read-only map. */
public val Map: Map<Int, Int> = mapOf()

/** An initially empty read-only map singleton. */
public object MapObject : Map<Int, Int> by mapOf()

/** An initially empty read-only map implementation. */
public class MapSubclass : Map<Int, Int> by mapOf()

/** Returns an empty read-only collection. */
public fun collection(): Collection<Int> = listOf()

/** An initially empty read-only collection. */
public val Collection: Collection<Int> = listOf()

/** An initially empty read-only collection singleton. */
public object CollectionObject : Collection<Int> by listOf()

/** An initially empty read-only collection implementation. */
public class CollectionSubclass : Collection<Int> by listOf()
