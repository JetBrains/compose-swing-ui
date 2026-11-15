// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.node

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.core.RuleFinding
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Reports a `SwingNode` or `MenuNode` whose `update` writes a part of a composite component that source code
 * assembles.
 *
 * The component class `C` of the node call must be declared in the module under analysis. Inside the `block` of a
 * `SwingNodeUpdater.set` or `update` call, a write is a property assignment such as `title.text = it`,
 * `this.title.text = it` or `title.text += it`, or a call of a setter such as `title.setText(it)`. A write counts
 * only when the assigned property's setter or the called member resolves. It is reported when the receiver
 * of the write, `title`, is a property that
 * - is a member of `C` or of a superclass of `C` declared in the module, where the walk up the superclasses stops
 * at the first class that is not declared in source,
 * - has the type `java.awt.Component` or a subclass, and
 * - has an initializer that builds the part: a constructor call, or a call of a helper function that returns
 * `java.awt.Component` or a subclass, optionally followed by one `apply`, `also`, `run` or `let` call of the
 * Kotlin standard library.
 *
 * A helper function counts only when the component class or a superclass of it declares it in source, or the same
 * file declares it at top level. A function of another object, such as `source.label()`, does not.
 *
 * Each property is reported once per node call, at its first write. The message names the class that builds the
 * part, which can be a superclass of `C`. A write through a safe call, such as `title?.text = it`, counts.
 *
 * Never reported are
 * - a component class from a library or the JDK, a black box that the rule never looks into, because the rule reads
 * the class's source,
 * - a part that a constructor parameter hands in or that is assigned later,
 * - a delegated part, such as `val title by lazy { JLabel() }`,
 * - an initializer that calls a function from a library or the JDK,
 * - a write to a property of `C` itself, such as `text = it`,
 * - a read of a part,
 * - a write outside a `set` or `update` block, so an `init` or `reconcile` block is not checked, and
 * - a write through `apply`, `with` or another scope function on the part, such as `title.apply { text = it }` or
 * `with(title) { text = it }`.
 *
 * A composite panel that is assembled in Swing and hosted as one node hides its parts from the composition. No
 * modifier, test tag or style reaches them, and `update` pushes their values by hand. Compose the parts from
 * composables in a library container, or keep the composite a black box whose own setters write its parts.
 *
 * The rule resolves calls, symbols and types, so it runs only under the type-resolving tasks (`detektMain`,
 * `detektTest`, `detektTestFixtures`). A call, reference or type that does not resolve is never reported.
 */
internal object SwingNodeUpdateWritesComponentPart : ComposeSwingRule {
    override val id: String = "SwingNodeUpdateWritesComponentPart"
    override val description: String = "A node's `update` should not write the children a component creates itself"
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
        val node = nodeUpdate(call)
        if (node == null) return emptyList()
        return node.blocks
            .flatMap { partWrites(it) }
            .distinctBy { it.partSymbol }
            .map {
                val message = writesComponentPartMessage(it.part, it.component)
                RuleFinding(entity = it.write, message = message)
            }
    }

    private class PartWrite(
        val write: KtExpression,
        val part: String,
        val partSymbol: KaPropertySymbol,
        val component: String,
    )

    /** The writes in [updater] to a part that the component of the block builds. */
    private fun KaSession.partWrites(updater: UpdaterBlock): List<PartWrite> =
        writeTargets(updater.block).filter { resolves(it) }.mapNotNull { write ->
            builtPart(write.target, updater.receiver)?.let {
                val builder = (it.containingSymbol as? KaClassSymbol)?.name?.asString().orEmpty()
                PartWrite(write.site, it.name.asString(), it, builder)
            }
        }

    /** Whether [write] names a setter or a property that resolves, so a misspelled write is never reported. */
    private fun KaSession.resolves(write: WriteSite): Boolean =
        when (val access = write.access) {
            is KtCallExpression -> access.resolveToCall()?.successfulFunctionCallOrNull() != null
            else -> access.resolveToCall()?.successfulVariableAccessCall() != null
        }

    /**
     * The property that [target] names, when it is a part reached through [receiver] and built by an initializer of
     * the source classes of the receiver's type, which is the type after a smart cast; otherwise null.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.builtPart(
        target: KtExpression,
        receiver: KaSymbol,
    ): KaPropertySymbol? {
        val access = KtPsiUtil.safeDeparenthesize(target).resolveToCall()?.successfulVariableAccessCall()
        val property = access?.symbol as? KaPropertySymbol
        val reached = access != null && isOnReceiver(access, receiver)
        val component = (access?.dispatchReceiver?.type as? KaClassType)?.symbol
        val sourceClasses = (component as? KaClassSymbol)?.let { sourceClassChain(it) }.orEmpty()
        return property?.takeIf { reached && isBuiltPartOf(it, sourceClasses) }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isBuiltPartOf(
        property: KaPropertySymbol,
        sourceClasses: List<KaClassSymbol>,
    ): Boolean {
        val owner = (property.containingSymbol as? KaClassSymbol)?.takeIf { it in sourceClasses } ?: return false
        val initializer = (property.psi as? KtProperty)?.initializer
        return property.returnType.isSubtypeOf(COMPONENT) &&
            initializer != null && buildsFromOwnCode(initializer, owner)
    }

    /**
     * Whether [initializer], the initializer of a property of [owner], is a call of a constructor, or of a function
     * that [owner] or a superclass of it declares in source, or that the same file declares at top level, and that
     * returns a component, optionally followed by one scope function call.
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.buildsFromOwnCode(
        initializer: KtExpression,
        owner: KaClassSymbol,
    ): Boolean {
        var built = KtPsiUtil.safeDeparenthesize(initializer)
        val qualified = built as? KtDotQualifiedExpression
        val scope = qualified?.selectorExpression as? KtCallExpression
        if (qualified != null && scope != null && stdlibScopeCall(scope) != null) {
            built = KtPsiUtil.safeDeparenthesize(qualified.receiverExpression)
        }
        val constructor = built.getPossiblyQualifiedCallExpression() ?: return false
        val resolved = constructor.resolveToCall()?.successfulFunctionCallOrNull()
        val callee = resolved?.symbol
        val ownHelper =
            callee is KaNamedFunctionSymbol &&
                callee.returnType.isSubtypeOf(COMPONENT) &&
                isOwnHelper(resolved, callee, owner, initializer)
        return callee is KaConstructorSymbol || ownHelper
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.isOwnHelper(
        call: KaFunctionCall<*>,
        callee: KaNamedFunctionSymbol,
        owner: KaClassSymbol,
        initializer: KtExpression,
    ): Boolean =
        callee.origin == KaSymbolOrigin.SOURCE &&
            call.extensionReceiver == null &&
            when (callee.containingSymbol) {
                is KaClassSymbol -> isOnReceiver(call, owner)
                else -> callee.psi?.containingFile == initializer.containingFile
            }
}

private val COMPONENT: ClassId = ClassId.fromString("java/awt/Component")

/**
 * [symbol] and its superclasses, nearest first, up to the first class that is not declared in source, or nothing
 * when [symbol] itself is not. A class from a library or the JDK is a black box that the rules never look into.
 */
