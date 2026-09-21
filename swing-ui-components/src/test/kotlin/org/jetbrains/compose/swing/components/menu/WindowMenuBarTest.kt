// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.menu

import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.components.menu.MenuFixture
import org.jetbrains.compose.swing.fixtures.components.menu.MenuItemFixture
import org.jetbrains.compose.swing.rawSwingTests
import org.junit.jupiter.api.TestFactory

/**
 * Conformance of the menu items and menus a window's menu bar holds: each is held to the counts and
 * to the raw Swing behavior its fixture states.
 */
class WindowMenuBarTest {
    @TestFactory
    fun counts() = MenuItemFixture.countTests()

    @TestFactory
    fun rawSwing() = MenuItemFixture.rawSwingTests()

    @TestFactory
    fun menuCounts() = MenuFixture.countTests()

    @TestFactory
    fun menuRawSwing() = MenuFixture.rawSwingTests()
}
