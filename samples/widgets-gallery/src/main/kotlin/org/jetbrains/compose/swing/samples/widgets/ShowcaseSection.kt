package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.samples.widgets.components.ComponentsSection
import org.jetbrains.compose.swing.samples.widgets.components.FormInputsSection
import org.jetbrains.compose.swing.samples.widgets.components.RadioGroupSection
import org.jetbrains.compose.swing.samples.widgets.custom.CanvasSection
import org.jetbrains.compose.swing.samples.widgets.custom.CustomComponentSection
import org.jetbrains.compose.swing.samples.widgets.layout.LayoutsSection
import org.jetbrains.compose.swing.samples.widgets.layout.ScrollPaneSection
import org.jetbrains.compose.swing.samples.widgets.layout.SplitToolBarSection
import org.jetbrains.compose.swing.samples.widgets.layout.TabsSection
import org.jetbrains.compose.swing.samples.widgets.modifier.AccessibilitySection
import org.jetbrains.compose.swing.samples.widgets.modifier.ContextMenuSection
import org.jetbrains.compose.swing.samples.widgets.modifier.DataTransferSection
import org.jetbrains.compose.swing.samples.widgets.modifier.ModifierGallery
import org.jetbrains.compose.swing.samples.widgets.runtime.AnimationSection
import org.jetbrains.compose.swing.samples.widgets.runtime.CompositionLocalsSection
import org.jetbrains.compose.swing.samples.widgets.runtime.DynamicHierarchySection
import org.jetbrains.compose.swing.samples.widgets.runtime.EffectsSection
import org.jetbrains.compose.swing.samples.widgets.selection.TableSection
import org.jetbrains.compose.swing.samples.widgets.selection.TreeSection
import org.jetbrains.compose.swing.samples.widgets.text.EditorSection
import org.jetbrains.compose.swing.samples.widgets.text.RichTextSection
import org.jetbrains.compose.swing.samples.widgets.window.LayeredAndMdiSection
import org.jetbrains.compose.swing.samples.widgets.window.TraySection
import org.jetbrains.compose.swing.samples.widgets.window.WindowsSection
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.SwingConstants

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

// One example, titled and boxed. The card spans the width of the section column it sits in, and is the
// column its own examples stack in: each one keeps the size it asks for and starts at the card's leading
// edge, so a card is exactly as tall as its content.
@Composable
internal fun ColumnScope.ExampleCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A border is compared by identity, so one built inline would be a new border on every recomposition
    // and would be written to the panel each time.
    val cardBorder =
        remember(title) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(6, 6, 6, 6),
            )
        }
    Column(modifier = SwingModifier.border(cardBorder).fillWidth()) {
        content()
    }
}

// The standard body shape for a section: a vertical column of cards that scrolls vertically only, so a
// wide example never forces a sideways scrollbar onto the whole section.
@Composable
internal fun SectionColumn(cards: @Composable ColumnScope.() -> Unit) {
    ScrollPane(
        verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    ) {
        Column(SwingModifier.viewport()) {
            cards()
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
