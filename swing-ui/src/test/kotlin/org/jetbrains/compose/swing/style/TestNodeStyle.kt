package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.style.LabelStyleScope

/** The kind of a test's raw node. It applies only [AllComponents]. */
internal val TestNodeKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    )

/** As [TestNodeKind], for a raw node built on a label. */
internal val TestLabelKind =
    SwingStyleKind<LabelStyleScope, Unit>({ base, _ -> object : LabelStyleScope, ComponentStyleScope by base {} })
