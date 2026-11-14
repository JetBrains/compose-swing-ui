// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Reports a `SwingModifier` factory that returns the element it builds instead of the chain.
 *
 * A caller reads one type all the way through a chain of factory calls. A factory that narrows its
 * return type to the element it builds makes callers depend on the concrete type, and so makes the element
 * part of the API.
 *
 * Only a declaration already declared as an extension on the chain is a factory candidate here:
 * nothing short of resolving types tells a plain declaration's return type apart from any other type
 * with the same name, so a declaration is checked only once its receiver already marks it as one.
 */
internal object SwingModifierFactoryReturnType : ComposeSwingRule {
    override val id: String = "SwingModifierFactoryReturnType"
    override val description: String = "A `SwingModifier` factory should declare `SwingModifier` as its return type"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                reportIfReturnTypeIsNotTheChain(function)
            }

            override fun visitProperty(property: KtProperty) {
                if (property.isFactoryCandidate()) reportIfReturnTypeIsNotTheChain(property)
            }

            private fun reportIfReturnTypeIsNotTheChain(declaration: KtCallableDeclaration) {
                if (!declaration.receiverTypeReference.declaresSwingModifier() ||
                    !declaration.isReachableFromOutside()
                ) {
                    return
                }
                val returnType = declaration.typeReference
                if (returnType == null || returnType.declaresSwingModifier()) return
                reporter.report(returnType, factoryReturnTypeMessage(declaration.name, returnType.text))
            }
        }

    private fun factoryReturnTypeMessage(
        name: String?,
        returnTypeText: String,
    ): String =
        "The `$name` factory returns `$returnTypeText` instead of `$SWING_MODIFIER`. " +
            "Callers then depend on that concrete type, so the factory cannot change what it builds " +
            "without breaking them. Declare `$SWING_MODIFIER` as the return type."
}

/**
 * Reports a `SwingModifier` factory that is not declared as an extension on the chain.
 *
 * A factory is declared as an extension on `SwingModifier` so that calls chain fluently. A factory that
 * is not an extension has to be joined onto the chain by hand with `then`.
 *
 * Only a declaration already declared to return the chain itself is a factory candidate here: nothing
 * short of resolving types tells a plain declaration's return type apart from any other type with the
 * same name, so a declaration is checked only once its own return type already marks it as one.
 */
internal object SwingModifierFactoryExtensionFunction : ComposeSwingRule {
    override val id: String = "SwingModifierFactoryExtensionFunction"
    override val description: String = "A `SwingModifier` factory should be an extension on `SwingModifier`"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                reportIfNotAnExtension(function)
            }

            override fun visitProperty(property: KtProperty) {
                if (property.isFactoryCandidate()) {
                    reportIfNotAnExtension(property)
                }
            }

            private fun reportIfNotAnExtension(declaration: KtCallableDeclaration) {
                val returnType = declaration.typeReference
                if (returnType == null || !returnType.declaresSwingModifier() ||
                    !declaration.isReachableFromOutside()
                ) {
                    return
                }
                if (declaration.receiverTypeReference.declaresSwingModifier() || declaration.isMemberOfTheChain()) {
                    return
                }
                reporter.report(declaration, factoryNotExtensionMessage(declaration.name))
            }
        }

    private fun factoryNotExtensionMessage(name: String?): String =
        "The `$name` factory is not an extension on `$SWING_MODIFIER`. " +
            "Callers build a chain by calling one factory after another, " +
            "and only an extension joins that chain. " +
            "Declare it as an extension on `$SWING_MODIFIER` and build the result on `this`. " +
            "If the factory has another receiver now, take that value as a parameter."
}

/**
 * Whether [this] names the library chain in a form this PSI-only rule can identify reliably.
 *
 * An arbitrary qualified name ending in `SwingModifier` is not enough: a consumer can define
 * `Other.SwingModifier`, and resolving that name would make the rule depend on a compiler classpath.
 * Keep the accepted forms to the imported/simple name and the library's exact FQN.
 */
internal fun KtTypeReference?.declaresSwingModifier(): Boolean =
    (this?.typeElement?.unwrapNullability() as? KtUserType)?.let { type ->
        type.declaresLibraryType(SWING_MODIFIER_FQN)
    } == true

