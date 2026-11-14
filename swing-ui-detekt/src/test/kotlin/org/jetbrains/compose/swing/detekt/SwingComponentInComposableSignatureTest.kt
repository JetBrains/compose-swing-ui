// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwingComponentInComposableSignatureTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource(
        "org.jetbrains.compose.swing.detekt.SwingComponentInComposableSignatureTest\$ReportedCases#reported",
        "org.jetbrains.compose.swing.detekt.SwingComponentInComposableSignatureTest\$AliasCases#reported",
    )
    fun `reports a component in the signature at each position`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        assertEquals(positions, lintPositions(code), label)
    }

    @ParameterizedTest(name = "passes {0}")
    @MethodSource(
        "org.jetbrains.compose.swing.detekt.SwingComponentInComposableSignatureTest\$PassedCases#passed",
    )
    fun `does not report a signature free of components`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), lintPositions(code), label)
    }

    @Test
    fun `names the position and the type written there`() {
        val finding = lint("@Composable fun Show(label: JLabel) {}").single()

        assertEquals(
            "The `label` parameter, `JLabel`, names a Swing component. A caller that holds the component " +
                "can change what the composition declares on it, so the composition is no longer the only writer " +
                "of its properties. Take the data, callbacks, value types or models the component shows instead. " +
                "If this is API for writing components, mark it `@SwingComponentLibraryApi`.",
            finding.message,
        )
    }

    @Test
    fun `names an inferred return type without a written type`() {
        val finding = lint("@Composable fun parentOf() = JPanel().parent").single()

        assertEquals(
            "The inferred return type names a Swing component. A caller that holds the component " +
                "can change what the composition declares on it, so the composition is no longer the only writer " +
                "of its properties. Take the data, callbacks, value types or models the component shows instead. " +
                "If this is API for writing components, mark it `@SwingComponentLibraryApi`.",
            finding.message,
        )
    }

    @Test
    fun `names the remedy for a type-parameter bound`() {
        val finding = lint("@Composable fun <V : Component> Show(v: V) {}").single()

        assertEquals(
            "The bound of type parameter `V`, `Component`, names a Swing component. A caller that holds the " +
                "component can change what the composition declares on it, so the composition is no longer the only " +
                "writer of its properties. Take the data, callbacks, value types or models the component shows " +
                "instead. " +
                "If this is API for writing components, mark it `@SwingComponentLibraryApi`.",
            finding.message,
        )
    }

    @Test
    fun `recognizes the composable annotation through an import alias`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable as C
            import javax.swing.JLabel

            @C fun Bad(p: JLabel) {}

            fun host(content: @C (JLabel) -> Unit) {}
            """.trimIndent()

        assertEquals(listOf("p: JLabel", "JLabel #3"), sourcePositions(source))
    }

    @Test
    fun `ignores an annotation named Composable from the same package`() {
        val source =
            """
            package sample

            import javax.swing.JLabel

            @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
            annotation class Composable

            @Composable fun Plain(p: JLabel) {}

            fun host(content: @Composable (JLabel) -> Unit) {}
            """.trimIndent()

        assertEquals(emptyList(), sourcePositions(source))
    }

    @Test
    fun `exempts a composable marked as component-library API`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi

            @SwingComponentLibraryApi
            @Composable fun Wrap(p: JLabel) {}

            @SwingComponentLibraryApi
            @Composable fun host(content: @Composable (JLabel) -> Unit) {}
            """.trimIndent()

        assertEquals(emptyList(), sourcePositions(source))
    }

    @Test
    fun `exempts a member of a class marked as component-library API`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi

            @SwingComponentLibraryApi
            class Library {
                @Composable fun Wrap(p: JLabel) {}
            }
            """.trimIndent()

        assertEquals(emptyList(), sourcePositions(source))
    }

    @Test
    fun `reports a composable that only opts in to component-library API`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi

            @OptIn(SwingComponentLibraryApi::class)
            @Composable fun Wrap(p: JLabel) {}
            """.trimIndent()

        assertEquals(listOf("p: JLabel"), sourcePositions(source))
    }

    @Test
    fun `reports a composable marked with a same-named annotation from another package`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JLabel

            annotation class SwingComponentLibraryApi

            @SwingComponentLibraryApi
            @Composable fun Wrap(p: JLabel) {}
            """.trimIndent()

        assertEquals(listOf("p: JLabel"), sourcePositions(source))
    }

    private fun lint(code: String) = lintSource("$HEADER\n$code")

    private fun lintSource(source: String) =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("SwingComponentInComposableSignature"))
            .lintWithContext(
                environment,
                source,
                COMPOSABLE_STUBS,
                """
