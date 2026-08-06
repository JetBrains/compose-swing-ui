package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Canvas
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.borderPainted
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.contentAreaFilled
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.disabledIcon
import org.jetbrains.compose.swing.modifier.appearance.disabledSelectedIcon
import org.jetbrains.compose.swing.modifier.appearance.focusPainted
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalTextPosition
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.iconTextGap
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.pressedIcon
import org.jetbrains.compose.swing.modifier.appearance.rolloverEnabled
import org.jetbrains.compose.swing.modifier.appearance.rolloverIcon
import org.jetbrains.compose.swing.modifier.appearance.rolloverSelectedIcon
import org.jetbrains.compose.swing.modifier.appearance.selectedIcon
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.appearance.verticalTextPosition
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.samples.widgets.rememberDotIcon
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.SwingConstants

// The appearance-modifier cards of the modifier gallery: painting, icons, text layout, cursor,
// tooltip, client properties, and the component name.

@Composable
internal fun ColumnScope.AppearanceCard() {
    ExampleCard("background / foreground / font / lineBorder") {
        var fancy by remember { mutableStateOf(true) }
        CheckBox(text = "Fancy styling", checked = fancy, onCheckedChange = { fancy = it })
        val styled =
            if (fancy) {
                SwingModifier
                    .opaque(true)
                    .background(Color(0xFF, 0xF3, 0xE0))
                    .foreground(Color(0xBF, 0x36, 0x0C))
                    .font(Font(Font.SERIF, Font.BOLD or Font.ITALIC, 15))
                    .lineBorder(Color(0xBF, 0x36, 0x0C), 2)
            } else {
                SwingModifier
            }
        Label("Styled when the box is checked", modifier = styled)
    }
}

@Composable
internal fun ColumnScope.IconFamilyCard() {
    ExampleCard(
        "icon family (icon / selectedIcon / pressedIcon / rolloverIcon / " +
            "rolloverSelectedIcon / disabledIcon / disabledSelectedIcon)",
    ) {
        var enabled by remember { mutableStateOf(true) }
        var checked by remember { mutableStateOf(false) }
        val baseDot = rememberDotIcon(Color.GRAY)
        val selectedDot = rememberDotIcon(Color(0x2E, 0x7D, 0x32))
        val pressedDot = rememberDotIcon(Color(0x0D, 0x47, 0xA1))
        val rolloverDot = rememberDotIcon(Color(0xF9, 0xA8, 0x25))
        val rolloverSelectedDot = rememberDotIcon(Color(0xEF, 0x6C, 0x00))
        val disabledDot = rememberDotIcon(Color.LIGHT_GRAY)
        val disabledSelectedDot = rememberDotIcon(Color(0xB0, 0xBE, 0xC5))
        WrappedCaption(
            "The base icon shows unchecked; check the box for selectedIcon, uncheck \"Enabled\" for " +
                "disabledIcon/disabledSelectedIcon, and hover or press the box for rolloverIcon/" +
                "rolloverSelectedIcon/pressedIcon.",
        )
        CheckBox(text = "Enabled", checked = enabled, onCheckedChange = { enabled = it })
        CheckBox(
            text = "Status",
            checked = checked,
            onCheckedChange = { checked = it },
            modifier =
                SwingModifier
                    .enabled(enabled)
                    .icon(baseDot)
                    .selectedIcon(selectedDot)
                    .pressedIcon(pressedDot)
                    .rolloverIcon(rolloverDot)
                    .rolloverSelectedIcon(rolloverSelectedDot)
                    .disabledIcon(disabledDot)
                    .disabledSelectedIcon(disabledSelectedDot),
        )
    }
}

@Composable
internal fun ColumnScope.TextPositionCard() {
    ExampleCard("horizontalTextPosition / verticalTextPosition / iconTextGap") {
        var horizontal by remember { mutableIntStateOf(0) }
        var vertical by remember { mutableIntStateOf(0) }
        var gap by remember { mutableIntStateOf(4) }
        val dot = rememberDotIcon(Color(0x1E, 0x88, 0xE5), size = 18)
        FlowPanel {
            Label("Text side:")
            RadioGroup(selectedIndex = horizontal, onSelectionChange = { horizontal = it }, axis = BoxLayout.X_AXIS) {
                option("Left")
                option("Center")
                option("Right")
            }
        }
        FlowPanel {
            Label("Text row:")
            RadioGroup(selectedIndex = vertical, onSelectionChange = { vertical = it }, axis = BoxLayout.X_AXIS) {
                option("Top")
                option("Center")
                option("Bottom")
            }
        }
        FlowPanel {
            Label("Gap:")
            Spinner(gap, onValueChange = { gap = it.toInt() }, min = 0, max = 20, step = 1)
        }
        val textSide = intArrayOf(SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT)[horizontal]
        val textRow = intArrayOf(SwingConstants.TOP, SwingConstants.CENTER, SwingConstants.BOTTOM)[vertical]
        FlowPanel(modifier = SwingModifier.preferredSize(Dimension(260, 90))) {
            Button(
                "Labelled",
                modifier =
                    SwingModifier
                        .icon(dot)
                        .horizontalTextPosition(textSide)
                        .verticalTextPosition(textRow)
                        .iconTextGap(gap),
            )
        }
    }
}