internal const val SWING_MODIFIER_PACKAGE: String = "org.jetbrains.compose.swing.modifier"

internal const val SWING_MODIFIER_FQN: String = "$SWING_MODIFIER_PACKAGE.$SWING_MODIFIER"

/** Whether [this] names the type [fullyQualifiedName] by its simple name, an import of it, or the name in full. */
internal fun KtUserType.declaresLibraryType(fullyQualifiedName: String): Boolean {
    val simpleName = fullyQualifiedName.substringAfterLast('.')
    return qualifier?.let {
        it.text == fullyQualifiedName.substringBeforeLast('.') && referencedName == simpleName
    }
        ?: (containingFile as? KtFile)?.let { file ->
            !isShadowedLexically(referencedName) &&
                !file.declaresTypeName(referencedName) &&
                file.resolvesImportedName(referencedName, fullyQualifiedName, allowMultipleWildcardImports = true)
        }
        ?: false
}

private fun KtUserType.isShadowedLexically(name: String?): Boolean =
    parents.filterIsInstance<KtClassOrObject>().any { owner ->
        owner.name == name || owner.declarations.any { it.isTypeNamed(name) }
    } ||
        parents.filterIsInstance<KtTypeParameterListOwner>().any { owner ->
            owner.typeParameters.any { it.name == name }
        }

private fun KtFile.declaresTypeName(name: String?): Boolean = declarations.any { it.isTypeNamed(name) }

private fun KtDeclaration.isTypeNamed(name: String?): Boolean =
    when (this) {
        is KtClassOrObject -> this.name == name
        is KtTypeAlias -> this.name == name
        else -> false
    }

internal fun KtFile.resolvesImportedName(
    referencedName: String?,
    fullyQualifiedName: String,
    allowMultipleWildcardImports: Boolean = false,
): Boolean {
    val exactImports = importDirectives.filter { !it.isAllUnder && it.importedName() == referencedName }
    val packageName = fullyQualifiedName.substringBeforeLast('.')
    val simpleName = fullyQualifiedName.substringAfterLast('.')
    return when {
        exactImports.isNotEmpty() -> {
            exactImports.singleOrNull()?.importPath?.pathStr == fullyQualifiedName
        }

        referencedName != simpleName -> {
            false
        }

        packageFqName.asString() == packageName -> {
            true
        }

        else -> {
            val wildcardImports = importDirectives.filter(KtImportDirective::isAllUnder)
            (allowMultipleWildcardImports || wildcardImports.size == 1) &&
                wildcardImports.any { it.importedFqName?.asString() == packageName }
        }
    }
}

internal fun KtImportDirective.importedName(): String? = aliasName ?: importPath?.pathStr?.substringAfterLast('.')

/** Whether [this] is the simple name of the companion of the type [fullyQualifiedName], imported or in its package. */
internal fun KtNameReferenceExpression.isLibraryCompanionValue(fullyQualifiedName: String): Boolean =
    !isShadowedByValue() &&
        (
            containingKtFile.resolvesImportedName(getReferencedName(), fullyQualifiedName) ||
                containingKtFile.resolvesImportedName(getReferencedName(), "$fullyQualifiedName.Companion")
        )

private fun KtNameReferenceExpression.isShadowedByValue(): Boolean {
    val name = getReferencedName()
    val hasParameter =
        parents.filterIsInstance<KtFunction>().any { function ->
            function.valueParameters.any { parameter ->
                parameter.startOffset < startOffset &&
                    (
                        parameter.name == name ||
                            parameter.destructuringDeclaration
                                ?.entries
                                .orEmpty()
                                .any { it.name == name }
                    )
            }
        }
    val hasLocal =
        parents.filterIsInstance<KtBlockExpression>().any { block ->
            block.statements.filterIsInstance<KtProperty>().any {
                it.name == name && it.startOffset < startOffset
            }
        }
    val hasTopLevel = containingKtFile.declarations.filterIsInstance<KtProperty>().any { it.name == name }
    // Object and companion members are reachable from any depth.
    val owners = parents.filterIsInstance<KtClassOrObject>().toList()
    val instanceMembersReachable =
        owners.runningFold(true) { reachable, owner -> reachable && owner.keepsEnclosingInstance }
    val scopes =
        owners.zip(instanceMembersReachable).flatMap { (owner, reachable) ->
            listOfNotNull(owner.takeIf { reachable || it is KtObjectDeclaration }) +
                (owner as? KtClass)?.companionObjects.orEmpty()
        }
    return hasParameter || hasLocal || hasTopLevel || scopes.any { name in it.valueNames }
}

