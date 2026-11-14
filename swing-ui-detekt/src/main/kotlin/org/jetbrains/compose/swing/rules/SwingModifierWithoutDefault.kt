// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Reports a composable whose `SwingModifier` parameter does not default to the empty chain.
 *
 * A caller that has nothing to declare should not have to say so. The parameter defaults to the empty
 * chain, as `Modifier` does in Compose, and every wrapper in this library is written that way.
 *
 * Only a declaration a caller outside its module can reach is checked. A private helper is always
 * called with a chain by the composable that owns it, and a default there would stand for nothing.
 */
internal object SwingModifierWithoutDefault : ComposeSwingRule {
    override val id: String = "SwingModifierWithoutDefault"
    override val description: String = "A composable's `SwingModifier` parameter should default to the empty modifier"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.isComposable() || !function.isReachableFromOutside() ||
                    function.isImplementingSomething()
                ) {
                    return
                }
                function.valueParameters
                    .filter { it.declaresSwingModifier() }
                    .forEach(::reportUnlessDefaultsToTheEmptyChain)
            }

            private fun reportUnlessDefaultsToTheEmptyChain(parameter: KtParameter) {
                val default = parameter.defaultValue
                if (default == null) {
                    reporter.report(parameter, missingDefaultMessage(parameter.name))
                    return
                }
                if (default.isLibraryCompanionDefault(SWING_MODIFIER_FQN)) return
                reporter.report(default, wrongDefaultMessage(parameter.name, default.text))
            }
        }
}

internal const val SWING_MODIFIER: String = "SwingModifier"

internal fun KtCallableDeclaration.isComposable(): Boolean =
    when (this) {
        is KtProperty -> {
            annotationEntries.any { it.isComposableAnnotation() && it.useSiteTarget?.text == "get" } ||
                getter?.annotationEntries?.any { it.isComposableAnnotation() } == true
        }

        else -> {
            hasComposableAnnotation()
        }
    }

internal fun KtAnnotated.hasComposableAnnotation(): Boolean = annotationEntries.any { it.isComposableAnnotation() }

/**
 * Whether [this] is `@Composable`, written by its own name or, unqualified, by an alias the file imports it
 * under. A class declared under the alias's name shadows the import inside the class that declares it, or
 * that declares the companion that does. A top-level class does not: the import wins over it.
 */
private fun KtAnnotationEntry.isComposableAnnotation(): Boolean {
    val name = shortName?.asString() ?: return false
    return name == "Composable" ||
        (
            typeReference?.text == name &&
                containingKtFile.importDirectives.any {
                    it.aliasName == name && it.importPath?.fqName?.asString() == "androidx.compose.runtime.Composable"
                } &&
                !declaresClassNamed(name)
        )
}

private fun KtAnnotationEntry.declaresClassNamed(name: String): Boolean =
    parents
        .filterIsInstance<KtClassOrObject>()
        .flatMap { owner -> owner.declarations + owner.companionObjects.flatMap { it.declarations } }
        .any { it is KtClassOrObject && it.name == name }

/**
 * Whether a caller outside this library can reach [this]: neither it nor any declaration around it is
 * private or internal, and it is not local.
 *
 * A local property cannot declare a getter, so a property that reaches this check is never local;
 * locality is checked on a function alone.
 */
internal fun KtCallableDeclaration.isReachableFromOutside(): Boolean =
    (this !is KtNamedFunction || !isLocal) &&
        !isHiddenFromOutside() &&
        parents.filterIsInstance<KtClassOrObject>().none { it.isLocal || it.isHiddenFromOutside() }

private fun KtModifierListOwner.isHiddenFromOutside(): Boolean =
    hasModifier(KtTokens.PRIVATE_KEYWORD) || hasModifier(KtTokens.INTERNAL_KEYWORD)

/**
 * Whether [this] answers for a signature it did not choose, which a default cannot be added to without
 * changing what it implements.
 */
internal fun KtModifierListOwner.isImplementingSomething(): Boolean =
    hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
        hasModifier(KtTokens.ACTUAL_KEYWORD) ||
        hasModifier(KtTokens.ABSTRACT_KEYWORD)

internal fun KtParameter.declaresSwingModifier(): Boolean = typeReference.declaresSwingModifier()

/** Whether [this] names the companion of the type [fullyQualifiedName], bare or qualified with `.Companion`. */
internal fun KtExpression.isLibraryCompanionDefault(fullyQualifiedName: String): Boolean {
    val expression = KtPsiUtil.deparenthesize(this)
    val companionName =
        if (expression is KtDotQualifiedExpression && expression.selectorExpression?.text == "Companion") {
            KtPsiUtil.deparenthesize(expression.receiverExpression)
        } else {
            expression
        }
    return when (companionName) {
        is KtDotQualifiedExpression -> companionName.text == fullyQualifiedName
        is KtNameReferenceExpression -> companionName.isLibraryCompanionValue(fullyQualifiedName)
        else -> false
    }
}

private fun missingDefaultMessage(name: String?): String =
    "The `$name` parameter has no default. A caller that has no modifiers " +
        "to apply must still pass one. Add `= $SWING_MODIFIER` as the default."

private fun wrongDefaultMessage(
    name: String?,
    defaultText: String,
): String =
    "The `$name` parameter defaults to `$defaultText` instead of the empty " +
        "modifier. Every caller that leaves it out gets these modifiers without asking for " +
        "them. Default it to the `$SWING_MODIFIER` companion."
