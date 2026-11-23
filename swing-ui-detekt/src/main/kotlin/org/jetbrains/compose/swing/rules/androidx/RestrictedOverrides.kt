// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration

/**
 * Reports the restrictions that [declaration] breaks by overriding a restricted member, at its name, the way
 * androidx's check reports a Kotlin override. An override that carries a `@RestrictTo` of its own is not
 * reported, because that annotation, not the overridden member's, is the closer one.
 */
internal fun reportRestrictedOverride(
    declaration: KtCallableDeclaration,
    reporter: ComposeSwingRuleReporter,
    libraries: LibraryCoordinates,
) {
    if (!declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
    val messages = analyze(declaration) { overrideRestrictionMessages(declaration, libraries) }
    messages.forEach { reporter.report(declaration.nameIdentifier ?: declaration, it) }
}

private fun KaSession.overrideRestrictionMessages(
    declaration: KtCallableDeclaration,
    libraries: LibraryCoordinates,
): List<String> {
    val override = declaration.symbol as? KaCallableSymbol
    if (override == null || override.annotations.restrictToScope() != null) return emptyList()
    return override.directlyOverriddenSymbols
        .toList()
        .flatMap { overridden ->
            val accessors = (overridden as? KaPropertySymbol)?.let { listOf(it.getter, it.setter) } ?: listOf(null)
            restrictionMessages(overridden, declaration, restrictionScope(overridden, accessors), libraries)
        }.distinct()
}
