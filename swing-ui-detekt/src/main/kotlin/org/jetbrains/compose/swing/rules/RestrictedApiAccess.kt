// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression

/**
 * The `@RestrictTo` scope that applies to [expression], a use of [symbol]. For a property, it is the union of
 * the scopes of the accessors the expression invokes; each accessor, then the property, then its backing field,
 * then the declarations that contain it are searched in turn for the first `@RestrictTo`.
 */
internal fun KaSession.declaredRestrictionScope(
    symbol: KaDeclarationSymbol,
    expression: KtSimpleNameExpression,
): Int {
    val accessors = (symbol as? KaPropertySymbol)?.let { invokedAccessors(it, expression) } ?: listOf(null)
    return restrictionScope(symbol, accessors)
}

/**
 * The `@RestrictTo` scope of [symbol] as seen through [accessors], each searched before [symbol] and the
 * declarations that contain it; a null accessor stands for [symbol] used as a whole.
 */
internal fun KaSession.restrictionScope(
    symbol: KaDeclarationSymbol,
    accessors: List<KaPropertyAccessorSymbol?>,
): Int =
    accessors.fold(0) { bits, accessor ->
        val chain =
            sequence {
                if (accessor != null) yield(accessor)
                for (declaration in generateSequence(symbol) { it.containingDeclaration }) {
                    yield(declaration)
                    (declaration as? KaPropertySymbol)?.let { propertyBackingField(it) }?.let { yield(it) }
                }
            }
        bits or (chain.firstNotNullOfOrNull { it.annotations.restrictToScope() } ?: 0)
    }

/**
 * Whether a declaration that contains [expression] is itself restricted to tests, which makes a use of another
 * test-restricted API inside it a use from tests. An annotation without a use-site target on a property lands on
 * its backing field, so the field is checked as well.
 */
internal fun KaSession.isInsideTestRestrictedDeclaration(expression: KtElement): Boolean =
    generateSequence(expression.parent) { it.parent }
        .filterIsInstance<KtDeclaration>()
        .filter { it.annotationEntries.isNotEmpty() }
        .map { it.symbol }
        .any { declaration ->
            val annotated = listOfNotNull(declaration, (declaration as? KaPropertySymbol)?.backingFieldSymbol)
            annotated.any { (it.annotations.restrictToScope() ?: 0) and SCOPE_TESTS != 0 }
        }

/**
 * The backing field of [property], whose `@RestrictTo` stands for the property: the annotation has no property
 * target, so one without a use-site target lands on the field. A source property's `@field:RestrictTo` is an
 * explicit request that only field references be restricted, so it is not read here. A compiled field's
 * annotation is the same whichever way it was written, so it is always read.
 */
private fun KaSession.propertyBackingField(property: KaPropertySymbol): KaBackingFieldSymbol? {
    val field = property.backingFieldSymbol ?: return null
    val restrictTo = ClassId.topLevel(FqName("androidx.annotation.RestrictTo"))
    val explicitlyOnField =
        property.origin == KaSymbolOrigin.SOURCE &&
            field.annotations[restrictTo].any { it.useSiteTarget == AnnotationUseSiteTarget.FIELD }
    return field.takeUnless { explicitlyOnField }
}

internal fun KaSession.invokedAccessors(
    property: KaPropertySymbol,
    name: KtSimpleNameExpression,
): List<KaPropertyAccessorSymbol?> {
    val target = (name.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == name } ?: name
    val user = target.parent
    val reads = listOf(property.getter)
    val writes = listOf(property.setter)
    val both = reads + writes
    return when {
        user is KtBinaryExpression && user.left == target -> {
            when (user.operationToken) {
                KtTokens.EQ -> writes
                in KtTokens.AUGMENTED_ASSIGNMENTS -> if (isReassigning(user)) both else reads
                else -> reads
            }
        }

        user is KtUnaryExpression && user.baseExpression == target -> {
            if (user.operationToken == KtTokens.PLUSPLUS || user.operationToken == KtTokens.MINUSMINUS) both else reads
        }

        else -> {
            reads
        }
    }
}

private fun KaSession.isReassigning(assignment: KtBinaryExpression): Boolean =
    assignment.resolveToCall()?.singleCallOrNull<KaCompoundVariableAccessCall>() != null
