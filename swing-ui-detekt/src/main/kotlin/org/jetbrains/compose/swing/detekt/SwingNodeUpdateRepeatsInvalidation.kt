package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

/**
 * Reports a call of `revalidate`, `invalidate` or `repaint` that repeats what the property writes next to it
 * already request.
 *
 * The call is reported when it is a statement of a `set` or `update` block inside the `update` lambda of a
 * `SwingNode` or `MenuNode` call, on the component, implicit or `this`, in any overload. Every statement of the
 * block that is not an invalidation call must be a write to a property of the component, by assignment or by a call
 * of the setter such as `setFont(x)`, whose JDK setter requests the same thing whenever the value changes:
 * `revalidate` or `invalidate` after setters that call `revalidate`, `repaint` after setters that call `repaint`.
 * A block may hold several invalidation calls, and each one that the writes already request is reported. A local
 * `val` or `var` declaration in the block is skipped unless its initializer calls the component or names it as
 * `this`, which makes it a statement that is not a write.
 * The setters the rule knows are `JComponent.setFont`, `setBorder`, `setForeground` and `setBackground`,
 * `JLabel.setText`, `setIcon` and `setHorizontalAlignment`, and `AbstractButton.setText`, `setIcon` and
 * `setHorizontalAlignment`. `setBorder` and the two `setIcon` setters request a layout only when the border insets or
 * the icon size change, so for them the rule knows only the repaint.
 *
 * A block with any other statement, such as a call on another receiver, is never reported. A block with a write
 * outside the table is never reported, as with `JTextField.setColumns`, which only invalidates and is meant to be
 * followed by a revalidate. An invalidation call that asks for more than the writes request is not reported. An
 * invalidation outside an updater block is never reported, and neither are `validate` and `doLayout`.
 *
 * The call adds a second request on every change and suggests that the component's setter does not make the
 * request when it does.
 *
 * The rule resolves calls and symbols, so it runs only under the type-resolving tasks (`detektMain`,
 * `detektTest`, `detektTestFixtures`). A call or write that does not resolve is never reported.
 */
