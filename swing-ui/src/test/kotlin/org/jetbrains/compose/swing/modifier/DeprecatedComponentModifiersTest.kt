package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.modifier.accessibility.accessibleDescription
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusTraversalIndex
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.interaction.verifyInputWhenFocusTarget
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.alignmentY
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
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.FOCUS_TRAVERSAL_INDEX_KEY
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.TestNodeKind
import org.jetbrains.compose.swing.style.accessibleDescription
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.alignmentX
import org.jetbrains.compose.swing.style.alignmentY
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.bounds
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.emptyBorder
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.focusTraversalIndex
import org.jetbrains.compose.swing.style.focusable
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.height
import org.jetbrains.compose.swing.style.lineBorder
import org.jetbrains.compose.swing.style.location
import org.jetbrains.compose.swing.style.maximumSize
import org.jetbrains.compose.swing.style.minimumSize
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.size
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.style.verifyInputWhenFocusTarget
import org.jetbrains.compose.swing.style.visible
import org.jetbrains.compose.swing.style.width
import org.jetbrains.compose.swing.style.x
import org.jetbrains.compose.swing.style.y
import org.jetbrains.compose.swing.util.get
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.border.Border
import javax.swing.border.LineBorder

// The test calls the deprecated modifiers on purpose to pin their forwarding.

@Suppress("DEPRECATION")
class DeprecatedComponentModifiersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun deprecatedModifiersMatchStylesAndRestore(case: DeprecatedModifierCase<*>) = runDeprecatedModifierCase(case)

    companion object {
        private val givenBorder: Border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        private val tipAt: (MouseEvent) -> String? = { "per location" }

        private fun borderInsets(component: JComponent): Insets? = component.border?.getBorderInsets(component)

        private fun line(component: JComponent): Any? =
            (component.border as? LineBorder)?.let { it.lineColor to it.thickness } ?: component.border

        private fun hoveredToolTip(component: JComponent): String? {
            component.dispatchEvent(MouseEvent(component, MouseEvent.MOUSE_MOVED, 0, 0, 1, 1, 0, false))
            return component.toolTipText
        }

        @JvmStatic
        fun cases(): List<Named<*>> =
            appearanceCases() + geometryCases() + sizeCases() + componentCases() + borderCases()

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
            { m, s -> LayeredPane { Label("component", m.layer(JLayeredPane.DEFAULT_LAYER), s) } },
            { read(it as JLabel) },
            modifier,
            SwingStyle(style),
        )

        private fun componentCases(): List<Named<*>> = listOf(
            case("alignmentX", 0.25f, { it.alignmentX }, SwingModifier.alignmentX(0.25f)) { alignmentX(0.25f) },
            case("alignmentY", 0.75f, { it.alignmentY }, SwingModifier.alignmentY(0.75f)) { alignmentY(0.75f) },
            case(
                "focusTraversalIndex",
                3,
                { it[FOCUS_TRAVERSAL_INDEX_KEY] },
                SwingModifier.focusTraversalIndex(3),
            ) { focusTraversalIndex(3) },
            case("opaque", true, { it.isOpaque }, SwingModifier.opaque(true)) { opaque(true) },
            case(
                "verifyInputWhenFocusTarget",
                false,
                { it.verifyInputWhenFocusTarget },
                SwingModifier.verifyInputWhenFocusTarget(false),
            ) { verifyInputWhenFocusTarget(false) },
        )

        private fun borderCases(): List<Named<*>> = listOf(
            case("border", givenBorder, { it.border }, SwingModifier.border(givenBorder)) { border(givenBorder) },
            case(
                "emptyBorder(all)",
                Insets(3, 3, 3, 3),
                ::borderInsets,
                SwingModifier.emptyBorder(3),
            ) { emptyBorder(3) },
            case(
                "emptyBorder(top, left, bottom, right)",
                Insets(1, 2, 3, 4),
                ::borderInsets,
                SwingModifier.emptyBorder(1, 2, 3, 4),
            ) { emptyBorder(1, 2, 3, 4) },
            case(
                "emptyBorder(insets)",
                Insets(4, 3, 2, 1),
                ::borderInsets,
                SwingModifier.emptyBorder(Insets(4, 3, 2, 1)),
            ) { emptyBorder(Insets(4, 3, 2, 1)) },
            case(
                "lineBorder",
                Color.BLUE to 3,
                ::line,
                SwingModifier.lineBorder(Color.BLUE, 3),
            ) { lineBorder(Color.BLUE, 3) },
            case("toolTip(text)", "hint", { it.toolTipText }, SwingModifier.toolTip("hint")) { toolTip("hint") },
            case(
                "toolTip(per location)",
                "per location",
                ::hoveredToolTip,
                SwingModifier.toolTip(tipAt),
            ) { toolTip(tipAt) },
        )

        private fun case(
            name: String,
            expected: Any?,
            read: (JComponent) -> Any?,
            modifier: SwingModifier,
            style: SwingComponentStyleScope.() -> Unit,
        ): Named<*> = Named.of(
            name,
            DeprecatedModifierCase<SwingComponentStyleScope>(
                name,
                expected,
                { m, s -> SwingNode(factory = { JLabel("stock") }, modifier = m.styleable(s, TestNodeKind)) },
                read,
                modifier,
                SwingStyle(style),
            ),
        )
    }
}
