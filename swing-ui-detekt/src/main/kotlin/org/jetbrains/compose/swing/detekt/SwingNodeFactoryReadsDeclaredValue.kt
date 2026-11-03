package org.jetbrains.compose.swing.detekt

import com.intellij.psi.PsiElement
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
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Reports a `SwingNode` or `MenuNode` factory that reads a value its composable passes in.
 *
 * A node runs its factory once, when it is created, so a value the factory reads reaches the component only
 * then. Reported are the references in the factory lambda that resolve to a value parameter of the enclosing
 * named `@Composable` function, or to a value parameter (explicit or the implicit `it`) of a lambda around the
 * node inside that function. Each parameter is reported once per factory, at its first reference.
 *
 * Not reported are locals, whether remembered, delegated or plain: the composition declares a local again
 * with each pass, and the rule cannot tell which of them the node keeps. A class property, a member of an
 * anonymous object and a value the factory declares itself are not reported either.
 * A function-typed parameter of the composable that the factory invokes itself is not reported, whether it creates the
 * component or not. An invocation inside a function, lambda or accessor that the factory declares is reported, since
 * it runs later.
 *
 * Exempt are the parameters that `update` applies: one that a value argument of a `set` or `update` call reads,
 * or that the block of a `reconcile` or `reconcileWithChildren` call reads. Reads anywhere in either block count.
 * The call must be a statement of the `update` lambda itself, on its `SwingNodeUpdater` receiver, implicit or
 * `this`. A write nested in a lambda, a condition, a local function or an object does not count, even one that
 * runs, such as inside `forEach` or `run`. Nor does a write in or after a statement that contains a return from
 * `update`. Also exempt are those named by the keys of an enclosing `androidx.compose.runtime.key` call, since a
 * change of the key builds the node again. The block of that call does not count as a key, and a `key` call
 * exempts only a node inside its block.
 *
 * The rule resolves calls and symbols, so it runs only under the type-resolving tasks (`detektMain`,
 * `detektTest`, `detektTestFixtures`). A call or reference that does not resolve is never reported.
 */
