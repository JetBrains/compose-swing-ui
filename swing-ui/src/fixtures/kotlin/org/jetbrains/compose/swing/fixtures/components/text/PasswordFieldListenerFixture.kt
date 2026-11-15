// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JPasswordField
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument

/** The characters both sides of [PasswordFieldListenerFixture] show. */
private const val SHOWN_TEXT = "Before"

/** The parameters [PasswordFieldListenerFixture] varies: the document listener that observes edits. */
public data class PasswordFieldListenerParameters(
    val documentListener: DocumentListener,
)

/** A [PasswordField] with the document listener overload against a raw [JPasswordField] showing the same characters. */
public object PasswordFieldListenerFixture : ComposableFixture<PasswordFieldListenerParameters, JPasswordField>(
    initial = PasswordFieldListenerParameters(documentListener = EditListener {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<PasswordFieldListenerParameters> =
        listOf(
            initial.copy(documentListener = EditListener {}),
        )

    @Composable
    override fun Content(parameters: PasswordFieldListenerParameters): Unit = Content(parameters.documentListener)

    /** Composes a [PasswordField] showing the shared characters, reporting document edits to [documentListener]. */
    @Composable
    public fun Content(documentListener: DocumentListener) {
        PasswordField(
            value = SHOWN_TEXT.toCharArray(),
            documentListener = documentListener,
        )
    }

    /** Builds a [JPasswordField] showing the shared characters, before any of the fixture's values are applied. */
    override fun createRawSwing(): JPasswordField = JPasswordField(SHOWN_TEXT)

    /**
     * Attaches the document listener of [parameters] to [component] unless it is already attached. Otherwise, the
     * listeners this fixture attached for [initial] and [alternatives] are removed first.
     */
    override fun updateRawSwing(
        component: JPasswordField,
        parameters: PasswordFieldListenerParameters,
    ) {
        val document = component.document
        val attached = (document as? AbstractDocument)?.documentListeners.orEmpty()
        if (parameters.documentListener !in attached) {
            document.removeDocumentListener(initial.documentListener)
            alternatives.forEach { document.removeDocumentListener(it.documentListener) }
            document.addDocumentListener(parameters.documentListener)
        }
    }
}
