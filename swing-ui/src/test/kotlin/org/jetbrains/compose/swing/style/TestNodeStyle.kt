package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.components.style.ComboBoxStyleScope
import org.jetbrains.compose.swing.components.style.ListBoxStyleScope
import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier

/** The kind of a test's raw node. It applies no nested styles, so a raw node gets only the style it declares. */
internal val TestNodeKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    )

/** As [TestNodeKind], for a test's raw text component. */
internal val TestTextNodeKind =
    SwingStyleKind<TextStyleScope, Unit>({ base, _ -> object : TextStyleScope, ComponentStyleScope by base {} })

/** As [TestNodeKind], for a test's raw list. */
internal val TestListNodeKind =
    SwingStyleKind<ListBoxStyleScope, Unit>({ base, _ -> object : ListBoxStyleScope, ComponentStyleScope by base {} })

/** As [TestNodeKind], for a test's raw combo box. */
internal val TestComboNodeKind =
    SwingStyleKind<ComboBoxStyleScope, Unit>({ base, _ -> object : ComboBoxStyleScope, ComponentStyleScope by base {} })

/** Declares [style] on a test's raw node, as [kind]. Put it last in the modifier. */
internal fun <S : ComponentStyleScope> SwingModifier.styleable(
    kind: SwingStyleKind<S, Unit>,
    style: SwingStyle<S>,
): SwingModifier = styleable(style, kind)
