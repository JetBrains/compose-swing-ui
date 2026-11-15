// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core

import com.intellij.psi.PsiElement
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Reports a composable whose signature names `java.awt.Component` or any subclass of it.
 *
 * A composable that takes or hands back a component makes the caller own an object the composition is
 * meant to build, hold and release itself, and ties the composable to one instance. Take the state the
 * component shows and declare the component inside, through `SwingNode`.
 *
 * Checked are a named `@Composable` function, a `@Composable` property getter, a `@Composable` function
 * type also where it sits in a declaration that is not composable, and a use of a type alias that
 * contains a `@Composable` function type after expansion. A type alias is reported at its uses, not at its
 * declaration.
 * A function class with type arguments, such as
 * `Function1<JLabel, Unit>`, counts as a function type. A function type or alias use nested in another such
 * type is searched by the enclosing one and not reported again. For each, the value parameters, the
 * extension receiver, the context parameters, the return type (an inferred one too) and the type-parameter
 * bounds are searched through nullability, type arguments and function types at any depth, and a type
 * parameter of an enclosing class is searched through its bounds. A function's own type parameter is
 * reported once, at its bound, and not again where it is used. Visibility does not matter. An `actual` is
 * skipped, since its `expect` is the declaration that is checked. An `override` is reported at a position
 * only when no directly overridden declaration names a component at the matching position, so a component
 * that the supertype declares is reported once, there. A function type or alias use in the signature of an
 * `override` or `actual` is skipped, since the declaration it answers for is checked instead. Accepted
 * limit: a non-composable override or actual that introduces a component in such a type goes unreported,
 * and the base that declares the type is checked.
 *
 * Declarations marked `org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi`, and the members of a
 * class marked with it, are exempt, since a component library is where a composable takes a component.
 *
 * The rule resolves types, so it runs only under the type-resolving detekt tasks, and a type that does
 * not resolve is never reported.
 */
