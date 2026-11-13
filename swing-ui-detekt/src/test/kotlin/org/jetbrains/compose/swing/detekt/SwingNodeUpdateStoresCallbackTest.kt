package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class SwingNodeUpdateStoresCallbackTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports a callback stored on a component that listens to itself`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        val source = "$HEADER\n$code"
        assertEquals(positions, lint(source).textsIn(source), label)
    }

    @ParameterizedTest(name = "names {0}")
    @MethodSource("messages")
    fun `names the property, the component, the registration and the modifier`(
        registration: String,
        advice: String,
    ) {
        val code =
            """
            class ActionButton : JButton() {
                var onClick: () -> Unit = {}

                init { $registration { onClick() } }
            }

            @Composable fun Show(onClick: () -> Unit) {
                SwingNode(factory = { ActionButton() }, update = { set(onClick) { this.onClick = it } })
            }
            """.trimIndent()

        val finding = lint("$HEADER\n$code").single()

        assertEquals(
            "`ActionButton` calls the callback stored in `onClick` from a listener it adds to itself. That listener " +
                "stays on the component after its node is released, while a listener modifier lives exactly as long " +
                "as the node. Remove the callback property and the self-registration, and attach the listener with " +
                "$advice.",
            finding.message,
        )
    }

    companion object {
        private val environment = createEnvironment()

        internal const val HEADER = """
package sample

import androidx.compose.runtime.Composable
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.compose.swing.detekt.fixture.LibraryButton
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.SwingNode

typealias Callback = () -> Unit
"""

        /** A composable that sets [assignment] on the component [component] builds. */
        internal fun show(
            component: String,
            assignment: String = "this.onClick = it",
            valueType: String = "() -> Unit",
            node: String = "SwingNode",
        ): String =
            """
            @Composable fun Show(callback: $valueType) {
                $node(factory = { $component() }, update = { set(callback) { $assignment } })
            }
            """.trimIndent()

        internal fun case(
            label: String,
            classes: String,
            composable: String,
            vararg reported: String,
        ): Arguments = Arguments.of(label, classes.trimIndent() + "\n\n" + composable, reported.toList())

        internal const val ACTION_BUTTON = """
            class ActionButton : JButton() {
                var onClick: () -> Unit = {}

                init {
                    putClientProperty("html.disable", true)
                    addActionListener { onClick() }
                }
            }
        """

        internal fun lint(source: String) =
            SwingNodeUpdateStoresCallback(Config.empty)
                .lintWithContext(environment, source, COMPOSABLE_STUBS, NODE_UPDATER_STUBS, UPDATING_NODE_STUBS)

        @JvmStatic
        fun messages(): List<Arguments> =
            listOf(
                Arguments.of("addActionListener", "`SwingModifier.actionListener`"),
                Arguments.of(
                    "addPropertyChangeListener",
                    "`SwingModifier.propertyChangeListener`",
                ),
                Arguments.of(
                    "addVetoableChangeListener",
                    "`SwingModifier.listener` and a `ListenerRegistration` that adds and removes it",
                ),
            )

        @JvmStatic
        fun reported(): List<Arguments> =
            reportedSites() + reportedHierarchies() + reportedShapes() + reportedReads() + reportedBlocks() +
                reportedConstruction() + reportedScopes() + reportedReferences()

        private fun reportedConstruction(): List<Arguments> =
            listOf(
                case(
                    "a listener registered inside a scope function that runs at once",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { run { addActionListener { onClick() } } }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a listener registered inside apply",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { apply { addActionListener { onClick() } } }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a property that a subclass overrides",
                    """
                    open class Base : JButton() {
                        open var onClick: () -> Unit = {}

                        init { addActionListener { onClick() } }
                    }

                    class Sub : Base() {
                        override var onClick: () -> Unit = {}
                    }
                    """,
                    show("Sub"),
                    "this.onClick = it",
                ),
            )

        private fun reportedScopes(): List<Arguments> =
            listOf(
                case(
                    "a listener registered on an explicit this inside apply",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { apply { this.addActionListener { onClick() } } }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a read on an explicit this of the apply receiver",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { apply { addActionListener { this.onClick() } } }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a listener registered inside nested apply blocks",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { apply { apply { addActionListener { onClick() } } } }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
            )

        private fun reportedReferences(): List<Arguments> =
            listOf(
                case(
                    "a function reference to a member of a source superclass",
                    """
                    open class Base : JButton() {
                        var onClick: () -> Unit = {}

                        protected fun fire(event: ActionEvent) { onClick() }
                    }

                    class Chip : Base() {
                        init { addActionListener(::fire) }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a function reference on an explicit this",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { addActionListener(this::fire) }

                        private fun fire(event: ActionEvent) { onClick() }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
            )

        private fun reportedSites(): List<Arguments> =
            listOf(
                case(
                    "an action button with a listener in init",
                    ACTION_BUTTON,
                    show("ActionButton"),
                    "this.onClick = it",
                ),
                case(
                    "a link label with a mouse listener in init",
                    """
                    class LinkLabel : JLabel() {
                        var onClick: () -> Unit = {}

                        init {
                            addMouseListener(object : MouseAdapter() {
                                override fun mousePressed(event: MouseEvent) { onClick() }
                            })
                        }
                    }
                    """,
                    show("LinkLabel", "onClick = it"),
                    "onClick = it",
                ),
            )

        private fun reportedHierarchies(): List<Arguments> =
            listOf(
                case(
                    "a subclass of a painted chart whose superclass listens",
                    """
                    abstract class PaintedChart<D : Any> : JComponent() {
                        var data: D? = null
                        var onClick: ((Int) -> Unit)? = null

                        init {
                            addMouseListener(object : MouseAdapter() {
                                override fun mousePressed(event: MouseEvent) { onClick?.invoke(0) }
                            })
                        }
                    }

                    class PieChart : PaintedChart<String>()
                    """,
                    show("PieChart", "this.onClick = it", "((Int) -> Unit)?"),
                    "this.onClick = it",
                ),
                case(
                    "a horizontal bar chart with listeners in init",
                    """
                    class HorizontalBars : JComponent() {
                        var onClick: ((Int) -> Unit)? = null

                        init {
                            addMouseListener(object : MouseAdapter() {
                                override fun mousePressed(event: MouseEvent) { onClick?.invoke(0) }
                            })
                        }
                    }
                    """,
                    show("HorizontalBars", "this.onClick = it", "((Int) -> Unit)?"),
                    "this.onClick = it",
                ),
            )

        private fun reportedShapes(): List<Arguments> =
            listOf(
                case(
                    "a listener in a secondary constructor",
                    """
                    class Chip : JButton {
                        var onClick: () -> Unit = {}

                        constructor() : super() {
                            addActionListener { onClick() }
                        }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a listener in a property initializer",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}
                        private val registered = addActionListener { onClick() }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a listener registered on an explicit this",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { this.addActionListener { this.onClick() } }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
            )

        private fun reportedReads(): List<Arguments> =
            listOf(
                case(
                    "a function reference to a member that reads the property",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { addActionListener(::fire) }

                        private fun fire(event: ActionEvent) { onClick() }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
                case(
                    "a second listener that reads the property beside one that does not",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init {
                            addComponentListener(object : java.awt.event.ComponentAdapter() {})
                            addActionListener { onClick() }
                        }
                    }
                    """,
                    show("Chip"),
                    "this.onClick = it",
                ),
            )

        private fun reportedBlocks(): List<Arguments> =
            listOf(
                case(
                    "a function type behind an alias",
                    ACTION_BUTTON,
                    show("ActionButton", valueType = "Callback"),
                    "this.onClick = it",
                ),
                case(
                    "a node that is a MenuNode",
                    ACTION_BUTTON,
                    show("ActionButton", node = "MenuNode"),
                    "this.onClick = it",
                ),
                case(
                    "a callback of the component inside a larger block",
                    ACTION_BUTTON,
                    """
                    @Composable fun Show(text: String, onClick: () -> Unit) {
                        SwingNode(
                            factory = { ActionButton() },
                            update = {
                                set(text) { this.text = it }
                                update(onClick) { this.onClick = it }
                            },
                        )
                    }
                    """.trimIndent(),
                    "this.onClick = it",
                ),
                case(
                    "each assignment of two callbacks",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}
                        var onHover: () -> Unit = {}

                        init { addActionListener { onClick(); onHover() } }
                    }
                    """,
                    """
                    @Composable fun Show(onClick: () -> Unit, onHover: () -> Unit) {
                        SwingNode(
                            factory = { Chip() },
                            update = {
                                set(onClick) { this.onClick = it }
                                set(onHover) { this.onHover = it }
                            },
                        )
                    }
                    """.trimIndent(),
                    "this.onClick = it",
                    "this.onHover = it",
                ),
            )
    }
}
