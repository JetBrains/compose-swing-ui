// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures

/**
 * Marks a fixture whose raw side is a [java.awt.Window] that declares the properties of the window itself, so the
 * raw Swing check compares the composed window's title, size and the like as well as its content.
 */
public interface WindowPropertiesFixture
