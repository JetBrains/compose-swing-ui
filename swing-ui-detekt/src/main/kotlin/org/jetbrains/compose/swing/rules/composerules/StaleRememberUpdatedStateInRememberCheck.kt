// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.composerules

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents

/** Runs [StaleRememberUpdatedStateInRememberCheck] as a [ComposeSwingRule]. */
internal object StaleRememberUpdatedStateInRemember : ComposeSwingRule {
    override val id: String = "StaleRememberUpdatedStateInRemember"
    override val description: String = "rememberUpdatedState values should not be read eagerly in remember initializers"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = StaleRememberUpdatedStateInRememberCheck(reporter)
}

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.

/** Reports `rememberUpdatedState` values that a `remember` initializer reads eagerly and so captures stale. */
@Suppress("ReturnCount", "TooManyFunctions")
internal class StaleRememberUpdatedStateInRememberCheck(
    private val reporter: ComposeSwingRuleReporter,
) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        val calculation = expression.calculationLambda()?.bodyExpression ?: return
        if (!expression.isInsideComposableScope()) return
        val cacheFunction = expression.memoizingComposableName() ?: return
        reportStaleReads(expression, calculation, cacheFunction)
    }

    private fun KtProperty.toRememberUpdatedStateProperty(): RememberUpdatedStateProperty? {
        if (!isInsideComposableScope()) return null
        val readKind =
            if (hasDelegate()) {
                RememberUpdatedStateReadKind.DelegatedValue
            } else {
                RememberUpdatedStateReadKind.StateValue
            }
        val rememberUpdatedStateCall = (delegateExpression ?: initializer)?.rememberUpdatedStateCall() ?: return null
        if (!rememberUpdatedStateCall.isRememberUpdatedStateCall()) return null

        return RememberUpdatedStateProperty(
            property = this,
            readKind = readKind,
            sourceExpressions = rememberUpdatedStateCall.argumentExpressions(),
        )
    }

    private fun reportStaleReads(
        call: KtCallExpression,
        calculation: KtExpression,
        cacheFunction: String,
    ) {
        val keyExpressions by lazy(LazyThreadSafetyMode.NONE) { call.keyExpressions() }
        calculation.accept(
            object : KtTreeVisitorVoid() {
                override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                    if (lambdaExpression.isEagerScopeFunctionLambda()) {
                        super.visitLambdaExpression(lambdaExpression)
                    }
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    // Local functions defer reads until invocation.
                }

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                    // Property getters defer reads until invocation.
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    if (expression.isPropertyDeclarationName()) return
                    val property =
                        expression
                            .resolvedProperty()
                            ?.takeIf { it.name == expression.getReferencedName() && it.isInScopeBefore(call) }
                            ?.toRememberUpdatedStateProperty() ?: return
                    if (property.isStaleRead(expression) && !property.isKeyedBy(keyExpressions)) {
                        reporter.report(expression, staleReadMessage(cacheFunction))
                    }
                }
            },
        )
    }

    private fun KtSimpleNameExpression.resolvedProperty(): KtProperty? =
        runCatching {
            analyze(this) {
                mainReference.resolveToSymbol()?.sourcePsiSafe<KtProperty>()
                    ?: (resolveToCall() as? KaVariableAccessCall)?.signature?.symbol?.sourcePsiSafe<KtProperty>()
            }
        }.getOrNull()

    private fun KtProperty.isInScopeBefore(call: KtCallExpression): Boolean {
        if (containingFile != call.containingFile || textRange.startOffset >= call.textRange.startOffset) return false
        val scope =
            parents.firstOrNull { it is KtNamedFunction || it is KtPropertyAccessor || it is KtLambdaExpression }
        return scope != null && PsiTreeUtil.isAncestor(scope, call, true) &&
            parents.filterIsInstance<KtCallExpression>().none {
                it.isInsideComposableScope() && it.memoizingComposableName() != null
            }
    }

    private fun KtCallExpression.calculationLambda(): KtLambdaExpression? =
        valueArguments
            .firstOrNull { argument -> argument.argumentName().isInitializerArgumentName() }
            ?.getArgumentExpression() as? KtLambdaExpression
            ?: lambdaArguments.lastOrNull()?.getLambdaExpression()
            ?: valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression

    private fun RememberUpdatedStateProperty.isKeyedBy(keyExpressions: List<KtExpression>): Boolean =
        keyExpressions.any { keyExpression ->
            when (readKind) {
                RememberUpdatedStateReadKind.DelegatedValue -> {
                    (keyExpression as? KtSimpleNameExpression)?.isResolvedReadOf(property) == true
                }

                RememberUpdatedStateReadKind.StateValue -> {
                    keyExpression.isStateValueReadOf(property)
                }
            } ||
                sourceExpressions.any { sourceExpression -> keyExpression.isSameResolvedValueAs(sourceExpression) }
        }

    private fun RememberUpdatedStateProperty.isStaleRead(expression: KtSimpleNameExpression): Boolean =
        when (readKind) {
            RememberUpdatedStateReadKind.DelegatedValue -> true
            RememberUpdatedStateReadKind.StateValue -> expression.isStateValueReceiver()
        }

    private fun KtCallExpression.keyExpressions(): List<KtExpression> =
        if (isResolvedCallToAnyOf(setOf(ComposeFqNames.RememberSaveable))) {
            rememberSaveableKeyExpressions()
        } else {
            rememberOrRetainKeyExpressions()
        }

    private fun KtCallExpression.rememberOrRetainKeyExpressions(): List<KtExpression> =
        valueArguments.flatMap { argument ->
            val expression = argument.getArgumentExpression() ?: return@flatMap emptyList()
            if (expression is KtLambdaExpression || argument.argumentName() == "calculation") {
                emptyList()
            } else {
                argument.flattenedKeyExpressions()
            }
        }

    private fun KtCallExpression.rememberSaveableKeyExpressions(): List<KtExpression> =
        valueArguments.flatMap { argument ->
            val expression = argument.getArgumentExpression() ?: return@flatMap emptyList()
            if (expression is KtLambdaExpression) return@flatMap emptyList()

            when (argument.argumentName()) {
                null, "inputs" -> argument.flattenedKeyExpressions()
                else -> emptyList()
            }
        }

    private fun KtValueArgument.flattenedKeyExpressions(): List<KtExpression> {
        val expression = getArgumentExpression() ?: return emptyList()
        return expression.flattenKeyExpression(
            containingCall = containingCallExpression(),
            shouldFlattenArrayInitializer = getSpreadElement() != null || argumentName().isArrayContainerArgumentName(),
        )
    }

    private fun KtValueArgument.containingCallExpression(): KtCallExpression? =
        generateSequence(parent) { element ->
            element.parent
        }.filterIsInstance<KtCallExpression>().firstOrNull()

    private fun KtExpression.flattenKeyExpression(
        containingCall: KtCallExpression? = null,
        shouldFlattenArrayInitializer: Boolean = false,
    ): List<KtExpression> =
        when {
            this is KtCallExpression && isArrayOfCall() -> {
                argumentExpressions()
            }

            shouldFlattenArrayInitializer && this is KtSimpleNameExpression -> {
                resolvedPropertyInitializer()
                    ?.takeUnless { containingCall != null && isMutatedBefore(containingCall) }
                    ?.flattenKeyExpression(containingCall = containingCall, shouldFlattenArrayInitializer = true)
                    ?: listOf(this)
            }

            else -> {
                listOf(this)
            }
        }

    private fun KtSimpleNameExpression.isMutatedBefore(containingCall: KtCallExpression): Boolean {
        val property = resolvedLocalImmutableProperty() ?: return true
        val scanRoot = property.parent ?: return true
        var isMutated = false
        scanRoot.accept(
            object : KtTreeVisitorVoid() {
                override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                    if (lambdaExpression.isEagerScopeFunctionLambda()) {
                        super.visitLambdaExpression(lambdaExpression)
                    }
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    // Local functions defer writes until invocation.
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    if (expression.textOffset >= containingCall.textOffset) return
                    if (expression.isSetCallOn(property)) {
                        isMutated = true
                        return
                    }
                    super.visitCallExpression(expression)
                }

                override fun visitBinaryExpression(expression: KtBinaryExpression) {
                    if (expression.textOffset >= containingCall.textOffset) return
                    if (expression.operationToken == KtTokens.EQ &&
                        (expression.left as? KtArrayAccessExpression)
                            ?.arrayExpression
                            ?.let { arrayExpression -> arrayExpression as? KtSimpleNameExpression }
                            ?.isReadOfArrayAlias(property) == true
                    ) {
                        isMutated = true
                        return
                    }
                    super.visitBinaryExpression(expression)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    if (expression.textOffset >= containingCall.textOffset) return
                    if ((expression.receiverExpression as? KtSimpleNameExpression)?.isReadOfArrayAlias(
                            property,
                        ) == true &&
                        (expression.selectorExpression as? KtCallExpression)?.calleeExpression?.text == "set"
                    ) {
                        isMutated = true
                        return
                    }
                    super.visitDotQualifiedExpression(expression)
                }
            },
        )

        return isMutated
    }

    private fun KtCallExpression.isSetCallOn(property: KtProperty): Boolean =
        calleeExpression?.text == "set" &&
            (parent as? KtDotQualifiedExpression)
                ?.receiverExpression
                ?.let { receiver -> receiver as? KtSimpleNameExpression }
                ?.isReadOfArrayAlias(property) == true

    private fun KtSimpleNameExpression.isReadOfArrayAlias(property: KtProperty): Boolean = isResolvedReadOf(property)

    private fun KtCallExpression.isArrayOfCall(): Boolean = calleeExpression?.text == "arrayOf"

    private fun KtCallExpression.argumentExpressions(): List<KtExpression> =
        valueArguments
            .mapNotNull { argument -> argument.getArgumentExpression() }

    private fun KtExpression.rememberUpdatedStateCall(): KtCallExpression? =
        when (this) {
            is KtCallExpression -> this
            is KtDotQualifiedExpression -> selectorExpression as? KtCallExpression
            else -> null
        }

    private fun KtValueArgument.argumentName(): String? = getArgumentName()?.asName?.asString()

    private fun String?.isInitializerArgumentName(): Boolean = this == "calculation" || this == "init"

    private fun String?.isArrayContainerArgumentName(): Boolean = this == "keys" || this == "inputs"

    private fun KtSimpleNameExpression.isPropertyDeclarationName(): Boolean =
        (parent as? KtProperty)?.nameIdentifier == this

    private fun KtSimpleNameExpression.isStateValueReceiver(): Boolean =
        (parent as? KtDotQualifiedExpression)?.isStateValueReadOf(this) == true

    private fun KtExpression.isStateValueReadOf(property: KtProperty): Boolean =
        (this as? KtDotQualifiedExpression)?.let { qualifiedExpression ->
            (qualifiedExpression.receiverExpression as? KtSimpleNameExpression)?.isResolvedReadOf(property) == true &&
                qualifiedExpression.selectorExpression?.text == "value"
        } == true

    private fun KtDotQualifiedExpression.isStateValueReadOf(receiver: KtSimpleNameExpression): Boolean =
        receiverExpression == receiver && selectorExpression?.text == "value"
}

private data class RememberUpdatedStateProperty(
    val property: KtProperty,
    val readKind: RememberUpdatedStateReadKind,
    val sourceExpressions: List<KtExpression>,
)

private enum class RememberUpdatedStateReadKind {
    DelegatedValue,
    StateValue,
}

private fun staleReadMessage(cacheFunction: String): String {
    val argument = if (cacheFunction == "rememberSaveable") "an input" else "a key"
    return "This code reads a `rememberUpdatedState` value directly inside `$cacheFunction`. " +
        "The cached result can keep the value read when it was created even after `rememberUpdatedState` receives " +
        "a new value. Add the source value as $argument to recalculate the result when it changes, " +
        "or defer the read in a lambda to get the latest value when it is used."
}
