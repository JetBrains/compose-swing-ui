// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtContextParameterList
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

internal val COMPOSABLE_CLASS_ID: ClassId = ClassId.fromString("androidx/compose/runtime/Composable")

/** Whether the resolved `androidx.compose.runtime.Composable` annotates [this], whatever name it is imported as. */
internal fun KaAnnotated.hasComposableAnnotation(): Boolean = COMPOSABLE_CLASS_ID in annotations

/** The function type that [typeReference] names when it carries `@Composable`, on the type or its nullable form. */
internal fun KaSession.composableFunctionType(typeReference: KtTypeReference): KtFunctionType? {
    val functionType = typeReference.typeElement?.unwrapNullability() as? KtFunctionType ?: return null
    return functionType.takeIf { typeReference.type.hasComposableAnnotation() }
}

/**
 * Returns the callable whose signature holds the outermost type reference that encloses this one, which is this
 * one when none does. A signature holds a reference as its receiver, return type, value or context
 * parameter type, or as a bound or constraint of one of its type parameters. A function literal counts as a
 * callable, so a lambda's parameter type returns the lambda. Returns null for a local property and for any
 * other position, such as a type argument in a body.
 */
internal fun KtTypeReference.signatureOwner(): KtCallableDeclaration? {
    val outermost = parents.filterIsInstance<KtTypeReference>().lastOrNull() ?: this
    val owner =
        when (val parent = outermost.parent) {
            is KtParameter -> {
                parent.ownerFunction ?: (parent.parent as? KtContextParameterList)?.ownerDeclaration
            }

            is KtCallableDeclaration -> {
                parent.takeIf { it.receiverTypeReference == outermost || it.typeReference == outermost }
            }

            is KtTypeParameter, is KtTypeConstraint -> {
                parent.getNonStrictParentOfType<KtTypeParameterListOwner>()
            }

            else -> {
                null
            }
        }
    return (owner as? KtCallableDeclaration)?.takeUnless { it is KtProperty && it.isLocal }
}

internal fun KtCallableDeclaration.isOverrideOrActual(): Boolean =
    hasModifier(KtTokens.OVERRIDE_KEYWORD) || hasModifier(KtTokens.ACTUAL_KEYWORD)

/**
 * Whether the signature that holds [this] is an `override` or `actual`. A `val` or `var` constructor parameter
 * carries its own `override` or `actual` modifier, while [signatureOwner] returns the primary constructor, which
 * has none.
 */
internal fun KtTypeReference.isInOverrideOrActualSignature(): Boolean {
    val outermost = parents.filterIsInstance<KtTypeReference>().lastOrNull() ?: this
    return (outermost.parent as? KtParameter)?.isOverrideOrActual() == true ||
        signatureOwner()?.isOverrideOrActual() == true
}
