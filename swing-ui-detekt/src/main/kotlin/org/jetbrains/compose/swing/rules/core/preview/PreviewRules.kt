// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.preview

import io.nlopez.compose.core.util.hasAnyContextArguments
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.core.hasComposableAnnotation
import org.jetbrains.compose.swing.rules.core.modifier.isComposable
import org.jetbrains.compose.swing.rules.psi.declaresLibraryType
import org.jetbrains.compose.swing.rules.psi.importedName
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.psiUtil.parents

internal object PreviewAnnotationNaming : ComposeSwingRule {
    override val id: String = "PreviewAnnotationNaming"
    override val description: String = "Preview annotation classes should be named appropriately"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                if (!klass.isAnnotation() || klass.name.orEmpty().startsWith("Preview")) return
                if (klass.hasSwingPreviewAnnotation()) {
                    reporter.report(klass.nameIdentifier ?: klass, PREVIEW_ANNOTATION_NAMING_MESSAGE)
                }
            }
        }
}

internal object PreviewNaming : ComposeSwingRule {
    override val id: String = "PreviewNaming"
    override val description: String = "Preview composables should be named appropriately"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = false
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val strategy = config.valueOrDefault("previewNamingStrategy", "suffix")
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                val name = function.nameAsSafeName.asString()
                val matches =
                    when (strategy) {
                        "prefix" -> name.startsWith("Preview")
                        "anywhere" -> name.contains("Preview")
                        else -> name.endsWith("Preview")
                    }
                if (!matches && function.isComposable() && function.hasSwingPreviewAnnotation()) {
                    reporter.report(function.nameIdentifier ?: function, previewNamingMessage(strategy))
                }
            }
        }
    }
}

internal object PreviewNeedsComposableAnnotation : ComposeSwingRule {
    override val id = "PreviewNeedsComposableAnnotation"
    override val description = "Swing preview functions must be composable"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.hasSwingPreviewAnnotation()) return
                if (!analyze(function) { function.symbol.hasComposableAnnotation() }) {
                    reporter.report(function.nameIdentifier ?: function, PREVIEW_NEEDS_COMPOSABLE_MESSAGE)
                }
            }
        }
}

internal object PreviewPublic : ComposeSwingRule {
    override val id: String = "PreviewPublic"
    override val description: String = "Preview composables should not be public"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (function.isPublic && function.isComposable() && function.hasSwingPreviewAnnotation()) {
                    reporter.report(function.nameIdentifier ?: function, PREVIEW_PUBLIC_MESSAGE)
                }
            }
        }
}

internal object PreviewSignature : ComposeSwingRule {
    override val id = "PreviewSignature"
    override val description = "Swing preview functions must be top-level and take no arguments"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (!function.hasSwingPreviewAnnotation()) return
                val message =
                    when {
                        !function.isTopLevel -> PREVIEW_TOP_LEVEL_MESSAGE
                        function.valueParameters.isNotEmpty() -> PREVIEW_VALUE_PARAMETERS_MESSAGE
                        function.receiverTypeReference != null -> PREVIEW_RECEIVER_MESSAGE
                        function.hasAnyContextArguments -> PREVIEW_CONTEXT_PARAMETERS_MESSAGE
                        else -> return
                    }
                reporter.report(function.nameIdentifier ?: function, message)
            }
        }
}

internal fun KtAnnotated.hasSwingPreviewAnnotation(): Boolean =
    annotationEntries.any { entry -> entry.resolvesToSwingPreview() ?: entry.namesSwingPreview(mutableSetOf()) }

@OptIn(KaExperimentalApi::class)
private fun KtAnnotationEntry.resolvesToSwingPreview(): Boolean? =
    runCatching {
        analyze(this) {
            val classId = (typeReference?.type?.fullyExpandedType as? KaClassType)?.classId ?: return@analyze null
            if (findClass(swingPreviewClassId) == null) return@analyze null
            isSwingPreview(classId, mutableSetOf())
        }
    }.getOrNull()

