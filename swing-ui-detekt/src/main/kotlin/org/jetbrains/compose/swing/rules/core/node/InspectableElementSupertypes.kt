// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.node

import org.jetbrains.compose.swing.rules.core.modifier.SELF_DESCRIPTION
import org.jetbrains.compose.swing.rules.core.modifier.SWING_MODIFIER_FQN
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject

private val SWING_MODIFIER_CLASS_ID: ClassId = ClassId.topLevel(FqName(SWING_MODIFIER_FQN))

/**
 * Whether the library's `SwingModifier.InspectableElement` is among the supertypes of [this], at any depth,
 * and [this] is not a `SwingModifier.Node`.
 *
 * A supertype is matched by what it resolves to, not by how it is written, so an import by simple name, an
 * import alias and a base class that implements the interface all count. A supertype that does not resolve
 * never matches.
 *
 * An element describes a modifier to a tool; a node holds the state of a modifier attached to a component. A
 * node may still implement the interface - a layout node implements `ParentLayoutElement` so that a parent
 * reads the declaration from the node - but the element that creates it is what a tool shows, so a node is
 * not one to describe itself.
 */
internal fun KtClassOrObject.isResolvedInspectableElement(): Boolean =
    analyze(this) {
        val type = (this@isResolvedInspectableElement.symbol as? KaClassSymbol)?.defaultType
        type?.isSubtypeOf(SWING_MODIFIER_CLASS_ID.createNestedClassId(Name.identifier("InspectableElement"))) == true &&
            !type.isSubtypeOf(SWING_MODIFIER_CLASS_ID.createNestedClassId(Name.identifier("Node")))
    }

/**
 * Whether a supertype of [this] other than a type nested in `SwingModifier` overrides `name` or
 * `declaredValues`, which [this] then inherits.
 *
 * The types nested in `SwingModifier` are not consulted: `NodeElement` overrides both with the defaults the
 * rule exists to find, so counting its overrides would clear every element.
 */
internal fun KtClassOrObject.inheritsSelfDescription(): Boolean =
    analyze(this) {
        (this@inheritsSelfDescription.symbol as? KaClassSymbol)
            ?.defaultType
            ?.allSupertypes
            .orEmpty()
            .mapNotNull { (it as? KaClassType)?.symbol as? KaClassSymbol }
            .filter { it.classId?.outerClassId != SWING_MODIFIER_CLASS_ID }
            .any { supertype ->
                SELF_DESCRIPTION.any { member ->
                    supertype.declaredMemberScope
                        .callables(Name.identifier(member))
                        .any {
                            it is KaPropertySymbol && it.receiverParameter == null &&
                                it.directlyOverriddenSymbols.any()
                        }
                }
            }
    }
