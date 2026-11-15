// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.modifier

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.psi.isLibraryCompanionValue
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Reports a `SwingModifier` factory whose body never reaches the chain it was given.
 *
 * A factory extension is handed the receiver so a caller's own chain rides through it. A body that
 * restarts the chain from `SwingModifier` itself, rather than from the receiver, returns a chain built
 * from nothing and drops everything the caller had declared.
 *
 * Nothing short of resolving types tells a call that takes the receiver implicitly apart from a plain
 * function call, so a body that delegates that way - `listener(...)`, `binding(...)`, `property(...)`,
 * which is how most factories in this library are written - is passed over. Only a body whose answer is
 * a chain rooted at `SwingModifier` itself, taking the receiver in nowhere along it, is reported: that
 * is the one shape a fresh chain and a dropped receiver can be told apart in without resolving
 * anything.
 */
internal object SwingModifierUnreferencedReceiver : ComposeSwingRule {
    override val id: String = "SwingModifierUnreferencedReceiver"
    override val description: String = "A `SwingModifier` factory should return a chain that includes its receiver"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                reportIfChainIsRestartedFromScratch(function, function.bodyExpression)
            }

            override fun visitProperty(property: KtProperty) {
                if (property.isFactoryCandidate()) {
                    reportIfChainIsRestartedFromScratch(property, property.getter?.bodyExpression)
                }
            }

            private fun reportIfChainIsRestartedFromScratch(
                declaration: KtCallableDeclaration,
                body: KtExpression?,
            ) {
                val restartsTheChain =
                    declaration.receiverTypeReference.declaresSwingModifier() &&
                        declaration.returnsAChain() &&
                        body?.restartsTheChainFromScratch(declaration) == true
                if (!restartsTheChain) return
                reporter.report(declaration, swingModifierUnreferencedReceiverMessage(declaration.name))
            }

            /** Whether this body demonstrably returns a fresh chain without its receiver. */
            private fun KtExpression.restartsTheChainFromScratch(declaration: KtCallableDeclaration): Boolean =
                returnedExpressions().any {
                    (it.chainRoot() as? KtNameReferenceExpression)
                        ?.isLibraryCompanionValue(SWING_MODIFIER_FQN) == true &&
                        !it.reachesTheReceiver(declaration)
                }

            /** A body's own unlabeled `return`s and the values its expression body can answer with. */
            private fun KtExpression.returnedExpressions(): List<KtExpression> =
                if (this is KtBlockExpression) {
                    collectDescendantsOfType<KtReturnExpression> {
                        it.getTargetLabel() == null && it.belongsTo(this)
                    }.mapNotNull { it.returnedExpression }.flatMap { it.terminalResults() }
                } else {
                    terminalResults()
                }

            /** The values terminal Kotlin expression forms can answer with. */
            private fun KtExpression?.terminalResults(): List<KtExpression> =
                when (this) {
                    is KtBlockExpression -> {
                        statements.lastOrNull().terminalResults()
                    }

                    is KtIfExpression -> {
                        getElse()
                            ?.let {
                                listOfNotNull(then, it).flatMap { branch -> branch.terminalResults() }
                            }.orEmpty()
                    }

                    is KtWhenExpression -> {
                        entries.mapNotNull { it.expression }.flatMap { it.terminalResults() }
                    }

                    is KtTryExpression -> {
                        (listOf(tryBlock) + catchClauses.mapNotNull { it.catchBody }).flatMap { it.terminalResults() }
                    }

                    null -> {
                        emptyList()
                    }

                    else -> {
                        listOf(this)
                    }
                }

            /** A return belongs to this body when no nested callable or accessor owns it before the body does. */
            private fun KtReturnExpression.belongsTo(body: KtBlockExpression): Boolean =
                parents.takeWhile { it !== body }.none {
                    it is KtNamedFunction || it is KtPropertyAccessor
                }

            private tailrec fun KtExpression.chainRoot(): KtExpression {
                val left =
                    when (this) {
                        is KtDotQualifiedExpression -> receiverExpression
                        is KtBinaryExpression -> left.takeIf { operationReference.getReferencedName() == "then" }
                        is KtParenthesizedExpression -> expression
                        else -> null
                    }
                return left?.chainRoot() ?: this
            }

            /** Whether this chain contains the factory receiver, including an explicitly labeled one. */
            private fun KtExpression.reachesTheReceiver(declaration: KtCallableDeclaration): Boolean =
                anyDescendantOfType<KtThisExpression> { self ->
                    self.getLabelName() == declaration.name ||
                        (
                            self.getTargetLabel() == null &&
                                self.parents.takeWhile { it !== this }.none { it is KtLambdaExpression }
                        )
                }

            /** Whether this declaration answers with the chain. */
            private fun KtCallableDeclaration.returnsAChain(): Boolean = typeReference.declaresSwingModifier()
        }
}

