package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
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
import kotlin.test.assertNotEquals

/** The setters every library component accepts, each set, changed and dropped by a style. */
class SwingComponentStylePropertiesTest {
    @TestFactory
    fun settersWriteUpdateAndRestore(): List<DynamicTest> = setterCaseTests(cases)

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
        onNodeOfType<JButton>().assertProperty(
            "Persist the document",
            "the accessible name should apply while the modifier is present",
        ) { accessibleContext.accessibleName }

        // A button answers with its text while it holds no name of its own, so a name pinned on removal
        // would go on being announced after the text moved on.
        named = false
        text = "Store"
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(
            "Store",
            "removing the modifier should hand the name back to the button's own text",
        ) { accessibleContext.accessibleName }
    }

    @ParameterizedTest
    @EnumSource(AccessiblePropertyCase::class)
    fun droppingAccessiblePropertyRestoresTheWidgetValue(case: AccessiblePropertyCase) = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = {
                    JLabel("label text").apply {
                        accessibleContext.accessibleName = "carried by the widget"
                        accessibleContext.accessibleDescription = "described by the widget"
                    }
                },
                modifier =
                    SwingModifier.styleable(TestNodeKind) {
                        if (declared) {
                            case.set(this)
                        }
                    },
            )
        }
        onNodeOfType<JLabel>().assertProperty(case.declared) { case.read(this) }
        declared = false
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(case.own) { case.read(this) }
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
        onNodeOfType<JTextField>().assertProperty(
            "The address to deliver to",
            "the accessible description should apply while the modifier is present",
        ) { accessibleContext.accessibleDescription }

        described = false
        tip = "Where you work"
        awaitIdle()
        onNodeOfType<JTextField>().assertProperty(
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

    enum class AccessiblePropertyCase(
        val declared: String,
        val own: String,
        val set: SwingComponentStyleScope.() -> Unit,
        val read: (JLabel) -> String?,
    ) {
        Name(
            "Named by the declaration",
            "carried by the widget",
            { accessibleName("Named by the declaration") },
            { it.accessibleContext.accessibleName },
        ),
        Description(
            "Described by the declaration",
            "described by the widget",
            { accessibleDescription("Described by the declaration") },
            { it.accessibleContext.accessibleDescription },
        ),
    }

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

        inline fun <reified T : Any> case(
            name: String,
            first: T,
            second: T,
            label: LabelUnderTest,
            noinline set: SwingComponentStyleScope.(T) -> Unit,
        ): SetterCase =
            SetterCase(name, first, second, { onNodeOfType<JLabel>() }, { label.read(it as JLabel) }) { value ->
                val style = remember { SwingStyle<SwingComponentStyleScope> { (value() as T?)?.let { set(it) } } }
                SwingNode(
                    factory = {
                        JLabel("text").apply(label.prepare).also {
                            assertNotEquals(
                                second,
                                label.read(it),
                                "the case must change the value before the style drops it",
                            )
                        }
                    },
                    modifier = SwingModifier.styleable(TestNodeKind, style),
                )
            }

        val cases =
            listOf(
                case(
                    "background",
                    Color.RED,
                    Color.BLUE,
                    LabelUnderTest { if (it.isBackgroundSet) it.background else null },
                ) {
                    background(it)
                },
                case(
                    "foreground",
                    Color.RED,
                    Color.BLUE,
                    LabelUnderTest { if (it.isForegroundSet) it.foreground else null },
                ) {
                    foreground(it)
                },
                case(
                    "font",
                    Font(Font.SERIF, Font.BOLD, 20),
                    Font(Font.MONOSPACED, Font.PLAIN, 9),
                    LabelUnderTest { it.font },
                ) {
                    font(it)
                },
                case(
                    "cursor",
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                    Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR),
                    LabelUnderTest { if (it.isCursorSet) it.cursor else null },
                ) { cursor(it) },
                case("accessibleName", "first", "second", LabelUnderTest { it.accessibleContext.accessibleName }) {
                    accessibleName(it)
                },
                case(
                    "accessibleDescription",
                    "first",
                    "second",
                    LabelUnderTest { it.accessibleContext.accessibleDescription },
                ) {
                    accessibleDescription(it)
                },
                case("border", firstBorder, secondBorder, LabelUnderTest({ border = ownBorder }) { it.border }) {
                    border(it)
                },
                case(
                    "lineBorder",
                    Color.RED to 1,
                    Color.BLUE to 3,
                    LabelUnderTest({ border = ownBorder }) { it.line() ?: it.border },
                ) {
                    val (color, thickness) = it
                    lineBorder(color, thickness)
                },
                case(
                    "emptyBorder(all)",
                    Insets(3, 3, 3, 3),
                    Insets(5, 5, 5, 5),
                    LabelUnderTest({ border = ownBorder }) { it.borderInsets() },
                ) {
                    emptyBorder(it.top)
                },
                case(
                    "emptyBorder(sides)",
                    Insets(1, 2, 3, 4),
                    Insets(4, 3, 2, 1),
                    LabelUnderTest({ border = ownBorder }) { it.borderInsets() },
                ) {
                    emptyBorder(it.top, it.left, it.bottom, it.right)
                },
                case(
                    "emptyBorder(insets)",
                    Insets(1, 2, 3, 4),
                    Insets(4, 3, 2, 1),
                    LabelUnderTest({ border = ownBorder }) { it.borderInsets() },
                ) {
                    emptyBorder(it)
                },
                case("opaque", false, true, LabelUnderTest({ isOpaque = false }) { it.isOpaque }) { opaque(it) },
                case("toolTip(text)", "first", "second", LabelUnderTest({ toolTipText = "own" }) { it.toolTipText }) {
                    toolTip(it)
                },
                case(
                    "toolTip(at)",
                    "first at",
                    "second at",
                    LabelUnderTest({ toolTipText = "own" }, ::hoveredToolTip),
                ) {
                    val text = it
                    toolTip { text }
                },
                case("alignmentX", 0.1f, 0.9f, LabelUnderTest({ alignmentX = 0.3f }) { it.alignmentX }) {
                    alignmentX(it)
                },
                case("alignmentY", 0.1f, 0.9f, LabelUnderTest({ alignmentY = 0.3f }) { it.alignmentY }) {
                    alignmentY(it)
                },
                case(
                    "verifyInputWhenFocusTarget",
                    true,
                    false,
                    LabelUnderTest({ verifyInputWhenFocusTarget = true }) { it.verifyInputWhenFocusTarget },
                ) { verifyInputWhenFocusTarget(it) },
            )
    }
}

/** What a case reads from a label, and how the label is set up with a value of its own beforehand. */
class LabelUnderTest(
    val prepare: JLabel.() -> Unit = {},
    val read: (JLabel) -> Any?,
)
