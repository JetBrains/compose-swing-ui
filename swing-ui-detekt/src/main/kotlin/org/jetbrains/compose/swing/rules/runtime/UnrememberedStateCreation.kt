// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a state object created during composition without `remember`: a call to a `@StateFactoryMarker`
 * function, a constructor of a `State` type, or an object literal that implements `State`.
 */
internal class UnrememberedStateCreation(
    private val reporter: ComposeSwingRuleReporter,
) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        val factory =
            analyze(expression) {
                val symbol =
                    expression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return@analyze null
                when {
                    StateFactoryMarker in symbol.annotations -> {
                        (symbol as? KaNamedFunctionSymbol)?.name?.asString() ?: expression.calleeExpression?.text
                    }

                    symbol is KaConstructorSymbol && symbol.returnType.isSubtypeOf(Names.Runtime.State.classId) -> {
                        expression.calleeExpression?.text
                    }

                    else -> {
                        null
                    }
                }
            }
        if (factory != null && with(RememberInComposition) { expression.isUnrememberedCall() }) {
            reporter.report(expression, stateFactoryMessage(factory))
        }
    }

    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        val declaration = expression.objectDeclaration
        val implementsState =
            analyze(expression) {
                declaration.symbol.superTypes.any { it.isSubtypeOf(Names.Runtime.State.classId) }
            }
        if (implementsState && with(RememberInComposition) { expression.isUnrememberedCall() }) {
            reporter.report(declaration.getObjectKeyword() ?: expression, STATE_CREATION_MESSAGE)
        }
    }
}

private val StateFactoryMarker =
    ClassId(FqName("androidx.compose.runtime.snapshots"), Name.identifier("StateFactoryMarker"))

private const val STATE_CREATION_MESSAGE =
    "This state object is created during composition without being remembered. Recomposition can replace it and lose " +
        "its current value. Create it inside `remember`."

private fun stateFactoryMessage(factory: String): String =
    "This call to `$factory` creates state during composition without remembering it. Recomposition can replace " +
        "the state and lose its current value. Call `$factory` inside `remember`."
