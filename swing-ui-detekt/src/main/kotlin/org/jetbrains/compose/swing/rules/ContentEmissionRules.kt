// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import com.intellij.psi.PsiElement
import io.nlopez.compose.core.util.hasAnyContextArguments
import io.nlopez.compose.core.util.hasReceiverType
import io.nlopez.compose.core.util.returnsValue
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/** Reports composable functions that emit content and declare a result. */
internal object ContentEmitterReturningValues : ComposeSwingRule {
    override val id: String = "ContentEmitterReturningValues"
    override val description: String = "Composable functions that emit content should not return values"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val emission = SwingContentEmission(config)
        return object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                val function = element as? KtFunction ?: return
                if (!function.isEmissionCandidate() || !function.returnsValue) return
                if (emission.count(function) > 0) {
                    reporter.report(
                        function.nameIdentifier ?: function,
                        CONTENT_EMITTER_RETURNING_VALUE_MESSAGE,
                    )
                }
            }
        }
    }
}

/** Reports a composable function that emits content from multiple sources. */
internal object MultipleContentEmitters : ComposeSwingRule {
    override val id: String = "MultipleEmitters"
    override val description: String = "Composable functions should not emit multiple pieces of content"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val emission = SwingContentEmission(config)
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.isEmissionCandidate() || function.hasAnyContextArguments) return
                val contributesMenus =
                    function.annotationEntries.any {
                        val type = it.typeReference?.typeElement as? KtUserType
                        type?.declaresLibraryType(SWING_MENU_COMPOSABLE_FQN) == true
                    }
                if (!contributesMenus && emission.count(function, groupVirtualNodes = true) > 1) {
                    reporter.report(
                        function.nameIdentifier ?: function,
                        MULTIPLE_CONTENT_EMITTERS_MESSAGE,
                    )
                }
            }
        }
    }
}

private fun KtFunction.isEmissionCandidate(): Boolean = isComposable() && hasBlockBody() && !hasReceiverType

