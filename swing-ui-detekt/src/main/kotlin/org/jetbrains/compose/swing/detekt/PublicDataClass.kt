// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports a `data class` that a consumer can see.
 *
 * The compiler generates `copy` and `componentN` from the primary constructor, and both are part of the
 * binary a consumer links against. A parameter added later changes their signatures, so code compiled
 * against the previous ones stops linking - a break that no source change to the class can avoid. A
 * regular class publishes the members it chooses and can gain a constructor parameter without touching
 * any of them.
 *
 * Explicit API mode requires a visibility keyword; it says nothing about `data`, so a public data class
 * compiles cleanly under it.
 *
 * A `data object` is left alone: it generates `equals`, `hashCode` and `toString` and no constructor-shaped
 * members, so there is nothing for a later parameter to change.
 *
 * A class is taken to be out of reach when it, or anything it is declared inside, is `private` or
 * `internal`. `protected` is reported, being the surface a subclass outside the module compiles against.
 */
internal class PublicDataClass(
    config: Config,
) : Rule(config, "A data class in the public API cannot gain a constructor parameter without breaking callers.") {
    override val ruleName: RuleName = RuleName("PublicDataClass")

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (!klass.isData() || klass.isLocal || !klass.isReachableFromOutside()) return
        report(
            Finding(
                entity = Entity.from(klass.nameIdentifier ?: klass),
                message = MESSAGE,
            ),
        )
    }
}

private const val MESSAGE: String =
    "This data class is part of the published API, so its generated `copy` and `componentN` are too. " +
        "Adding a constructor parameter later changes their signatures and breaks a caller compiled " +
        "against the previous ones. Declare a regular class carrying the members it means to publish."

/**
 * Whether a consumer outside this module can name [this]: neither it nor any class or object it is
 * declared inside is `private` or `internal`. A companion is a container like any other, so a data class
 * inside the companion of a public class is reachable.
 */
internal fun KtClassOrObject.isReachableFromOutside(): Boolean =
    generateSequence(this) { it.getStrictParentOfType<KtClassOrObject>() }
        .none { it.hasModifier(KtTokens.PRIVATE_KEYWORD) || it.hasModifier(KtTokens.INTERNAL_KEYWORD) }