/**
 * Reports `then` handed a factory call that takes its receiver implicitly, chaining the receiver twice.
 *
 * `then` takes an already-built chain as its argument. A factory called inside it without an explicit
 * receiver chains onto the caller's own receiver a second time: `this.then(factory())` expands to
 * `this.then(this.then(Element))`, and everything the caller had declared before that point appears twice
 * on the resulting chain.
 *
 * Nothing short of resolving types tells such a call apart from a bare function call that carries no
 * receiver at all - the two read alike. Only a call whose callee is declared, elsewhere in the same file,
 * as an extension on `SwingModifier` is reported here.
 */
internal object SwingModifierThen : ComposeSwingRule {
    override val id: String = "SwingModifierThen"
    override val description: String = "`then` should not take a factory call that runs on the receiver implicitly"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                reportChainedTwice(expression.argumentChainedTwiceByThen())
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                reportChainedTwice(expression.argumentChainedTwiceByThen())
            }

            private fun reportChainedTwice(chainedTwice: KtCallExpression?) {
                if (chainedTwice == null) return
                reporter.report(chainedTwice, swingModifierThenMessage(chainedTwice.text))
            }

            /** The bare factory argument when this `then` call demonstrably targets the chain. */
            private fun KtCallExpression.argumentChainedTwiceByThen(): KtCallExpression? =
                valueArguments
                    .singleOrNull()
                    ?.getArgumentExpression()
                    ?.takeIf {
                        val qualifier = parent as? KtQualifiedExpression
                        val hasKnownReceiver =
                            (
                                qualifier == null && hasImplicitSwingModifierReceiver() &&
                                    !isShadowedByLocalCallable("then")
                            ) ||
                                qualifier?.let { qualified ->
                                    qualified.selectorExpression === this &&
                                        qualified.receiverExpression.hasKnownSwingModifierReceiver()
                                } == true
                        calleeExpression?.text == "then" && hasKnownReceiver
                    }?.let { it as? KtCallExpression }
                    ?.takeIf { it.calleeDeclaresSwingModifierReceiver() }

            private fun KtCallExpression.hasImplicitSwingModifierReceiver(): Boolean =
                parents
                    .takeWhile { it !is KtLambdaExpression }
                    .filterIsInstance<KtCallableDeclaration>()
                    .firstOrNull { it is KtNamedFunction || it is KtProperty }
                    ?.receiverTypeReference
                    .declaresSwingModifier()

            private fun KtCallExpression.isShadowedByLocalCallable(name: String): Boolean =
                parents.filterIsInstance<KtBlockExpression>().any { block ->
                    block.statements.any {
                        (it is KtNamedFunction && it.isLocal && it.name == name) ||
                            (it is KtProperty && it.isLocal && it.name == name && it.textOffset < textOffset)
                    }
                }

            private fun KtBinaryExpression.argumentChainedTwiceByThen(): KtCallExpression? =
                (right as? KtCallExpression)
                    ?.takeIf { operationReference.getReferencedName() == "then" }
                    ?.takeIf { left?.hasKnownSwingModifierReceiver() == true }
                    ?.takeIf { it.calleeDeclaresSwingModifierReceiver() }

            private fun KtExpression.hasKnownSwingModifierReceiver(): Boolean =
                (this as? KtThisExpression)?.let { self ->
                    val label = self.getLabelName()
                    val scope =
                        if (label == null) self.parents.takeWhile { it !is KtLambdaExpression } else self.parents
                    scope
                        .filterIsInstance<KtCallableDeclaration>()
                        .firstOrNull {
                            (it is KtNamedFunction || it is KtProperty) && (label == null || it.name == label)
                        }?.receiverTypeReference
                        .declaresSwingModifier()
                } == true

            private fun KtCallExpression.calleeDeclaresSwingModifierReceiver(): Boolean {
                val name = calleeExpression?.text
                val file = containingFile as? KtFile
                val shadowsTopLevelFactory = name != null && isShadowedByLocalCallable(name)
                if (name == null || shadowsTopLevelFactory) return false
                val candidates =
                    file
                        ?.declarations
                        ?.filterIsInstance<KtNamedFunction>()
                        ?.filter { it.name == name }
                        .orEmpty()
                return candidates.singleOrNull()?.receiverTypeReference.declaresSwingModifier()
            }
        }
}

private fun swingModifierUnreferencedReceiverMessage(name: String?): String =
    "A return of the `$name` factory starts a new chain from `$SWING_MODIFIER` " +
        "and leaves out the receiver. The receiver holds the modifiers the caller already " +
        "applied, so this return drops them. Build the returned chain on `this`, or call " +
        "another factory on the receiver."

private fun swingModifierThenMessage(callText: String): String =
    "The `$callText` call inside `then` runs on the receiver implicitly. " +
        "A factory returns its receiver's modifiers along with its own, so `then` adds " +
        "the receiver's modifiers a second time. Call the factory on the receiver itself, " +
        "or on `$SWING_MODIFIER` inside `then`."
