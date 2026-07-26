package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JScrollPane
import javax.swing.SwingConstants

// The left-edge alignment shared by every direct child of a card column. A vertical BoxLayout lines its
// children up by alignmentX; leaf controls already report 0.0 but panels default to centered (0.5), and
// mixing the two pushes the left-aligned controls sideways to track the centered ones - an offset that
// shifts whenever the column width changes, so a control visibly jumps on toggle. Tagging every panel
// sibling with this keeps the column flush-left and stable.
internal const val LEFT_ALIGNED: Float = Component.LEFT_ALIGNMENT

// A navigable section of the showcase: a sidebar title paired with the composable that renders its body,
// so adding a section is a single list entry and the navigation shell and the body switch read from one source.
internal class ShowcaseSection(
    val title: String,
    val body: @Composable () -> Unit,
)

internal val showcaseSections: List<ShowcaseSection> =
    listOf(
        ShowcaseSection("Components") { ComponentsSection() },
        ShowcaseSection("Form inputs") { FormInputsSection() },
        ShowcaseSection("RadioGroup") { RadioGroupSection() },
        ShowcaseSection("Rich text") { RichTextSection() },
        ShowcaseSection("Editor") { EditorSection() },
        ShowcaseSection("Accessibility") { AccessibilitySection() },
        ShowcaseSection("Table") { TableSection() },
        ShowcaseSection("Tree") { TreeSection() },
        ShowcaseSection("Layouts") { LayoutsSection() },
        ShowcaseSection("Split & ToolBar") { SplitToolBarSection() },
        ShowcaseSection("ScrollPane") { ScrollPaneSection() },
        ShowcaseSection("Tabs") { TabsSection() },
        ShowcaseSection("Canvas") { CanvasSection() },
        ShowcaseSection("Custom component") { CustomComponentSection() },
        ShowcaseSection("Context menu") { ContextMenuSection() },
        ShowcaseSection("Data transfer") { DataTransferSection() },
        ShowcaseSection("Top-level windows") { WindowsSection() },
        ShowcaseSection("Layered & MDI") { LayeredAndMdiSection() },
        ShowcaseSection("System tray") { TraySection() },
        ShowcaseSection("Dynamic hierarchy") { DynamicHierarchySection() },
        ShowcaseSection("Composition locals") { CompositionLocalsSection() },
        ShowcaseSection("Effects") { EffectsSection() },
        ShowcaseSection("Animation") { AnimationSection() },
        ShowcaseSection("Modifier gallery") { ModifierGallery() },
    )

@Composable
internal fun ExampleCard(
    title: String,
    content: @Composable () -> Unit,
) {
    BorderPanel(
        modifier =
            SwingModifier.border(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6),
                ),
            ),
    ) {
        north {
            BoxPanel(axis = BoxLayout.Y_AXIS) {
                content()
            }
        }
    }
}

// The standard body shape for a section: a vertical column of cards that scrolls vertically only, so a
// wide example never forces a sideways scrollbar onto the whole section.
@Composable
internal fun SectionColumn(cards: @Composable () -> Unit) {
    ScrollPane(
        verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    ) {
        content {
            BoxPanel(axis = BoxLayout.Y_AXIS) {
                cards()
            }
        }
    }
}

@Composable
internal fun SectionHeading(text: String) {
    FlowPanel(alignment = FlowLayout.LEADING) {
        Label(
            text = text,
            modifier =
                SwingModifier
                    .font(Font(Font.SANS_SERIF, Font.BOLD, 16))
                    .foreground(Color(0x2D4B73)),
        )
    }
}

// Explanatory text inside a card. Wrapping it in an HTML body of bounded width lets a long caption flow
// onto multiple lines instead of forcing the layout wide and triggering a horizontal scrollbar.
@Composable
internal fun WrappedCaption(text: String) {
    Label(
        text = "<html><body style='width:440px'>$text</body></html>",
        modifier = SwingModifier.horizontalAlignment(SwingConstants.LEADING),
    )
}
