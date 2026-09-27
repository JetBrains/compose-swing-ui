package org.jetbrains.compose.swing.preview.host

import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.UnsupportedLookAndFeelException

/** Installs [className], or restores [prepared] where a rendering asks for no look and feel of its own. */
internal fun installLookAndFeel(
    className: String,
    prepared: LookAndFeel?,
) {
    if (className.isEmpty()) {
        if (UIManager.getLookAndFeel() !== prepared) UIManager.setLookAndFeel(prepared)
        return
    }
    if (UIManager.getLookAndFeel()?.javaClass?.name == className) return
    try {
        UIManager.setLookAndFeel(lookAndFeelNamed(className))
    } catch (unsupported: UnsupportedLookAndFeelException) {
        throw PreviewFailure("Look and feel '$className' is not supported on this platform.", unsupported)
    }
}

/**
 * The look and feel [className] names, loaded through this host's own class loader.
 *
 * Naming it to `UIManager` instead would resolve it through the context class loader of whichever
 * thread installs it, which is the event dispatch thread. That thread belongs to whoever is running
 * the host: in a process of its own that is the preview classpath, but inside an application it is
 * that application's, which knows nothing of the previewed project. A look and feel the previewed
 * project supplies - a theme library, or a theme the project wrote itself - is only ever on this
 * class loader.
 */
private fun lookAndFeelNamed(className: String): LookAndFeel =
    try {
        Class
            .forName(className, true, PreviewRequest::class.java.classLoader)
            .getDeclaredConstructor()
            .newInstance() as LookAndFeel
    } catch (unavailable: ReflectiveOperationException) {
        throw PreviewFailure("Look and feel '$className' cannot be installed.", unavailable)
    } catch (notALookAndFeel: ClassCastException) {
        throw PreviewFailure("'$className' is not a look and feel.", notALookAndFeel)
    }
