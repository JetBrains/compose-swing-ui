package org.jetbrains.compose.swing.samples.agent.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme
import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import java.awt.Color
import java.awt.Font
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * The two looks the app switches between. The app calls its colors, fonts and shapes the theme; the Swing
 * mechanism that installs them is the look and feel.
 */
internal enum class ThemeMode { Dark, Light }

/** The app's color tokens, read from the installed look and feel so nothing hard-codes dark or light. */
@Immutable
internal data class AgentColors(
    val background: Color,
    val surface: Color,
    val raisedSurface: Color,
    val border: Color,
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    /** Text and glyphs on an [accent] fill. */
    val accentText: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val codeBackground: Color,
    /** The fill of the user's prompt bubbles. */
    val userBubble: Color,
    /** The fill of whatever is selected, open or highlighted: an active session, a pressed chip, a menu row. */
    val selection: Color,
    /** The fill behind an added diff line. */
    val diffAdded: Color,
    /** The fill behind a removed diff line. */
    val diffRemoved: Color,
    /** The fill behind a diff's hunk header. */
    val diffHunk: Color,
    /** The drop shadow under a popup card. */
    val shadow: Color,
)

/** The app's type scale, derived from the look and feel's label font. */
@Immutable
internal data class AgentFonts(
    val body: Font,
    val small: Font,
    val title: Font,
    val mono: Font,
)

/** Corner shapes: [small] for controls and rows, [medium] for cards, [large] for the composer and bubbles. */
@Immutable
internal data class AgentShapes(
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
)

/** Colors, fonts and shapes together, as one composition local carries them. */
@Immutable
internal data class AgentTheme(
    val colors: AgentColors,
    val fonts: AgentFonts,
    val shapes: AgentShapes,
)

private val agentShapes =
    AgentShapes(small = RoundedCornerShape(6f), medium = RoundedCornerShape(10f), large = RoundedCornerShape(14f))

/** The theme read from the installed look and feel; re-read it after every look-and-feel change. */
@Suppress("CompositionLocalAllowlist") // One value the whole tree reads, changing only with the look and feel.
internal val LocalAgentTheme = staticCompositionLocalOf { readAgentTheme() }

internal fun readAgentTheme(): AgentTheme {
    val colors =
        AgentColors(
            background = uiColor("Panel.background"),
            surface = uiColor("TextArea.background"),
            raisedSurface = uiColor(RAISED_BACKGROUND),
            border = uiColor("Component.borderColor"),
            text = uiColor("Label.foreground"),
            mutedText = uiColor(MUTED_FOREGROUND),
            accent = uiColor("Component.accentColor"),
            accentText = uiColor("Button.default.foreground"),
            success = uiColor("Actions.Green"),
            warning = uiColor("Actions.Yellow"),
            danger = uiColor("Actions.Red"),
            codeBackground = uiColor(CODE_BACKGROUND),
            userBubble = uiColor(USER_BUBBLE_BACKGROUND),
            selection = uiColor(SELECTION_BACKGROUND),
            diffAdded = uiColor("Agent.diffAddedBackground"),
            diffRemoved = uiColor("Agent.diffRemovedBackground"),
            diffHunk = uiColor("Agent.diffHunkBackground"),
            shadow = uiColor(SHADOW_COLOR),
        )
    val label = UIManager.getFont("Label.font")
    val fonts =
        AgentFonts(
            body = label,
            small = label.deriveFont(label.size2D - 1f),
            title = label.deriveFont(Font.BOLD, label.size2D + 1f),
            mono = Font(Font.MONOSPACED, Font.PLAIN, label.size - 1),
        )
    return AgentTheme(colors, fonts, agentShapes)
}

private fun uiColor(key: @NonNls String): Color =
    checkNotNull(UIManager.getColor(key)) {
        "The look and feel defines no $key."
    }

/** This color moved [fraction] of the way towards [other]. */
internal fun Color.mix(
    other: Color,
    fraction: Float,
): Color {
    fun channel(
        from: Int,
        to: Int,
    ) = (from + (to - from) * fraction).toInt()
    return Color(channel(red, other.red), channel(green, other.green), channel(blue, other.blue))
}

