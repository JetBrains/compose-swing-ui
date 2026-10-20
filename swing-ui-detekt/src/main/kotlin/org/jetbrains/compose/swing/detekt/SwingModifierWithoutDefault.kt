package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.lexer.KtTokens
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
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Reports a composable whose `SwingModifier` parameter does not default to the empty chain.
 *
 * A caller that has nothing to declare should not have to say so. The parameter defaults to the empty
 * chain, as `Modifier` does in Compose, and every wrapper in this library is written that way.
 *
 * Only a declaration a caller outside its own file can reach is checked. A private helper is always
 * called with a chain by the composable that owns it, and a default there would stand for nothing.
 */
public class SwingModifierWithoutDefault(
    config: Config,
) : Rule(config, "A composable's SwingModifier parameter should default to the empty chain.") {
    override val ruleName: RuleName = RuleName("SwingModifierWithoutDefault")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.isComposable() || !function.isReachableFromOutside() || function.isImplementingSomething()) {
            return
        }
        function.valueParameters.filter { it.declaresSwingModifier() }.forEach(::reportUnlessDefaultsToTheEmptyChain)
    }

    private fun reportUnlessDefaultsToTheEmptyChain(parameter: KtParameter) {
        val default = parameter.defaultValue
        if (default == null) {
            report(
                Finding(
                    entity = Entity.from(parameter),
                    message =
                        "The `${parameter.name}` parameter has no default. Default it to `$SWING_MODIFIER` so a " +
                            "caller with nothing to declare can leave it out.",
                ),
            )
            return
        }
        if (default.isLibraryCompanionDefault(SWING_MODIFIER_FQN)) return
        report(
            Finding(
                entity = Entity.from(default),
                message =
                    "The `${parameter.name}` parameter defaults to `${default.text}`, not the empty chain. " +
                        "Default it to `$SWING_MODIFIER` so a caller with nothing to declare inherits nothing " +
                        "it did not ask for.",
            ),
        )
    }
}

internal const val SWING_MODIFIER: String = "SwingModifier"

internal fun KtCallableDeclaration.isComposable(): Boolean =
    when (this) {
        is KtProperty -> {
            annotationEntries.any {
                it.shortName?.asString() == "Composable" && it.useSiteTarget?.text == "get"
            } ||
                getter?.annotationEntries?.any { it.shortName?.asString() == "Composable" } == true
        }

        else -> {
            annotationEntries.any { it.shortName?.asString() == "Composable" }
        }
    }

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