internal object SwingComponentInComposableSignature : ComposeSwingRule {
    override val id: String = "SwingComponentInComposableSignature"
    override val description: String = "A composable's signature should not name a Swing component"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                analyze(function) {
                    if (isCheckedComposable(function) && !isExempt(function)) {
                        findingsIn(function, function.symbol)
                    } else {
                        emptyList()
                    }
                }.forEach { reporter.report(it.entity, it.message) }
            }

            override fun visitProperty(property: KtProperty) {
                analyze(property) {
                    if (isCheckedComposable(property) && !isExempt(property)) {
                        findingsIn(property, property.symbol)
                    } else {
                        emptyList()
                    }
                }.forEach { reporter.report(it.entity, it.message) }
            }

            override fun visitTypeReference(typeReference: KtTypeReference) {
                if (typeReference.parents.any { it is KtTypeAlias }) return
                val typeElement = typeReference.typeElement?.unwrapNullability()
                if (typeElement !is KtFunctionType && typeElement !is KtUserType) return
                analyze(typeReference) {
                    val functionType = composableFunctionType(typeReference)
                    when {
                        isCoveredByASignature(typeReference) -> emptyList<RuleFinding>()
                        typeReference.parents.any { it is KtDeclaration && isExempt(it) } -> emptyList<RuleFinding>()
                        ComposableAliasUses.isInsideExpansion(this, typeReference) -> emptyList<RuleFinding>()
                        functionType != null -> findingsIn(functionType)
                        else -> expandedFunctionTypeFindingsIn(typeReference)
                    }
                }.forEach { reporter.report(it.entity, it.message) }
            }
        }

    private fun KaSession.findingsIn(
        declaration: KtCallableDeclaration,
        symbol: KaCallableSymbol,
    ): List<RuleFinding> {
        val overridden =
            if (declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
                symbol.directlyOverriddenSymbols.map { it.fakeOverrideOriginal }.toList()
            } else {
                emptyList()
            }
        val positions =
            declarationPositions(declaration, symbol).filterNot { position ->
                overridden.any { base -> position.typeIn(base).any { namesComponent(it, emptySet()) } }
            }
        return findingsNaming(positions, declaration.typeParameters.map { it.symbol }.toSet())
    }

    private fun KaSession.declarationPositions(
        declaration: KtCallableDeclaration,
        symbol: KaCallableSymbol,
    ): List<Position> =
        buildList {
            declaration.receiverTypeReference?.let {
                add(positionAt(it, "The receiver") { base -> listOfNotNull(base.receiverType) })
            }
            declaration.contextParameters.forEachIndexed { index, parameter ->
                parameter.typeReference?.let { add(parameterPosition(parameter, it, contextTypeAt(index))) }
            }
            declaration.valueParameters.forEachIndexed { index, parameter ->
                parameter.typeReference?.let { add(parameterPosition(parameter, it, valueTypeAt(index))) }
            }
            val returnTypes: (KaCallableSymbol) -> List<KaType> = { listOf(it.returnType) }
            val returnTypeReference = declaration.typeReference
            if (returnTypeReference != null) {
                add(positionAt(returnTypeReference, "The return type", returnTypes))
            } else {
                add(
                    Position(
                        entity = (declaration.nameIdentifier ?: declaration),
                        subject = "The inferred return type",
                        written = null,
                        type = symbol.returnType,
                        typeIn = returnTypes,
                    ),
                )
            }
            declaration.typeParameters.forEachIndexed { index, parameter ->
                parameter.extendsBound?.let {
                    add(positionAt(it, "The bound of type parameter `${parameter.name}`", boundsAt(index)))
                }
            }
            declaration.typeConstraints.forEach { constraint ->
                constraint.boundTypeReference?.let {
                    val index =
                        declaration.typeParameters.indexOfFirst { p ->
                            p.name == constraint.subjectTypeParameterName?.text
                        }
                    val subject = "The bound of type parameter `${constraint.subjectTypeParameterName?.text}`"
                    add(positionAt(it, subject, boundsAt(index)))
                }
            }
        }

    /** An alias use gets one finding even when its expansion contains several component positions. */
    private fun KaSession.expandedFunctionTypeFindingsIn(typeReference: KtTypeReference): List<RuleFinding> =
        ComposableAliasUses
            .expandedFunctionTypes(this, typeReference)
            .flatMap { findingsNaming(aliasPositions(typeReference, it), emptySet()).asSequence() }
            .take(1)
            .toList()

    private fun KaSession.findingsIn(functionType: KtFunctionType): List<RuleFinding> {
        val positions =
            buildList {
                functionType.receiverTypeReference?.let { add(positionAt(it, "The receiver")) }
                functionType.contextReceiversTypeReferences.forEach {
                    add(positionAt(it, "A context parameter"))
                }
                functionType.parameters.forEach { parameter ->
                    parameter.typeReference?.let { add(positionAt(it, "A parameter")) }
                }
                functionType.returnTypeReference?.let { add(positionAt(it, "The return type")) }
            }
        return findingsNaming(positions, ownTypeParameters = emptySet())
    }

    private fun KaSession.findingsNaming(
        positions: List<Position>,
        ownTypeParameters: Set<KaTypeParameterSymbol>,
    ): List<RuleFinding> =
        positions.filter { namesComponent(it.type, ownTypeParameters) }.map {
            val written = it.written?.let { text -> ", `$text`," }.orEmpty()
            RuleFinding(
                entity = it.entity,
                message = namesComponentMessage(it.subject, written),
            )
        }

    private fun KaSession.namesComponent(
        type: KaType,
        ownTypeParameters: Set<KaTypeParameterSymbol>,
        visited: MutableSet<KaTypeParameterSymbol> = mutableSetOf(),
    ): Boolean =
        when (type) {
            is KaFlexibleType -> {
                namesComponent(type.lowerBound, ownTypeParameters, visited)
            }

            is KaDefinitelyNotNullType -> {
                namesComponent(type.original, ownTypeParameters, visited)
            }

            is KaTypeParameterType -> {
                val symbol = type.symbol
                symbol !in ownTypeParameters &&
                    visited.add(symbol) &&
                    symbol.upperBounds.any { namesComponent(it, ownTypeParameters, visited) }
            }

            is KaClassType -> {
                val expanded = type.fullyExpandedType as? KaClassType ?: type
                expanded.classId != StandardClassIds.Nothing &&
                    (
                        expanded.isSubtypeOf(ClassId.fromString("java/awt/Component")) ||
                            expanded.typeArguments.any { argument ->
                                argument.type?.let { namesComponent(it, ownTypeParameters, visited) } == true
                            }
                    )
            }

            else -> {
                false
            }
        }
}

/** Finds the composable function types that a type alias use expands to. */
private object ComposableAliasUses {
    fun expandedFunctionTypes(
        session: KaSession,
        typeReference: KtTypeReference,
    ): Sequence<KaFunctionType> =
        with(session) {
            val type = typeReference.type.fullyExpandedType
            if (type.abbreviation != null) {
                composableFunctionTypesIn(type)
            } else {
                listOfNotNull((type as? KaFunctionType)?.takeIf { it.hasComposableAnnotation() }).asSequence()
            }
        }

    private fun KaSession.composableFunctionTypesIn(type: KaType): Sequence<KaFunctionType> =
        sequence {
            when (val expanded = type.fullyExpandedType) {
                is KaFlexibleType -> {
                    yieldAll(composableFunctionTypesIn(expanded.lowerBound))
                }

                is KaDefinitelyNotNullType -> {
                    yieldAll(composableFunctionTypesIn(expanded.original))
                }

                is KaClassType -> {
                    if (expanded is KaFunctionType && expanded.hasComposableAnnotation()) {
                        yield(expanded)
                    } else {
                        expanded.typeArguments.forEach { argument ->
                            argument.type?.let { yieldAll(composableFunctionTypesIn(it)) }
                        }
                    }
                }
            }
        }