/** Classifies calls from imports, visible declarations and configured emitter names. */
internal class SwingContentEmission(
    config: ComposeSwingRuleConfig,
) {
    private val configuredEmitters = config.valueOrDefault("contentEmitters", emptyList<String>()).toSet()
    private val deniedEmitters = config.valueOrDefault("contentEmittersDenylist", emptyList<String>()).toSet()
    private val knownCallables = KnownCallables + configuredEmitters + deniedEmitters

    fun emitsContent(
        call: KtCallExpression,
        resolvedFqName: String? = null,
        declaration: KtNamedFunction? = null,
    ): Boolean {
        val paths = countCall(call, EmissionQuery(), resolvedFqName, includeTransparent = false, declaration)
        return paths.maximum > 0
    }

    fun isEmissionBoundary(
        call: KtCallExpression,
        resolvedFqName: String? = null,
    ): Boolean = classify(call, reference(call, resolvedFqName)) == EmissionKind.Boundary

    fun transparentContent(
        call: KtCallExpression,
        resolvedFqName: String,
    ): KtLambdaExpression? =
        if (isEmissionBoundary(call, resolvedFqName)) null else call.transparentContent(resolvedFqName)

    fun count(
        function: KtFunction,
        groupVirtualNodes: Boolean = false,
    ): Int {
        val query = EmissionQuery(groupVirtualNodes, root = function)
        return countExpression(function.bodyExpression) { countCall(it, query) }.maximum
    }

    fun mayApplyModifier(call: KtCallExpression): Boolean {
        val reference = reference(call)
        return when (classify(call, reference)) {
            EmissionKind.Boundary -> {
                false
            }

            EmissionKind.Attached -> {
                true
            }

            EmissionKind.Unknown -> {
                reference.fqName == VIRTUAL_SWING_NODE_FQN ||
                    reference.localFunction?.isComposable() == true ||
                    (reference.isUnresolved && call.calleeName?.firstOrNull()?.isUpperCase() == true)
            }
        }
    }

    fun includes(
        call: KtCallExpression,
        within: PsiElement,
    ): Boolean =
        call.parents.firstOrNull { parent ->
            parent == within ||
                when (parent) {
                    is KtFunctionLiteral -> false
                    is KtDeclaration -> true
                    is KtLambdaExpression -> !parent.isCompositionContent()
                    else -> false
                }
        } == within

    private fun KtNamedFunction.emitsContent(query: EmissionQuery): Boolean =
        isComposable() &&
            query.emits(this) {
                countExpression(bodyExpression) { countCall(it, query) }.maximum > 0
            }

    private fun classify(
        call: KtCallExpression,
        reference: CallReference,
    ): EmissionKind =
        when {
            reference.fqName in SeparateRoots || call.matches(reference.fqName, deniedEmitters) -> EmissionKind.Boundary

            reference.fqName in AttachedEmitters ||
                call.matches(reference.fqName, configuredEmitters) -> EmissionKind.Attached

            else -> EmissionKind.Unknown
        }

    private fun countCall(
        call: KtCallExpression,
        query: EmissionQuery,
        resolvedFqName: String? = null,
        includeTransparent: Boolean = true,
        declaration: KtNamedFunction? = null,
    ): EmissionPaths {
        val reference = declaration?.let { CallReference(resolvedFqName, it) } ?: reference(call, resolvedFqName)
        val kind = classify(call, reference)
        return when {
            kind == EmissionKind.Boundary -> {
                EmissionPaths.Empty
            }

            kind == EmissionKind.Attached || reference.localFunction?.emitsContent(query) == true -> {
                EmissionPaths.One
            }

            includeTransparent -> {
                val slot = call.transparentContent(reference.fqName)
                val paths = countExpression(slot?.bodyExpression) { countCall(it, query) }
                if (query.groupVirtualNodes && reference.fqName == VIRTUAL_SWING_NODE_FQN) paths.asGroup() else paths
            }

            else -> {
                EmissionPaths.Empty
            }
        }
    }

    private fun KtLambdaExpression.isCompositionContent(): Boolean {
        val argument = parent as? KtValueArgument
        val owner = argument?.parent
        val call = (owner as? KtCallExpression) ?: (owner?.parent as? KtCallExpression) ?: return false
        val reference = reference(call)
        val namedContent = argument?.getArgumentName()?.asName?.asString() == "content"
        return classify(call, reference) != EmissionKind.Boundary &&
            when {
                call.transparentContent(reference.fqName) == this -> true
                reference.fqName in ContentContainers -> namedContent || argument in call.lambdaArguments
                else -> namedContent && call.matches(reference.fqName, configuredEmitters)
            }
    }

    private fun reference(
        call: KtCallExpression,
        resolvedFqName: String? = null,
    ): CallReference = call.reference(resolvedFqName, knownCallables)
}

private enum class EmissionKind { Attached, Boundary, Unknown }

private fun KtCallExpression.matches(
    fqName: String?,
    configured: Set<String>,
): Boolean = calleeName in configured || fqName in configured || fqName?.substringAfterLast('.') in configured

private fun KtCallExpression.reference(
    resolvedFqName: String?,
    knownCallables: Set<String>,
): CallReference {
    val name = calleeName ?: return CallReference(resolvedFqName)
    val file = containingKtFile
    val receiver = (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == this }?.receiverExpression
    val local = if (receiver == null) visibleDeclarations(name) else emptyList()
    val function = local.singleOrNull() as? KtNamedFunction

    return when {
        resolvedFqName != null -> {
            CallReference(resolvedFqName, file.functionAt(resolvedFqName, function))
        }

        receiver != null -> {
            val qualifiedName = "${receiver.text}.$name"
            CallReference(qualifiedName, file.functionAt(qualifiedName))
        }

        local.isNotEmpty() -> {
            CallReference(function?.fqName?.asString(), function, shadowed = true)
        }

        else -> {
            file.topLevelReference(name, knownCallables)
        }
    }
}