@Composable
internal fun ColumnScope.ButtonPaintingCard() {
    ExampleCard("borderPainted / contentAreaFilled / rolloverEnabled / focusPainted") {
        var isBorderPainted by remember { mutableStateOf(true) }
        var isContentAreaFilled by remember { mutableStateOf(true) }
        var isRolloverEnabled by remember { mutableStateOf(true) }
        var isFocusPainted by remember { mutableStateOf(true) }
        CheckBox(text = "borderPainted", checked = isBorderPainted, onCheckedChange = { isBorderPainted = it })
        CheckBox(
            text = "contentAreaFilled",
            checked = isContentAreaFilled,
            onCheckedChange = { isContentAreaFilled = it },
        )
        CheckBox(text = "rolloverEnabled", checked = isRolloverEnabled, onCheckedChange = { isRolloverEnabled = it })
        CheckBox(text = "focusPainted", checked = isFocusPainted, onCheckedChange = { isFocusPainted = it })
        WrappedCaption("Hover or Tab to this button to see rolloverEnabled and focusPainted take effect.")
        FlowPanel {
            Button(
                "Hover, focus or click me",
                modifier =
                    SwingModifier
                        .borderPainted(isBorderPainted)
                        .contentAreaFilled(isContentAreaFilled)
                        .rolloverEnabled(isRolloverEnabled)
                        .focusPainted(isFocusPainted),
            )
        }
    }
}

@Composable
internal fun ColumnScope.CursorAndToolTipCard() {
    ExampleCard("cursor / toolTip") {
        var hand by remember { mutableStateOf(true) }
        CheckBox(text = "Hand cursor", checked = hand, onCheckedChange = { hand = it })
        Label(
            "Hover me: cursor + tooltip",
            modifier =
                SwingModifier
                    .opaque(true)
                    .background(Color(0xE8, 0xF5, 0xE9))
                    .preferredSize(Dimension(260, 32))
                    .cursor(Cursor.getPredefinedCursor(if (hand) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR))
                    .toolTip(if (hand) "Click affordance: the hand cursor" else "Plain pointer"),
        )
    }
}

@Composable
internal fun ColumnScope.PerLocationToolTipCard() {
    ExampleCard("toolTip per pointer location") {
        val bands =
            listOf(
                "Reads" to Color(0x42, 0x85, 0xF4),
                "Writes" to Color(0xEA, 0x43, 0x35),
                "Evictions" to Color(0xFB, 0xBC, 0x05),
            )
        Canvas(
            modifier =
                SwingModifier
                    .preferredSize(Dimension(300, 56))
                    .lineBorder(Color.GRAY)
                    .toolTip { event ->
                        val width = event.component.width.coerceAtLeast(1)
                        bands.getOrNull(event.x * bands.size / width)?.first
                    },
        ) { g, width, height ->
            val band = width / bands.size
            bands.forEachIndexed { index, (_, color) ->
                g.color = color
                g.fillRect(index * band, 0, band, height)
            }
        }
        WrappedCaption(
            "One component, three tooltips: the lambda is asked for the tip belonging to the band under " +
                "the pointer, which is how a chart names the point it is over and a table the cell.",
        )
    }
}

@Composable
internal fun ColumnScope.ClientPropertyCard() {
    ExampleCard("clientProperty") {
        var tag by remember { mutableStateOf("alpha") }
        Label(
            "Carries clientProperty \"role\" = $tag",
            modifier = SwingModifier.clientProperty("role", tag),
        )
        FlowPanel {
            Button("role = alpha", onClick = { tag = "alpha" })
            Button("role = beta", onClick = { tag = "beta" })
        }
    }
}

@Composable
internal fun ColumnScope.NameCard() {
    ExampleCard("name") {
        var componentName by remember { mutableStateOf("search-field") }
        FlowPanel {
            Label("Component name:")
            TextField(value = componentName, onValueChange = { componentName = it }, columns = 16)
        }
        Label(
            "Looked up by tests/automation as \"$componentName\"",
            modifier = SwingModifier.name(componentName),
        )
    }
}