public class SwingNodeFactoryReadsDeclaredValue(
    config: Config,
) : Rule(config, "A parameter that only a `SwingNode` factory reads never updates the component"),
    RequiresAnalysisApi {
    override val ruleName: RuleName = RuleName("SwingNodeFactoryReadsDeclaredValue")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val function = expression.getStrictParentOfType<KtNamedFunction>() ?: return
        if (!expression.mayBeNodeCall()) return
        analyze(expression) {
            if (function.symbol.hasComposableAnnotation()) findingsIn(expression, function) else emptyList()
        }.forEach(::report)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.findingsIn(
        call: KtCallExpression,
        function: KtNamedFunction,
    ): List<Finding> {
        val (resolved, factory) = nodeCallWithFactory(call) ?: return emptyList()
        val exempt = appliedSymbols(resolved.argumentExpression(UPDATE)) + keyedSymbols(call, function)
        val declared = function.symbol.valueParameters + enclosingLambdaParameters(call, function)
        return factory
            .collectDescendantsOfType<KtNameReferenceExpression>()
            .sortedBy { it.startOffset }
            .mapNotNull { reference -> (reference.resolveSymbol() as? KaValueParameterSymbol)?.let { reference to it } }
            .filter { (_, parameter) -> parameter in declared && parameter !in exempt }
            .filterNot { (reference, parameter) ->
                isInvokedBy(factory, reference) && parameter in function.symbol.valueParameters &&
                    parameter.returnType.fullyExpandedType is KaFunctionType
            }.distinctBy { (_, parameter) -> parameter }
            .map { (reference, parameter) ->
                val name = reference.getReferencedName()
                val message =
                    if (parameter.returnType is KaFunctionType) {
                        functionTypedParameterMessage(name)
                    } else {
                        parameterMessage(name)
                    }
                Finding(entity = Entity.from(reference), message = message)
            }
    }

    /**
     * Whether [reference] is invoked by the factory itself: it is the callee of a call, or the receiver of a call to
     * the `invoke` of its function type, and no function, lambda or accessor nested in [factory] defers the call. A
     * user extension named `invoke` is not that function.
     */
    private fun KaSession.isInvokedBy(
        factory: KtLambdaExpression,
        reference: KtNameReferenceExpression,
    ): Boolean {
        val outer = reference.parents.firstOrNull { it !is KtParenthesizedExpression }
        val call =
            invocationOf(outer, reference)
                ?.takeIf { it.getStrictParentOfType<KtDeclarationWithBody>() === factory.functionLiteral }
        val invoke = call?.resolveToCall()?.successfulFunctionCallOrNull()
        return invoke?.symbol?.callableId?.callableName == Name.identifier("invoke") &&
            invoke.dispatchReceiver?.type is KaFunctionType
    }

    private fun invocationOf(
        outer: PsiElement?,
        reference: KtNameReferenceExpression,
    ): KtCallExpression? =
        when {
            outer is KtCallExpression && outer.calleeExpression.deparenthesized() === reference -> {
                outer
            }

            outer is KtDotQualifiedExpression && outer.receiverExpression.deparenthesized() === reference -> {
                outer.selectorExpression as? KtCallExpression
            }

            else -> {
                null
            }
        }

    private fun KtExpression?.deparenthesized(): KtExpression? = this?.let { KtPsiUtil.safeDeparenthesize(it) }

    /** The value parameters of the lambdas that enclose [call] inside [function]. */
    private fun KaSession.enclosingLambdaParameters(
        call: KtCallExpression,
        function: KtNamedFunction,
    ): List<KaValueParameterSymbol> =
        call.parents
            .takeWhile { it !== function }
            .filterIsInstance<KtLambdaExpression>()
            .flatMap { it.functionLiteral.symbol.valueParameters }
            .toList()

    /**
     * The symbols read by value arguments of `set` and `update`, or by blocks of `reconcile` and
     * `reconcileWithChildren`.
     * Only statements of [update] before the first statement that contains a return from it count.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.appliedSymbols(update: KtExpression?): Set<KaSymbol> {
        val lambda = update as? KtLambdaExpression
        val updateSymbol = lambda?.functionLiteral?.symbol
        val updater = updateSymbol?.receiverParameter ?: return emptySet()
        return lambda.bodyExpression
            ?.statements
            .orEmpty()
            .map { KtPsiUtil.safeDeparenthesize(it) }
            .takeWhile { statement ->
                !statement.anyDescendantOfType<KtReturnExpression> { it.resolveSymbol() == updateSymbol }
            }.mapNotNull { it.getPossiblyQualifiedCallExpression() }
            .mapNotNull { call -> call.resolveToCall()?.successfulFunctionCallOrNull() }
            .filter { isOnUpdater(it, updater) }
            .mapNotNull { call ->
                when {
                    call.symbol.isUpdaterWrite() -> call.argumentExpression(Name.identifier("value"))
                    call.symbol.isUpdaterReconcile() -> call.argumentExpression(BLOCK)
                    else -> null
                }
            }.flatMapTo(mutableSetOf()) { valueSymbols(it) }
    }

    /**
     * The symbols that the non-block arguments of the `key` calls around [call], inside [function], read. A `key`
     * call counts only when [call] lies inside its block argument.
     */
    private fun KaSession.keyedSymbols(
        call: KtCallExpression,
        function: KtNamedFunction,
    ): Set<KaSymbol> =
        call.parents
            .takeWhile { it !== function }
            .filterIsInstance<KtCallExpression>()
            .mapNotNull { it.resolveToCall()?.successfulFunctionCallOrNull() }
            .filter { it.symbol.callableId == CallableId(COMPOSABLE_CLASS_ID.packageFqName, Name.identifier("key")) }
            .filter { key -> key.argumentExpression(BLOCK)?.let { PsiTreeUtil.isAncestor(it, call, true) } == true }
            .flatMapTo(mutableSetOf()) { key ->
                key.valueArgumentMapping
                    .filterValues { it.name != BLOCK }
                    .keys
                    .flatMap { valueSymbols(it) }
            }

    /** The symbols of the name references in [expression], [expression] included. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.valueSymbols(expression: KtExpression): Set<KaSymbol> {
        val references = expression.collectDescendantsOfType<KtNameReferenceExpression>()
        val self = listOfNotNull(expression as? KtNameReferenceExpression)
        return (self + references).mapNotNullTo(mutableSetOf()) { it.resolveSymbol() }
    }
}

/** Whether [this] is `SwingNodeUpdater.reconcile` or the node package's `reconcileWithChildren` extension. */
private fun KaFunctionSymbol.isUpdaterReconcile(): Boolean {
    val id = callableId ?: return false
    return when (id.callableName) {
        Name.identifier("reconcile") -> id.classId == SWING_NODE_UPDATER
        Name.identifier("reconcileWithChildren") -> id.packageName == NODE_PACKAGE_NAME && id.classId == null
        else -> false
    }
}

private fun functionTypedParameterMessage(name: String): String =
    "The component keeps calling the first `$name` it got. `factory` runs only when the node is created, so " +
        "a later `$name` never reaches it. Read `$name` through `rememberUpdatedState` each time the " +
        "callback runs."

private fun parameterMessage(name: String): String =
    "A new `$name` never reaches the component. `factory` runs only when the node is created, and " +
        "`update` does not apply `$name`. Apply `$name` in `update` with `set` or `update`. If the component " +
        "takes it only when it is built, wrap the node in a `key` on `$name`."
