// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
 * A chain is plain data, built in place wherever it is declared. A factory that composes ties the value
 * it builds to the composition that called it, so the chain can be built only inside a composable, and
 * its state belongs to that composition.
 *
 * A factory that needs something remembered takes it: `remember` it at the call site, or state it as a
 * `remember*` function of its own that returns the value the plain factory then takes. State private to
 * each component the modifier reaches belongs in a `ComponentNode` instead.
 *
 * Because this rule is PSI-only, it checks factories with an explicit `SwingModifier` return type and
 * leaves inferred return types alone rather than guessing their semantic type.
 */
public class ComposableSwingModifierFactory(
    config: Config,
) : Rule(config, "A `SwingModifier` factory should not be `@Composable`") {
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
                message = composableSwingModifierFactoryMessage(declaration.name),
            ),
        )
    }
}

private fun composableSwingModifierFactoryMessage(name: String?): String =
    "`$name` is a `$SWING_MODIFIER` factory marked `@Composable`. " +
        "What it remembers belongs to the composable that calls it, " +
        "not to the component the modifier reaches. " +
        "Remove `@Composable`, and let the caller create remembered values and pass them in. " +
        "Keep state for each component in a `$SWING_MODIFIER.ComponentNode` " +
        "that a `$SWING_MODIFIER.NodeElement` creates."
