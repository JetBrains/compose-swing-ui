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
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

/**
 * Reports a set or update block of `SwingNode` or `MenuNode` that stores a callback in a property of a component
 * that listens to itself.
 *
 * The rule checks each `set` and `update` call on the `SwingNodeUpdater` in the `update` lambda of a node call. In
 * the block of that call, it reports an assignment to a property of the component, written on the block's receiver,
 * implicit or `this`, whose assigned value has a Kotlin function type, type aliases expanded. The component class
 * must be declared in the source under analysis. A superclass of it counts when it is declared in source too, and
 * the walk up the superclasses stops at the first class from a library or the JDK. The component, or such a
 * superclass, must call a member named `add*Listener` on itself while it is built. That is the code of a
 * constructor, an `init` block or a property initializer, including a lambda that a Kotlin standard library scope
 * function such as `apply` or `run` runs at once. The argument of that call must read the assigned property, or a
 * property that it overrides, on the component. The read is in a lambda body, in the member bodies of an object
 * literal or in a member that a function reference names, and an assignment to the property is not a read. A
 * function reference counts when it has no receiver or `this`, and names a member of the component or of such a
 * superclass. Each assignment is reported once, at the assignment.
 *
 * Not reported are a component class from a library or the JDK, because the rule reads the class's source, a class
 * that installs no listener on itself while it is built, a listener that does not read the assigned property, a
 * registration in `with(this) { }` in an initializer or `init` block, since `with` takes the component as an argument
 * rather than as a receiver, and a function stored on a part of the component rather than on the component. Listeners
 * that a class registers on a part, or after it is built, are not looked at either, nor is a lambda that is stored
 * for later or a function that is declared in an `init` block. A listener that reaches the callback through a
 * member function it calls is not reported, and neither is `addXListener(this)` on a class that implements the
 * listener itself.
 *
 * The `SwingNode` documentation says to install listeners through the modifier mechanism: a listener modifier
 * attaches when the node attaches and detaches when it is released.
 *
 * The rule resolves calls, types and symbols, so it runs only under the type-resolving tasks (`detektMain`,
 * `detektTest`, `detektTestFixtures`). An expression that does not resolve is never reported.
 */
