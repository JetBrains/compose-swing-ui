// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.components.button.ButtonFixture
import org.jetbrains.compose.swing.fixtures.components.button.CheckBoxFixture
import org.jetbrains.compose.swing.fixtures.components.button.RadioButtonFixture
import org.jetbrains.compose.swing.fixtures.components.button.ToggleButtonFixture
import org.jetbrains.compose.swing.fixtures.components.desktop.DesktopPaneFixture
import org.jetbrains.compose.swing.fixtures.components.desktop.GlassPaneFixture
import org.jetbrains.compose.swing.fixtures.components.desktop.GlassPaneLayoutFixture
import org.jetbrains.compose.swing.fixtures.components.desktop.InternalFrameFixture
import org.jetbrains.compose.swing.fixtures.components.desktop.InternalFrameStateFixture
import org.jetbrains.compose.swing.fixtures.components.desktop.LayeredPaneFixture
import org.jetbrains.compose.swing.fixtures.components.layout.GlueFixture
import org.jetbrains.compose.swing.fixtures.components.layout.HorizontalScrollbarFixture
import org.jetbrains.compose.swing.fixtures.components.layout.PanelFixture
import org.jetbrains.compose.swing.fixtures.components.layout.RigidAreaFixture
import org.jetbrains.compose.swing.fixtures.components.layout.ScrollPaneFixture
import org.jetbrains.compose.swing.fixtures.components.layout.SpacerFixture
import org.jetbrains.compose.swing.fixtures.components.layout.SplitPaneFixture
import org.jetbrains.compose.swing.fixtures.components.layout.StrutFixture
import org.jetbrains.compose.swing.fixtures.components.layout.TabbedPaneFixture
import org.jetbrains.compose.swing.fixtures.components.layout.VerticalScrollbarFixture
import org.jetbrains.compose.swing.fixtures.components.layout.ViewportFixture
import org.jetbrains.compose.swing.fixtures.components.menu.CheckBoxMenuItemFixture
import org.jetbrains.compose.swing.fixtures.components.menu.ContextMenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuItemFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuSeparatorFixture
import org.jetbrains.compose.swing.fixtures.components.menu.PopupMenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.RadioButtonMenuItemFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.accessibility.LabelForFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.accessibility.LabelTargetFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.interaction.ButtonGroupFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.interaction.DefaultButtonFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.interaction.DocumentFilterFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.interaction.NavigationFilterFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.interaction.OnAcceptFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ActionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.CaretListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.CaretListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ChangeListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.DocumentListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.DocumentListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.DocumentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.HyperlinkListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.HyperlinkListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.InternalFrameListenerChangeFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.InternalFrameListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.InternalFrameListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ListSelectionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ListSelectionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.ListSelectionListenerScopedFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeExpansionListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeExpansionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeExpansionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeSelectionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeSelectionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeWillExpandListenerDirectionalFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeWillExpandListenerFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.listener.TreeWillExpandListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxModelFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.RadioGroupFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableModelStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeModelStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.EditorPaneFixture
import org.jetbrains.compose.swing.fixtures.components.text.EditorPaneStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.FormattedTextFieldFixture
import org.jetbrains.compose.swing.fixtures.components.text.FormattedTextFieldStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.PasswordFieldFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextAreaFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextAreaStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextFieldFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextFieldStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextPaneStateFixture

/** Every fixture of swing-ui-components, for consumers that run all of them, such as the benchmark suites. */
public val swingUiComponentsFixtures: List<ComposableFixture<*, *>> =
    listOf(
        ComboBoxFixture,
        ComboBoxModelFixture,
        LabelFixture,
        LayerFixture,
        ProgressBarFixture,
        ProgressBarModelFixture,
        SeparatorFixture,
        SliderFixture,
        SliderModelFixture,
        SpinnerDateFixture,
        SpinnerFixture,
        SpinnerListFixture,
        SpinnerModelFixture,
        ButtonFixture,
        CheckBoxFixture,
        RadioButtonFixture,
        ToggleButtonFixture,
        DesktopPaneFixture,
        GlassPaneFixture,
        GlassPaneLayoutFixture,
        InternalFrameFixture,
        InternalFrameStateFixture,
        LayeredPaneFixture,
        GlueFixture,
        HorizontalScrollbarFixture,
        PanelFixture,
        RigidAreaFixture,
        ScrollPaneFixture,
        SpacerFixture,
        SplitPaneFixture,
        StrutFixture,
        TabbedPaneFixture,
        VerticalScrollbarFixture,
        ViewportFixture,
        CheckBoxMenuItemFixture,
        ContextMenuFixture,
        MenuFixture,
        MenuItemFixture,
        MenuSeparatorFixture,
        PopupMenuFixture,
        RadioButtonMenuItemFixture,
        ListBoxModelFixture,
        ListBoxStateFixture,
        RadioGroupFixture,
        TableModelStateFixture,
        TableStateFixture,
        TreeModelStateFixture,
        TreeStateFixture,
        EditorPaneFixture,
        EditorPaneStateFixture,
        FormattedTextFieldFixture,
        FormattedTextFieldStateFixture,
        PasswordFieldFixture,
        TextAreaFixture,
        TextAreaStateFixture,
        TextFieldFixture,
        TextFieldStateFixture,
        TextPaneStateFixture,
        LabelForFixture,
        LabelTargetFixture,
        ButtonGroupFixture,
        DefaultButtonFixture,
        DocumentFilterFixture,
        NavigationFilterFixture,
        OnAcceptFixture,
        ActionListenerFixture,
        ActionListenerListenerFixture,
        CaretListenerFixture,
        CaretListenerListenerFixture,
        ChangeListenerFixture,
        ChangeListenerListenerFixture,
        DocumentListenerCallbackFixture,
        DocumentListenerFixture,
        DocumentListenerListenerFixture,
        HyperlinkListenerFixture,
        HyperlinkListenerListenerFixture,
        InternalFrameListenerChangeFixture,
        InternalFrameListenerFixture,
        InternalFrameListenerListenerFixture,
        ListSelectionListenerFixture,
        ListSelectionListenerListenerFixture,
        ListSelectionListenerScopedFixture,
        TreeExpansionListenerCallbackFixture,
        TreeExpansionListenerFixture,
        TreeExpansionListenerListenerFixture,
        TreeSelectionListenerFixture,
        TreeSelectionListenerListenerFixture,
        TreeWillExpandListenerDirectionalFixture,
        TreeWillExpandListenerFixture,
        TreeWillExpandListenerListenerFixture,
    )
