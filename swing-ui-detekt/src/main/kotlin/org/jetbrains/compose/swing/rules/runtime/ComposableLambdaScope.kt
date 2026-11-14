// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Returns whether the body of this non-inlined lambda runs during composition.
 *
 * A lambda is composable when it carries `@Composable` itself, when the parameter it is passed to has a
 * composable function type, or when it is the initializer of a property declared with a composable type.
 * A lambda nested deeper inside such a property's initializer takes nothing from the property's type.
 */
internal fun KaSession.isComposableLambda(lambda: KtLambdaExpression): Boolean {
    val composable = Names.Runtime.Composable.classId
    val annotated = lambda.parent as? KtAnnotatedExpression
    val explicitlyComposable =
        annotated?.annotationEntries?.any { it.typeReference?.type?.isClassType(composable) == true } == true
    val call =
        lambda.parents
            .takeWhile { it !is KtDeclaration && it !is KtLambdaExpression }
            .filterIsInstance<KtCallExpression>()
            .firstOrNull()
            ?.resolveToCall()
            ?.successfulFunctionCallOrNull()
    val parameter = call?.valueArgumentMapping?.get(lambda)?.symbol
    val initialized = annotated ?: lambda
    val property = (initialized.parent as? KtProperty)?.takeIf { it.initializer == initialized }
    return when {
        explicitlyComposable -> {
            true
        }

        parameter != null -> {
            composable in parameter.returnType.annotations
        }

        else -> {
            property
                ?.typeReference
                ?.type
                ?.annotations
                ?.contains(composable) == true ||
                lambda.expressionType?.annotations?.contains(composable) == true ||
                composable in lambda.functionLiteral.symbol.annotations
        }
    }
}