public class SwingNodeUpdateStoresCallback(
    config: Config,
) : Rule(
        config,
        "A node should report events through a listener modifier, not a callback stored on its component",
    ),
    RequiresAnalysisApi {
    override val ruleName: RuleName = RuleName("SwingNodeUpdateStoresCallback")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.mayBeNodeCall()) return
        analyze(expression) { findingsIn(expression) }.forEach(::report)
    }

    private fun KaSession.findingsIn(call: KtCallExpression): List<Finding> =
        nodeUpdate(call)?.blocks.orEmpty().flatMap { updater ->
            updater.component?.let { assignmentFindings(updater.block, updater.receiver, it) }.orEmpty()
        }

    private fun KaSession.assignmentFindings(
        block: KtLambdaExpression,
        receiver: KaSymbol,
        component: KaClassSymbol,
    ): List<Finding> {
        val registrations by lazy(LazyThreadSafetyMode.NONE) { selfRegistrations(component) }
        return block
            .collectDescendantsOfType<KtBinaryExpression> { it.operationToken == KtTokens.EQ }
            .mapNotNull { assignment ->
                val property = storedProperty(assignment, receiver) ?: return@mapNotNull null
                val listener =
                    registrations.firstOrNull { readsProperty(it.call, property, it.owner) }
                        ?: return@mapNotNull null
                Finding(
                    entity = Entity.from(assignment),
                    message =
                        storedCallbackMessage(
                            property.name.asString(),
                            component.name?.asString(),
                            listener.name,
                        ),
                )
            }
    }

    /** The property of the component that [assignment] writes on [receiver] a function to, or null. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.storedProperty(
        assignment: KtBinaryExpression,
        receiver: KaSymbol,
    ): KaPropertySymbol? {
        val target = assignment.left?.let { KtPsiUtil.safeDeparenthesize(it) }
        val access = target?.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
        val storesFunction = assignment.right?.expressionType?.fullyExpandedType is KaFunctionType
        return (access?.symbol as? KaPropertySymbol)
            ?.takeIf { storesFunction && isOnReceiver(access, receiver) }
    }

    /**
     * The `add*Listener` calls that [component], or a superclass of it declared in source, makes on itself while it
     * is built, nearest class first. A class from a library or the JDK is a black box: the walk stops there.
     */
    private fun KaSession.selfRegistrations(component: KaClassSymbol): List<SelfListenerRegistration> =
        sourceClassChain(component).flatMap { registrationsIn(it) }

    /**
     * Whether a body in [call], or a member that a function reference in it names, reads [property], or a property
     * that it overrides, on [owner]. A read in [call] counts only inside a lambda, function or accessor body, since
     * any other read runs once, when the listener is registered.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.readsProperty(
        call: KtCallExpression,
        property: KaPropertySymbol,
        owner: KaClassSymbol,
    ): Boolean {
        val chain = sourceClassChain(owner)
        val names: Set<KaSymbol?> = setOf<KaSymbol?>(property) + property.allOverriddenSymbols

        fun reads(
            scope: KtElement,
            on: KaClassSymbol,
            deferred: Boolean,
        ) = scope.collectDescendantsOfType<KtNameReferenceExpression>().any {
            it.resolveSymbol() in names && isReadOn(it, on) &&
                (
                    !deferred || PsiTreeUtil
                        .getParentOfType(it, KtDeclarationWithBody::class.java, true)
                        ?.let { body -> PsiTreeUtil.isAncestor(scope, body, true) } == true
                )
        }
        val members =
            call.collectDescendantsOfType<KtCallableReferenceExpression>().mapNotNull { reference ->
                val member = reference.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
                val declaring = member?.containingDeclaration as? KaClassSymbol ?: return@mapNotNull null
                val function = member.psi as? KtFunction ?: return@mapNotNull null
                (function to declaring).takeIf { declaring in chain && isOwnerReceiver(reference, owner) }
            }
        return reads(call, owner, true) || members.any { (function, declaring) -> reads(function, declaring, false) }
    }

    /** Whether [reference] has no receiver or `this` as its receiver, with [owner] as the `this`. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isOwnerReceiver(
        reference: KtCallableReferenceExpression,
        owner: KaClassSymbol,
    ): Boolean {
        val receiver = reference.receiverExpression?.let { KtPsiUtil.safeDeparenthesize(it) } ?: return true
        return (receiver as? KtThisExpression)?.resolveSymbol()?.let { isOwnerSymbol(it, owner) } == true
    }

    /** Whether [reference] reads a property of [owner]: it is no assignment target and its receiver is [owner]. */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isReadOn(
        reference: KtNameReferenceExpression,
        owner: KaClassSymbol,
    ): Boolean {
        val access =
            generateSequence<KtExpression>(reference.getQualifiedExpressionForSelectorOrThis()) {
                it.parent as? KtParenthesizedExpression
            }.last()
        val target = (access.parent as? KtBinaryExpression)?.takeIf { it.left == access }
        if (target?.operationToken == KtTokens.EQ) return false
        return isOnOwner(reference.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>(), owner)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.registrationsIn(owner: KaClassSymbol): List<SelfListenerRegistration> {
        val psi = owner.psi as? KtClassOrObject ?: return emptyList()
        val building =
            psi.declarations.flatMap<_, KtElement> { declaration ->
                when (declaration) {
                    is KtClassInitializer -> listOf(declaration)
                    is KtSecondaryConstructor -> listOf(declaration)
                    is KtProperty -> listOfNotNull(declaration.initializer)
                    else -> emptyList()
                }
            }
        return building
            .flatMap { element -> BuildCalls { scopeCall(it) != null }.also { element.accept(it) }.calls }
            .mapNotNull { call ->
                val resolved = call.resolveToCall()?.successfulFunctionCallOrNull() ?: return@mapNotNull null
                val name =
                    resolved.symbol.callableId
                        ?.callableName
                        ?.asString()
                name
                    ?.takeIf { Regex("add\\w*Listener").matches(it) && isOnOwner(resolved, owner) }
                    ?.let { SelfListenerRegistration(it, call, owner) }
            }
    }

    /** An `add*Listener` call that [owner] makes on itself while it is built, named [name]. */
    private class SelfListenerRegistration(
        val name: String,
        val call: KtCallExpression,
        val owner: KaClassSymbol,
    )

    /**
     * The calls of the code a class runs while it is built. A lambda is entered only when [runsAtOnce] accepts it,
     * and a nested function or class is not entered.
     */
    private class BuildCalls(
        private val runsAtOnce: (KtLambdaExpression) -> Boolean,
    ) : KtTreeVisitorVoid() {
        val calls: MutableList<KtCallExpression> = mutableListOf()

        override fun visitCallExpression(expression: KtCallExpression) {
            calls += expression
            super.visitCallExpression(expression)
        }

        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
            if (runsAtOnce(lambdaExpression)) super.visitLambdaExpression(lambdaExpression)
        }

        override fun visitNamedFunction(function: KtNamedFunction) = Unit

        override fun visitClassOrObject(classOrObject: KtClassOrObject) = Unit
    }
}

