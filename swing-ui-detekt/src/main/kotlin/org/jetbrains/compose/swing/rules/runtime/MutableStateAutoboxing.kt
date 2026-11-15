// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports `mutableStateOf` whose value type, as the compiler resolves it, is a non-null `Int`, `Long`,
 * `Float` or `Double` and whose policy is the default structural equality, since a primitive state class
 * avoids boxing each value.
 *
 * It also reports a non-null `List` or `Set` of `Int`, `Long` or `Float`, or a non-null `Map` from `Int`,
 * `Long` or `Float` to `Int`, `Long` or `Float`, but only where the call's single argument is a parameter of
 * a function, lambda or secondary constructor read as is. A value that is a call, a property, a local variable
 * or a primary constructor parameter is not reported, which is the scope compose-rules gives these types.
 * The immutable and persistent collections of kotlinx.collections.immutable count as their read-only
 * counterparts.
 */
internal object MutableStateAutoboxing : ComposeSwingRule {
    override val id = "MutableStateAutoboxing"
    override val description = "Avoid autoboxing when creating MutableState"
    override val severity = ComposeSwingRule.Severity.Warning
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    private val scalars =
        mapOf(
            "kotlin.Int" to "Int",
            "kotlin.Long" to "Long",
            "kotlin.Float" to "Float",
            "kotlin.Double" to "Double",
        )

    private val collectionElements = scalars - "kotlin.Double"

    private val collections =
        listOf("List", "Set", "Map")
            .flatMap { kind ->
                val immutable = "kotlinx.collections.immutable"
                listOf(
                    "kotlin.collections.$kind" to kind,
                    "$immutable.Immutable$kind" to kind,
                    "$immutable.Persistent$kind" to kind,
                )
            }.toMap()

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                val message = analyze(expression) { messageFor(expression) } ?: return
                reporter.report(expression.calleeExpression ?: expression, message)
            }
        }

    private fun KaSession.messageFor(expression: KtCallExpression): String? {
        val call =
            expression
                .resolveToCall()
                ?.successfulFunctionCallOrNull()
                ?.takeIf { it.isRuntimeFunction(Names.Runtime.MutableStateOf) && usesStructuralEqualityPolicy(it) }
        val type = call?.typeArgumentsMapping?.values?.singleOrNull() ?: return null
        val classId = nonNullClassName(type)
        val primitive = scalars[classId]
        val kind = collections[classId]
        return when {
            primitive != null -> {
                primitiveStateMessage(primitive)
            }

            kind != null && readsFunctionParameter(expression) -> {
                elementsOf(type)?.let { primitiveCollectionStateMessage(kind, it) }
            }

            else -> {
                null
            }
        }
    }

    private fun KaSession.readsFunctionParameter(call: KtCallExpression): Boolean {
        val argument = call.valueArguments.singleOrNull()?.getArgumentExpression() as? KtNameReferenceExpression
        val parameter = argument?.mainReference?.resolveToSymbol() as? KaValueParameterSymbol
        return when (val owner = parameter?.containingDeclaration) {
            is KaNamedFunctionSymbol -> true
            is KaAnonymousFunctionSymbol -> !parameter.isImplicitLambdaParameter
            is KaConstructorSymbol -> !owner.isPrimary
            else -> false
        }
    }

    private fun KaSession.elementsOf(type: KaType): List<String>? {
        val arguments = (type.fullyExpandedType as? KaClassType)?.typeArguments
        val names = arguments?.map { collectionElements[nonNullClassName(it.type)] }
        return names?.takeIf { null !in it }?.filterNotNull()
    }

    private fun KaSession.nonNullClassName(type: KaType?): String? =
        type
            ?.takeUnless { it.isMarkedNullable }
            ?.expandedSymbol
            ?.classId
            ?.asFqNameString()

    private fun KaSession.usesStructuralEqualityPolicy(call: KaFunctionCall<*>): Boolean {
        val policy =
            call.valueArgumentMapping.entries
                .firstOrNull { it.value.name.identifier == "policy" }
                ?.key
                ?.let(KtPsiUtil::deparenthesize)
        // No policy argument means the default, which is structural equality.
        return policy == null ||
            policy.resolveToCall()?.successfulFunctionCallOrNull()?.isRuntimeFunction(
                Names.Runtime.StructuralEqualityPolicy,
            ) == true
    }

    private fun KaFunctionCall<*>.isRuntimeFunction(name: Name): Boolean {
        val id = symbol.callableId
        return id?.packageName?.asString() == name.packageName.javaPackageName &&
            id.callableName.asString() == name.shortName
    }
}

private fun primitiveStateMessage(type: String): String =
    "This state stores a boxed `$type`. Boxing wraps primitive values in objects and can add allocations when the " +
        "state changes. Use `mutable${type}StateOf` to store the primitive directly."

private fun primitiveCollectionStateMessage(
    kind: String,
    elements: List<String>,
): String {
    val type = elements.joinToString(prefix = "$kind<", postfix = ">")
    val factory = elements.joinToString(separator = "", prefix = "mutable", postfix = "${kind}Of")
    return "This state stores a `$type` with boxed elements. Boxing wraps primitive values in objects. " +
        "Use `androidx.collection.$factory` to create a primitive-backed collection. " +
        "Build a new collection with the changes and assign it to state so Compose can observe the update."
}