public class SwingNodeUpdateRepeatsInvalidation(
    config: Config,
) : Rule(
        config,
        "An updater block should not repeat the layout or repaint request its setters make",
    ),
    RequiresAnalysisApi {
    override val ruleName: RuleName = RuleName("SwingNodeUpdateRepeatsInvalidation")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.mayBeNodeCall()) return
        analyze(expression) { findingsIn(expression) }.forEach(::report)
    }

    private fun KaSession.findingsIn(call: KtCallExpression): List<Finding> =
        nodeUpdate(call)?.blocks.orEmpty().flatMap { findingsIn(it.block, it.receiver) }

    private fun KaSession.findingsIn(
        block: KtLambdaExpression,
        receiver: KaSymbol,
    ): List<Finding> {
        val statements =
            block.bodyExpression
                ?.statements
                .orEmpty()
                .map { KtPsiUtil.safeDeparenthesize(it) }
                .filterNot { it is KtProperty && !usesReceiver(it, receiver) }
        val invalidations =
            statements.mapNotNull { statement -> invalidationOf(statement, receiver)?.let { statement to it } }
        val writes =
            statements
                .filter { statement -> invalidations.none { it.first === statement } }
                .map { writeOf(it, receiver) }
        val repeated = writes.filterNotNull().takeIf { it.isNotEmpty() && it.size == writes.size }
        if (repeated == null) return emptyList()
        return invalidations
            .filter { (_, request) -> repeated.all { request in it.requests } }
            .map { (statement, _) ->
                Finding(
                    entity = Entity.from(statement),
                    message = repeatedInvalidationMessage(statement, repeated.map { it.setter }),
                )
            }
    }

    /** Whether the initializer of [property] calls the component or names it as `this`. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.usesReceiver(
        property: KtProperty,
        receiver: KaSymbol,
    ): Boolean {
        val initializer = property.initializer ?: return false
        return initializer.collectDescendantsOfType<KtThisExpression> { it.resolveSymbol() == receiver }.isNotEmpty() ||
            initializer.collectDescendantsOfType<KtCallExpression>().any {
                val call = it.resolveToCall()?.successfulFunctionCallOrNull()
                isOnUpdater(call, receiver)
            }
    }

    /** The request [statement] makes when it calls `revalidate`, `invalidate` or `repaint` on [receiver]. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.invalidationOf(
        statement: KtExpression,
        receiver: KaSymbol,
    ): InvalidationRequest? =
        statement
            .getPossiblyQualifiedCallExpression()
            ?.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.takeIf { call -> isOnReceiver(call, receiver) }
            ?.symbol
            ?.callableId
            ?.takeIf { id -> id.classId?.packageFqName in setOf(FqName("java.awt"), FqName("javax.swing")) }
            ?.let { id ->
                when (id.callableName.asString()) {
                    "revalidate", "invalidate" -> InvalidationRequest.LAYOUT
                    "repaint" -> InvalidationRequest.REPAINT
                    else -> null
                }
            }

    /**
     * The write [statement] makes to a property of [receiver] whose setter is in the table, by assignment or by a
     * call of the setter, or null.
     */
    private fun KaSession.writeOf(
        statement: KtExpression,
        receiver: KaSymbol,
    ): SetterWrite? = assignmentOf(statement, receiver) ?: setterCallOf(statement, receiver)

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.setterCallOf(
        statement: KtExpression,
        receiver: KaSymbol,
    ): SetterWrite? {
        val call =
            statement
                .getPossiblyQualifiedCallExpression()
                ?.resolveToCall()
                ?.successfulFunctionCallOrNull()
                ?.takeIf { isOnReceiver(it, receiver) }
        val setter = call?.symbol?.callableId
        val requests = setter?.let { SETTERS[it] }
        return if (setter != null && requests != null) {
            SetterWrite(setter, requests)
        } else {
            null
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.assignmentOf(
        statement: KtExpression,
        receiver: KaSymbol,
    ): SetterWrite? {
        val target =
            (statement as? KtBinaryExpression)
                ?.takeIf { it.operationToken == KtTokens.EQ }
                ?.left
                ?.let { KtPsiUtil.safeDeparenthesize(it) }
        val access = target?.resolveToCall()?.successfulVariableAccessCall()
        val setter = (access?.symbol as? KaSyntheticJavaPropertySymbol)?.javaSetterSymbol?.callableId
        val requests = setter?.let { SETTERS[it] }
        return if (setter != null && requests != null) {
            SetterWrite(setter, requests).takeIf { isOnReceiver(access, receiver) }
        } else {
            null
        }
    }

    private class SetterWrite(
        val setter: CallableId,
        val requests: Set<InvalidationRequest>,
    )

    private enum class InvalidationRequest { LAYOUT, REPAINT }

    private companion object {
        private fun setter(
            owner: String,
            name: String,
            vararg requests: InvalidationRequest,
        ): Pair<CallableId, Set<InvalidationRequest>> =
            CallableId(ClassId(FqName("javax.swing"), Name.identifier(owner)), Name.identifier(name)) to
                requests.toSet()

        private val SETTERS: Map<CallableId, Set<InvalidationRequest>> =
            mapOf(
                setter("JComponent", "setFont", InvalidationRequest.LAYOUT, InvalidationRequest.REPAINT),
                setter("JComponent", "setBorder", InvalidationRequest.REPAINT),
                setter("JComponent", "setForeground", InvalidationRequest.REPAINT),
                setter("JComponent", "setBackground", InvalidationRequest.REPAINT),
                setter("JLabel", "setText", InvalidationRequest.LAYOUT, InvalidationRequest.REPAINT),
                setter("JLabel", "setIcon", InvalidationRequest.REPAINT),
                setter("JLabel", "setHorizontalAlignment", InvalidationRequest.REPAINT),
                setter("AbstractButton", "setText", InvalidationRequest.LAYOUT, InvalidationRequest.REPAINT),
                setter("AbstractButton", "setIcon", InvalidationRequest.REPAINT),
                setter("AbstractButton", "setHorizontalAlignment", InvalidationRequest.REPAINT),
            )
    }
}

private fun List<String>.joinedWithAnd(): String =
    if (size < 2) joinToString() else dropLast(1).joinToString() + " and " + last()

private fun repeatedInvalidationMessage(
    statement: KtExpression,
    setters: List<CallableId>,
): String {
    val call =
        statement
            .getPossiblyQualifiedCallExpression()
            ?.calleeExpression
            ?.text
            .orEmpty()
    val kind = if (call == "repaint") "repaint" else "layout"
    val named = setters.map { "`${it.classId?.shortClassName}.${it.callableName}`" }.distinct()
    return "This `$call` call repeats the $kind request already made by ${named.joinedWithAnd()}. " +
        "It adds a second $kind on every change. Remove the call."
}
