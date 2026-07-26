package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Stable
import javax.swing.JRootPane

/**
 * The receiver of the content of a [Window] and of a [Dialog]: the window that content fills.
 *
 * What a window carries besides its content is declared on this scope, so such a declaration is only
 * available where there is a window to carry it. [MenuBar] is one: it declares the menu bar of the
 * window whose content it is composed in, and a call to it anywhere else does not compile.
 *
 * A scope is received, never made: every value of this type is one a window handed its content, so a
 * declaration made on it reaches the window that content is in and no other.
 */
@Stable
public class WindowScope private constructor(
    /**
     * The root pane of the window this is the scope of, the library's own handle on that window, held for
     * the menu bar's sake.
     *
     * The getter is synthetic on the JVM. Kotlin gives an `internal` member of a class a mangled JVM name
     * but public bytecode access, and a mangled name is still one javac resolves, so the annotation is what
     * keeps the window a scope stands for out of a Java caller's reach.
     */
    @get:JvmSynthetic
    internal val rootPane: JRootPane,
) {
    internal companion object {
        /**
         * The scope the window rooted at [rootPane] hands its content.
         *
         * Synthetic on the JVM for the same reason as the pane it stands the scope over: an `internal`
         * companion object is a public class in the bytecode and keeps the names of its members, so without
         * the annotation this would be a way to a scope over a pane belonging to no window.
         */
        @JvmSynthetic
        fun of(rootPane: JRootPane): WindowScope = WindowScope(rootPane)
    }
}
