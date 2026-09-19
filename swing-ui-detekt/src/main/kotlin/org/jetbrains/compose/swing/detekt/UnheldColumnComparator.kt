package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Reports a `comparator` argument to a table `column` or `addColumn` call that is built where the column
 * is declared: a lambda, an object expression, or a call other than Compose runtime `remember`.
 *
 * A column's comparator is compared by identity, so a new instance on every pass has the table sort its
 * rows again on each of them, at a cost that grows with the number of rows. Hold one instance instead: a
 * top-level or member value, or a `remember` in the composable that declares the table.
 *
 * The argument is found by its name, `comparator`, rather than by the parameter it fills: a positional
 * argument is passed over, since telling it apart from `column`'s other positional arguments takes the
 * parameter's declared type.
 */
public class UnheldColumnComparator(
    config: Config,
) : Rule(config, "A table column's comparator should be held across passes, not built where the column is declared.") {
    override val ruleName: RuleName = RuleName("UnheldColumnComparator")

    private companion object {
        val COLUMN_CALL_NAMES: Set<String> = setOf("column")
        const val TABLE_PACKAGE: String = "org.jetbrains.compose.swing.components.selection"
        const val TABLE_FQN: String = "$TABLE_PACKAGE.Table"
        const val TABLE_NAME: String = "Table"
        const val COMPARATOR_ARGUMENT_NAME: String = "comparator"
        const val COMPOSE_RUNTIME_PACKAGE: String = "androidx.compose.runtime"
        const val COMPOSE_REMEMBER_FQN: String = "$COMPOSE_RUNTIME_PACKAGE.remember"
        const val REMEMBER_CALL_NAME: String = "remember"
        const val RUN_CALL_NAME: String = "run"
        const val MESSAGE: String =
            "This comparator is built anew on every pass, so the table sorts its rows again on each of them. " +
                "Hold one instance instead: a top-level or member value, or a `remember` in the composable that " +
                "declares the table."
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.isTableColumnCall()) return
        val comparator = expression.namedComparatorArgument()?.takeIf { it.buildsANewComparator() } ?: return
        report(
            Finding(
                entity = Entity.from(comparator),
                message = MESSAGE,
            ),
        )
    }

    private fun KtCallExpression.isTableColumnCall(): Boolean {
        val calleeName = calleeExpression?.text.orEmpty()
        val qualifier = (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression === this }
        val hasUnknownReceiver =
            qualifier?.receiverExpression?.let { it !is KtThisExpression || it.getLabelName() != null } == true
        if (calleeName !in COLUMN_CALL_NAMES || isShadowedByCallable(calleeName) || hasUnknownReceiver) {
            return false
        }
        val lambdas = parents.filterIsInstance<KtLambdaExpression>().toList()
        val tableLambda = lambdas.firstOrNull { it.owningCall()?.isTableCall() == true }
        return tableLambda != null &&
            lambdas.takeWhile { it !== tableLambda }.all { it.owningCall()?.isTransparentRunCall() == true }
    }

    private fun KtLambdaExpression.owningCall(): KtCallExpression? =
        parents.filterIsInstance<KtCallExpression>().firstOrNull()

    /** A known scope-preserving lambda hop that leaves the table receiver available. */
    private fun KtCallExpression.isTransparentRunCall(): Boolean {
        val calleeName = calleeExpression?.text ?: return false
        val qualifier = (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression === this }
        val file = containingFile as? KtFile
        val exactImports =
            file?.importDirectives?.filter { !it.isAllUnder && it.importedName() == calleeName }.orEmpty()
        val hasOtherWildcard =
            file?.importDirectives?.any {
                it.isAllUnder && it.importPath?.pathStr?.removeSuffix(".*") != "kotlin"
            } == true
        return (calleeName == RUN_CALL_NAME && qualifier?.receiverExpression?.text == "kotlin") ||
            (
                qualifier == null &&
                    !isShadowedByCallable(calleeName) &&
                    (
                        exactImports.singleOrNull()?.importPath?.pathStr == "kotlin.run" ||
                            (calleeName == RUN_CALL_NAME && exactImports.isEmpty() && !hasOtherWildcard)
                    )
            )
    }

    private fun KtCallExpression.isTableCall(): Boolean {
        val calleeName = calleeExpression?.text.orEmpty()
        val qualifiedParent = (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression === this }
        return calleeName.isNotEmpty() &&
            !isShadowedByCallable(calleeName) &&
            if (qualifiedParent != null) {
                qualifiedParent.receiverExpression.text == TABLE_PACKAGE && calleeName == TABLE_NAME
            } else {
                (containingFile as? KtFile)?.resolvesImportedName(calleeName, TABLE_FQN) == true
            }
    }

    /** Whether a same-file callable makes a PSI-only call target ambiguous. */
    private fun KtCallExpression.isShadowedByCallable(name: String): Boolean {
        val file = containingFile as? KtFile
        return file
            ?.declarations
            ?.filterIsInstance<KtNamedDeclaration>()
            ?.any { it.name == name } == true ||
            parents.filterIsInstance<KtClassOrObject>().any { owner ->
                owner.declarations.filterIsInstance<KtNamedDeclaration>().any { it.name == name }
            } ||
            parents.filterIsInstance<KtBlockExpression>().any { block ->
                block.statements.filterIsInstance<KtNamedDeclaration>().any {
                    it.name == name && (it is KtNamedFunction || it.startOffset < startOffset)
                }
            }
    }

    /**
     * The expression [this] call's `comparator` argument is passed with, found by the argument's name since
     * no declared type is available to tell the parameter apart from `column`'s other, positional ones.
     */
    private fun KtCallExpression.namedComparatorArgument(): KtExpression? =
        valueArguments
            .firstOrNull { it.getArgumentName()?.asName?.identifier == COMPARATOR_ARGUMENT_NAME }
            ?.getArgumentExpression()

    /**
     * Whether [this] hands over a comparator built anew each time it is evaluated: a lambda, an object, or a
     * call other than Compose runtime `remember`. A local name is followed to its initializer; parameters,
     * members and top-level values outlive the pass and are left alone.
     */
    private fun KtExpression.buildsANewComparator(): Boolean =
        when (val expression = KtPsiUtil.deparenthesize(this)) {
            is KtLambdaExpression -> {
                true
            }

            is KtObjectLiteralExpression -> {
                true
            }

            is KtQualifiedExpression -> {
                (expression.selectorExpression as? KtCallExpression)?.buildsANewComparator() ==
                    true
            }

            is KtCallExpression -> {
                !expression.isComposeRemember()
            }

            is KtNameReferenceExpression -> {
                expression.localInitializer()?.buildsANewComparator() == true
            }

            else -> {
                false
            }
        }

    private fun KtCallExpression.isComposeRemember(): Boolean {
        val calleeName = calleeExpression?.text ?: return false
        val qualifier = (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression === this }
        return if (qualifier != null) {
            calleeName == REMEMBER_CALL_NAME && qualifier.receiverExpression.text == COMPOSE_RUNTIME_PACKAGE
        } else {
            !isShadowedByCallable(calleeName) &&
                (containingFile as? KtFile)?.resolvesImportedName(calleeName, COMPOSE_REMEMBER_FQN) == true
        }
    }

    /**
     * What the nearest preceding local property visible to [this] was declared with; `null` where the name
     * instead belongs to a parameter, a member or a top-level value, none of which are rebuilt with the
     * function that holds this reference. A selector on a receiver, such as a member property, is never a
     * local and is not followed by name at all.
     */
    private fun KtNameReferenceExpression.localInitializer(): KtExpression? {
        val name = getReferencedName()
        return getStrictParentOfType<KtNamedFunction>()
            ?.collectDescendantsOfType<KtProperty> {
                it.isLocal && it.name == name && it.startOffset < startOffset && it.isVisibleAt(this)
            }?.maxByOrNull { it.startOffset }
            ?.initializer
    }

    private fun KtProperty.isVisibleAt(reference: KtNameReferenceExpression): Boolean =
        reference.parents.any { it === parent }
}
