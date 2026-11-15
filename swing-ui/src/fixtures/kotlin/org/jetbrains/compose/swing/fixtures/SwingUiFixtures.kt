// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

import org.jetbrains.compose.swing.fixtures.components.ComboBoxActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.ComboBoxFixture
import org.jetbrains.compose.swing.fixtures.components.ComboBoxModelActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.ComboBoxModelFixture
import org.jetbrains.compose.swing.fixtures.components.LabelFixture
import org.jetbrains.compose.swing.fixtures.components.LayerFixture
import org.jetbrains.compose.swing.fixtures.components.LayerUiFixture
import org.jetbrains.compose.swing.fixtures.components.ProgressBarFixture
import org.jetbrains.compose.swing.fixtures.components.ProgressBarModelFixture
import org.jetbrains.compose.swing.fixtures.components.SeparatorFixture
import org.jetbrains.compose.swing.fixtures.components.SliderChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.components.SliderFixture
import org.jetbrains.compose.swing.fixtures.components.SliderModelChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.components.SliderModelFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerDateFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerListFixture
import org.jetbrains.compose.swing.fixtures.components.SpinnerModelFixture
import org.jetbrains.compose.swing.fixtures.components.button.ButtonActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.button.ButtonFixture
import org.jetbrains.compose.swing.fixtures.components.button.CheckBoxActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.button.CheckBoxFixture
import org.jetbrains.compose.swing.fixtures.components.button.RadioButtonActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.button.RadioButtonFixture
import org.jetbrains.compose.swing.fixtures.components.button.ToggleButtonActionListenerFixture
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
import org.jetbrains.compose.swing.fixtures.components.layout.SplitPaneDividerLocationListenerFixture
import org.jetbrains.compose.swing.fixtures.components.layout.SplitPaneFixture
import org.jetbrains.compose.swing.fixtures.components.layout.StrutFixture
import org.jetbrains.compose.swing.fixtures.components.layout.TabbedPaneChangeListenerFixture
import org.jetbrains.compose.swing.fixtures.components.layout.TabbedPaneFixture
import org.jetbrains.compose.swing.fixtures.components.layout.VerticalScrollbarFixture
import org.jetbrains.compose.swing.fixtures.components.layout.ViewportFixture
import org.jetbrains.compose.swing.fixtures.components.menu.CheckBoxMenuItemActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.menu.CheckBoxMenuItemFixture
import org.jetbrains.compose.swing.fixtures.components.menu.ContextMenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuItemActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuItemFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuSeparatorFixture
import org.jetbrains.compose.swing.fixtures.components.menu.PopupMenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.RadioButtonMenuItemActionListenerFixture
import org.jetbrains.compose.swing.fixtures.components.menu.RadioButtonMenuItemFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxListenerFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxModelFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.RadioGroupFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableListenerFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableModelFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableModelListenerFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableModelStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TableStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeListenerFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeModelFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeModelListenerFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeModelStateFixture
import org.jetbrains.compose.swing.fixtures.components.selection.TreeStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.EditorPaneFixture
import org.jetbrains.compose.swing.fixtures.components.text.EditorPaneListenerFixture
import org.jetbrains.compose.swing.fixtures.components.text.EditorPaneStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.FormattedTextFieldFixture
import org.jetbrains.compose.swing.fixtures.components.text.FormattedTextFieldListenerFixture
import org.jetbrains.compose.swing.fixtures.components.text.FormattedTextFieldStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.PasswordFieldFixture
import org.jetbrains.compose.swing.fixtures.components.text.PasswordFieldListenerFixture
import org.jetbrains.compose.swing.fixtures.components.text.PasswordFieldStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextAreaFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextAreaStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextFieldFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextFieldListenerFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextFieldStateFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextPaneFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextPaneListenerFixture
import org.jetbrains.compose.swing.fixtures.components.text.TextPaneStateFixture

/** Every fixture of swing-ui, for consumers that run all of them, such as the benchmark suites. */
public val swingUiFixtures: List<ComposableFixture<*, *>> =
    listOf(
        ComboBoxActionListenerFixture,
        ComboBoxFixture,
        ComboBoxModelActionListenerFixture,
        ComboBoxModelFixture,
        LabelFixture,
        LayerFixture,
        LayerUiFixture,
        ProgressBarFixture,
        ProgressBarModelFixture,
        SeparatorFixture,
        SliderChangeListenerFixture,
        SliderFixture,
        SliderModelChangeListenerFixture,
        SliderModelFixture,
        SpinnerDateFixture,
        SpinnerFixture,
        SpinnerListFixture,
        SpinnerModelFixture,
        ButtonActionListenerFixture,
        ButtonFixture,
        CheckBoxActionListenerFixture,
        CheckBoxFixture,
        RadioButtonActionListenerFixture,
        RadioButtonFixture,
        ToggleButtonActionListenerFixture,
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
        SplitPaneDividerLocationListenerFixture,
        SplitPaneFixture,
        StrutFixture,
        TabbedPaneChangeListenerFixture,
        TabbedPaneFixture,
        VerticalScrollbarFixture,
        ViewportFixture,
        CheckBoxMenuItemActionListenerFixture,
        CheckBoxMenuItemFixture,
        ContextMenuFixture,
        MenuFixture,
        MenuItemActionListenerFixture,
        MenuItemFixture,
        MenuSeparatorFixture,
        PopupMenuFixture,
        RadioButtonMenuItemActionListenerFixture,
        RadioButtonMenuItemFixture,
        ListBoxFixture,
        ListBoxListenerFixture,
        ListBoxModelFixture,
        ListBoxStateFixture,
        RadioGroupFixture,
        TableFixture,
        TableListenerFixture,
        TableModelFixture,
        TableModelListenerFixture,
        TableModelStateFixture,
        TableStateFixture,
        TreeFixture,
        TreeListenerFixture,
        TreeModelFixture,
        TreeModelListenerFixture,
        TreeModelStateFixture,
        TreeStateFixture,
        EditorPaneFixture,
        EditorPaneListenerFixture,
        EditorPaneStateFixture,
        FormattedTextFieldFixture,
        FormattedTextFieldListenerFixture,
        FormattedTextFieldStateFixture,
        PasswordFieldFixture,
        PasswordFieldListenerFixture,
        PasswordFieldStateFixture,
        TextAreaFixture,
        TextAreaStateFixture,
        TextFieldFixture,
        TextFieldListenerFixture,
        TextFieldStateFixture,
        TextPaneFixture,
        TextPaneListenerFixture,
        TextPaneStateFixture,
    )
