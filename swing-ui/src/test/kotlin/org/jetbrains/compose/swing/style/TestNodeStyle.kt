// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.ComboBoxStyleScope
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.components.style.ListBoxStyleScope
import org.jetbrains.compose.swing.components.style.TextStyleScope

/** The kind of a test's raw node. It applies only [AllComponents]. */
internal val TestNodeKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    )

/** As [TestNodeKind], for a raw node built on a label. */
internal val TestLabelKind =
    SwingStyleKind<LabelStyleScope, Unit>({ base, _ -> object : LabelStyleScope, ComponentStyleScope by base {} })

/** As [TestNodeKind], for a raw node built on a button. */
internal val TestButtonKind =
    SwingStyleKind<ButtonStyleScope, Unit>({ base, _ -> object : ButtonStyleScope, ComponentStyleScope by base {} })

/** As [TestNodeKind], for a test's raw text component. */
internal val TestTextNodeKind =
    SwingStyleKind<TextStyleScope, Unit>({ base, _ -> object : TextStyleScope, ComponentStyleScope by base {} })

/** As [TestNodeKind], for a test's raw list. */
internal val TestListNodeKind =
    SwingStyleKind<ListBoxStyleScope, Unit>({ base, _ -> object : ListBoxStyleScope, ComponentStyleScope by base {} })

/** As [TestNodeKind], for a test's raw combo box. */
internal val TestComboNodeKind =
    SwingStyleKind<ComboBoxStyleScope, Unit>({ base, _ -> object : ComboBoxStyleScope, ComponentStyleScope by base {} })
