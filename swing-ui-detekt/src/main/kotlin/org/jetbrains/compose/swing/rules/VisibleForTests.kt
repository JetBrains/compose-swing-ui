// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertyAccessorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports a reference from non-test code to a member that `@VisibleForTesting` widened for tests, from outside the
 * visibility its `otherwise` argument names: the declaring class for `PRIVATE`, which is also the default, its package
 * for `PACKAGE_PRIVATE`, and the declaring class or a subclass for `PROTECTED`. `NONE` allows no production caller,
 * as `RestrictTo.Scope.TESTS` does. The declaring class is the one that contains the annotated declaration, so a
 * top-level declaration, a class among them, has none and its file stands in for it, and a member of a companion
 * object is also reachable from the class that holds the companion.
 *
 * Test sources and references from a declaration that is itself test code are exempt: one annotated
 * `VisibleForTesting` or `TestOnly` of any package, or `RestrictTo(TESTS)`. Only
 * `androidx.annotation.VisibleForTesting` restricts the member it annotates; another annotation of that simple name,
 * such as the one in `org.jetbrains.annotations`, states no such restriction.
 */
internal object VisibleForTests : ComposeSwingRule {
    override val id: String = "VisibleForTests"
    override val description: String =
        "This API is visible for testing only and is referenced beyond the visibility its annotation allows."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (expression.isExempt()) return
                val messages =
                    analyze(expression) {
                        if (isInsideTestCode(expression)) emptyList() else violationMessages(expression)
                    }
                messages.forEach { reporter.report(expression, it) }
            }
        }
}

private enum class Visibility(
    val otherwise: Int,
    val allowedFrom: String,
) {
    PRIVATE(otherwise = 2, allowedFrom = "tests or within private scope"),
    PACKAGE_PRIVATE(otherwise = 3, allowedFrom = "tests or within package private scope"),
    PROTECTED(otherwise = 4, allowedFrom = "tests or within protected scope"),
    NONE(otherwise = 5, allowedFrom = "tests"),
}

private fun KtSimpleNameExpression.isExempt(): Boolean {
    val inDirective =
        getStrictParentOfType<KtImportDirective>() != null || getStrictParentOfType<KtPackageDirective>() != null
    return inDirective || isInTestSource()
}

private fun KaSession.isInsideTestCode(element: KtElement): Boolean =
    isInsideTestRestrictedDeclaration(element) ||
        generateSequence(element.parent) { it.parent }
            .filterIsInstance<KtAnnotated>()
            .flatMap { it.annotationEntries }
            .any { isTestContextAnnotation(it) }

@OptIn(KaExperimentalApi::class)
private fun KaSession.violationMessages(expression: KtSimpleNameExpression): List<String> {
    val resolved = expression.mainReference.resolveToSymbol() as? KaDeclarationSymbol ?: return emptyList()
    val declaration = (resolved as? KaCallableSymbol)?.fakeOverrideOriginal ?: resolved
    val declaringClass = declaration as? KaClassSymbol ?: declaration.containingDeclaration as? KaClassSymbol
    val accessors = (declaration as? KaPropertySymbol)?.let { invokedAccessors(it, expression) } ?: listOf(null)
    return accessors
        .mapNotNull { declaredVisibility(declaration, it) }
        .filterNot { (visibility, owner) -> allows(visibility, declaration, owner, expression) }
        .map { (visibility, _) ->
            val api = apiName(declaration, declaringClass)
            "$api should only be accessed from ${visibility.allowedFrom}"
        }.distinct()
}

private fun KaSession.declaredVisibility(
    symbol: KaDeclarationSymbol,
    accessor: KaPropertyAccessorSymbol?,
): Pair<Visibility, KaClassSymbol?>? {
    val declarations =
        sequence {
            if (accessor != null) yield(accessor)
            yieldAll(generateSequence(symbol) { it.containingDeclaration })
        }
    val annotated = declarations.firstOrNull { it.annotations.any(::isVisibleForTesting) } ?: return null
    val holder = if (annotated is KaPropertyAccessorSymbol) symbol else annotated
    return visibilityOf(annotated.annotations.first(::isVisibleForTesting)) to
        holder.containingDeclaration as? KaClassSymbol
}

private fun isVisibleForTesting(annotation: KaAnnotation): Boolean =
    annotation.classId == ClassId.topLevel(FqName("androidx.annotation.VisibleForTesting"))

private fun visibilityOf(annotation: KaAnnotation): Visibility {
    val argument = annotation.arguments.firstOrNull { it.name.asString() == "otherwise" }?.expression
    val constant = ((argument as? KaAnnotationValue.ConstantValue)?.value as? KaConstantValue.IntValue)?.value
    return Visibility.entries.firstOrNull { it.otherwise == constant } ?: Visibility.PRIVATE
}

private fun KaSession.allows(
    visibility: Visibility,
    declaration: KaDeclarationSymbol,
    owner: KaClassSymbol?,
    expression: KtElement,
): Boolean =
    when (visibility) {
        Visibility.NONE -> {
            false
        }

        Visibility.PACKAGE_PRIVATE -> {
            packageName(declaration) == expression.containingKtFile.packageFqName
        }

        Visibility.PRIVATE -> {
            isWithin(declaration, owner, expression)
        }

        Visibility.PROTECTED -> {
            isWithin(declaration, owner, expression) || (owner != null && isWithinSubclass(expression, owner))
        }
    }

private fun KaSession.isWithin(
    declaration: KaDeclarationSymbol,
    owner: KaClassSymbol?,
    expression: KtElement,
): Boolean {
    if (owner == null) return declaration.psi?.containingFile == expression.containingFile
    val classId = owner.classId
    val outer = classId?.takeIf { owner.classKind == KaClassKind.COMPANION_OBJECT }?.outerClassId
    val owners = setOfNotNull(classId, outer)
    return generateSequence(expression.getStrictParentOfType<KtClassOrObject>()) {
        it.getStrictParentOfType<KtClassOrObject>()
    }.any { it.classSymbol?.classId in owners }
}

private fun KaSession.packageName(declaration: KaDeclarationSymbol): FqName? =
    generateSequence(declaration) { it.containingDeclaration }.firstNotNullOfOrNull {
        (it as? KaClassLikeSymbol)?.classId?.packageFqName ?: (it as? KaCallableSymbol)?.callableId?.packageName
    }
