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

package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class MutableCollectionMutableStateDetectorTest {
    @Test
    fun mutableCollection_stdlib() {
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val list = mutableListOf(1)
            val set = mutableSetOf(1)
            val map = mutableMapOf(1 to 1)
            val collection: MutableCollection<Int> = list

            val listFunction = mutableStateOf(mutableListOf(1))
            val listProperty = mutableStateOf(list)

            val setFunction = mutableStateOf(mutableSetOf(1))
            val setProperty = mutableStateOf(set)

            val mapFunction = mutableStateOf(mutableMapOf(1 to 1))
            val mapProperty = mutableStateOf(map)

            val collectionProperty = mutableStateOf(collection)

            fun test(
                listParam: MutableList<Int>,
                setParam: MutableSet<Int>,
                mapParam: MutableMap<Int, Int>,
                collectionParam: MutableCollection<Int>
            ) {
                val listParameter = mutableStateOf(listParam)
                val setParameter = mutableStateOf(setParam)
                val mapParameter = mutableStateOf(mapParam)
                val collectionProperty = mutableStateOf(collectionParam)
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        val expectedLines = listOf<Int>(10, 11, 13, 14, 16, 17, 19, 27, 28, 29, 30)
        assertEquals(expectedLines.size, findings.size)
        expectedLines.zip(findings).forEach { (line, finding) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            assertEquals("Creating a MutableState object with a mutable collection type", finding.message)
            assertEquals(
                "mutableStateOf",
                source.substring(finding.entity.location.text.start, finding.entity.location.text.end),
            )
        }
    }

    @Test
    fun mutableCollection_stdlib_explicitExpressionType_noErrors() {
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val list = mutableListOf(1)
            val set = mutableSetOf(1)
            val map = mutableMapOf(1 to 1)
            val collection: MutableCollection<Int> = list

            val listFunction = mutableStateOf<List<Int>>(mutableListOf(1))
            val listProperty = mutableStateOf<List<Int>>(list)

            val setFunction = mutableStateOf<Set<Int>>(mutableSetOf(1))
            val setProperty = mutableStateOf<Set<Int>>(set)

            val mapFunction = mutableStateOf<Map<Int, Int>>(mutableMapOf(1 to 1))
            val mapProperty = mutableStateOf<Map<Int, Int>>(map)

            val collectionProperty = mutableStateOf<Collection<Int>>(collection)

            fun test(
                listParam: MutableList<Int>,
                setParam: MutableSet<Int>,
                mapParam: MutableMap<Int, Int>,
                collectionParam: MutableCollection<Int>
            ) {
                val listParameter = mutableStateOf<List<Int>>(listParam)
                val setParameter = mutableStateOf<Set<Int>>(setParam)
                val mapParameter = mutableStateOf<Map<Int, Int>>(mapParam)
                val collectionProperty = mutableStateOf<Collection<Int>>(collectionParam)
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        assertEquals(0, findings.size)
    }

    @Test
    fun mutableCollection_stdlib_explicitPropertyType_noErrors() {
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val list = mutableListOf(1)
            val set = mutableSetOf(1)
            val map = mutableMapOf(1 to 1)
            val collection: MutableCollection<Int> = list

            val listFunction: MutableState<List<Int>> = mutableStateOf(mutableListOf(1))
            val listProperty: MutableState<List<Int>> = mutableStateOf(list)

            val setFunction: MutableState<Set<Int>> = mutableStateOf(mutableSetOf(1))
            val setProperty: MutableState<Set<Int>> = mutableStateOf(set)

            val mapFunction: MutableState<Map<Int, Int>> = mutableStateOf(mutableMapOf(1 to 1))
            val mapProperty: MutableState<Map<Int, Int>> = mutableStateOf(map)

            val collectionProperty: MutableState<Collection<Int>> = mutableStateOf(collection)

            fun test(
                listParam: MutableList<Int>,
                setParam: MutableSet<Int>,
                mapParam: MutableMap<Int, Int>,
                collectionParam: MutableCollection<Int>
            ) {
                val listParameter: MutableState<List<Int>> = mutableStateOf(listParam)
                val setParameter: MutableState<Set<Int>> = mutableStateOf(setParam)
                val mapParameter: MutableState<Map<Int, Int>> = mutableStateOf(mapParam)
                val collectionProperty: MutableState<Collection<Int>> = mutableStateOf(collectionParam)
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        assertEquals(0, findings.size)
    }

    @Test
    fun mutableCollection_java() {
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val list = java.util.ArrayList<Int>()
            val set = java.util.HashSet<Int>()
            val linkedSet = java.util.LinkedHashSet<Int>()
            val map = java.util.HashMap<Int, Int>()
            val linkedMap = java.util.LinkedHashMap<Int, Int>()
            val collection: java.util.Collection<Int> = list as java.util.Collection<Int>

            val listFunction = mutableStateOf(java.util.ArrayList<Int>())
            val listProperty = mutableStateOf(list)

            val setFunction = mutableStateOf(java.util.HashSet<Int>())
            val setProperty = mutableStateOf(set)

            val linkedSetFunction = mutableStateOf(java.util.LinkedHashSet<Int>())
            val linkedSetProperty = mutableStateOf(linkedSet)

            val mapFunction = mutableStateOf(java.util.HashMap<Int, Int>())
            val mapProperty = mutableStateOf(map)

            val linkedMapFunction = mutableStateOf(java.util.LinkedHashMap<Int, Int>())
            val linkedMapProperty = mutableStateOf(linkedMap)

            val collectionProperty = mutableStateOf(collection)

            fun test(
                listParam: java.util.ArrayList<Int>,
                setParam: java.util.HashSet<Int>,
                linkedSetParam: java.util.LinkedHashSet<Int>,
                mapParam: java.util.HashMap<Int, Int>,
                linkedMapParam: java.util.LinkedHashMap<Int, Int>,
                collectionParam: java.util.Collection<Int>
            ) {
                val listParameter = mutableStateOf(listParam)
                val setParameter = mutableStateOf(setParam)
                val linkedSetParameter = mutableStateOf(linkedSetParam)
                val mapParameter = mutableStateOf(mapParam)
                val linkedMapParameter = mutableStateOf(linkedMapParam)
                val collectionProperty = mutableStateOf(collectionParam)
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        val expectedLines = listOf<Int>(12, 13, 15, 16, 18, 19, 21, 22, 24, 25, 27, 37, 38, 39, 40, 41, 42)
        assertEquals(expectedLines.size, findings.size)
        expectedLines.zip(findings).forEach { (line, finding) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            assertEquals("Creating a MutableState object with a mutable collection type", finding.message)
            assertEquals(
                "mutableStateOf",
                source.substring(finding.entity.location.text.start, finding.entity.location.text.end),
            )
        }
    }

    @Test
    fun mutableCollection_kotlinTypeAliases() {
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val list = kotlin.collections.ArrayList<Int>()
            val set = kotlin.collections.HashSet<Int>()
            val linkedSet = kotlin.collections.LinkedHashSet<Int>()
            val map = kotlin.collections.HashMap<Int, Int>()
            val linkedMap = kotlin.collections.LinkedHashMap<Int, Int>()

            val listFunction = mutableStateOf(kotlin.collections.ArrayList<Int>())
            val listProperty = mutableStateOf(list)

            val setFunction = mutableStateOf(kotlin.collections.HashSet<Int>())
            val setProperty = mutableStateOf(set)

            val linkedSetFunction = mutableStateOf(kotlin.collections.LinkedHashSet<Int>())
            val linkedSetProperty = mutableStateOf(linkedSet)

            val mapFunction = mutableStateOf(kotlin.collections.HashMap<Int, Int>())
            val mapProperty = mutableStateOf(map)

            val linkedMapFunction = mutableStateOf(kotlin.collections.LinkedHashMap<Int, Int>())
            val linkedMapProperty = mutableStateOf(linkedMap)

            fun test(
                listParam: kotlin.collections.ArrayList<Int>,
                setParam: kotlin.collections.HashSet<Int>,
                linkedSetParam: kotlin.collections.LinkedHashSet<Int>,
                mapParam: kotlin.collections.HashMap<Int, Int>,
                linkedMapParam: kotlin.collections.LinkedHashMap<Int, Int>,
            ) {
                val listParameter = mutableStateOf(listParam)
                val setParameter = mutableStateOf(setParam)
                val linkedSetParameter = mutableStateOf(linkedSetParam)
                val mapParameter = mutableStateOf(mapParam)
                val linkedMapParameter = mutableStateOf(linkedMapParam)
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        val expectedLines = listOf<Int>(11, 12, 14, 15, 17, 18, 20, 21, 23, 24, 33, 34, 35, 36, 37)
        assertEquals(expectedLines.size, findings.size)
        expectedLines.zip(findings).forEach { (line, finding) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            assertEquals("Creating a MutableState object with a mutable collection type", finding.message)
            assertEquals(
                "mutableStateOf",
                source.substring(finding.entity.location.text.start, finding.entity.location.text.end),
            )
        }
    }

    @Test
    fun mutableCollection_sourceExtensions() {
        val source =
            """
            package test

            import androidx.compose.runtime.*


            val listFunction = mutableStateOf(mutableList())
            val listProperty = mutableStateOf(MutableList)
            val listObjectImplementation = mutableStateOf(MutableListObject)
            val listSubclass = mutableStateOf(MutableListSubclass())

            val setFunction = mutableStateOf(mutableSet())
            val setProperty = mutableStateOf(MutableSet)
            val setObjectImplementation = mutableStateOf(MutableSetObject)
            val setSubclass = mutableStateOf(MutableSetSubclass())

            val mapFunction = mutableStateOf(mutableMap())
            val mapProperty = mutableStateOf(MutableMap)
            val mapObjectImplementation = mutableStateOf(MutableMapObject)
            val mapSubclass = mutableStateOf(MutableMapSubclass())

            val collectionFunction = mutableStateOf(mutableCollection())
            val collectionProperty = mutableStateOf(MutableCollection)
            val collectionObjectImplementation = mutableStateOf(MutableCollectionObject)
            val collectionSubclass = mutableStateOf(MutableCollectionSubclass())

            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source, mutableSourceDeclarations)
        val expectedLines = listOf<Int>(6, 7, 8, 9, 11, 12, 13, 14, 16, 17, 18, 19, 21, 22, 23, 24)
        assertEquals(expectedLines.size, findings.size)
        expectedLines.zip(findings).forEach { (line, finding) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            assertEquals("Creating a MutableState object with a mutable collection type", finding.message)
            assertEquals(
                "mutableStateOf",
                source.substring(finding.entity.location.text.start, finding.entity.location.text.end),
            )
        }
    }

    @Test
    fun mutableCollection_compiledExtensions() {
        val source =
            """
            package test

            import androidx.compose.runtime.*
            import stubs.*

            val listFunction = mutableStateOf(mutableList())
            val listProperty = mutableStateOf(MutableList)
            val listObjectImplementation = mutableStateOf(MutableListObject)
            val listSubclass = mutableStateOf(MutableListSubclass())

            val setFunction = mutableStateOf(mutableSet())
            val setProperty = mutableStateOf(MutableSet)
            val setObjectImplementation = mutableStateOf(MutableSetObject)
            val setSubclass = mutableStateOf(MutableSetSubclass())

            val mapFunction = mutableStateOf(mutableMap())
            val mapProperty = mutableStateOf(MutableMap)
            val mapObjectImplementation = mutableStateOf(MutableMapObject)
            val mapSubclass = mutableStateOf(MutableMapSubclass())

            val collectionFunction = mutableStateOf(mutableCollection())
            val collectionProperty = mutableStateOf(MutableCollection)
            val collectionObjectImplementation = mutableStateOf(MutableCollectionObject)
            val collectionSubclass = mutableStateOf(MutableCollectionSubclass())
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        val expectedLines = listOf<Int>(6, 7, 8, 9, 11, 12, 13, 14, 16, 17, 18, 19, 21, 22, 23, 24)
        assertEquals(expectedLines.size, findings.size)
        expectedLines.zip(findings).forEach { (line, finding) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
            assertEquals("Creating a MutableState object with a mutable collection type", finding.message)
            assertEquals(
                "mutableStateOf",
                source.substring(finding.entity.location.text.start, finding.entity.location.text.end),
            )
        }
    }

    @Test
    fun immutableCollection_stdlib_noErrors() {
        val source =
            """
            package test

            import androidx.compose.runtime.*

            val list = listOf(1)
            val set = setOf(1)
            val map = mapOf(1 to 1)
            val collection: Collection<Int> = list

            val listFunction = mutableStateOf(listOf(1))
            val listProperty = mutableStateOf(list)

            val setFunction = mutableStateOf(setOf(1))
            val setProperty = mutableStateOf(set)

            val mapFunction = mutableStateOf(mapOf(1 to 1))
            val mapProperty = mutableStateOf(map)

            val collectionProperty = mutableStateOf(collection)

            fun test(
                listParam: List<Int>,
                setParam: Set<Int>,
                mapParam: Map<Int, Int>,
                collectionParam: Collection<Int>
            ) {
                val listParameter = mutableStateOf(listParam)
                val setParameter = mutableStateOf(setParam)
                val mapParameter = mutableStateOf(mapParam)
                val collectionProperty = mutableStateOf(collectionParam)
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        assertEquals(0, findings.size)
    }

    @Test
    fun immutableCollection_sourceExtensions_noErrors() {
        val source =
            """
            package test

            import androidx.compose.runtime.*


            val listFunction = mutableStateOf(list())
            val listProperty = mutableStateOf(List)
            val listObjectImplementation = mutableStateOf(ListObject)
            val listSubclass = mutableStateOf(ListSubclass())

            val setFunction = mutableStateOf(set())
            val setProperty = mutableStateOf(Set)
            val setObjectImplementation = mutableStateOf(SetObject)
            val setSubclass = mutableStateOf(SetSubclass())

            val mapFunction = mutableStateOf(map())
            val mapProperty = mutableStateOf(Map)
            val mapObjectImplementation = mutableStateOf(MapObject)
            val mapSubclass = mutableStateOf(MapSubclass())

            val collectionFunction = mutableStateOf(collection())
            val collectionProperty = mutableStateOf(Collection)
            val collectionObjectImplementation = mutableStateOf(CollectionObject)
            val collectionSubclass = mutableStateOf(CollectionSubclass())

            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source, immutableSourceDeclarations)
        assertEquals(0, findings.size)
    }

    @Test
    fun immutableCollection_compiledExtensions_noErrors() {
        val source =
            """
            package test

            import androidx.compose.runtime.*
            import stubs.*

            val listFunction = mutableStateOf(list())
            val listProperty = mutableStateOf(List)
            val listObjectImplementation = mutableStateOf(ListObject)
            val listSubclass = mutableStateOf(ListSubclass())

            val setFunction = mutableStateOf(set())
            val setProperty = mutableStateOf(Set)
            val setObjectImplementation = mutableStateOf(SetObject)
            val setSubclass = mutableStateOf(SetSubclass())

            val mapFunction = mutableStateOf(map())
            val mapProperty = mutableStateOf(Map)
            val mapObjectImplementation = mutableStateOf(MapObject)
            val mapSubclass = mutableStateOf(MapSubclass())

            val collectionFunction = mutableStateOf(collection())
            val collectionProperty = mutableStateOf(Collection)
            val collectionObjectImplementation = mutableStateOf(CollectionObject)
            val collectionSubclass = mutableStateOf(CollectionSubclass())
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source)
        assertEquals(0, findings.size)
    }

    @Test
    fun guavaImmutableCollections_noErrors() {
        val source =
            """
            package test

            import androidx.compose.runtime.*
            import com.google.common.collect.ImmutableCollection
            import com.google.common.collect.ImmutableSet

            val set = ImmutableSet.of(1)
            val collection: ImmutableCollection<Int> = ImmutableSet.of(1)

            val collectionProperty = mutableStateOf(collection)
            val setFunction = mutableStateOf(ImmutableSet.of(1))
            val setProperty = mutableStateOf(set)

            object ImmutableCollectionObject : ImmutableCollection<Int>() {
                override val size: Int
                    get() = 0

                override fun iterator(): MutableIterator<Int> {
                    return mutableListOf<Int>().iterator()
                }
            }

            class ImmutableCollectionSubclass() : ImmutableCollection<Int>() {
                override val size: Int
                    get() = 0

                override fun iterator(): MutableIterator<Int> {
                    return mutableListOf<Int>().iterator()
                }
            }

            val subclass = ImmutableCollectionSubclass()

            val objectProperty = mutableStateOf(ImmutableCollectionObject)
            val subclassConstructor = mutableStateOf(ImmutableCollectionSubclass())
            val subclassProperty = mutableStateOf(subclass)
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("MutableCollectionMutableState", source, guavaStubs)
        assertEquals(0, findings.size)
    }

    @Test
    fun unrelatedFunctionWithSameName() {
        val source =
            """
            package test

            fun <T> mutableStateOf(value: T): T = value
            val value = mutableStateOf(mutableListOf(1))
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("MutableCollectionMutableState", source).size)
    }
}

private val mutableSourceDeclarations =
    """
    package test

    fun mutableList(): MutableList<Int> = mutableListOf()
    val MutableList: MutableList<Int> = mutableListOf()
    object MutableListObject : MutableList<Int> by mutableListOf()
    class MutableListSubclass : MutableList<Int> by mutableListOf()

    fun mutableSet(): MutableSet<Int> = mutableSetOf()
    val MutableSet: MutableSet<Int> = mutableSetOf()
    object MutableSetObject : MutableSet<Int> by mutableSetOf()
    class MutableSetSubclass : MutableSet<Int> by mutableSetOf()

    fun mutableMap(): MutableMap<Int, Int> = mutableMapOf()
    val MutableMap: MutableMap<Int, Int> = mutableMapOf()
    object MutableMapObject : MutableMap<Int, Int> by mutableMapOf()
    class MutableMapSubclass : MutableMap<Int, Int> by mutableMapOf()

    fun mutableCollection(): MutableCollection<Int> = mutableListOf()
    val MutableCollection: MutableCollection<Int> = mutableListOf()
    object MutableCollectionObject : MutableCollection<Int> by mutableListOf()
    class MutableCollectionSubclass : MutableCollection<Int> by mutableListOf()
    """.trimIndent()

private val immutableSourceDeclarations =
    """
    package test

    fun list(): List<Int> = listOf()
    val List: List<Int> = listOf()
    object ListObject : List<Int> by listOf()
    class ListSubclass : List<Int> by listOf()

    fun set(): Set<Int> = setOf()
    val Set: Set<Int> = setOf()
    object SetObject : Set<Int> by setOf()
    class SetSubclass : Set<Int> by setOf()

    fun map(): Map<Int, Int> = mapOf()
    val Map: Map<Int, Int> = mapOf()
    object MapObject : Map<Int, Int> by mapOf()
    class MapSubclass : Map<Int, Int> by mapOf()

    fun collection(): Collection<Int> = listOf()
    val Collection: Collection<Int> = listOf()
    object CollectionObject : Collection<Int> by listOf()
    class CollectionSubclass : Collection<Int> by listOf()
    """.trimIndent()

// The shape of Guava's immutable collections that androidx's test declares. They are analyzed as sources only, so they
// never reach the runtime classpath, where the compiler's own Guava lives.
private val guavaStubs =
    """
    package com.google.common.collect
    abstract class ImmutableCollection<E> : java.util.AbstractCollection<E>()
    abstract class ImmutableSet<E> : ImmutableCollection<E>(), Set<E> {
        companion object {
            fun <E> of(element: E): ImmutableSet<E> = error("stub")
        }
    }
    """.trimIndent()
