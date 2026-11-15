// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.node

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.core.RuleFinding
import org.jetbrains.compose.swing.rules.core.hasComposableAnnotation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a composable that calls `SwingNode` or `MenuNode` more than once.
 *
 * The rule checks the body of each `@Composable` named function, top-level, member or local, and of each
 * `@Composable` property getter. Every call to the top-level `SwingNode` or `MenuNode` of
 * `org.jetbrains.compose.swing.node` counts, in source order, including calls in nested lambdas such as a
 * content lambda, `key` or `forEach`. Branches of `if`, `when` and `try` count together, because a dispatcher
 * that picks one node per pass is the same misuse. The first call is left alone and each further one is
 * reported at its call expression.
 *
 * It does not descend into nested named declarations: local functions, classes, objects and anonymous objects.
 * A local `@Composable` function is its own scope. `ExistingSwingNode` is not counted, and a composable lambda
 * outside a named function, such as one passed to `setContent`, is not checked.
 *
 * `SwingNode` brings one component from a component library into the composition. A composable that calls it more
 * than once builds its layout from raw Swing components and mixes them with composables in one function. The
 * composition does not know what those components are, so their layout, state and listeners stay in Swing, and a
 * place that holds exactly one component, such as a tab header, refuses the second one.
 *
 * The rule resolves calls, so it runs only under the type-resolving tasks (`detektMain`, `detektTest`,
 * `detektTestFixtures`). A call that does not resolve is never counted.
 */
internal object MultipleSwingNodeCalls : ComposeSwingRule {
    override val id: String = "MultipleSwingNodeCalls"
    override val description: String = "A composable should not build its layout from raw Swing components"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                val name = function.name ?: return
                val body = function.bodyExpression
                val candidates = if (body != null) candidateCalls(body) else emptyList()
                if (candidates.isEmpty()) return
                analyze(function) {
                    if (function.symbol.hasComposableAnnotation()) furtherNodes(candidates, name) else emptyList()
                }.forEach { reporter.report(it.entity, it.message) }
            }

            override fun visitProperty(property: KtProperty) {
                val name = property.name ?: return
                val body = property.getter?.bodyExpression
                val candidates = if (body != null) candidateCalls(body) else emptyList()
                if (candidates.isEmpty()) return
                analyze(property) {
                    val getter = (property.symbol as? KaPropertySymbol)?.getter
                    if (getter?.hasComposableAnnotation() == true) furtherNodes(candidates, name) else emptyList()
                }.forEach { reporter.report(it.entity, it.message) }
            }
        }

    /** The calls in [body] that name a node function, by syntax alone. */
    private fun candidateCalls(body: KtElement): List<KtCallExpression> =
        NodeCalls().also(body::accept).calls.filter { it.mayBeNodeCall() }

    private fun KaSession.furtherNodes(
        candidates: List<KtCallExpression>,
        owner: String,
    ): List<RuleFinding> {
        val nodeCalls =
            candidates
                .mapNotNull { call ->
                    call
                        .resolveToCall()
                        ?.successfulFunctionCallOrNull()
                        ?.takeIf { it.isNodeCall() }
                        ?.let { call to it }
                }
        return nodeCalls.drop(1).map { (call, resolved) ->
            RuleFinding(
                entity = call,
                message = multipleNodeCallMessage(owner, resolved.symbol.callableId?.callableName),
            )
        }
    }

    /** The calls in source order, without those inside a nested named declaration. */
    private class NodeCalls : KtTreeVisitorVoid() {
        val calls: MutableList<KtCallExpression> = mutableListOf()

        override fun visitCallExpression(expression: KtCallExpression) {
            calls += expression
            super.visitCallExpression(expression)
        }

        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.name == null) super.visitNamedFunction(function)
        }

        override fun visitClassOrObject(classOrObject: KtClassOrObject) = Unit

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) = Unit
    }
}

private fun multipleNodeCallMessage(
    owner: String,
    nodeName: Name?,
): String =
    "`$owner` builds its layout from raw Swing components. A Swing component placed directly " +
        "in a layout keeps its layout, state and listeners out of the composition's reach. " +
        "Give each component its own composable that calls " +
        "`$nodeName` once, and build `$owner` " +
        "from component composables and the library's layouts."
