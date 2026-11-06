package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.util.get
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Insets
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.border.Border
import javax.swing.border.LineBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/** The setters every library component accepts, each set, changed and dropped by a style. */
class SwingComponentStylePropertiesTest {
    @TestFactory
    fun settersWriteUpdateAndRestore(): List<DynamicTest> = cases.map { case ->
        dynamicTest(case.name) {
            runComposeSwingTest {
                val label = JLabel("text").apply(case.prepare)
                val own = case.read(label)
                assertNotEquals(case.second, own, "the case must change the value before the style drops it")
                var step by mutableIntStateOf(0)
                val style =
                    SwingStyle<SwingComponentStyleScope> {
                        when (step) {
                            0 -> with(case) { set(true) }
                            1 -> with(case) { set(false) }
                        }
                    }
                setContent { SwingNode(factory = { label }, modifier = SwingModifier.styleable(style, TestNodeKind)) }
                assertEquals(case.first, case.read(label), "the style writes its value")

                step = 1
                awaitIdle()
                assertEquals(case.second, case.read(label), "a changed value is written")

                step = 2
                awaitIdle()
                assertEquals(own, case.read(label), "dropping the setter restores the own value")
            }
        }
    }

    @Test
    fun lastBorderWins() = runComposeSwingTest {
        val own = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        val label = JLabel("text").apply { border = own }
        var lineLast by mutableStateOf(true)
        val style =
            SwingStyle<SwingComponentStyleScope> {
                if (lineLast) {
                    emptyBorder(4)
                    lineBorder(Color.RED)
                } else {
                    lineBorder(Color.RED)
                    emptyBorder(4)
                }
            }
        setContent { SwingNode(factory = { label }, modifier = SwingModifier.styleable(style, TestNodeKind)) }
        assertEquals(Color.RED, (label.border as LineBorder).lineColor)

        lineLast = false
        awaitIdle()
        assertEquals(Insets(4, 4, 4, 4), label.border.getBorderInsets(label))
    }

