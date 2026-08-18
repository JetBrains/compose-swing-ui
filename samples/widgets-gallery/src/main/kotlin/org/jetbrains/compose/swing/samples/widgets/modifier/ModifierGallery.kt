package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.tooling.Preview

// A gallery where each SwingModifier builder visibly affects a real widget, across the modifier
// families - appearance, layout, interaction, keyboard, and raw listeners. State is hoisted so the
// appearance modifiers toggle, the interaction modifiers update a live status label, and the
// keyboard/raw-listener modifiers fire counters. The cards for each family live in a sibling file
// named for that family.
@Preview
@Composable
internal fun ModifierGallery() {
    SectionColumn {
        SectionHeading("Modifier gallery")
        AppearanceCard()
        IconFamilyCard()
        TextPositionCard()
        ButtonPaintingCard()
        CursorAndToolTipCard()
        PerLocationToolTipCard()
        ClientPropertyCard()
        NameCard()
        SizeAndVisibilityCard()
        SizeConstraintsCard()
        GeometryCard()
        AlignmentCard()
        VerticalAlignmentCard()
        MarginCard()
        EnabledCard()
        FocusableCard()
        FocusCard()
        InitialFocusCard()
        ButtonGroupCard()
        DisplayedMnemonicIndexCard()
        ActionCommandCard()
        PopupMenuCard()
        HoverFocusCard()
        PointerCard()
        KeyStrokeCard()
        KeyEventCard()
        SwingListenerCard()
        ChangeListenerCard()
        ListenerEscapeHatchCard()
        ItemListenerCard()
        DocumentListenerCard()
        PropertyChangeListenerCard()
        KeyListenerCard()
        MouseMotionAndWheelListenerCard()
        ComponentAndHierarchyListenerCard()
        ContainerListenerCard()
        ListSelectionListenerCard()
        TreeListenersCard()
        HyperlinkListenerCard()
        InternalFrameListenerCard()
    }
}