/** Whether code inside [this] can still reach the instance members of the class that encloses it. */
private val KtClassOrObject.keepsEnclosingInstance: Boolean
    get() = isLocal || (this is KtClass && isInner()) || (this is KtObjectDeclaration && isObjectLiteral())

/**
 * The names of the properties and `val` constructor parameters [this] declares. An extension property is left
 * out: without its receiver it names nothing.
 */
private val KtClassOrObject.valueNames: List<String?>
    get() =
        declarations.filterIsInstance<KtProperty>().filter { it.receiverTypeReference == null }.map { it.name } +
            primaryConstructorParameters.filter { it.hasValOrVar() }.map { it.name }

/**
 * Whether [this] is declared inside `SwingModifier` itself, where it is already on the chain and an
 * extension receiver would say the same thing twice.
 */
internal fun KtCallableDeclaration.isMemberOfTheChain(): Boolean =
    parents.filterIsInstance<KtClassOrObject>().any { it.name == SWING_MODIFIER } || isImplementingSomething()

/**
 * Whether [this] property is a factory candidate: a top-level `val` with a getter, the shape a property
 * takes when it acts as a `SwingModifier` factory.
 */
internal fun KtProperty.isFactoryCandidate(): Boolean = containingClassOrObject == null && !isVar && getter != null

internal object ModifierComposed : ComposeSwingRule {
    override val id: String = "ModifierComposed"
    override val description: String = "SwingModifier.composed should be used with caution"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val customModifiers = config.valueOrDefault("customModifiers", emptyList<String>()).toSet()
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                val receiver = function.receiverTypeReference ?: return
                val isModifier = receiver.declaresSwingModifier() || receiver.text in customModifiers
                if (!isModifier || function.isComposable()) return
                if (function.returnedExpressions().any { it.isSwingComposedCall(function) }) {
                    reporter.report(function.nameIdentifier ?: function, MODIFIER_COMPOSED_MESSAGE)
                }
            }
        }
    }

    private fun KtNamedFunction.returnedExpressions(): Sequence<KtExpression> =
        if (hasBlockBody()) {
            bodyBlockExpression
                ?.statements
                .orEmpty()
                .asSequence()
                .filterIsInstance<KtReturnExpression>()
                .mapNotNull { it.returnedExpression }
        } else {
            sequenceOf(bodyExpression).filterNotNull()
        }

    private fun KtExpression.isSwingComposedCall(factory: KtNamedFunction): Boolean {
        val expression = KtPsiUtil.safeDeparenthesize(this)
        val qualified = expression as? KtDotQualifiedExpression
        val call = (qualified?.selectorExpression ?: expression) as? KtCallExpression ?: return false
        val callee = (call.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        return callee != null &&
            containingKtFile.resolvesImportedName(callee, SWING_COMPOSED_FQN) &&
            !call.hasVisibleNamesake(callee) &&
            qualified?.receiverExpression.isSwingModifierReceiver(factory)
    }

    private fun KtExpression?.isSwingModifierReceiver(factory: KtNamedFunction): Boolean =
        when (val receiver = this?.let(KtPsiUtil::safeDeparenthesize)) {
            null -> true
            is KtThisExpression -> receiver.getLabelName()?.let { it == factory.name } ?: true
            is KtNameReferenceExpression -> receiver.isLibraryCompanionValue(SWING_MODIFIER_FQN)
            else -> false
        }
}

private const val SWING_COMPOSED_FQN = "$SWING_MODIFIER_PACKAGE.composed"

private const val MODIFIER_COMPOSED_MESSAGE =
    "This modifier factory uses `composed`. Each call creates a new element, so a composable taking it cannot skip. " +
        "Consider a `SwingModifier.ComponentNode` for per-component state and behavior. " +
        "Keep `composed` when the modifier must call composables."