    /** An enclosing alias use searches every composable function type in its expansion. */
    fun isInsideExpansion(
        session: KaSession,
        typeReference: KtTypeReference,
    ): Boolean =
        typeReference.parents.filterIsInstance<KtTypeReference>().any { expandedFunctionTypes(session, it).any() }
}

/** A signature position: where it is written, the type written there, and how a base declaration types it. */
private class Position(
    val entity: PsiElement,
    val subject: String,
    val written: String?,
    val type: KaType,
    val typeIn: (KaCallableSymbol) -> List<KaType> = NO_TYPES,
)

private val NO_TYPES: (KaCallableSymbol) -> List<KaType> = { emptyList() }

@OptIn(KaExperimentalApi::class)
private fun contextTypeAt(index: Int): (KaCallableSymbol) -> List<KaType> =
    { listOfNotNull(it.contextParameters.getOrNull(index)?.returnType) }

private fun valueTypeAt(index: Int): (KaCallableSymbol) -> List<KaType> =
    { listOfNotNull((it as? KaFunctionSymbol)?.valueParameters?.getOrNull(index)?.returnType) }

@OptIn(KaExperimentalApi::class)
private fun boundsAt(index: Int): (KaCallableSymbol) -> List<KaType> =
    { symbol ->
        val parameter = symbol.typeParameters.getOrNull(index)
        parameter?.upperBounds.orEmpty()
    }

private fun KaSession.positionAt(
    typeReference: KtTypeReference,
    subject: String,
    typeIn: (KaCallableSymbol) -> List<KaType> = NO_TYPES,
): Position =
    Position(
        entity = typeReference,
        subject = subject,
        written = typeReference.text,
        type = typeReference.type,
        typeIn = typeIn,
    )

private fun KaSession.parameterPosition(
    parameter: KtParameter,
    typeReference: KtTypeReference,
    typeIn: (KaCallableSymbol) -> List<KaType>,
): Position =
    Position(
        entity = parameter,
        subject = "The `${parameter.name}` parameter",
        written = typeReference.text,
        type = typeReference.type,
        typeIn = typeIn,
    )

/** The positions of a composable type alias use, read off the function type it expands to. */
@OptIn(KaExperimentalApi::class)
private fun aliasPositions(
    typeReference: KtTypeReference,
    type: KaFunctionType,
): List<Position> {
    fun at(
        subject: String,
        valueType: KaType,
    ): Position =
        Position(
            entity = typeReference,
            subject = subject,
            written = typeReference.text,
            type = valueType,
        )
    return buildList {
        type.receiverType?.let { add(at("The receiver", it)) }
        type.contextReceivers.forEach { add(at("A context parameter", it.type)) }
        type.parameterTypes.forEach { add(at("A parameter", it)) }
        add(at("The return type", type.returnType))
    }
}

/**
 * Whether the declaration or a class that encloses it carries the resolved component-library marker. Only the
 * marker annotation counts; an `@OptIn` that names it does not.
 */
private fun KaSession.isExempt(declaration: KtDeclaration): Boolean =
    (listOf(declaration) + declaration.parents.filterIsInstance<KtDeclaration>()).any {
        (it is KtNamedFunction || it is KtProperty || it is KtClassOrObject) &&
            it.symbol.annotations.contains(
                ClassId.fromString("org/jetbrains/compose/swing/annotations/SwingComponentLibraryApi"),
            )
    }

private fun KaSession.isCheckedComposable(declaration: KtCallableDeclaration): Boolean =
    !declaration.hasModifier(KtTokens.ACTUAL_KEYWORD) &&
        when (declaration) {
            is KtNamedFunction -> {
                declaration.nameIdentifier != null && declaration.symbol.hasComposableAnnotation()
            }

            is KtProperty -> {
                (declaration.symbol as? KaPropertySymbol)?.getter?.hasComposableAnnotation() == true
            }

            else -> {
                false
            }
        }

/**
 * Whether a declaration already searches the type reference: a checked composable, an enclosing composable
 * function type, or an `override` or `actual` whose base or `expect` declares the signature. The outermost
 * type reference must be a searched position of a checked composable (a receiver, a value or context
 * parameter, a return type or a type-parameter bound).
 */
private fun KaSession.isCoveredByASignature(typeReference: KtTypeReference): Boolean {
    val enclosing = typeReference.parents.filterIsInstance<KtTypeReference>().toList()
    val owner = typeReference.signatureOwner()
    return typeReference.isInOverrideOrActualSignature() ||
        enclosing.any { composableFunctionType(it) != null } ||
        (owner != null && isCheckedComposable(owner))
}

private fun namesComponentMessage(
    subject: String,
    written: String,
): String =
    "${subject}$written names a Swing component. A caller that holds the component can change " +
        "what the composition declares on it, so the composition is no longer the only writer of " +
        "its properties. Take the data, callbacks, value types or models the component shows " +
        "instead. If this is API for writing components, mark it `@SwingComponentLibraryApi`."
