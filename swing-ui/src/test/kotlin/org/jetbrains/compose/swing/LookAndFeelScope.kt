package org.jetbrains.compose.swing

import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * Runs [body] with Metal installed, so what a look and feel has installed onto a component is known,
 * and puts the host's own back afterwards. [body] is free to install a further look and feel of its own;
 * a component keeps whatever was written onto it, so what [body] measured outlives the restore.
 *
 * A look and feel is process-wide, so leaving one installed would decide what every later test in this
 * JVM measures.
 */
internal inline fun <R> underMetal(body: () -> R): R {
    val hostLookAndFeel = UIManager.getLookAndFeel()
    UIManager.setLookAndFeel(MetalLookAndFeel())
    try {
        return body()
    } finally {
        UIManager.setLookAndFeel(hostLookAndFeel)
    }
}

/**
 * Runs [body] with the look-and-feel default [key] answering [value], so a value a component takes only
 * from its look and feel can be chosen for the measurement, and drops the choice afterwards so the
 * installed look and feel answers for [key] again.
 *
 * Look-and-feel defaults are process-wide, so leaving one chosen would decide what every later test in
 * this JVM measures. The choice overrides the installed look and feel's own answer, so it is made where
 * no other choice for [key] is in force.
 */
internal inline fun <R> withLookAndFeelDefault(
    key: String,
    value: Any,
    body: () -> R,
): R {
    UIManager.put(key, value)
    try {
        return body()
    } finally {
        UIManager.put(key, null)
    }
}

/**
 * Runs [body] with the installed look and feel naming no answer for [key], so what a component does
 * where its look and feel has none to give can be measured, and names it again afterwards.
 *
 * The answer is dropped where the installed look and feel keeps it, which is the only place one can be
 * taken away rather than overridden. That table is process-wide, so leaving [key] dropped would decide
 * what every later test in this JVM measures.
 */
internal inline fun <R> withoutLookAndFeelDefault(
    key: String,
    body: () -> R,
): R {
    val defaults = UIManager.getLookAndFeelDefaults()
    val named = defaults[key]
    defaults[key] = null
    try {
        return body()
    } finally {
        defaults[key] = named
    }
}
