package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Stable
import javax.swing.JRootPane

/**
 * The receiver of the content of a [Window] and of a [Dialog]: the window that content fills.
 *
 * What a window carries besides its content is declared on this scope, so such a declaration is only
 * available where there is a window to carry it. [MenuBar] and [GlassPane] are such declarations: each
 * reaches the window whose content it is composed in, and a call to either anywhere else does not
 * compile.
 *
 * A scope is received, never made: every value of this type is one a window handed its content, so a
 * declaration made on it reaches the window that content is in and no other.
 */
@Stable
public class WindowScope private constructor(
    /**
     * The root pane of the window this is the scope of, the library's own handle on that window, held for
     * the sake of what the window carries around its content: its menu bar and its glass pane.
     *
     * The getter is synthetic on the JVM. Kotlin gives an `internal` member of a class a mangled JVM name
     * but public bytecode access, and a mangled name is still one javac resolves, so the annotation is what
     * keeps the window a scope stands for out of a Java caller's reach.
     */
    @get:JvmSynthetic
    internal val rootPane: JRootPane,
) {
    /**
     * The [WindowDecoration] currently serving this window as its menu bar, or `null` while none does.
     *
     * A window carries one menu bar, so whether one is already declared is read directly off this field:
     * this scope is one instance for as long as the window exists, so the [MenuBar] composable finds its
     * own earlier declaration here without reading anything back off the Swing side.
     *
     * The getter and setter are synthetic on the JVM for the same reason [rootPane]'s getter is.
     */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var declaredMenuBar: WindowDecoration<*>? = null

    /**
     * The [WindowDecoration] currently serving this window as its glass pane, or `null` while none
     * does, the same way [declaredMenuBar] answers for the menu bar.
     */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var declaredGlassPane: WindowDecoration<*>? = null

    internal companion object {
        /**
         * The scope the window rooted at [rootPane] hands its content.
         *
         * Synthetic because an `internal` companion object is a public class in the bytecode and keeps
         * its members' names. The constructor is private, so without the annotation this would be a
         * Java caller's one way to a scope over a pane belonging to no window.
         */
        @JvmSynthetic
        fun of(rootPane: JRootPane): WindowScope = WindowScope(rootPane)
    }
}
