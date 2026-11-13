package org.jetbrains.compose.swing.detekt

import org.jetbrains.compose.swing.detekt.SwingNodeUpdateStoresCallbackTest.Companion.ACTION_BUTTON
import org.jetbrains.compose.swing.detekt.SwingNodeUpdateStoresCallbackTest.Companion.HEADER
import org.jetbrains.compose.swing.detekt.SwingNodeUpdateStoresCallbackTest.Companion.case
import org.jetbrains.compose.swing.detekt.SwingNodeUpdateStoresCallbackTest.Companion.lint
import org.jetbrains.compose.swing.detekt.SwingNodeUpdateStoresCallbackTest.Companion.show
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class SwingNodeUpdateStoresCallbackPassedTest {
    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report anything else`(
        label: String,
        code: String,
    ) {
        val source = "$HEADER\n$code"
        assertEquals(emptyList(), lint(source).textsIn(source), label)
    }

    companion object {
        @JvmStatic
        fun passed(): List<Arguments> =
            passedLibrary() + passedUnregistered() + passedLater() + passedUnreadProperty() + passedUnreadReads() +
                passedOtherWrites() + passedOutsideUpdater() + passedUnreadForms()

        private fun passedLater(): List<Arguments> =
            listOf(
                case(
                    "a listener in a lambda that is stored for later",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}
                        val installLater = { addActionListener { onClick() } }
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a listener in a function declared inside init",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { fun later() { addActionListener { onClick() } } }
                    }
                    """,
                    show("Chip"),
                ),
            )

        private fun passedLibrary(): List<Arguments> =
            listOf(
                case(
                    "a component from a library that listens to itself",
                    "",
                    show("LibraryButton", "this.onPress = it"),
                ),
            )

        private fun passedUnregistered(): List<Arguments> =
            listOf(
                case(
                    "a component that installs no listener",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a component that registers on a part",
                    """
                    class Chip : JPanel() {
                        var onClick: () -> Unit = {}
                        private val part = JButton()

                        init { part.addActionListener { onClick() } }
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a component that registers in a method rather than while built",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        fun listen() { addActionListener { onClick() } }
                    }
                    """,
                    show("Chip"),
                ),
            )

        private fun passedUnreadProperty(): List<Arguments> =
            listOf(
                case(
                    "an unrelated listener beside a callback property",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { addComponentListener(object : java.awt.event.ComponentAdapter() {}) }
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a listener whose body does not read the assigned property",
                    """
                    class Chip : JButton() {
                        var onSelect: () -> Unit = {}
                        var onOther: () -> Unit = {}

                        init { addPropertyChangeListener { onOther() } }
                    }
                    """,
                    show("Chip", "this.onSelect = it"),
                ),
                case(
                    "a call that merely resembles a registration",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { removeActionListener { onClick() } }

                        private fun removeActionListener(block: () -> Unit) {}
                    }
                    """,
                    show("Chip"),
                ),
            )

        private fun passedUnreadReads(): List<Arguments> =
            listOf(
                case(
                    "a listener that only writes the assigned property",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { addActionListener { onClick = {} } }
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a read that only runs while the listener is built",
                    """
                    fun makeListener(callback: () -> Unit): java.awt.event.ActionListener =
                        java.awt.event.ActionListener { callback() }

                    class Chip : JButton() {
                        var onAction: () -> Unit = {}

                        init { addActionListener(makeListener(onAction)) }
                    }
                    """,
                    show("Chip", "this.onAction = it"),
                ),
                case(
                    "a listener that reads the callback of another component",
                    """
                    class Chip(val peer: Chip? = null) : JButton() {
                        var onClick: (() -> Unit)? = null

                        init { addActionListener { peer?.onClick?.invoke() } }
                    }
                    """,
                    show("Chip", valueType = "(() -> Unit)?"),
                ),
            )

        private fun passedOtherWrites(): List<Arguments> =
            listOf(
                case(
                    "a callback stored on a part, which is not one the component's own listener reads",
                    """
                    class Chip : JPanel() {
                        val part = ActionButton()

                        init {
                            addMouseListener(object : MouseAdapter() {
                                override fun mousePressed(event: MouseEvent) { part.onClick() }
                            })
                        }
                    }
                    """ + ACTION_BUTTON,
                    show("Chip", "part.onClick = it"),
                ),
                case(
                    "a value that is not a function",
                    """
                    class Chip : JButton() {
                        var caption: String = ""

                        init { addActionListener { caption.length } }
                    }
                    """,
                    show("Chip", "this.caption = it", "String"),
                ),
                case(
                    "a function stored on another instance of the component",
                    ACTION_BUTTON,
                    """
                    @Composable fun Show(callback: () -> Unit) {
                        val other = ActionButton()
                        SwingNode(factory = { ActionButton() }, update = { set(callback) { other.onClick = it } })
                    }
                    """.trimIndent(),
                ),
            )

        private fun passedOutsideUpdater(): List<Arguments> =
            listOf(
                case(
                    "an assignment outside an updater block",
                    ACTION_BUTTON,
                    """
                    @Composable fun Show(onClick: () -> Unit) {
                        SwingNode(factory = { ActionButton().also { it.onClick = onClick } })
                    }
                    """.trimIndent(),
                ),
                case(
                    "a write inside a block that does not run on the component",
                    ACTION_BUTTON,
                    """
                    class Holder { var onClick: () -> Unit = {} }

                    @Composable fun Show(onClick: () -> Unit) {
                        SwingNode(factory = { ActionButton() }, update = { set(onClick) { Holder().onClick = it } })
                    }
                    """.trimIndent(),
                ),
            )

        private fun passedUnreadForms(): List<Arguments> =
            listOf(
                case(
                    "a listener that only writes the assigned property in parentheses",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { addActionListener { (onClick) = {} } }
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a listener that only writes a qualified assigned property in parentheses",
                    """
                    class Chip : JButton() {
                        var onClick: () -> Unit = {}

                        init { addActionListener { (this.onClick) = {} } }
                    }
                    """,
                    show("Chip"),
                ),
                case(
                    "a function reference on another instance of the component",
                    """
                    class Chip(val peer: Chip? = null) : JButton() {
                        var onClick: () -> Unit = {}

                        init { addActionListener(peer!!::fire) }

                        private fun fire(event: ActionEvent) { onClick() }
                    }
                    """,
                    show("Chip"),
                ),
            )
    }
}
