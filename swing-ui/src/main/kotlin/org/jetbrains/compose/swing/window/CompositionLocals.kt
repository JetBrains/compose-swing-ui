@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import java.awt.Window

/**
 * The owning AWT [Window] of the current subtree.
 *
 * [Window] provides the backing [javax.swing.JFrame] here around its content, so any descendant can
 * read the top-level window it lives in. This is the parent used when opening modal dialogs or other
 * top-level peers that must be anchored to (and stay in front of) the window that owns them.
 *
 * The value is `null` under a bare `application { }` scope that has not (yet) created any [Window],
 * so consumers must handle the absence of an owning window.
 */
public val LocalWindow: ProvidableCompositionLocal<Window?> = compositionLocalOf { null }
