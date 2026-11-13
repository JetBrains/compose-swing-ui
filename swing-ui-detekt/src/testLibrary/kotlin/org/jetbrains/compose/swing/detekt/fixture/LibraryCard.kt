// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.fixture

import javax.swing.JLabel
import javax.swing.JPanel

/** A composite compiled apart from the rule tests, so rules see it with library origin. */
public class LibraryCard : JPanel() {
    /** The label part of the card, which the card builds itself. */
    public val title: JLabel = JLabel()
}
