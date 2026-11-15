// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

/** The parameters [DocumentFilterFixture] varies: the document filter declared on the text field. */
public data class DocumentFilterParameters(
    val filter: DocumentFilter?,
)

/** A text field declaring [SwingModifier.documentFilter] against a raw [JTextField] holding the same filter. */
public object DocumentFilterFixture : ComposableFixture<DocumentFilterParameters, JTextField>(
    initial = DocumentFilterParameters(filter = DocumentFilter()),
) {
    /** The single-change copies of [initial]: the filter swapped for one that upper-cases every inserted string. */
    override val alternatives: List<DocumentFilterParameters> =
        listOf(
            initial.copy(
                filter =
                    object : DocumentFilter() {
                        override fun insertString(
                            bypass: FilterBypass,
                            offset: Int,
                            text: String?,
                            attributes: AttributeSet?,
                        ) {
                            super.insertString(bypass, offset, text?.uppercase(), attributes)
                        }
                    },
            ),
        )

    @Composable
    override fun Content(parameters: DocumentFilterParameters): Unit = Content(parameters.filter)

    /** Composes a text field that declares [filter] as its document filter. */
    @Composable
    public fun Content(filter: DocumentFilter?) {
        SwingNode(
            factory = { JTextField("Text").also { it.caretPosition = 0 } },
            modifier = SwingModifier.documentFilter(filter),
        )
    }

    /** Builds a bare [JTextField] showing the same text and caret position as the composed text field. */
    override fun createRawSwing(): JTextField = JTextField("Text").also { it.caretPosition = 0 }

    /** Sets the document filter of [component]'s document to the filter of [parameters]. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: DocumentFilterParameters,
    ) {
        val document = component.document
        if (document is AbstractDocument) {
            document.documentFilter = parameters.filter
        }
    }
}
