// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayInputStream
import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.swing.plaf.synth.SynthLookAndFeel

/**
 * Runs [body] with Metal installed, so what a look and feel has installed onto a component is known,
 * and puts the host's own back afterwards. [body] is free to install a further look and feel of its own;
 * a component keeps whatever was written onto it, so what [body] measured outlives the restore.
 *
 * A look and feel is process-wide, so leaving one installed would decide what every later test in this
 * JVM measures.
 */
internal inline fun <R> underMetal(body: () -> R): R {
    recordHostLookAndFeel()
    val hostLookAndFeel = UIManager.getLookAndFeel()
    try {
        UIManager.setLookAndFeel(MetalLookAndFeel())
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

/**
 * Runs [body] under a Synth look and feel loaded from [style], and puts the host's own back afterwards.
 *
 * Synth installs a widget property from its style table rather than from a look-and-feel default, so
 * `UIManager.get` answers nothing for one. Only a wrapper that reads the property back off its widget
 * answers correctly here.
 *
 * [style] is a Synth XML document.
 *
 * Skipped where the host's look and feel does not ship with the JDK. A Synth style names only what it
 * is asked to, and a look and feel outside the JDK may keep a listener on the defaults table that
 * outlives the switch and reads a value this table does not carry - which fails this test and leaves
 * the ones after it measuring a look and feel that has since read the wrong thing.
 */
internal inline fun <R> underSynth(
    style: String,
    body: () -> R,
): R {
    recordHostLookAndFeel()
    assumeTrue(
        hostLookAndFeelShipsWithTheJdk(),
        "a Synth look and feel is installed over the host's, which must be one that ships with the JDK",
    )
    val hostLookAndFeel = UIManager.getLookAndFeel()
    val synth = SynthLookAndFeel()
    synth.load(ByteArrayInputStream(style.toByteArray()), SynthLookAndFeel::class.java)
    try {
        UIManager.setLookAndFeel(synth)
        return body()
    } finally {
        UIManager.setLookAndFeel(hostLookAndFeel)
    }
}

/** The look and feel this JVM started under. [UIManager] stops answering with it once one of the
 * helpers here installs another over it. */
private var hostLookAndFeel: LookAndFeel? = null

/**
 * Records what is installed now as the host's, unless one of these helpers recorded it already. Called
 * before each of them installs one, while the host's is still there to be read.
 */
internal fun recordHostLookAndFeel() {
    if (hostLookAndFeel == null) hostLookAndFeel = UIManager.getLookAndFeel()
}

/**
 * Whether the look and feel this JVM started under ships with the JDK. One that does lives in the
 * java.desktop module, which the bootstrap loader loads, so its class names no loader at all.
 */
internal fun hostLookAndFeelShipsWithTheJdk(): Boolean =
    checkNotNull(hostLookAndFeel) { "the host look and feel is recorded before it is asked about" }
        .javaClass.classLoader == null
