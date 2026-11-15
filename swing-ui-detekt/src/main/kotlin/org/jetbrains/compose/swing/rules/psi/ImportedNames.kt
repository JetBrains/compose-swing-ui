// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.psi

import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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
