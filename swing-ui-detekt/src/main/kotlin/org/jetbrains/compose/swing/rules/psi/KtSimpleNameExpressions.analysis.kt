// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.psi

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal fun KtSimpleNameExpression.isResolvedReadOf(property: KtProperty): Boolean = runCatching {
    analyze(this) {
        val propertySymbol = property.symbol
        val referenceSymbol = this@isResolvedReadOf.mainReference.resolveToSymbol()
        if (referenceSymbol == propertySymbol || referenceSymbol?.sourcePsiSafe<KtProperty>() == property) {
            return@analyze true
        }

        val read = this@isResolvedReadOf.resolveToCall() as? KaVariableAccessCall ?: return@analyze false
        read.signature.symbol == propertySymbol || read.signature.symbol.sourcePsiSafe<KtProperty>() == property
    }
}.getOrDefault(false)

internal fun KtSimpleNameExpression.resolvedPropertyInitializer(): KtExpression? =
    resolvedLocalImmutableProperty()?.initializer

internal fun KtSimpleNameExpression.resolvedLocalImmutableProperty(): KtProperty? = runCatching {
    analyze(this) {
        mainReference.resolveToSymbol()
            ?.sourcePsiSafe<KtProperty>()
            ?.takeIf { property -> property.isLocal && !property.isVar }
    }
}.getOrNull()

internal fun KtSimpleNameExpression.isPropertyReadWithExecutableAccess(): Boolean = resolvedPropertyRead(
    symbolPredicate = { false },
    sourcePredicate = { property -> property.getter?.bodyExpression != null || property.hasDelegate() },
)

internal fun KtSimpleNameExpression.resolvedPropertyRead(
    symbolPredicate: (KaPropertySymbol) -> Boolean,
    sourcePredicate: (KtProperty) -> Boolean,
): Boolean = runCatching {
    analyze(this) {
        val property = ((resolveToCall() as? KaVariableAccessCall)?.signature?.symbol as? KaPropertySymbol)
            ?: mainReference.resolveToSymbol() as? KaPropertySymbol
            ?: return@analyze false
        symbolPredicate(property) || property.sourcePsiSafe<KtProperty>()?.let(sourcePredicate) == true
    }
}.getOrDefault(false)
