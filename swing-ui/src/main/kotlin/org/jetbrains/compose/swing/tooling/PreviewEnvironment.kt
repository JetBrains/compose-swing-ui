package org.jetbrains.compose.swing.tooling

/**
 * Prepares the JVM that renders this classpath's [Preview]s, so every one of them is rendered the way
 * the application would render it.
 *
 * A preview process starts as a bare JVM: no look and feel installed, the default scaling, and the
 * default locale. This is where those are settled, and it settles which look and feel every [Preview]
 * on the classpath renders under by default.
 *
 * An implementation is found through [java.util.ServiceLoader], so it needs a constructor that takes no
 * arguments and a `META-INF/services/org.jetbrains.compose.swing.tooling.PreviewEnvironment` entry
 * naming it. At most one may be on a preview's classpath; a second is an error rather than a winner.
 *
 * ```
 * class ApplicationPreviews : PreviewEnvironment {
 *     override fun prepare() {
 *         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
 *     }
 * }
 * ```
 */
public interface PreviewEnvironment {
    /**
     * Settles whatever the process needs before a preview is composed in it.
     *
     * Called once, before anything Swing is touched, on the thread the process started on, so a system
     * property that only takes effect before the toolkit initializes - `sun.java2d.uiScale` among them -
     * can still be set here.
     *
     * A [Preview] that states a look and feel of its own installs it after this returns, so an
     * annotation overrides what this settles rather than the other way round.
     *
     * A throw from here fails every preview on the classpath, and is reported in place of each rendering.
     */
    public fun prepare()
}