package androidx.compose.runtime

class State<T>(val value: T)
""",
                """
package sample.base

open class BaseLabel : javax.swing.JLabel()
""",
                """
package org.jetbrains.compose.swing.annotations

annotation class SwingComponentLibraryApi
""",
            )

    private fun sourcePositions(source: String): List<String> =
        lintSource(source).map { positionOf(source, it.entity.location.text.start, it.entity.location.text.end) }

    /** The source each finding points at, read off the offsets because the PSI is gone once the lint returns. */
    private fun lintPositions(code: String): List<String> {
        val source = "$HEADER\n$code"
        return lint(code).map { positionOf(source, it.entity.location.text.start, it.entity.location.text.end) }
    }

    companion object {
        private val environment = createEnvironment()

        private const val HEADER = """
package sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import java.awt.Component
import java.awt.Window
import javax.swing.*
import sample.base.BaseLabel
"""
    }

    object ReportedCases {
        fun case(
            label: String,
            code: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, code.trimIndent(), positions.toList())

        @JvmStatic
        fun reported(): List<Arguments> =
            reportedParameterTypes() +
                reportedSignaturePositions() +
                reportedDeclarationShapes() +
                reportedMemberForms() +
                reportedComposableFunctionTypes() +
                reportedLibraryShapes() +
                reportedSupertypesAndGenerics() +
                reportedInferredOverrides() +
                reportedFunctionTypeOverrides() +
                reportedFakeAndConstructorOverrides() +
                reportedOverridesNamedByBase()

        private fun reportedParameterTypes(): List<Arguments> =
            listOf(
                case("a JLabel parameter", "@Composable fun Show(label: JLabel) {}", "label: JLabel"),
                case("a Component parameter", "@Composable fun Show(c: Component) {}", "c: Component"),
                case("a nullable parameter", "@Composable fun Show(c: JComponent?) {}", "c: JComponent?"),
                case(
                    "a private subclass",
                    """
                    private class CountingLabel : JLabel()

                    @Composable fun Show(label: CountingLabel) {}
                    """,
                    "label: CountingLabel",
                ),
                case(
                    "a two-level subclass from another file",
                    """
                    class Fancy : BaseLabel()

                    @Composable fun Show(label: Fancy) {}
                    """,
                    "label: Fancy",
                ),
                case("a type argument", "@Composable fun Show(w: List<State<Window?>>) {}", "w: List<State<Window?>>"),
                case(
                    "a non-function type alias at its use in a composable parameter",
                    """
                    typealias Labels = List<JLabel>

                    @Composable fun Show(labels: Labels) {}
                    """,
                    "labels: Labels",
                ),
                case(
                    "a type alias of a function type",
                    """
                    typealias Make = () -> JLabel

                    @Composable fun Show(make: Make) {}
                    """,
                    "make: Make",
                ),
            )

        private fun reportedSignaturePositions(): List<Arguments> =
            listOf(
                case("a class token", "@Composable fun Show(type: Class<out JLabel>) {}", "type: Class<out JLabel>"),
                case("a star projection of a component", "@Composable fun Show(c: JComboBox<*>) {}", "c: JComboBox<*>"),
                case("a vararg", "@Composable fun Show(vararg labels: JLabel) {}", "vararg labels: JLabel"),
                case(
                    "a function type returning one",
                    "@Composable fun Show(make: () -> JLabel) {}",
                    "make: () -> JLabel",
                ),
                case(
                    "a function type taking one",
                    "@Composable fun Show(display: (JPopupMenu, Component, Int, Int) -> Unit) {}",
                    "display: (JPopupMenu, Component, Int, Int) -> Unit",
                ),
                case(
                    "a function type receiver",
                    "@Composable fun Show(install: JTree.(String) -> Unit) {}",
                    "install: JTree.(String) -> Unit",
                ),
                case("an extension receiver", "@Composable fun Window.Host() {}", "Window #2"),
                case(
                    "a context parameter",
                    """
                    context(panel: JPanel)
                    @Composable fun Show() {}
                    """,
                    "panel: JPanel",
                ),
                case("a written return type", "@Composable fun make(): JLabel = JLabel()", "JLabel #1"),
                case("an inferred return type", "@Composable fun parentOf() = JPanel().parent", "parentOf"),
                case(
                    "a function type return naming a component",
                    "@Composable fun <T> reader(): JComboBox<*>.() -> T? = { null }",
                    "JComboBox<*>.() -> T?",
                ),
                case("a type-parameter bound", "@Composable fun <V : Component> Show(v: V) {}", "Component #2"),
                case("a where-clause bound", "@Composable fun <V> Show(v: V) where V : JComponent {}", "JComponent"),
            )

        private fun reportedDeclarationShapes(): List<Arguments> =
            listOf(
                case(
                    "a bound reached through another parameter",
                    "@Composable fun <A : JLabel, B : A> Show(a: A, b: B) {}",
                    "JLabel",
                ),
                case(
                    "a class type parameter",
                    """
                    class Host<T : JLabel> {
                        @Composable fun Show(x: T) {}
                    }
                    """,
                    "x: T",
                ),
                case(
                    "a composable getter",
                    """
                    val Window.label: JLabel
                        @Composable get() = JLabel()
                    """,
                    "Window #2",
                    "JLabel #1",
                ),
                case(
                    "an abstract member",
                    """
                    interface Slot {
                        @Composable fun Show(label: JLabel)
                    }
                    """,
                    "label: JLabel",
                ),
            )

        private fun reportedMemberForms(): List<Arguments> =
            listOf(
                case(
                    "an open member",
                    """
                    open class Slot {
                        @Composable open fun Show(label: JLabel) {}
                    }
                    """,
                    "label: JLabel",
                ),
                case(
                    "an override only once, at the declaration it answers for",
                    """
                    interface Slot {
                        @Composable fun Show(label: JLabel)
                    }

                    class Impl : Slot {
                        @Composable override fun Show(label: JLabel) {}
                    }
                    """,
                    "label: JLabel #1",
                ),
                case(
                    "a local composable",
                    """
                    @Composable fun Outer() {
                        @Composable fun inner(label: JLabel) {}
                    }
                    """,
                    "label: JLabel",
                ),
                case(
                    "a private composable",
                    "@Composable private fun Show(label: JLabel) {}",
                    "label: JLabel",
                ),
            )

        private fun reportedComposableFunctionTypes(): List<Arguments> =
            listOf(
                case(
                    "a composable function type in a plain function",
                    "fun host(content: @Composable (JLabel) -> Unit) {}",
                    "JLabel",
                ),
                case(
                    "a nullable composable function type in a plain function",
                    "fun host(content: (@Composable (JLabel) -> Unit)?) {}",
                    "JLabel",
                ),
                case(
                    "a composable function type once, in a composable",
                    "@Composable fun Host(content: @Composable (JLabel) -> Unit) {}",
                    "content: @Composable (JLabel) -> Unit",
                ),
                case(
                    "a composable function type nested in another",
                    "fun host(content: @Composable (@Composable () -> JLabel) -> Unit) {}",
                    "@Composable () -> JLabel",
                ),
                case(
                    "a non-generic composable type alias at each use",
                    """
                    typealias Slot = @Composable (JLabel) -> Unit

                    fun host(content: Slot) {}
                    fun other(content: Slot) {}
                    """,
                    "Slot #2",
                    "Slot #3",
                ),
                case(
                    "a composable function class written with type arguments",
                    "fun host(content: @Composable Function1<JLabel, Unit>) {}",
                    "@Composable Function1<JLabel, Unit>",
                ),
                case(
                    "two positions in one signature",
                    "@Composable fun Show(a: JLabel, b: JPanel) {}",
                    "a: JLabel",
                    "b: JPanel",
                ),
            )

        private fun reportedLibraryShapes(): List<Arguments> =
            listOf(
                case(
                    "a SwingNode-shaped declaration, which has no exemption",
                    """
                    class SwingNodeUpdater<T : Component>

                    @Composable
                    fun <T : Component> SwingNode(
                        factory: () -> T,
                        update: SwingNodeUpdater<JButton>.() -> Unit = {},
                    ) {}
                    """,
                    "update: SwingNodeUpdater<JButton>.() -> Unit = {}",
                    "Component #3",
                ),
                case(
                    "an ExistingSwingNode-shaped claim",
                    """
                    @Composable
                    fun <P : Any> ExistingSwingNode(
                        parent: P,
                        claim: P.() -> Component?,
                    ) {}
                    """,
                    "claim: P.() -> Component?",
                ),
                case(
                    "a MenuNode-shaped factory",
                    "@Composable fun MenuNode(factory: () -> JMenuItem) {}",
                    "factory: () -> JMenuItem",
                ),
            )

        private fun reportedSupertypesAndGenerics(): List<Arguments> =
            listOf(
                case(
                    "an override returning a component from a supertype returning Any",
                    """
                    interface Slot {
                        @Composable fun value(): Any
                    }

                    class Impl : Slot {
                        @Composable override fun value(): JLabel = JLabel()
                    }
                    """,
                    "JLabel #1",
                ),
                case(
                    "an override taking a component for a generic supertype's parameter",
                    """
                    interface Slot<T> {
                        @Composable fun Show(value: T)
                    }

                    class LabelSlot : Slot<JLabel> {
                        @Composable override fun Show(value: JLabel) {}
                    }
                    """,
                    "value: JLabel",
                ),
                case(
                    "a class type parameter bounded by a component type argument",
                    """
                    class Host<T : List<JLabel>> {
                        @Composable fun Show(value: T) {}
                    }
                    """,
                    "value: T",
                ),
                case(
                    "a generic composable type alias in a plain function",
                    """
                    typealias Slot<T> = @Composable (T) -> Unit

                    fun host(content: Slot<JLabel>) {}
                    """,
                    "Slot<JLabel>",
                ),
                case(
                    "a composable function type in a composable body",
                    "@Composable fun Host() { listOf<@Composable (JLabel) -> Unit>() }",
                    "JLabel",
                ),
                case(
                    "a context parameter of a composable function type",
                    "fun host(content: @Composable context(JLabel) () -> Unit) {}",
                    "JLabel",
                ),
            )

        private fun reportedInferredOverrides(): List<Arguments> =
            listOf(
                case(
                    "an override with an inferred return type, once, at the declaration it answers for",
                    """
                    interface Base {
                        @Composable fun Show(): JLabel
                    }

                    class Impl : Base {
                        @Composable override fun Show() = JLabel()
                    }
                    """,
                    "JLabel #1",
                ),
                case(
                    "an override with an inferred return type naming a component its supertype does not",
                    """
                    interface Slot {
                        @Composable fun value(): Any
                    }

                    class Impl : Slot {
                        @Composable override fun value() = JLabel()
                    }
                    """,
                    "value #2",
                ),
            )

        private fun reportedFunctionTypeOverrides(): List<Arguments> =
            listOf(
                case(
                    "an interface function type, once, not its override",
                    """
                    interface Host {
                        fun host(content: @Composable (JLabel) -> Unit)
                    }

                    class Impl : Host {
                        override fun host(content: @Composable (JLabel) -> Unit) {}
                    }
                    """,
                    "JLabel #1",
                ),
                case(
                    "an actual's function type, once, at its expect",
                    """
                    expect fun host(content: @Composable (JLabel) -> Unit)

                    actual fun host(content: @Composable (JLabel) -> Unit) {}
                    """,
                    "JLabel #1",
                ),
                case(
                    "an override's function type property, once, at the interface",
                    """
                    interface Host {
                        val content: @Composable (JLabel) -> Unit
                    }

                    class Impl : Host {
                        override val content: @Composable (JLabel) -> Unit = {}
                    }
                    """,
                    "JLabel #1",
                ),
                case(
                    "an override's generic alias use, once, at the interface",
                    """
                    typealias Slot<T> = @Composable (T) -> Unit

                    interface Host {
                        fun host(content: Slot<JLabel>)
                    }

                    class Impl : Host {
                        override fun host(content: Slot<JLabel>) {}
                    }
                    """,
                    "Slot<JLabel> #1",
                ),
            )

        private fun reportedFakeAndConstructorOverrides(): List<Arguments> =
            listOf(
                case(
                    "a fake override of a generic supertype's member, once, at the override",
                    """
                    interface Slot<T> {
                        @Composable fun Show(value: T)
                    }

                    abstract class Mid : Slot<JLabel>

                    class Impl : Mid() {
                        @Composable override fun Show(value: JLabel) {}
                    }
                    """,
                    "value: JLabel",
                ),
                case(
                    "a constructor property's function type override, once, at the interface",
                    """
                    interface Host {
                        val content: @Composable (JLabel) -> Unit
                    }

                    class Impl(override val content: @Composable (JLabel) -> Unit) : Host
                    """,
                    "JLabel #1",
                ),
            )

        private fun reportedOverridesNamedByBase(): List<Arguments> =
            listOf(
                case(
                    "a receiver named by the base, once, at the base",
                    """
                    interface Slot {
                        @Composable fun JLabel.Show()
                    }

                    class Impl : Slot {
                        @Composable override fun JLabel.Show() {}
                    }
                    """,
                    "JLabel #1",
                ),
                case(
                    "a context parameter named by the base, once, at the base",
                    """
                    interface Slot {
                        context(panel: JPanel)
                        @Composable fun Show()
                    }

                    class Impl : Slot {
                        context(panel: JPanel)
                        @Composable override fun Show() {}
                    }
                    """,
                    "panel: JPanel #1",
                ),
                case(
                    "a type-parameter bound named by the base, once, at the base",
                    """
                    interface Slot {
                        @Composable fun <V : Component> Show(v: V)
                    }

                    class Impl : Slot {
                        @Composable override fun <V : Component> Show(v: V) {}
                    }
                    """,
                    "Component #2",
                ),
            )
    }

    object AliasCases {
        @JvmStatic
        fun reported(): List<Arguments> =
            reportedNestedAliases() +
                reportedWrappedAliases() +
                reportedWrappedAliasOwnership()

        private fun case(
            label: String,
            code: String,
            vararg positions: String,
        ): Arguments = ReportedCases.case(label, code, *positions)

        private fun reportedNestedAliases(): List<Arguments> =
            listOf(
                case(
                    "a non-generic alias nested in a composable function type, once, at the outer position",
                    """
                    typealias Slot = @Composable (JLabel) -> Unit

                    fun host(content: @Composable (Slot) -> Unit) {}
                    """,
                    "Slot #2",
                ),
                case(
                    "a non-generic alias nested in another, once, at the outer use",
                    """
                    typealias Slot = @Composable (JLabel) -> Unit
                    typealias Outer = @Composable (Slot) -> Unit

                    fun host(content: Outer) {}
                    """,
                    "Outer #2",
                ),
                case(
                    "a generic alias naming a component in its declaration, once, at its use",
                    """
                    typealias Slot<T> = @Composable (JLabel, T) -> Unit

                    fun host(content: Slot<String>) {}
                    """,
                    "Slot<String>",
                ),
                case(
                    "a generic alias nested in another, once, at the outer use",
                    """
                    typealias Slot<T> = @Composable (T) -> Unit

                    fun host(content: Slot<Slot<JLabel>>) {}
                    """,
                    "Slot<Slot<JLabel>>",
                ),
                case(
                    "a written composable function type nested in an alias, once, at the outer use",
                    """
                    typealias Slot<T> = @Composable (T) -> Unit

                    fun host(content: Slot<@Composable (JLabel) -> Unit>) {}
                    """,
                    "Slot<@Composable (JLabel) -> Unit>",
                ),
                case(
                    "an alias with two naming arguments, once, at its use",
                    """
                    typealias Slot2<A, B> = @Composable (A, B) -> Unit

                    fun host(content: Slot2<JLabel, JLabel>) {}
                    """,
                    "Slot2<JLabel, JLabel>",
                ),
            )

        private fun reportedWrappedAliases(): List<Arguments> =
            listOf(
                case(
                    "an alias wrapping a composable function type in a list",
                    """
                    typealias SlotList = List<@Composable (JLabel) -> Unit>
                    fun host(slots: SlotList) {}
                    """,
                    "SlotList #2",
                ),
                case(
                    "an alias returning a composable function type",
                    """
                    typealias MakeSlot = () -> (@Composable (JLabel) -> Unit)
                    fun host(make: MakeSlot) {}
                    """,
                    "MakeSlot #2",
                ),
                case(
                    "two uses of an alias wrapping composable function types",
                    """
                    typealias SlotList = List<@Composable (JLabel) -> Unit>
                    fun host(slots: SlotList) {}
                    fun other(slots: SlotList) {}
                    """,
                    "SlotList #2",
                    "SlotList #3",
                ),
                case(
                    "a generic alias use holding a composable function type in its type argument",
                    """
                    typealias Holder<T> = List<T>
                    fun host(slots: Holder<@Composable (JPanel) -> Unit>) {}
                    """,
                    "Holder<@Composable (JPanel) -> Unit>",
                ),
                case(
                    "an alias taking a composable function type",
                    """
                    typealias UseSlot = (@Composable (JLabel) -> Unit) -> Unit
                    fun host(use: UseSlot) {}
                    """,
                    "UseSlot #2",
                ),
                case(
                    "an alias receiving a composable function type",
                    """
                    typealias SlotReceiver = (@Composable (JLabel) -> Unit).() -> Unit
                    fun host(use: SlotReceiver) {}
                    """,
                    "SlotReceiver #2",
                ),
            )

        private fun reportedWrappedAliasOwnership(): List<Arguments> =
            listOf(
                case(
                    "a wrapped alias in a composable signature, once, at its parameter",
                    """
                    typealias SlotList = List<@Composable (JLabel) -> Unit>
                    @Composable fun host(slots: SlotList) {}
                    """,
                    "slots: SlotList",
                ),
                case(
                    "a generic wrapped alias with a nested alias, once, at its outer use",
                    """
                    typealias Slot = @Composable (JLabel) -> Unit
                    typealias Slots<T> = List<T>
                    fun host(slots: Slots<Slot>) {}
                    """,
                    "Slots<Slot>",
                ),
                case(
                    "an alias wrapping several composable function types recursively, once, at its use",
                    """
                    typealias Slots = List<() -> Pair<@Composable (JLabel) -> Unit, @Composable () -> JPanel>>
                    fun host(slots: Slots) {}
                    """,
                    "Slots #2",
                ),
            )
    }

    object PassedCases {
        private fun clean(
            label: String,
            code: String,
        ): Arguments = Arguments.of(label, code.trimIndent())

        @JvmStatic
        fun passed(): List<Arguments> =
            listOf(
                clean(
                    "a composable function type in a type alias",
                    "typealias Slot = @Composable (JLabel) -> Unit",
                ),
                clean(
                    "a composable function type in a generic type alias",
                    "typealias Slot<T> = @Composable (JLabel, T) -> Unit",
                ),
                clean(
                    "an alias with a component outside its composable function type",
                    """
                    typealias Slots = Pair<JLabel, @Composable () -> Unit>
                    fun host(slots: Slots) {}
                    """,
                ),
            ) + passedPlainOrNonComponentSignatures() + passedConstructorPropertyOverrides()

        private fun passedPlainOrNonComponentSignatures(): List<Arguments> =
            listOf(
                clean("a plain function with a component", "fun show(label: JLabel) {}"),
                clean(
                    "a plain function with a component and a composable content lambda",
                    "fun setStockContent(parent: JPanel, content: @Composable () -> Unit) {}",
                ),
                clean(
                    "Swing types that are not components",
                    """
                    @Composable fun Show(
                        model: SpinnerModel,
                        renderer: ListCellRenderer<String>,
                        icon: Icon,
                        border: javax.swing.border.Border,
                        layout: java.awt.LayoutManager,
                        color: java.awt.Color,
                        item: java.awt.MenuItem,
                    ) {}
                    """,
                ),
                clean("state and plain values", "@Composable fun Show(text: String, state: State<String>) {}"),
                clean("an unbounded class type parameter", "class Host<T> { @Composable fun Show(x: T) {} }"),
                clean("an unbounded function type parameter", "@Composable fun <T> Show(x: T) {}"),
                clean(
                    "a class type parameter bounded by itself",
                    "class Host<T : Comparable<T>> { @Composable fun Show(value: T) {} }",
                ),
                clean(
                    "a generic composable type alias with a component-free argument",
                    """
                    typealias Slot<T> = @Composable (T) -> Unit

                    fun host(content: Slot<String>) {}
                    """,
                ),
                clean("a composable returning Nothing", "@Composable fun fail(): Nothing = error(\"never\")"),
                clean("an unresolved type", "@Composable fun Show(x: NoSuchType) {}"),
                clean("a non-composable function type", "fun host(make: () -> JLabel) {}"),
                clean(
                    "a generic type alias of a non-composable function type",
                    """
                    typealias Make<T> = (T) -> JLabel

                    fun host(make: Make<String>) {}
                    """,
                ),
                clean(
                    "a suppressed site",
                    """
                    @Suppress("SwingComponentInComposableSignature")
                    @Composable fun Show(label: JLabel) {}
                    """,
                ),
            )

        private fun passedConstructorPropertyOverrides(): List<Arguments> =
            listOf(
                clean(
                    "an override's constructor property function type",
                    "class Impl(override val content: @Composable (JLabel) -> Unit) : Host",
                ),
                clean(
                    "an actual's constructor property function type",
                    "actual class Host(actual val content: @Composable (JLabel) -> Unit)",
                ),
            )
    }
}
