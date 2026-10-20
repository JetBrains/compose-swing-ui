package org.jetbrains.compose.swing.style

/** The kind of a test's raw node. It applies no nested styles, so a raw node gets only the style it declares. */
internal val TestNodeKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    )
