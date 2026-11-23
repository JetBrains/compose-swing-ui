/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Ported from androidx's RestrictToDetector (frameworks/support/lint-checks/src/main/java/androidx/build/
// lint/RestrictToDetector.kt, `checkRestrictTo`), an Android Lint SourceCodeScanner, to a rule of this library.
// This port does not have Lint's UAST view of Kotlin, so the following are substituted or dropped rather
// than reproduced:
//  - Scope.LIBRARY, Scope.LIBRARY_GROUP and Scope.LIBRARY_GROUP_PREFIX: decided as upstream does, by the
//    Maven coordinates of the reference and of the declaration, in the same if/else-if order (RestrictToLibrary
//    in ModuleBoundary.kt, with upstream's `sameLibraryGroupPrefix`). Lint's `getLibrary(node) ?: project
//    .mavenCoordinate` is the analyzed module's coordinates and `findMavenCoordinate(member)` is the
//    coordinates of the compiled library that holds the declaration. Detekt has no Gradle model, so the build
//    supplies both through this rule's configuration (`moduleCoordinates`, `libraryCoordinates`; see
//    LibraryCoordinates). Coordinates the
//    build did not supply are unknown, which is upstream's null: they are never reported. A declaration in
//    source is the analyzed module's own, so upstream's "source of another Gradle project" branch of
//    Scope.LIBRARY has no counterpart.
//  - Scope.TESTS: decided by whether the reference's file path sits under a test source root, plus the
//    same enclosing-declaration walk upstream performs for `@VisibleForTesting`/`@TestOnly`, and for an
//    enclosing declaration that itself carries `@RestrictTo(TESTS)` (see RestrictedApiAccess.kt).
//  - Scope.SUBCLASSES: ported using the Analysis API's `isSubClassOf`, walking enclosing Kotlin classes
//    the way upstream walks enclosing (non-static) Java classes. Kotlin's inner/non-inner distinction
//    (plus anonymous object literals) stands in for Java's non-static/static one.
//  - The annotation lookup (upstream's `anyCloser` search over Lint's generic `AnnotationUsageInfo`
//    chain, with `inheritAnnotation = false`) is ported as a walk from the resolved declaration outward
//    through its containing declarations, stopping at the first `@RestrictTo`; it never looks at
//    supertypes. For a property access, the accessors the expression invokes (getter for a read, setter
//    for a write, both for a read-modify-write) are checked before the property itself, and the property's
//    backing field after it, because `@RestrictTo` has no property target and an annotation without a
//    use-site target lands on the field. This reproduces upstream's `@get:`/`@set:`/plain/`@field:`
//    handling in `test169610406`, except that a compiled field carries the same annotation whether it was
//    written plain or as `@field:`, so a compiled `@field:RestrictTo` property is reported like a plain one.
//  - Upstream's UAST-specific relocation of the report location/name onto a qualifier, and its
//    "no scope" (bare internal) message, are dead code paths in the androidx variant - every
//    `@RestrictTo` usage there carries a scope - and are not ported.
//  - Upstream's message says a Kotlin property was "called" (it sees the JVM getter/setter through
//    UAST). This substrate resolves the property itself, so the message says "accessed" instead; the
//    member name is the Kotlin property name (`foo1`), not the synthetic accessor name (`getFoo1`).

package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationList
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.nameOrAnonymous
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.jvm.JvmClassName

/**
 * Reports a use of an API that `@RestrictTo` restricts to a scope the use does not meet.
 * The rule ports androidx's RestrictTo check to this library's rule API.
 */
internal object RestrictedApi : ComposeSwingRule {
    override val id: String = "RestrictedApi"
    override val description: String = "This API has been flagged with a restriction that has not been met."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val libraries = LibraryCoordinates.from(config)
        return object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (expression.getStrictParentOfType<KtImportDirective>() != null ||
                    expression.getStrictParentOfType<KtPackageDirective>() != null
                ) {
                    return
                }
                val messages = analyze(expression) { restrictionMessages(expression, libraries) }
                messages.forEach { reporter.report(expression, it) }
            }

            override fun visitNamedFunction(function: KtNamedFunction) =
                reportRestrictedOverride(function, reporter, libraries)

            override fun visitProperty(property: KtProperty) = reportRestrictedOverride(property, reporter, libraries)
        }
    }
}

private fun KaSession.restrictionMessages(
    expression: KtSimpleNameExpression,
    libraries: LibraryCoordinates,
): List<String> {
    val symbol = expression.mainReference.resolveToSymbol() as? KaDeclarationSymbol ?: return emptyList()
    return restrictionMessages(symbol, expression, declaredRestrictionScope(symbol, expression), libraries)
}

internal fun KaSession.restrictionMessages(
    symbol: KaDeclarationSymbol,
    expression: KtElement,
    scope: Int,
    libraries: LibraryCoordinates,
): List<String> {
    val declaringClass = symbol as? KaClassSymbol ?: symbol.containingDeclaration as? KaClassSymbol
    val restrictions = violatedRestrictions(symbol, expression, declaringClass, scope, libraries)
    val api = apiName(symbol, declaringClass)
    val refType = if (symbol is KaFunctionSymbol) "called" else "accessed"
    return if (api == null) emptyList() else restrictions.map { "$api can only be $refType $it" }
}

internal fun KaSession.apiName(
    symbol: KaDeclarationSymbol,
    declaringClass: KaClassSymbol?,
): String? {
    val className = declaringClass?.nameOrAnonymous?.asString()
    val memberName = (symbol as? KaNamedSymbol)?.name?.asString()
    return when {
        className == null -> memberName
        symbol is KaClassSymbol || memberName == null -> className
        else -> "$className.$memberName"
    }
}