private fun KtFile.topLevelReference(
    name: String,
    knownCallables: Set<String>,
): CallReference {
    val imports =
        importDirectives
            .filter { !it.isAllUnder && it.importedName() == name }
            .mapNotNull { it.importedFqName?.asString() }
            .distinct()
    if (imports.isNotEmpty()) {
        val imported = imports.singleOrNull()
        return CallReference(imported, imported?.let { functionAt(it) }, shadowed = true)
    }

    val candidates = declarations.filter { it.name == name }
    val function = candidates.singleOrNull() as? KtNamedFunction
    return if (candidates.isNotEmpty()) {
        CallReference(function?.fqName?.asString(), function, shadowed = true)
    } else {
        CallReference(knownCallable(name, knownCallables))
    }
}

private fun countExpression(
    expression: KtExpression?,
    call: (KtCallExpression) -> EmissionPaths,
): EmissionPaths =
    when (expression) {
        is KtBlockExpression -> {
            expression.statements.fold(EmissionPaths.Empty) { paths, statement ->
                if (paths.continuing == null) paths else paths.then(countExpression(statement, call))
            }
        }

        is KtParenthesizedExpression -> {
            countExpression(expression.expression, call)
        }

        is KtQualifiedExpression -> {
            val paths = (expression.selectorExpression as? KtCallExpression)?.let(call) ?: EmissionPaths.Empty
            if (expression is KtSafeQualifiedExpression) paths.or(EmissionPaths.Empty) else paths
        }

        is KtLoopExpression -> {
            countExpression(expression.body, call).repeated()
        }

        is KtReturnExpression -> {
            val result = countExpression(expression.returnedExpression, call)
            if (expression.getLabelName() == null) EmissionPaths(null, result.maximum) else result
        }

        is KtCallExpression -> {
            call(expression)
        }

        else -> {
            countAlternatives(expression, call)
        }
    }

private fun countAlternatives(
    expression: KtExpression?,
    call: (KtCallExpression) -> EmissionPaths,
): EmissionPaths =
    when (expression) {
        is KtIfExpression -> {
            countExpression(expression.then, call).or(countExpression(expression.`else`, call))
        }

        is KtWhenExpression -> {
            val branches = expression.entries.map { countExpression(it.expression, call) }
            val paths = branches.reduceOrNull(EmissionPaths::or) ?: EmissionPaths.Empty
            if (expression.entries.any { it.isElse }) paths else paths.or(EmissionPaths.Empty)
        }

        is KtBinaryExpression -> {
            if (expression.operationToken == KtTokens.ELVIS) {
                countExpression(expression.left, call).or(countExpression(expression.right, call))
            } else {
                EmissionPaths.Empty
            }
        }

        else -> {
            EmissionPaths.Empty
        }
    }

private class EmissionQuery(
    val groupVirtualNodes: Boolean = false,
    root: KtFunction? = null,
) {
    private val active = mutableSetOf<KtFunction>().apply { root?.let(::add) }
    private val known = mutableMapOf<KtNamedFunction, Boolean>()
    private var recursionCount = 0

    fun emits(
        function: KtNamedFunction,
        evaluate: () -> Boolean,
    ): Boolean =
        known[function] ?: if (active.add(function)) {
            val recursionBefore = recursionCount
            val result = evaluate()
            active.remove(function)
            // A negative answer after reaching an active wrapper may depend on the caller.
            if (result || recursionCount == recursionBefore) known[function] = result
            result
        } else {
            recursionCount++
            false
        }
}

private data class EmissionPaths(
    val continuing: Int?,
    val returned: Int? = null,
) {
    val maximum: Int
        get() = maxOf(continuing ?: 0, returned ?: 0)

    fun then(next: EmissionPaths): EmissionPaths {
        val previous = continuing ?: return this
        val nextReturn = next.returned?.plus(previous)
        return EmissionPaths(next.continuing?.plus(previous), maxPath(returned, nextReturn))
    }

    fun or(other: EmissionPaths): EmissionPaths =
        EmissionPaths(maxPath(continuing, other.continuing), maxPath(returned, other.returned))

    fun repeated(): EmissionPaths = EmissionPaths(continuing?.times(2) ?: 0, returned)

    fun asGroup(): EmissionPaths = EmissionPaths(continuing?.coerceAtMost(1), returned?.coerceAtMost(1))

    private fun maxPath(
        first: Int?,
        second: Int?,
    ): Int? = if (first == null) second else maxOf(first, second ?: first)

    companion object {
        val Empty = EmissionPaths(0)
        val One = EmissionPaths(1)
    }
}