    @Test
    // Modifier and style borders write the same Swing property, with the style declared last.
    @Suppress("DEPRECATION")
    fun styleBorderOverridesChangingModifier() = runComposeSwingTest {
        var modifierColor by mutableStateOf(Color.BLUE)
        setContent {
            Label(
                text = "text",
                modifier = SwingModifier.lineBorder(modifierColor, 3),
                style = { lineBorder(Color.RED, 2) },
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(Color.RED) { (border as? LineBorder)?.lineColor }
        label.assertProperty(Insets(2, 2, 2, 2)) { insets }

        modifierColor = Color.GREEN
        awaitIdle()

        label.assertProperty(Color.RED) { (border as? LineBorder)?.lineColor }
        label.assertProperty(Insets(2, 2, 2, 2)) { insets }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun equalDeclarationsDoNotWrite(tooltip: Boolean) = runComposeSwingTest {
        val label = JLabel("text")
        var pass by mutableIntStateOf(0)
        val style =
            SwingStyle<SwingComponentStyleScope> {
                pass.let { }
                if (tooltip) toolTip("styled") else lineBorder(Color.RED, 2)
            }
        setContent { SwingNode(factory = { label }, modifier = SwingModifier.styleable(style, TestNodeKind)) }
        val written = label.border
        if (tooltip) {
            assertEquals("styled", label.toolTipText)
            label.toolTipText = "written outside"
        }
        pass++
        awaitIdle()
        if (tooltip) {
            assertEquals("written outside", label.toolTipText, "an equal tooltip declaration writes nothing")
            label.toolTipText = "styled"
        } else {
            assertSame(written, label.border, "an equal line border declaration keeps the written instance")
        }
    }

    @Test
    fun droppingTheAccessibleNameLetsTheButtonAnswerWithItsOwnText() = runComposeSwingTest {
        var named by mutableStateOf(true)
        var text by mutableStateOf("Save")
        setContent {
            Button(
                text,
                onClick = { },
                style = { if (named) accessibleName("Persist the document") },
            )
        }
        val button = onNodeOfType<JButton>()
        button.assertProperty(
            "Persist the document",
            "the accessible name should apply while the modifier is present",
        ) { accessibleContext.accessibleName }

        // A button answers with its text while it holds no name of its own, so a name pinned on removal
        // would go on being announced after the text moved on.
        named = false
        text = "Store"
        awaitIdle()
        button.assertProperty(
            "Store",
            "removing the modifier should hand the name back to the button's own text",
        ) { accessibleContext.accessibleName }
    }

    @Test
    fun droppingTheAccessibleNamePutsBackTheOneTheWidgetWasBuiltCarrying() = runComposeSwingTest {
        var named by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JLabel("label text").apply { accessibleContext.accessibleName = "carried by the widget" } },
                modifier =
                    if (named) {
                        SwingModifier.styleable({ accessibleName("Named by the declaration") }, TestNodeKind)
                    } else {
                        SwingModifier
                    },
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(
            "Named by the declaration",
            "the declared name should apply while the modifier is present",
        ) { accessibleContext.accessibleName }

        // A context answers a name it was set and, where it was set none, one it derives - and cannot be
        // asked which. A name the widget was built carrying is the widget's own, so it is put back rather
        // than left to the derivation.
        named = false
        awaitIdle()
        label.assertProperty(
            "carried by the widget",
            "removing the modifier should put back the name the widget carried",
        ) { accessibleContext.accessibleName }
    }

    @Test
    fun droppingTheAccessibleDescriptionPutsBackTheOneTheWidgetWasBuiltCarrying() = runComposeSwingTest {
        var described by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = {
                    JLabel("label text").apply { accessibleContext.accessibleDescription = "described by the widget" }
                },
                modifier =
                    if (described) {
                        SwingModifier.styleable({ accessibleDescription("Described by the declaration") }, TestNodeKind)
                    } else {
                        SwingModifier
                    },
            )
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(
            "Described by the declaration",
            "the declared description should apply while the modifier is present",
        ) { accessibleContext.accessibleDescription }

        described = false
        awaitIdle()
        label.assertProperty(
            "described by the widget",
            "removing the modifier should put back the description the widget carried",
        ) { accessibleContext.accessibleDescription }
    }

    @Test
    fun droppingTheAccessibleDescriptionLetsTheFieldAnswerWithItsTooltip() = runComposeSwingTest {
        var described by mutableStateOf(true)
        var tip by mutableStateOf("Where you live")
        setContent {
            TextField(
                "",
                onValueChange = {},
                style = {
                    toolTip(tip)
                    if (described) accessibleDescription("The address to deliver to")
                },
            )
        }
        val field = onNodeOfType<JTextField>()
        field.assertProperty(
            "The address to deliver to",
            "the accessible description should apply while the modifier is present",
        ) { accessibleContext.accessibleDescription }

        described = false
        tip = "Where you work"
        awaitIdle()
        field.assertProperty(
            "Where you work",
            "removing the modifier should hand the description back to the field's own tooltip",
        ) { accessibleContext.accessibleDescription }
    }

    @Test
    fun matchersFindAccessibleProperties() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                style = {
                    accessibleName("Coordinate")
                    accessibleDescription("The horizontal position")
                },
            )
        }
        // Each matcher must find the label the accessible state was declared on, not any node.
        onNode(SwingMatcher.hasAccessibleName("Coordinate")).assert(SwingMatcher.isOfType<JLabel>())
        onNode(SwingMatcher.hasAccessibleDescription("The horizontal position"))
            .assert(SwingMatcher.isOfType<JLabel>())
    }

    private class Case(
        val name: String,
        val first: Any?,
        val second: Any?,
        val prepare: JLabel.() -> Unit = {},
        val read: (JLabel) -> Any?,
        val set: SwingComponentStyleScope.(first: Boolean) -> Unit,
    )

    private companion object {
        val ownBorder: Border = BorderFactory.createEmptyBorder(9, 9, 9, 9)
        val firstBorder: Border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        val secondBorder: Border = BorderFactory.createEmptyBorder(2, 2, 2, 2)

        fun JLabel.borderInsets(): Insets? = border?.getBorderInsets(this)

        fun JLabel.line(): Pair<Color, Int>? = (border as? LineBorder)?.let { it.lineColor to it.thickness }

        /** Moves the pointer over [label], which a per-location tooltip answers, and reads the tooltip text. */
        fun hoveredToolTip(label: JLabel): String? {
            label.dispatchEvent(MouseEvent(label, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false))
            return label.toolTipText
        }

        val cases =
            listOf(
                Case("background", Color.RED, Color.BLUE, read = { if (it.isBackgroundSet) it.background else null }) {
                    background(if (it) Color.RED else Color.BLUE)
                },
                Case("foreground", Color.RED, Color.BLUE, read = { if (it.isForegroundSet) it.foreground else null }) {
                    foreground(if (it) Color.RED else Color.BLUE)
                },
                Case(
                    "font",
                    Font(Font.SERIF, Font.BOLD, 20),
                    Font(Font.MONOSPACED, Font.PLAIN, 9),
                    read = { it.font },
                ) {
                    font(if (it) Font(Font.SERIF, Font.BOLD, 20) else Font(Font.MONOSPACED, Font.PLAIN, 9))
                },
                Case(
                    "cursor",
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                    Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR),
                    read = { if (it.isCursorSet) it.cursor else null },
                ) { cursor(Cursor.getPredefinedCursor(if (it) Cursor.HAND_CURSOR else Cursor.WAIT_CURSOR)) },
                Case("accessibleName", "first", "second", read = { it.accessibleContext.accessibleName }) {
                    accessibleName(if (it) "first" else "second")
                },
                Case(
                    "accessibleDescription",
                    "first",
                    "second",
                    read = { it.accessibleContext.accessibleDescription },
                ) {
                    accessibleDescription(if (it) "first" else "second")
                },
                Case("border", firstBorder, secondBorder, { border = ownBorder }, { it.border }) {
                    border(if (it) firstBorder else secondBorder)
                },
                Case(
                    "lineBorder",
                    Color.RED to 1,
                    Color.BLUE to 3,
                    { border = ownBorder },
                    { it.line() ?: it.border },
                ) {
                    if (it) lineBorder(Color.RED) else lineBorder(Color.BLUE, 3)
                },
                Case(
                    "emptyBorder(all)",
                    Insets(3, 3, 3, 3),
                    Insets(5, 5, 5, 5),
                    { border = ownBorder },
                    { it.borderInsets() },
                ) {
                    emptyBorder(if (it) 3 else 5)
                },
                Case(
                    "emptyBorder(sides)",
                    Insets(1, 2, 3, 4),
                    Insets(4, 3, 2, 1),
                    { border = ownBorder },
                    { it.borderInsets() },
                ) {
                    if (it) emptyBorder(1, 2, 3, 4) else emptyBorder(4, 3, 2, 1)
                },
                Case(
                    "emptyBorder(insets)",
                    Insets(1, 2, 3, 4),
                    Insets(4, 3, 2, 1),
                    { border = ownBorder },
                    { it.borderInsets() },
                ) {
                    emptyBorder(if (it) Insets(1, 2, 3, 4) else Insets(4, 3, 2, 1))
                },
                Case("opaque", false, true, { isOpaque = false }, { it.isOpaque }) { opaque(!it) },
                Case("toolTip(text)", "first", "second", { toolTipText = "own" }, { it.toolTipText }) {
                    toolTip(if (it) "first" else "second")
                },
                Case("toolTip(at)", "first at", "second at", { toolTipText = "own" }, ::hoveredToolTip) {
                    if (it) toolTip { "first at" } else toolTip { "second at" }
                },
                Case(
                    "alignmentX",
                    0.1f,
                    0.9f,
                    { alignmentX = 0.3f },
                    { it.alignmentX },
                ) { alignmentX(if (it) 0.1f else 0.9f) },
                Case(
                    "alignmentY",
                    0.1f,
                    0.9f,
                    { alignmentY = 0.3f },
                    { it.alignmentY },
                ) { alignmentY(if (it) 0.1f else 0.9f) },
                Case("focusTraversalIndex", 1, 2, read = { it[FOCUS_TRAVERSAL_INDEX_KEY] }) {
                    focusTraversalIndex(if (it) 1 else 2)
                },
                Case(
                    "verifyInputWhenFocusTarget",
                    true,
                    false,
                    { verifyInputWhenFocusTarget = true },
                    { it.verifyInputWhenFocusTarget },
                ) { verifyInputWhenFocusTarget(it) },
            )
    }
}