private fun KaSession.violatedRestrictions(
    symbol: KaDeclarationSymbol,
    expression: KtElement,
    declaringClass: KaClassSymbol?,
    scope: Int,
    libraries: LibraryCoordinates,
): List<String> =
    buildList {
        libraryRestriction(scope, libraries.module) { declaringCoordinates(symbol, libraries) }?.let(::add)
        if (scope and SCOPE_TESTS != 0 && !isTestContext(expression)) add("from tests")
        if (scope and SCOPE_SUBCLASSES != 0 && declaringClass?.classId != null &&
            !isWithinSubclass(expression, declaringClass)
        ) {
            add("from subclasses")
        }
    }

private const val SCOPE_LIBRARY_GROUP = 1 shl 0
private const val SCOPE_LIBRARY = 1 shl 1
private const val SCOPE_LIBRARY_GROUP_PREFIX = 1 shl 2
internal const val SCOPE_TESTS = 1 shl 3
private const val SCOPE_SUBCLASSES = 1 shl 4

internal fun KaAnnotationList.restrictToScope(): Int? {
    val annotation = this[ClassId.topLevel(FqName("androidx.annotation.RestrictTo"))].firstOrNull() ?: return null
    return annotation.arguments
        .firstOrNull { it.name.asString() == "value" }
        ?.expression
        ?.let(::scopeBits) ?: 0
}

private fun scopeBits(value: KaAnnotationValue): Int =
    when (value) {
        is KaAnnotationValue.ArrayValue -> {
            value.values.fold(0) { bits, item -> bits or scopeBits(item) }
        }

        is KaAnnotationValue.EnumEntryValue -> {
            when (value.callableId?.callableName?.asString()) {
                "GROUP_ID", "LIBRARY_GROUP" -> SCOPE_LIBRARY_GROUP
                "LIBRARY" -> SCOPE_LIBRARY
                "LIBRARY_GROUP_PREFIX" -> SCOPE_LIBRARY_GROUP_PREFIX
                "TESTS" -> SCOPE_TESTS
                "SUBCLASSES" -> SCOPE_SUBCLASSES
                else -> 0
            }
        }

        else -> {
            0
        }
    }

/** Upstream's if/else-if chain over the library scopes; the declaration's coordinates are read only if one applies. */
private fun libraryRestriction(
    scope: Int,
    thisCoordinates: MavenCoordinate?,
    methodCoordinates: () -> MavenCoordinate?,
): String? =
    when {
        scope and SCOPE_LIBRARY_GROUP != 0 -> {
            RestrictToLibrary.libraryGroup(thisCoordinates, methodCoordinates())
        }

        scope and SCOPE_LIBRARY_GROUP_PREFIX != 0 -> {
            RestrictToLibrary.libraryGroupPrefix(thisCoordinates, methodCoordinates())
        }

        scope and SCOPE_LIBRARY != 0 -> {
            RestrictToLibrary.library(thisCoordinates, methodCoordinates())
        }

        else -> {
            null
        }
    }

/** Upstream's `findMavenCoordinate(member)`: the library holding a compiled declaration, else the analyzed module. */
@OptIn(KaExperimentalApi::class)
private fun KaSession.declaringCoordinates(
    symbol: KaDeclarationSymbol,
    libraries: LibraryCoordinates,
): MavenCoordinate? {
    val declaration = (symbol as? KaCallableSymbol)?.fakeOverrideOriginal ?: symbol
    val compiled = declaration.origin == KaSymbolOrigin.LIBRARY || declaration.origin == KaSymbolOrigin.JAVA_LIBRARY
    val enclosing = generateSequence(declaration) { it.containingDeclaration }
    val classId = enclosing.filterIsInstance<KaClassSymbol>().firstOrNull()?.classId
    val facade = enclosing.filterIsInstance<KaCallableSymbol>().firstOrNull()?.containingJvmClassName
    val jvmClass = classId?.let(JvmClassName::byClassId)?.internalName ?: facade?.replace('.', '/')
    val roots = (declaration.containingModule as? KaLibraryModule)?.binaryRoots.orEmpty()
    val declaringRoots = jvmClass?.let { ModuleBoundary.declaringRoots(roots, "$it.class") }.orEmpty()
    return if (compiled) libraries.of(declaringRoots) else libraries.module
}

private fun KaSession.isTestContext(expression: KtElement): Boolean {
    if (expression.isInTestSource() || isInsideTestRestrictedDeclaration(expression)) return true
    return generateSequence(expression.parent) { it.parent }
        .filterIsInstance<KtAnnotated>()
        .flatMap { it.annotationEntries }
        .any { isTestContextAnnotation(it) }
}

internal fun KaSession.isWithinSubclass(
    expression: KtElement,
    declaringClass: KaClassSymbol,
): Boolean =
    enclosingClassChain(expression).any { current ->
        val symbol = current.classSymbol
        symbol != null && (symbol.classId == declaringClass.classId || symbol.isSubClassOf(declaringClass))
    }

private fun enclosingClassChain(expression: KtElement): Sequence<KtClassOrObject> =
    generateSequence(expression.getStrictParentOfType<KtClassOrObject>()) { current ->
        val climbs =
            (current as? KtClass)?.isInner() == true || (current as? KtObjectDeclaration)?.isObjectLiteral() == true
        if (climbs) current.getStrictParentOfType<KtClassOrObject>() else null
    }
