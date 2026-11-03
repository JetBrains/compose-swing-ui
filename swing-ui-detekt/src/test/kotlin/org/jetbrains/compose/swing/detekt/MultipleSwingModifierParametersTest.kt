package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipleSwingModifierParametersTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports each chain parameter after the first`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        assertEquals(positions, lintPositions("$HEADER\n$code"), label)
    }

    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report a single chain parameter`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), lintPositions("$HEADER\n$code"), label)
    }

    @Test
    fun `names the further parameter`() {
        val finding = lint("$HEADER\n@Composable fun Show(a: SwingModifier, b: SwingModifier) = Unit").single()

        assertTrue(finding.message.startsWith("The `b` parameter is another `SwingModifier` parameter."))
    }

    @Test
    fun `resolves the chain through wildcard imports of two packages`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.modifier.*
            import javax.swing.*

            @Composable fun Show(a: SwingModifier, b: SwingModifier) = Unit
            """.trimIndent()

        assertEquals(listOf("b: SwingModifier"), lintPositions(source))
    }

    @Test
    fun `counts a written composable function class`() {
        val code = "fun host(content: @Composable Function2<SwingModifier, SwingModifier, Unit>) = Unit"

        assertEquals(
            listOf("@Composable Function2<SwingModifier, SwingModifier, Unit>"),
            lintPositions("$HEADER\n$code"),
        )
    }

    @Test
    fun `reports a generic alias only at a use`() {
        val code =
            """
            typealias Slot<T> = @Composable (SwingModifier, SwingModifier, T) -> Unit

            fun host(content: Slot<Int>) = Unit
            """.trimIndent()

        assertEquals(listOf("Slot<Int>"), lintPositions("$HEADER\n$code"))
    }

    @Test
    fun `recognizes the composable annotation through an import alias`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable as C
            import org.jetbrains.compose.swing.modifier.SwingModifier

            @C fun Show(a: SwingModifier, b: SwingModifier) = Unit
            """.trimIndent()

        assertEquals(listOf("b: SwingModifier"), lintPositions(source))
    }

    @Test
    fun `ignores an annotation named Composable from the same package`() {
        val source =
            """
            package sample

            import org.jetbrains.compose.swing.modifier.SwingModifier

            @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
            annotation class Composable

            @Composable fun Plain(a: SwingModifier, b: SwingModifier) = Unit

            fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit) = Unit
            """.trimIndent()

        assertEquals(emptyList(), lintPositions(source))
    }

    @Test
    fun `ignores an annotation named Composable from another package`() {
        val source =
            """
            package sample

            import other.Composable
            import org.jetbrains.compose.swing.modifier.SwingModifier

            @Composable fun Show(a: SwingModifier, b: SwingModifier) = Unit

            fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit) = Unit
            """.trimIndent()

        assertEquals(emptyList(), lintPositions(source))
    }

    private fun lint(source: String) =
        MultipleSwingModifierParameters(Config.empty)
            .lintWithContext(
                environment,
                source,
                COMPOSABLE_STUBS,
                """
package org.jetbrains.compose.swing.modifier

interface SwingModifier {
    companion object : SwingModifier
}
""",
                """
package other

interface SwingModifier

interface Modifier

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
annotation class Composable
""",
            )

    /** The source each finding points at, read off the offsets because the PSI is gone once the lint returns. */
    private fun lintPositions(source: String): List<String> =
        lint(source).map { positionOf(source, it.entity.location.text.start, it.entity.location.text.end) }

    companion object {
        private val environment = createEnvironment()

        private const val HEADER = """
package sample

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
"""

        private fun case(
            label: String,
            code: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, code.trimIndent(), positions.toList())

        private fun clean(
            label: String,
            code: String,
        ): Arguments = Arguments.of(label, code.trimIndent())

        @JvmStatic
        fun reported(): List<Arguments> =
            reportedChainPositions() +
                reportedDeclarationForms() +
                reportedFunctionTypeChains() +
                reportedAliasUses() +
                reportedTypeArguments() +
                reportedOverridesAndActualsAtTheirBase()

        private fun reportedChainPositions(): List<Arguments> =
            listOf(
                case(
                    "a second chain",
                    "@Composable fun Show(a: SwingModifier = SwingModifier, b: SwingModifier = SwingModifier) = Unit",
                    "b: SwingModifier = SwingModifier",
                ),
                case(
                    "every chain after the first",
                    "@Composable fun Show(a: SwingModifier, b: SwingModifier, c: SwingModifier) = Unit",
                    "b: SwingModifier",
                    "c: SwingModifier",
                ),
                case(
                    "a chain between other parameters",
                    "@Composable fun Show(a: SwingModifier, text: String, b: SwingModifier) = Unit",
                    "b: SwingModifier",
                ),
                case(
                    "a nullable second chain",
                    "@Composable fun Show(a: SwingModifier, b: SwingModifier?) = Unit",
                    "b: SwingModifier?",
                ),
                case(
                    "a fully qualified second chain",
                    "@Composable fun Show(" +
                        "a: SwingModifier, b: org.jetbrains.compose.swing.modifier.SwingModifier) = Unit",
                    "b: org.jetbrains.compose.swing.modifier.SwingModifier",
                ),
                case(
                    "a private composable",
                    "@Composable private fun Show(a: SwingModifier, b: SwingModifier) = Unit",
                    "b: SwingModifier",
                ),
                case(
                    "an internal composable",
                    "@Composable internal fun Show(a: SwingModifier, b: SwingModifier) = Unit",
                    "b: SwingModifier",
                ),
            )

        private fun reportedDeclarationForms(): List<Arguments> =
            listOf(
                case(
                    "an imported alias",
                    """
                    import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

                    @Composable fun Show(a: Chain, b: Chain) = Unit
                    """,
                    "b: Chain",
                ),
                case(
                    "a type alias of the chain",
                    """
                    typealias Chain = SwingModifier

                    @Composable fun Show(a: Chain, b: Chain) = Unit
                    """,
                    "b: Chain",
                ),
                case(
                    "a context parameter and a value parameter",
                    """
                    context(a: SwingModifier)
                    @Composable fun Show(b: SwingModifier) = Unit
                    """,
                    "b: SwingModifier",
                ),
                case(
                    "a composable getter's context parameters",
                    """
                    context(a: SwingModifier, b: SwingModifier)
                    val label: Int
                        @Composable get() = 1
                    """,
                    "b: SwingModifier",
                ),
                case(
                    "an abstract composable",
                    """
                    abstract class Host {
                        @Composable abstract fun Show(a: SwingModifier, b: SwingModifier)
                    }
                    """,
                    "b: SwingModifier",
                ),
            )

        private fun reportedFunctionTypeChains(): List<Arguments> =
            listOf(
                case(
                    "a second chain in a function type parameter",
                    "fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit) = Unit",
                    "SwingModifier #3",
                ),
                case(
                    "a second chain in a function type property",
                    "val content: @Composable (SwingModifier, SwingModifier) -> Unit = {}",
                    "SwingModifier #3",
                ),
                case(
                    "a nullable second chain in a function type",
                    "fun host(content: @Composable (SwingModifier, SwingModifier?) -> Unit) = Unit",
                    "SwingModifier?",
                ),
                case(
                    "a nullable composable function type",
                    "fun host(content: @Composable ((SwingModifier, SwingModifier) -> Unit)?) = Unit",
                    "SwingModifier #3",
                ),
                case(
                    "every chain after the first in a function type",
                    "fun host(content: @Composable (SwingModifier, SwingModifier, SwingModifier) -> Unit) = Unit",
                    "SwingModifier #3",
                    "SwingModifier #4",
                ),
                case(
                    "a named second chain in a function type",
                    "fun host(content: @Composable (a: SwingModifier, b: SwingModifier) -> Unit) = Unit",
                    "b: SwingModifier",
                ),
                case(
                    "a context chain and a value chain in a function type",
                    "fun host(content: @Composable context(SwingModifier) (b: SwingModifier) -> Unit) = Unit",
                    "b: SwingModifier",
                ),
                case(
                    "two context chains in a function type",
                    "fun host(content: @Composable context(SwingModifier, SwingModifier) () -> Unit) = Unit",
                    "SwingModifier #3",
                ),
            )

        private fun reportedAliasUses(): List<Arguments> =
            listOf(
                case(
                    "two uses of a non-generic alias",
                    """
                    typealias Pair2 = @Composable (SwingModifier, SwingModifier) -> Unit

                    fun host(a: Pair2, b: Pair2) = Unit
                    """,
                    "Pair2 #2",
                    "Pair2 #3",
                ),
                case(
                    "a function type nested in an alias parameter",
                    """
                    typealias Pair2 = @Composable (SwingModifier, SwingModifier) -> Unit
                    typealias Outer = @Composable (Pair2) -> Unit
                    fun host(a: Outer) = Unit
                    """,
                    "Outer #2",
                ),
                case(
                    "a function type nested in an alias type argument",
                    """
                    typealias SlotList = List<@Composable (SwingModifier, SwingModifier) -> Unit>
                    fun host(b: SlotList) = Unit
                    """,
                    "SlotList #2",
                ),
                case(
                    "a generic alias supplying both chains by type argument",
                    """
                    typealias Slot<T> = @Composable (T, T) -> Unit

                    fun host(content: Slot<SwingModifier>) = Unit
                    """,
                    "Slot<SwingModifier>",
                ),
            )

        private fun reportedTypeArguments(): List<Arguments> =
            listOf(
                case(
                    "a second chain in a type argument in a body",
                    "fun host() { listOf<@Composable (SwingModifier, SwingModifier) -> Unit>() }",
                    "SwingModifier #3",
                ),
                case(
                    "a second chain in a type argument in an override's body",
                    """
                    interface Host {
                        fun host()
                    }

                    class Impl : Host {
                        override fun host() {
                            listOf<@Composable (SwingModifier, SwingModifier) -> Unit>()
                        }
                    }
                    """,
                    "SwingModifier #3",
                ),
                case(
                    "a second chain in a type argument in an override's initializer",
                    """
                    interface Host {
                        val count: Int
                    }

                    class Impl : Host {
                        override val count = listOf<@Composable (SwingModifier, SwingModifier) -> Unit>().size
                    }
                    """,
                    "SwingModifier #3",
                ),
            )

        private fun reportedOverridesAndActualsAtTheirBase(): List<Arguments> =
            reportedOverrides() + reportedOverriddenFunctionTypes() + reportedActuals()

        private fun reportedOverrides(): List<Arguments> =
            listOf(
                case(
                    "an interface declaration, once, not its override",
                    """
                    interface Host {
                        @Composable fun Show(a: SwingModifier, b: SwingModifier)
                    }

                    class Impl : Host {
                        @Composable override fun Show(a: SwingModifier, b: SwingModifier) = Unit
                    }
                    """,
                    "b: SwingModifier #1",
                ),
            )

        private fun reportedOverriddenFunctionTypes(): List<Arguments> =
            listOf(
                case(
                    "an interface function type, once, not its override",
                    """
                    interface Host {
                        fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit)
                    }

                    class Impl : Host {
                        override fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit) = Unit
                    }
                    """,
                    "SwingModifier #3",
                ),
                case(
                    "a function type property, once, not its override",
                    """
                    interface Host {
                        val content: @Composable (SwingModifier, SwingModifier) -> Unit
                    }

                    class Impl : Host {
                        override val content: @Composable (SwingModifier, SwingModifier) -> Unit = {}
                    }
                    """,
                    "SwingModifier #3",
                ),
                case(
                    "a function type in a type argument, once, not its override",
                    """
                    interface Host {
                        fun host(content: List<@Composable (SwingModifier, SwingModifier) -> Unit>)
                    }

                    class Impl : Host {
                        override fun host(content: List<@Composable (SwingModifier, SwingModifier) -> Unit>) = Unit
                    }
                    """,
                    "SwingModifier #3",
                ),
                case(
                    "a constructor property's function type override, once, at the interface",
                    """
                    interface Host {
                        val content: @Composable (SwingModifier, SwingModifier) -> Unit
                    }

                    class Impl(override val content: @Composable (SwingModifier, SwingModifier) -> Unit) : Host
                    """,
                    "SwingModifier #3",
                ),
            )

        private fun reportedActuals(): List<Arguments> =
            listOf(
                case(
                    "an expect composable, once, not its actual",
                    """
                    @Composable expect fun Show(a: SwingModifier, b: SwingModifier)

                    @Composable actual fun Show(a: SwingModifier, b: SwingModifier) = Unit
                    """,
                    "b: SwingModifier #1",
                ),
                case(
                    "an expect function type, once, not its actual",
                    """
                    expect fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit)

                    actual fun host(content: @Composable (SwingModifier, SwingModifier) -> Unit) = Unit
                    """,
                    "SwingModifier #3",
                ),
                case(
                    "an expect property's function type, once, not its actual constructor property",
                    """
                    expect class Host {
                        val content: @Composable (SwingModifier, SwingModifier) -> Unit
                    }

                    actual class Host(actual val content: @Composable (SwingModifier, SwingModifier) -> Unit)
                    """,
                    "SwingModifier #3",
                ),
            )

        @JvmStatic
        fun passed(): List<Arguments> = passedSingleOrOtherTypes() + passedNotComposable()

        private fun passedSingleOrOtherTypes(): List<Arguments> =
            listOf(
                clean(
                    "an unused alias with two chains",
                    "typealias Pair2 = @Composable (SwingModifier, SwingModifier) -> Unit",
                ),
                clean("a single chain", "@Composable fun Show(modifier: SwingModifier = SwingModifier) = Unit"),
                clean("no chain", "@Composable fun Show(text: String) = Unit"),
                clean(
                    "a chain next to another type of the same name elsewhere",
                    "@Composable fun Show(a: SwingModifier, b: other.SwingModifier) = Unit",
                ),
                clean(
                    "a chain next to another modifier type",
                    "@Composable fun Show(a: SwingModifier, b: other.Modifier) = Unit",
                ),
                clean(
                    "two types of the same name elsewhere",
                    "@Composable fun Show(a: other.SwingModifier, b: other.SwingModifier) = Unit",
                ),
                clean(
                    "a composable getter with one chain",
                    "context(a: SwingModifier)\nval label: Int\n    @Composable get() = 1",
                ),
                clean(
                    "a single chain in a function type",
                    "fun host(content: @Composable (SwingModifier) -> Unit) = Unit",
                ),
                clean(
                    "a single context chain in a function type",
                    "fun host(content: @Composable context(SwingModifier) () -> Unit) = Unit",
                ),
                clean(
                    "a generic alias with one chain",
                    """
                    typealias Slot<T> = @Composable (T, Int) -> Unit

                    fun host(content: Slot<SwingModifier>) = Unit
                    """,
                ),
            )

        private fun passedNotComposable(): List<Arguments> =
            listOf(
                clean("two chains in a plain function", "fun show(a: SwingModifier, b: SwingModifier) = Unit"),
                clean(
                    "a plain function type with two chains",
                    "fun host(content: (SwingModifier, SwingModifier) -> Unit) = Unit",
                ),
                clean(
                    "a plain generic alias with two chains",
                    """
                    typealias Slot<T> = (T, T) -> Unit

                    fun host(content: Slot<SwingModifier>) = Unit
                    """,
                ),
                clean(
                    "a plain function class written with type arguments",
                    "fun host(content: Function2<SwingModifier, SwingModifier, Unit>) = Unit",
                ),
            )
    }
}