internal fun sourceClassChain(symbol: KaClassSymbol): List<KaClassSymbol> =
    generateSequence(symbol) { current ->
        current.superTypes
            .mapNotNull { (it as? KaClassType)?.symbol as? KaClassSymbol }
            .firstOrNull { it.classKind == KaClassKind.CLASS }
    }.takeWhile { it.origin == KaSymbolOrigin.SOURCE }.toList()

/** The resolved [call], when it calls a Kotlin standard library scope function, which runs its lambda at once. */
internal fun KaSession.stdlibScopeCall(call: KtCallExpression): KaFunctionCall<*>? {
    val scopeFunctions = setOf("apply", "also", "run", "let", "with")
    val resolved = call.resolveToCall()?.successfulFunctionCallOrNull()
    val id = resolved?.symbol?.callableId ?: return null
    return resolved.takeIf {
        id.packageName == FqName("kotlin") && id.classId == null && id.callableName.asString() in scopeFunctions
    }
}

/**
 * A write: the expression [site] that is reported, the [target] it writes through and the [access] that must resolve,
 * the qualified expression of an assignment or the setter call.
 */
private class WriteSite(
    val site: KtExpression,
    val target: KtExpression,
    val access: KtExpression,
)

/** Each write in [block] with the expression it writes through, in source order. */
private fun writeTargets(block: KtLambdaExpression): List<WriteSite> =
    block
        .collectDescendantsOfType<KtQualifiedExpression>()
        .sortedBy { it.startOffset }
        .mapNotNull { qualified ->
            val assignment = (qualified.parent as? KtBinaryExpression)?.takeIf { it.assigns(qualified) }
            val setter = (qualified.selectorExpression as? KtCallExpression)?.takeIf { it.isSetterCall() }
            when {
                assignment != null -> WriteSite(assignment, qualified.receiverExpression, qualified)
                setter != null -> WriteSite(qualified, qualified.receiverExpression, setter)
                else -> null
            }
        }

private fun KtBinaryExpression.assigns(target: KtExpression): Boolean =
    KtPsiUtil.isAssignment(this) && left?.let { KtPsiUtil.safeDeparenthesize(it) } === target

private fun KtCallExpression.isSetterCall(): Boolean {
    val name = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return false
    return name.startsWith("set") && name.removePrefix("set").firstOrNull()?.isUpperCase() == true
}

private fun writesComponentPartMessage(
    part: String,
    component: String,
): String =
    "This `update` writes `$part`, a child that `$component` builds itself. That child is not a node, so no " +
        "modifier, style or test tag reaches it, and its values are pushed by hand. Build the children as " +
        "composables in a library container, or give `$component` setters for what its children show and call " +
        "those from `update`."
