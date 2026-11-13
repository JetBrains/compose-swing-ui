// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.fixture

import javax.swing.JButton

/** A button compiled apart from the rule tests, so rules see it with library origin. */
public class LibraryButton : JButton() {
    /** The callback that the button's own action listener calls when the button is pressed. */
    public var onPress: () -> Unit = {}

    init {
        addActionListener { onPress() }
    }
}