/**
 * Installs the FlatLaf IntelliJ theme for [mode] with the app's calm accent, its tints and every [StyleClass]. The
 * accent goes through explicit keys because the IntelliJ themes ignore `@accentColor` for selections and default
 * buttons. FlatLaf's arcs are diameters, so its controls get twice the small corner radius.
 */
internal fun installLookAndFeel(mode: ThemeMode) {
    val dark = mode == ThemeMode.Dark
    val accent = if (dark) "#7B8CF0" else "#4C5FD5"
    val controlArc = (agentShapes.small.size * 2).toInt().toString()
    FlatLaf.setGlobalExtraDefaults(
        mapOf(
            "@accentColor" to accent,
            "Component.accentColor" to accent,
            "Component.focusColor" to accent,
            "Button.default.background" to accent,
            // The light accent is too pale for white text in the dark theme, so that theme writes on it in ink.
            "Button.default.foreground" to if (dark) "#1E1F22" else "#FFFFFF",
            "Tree.selectionBackground" to accent,
            "List.selectionBackground" to accent,
            "TextComponent.selectionBackground" to if (dark) "#3E4A85" else "#C9D0F5",
            "Component.arc" to controlArc,
            "Button.arc" to controlArc,
            "TextComponent.arc" to controlArc,
            "ScrollBar.thumbArc" to "999",
            "ScrollBar.thumbInsets" to "2,2,2,2",
            "ScrollBar.width" to "10",
            "PopupMenu.borderCornerRadius" to
                agentShapes.medium.size
                    .toInt()
                    .toString(),
            "ToolTip.borderCornerRadius" to
                agentShapes.small.size
                    .toInt()
                    .toString(),
            // Far enough toward the text to read at WCAG AA contrast on every pane, in both themes.
            MUTED_FOREGROUND to "mix(\$Label.foreground,\$Label.disabledForeground,45%)",
            RAISED_BACKGROUND to "lighten(\$TextArea.background,4%)",
            CODE_BACKGROUND to "mix(\$Label.foreground,\$TextArea.background,4%)",
            USER_BUBBLE_BACKGROUND to "mix(\$Component.accentColor,\$TextArea.background,16%)",
            SELECTION_BACKGROUND to "mix(\$Component.accentColor,\$Panel.background,18%)",
            "Agent.diffAddedBackground" to "mix(\$Actions.Green,\$Agent.codeBackground,18%)",
            "Agent.diffRemovedBackground" to "mix(\$Actions.Red,\$Agent.codeBackground,18%)",
            "Agent.diffHunkBackground" to "mix(\$Component.accentColor,\$Agent.codeBackground,8%)",
            SHADOW_COLOR to "fade(#000000,45%)",
            "Agent.warningBorderColor" to "mix(\$Actions.Yellow,\$Component.borderColor,70%)",
            "Agent.dangerBorderColor" to "mix(\$Actions.Red,\$Component.borderColor,50%)",
            "Agent.dangerBackground" to "mix(\$Actions.Red,\$Agent.raisedBackground,8%)",
        ) + StyleClass.entries.associate { it.defaultsKey to it.definition },
    )
    if (dark) FlatDarkFlatIJTheme.setup() else FlatLightFlatIJTheme.setup()
}

internal const val MUTED_FOREGROUND: @NonNls String = "Agent.mutedForeground"
private const val RAISED_BACKGROUND: @NonNls String = "Agent.raisedBackground"
private const val CODE_BACKGROUND: @NonNls String = "Agent.codeBackground"
private const val USER_BUBBLE_BACKGROUND: @NonNls String = "Agent.userBubbleBackground"
private const val SHADOW_COLOR: @NonNls String = "Agent.shadowColor"
private const val SELECTION_BACKGROUND: @NonNls String = "Agent.selectionBackground"

/**
 * Installs the look and feel on the event thread, starting that thread from the calling thread. Call it before
 * anything touches `Dispatchers.Swing`: its first use starts a `javax.swing.Timer`, and an event thread started by the
 * timer's thread has no context class loader, so FlatLaf's UI classes do not resolve on it.
 */
internal fun installLookAndFeelOnEventThread(mode: ThemeMode) =
    SwingUtilities.invokeAndWait { installLookAndFeel(mode) }

/** Switches the running app to [mode]; every window takes the new look and feel at once. */
internal fun switchLookAndFeel(mode: ThemeMode) {
    installLookAndFeel(mode)
    FlatLaf.updateUI()
}