private data class CallReference(
    val fqName: String?,
    val localFunction: KtNamedFunction? = null,
    val shadowed: Boolean = false,
) {
    val isUnresolved: Boolean
        get() = fqName == null && !shadowed
}

private val KtCallExpression.calleeName: String?
    get() = calleeExpression?.text?.removeSurrounding("`")

private fun KtFile.functionAt(
    fqName: String,
    local: KtNamedFunction? = null,
): KtNamedFunction? =
    local?.takeIf { it.fqName?.asString() == fqName }
        ?: declarations.filterIsInstance<KtNamedFunction>().singleOrNull { it.fqName?.asString() == fqName }

private fun KtCallExpression.visibleDeclarations(name: String): List<KtNamedDeclaration> {
    for (scope in parents) {
        val candidates =
            when (scope) {
                is KtBlockExpression -> scope.statements.filter { it.textOffset < textOffset }
                is KtClassBody -> scope.declarations
                is KtFunction -> scope.valueParameters + scope
                else -> continue
            }
        val matching = candidates.filterIsInstance<KtNamedDeclaration>().filter { it.name == name }
        if (matching.isNotEmpty()) return matching
    }
    return emptyList()
}

private fun KtFile.knownCallable(
    name: String,
    knownCallables: Set<String>,
): String? {
    val samePackage = "${packageFqName.asString()}.$name"
    if (samePackage in knownCallables) return samePackage
    val starImports =
        importDirectives
            .filter { it.isAllUnder }
            .mapNotNull { it.importedFqName?.asString() }
            .map { "$it.$name" }
            .distinct()
            .filter { it in knownCallables }
    return starImports.singleOrNull() ?: "kotlin.$name".takeIf { it in ScopeFunctions }
}

private fun KtCallExpression.transparentContent(fqName: String?): KtLambdaExpression? {
    if (fqName !in TransparentComposables && fqName !in ScopeFunctions) return null
    val trailing = lambdaArguments.singleOrNull()?.getLambdaExpression()
    val parameter = if (fqName in ScopeFunctions) "block" else "content"
    val named = valueArguments.singleOrNull { it.getArgumentName()?.asName?.asString() == parameter }
    return trailing ?: named?.getArgumentExpression() as? KtLambdaExpression
}

private val ContentContainers =
    setOf(
        "org.jetbrains.compose.swing.animation.AnimatedContent",
        "org.jetbrains.compose.swing.animation.AnimatedVisibility",
        "org.jetbrains.compose.swing.animation.Crossfade",
        "org.jetbrains.compose.swing.animation.DeferredAnimatedContent",
        "org.jetbrains.compose.swing.animation.DeferredAnimatedVisibility",
        "org.jetbrains.compose.swing.components.Layer",
        "org.jetbrains.compose.swing.components.desktop.DesktopPane",
        "org.jetbrains.compose.swing.components.desktop.LayeredPane",
        "org.jetbrains.compose.swing.components.layout.Panel",
        "org.jetbrains.compose.swing.components.layout.ScrollPane",
        "org.jetbrains.compose.swing.components.layout.SplitPane",
        "org.jetbrains.compose.swing.components.layout.TabbedPane",
        "org.jetbrains.compose.swing.components.layout.ToolBar",
        "org.jetbrains.compose.swing.components.menu.Menu",
        "org.jetbrains.compose.swing.foundation.layout.Box",
        "org.jetbrains.compose.swing.foundation.layout.Column",
        "org.jetbrains.compose.swing.foundation.layout.Layout",
        "org.jetbrains.compose.swing.foundation.layout.Row",
        "org.jetbrains.compose.swing.node.ExistingSwingNode",
        "org.jetbrains.compose.swing.node.MenuNode",
        "org.jetbrains.compose.swing.node.SwingNode",
    )

