// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a member or top-level property whose resolved type is a `CompositionLocal` and whose name does
 * not start with `Local`, whatever expression initializes it.
 * Local properties are ignored, as are parameters and constructor properties, which are not `KtProperty`.
 */
internal class CompositionLocalPropertyNaming(
    private val reporter: ComposeSwingRuleReporter,
    private val message: String,
) : KtVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        val name = property.nameIdentifier ?: return
        if (property.isLocal || property.name.orEmpty().startsWith("Local")) return
        val isCompositionLocal =
            analyze(property) { property.returnType.isSubtypeOf(Names.Runtime.CompositionLocal.classId) }
        if (isCompositionLocal) reporter.report(name, message)
    }
}
