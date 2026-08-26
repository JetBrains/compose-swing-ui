package org.jetbrains.compose.swing.tooling

/**
 * Marks a composable that an IDE may render on its own, so a component can be seen without running
 * the application.
 *
 * The composable must take no parameters: nothing supplies arguments to it.
 *
 * A [PreviewEnvironment] on the same classpath prepares the process every preview in it renders under;
 * what this annotation states is what that preview alone needs on top.
 *
 * ```
 * @Preview(lookAndFeel = "javax.swing.plaf.nimbus.NimbusLookAndFeel")
 * @Composable
 * fun LoginFormPreview() = LoginForm(user = "", onSubmit = {})
 * ```
 *
 * **Rendering one composable several ways.** Repeat the annotation, and the composable is rendered
 * once per occurrence, each under what that occurrence states:
 *
 * ```
 * @Preview(name = "Light", lookAndFeel = "com.formdev.flatlaf.FlatLightLaf")
 * @Preview(name = "Dark", lookAndFeel = "com.formdev.flatlaf.FlatDarkLaf")
 * @Composable
 * fun LoginFormPreview() = LoginForm(user = "", onSubmit = {})
 * ```
 *
 * A set worth repeating belongs on an annotation class of its own, which then stands for every
 * occurrence it carries. Annotating a composable with it renders all of them, and an annotation class
 * may carry others, so sets compose:
 *
 * ```
 * @Preview(name = "Light", lookAndFeel = "com.formdev.flatlaf.FlatLightLaf")
 * @Preview(name = "Dark", lookAndFeel = "com.formdev.flatlaf.FlatDarkLaf")
 * annotation class PreviewThemes
 *
 * @PreviewThemes
 * @Composable
 * fun LoginFormPreview() = LoginForm(user = "", onSubmit = {})
 * ```
 *
 * Such sets are the application's to declare: what "light and dark" means is its own look and feel,
 * which the library cannot name for it.
 *
 * @property name the label this rendering is shown under. Defaults to the empty string, meaning the
 *   function's own name where it is rendered one way, and its position where it is rendered several.
 * @property widthPx the width, in pixels, to lay the content out at. Defaults to `-1`, meaning the
 *   content's own preferred width, within whatever room the preview is shown in. A width and a height
 *   are two parameters because an annotation cannot hold a [java.awt.Dimension].
 * @property heightPx the height, in pixels, to lay the content out at. Defaults to `-1`, meaning the
 *   height the content takes at the width it ends up with, so that content which wraps is as tall as
 *   its wrapping makes it.
 * @property lookAndFeel the fully-qualified class name of the [javax.swing.LookAndFeel] to install
 *   before the content is composed. The class is loaded by name off the classpath the preview is
 *   rendered on, so anything but the JDK's own look and feels has to be a dependency of the module the
 *   preview is declared in. Defaults to the empty string, meaning whatever the [PreviewEnvironment]
 *   left installed.
 */
@MustBeDocumented
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
public annotation class Preview(
    val name: String = "",
    val widthPx: Int = -1,
    val heightPx: Int = -1,
    val lookAndFeel: String = "",
)
