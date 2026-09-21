// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

import org.jetbrains.compose.swing.fixtures.modifier.ComposedFixture
import org.jetbrains.compose.swing.fixtures.modifier.HierarchyListenerCallbackFixture
import org.jetbrains.compose.swing.fixtures.modifier.HierarchyListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.KeyFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.ClientPropertyFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.NameFixture
import org.jetbrains.compose.swing.fixtures.modifier.appearance.TestTagFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.ClipboardFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.DraggableFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.DropTargetFixture
import org.jetbrains.compose.swing.fixtures.modifier.datatransfer.OnExportDoneFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.FocusRequesterFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.FocusTraversalIndexFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.InitialFocusFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.InputVerifierFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnFocusFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnHoverFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OnPointerEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.interaction.OrderedFocusTraversalFixture
import org.jetbrains.compose.swing.fixtures.modifier.keyboard.OnKeyEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.keyboard.OnKeyStrokeFixture
import org.jetbrains.compose.swing.fixtures.modifier.keyboard.OnKeyStrokeKeyStrokeFixture
import org.jetbrains.compose.swing.fixtures.modifier.layout.LayoutConstraintFixture
import org.jetbrains.compose.swing.fixtures.modifier.layout.SlotFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.AdjustmentListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.AdjustmentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerChangeFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ComponentListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerDirectionalFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ContainerListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.FocusListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.FocusListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ItemListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ItemListenerListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.ItemListenerScopedFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.KeyListenerEventFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.KeyListenerFixture
import org.jetbrains.compose.swing.fixtures.modifier.listener.KeyListenerListenerFixture
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
import org.jetbrains.compose.swing.fixtures.node.RememberMirrorStateFixture
import org.jetbrains.compose.swing.fixtures.node.SwingNodeContainerFixture
import org.jetbrains.compose.swing.fixtures.node.SwingNodeFixture
import org.jetbrains.compose.swing.fixtures.node.VirtualSwingNodeFixture
import org.jetbrains.compose.swing.fixtures.style.StyleableFixture
import org.jetbrains.compose.swing.fixtures.window.DialogFixture
import org.jetbrains.compose.swing.fixtures.window.MenuBarFixture
import org.jetbrains.compose.swing.fixtures.window.WindowFixture

/** Every fixture of swing-ui-core, for consumers that run all of them, such as the benchmark suites. */
public val swingUiCoreFixtures: List<ComposableFixture<*, *>> =
    listOf(
        ComposedFixture,
        HierarchyListenerCallbackFixture,
        HierarchyListenerFixture,
        KeyFixture,
        ClientPropertyFixture,
        NameFixture,
        TestTagFixture,
        ClipboardFixture,
        DraggableFixture,
        DropTargetFixture,
        OnExportDoneFixture,
        FocusRequesterFixture,
        FocusTraversalIndexFixture,
        InitialFocusFixture,
        InputVerifierFixture,
        OnFocusFixture,
        OnHoverFixture,
        OnPointerEventFixture,
        OrderedFocusTraversalFixture,
        OnKeyEventFixture,
        OnKeyStrokeFixture,
        OnKeyStrokeKeyStrokeFixture,
        LayoutConstraintFixture,
        SlotFixture,
        AdjustmentListenerFixture,
        AdjustmentListenerListenerFixture,
        ComponentListenerChangeFixture,
        ComponentListenerFixture,
        ComponentListenerListenerFixture,
        ContainerListenerDirectionalFixture,
        ContainerListenerFixture,
        ContainerListenerListenerFixture,
        FocusListenerFixture,
        FocusListenerListenerFixture,
        ItemListenerFixture,
        ItemListenerListenerFixture,
        ItemListenerScopedFixture,
        KeyListenerEventFixture,
        KeyListenerFixture,
        KeyListenerListenerFixture,
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
        RememberMirrorStateFixture,
        SwingNodeContainerFixture,
        SwingNodeFixture,
        VirtualSwingNodeFixture,
        StyleableFixture,
        DialogFixture,
        MenuBarFixture,
        WindowFixture,
    )
