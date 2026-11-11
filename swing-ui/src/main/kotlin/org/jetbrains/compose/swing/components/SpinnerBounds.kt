// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import java.math.BigDecimal
import java.math.BigInteger

/**
 * [bound] - a spinner's [name] - in the class of [value]. The model holds each bound as a `Comparable`,
 * and a concrete `Number` compares against its own class alone, so a bound left in another class throws
 * a `ClassCastException` out of the model, at construction or on the first step taken against it.
 *
 * A `BigInteger` or `BigDecimal` value is refused a bound outright: the model steps such a value by
 * boxing the sum as a `Byte`, which no bound of the value's own class can be compared against either.
 *
 * The conversion must be exact. A bound the value's class cannot hold is not the bound that was declared,
 * so it is refused rather than rounded into one the spinner would silently honor instead.
 */
internal fun boundInTheClassOf(
    value: Number,
    bound: Number?,
    name: String,
): Comparable<*>? {
    require(bound == null || (value !is BigInteger && value !is BigDecimal)) {
        "A spinner over a ${value.javaClass.name} value takes no bounds, but $name is $bound. " +
            "Declare the value as a Byte, Short, Int, Long, Float or Double to bound it."
    }
    if (bound == null || bound.javaClass == value.javaClass) return bound as Comparable<*>?
    return requireNotNull(boundHeldIn(value, bound)) {
        "A spinner's $name must be one the class of its value can hold, but $name is $bound " +
            "(${bound.javaClass.name}) under a ${value.javaClass.name} value."
    } as Comparable<*>
}

/**
 * [bound] in the class of [value], or `null` where that class cannot hold it exactly.
 *
 * The comparison is between numbers, not texts. A `Float` or a `Double` is the binary value it holds, not
 * the shortest decimal that round-trips it, so `0.1f` is carried into a `Double` as the number the `Float`
 * held, and the `Double` `0.1` - which no `Float` holds - is refused rather than narrowed. Exactness is
 * asked of `BigDecimal` through `compareTo`, since its own equality counts scale and holds `0.0` and `0`
 * to be different numbers.
 *
 * An infinite or `NaN` bound has no decimal to judge: a `Float` and a `Double` each hold either exactly,
 * and no whole-number class holds one at all.
 */
private fun boundHeldIn(
    value: Number,
    bound: Number,
): Number? {
    val declared =
        bound.toDouble().takeIf { it.isFinite() }?.let {
            if (bound is Float || bound is Double) BigDecimal(it) else BigDecimal(bound.toString())
        }
    val exact = { held: Number -> declared == null || BigDecimal(held.toDouble()).compareTo(declared) == 0 }
    return runCatching {
        when (value) {
            is Byte -> declared?.byteValueExact()
            is Short -> declared?.shortValueExact()
            is Int -> declared?.intValueExact()
            is Long -> declared?.longValueExact()
            is Float -> bound.toFloat().takeIf(exact)
            is Double -> bound.toDouble().takeIf(exact)
            else -> null
        }
    }.getOrNull()
}
