/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.rules.runtime

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

internal fun KtExpression.isInvokedWithinComposable(): Boolean =
    analyze(this) {
        compositionBoundary(this@isInvokedWithinComposable)?.let { isComposableBoundary(it) } ==
            true
    }

internal fun KaSession.compositionBoundary(expression: KtExpression): KtExpression? =
    expression.parents.firstOrNull { isBoundaryCandidate(it) }.let { parent ->
        when (parent) {
            is KtLambdaExpression -> parent
            is KtNamedFunction -> parent
            is KtPropertyAccessor -> parent
            else -> null
        }
    }

private fun KaSession.isBoundaryCandidate(parent: PsiElement): Boolean =
    when (parent) {
        is KtLambdaExpression -> {
            !isInlined(parent)
        }

        is KtNamedFunction -> {
            parent.name != null || !isInlined(parent)
        }

        is KtClassInitializer -> {
            parent.isOutsideObjectLiteral()
        }

        is KtProperty -> {
            !parent.isLocal && parent.isOutsideObjectLiteral()
        }

        is KtObjectDeclaration -> {
            !parent.isObjectLiteral()
        }

        is KtDeclaration -> {
            parent !is org.jetbrains.kotlin.psi.KtFunctionLiteral
        }

        else -> {
            false
        }
    }

private fun KtDeclaration.isOutsideObjectLiteral(): Boolean =
    getStrictParentOfType<KtObjectDeclaration>()?.isObjectLiteral() != true

private fun KtExpression.enclosingCall(): KtCallExpression? =
    parents
        .takeWhile { it !is KtDeclaration && it !is KtLambdaExpression }
        .filterIsInstance<KtCallExpression>()
        .firstOrNull()

private fun KaSession.isComposableBoundary(boundary: KtExpression): Boolean =
    when (boundary) {
        is KtPropertyAccessor -> {
            Names.Runtime.Composable.classId in boundary.symbol.annotations
        }

        is KtNamedFunction -> {
            Names.Runtime.Composable.classId in boundary.symbol.annotations ||
                (boundary.parent as? KtProperty)
                    ?.takeIf { it.initializer == boundary }
                    ?.typeReference
                    ?.type
                    ?.annotations
                    ?.contains(Names.Runtime.Composable.classId) == true ||
                (boundary.name == null && isComposableArgument(boundary))
        }

        is KtLambdaExpression -> {
            isComposableLambda(boundary)
        }

        else -> {
            false
        }
    }

private fun KaSession.isComposableArgument(argument: KtExpression): Boolean {
    val parameter =
        argument
            .enclosingCall()
            ?.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.valueArgumentMapping
            ?.get(argument)
            ?.symbol
    return parameter != null && Names.Runtime.Composable.classId in parameter.returnType.annotations
}

private fun KaSession.isInlined(argument: KtExpression): Boolean {
    val resolved = argument.enclosingCall()?.resolveToCall()?.successfulFunctionCallOrNull()
    val function = resolved?.symbol as? KaNamedFunctionSymbol
    val parameter = resolved?.valueArgumentMapping?.get(argument)?.symbol
    return function != null && parameter != null && function.isInline &&
        Names.Runtime.Composable.classId !in function.annotations && !parameter.isNoinline && !parameter.isCrossinline
}
