// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.node

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.core.RuleFinding
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Reports a `SwingNode` or `MenuNode` factory lambda that hands back a component declared outside it.
 *
 * A node calls its factory afresh each time it is created, and builds a new node after one was released
 * for good. A factory that answers with a component it did not build gives every such node the same
 * instance, which the node it replaced may still be driving. Build the component inside the factory.
 *
 * Only a lambda literal passed as the factory is checked, and only what it hands back when that is a
 * reference: a simple name, `this` or `this@label`, or a property access on one of those, once parentheses,
 * `!!`, `as` and `as?` are removed. A property qualified by a package, such as `sample.panel`, is judged like the
 * simple name. The last expression, every branch of an `if`, `when`, `try` or `?:` that
 * ends it, and every labeled `return` from the lambda are searched. A name is reported when it resolves to a
 * declaration outside the factory lambda, and a `this` when the receiver it binds to belongs to a declaration
 * outside it. A call is never reported, a custom factory function included: telling what one hands back takes
 * more than the syntax shows. A factory passed as anything but a lambda literal is not checked.
 *
 * The rule resolves calls and symbols, so it runs only under the type-resolving tasks (`detektMain`,
 * `detektTest`, `detektTestFixtures`). A call or reference that does not resolve is never reported.
 */
internal object SwingNodeFactoryReusesComponent : ComposeSwingRule {
    override val id: String = "SwingNodeFactoryReusesComponent"
    override val description: String = "A `SwingNode` factory should create its component, not return an existing one"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                if (!expression.mayBeNodeCall()) return
                analyze(expression) { findingsIn(expression) }.forEach { reporter.report(it.entity, it.message) }
            }
        }

    private fun KaSession.findingsIn(call: KtCallExpression): List<RuleFinding> {
        val (_, factory) = nodeCallWithFactory(call) ?: return emptyList()
        return handedBack(factory)
            .filter { isDeclaredOutside(it, factory) }
            .map { RuleFinding(entity = it, message = reusesComponentMessage()) }
    }

    /** The expressions [factory] answers with: where it ends and each `return` to it by label. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.handedBack(factory: KtLambdaExpression): List<KtExpression> {
        val returned =
            factory
                .collectDescendantsOfType<KtReturnExpression> { it.resolveSymbol() == factory.functionLiteral.symbol }
                .mapNotNull { it.returnedExpression }
        return (listOfNotNull(factory.bodyExpression?.statements?.lastOrNull()) + returned).flatMap { it.results() }
    }

    /** Whether [receiver], once parentheses are removed, is a package name such as `sample` or `org.sample`. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.namesPackage(receiver: KtExpression): Boolean {
        val deparenthesized = KtPsiUtil.deparenthesize(receiver)
        val name = (deparenthesized as? KtQualifiedExpression)?.selectorExpression ?: deparenthesized
        val symbols = (name as? KtNameReferenceExpression)?.mainReference?.resolveToSymbols()
        return symbols?.singleOrNull() is KaPackageSymbol
    }

    /** Whether [expression] names something declared outside [factory], seen through parentheses, `!!` and casts. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isDeclaredOutside(
        expression: KtExpression,
        factory: KtLambdaExpression,
    ): Boolean =
        when (val deparenthesized = KtPsiUtil.deparenthesize(expression)) {
            is KtPostfixExpression -> {
                deparenthesized.operationToken == KtTokens.EXCLEXCL &&
                    deparenthesized.baseExpression?.let { isDeclaredOutside(it, factory) } == true
            }

            is KtBinaryExpressionWithTypeRHS -> {
                isDeclaredOutside(deparenthesized.left, factory)
            }

            is KtThisExpression -> {
                deparenthesized.resolveSymbol()?.declaredOutside(factory) == true
            }

            is KtNameReferenceExpression -> {
                deparenthesized.resolveSymbol()?.declaredOutside(factory) == true
            }

            is KtQualifiedExpression -> {
                val selector = deparenthesized.selectorExpression as? KtNameReferenceExpression
                val receiver = deparenthesized.receiverExpression
                when {
                    selector == null -> false
                    namesPackage(receiver) -> selector.resolveSymbol()?.declaredOutside(factory) == true
                    else -> isDeclaredOutside(receiver, factory)
                }
            }

            else -> {
                false
            }
        }
}

/** Whether the declaration of [this] symbol, or of the callable that owns a receiver, lies outside [factory]. */
private fun KaSymbol.declaredOutside(factory: KtLambdaExpression): Boolean {
    val declaration: PsiElement? = (this as? KaReceiverParameterSymbol)?.owningCallableSymbol?.psi ?: psi
    return declaration != null && !PsiTreeUtil.isAncestor(factory, declaration, false)
}

/** The expressions an expression may evaluate to: its branches where it chooses, itself otherwise. */
private fun KtExpression.results(): List<KtExpression> =
    when (val expression = KtPsiUtil.deparenthesize(this)) {
        is KtPostfixExpression -> {
            if (expression.operationToken == KtTokens.EXCLEXCL) {
                expression.baseExpression.resultsThrough(expression)
            } else {
                listOf(expression)
            }
        }

        is KtBinaryExpressionWithTypeRHS -> {
            expression.left.resultsThrough(expression)
        }

        is KtBlockExpression -> {
            listOfNotNull(expression.statements.lastOrNull()).flatMap { it.results() }
        }

        is KtIfExpression -> {
            listOfNotNull(expression.then, expression.`else`).flatMap { it.results() }
        }

        is KtWhenExpression -> {
            expression.entries.mapNotNull { it.expression }.flatMap { it.results() }
        }

        is KtTryExpression -> {
            (listOf(expression.tryBlock) + expression.catchClauses.mapNotNull(KtCatchClause::getCatchBody))
                .flatMap { it.results() }
        }

        is KtBinaryExpression -> {
            if (expression.operationToken == KtTokens.ELVIS) {
                listOfNotNull(expression.left, expression.right).flatMap { it.results() }
            } else {
                listOf(expression)
            }
        }

        else -> {
            listOfNotNull(expression)
        }
    }

/**
 * The results of [this] operand, seen through [wrapper], a `!!` or a cast. An operand that is a single value
 * keeps [wrapper] as its result, so the finding covers the expression as written.
 */
private fun KtExpression?.resultsThrough(wrapper: KtExpression): List<KtExpression> {
    val operand = this?.let { KtPsiUtil.deparenthesize(it) } ?: return listOf(wrapper)
    val results = operand.results()
    return if (results.singleOrNull() === operand) listOf(wrapper) else results
}

private fun reusesComponentMessage(): String =
    "This `factory` returns a component it did not build. A node that is released and rebuilt calls " +
        "`factory` again, so the new node gets the same component another node still holds. Build the " +
        "component inside `factory`. To configure a part that its parent built and keeps, use " +
        "`ExistingSwingNode`."
