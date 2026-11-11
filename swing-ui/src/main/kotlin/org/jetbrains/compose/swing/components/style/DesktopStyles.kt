// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey

/** Styles provided for every DesktopPane. */
public val DesktopPanes: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("DesktopPanes")

/** Styles provided for every InternalFrame. */
public val InternalFrames: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("InternalFrames")

/** Styles provided for every LayeredPane. */
public val LayeredPanes: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("LayeredPanes")

/** Styles provided for every Layer. */
public val Layers: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("Layers")

/** Styles provided for every glass pane that the `GlassPane` of a Layer or an InternalFrame declares. */
public val GlassPanes: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("GlassPanes")