private val AttachedEmitters =
    ContentContainers +
        setOf(
            "org.jetbrains.compose.swing.components.ComboBox",
            "org.jetbrains.compose.swing.components.Label",
            "org.jetbrains.compose.swing.components.ProgressBar",
            "org.jetbrains.compose.swing.components.Separator",
            "org.jetbrains.compose.swing.components.Slider",
            "org.jetbrains.compose.swing.components.Spinner",
            "org.jetbrains.compose.swing.components.button.Button",
            "org.jetbrains.compose.swing.components.button.CheckBox",
            "org.jetbrains.compose.swing.components.button.RadioButton",
            "org.jetbrains.compose.swing.components.button.ToggleButton",
            "org.jetbrains.compose.swing.components.layout.Glue",
            "org.jetbrains.compose.swing.components.layout.RigidArea",
            "org.jetbrains.compose.swing.components.layout.Spacer",
            "org.jetbrains.compose.swing.components.layout.Strut",
            "org.jetbrains.compose.swing.components.layout.ToolBarSeparator",
            "org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem",
            "org.jetbrains.compose.swing.components.menu.MenuItem",
            "org.jetbrains.compose.swing.components.menu.MenuSeparator",
            "org.jetbrains.compose.swing.components.menu.RadioButtonMenuItem",
            "org.jetbrains.compose.swing.components.selection.ListBox",
            "org.jetbrains.compose.swing.components.selection.RadioGroup",
            "org.jetbrains.compose.swing.components.selection.Table",
            "org.jetbrains.compose.swing.components.selection.Tree",
            "org.jetbrains.compose.swing.components.text.EditorPane",
            "org.jetbrains.compose.swing.components.text.FormattedTextField",
            "org.jetbrains.compose.swing.components.text.PasswordField",
            "org.jetbrains.compose.swing.components.text.TextArea",
            "org.jetbrains.compose.swing.components.text.TextField",
            "org.jetbrains.compose.swing.components.text.TextPane",
            "org.jetbrains.compose.swing.foundation.Canvas",
        )

private val SeparateRoots =
    setOf(
        "org.jetbrains.compose.swing.components.Tray",
        "org.jetbrains.compose.swing.components.menu.ContextMenu",
        "org.jetbrains.compose.swing.components.menu.PopupMenu",
        "org.jetbrains.compose.swing.window.Dialog",
        "org.jetbrains.compose.swing.window.MenuBar",
        "org.jetbrains.compose.swing.window.Window",
    )

private const val VIRTUAL_SWING_NODE_FQN = "org.jetbrains.compose.swing.node.VirtualSwingNode"
private const val SWING_MENU_COMPOSABLE_FQN = "org.jetbrains.compose.swing.annotations.SwingMenuComposable"

private val TransparentComposables =
    setOf("androidx.compose.runtime.CompositionLocalProvider", "androidx.compose.runtime.key", VIRTUAL_SWING_NODE_FQN)

private val ScopeFunctions = setOf("kotlin.also", "kotlin.apply", "kotlin.let", "kotlin.run", "kotlin.with")

private val KnownCallables = AttachedEmitters + SeparateRoots + TransparentComposables + ScopeFunctions

private const val CONTENT_EMITTER_RETURNING_VALUE_MESSAGE =
    "This composable both emits content and returns a value. " +
        "Callers have to emit the content to obtain the value. " +
        "Accept state or callbacks the caller needs to provide and have the composable return `Unit`. " +
        "Move independent value calculations into a separate function."

private const val MULTIPLE_CONTENT_EMITTERS_MESSAGE =
    "This composable emits multiple top-level components. " +
        "They attach as separate children of the parent, so callers cannot apply one modifier to the group. " +
        "Place them in a single root container or expose separate composables for callers to arrange themselves."
