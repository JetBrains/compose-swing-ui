// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class MutableStateAutoboxingResolutionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `decides by the resolved type of the state value`(
        label: String,
        declarations: String,
        recommended: String?,
    ) {
        val imports = "import androidx.compose.runtime.*\nimport kotlinx.collections.immutable.*"
        val source = "package test\n\n$imports\n\n$declarations"
        val immutableCollections =
            """
            package kotlinx.collections.immutable
            interface ImmutableList<out E> : List<E>
            interface PersistentList<out E> : ImmutableList<E>
            interface ImmutableSet<out E> : Set<E>
            interface PersistentSet<out E> : ImmutableSet<E>
            interface ImmutableMap<K, out V> : Map<K, V>
            interface PersistentMap<K, out V> : ImmutableMap<K, V>
            """.trimIndent()

        val findings = RuntimeRuleLint.lint("MutableStateAutoboxing", source, immutableCollections)

        val expected =
            recommended?.let { type ->
                if ('<' in type) {
                    val kind = type.substringBefore('<')
                    val arguments = type.substringAfter('<').removeSuffix(">").replace(", ", "")
                    val factory = "androidx.collection.mutable$arguments${kind}Of"
                    "This state stores a `$type` with boxed elements. Boxing wraps primitive values in objects. " +
                        "Use `$factory` to create a primitive-backed collection. " +
                        "Build a new collection with the changes and assign it to state so Compose can observe the update."
                } else {
                    "This state stores a boxed `$type`. Boxing wraps primitive values in objects and can add " +
                        "allocations when the state changes. Use `mutable${type}StateOf` to store the primitive directly."
                }
            }
        assertEquals(listOfNotNull(expected), findings.map { it.message }, label)
    }

    companion object {
        private val elements = listOf("Int", "Long", "Float")

        @JvmStatic
        fun cases(): List<Arguments> = scalarCases() + typedScalarCases() + reportedCollectionCases() + cleanCases()

        private fun scalarCases(): List<Arguments> =
            listOf(
                case(
                    "call expression resolving to Int",
                    """
                    val ratios = listOf(1, 2)
                    val state = mutableStateOf(ratios.lastIndex)
                    """.trimIndent(),
                    "Int",
                ),
                case(
                    "property read",
                    """
                    val count: Int = 3
                    val state = mutableStateOf(count)
                    """.trimIndent(),
                    "Int",
                ),
                case(
                    "constant",
                    """
                    const val limit = 3L
                    val state = mutableStateOf(limit)
                    """.trimIndent(),
                    "Long",
                ),
                case(
                    "function call",
                    """
                    fun ratio(): Float = 1f
                    val state = mutableStateOf(ratio())
                    """.trimIndent(),
                    "Float",
                ),
                case(
                    "arithmetic",
                    """
                    val base = 1.5
                    val state = mutableStateOf(base * 2)
                    """.trimIndent(),
                    "Double",
                ),
            )

        private fun typedScalarCases(): List<Arguments> =
            listOf(
                case("explicit type argument over a call", "val state = mutableStateOf<Int>(listOf(1).size)", "Int"),
                case(
                    "type alias",
                    """
                    typealias Index = Int
                    val state = mutableStateOf<Index>(0)
                    """.trimIndent(),
                    "Int",
                ),
                case(
                    "fully qualified call",
                    "val state = androidx.compose.runtime.mutableStateOf(listOf(1).size)",
                    "Int",
                ),
                case(
                    "parameter in an ordinary function",
                    "fun createState(value: Int) { mutableStateOf(value) }",
                    "Int",
                ),
                case(
                    "policy passed by name",
                    "val state = mutableStateOf(listOf(1).size, policy = structuralEqualityPolicy())",
                    "Int",
                ),
            )

        private fun reportedCollectionCases(): List<Arguments> {
            val collections =
                elements.flatMap { listOf("List<$it>", "Set<$it>") } +
                    elements.flatMap { key -> elements.map { value -> "Map<$key, $value>" } }
            val parameters = collections.map { type -> case("parameter of $type", parameterOf(type), type) }
            val immutable =
                listOf(
                    "ImmutableList<Int>" to "List<Int>",
                    "PersistentList<Long>" to "List<Long>",
                    "ImmutableSet<Float>" to "Set<Float>",
                    "PersistentSet<Int>" to "Set<Int>",
                    "ImmutableMap<Int, Long>" to "Map<Int, Long>",
                    "PersistentMap<Float, Float>" to "Map<Float, Float>",
                ).map { (type, recommended) -> case("parameter of $type", parameterOf(type), recommended) }
            return parameters + immutable +
                listOf(
                    case(
                        "type alias of a collection",
                        """
                        typealias Ids = List<Int>
                        fun state(items: Ids) = mutableStateOf(items)
                        """.trimIndent(),
                        "List<Int>",
                    ),
                    case(
                        "explicit type argument over a parameter",
                        "fun state(items: List<Int>) = mutableStateOf<List<Int>>(items)",
                        "List<Int>",
                    ),
                    case(
                        "named argument",
                        "fun state(items: List<Int>) = mutableStateOf(value = items)",
                        "List<Int>",
                    ),
                    case(
                        "delegate in a function",
                        "fun state(items: List<Int>) { var copy by mutableStateOf(items) }",
                        "List<Int>",
                    ),
                    case(
                        "lambda parameter",
                        "val state = { items: Set<Int> -> mutableStateOf(items) }",
                        "Set<Int>",
                    ),
                    case(
                        "secondary constructor parameter",
                        "class Holder { constructor(items: List<Int>) { val state = mutableStateOf(items) } }",
                        "List<Int>",
                    ),
                )
        }

        private fun cleanCases(): List<Arguments> = cleanParameterCases() + cleanPolicyCases() + cleanValueCases()

        private fun cleanParameterCases(): List<Arguments> =
            listOf(
                "List<Double>",
                "Set<Double>",
                "Map<Double, Int>",
                "Map<Int, Double>",
                "Map<Int, String>",
                "Map<String, Int>",
                "List<String>",
                "List<Short>",
                "MutableList<Int>",
                "MutableSet<Int>",
                "MutableMap<Int, Int>",
                "List<Int>?",
                "List<Int?>",
                "Set<Int?>",
                "Map<Int?, Int>",
                "Map<Int, Int?>",
                "Array<Int>",
                "IntArray",
            ).map { type -> case("parameter of $type", parameterOf(type), null) } +
                unreadParameterCases("List<Int>", "emptyList<Int>()")

        private fun cleanPolicyCases(): List<Arguments> =
            listOf(
                case(
                    "non-structural policy passed by name",
                    "val state = mutableStateOf(listOf(1).size, policy = neverEqualPolicy())",
                    null,
                ),
                case(
                    "non-structural policy over a collection",
                    "fun state(items: List<Int>) = mutableStateOf(items, neverEqualPolicy())",
                    null,
                ),
                case(
                    "explicit structural policy over a collection",
                    "fun state(items: Set<Int>) = mutableStateOf(items, structuralEqualityPolicy())",
                    null,
                ),
            )

        private fun cleanValueCases(): List<Arguments> =
            listOf(
                case(
                    "nullable property read",
                    """
                    val count: Int? = 3
                    val state = mutableStateOf(count)
                    """.trimIndent(),
                    null,
                ),
                case(
                    "nullable declared state over a call",
                    "val state: MutableState<Int?> = mutableStateOf(listOf(1).size)",
                    null,
                ),
                case("string value", "val state = mutableStateOf(listOf(\"a\").first())", null),
                case(
                    "value class value",
                    """
                    @JvmInline value class Meters(val value: Int)
                    val state = mutableStateOf(Meters(listOf(1).size))
                    """.trimIndent(),
                    null,
                ),
                case("primitive state factory", "val state = mutableIntStateOf(listOf(1).size)", null),
                case(
                    "unrelated function of the same name over an Int",
                    """
                    fun <T> mutableStateOf(value: T): T = value
                    val state = mutableStateOf(listOf(1).size)
                    """.trimIndent(),
                    null,
                ),
                case(
                    "unrelated function of the same name over a collection",
                    """
                    fun <T> mutableStateOf(value: T): T = value
                    fun state(items: List<Int>) = mutableStateOf(items)
                    """.trimIndent(),
                    null,
                ),
                case(
                    "implicit lambda parameter",
                    "val state: (Set<Int>) -> MutableState<Set<Int>> = { mutableStateOf(it) }",
                    null,
                ),
                case("receiver", "fun List<Int>.state() = mutableStateOf(this)", null),
                case(
                    "primitive collection",
                    "val state = mutableStateOf(androidx.collection.mutableIntSetOf())",
                    null,
                ),
            )

        /** Values other than a function parameter read as is, which compose-rules does not report. */
        private fun unreadParameterCases(
            type: String,
            empty: String,
        ): List<Arguments> =
            listOf(
                case("call resolving to $type", "val state = mutableStateOf($empty)", null),
                case("explicit type argument over a call of $type", "val state = mutableStateOf<$type>($empty)", null),
                case(
                    "local read of $type",
                    "fun state(items: $type) { val copy = items; mutableStateOf(copy) }",
                    null,
                ),
                case(
                    "top-level property of $type",
                    "val items: $type = $empty\nval state = mutableStateOf(items)",
                    null,
                ),
                case(
                    "constructor parameter delegate of $type",
                    "class Holder(items: $type) { var state: $type by mutableStateOf(items) }",
                    null,
                ),
                case(
                    "constructor property initializer of $type",
                    "class Holder(val items: $type) { val state = mutableStateOf(items) }",
                    null,
                ),
                case(
                    "initializer block of $type",
                    "class Holder(items: $type) { init { mutableStateOf(items) } }",
                    null,
                ),
            )

        private fun parameterOf(type: String) = "fun state(items: $type) = mutableStateOf(items)"

        private fun case(
            label: String,
            declarations: String,
            recommended: String?,
        ): Arguments = Arguments.of(label, declarations, recommended)
    }
}
