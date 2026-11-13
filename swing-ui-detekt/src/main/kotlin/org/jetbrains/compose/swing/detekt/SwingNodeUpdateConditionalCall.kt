package org.jetbrains.compose.swing.detekt

import com.intellij.psi.util.PsiTreeUtil
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Reports a set, update or declare call in the update lambda of SwingNode or MenuNode that a recomposition can skip.
 * Do not put them under a condition, loop, try or lambda; put the condition in the value.
 * The one exemption is the block lambda literal of another positional call.
 *
 * The rule resolves calls and symbols, so it runs only under the type-resolving tasks (`detektMain`, `detektTest`,
 * `detektTestFixtures`). A call or reference that does not resolve is never reported.
 */
public class SwingNodeUpdateConditionalCall(
    config: Config,
) : Rule(config, "Updater calls must run on every recomposition, in the same order"),
    RequiresAnalysisApi {
    override val ruleName: RuleName = RuleName("SwingNodeUpdateConditionalCall")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.mayBeNodeCall()) return
        analyze(expression) { findingsIn(expression) }.forEach(::report)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.findingsIn(node: KtCallExpression): List<Finding> {
        val update = nodeUpdate(node)?.call?.argumentExpression(UPDATE) as? KtLambdaExpression
        val receiver = update?.functionLiteral?.symbol?.receiverParameter
        if (update == null || receiver == null) return emptyList()
        return update
            .collectDescendantsOfType<KtCallExpression>()
            .filter { call -> isConditionalCall(call, update, receiver) }
            .map { call ->
                val name = call.calleeExpression?.text.orEmpty()
                val message =
                    if (isDirectStatement(call, update)) {
                        earlyReturnSkipsCallMessage(name)
                    } else {
                        conditionalCallSkippedMessage(name)
                    }
                Finding(Entity.from(call), message)
            }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isConditionalCall(
        call: KtCallExpression,
        update: KtLambdaExpression,
        receiver: KaSymbol,
    ): Boolean {
        if (!isPositionalCall(call, receiver)) return false
        val ancestors = call.parents.takeWhile { it !== update }.toList()
        val insideBlock =
            ancestors.filterIsInstance<KtCallExpression>().any { parent ->
                val resolved = parent.resolveToCall()?.successfulFunctionCallOrNull()
                val block = resolved?.argumentExpression(BLOCK)
                isPositionalCall(parent, receiver) && block is KtLambdaExpression &&
                    PsiTreeUtil.isAncestor(block, call, true)
            }
        return !insideBlock &&
            (
                !isDirectStatement(call, update) ||
                    update.anyDescendantOfType<KtReturnExpression> {
                        it.startOffset < call.endOffset && it.resolveSymbol() == update.functionLiteral.symbol
                    }
            )
    }

    private fun isDirectStatement(
        call: KtCallExpression,
        update: KtLambdaExpression,
    ): Boolean =
        update.bodyExpression?.statements?.any { statement ->
            val bare = statement.withoutParenthesesAndLabels()
            bare === call || (bare is KtQualifiedExpression && bare.selectorExpression === call)
        } == true

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isPositionalCall(
        call: KtCallExpression,
        receiver: KaSymbol,
    ): Boolean {
        val resolved = call.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val id = resolved.symbol.callableId
        val declare = Name.identifier("declare")
        val mirrorState = ClassId(NODE_PACKAGE_NAME, Name.identifier("MirrorState"))
        return if (resolved.symbol.isUpdaterWrite()) {
            isOnReceiver(resolved, receiver)
        } else {
            id?.packageName == NODE_PACKAGE_NAME && id.classId == null && id.callableName == declare &&
                resolved.symbol.valueParameters.any { parameter ->
                    (parameter.returnType as? KaClassType)?.classId == mirrorState
                } &&
                isExtensionReceiver(resolved.extensionReceiver, receiver)
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isExtensionReceiver(
        value: KaReceiverValue?,
        receiver: KaSymbol,
    ): Boolean =
        when (value) {
            is KaSmartCastedReceiverValue -> {
                isExtensionReceiver(value.original, receiver)
            }

            is KaImplicitReceiverValue -> {
                value.symbol == receiver
            }

            is KaExplicitReceiverValue -> {
                (KtPsiUtil.safeDeparenthesize(value.expression) as? KtThisExpression)?.resolveSymbol() == receiver
            }

            else -> {
                false
            }
        }
}

private fun KtExpression.withoutParenthesesAndLabels(): KtExpression {
    var current = KtPsiUtil.safeDeparenthesize(this)
    while (current is KtLabeledExpression) {
        current = KtPsiUtil.safeDeparenthesize(current.baseExpression ?: return current)
    }
    return current
}

private fun earlyReturnSkipsCallMessage(name: String): String =
    "An early return from `update` can skip this `$name` call. " +
        "When it is skipped, the calls after it can apply values that did not change " +
        "and miss values that did. " +
        "Return from `update` only after its last `set`, `update` or `declare` call, " +
        "or put the condition into the value the call passes."

private fun conditionalCallSkippedMessage(name: String): String =
    "This `$name` call does not run on every recomposition. " +
        "When it is skipped, the calls after it can apply values that did not change " +
        "and miss values that did. " +
        "Make the call a direct statement of `update`, and put the condition or loop " +
        "into the value it passes."