/** The resolved call of the scope function that [lambda] is the argument of, or null. */
private fun KaSession.scopeCall(lambda: KtLambdaExpression): KaFunctionCall<*>? {
    val argument = lambda.parent as? KtValueArgument
    val call = (argument?.parent as? KtValueArgumentList ?: argument)?.parent as? KtCallExpression
    return call?.let { stdlibScopeCall(it) }
}

/**
 * Whether [call] is on [owner]: as its implicit receiver or an explicit `this`, or as the receiver of a scope
 * function that [owner] is the receiver of, such as `apply`, at any depth.
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.isOnOwner(
    call: KaCallableMemberCall<*, *>?,
    owner: KaClassSymbol,
): Boolean = isOwnerValue(call?.partiallyAppliedSymbol?.dispatchReceiver, owner)

@OptIn(KaExperimentalApi::class)
private fun KaSession.isOwnerValue(
    value: KaReceiverValue?,
    owner: KaClassSymbol,
): Boolean =
    when (value) {
        is KaSmartCastedReceiverValue -> {
            isOwnerValue(value.original, owner)
        }

        is KaImplicitReceiverValue -> {
            isOwnerSymbol(value.symbol, owner)
        }

        is KaExplicitReceiverValue -> {
            val receiver = KtPsiUtil.safeDeparenthesize(value.expression) as? KtThisExpression
            receiver?.resolveSymbol()?.let { isOwnerSymbol(it, owner) } == true
        }

        else -> {
            false
        }
    }

/** Whether a `this` that resolves to [symbol] is [owner], or the receiver of a scope function run on [owner]. */
@OptIn(KaExperimentalApi::class)
private fun KaSession.isOwnerSymbol(
    symbol: KaSymbol,
    owner: KaClassSymbol,
): Boolean {
    if (symbol == owner) return true
    val literal = (symbol as? KaReceiverParameterSymbol)?.owningCallableSymbol?.psi
    val lambda = (literal as? KtFunctionLiteral)?.parent as? KtLambdaExpression
    return isOwnerValue(lambda?.let { scopeCall(it) }?.extensionReceiver, owner)
}

/**
 * The modifier that `addXListener` maps to, `xListener`, when `SwingModifier` declares it, from the files of its
 * `modifier/listener` package.
 */
private fun modifierFor(registration: String): String? {
    val declared =
        setOf(
            "actionListener",
            "adjustmentListener",
            "caretListener",
            "changeListener",
            "componentListener",
            "containerListener",
            "documentListener",
            "focusListener",
            "hierarchyListener",
            "hyperlinkListener",
            "internalFrameListener",
            "itemListener",
            "keyListener",
            "listSelectionListener",
            "mouseListener",
            "mouseMotionListener",
            "mouseWheelListener",
            "propertyChangeListener",
            "treeExpansionListener",
            "treeSelectionListener",
            "treeWillExpandListener",
        )
    return registration
        .removePrefix("add")
        .replaceFirstChar { it.lowercaseChar() }
        .takeIf { it in declared }
}

private fun storedCallbackMessage(
    property: String,
    component: String?,
    registration: String,
): String {
    val advice =
        modifierFor(registration)?.let { "`SwingModifier.$it`" }
            ?: "`SwingModifier.listener` and a `ListenerRegistration` that adds and removes it"
    val owner = component?.let { "`$it`" } ?: "The component"
    return "$owner calls the callback stored in `$property` from a listener it adds to itself. That listener " +
        "stays on the component after its node is released, while a listener modifier lives exactly as long " +
        "as the node. Remove the callback property and the self-registration, and attach the listener with " +
        "$advice."
}
