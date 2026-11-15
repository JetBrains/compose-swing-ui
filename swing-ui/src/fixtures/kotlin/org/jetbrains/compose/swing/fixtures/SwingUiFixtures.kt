// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

import org.jetbrains.compose.swing.fixtures.components.ComboBoxFixture
import org.jetbrains.compose.swing.fixtures.components.ComboBoxModelFixture
import org.jetbrains.compose.swing.fixtures.components.LabelFixture
import org.jetbrains.compose.swing.fixtures.components.LayerFixture
import org.jetbrains.compose.swing.fixtures.components.LayerUiFixture
import org.jetbrains.compose.swing.fixtures.components.ProgressBarFixture
import org.jetbrains.compose.swing.fixtures.components.ProgressBarModelFixture
import org.jetbrains.compose.swing.fixtures.components.SeparatorFixture
import org.jetbrains.compose.swing.fixtures.components.SliderFixture
import org.jetbrains.compose.swing.fixtures.components.SliderModelFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerDateFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerListFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerModelFixture
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
import org.jetbrains.compose.swing.fixtures.modifier.ComposedFixture
import org.jetbrains.compose.swing.fixtures.modifier.HierarchyListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.modifier.HierarchyListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.KeyFixture
import org.jetbrains.compose.swing.fixtures.modifier.accessibility.LabelForFixture
import org.jetbrains.compose.swing.fixtures.modifier.accessibility.LabelTargetFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.ClientPropertyFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.NameFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.TestTagFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.ClipboardFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.DraggableFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.DropTargetFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.OnExportDoneFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.ButtonGroupFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.DefaultButtonFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.DocumentFilterFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.FocusRequesterFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.FocusTraversalIndexFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.InitialFocusFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.InputVerifierFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.NavigationFilterFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnAcceptFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnFocusFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnHoverFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnPointerEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OrderedFocusTraversalFixture
import org.jetbrains.compose.swing.fixtures.modifier.keyboard.OnKeyEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.keyboard.OnKeyStrokeFixture
import org.jetbrains.compose.swing.fixtures.modifier.keyboard.OnKeyStrokeKeyStrokeFixture
import org.jetbrains.compose.swing.fixtures.modifier.layout.LayoutConstraintFixture
import org.jetbrains.compose.swing.fixtures.modifier.layout.SlotFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ActionListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ActionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.AdjustmentListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.AdjustmentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.CaretListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.CaretListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ChangeListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerChangeFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerDirectionalFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.DocumentListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.DocumentListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.DocumentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.FocusListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.FocusListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.HyperlinkListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.HyperlinkListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.InternalFrameListenerChangeFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.InternalFrameListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.InternalFrameListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ItemListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ItemListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ItemListenerScopedFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.KeyListenerEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.KeyListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.KeyListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListSelectionListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListSelectionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListSelectionListenerScopedFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ListenerOnEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseListenerEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseMotionListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseMotionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseWheelListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.MouseWheelListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerNameFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.PropertyChangeListenerTargetFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeExpansionListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeExpansionListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeExpansionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeSelectionListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeSelectionListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeWillExpandListenerDirectionalFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeWillExpandListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.TreeWillExpandListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.node.RememberMirrorStateFixture
import org.jetbrains.compose.swing.fixtures.node.SwingNodeContainerFixture
import org.jetbrains.compose.swing.fixtures.node.SwingNodeFixture
import org.jetbrains.compose.swing.fixtures.node.VirtualSwingNodeFixture
import org.jetbrains.compose.swing.fixtures.style.StyleableFixture
import org.jetbrains.compose.swing.fixtures.window.DialogFixture
import org.jetbrains.compose.swing.fixtures.window.MenuBarFixture
import org.jetbrains.compose.swing.fixtures.window.WindowFixture

/** Every fixture of swing-ui, for consumers that run all of them, such as the benchmark suites. */
public val swingUiFixtures: List<ComposableFixture<*, *>> =
    listOf(
        ComboBoxFixture,
        ComboBoxModelFixture,
        LabelFixture,
        LayerFixture,
        LayerUiFixture,
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
        ComposedFixture,
        HierarchyListenerCallbackFixture,
        HierarchyListenerFixture,
        KeyFixture,
        LabelForFixture,
        LabelTargetFixture,
        ClientPropertyFixture,
        NameFixture,
        TestTagFixture,
        ClipboardFixture,
        DraggableFixture,
        DropTargetFixture,
        OnExportDoneFixture,
        ButtonGroupFixture,
        DefaultButtonFixture,
        DocumentFilterFixture,
        FocusRequesterFixture,
        FocusTraversalIndexFixture,
        InitialFocusFixture,
        InputVerifierFixture,
        NavigationFilterFixture,
        OnAcceptFixture,
        OnFocusFixture,
        OnHoverFixture,
        OnPointerEventFixture,
        OrderedFocusTraversalFixture,
        OnKeyEventFixture,
        OnKeyStrokeFixture,
        OnKeyStrokeKeyStrokeFixture,
        LayoutConstraintFixture,
        SlotFixture,
        ActionListenerFixture,
        ActionListenerListenerFixture,
        AdjustmentListenerFixture,
        AdjustmentListenerListenerFixture,
        CaretListenerFixture,
        CaretListenerListenerFixture,
        ChangeListenerFixture,
        ChangeListenerListenerFixture,
        ComponentListenerChangeFixture,
        ComponentListenerFixture,
        ComponentListenerListenerFixture,
        ContainerListenerDirectionalFixture,
        ContainerListenerFixture,
        ContainerListenerListenerFixture,
        DocumentListenerCallbackFixture,
        DocumentListenerFixture,
        DocumentListenerListenerFixture,
        FocusListenerFixture,
        FocusListenerListenerFixture,
        HyperlinkListenerFixture,
        HyperlinkListenerListenerFixture,
        InternalFrameListenerChangeFixture,
        InternalFrameListenerFixture,
        InternalFrameListenerListenerFixture,
        ItemListenerFixture,
        ItemListenerListenerFixture,
        ItemListenerScopedFixture,
        KeyListenerEventFixture,
        KeyListenerFixture,
        KeyListenerListenerFixture,
        ListSelectionListenerFixture,
        ListSelectionListenerListenerFixture,
        ListSelectionListenerScopedFixture,
        ListenerCallbackFixture,
        ListenerFixture,
        ListenerOnEventFixture,
        MouseListenerEventFixture,
        MouseListenerFixture,
        MouseListenerListenerFixture,
        MouseMotionListenerFixture,
        MouseMotionListenerListenerFixture,
        MouseWheelListenerFixture,
        MouseWheelListenerListenerFixture,
        PropertyChangeListenerFixture,
        PropertyChangeListenerListenerFixture,
        PropertyChangeListenerNameFixture,
        PropertyChangeListenerTargetFixture,
        TreeExpansionListenerCallbackFixture,
        TreeExpansionListenerFixture,
        TreeExpansionListenerListenerFixture,
        TreeSelectionListenerFixture,
        TreeSelectionListenerListenerFixture,
        TreeWillExpandListenerDirectionalFixture,
        TreeWillExpandListenerFixture,
        TreeWillExpandListenerListenerFixture,
        RememberMirrorStateFixture,
        SwingNodeContainerFixture,
        SwingNodeFixture,
        VirtualSwingNodeFixture,
        StyleableFixture,
        DialogFixture,
        MenuBarFixture,
        WindowFixture,
    )
