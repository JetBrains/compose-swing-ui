// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
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
public class SwingModifierFactoryReturnType(
    config: Config,
) : Rule(config, "A `SwingModifier` factory should declare `SwingModifier` as its return type") {
    override val ruleName: RuleName = RuleName("SwingModifierFactoryReturnType")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        reportIfReturnTypeIsNotTheChain(function)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (property.isFactoryCandidate()) reportIfReturnTypeIsNotTheChain(property)
    }

    private fun reportIfReturnTypeIsNotTheChain(declaration: KtCallableDeclaration) {
        if (!declaration.receiverTypeReference.declaresSwingModifier() || !declaration.isReachableFromOutside()) {
            return
        }
        val returnType = declaration.typeReference
        if (returnType == null || returnType.declaresSwingModifier()) return
        report(
            Finding(
                entity = Entity.from(returnType),
                message = factoryReturnTypeMessage(declaration.name, returnType.text),
            ),
        )
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
public class SwingModifierFactoryExtensionFunction(
    config: Config,
) : Rule(config, "A `SwingModifier` factory should be an extension on `SwingModifier`") {
    override val ruleName: RuleName = RuleName("SwingModifierFactoryExtensionFunction")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        reportIfNotAnExtension(function)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (property.isFactoryCandidate()) {
            reportIfNotAnExtension(property)
        }
    }

    private fun reportIfNotAnExtension(declaration: KtCallableDeclaration) {
        val returnType = declaration.typeReference
        if (returnType == null || !returnType.declaresSwingModifier() || !declaration.isReachableFromOutside()) {
            return
        }
        if (declaration.receiverTypeReference.declaresSwingModifier() || declaration.isMemberOfTheChain()) return
        report(
            Finding(
                entity = Entity.from(declaration),
                message = factoryNotExtensionMessage(declaration.name),
            ),
        )
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
                file.resolvesImportedName(referencedName, fullyQualifiedName)
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
            wildcardImports
                .singleOrNull()
                ?.importPath
                ?.pathStr
                ?.removeSuffix(".*") == packageName
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
        parents.filterIsInstance<KtNamedFunction>().firstOrNull()?.valueParameters?.any {
            it.name == name && it.startOffset < startOffset
        } == true
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
