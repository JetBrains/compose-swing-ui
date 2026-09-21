// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.assertDeclares
import org.jetbrains.compose.swing.modifier.listener.assertRefused
import kotlin.test.Test

/**
 * A builder taking one callback per method of a listener interface defaults every one of them, so that a
 * caller names only the methods they want - which leaves a call naming none of them compiling. Such a
 * call is refused, since the listener it would register is built out of the callbacks the call names and
 * can therefore never reach the caller, however the declaration changes afterwards.
 */
class UndeclaredCallbackTest {
    @Test
    fun aBuilderDeclaringNoCallbackIsRefused() {
        assertRefused("documentListener") { SwingModifier.documentListener() }
        assertRefused("internalFrameListener") { SwingModifier.internalFrameListener() }
        assertRefused("treeExpansionListener") { SwingModifier.treeExpansionListener() }
        assertRefused("treeWillExpandListener") { SwingModifier.treeWillExpandListener() }
    }

    @Test
    fun aBuilderDeclaringOneCallbackIsAccepted() {
        assertDeclares("documentListener") { SwingModifier.documentListener(onChange = {}) }
        assertDeclares("internalFrameListener") { SwingModifier.internalFrameListener(onFrameClosing = {}) }
        assertDeclares("treeExpansionListener") { SwingModifier.treeExpansionListener(onTreeCollapsed = {}) }
        assertDeclares("treeWillExpandListener") { SwingModifier.treeWillExpandListener(onWillCollapse = { true }) }
    }
}
