package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.modifier.accessibility.accessibleDescription
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.layout.bounds
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.modifier.layout.height
import org.jetbrains.compose.swing.modifier.layout.location
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.size
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.modifier.layout.width
import org.jetbrains.compose.swing.modifier.layout.x
import org.jetbrains.compose.swing.modifier.layout.y
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.accessibleDescription
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.bounds
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.focusable
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.height
import org.jetbrains.compose.swing.style.location
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.size
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.style.width
import org.jetbrains.compose.swing.style.x
import org.jetbrains.compose.swing.style.y
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.JLayeredPane

// The test calls the deprecated modifiers on purpose to pin their forwarding.

/**
 * A deprecated modifier leaves the component with what the style setter of the same name writes, and removing it
 * restores the value the component held before.
 */
@Suppress("DEPRECATION")
class DeprecatedComponentModifiersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun aDeprecatedModifierLeavesWhatTheStyleSetterWritesAndRemovingItRestoresTheOriginal(
        case: DeprecatedModifierCase<*>,
    ) = runDeprecatedModifierCase(case)

    companion object {
        @JvmStatic
        fun cases(): List<Named<*>> = appearanceCases() + geometryCases() + sizeCases()

        private fun appearanceCases(): List<Named<*>> = listOf(
            label("foreground", Color.MAGENTA, { it.foreground }, SwingModifier.foreground(Color.MAGENTA)) {
                foreground(Color.MAGENTA)
            },
            label("background", Color.ORANGE, { it.background }, SwingModifier.background(Color.ORANGE)) {
                background(Color.ORANGE)
            },
            label(
                "font",
                Font("Dialog", Font.BOLD, 19),
                { it.font },
                SwingModifier.font(Font("Dialog", Font.BOLD, 19)),
            ) { font(Font("Dialog", Font.BOLD, 19)) },
            label(
                "cursor",
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                { it.cursor },
                SwingModifier.cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)),
            ) { cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)) },
            label("enabled", false, { it.isEnabled }, SwingModifier.enabled(false)) { enabled(false) },
            label("focusable", false, { it.isFocusable }, SwingModifier.focusable(false)) { focusable(false) },
            label("visible", false, { it.isVisible }, SwingModifier.visible(false)) { visible(false) },
            label(
                "componentOrientation",
                ComponentOrientation.RIGHT_TO_LEFT,
                { it.componentOrientation },
                SwingModifier.componentOrientation(ComponentOrientation.RIGHT_TO_LEFT),
            ) { componentOrientation(ComponentOrientation.RIGHT_TO_LEFT) },
            label(
                "accessibleName",
                "Names the label",
                { it.accessibleContext.accessibleName },
                SwingModifier.accessibleName("Names the label"),
            ) { accessibleName("Names the label") },
            label(
                "accessibleDescription",
                "Explains the label",
                { it.accessibleContext.accessibleDescription },
                SwingModifier.accessibleDescription("Explains the label"),
            ) { accessibleDescription("Explains the label") },
        ).map { Named.of(it.name, it) }

        private fun geometryCases(): List<Named<*>> = listOf(
            label("x", 37, { it.x }, SwingModifier.x(37)) { x(37) },
            label("y", 29, { it.y }, SwingModifier.y(29)) { y(29) },
            label("location(x, y)", Point(31, 17), { it.location }, SwingModifier.location(31, 17)) {
                location(31, 17)
            },
            label("location(point)", Point(31, 17), { it.location }, SwingModifier.location(Point(31, 17))) {
                location(Point(31, 17))
            },
            label("width", 120, { it.width }, SwingModifier.width(120)) { width(120) },
            label("height", 45, { it.height }, SwingModifier.height(45)) { height(45) },
            label("size(width, height)", Dimension(121, 46), { it.size }, SwingModifier.size(121, 46)) {
                size(121, 46)
            },
            label("size(dimension)", Dimension(121, 46), { it.size }, SwingModifier.size(Dimension(121, 46))) {
                size(Dimension(121, 46))
            },
            label(
                "bounds",
                Rectangle(11, 12, 130, 40),
                { it.bounds },
                SwingModifier.bounds(11, 12, 130, 40),
            ) { bounds(11, 12, 130, 40) },
        ).map { Named.of(it.name, it) }

        private fun sizeCases(): List<Named<*>> = listOf(
            label(
                "preferredSize(dimension)",
                Dimension(141, 52),
                { it.preferredSize },
                SwingModifier.preferredSize(Dimension(141, 52)),
            ) { preferredSize(Dimension(141, 52)) },
            label(
                "preferredSize(width, height)",
                Dimension(141, 52),
                { it.preferredSize },
                SwingModifier.preferredSize(141, 52),
            ) { preferredSize(141, 52) },
            label(
                "minimumSize(dimension)",
                Dimension(33, 22),
                { it.minimumSize },
                SwingModifier.minimumSize(Dimension(33, 22)),
            ) { minimumSize(Dimension(33, 22)) },
            label(
                "minimumSize(width, height)",
                Dimension(33, 22),
                { it.minimumSize },
                SwingModifier.minimumSize(33, 22),
            ) { minimumSize(33, 22) },
            label(
                "maximumSize(dimension)",
                Dimension(333, 222),
                { it.maximumSize },
                SwingModifier.maximumSize(Dimension(333, 222)),
            ) { maximumSize(Dimension(333, 222)) },
            label(
                "maximumSize(width, height)",
                Dimension(333, 222),
                { it.maximumSize },
                SwingModifier.maximumSize(333, 222),
            ) { maximumSize(333, 222) },
        ).map { Named.of(it.name, it) }

        private fun label(
            name: String,
            expected: Any?,
            read: (JLabel) -> Any?,
            modifier: SwingModifier,
            style: LabelStyleScope.() -> Unit,
        ) = DeprecatedModifierCase<LabelStyleScope>(
            name,
            expected,
            { read(it as JLabel) },
            modifier,
            SwingStyle(style),
        ) { hostModifier, hostStyle ->
            LayeredPane { Label("component", hostModifier.layer(JLayeredPane.DEFAULT_LAYER), hostStyle) }
        }
    }
}