@OptIn(KaExperimentalApi::class)
private fun KaSession.isSwingPreview(
    classId: ClassId,
    visited: MutableSet<ClassId>,
): Boolean =
    classId == swingPreviewClassId ||
        (
            visited.add(classId) &&
                findClass(classId)?.annotations?.any { annotation ->
                    annotation.classId?.let { isSwingPreview(it, visited) } == true
                } == true
        )

private fun KtAnnotationEntry.namesSwingPreview(visited: MutableSet<KtClass>): Boolean {
    val type = typeReference?.typeElement as? KtUserType ?: return false
    return type.declaresLibraryType(SWING_PREVIEW_FQN) ||
        type.sameFileAnnotationClass()?.let { declaration ->
            declaration.fqName?.asString() == SWING_PREVIEW_FQN ||
                (visited.add(declaration) && declaration.annotationEntries.any { it.namesSwingPreview(visited) })
        } == true
}

private fun KtUserType.sameFileAnnotationClass(): KtClass? {
    val name = referencedName ?: return null
    val file = containingKtFile
    val scopes = parents.filterIsInstance<KtClassOrObject>().map { it.declarations } + sequenceOf(file.declarations)
    val declaration =
        scopes.firstNotNullOfOrNull { scope ->
            scope.filterIsInstance<KtClassOrObject>().singleOrNull { it.name == name }
        } as? KtClass
    return declaration?.takeIf { it.isAnnotation() && namesDeclaration(it) }
}

private fun KtUserType.namesDeclaration(declaration: KtClass): Boolean {
    val qualifiedName = declaration.fqName?.asString() ?: return false
    val name = referencedName
    val explicitImports = containingKtFile.importDirectives.filter { !it.isAllUnder && it.importedName() == name }
    return when {
        qualifier != null -> text == qualifiedName
        explicitImports.isNotEmpty() -> explicitImports.singleOrNull()?.importedFqName?.asString() == qualifiedName
        else -> true
    }
}

private const val SWING_PREVIEW_FQN = "org.jetbrains.compose.swing.tooling.Preview"
private val swingPreviewClassId = ClassId.topLevel(FqName(SWING_PREVIEW_FQN))

private const val PREVIEW_ANNOTATION_NAMING_MESSAGE =
    "This preview annotation's name lacks the `Preview` prefix. " +
        "A reusable annotation can apply several rendering variants to a composable. " +
        "The prefix makes that purpose visible where the annotation is used. " +
        "Rename the annotation to start with `Preview`."

private fun previewNamingMessage(strategy: String): String =
    "This preview composable's name does not match the configured placement of `Preview`. " +
        "A consistent marker identifies preview entry points when browsing composable functions. " +
        when (strategy) {
            "prefix" -> "Rename it to start with `Preview`."
            "anywhere" -> "Include `Preview` anywhere in the function name."
            else -> "Rename it to end with `Preview`."
        }

private const val PREVIEW_PUBLIC_MESSAGE =
    "This preview composable is public. " +
        "Public visibility exposes a tooling entry point to application callers, and the preview tool can render " +
        "private functions. Make the preview private. If application code also calls this composable, " +
        "move the preview annotation to a separate private function that calls it."

private const val PREVIEW_NEEDS_COMPOSABLE_MESSAGE =
    "This preview function is missing `@Composable`. Swing renders previews inside a composition. " +
        "Annotate the function with `@Composable` from Compose Runtime."

private const val PREVIEW_TOP_LEVEL_MESSAGE =
    "This preview function is declared inside another declaration. The preview renderer looks for top-level " +
        "functions in the Kotlin file. Move the preview to the top level or call the composable from a top-level preview."

private const val PREVIEW_VALUE_PARAMETERS_MESSAGE =
    "This preview function declares parameters. Swing previews call the function without arguments. " +
        "Move the preview annotation to a parameterless composable that supplies the values."

private const val PREVIEW_RECEIVER_MESSAGE =
    "This preview function has an extension receiver. The preview renderer has no receiver to pass. " +
        "Call the extension from a separate preview that provides the receiver."

private const val PREVIEW_CONTEXT_PARAMETERS_MESSAGE =
    "This preview function requires context arguments. The preview renderer has no context arguments to pass. " +
        "Provide the context inside a separate preview and call the composable from there."
