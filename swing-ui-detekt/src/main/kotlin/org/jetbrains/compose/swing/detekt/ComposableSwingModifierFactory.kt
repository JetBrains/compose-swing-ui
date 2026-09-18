package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Reports a `SwingModifier` factory marked `@Composable`.
 *
 * A chain is plain data. A factory that composes ties the value it builds to the composition that
 * called it, so the chain can no longer be hoisted, held across passes, or built anywhere but inside a
 * composable - and a caller who does hold it gets a value whose state belongs to a composition that has
 * since left.
 *
 * A factory that needs something remembered takes it: `remember` it at the call site, or state it as a
 * `remember*` function of its own that returns the value the plain factory then takes. State private to
 * each component the modifier reaches is built with `composed` instead.
 *
 * Because this rule is PSI-only, it checks factories with an explicit `SwingModifier` return type and
 * leaves inferred return types alone rather than guessing their semantic type.
 */
public class ComposableSwingModifierFactory(
    config: Config,
) : Rule(config, "A SwingModifier factory should be plain, not composable.") {
    override val ruleName: RuleName = RuleName("ComposableSwingModifierFactory")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        reportIfComposableFactory(function)
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (property.isFactoryCandidate()) reportIfComposableFactory(property)
    }

    private fun reportIfComposableFactory(declaration: KtCallableDeclaration) {
        if (!declaration.typeReference.declaresSwingModifier() || !declaration.isComposable()) return
        report(
            Finding(
                entity = Entity.from(declaration),
                message =
                    "`${declaration.name}` builds a $SWING_MODIFIER and is `@Composable`, which ties the chain " +
                        "to the composition that called it. Take what it needs to remember as a parameter, or build " +
                        "state private to each component with `composed`.",
            ),
        )
    }
}
