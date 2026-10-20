package org.jetbrains.compose.swing.samples.agent.theme

import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty

/**
 * The app's FlatLaf style classes. [installLookAndFeel] registers each one for its Swing component type, so a class
 * applies only to that type. Colors refer to UIManager keys, so a component follows a theme switch with no
 * recomposition.
 *
 * A class that sets `font` must not share a component with a `font(...)` modifier: the last writer wins, and every
 * theme switch hands the font back to the class. Show or hide a styled component by emitting or dropping it, never
 * by toggling its class: a class's `border` stays on the component after a theme switch.
 */
internal enum class StyleClass(
    private val componentType: @NonNls String,
    internal val id: @NonNls String,
    internal val definition: @NonNls String,
) {
    /** A button that looks like the default button, wherever it is. */
    Primary(
        "Button",
        "primary",
        "background: \$Button.default.background; foreground: \$Button.default.foreground; " +
            "hoverBackground: \$Button.default.hoverBackground; " +
            "pressedBackground: \$Button.default.pressedBackground; " +
            "borderColor: \$Button.default.borderColor; focusedBorderColor: \$Button.default.focusedBorderColor",
    ),

    /** A destructive choice in a quiet button: its text takes the danger color only while hovered. */
    Destructive("Button", "destructive", "hoverForeground: \$Actions.Red"),

    /** A fully rounded button, such as a floating pill. */
    Pill("Button", "pill", "arc: 999"),

    /** A side-pane row: a toolbar-style toggle button with row padding. */
    SessionRow("ToggleButton", "sessionRow", "arc: 12; toolbar.margin: 4,8,4,8"),

    /**
     * A rounded surface with a 1 px outline and 6 px of padding inside it. Set on a non-opaque `Panel`: an opaque
     * one fills its corners with its Swing parent's background.
     */
    Card("Panel", "card", "arc: 20; border: 7,7,7,7,\$Component.borderColor,1,20; background: \$TextArea.background"),

    /** A [Card] lifted off the page, for transcript cards, popups and the typing bubble. */
    RaisedCard(
        "Panel",
        "raisedCard",
        "arc: 20; border: 7,7,7,7,\$Component.borderColor,1,20; background: \$Agent.raisedBackground",
    ),

    /** A [RaisedCard] that waits for the user: its outline takes the warning color. */
    WarningCard(
        "Panel",
        "warningCard",
        "arc: 20; border: 7,7,7,7,\$Agent.warningBorderColor,1,20; background: \$Agent.raisedBackground",
    ),

    /** A [RaisedCard] tinted with the danger color, for errors. */
    DangerCard(
        "Panel",
        "dangerCard",
        "arc: 20; border: 7,7,7,7,\$Agent.dangerBorderColor,1,20; background: \$Agent.dangerBackground",
    ),

    /** A recessed block for code, with its padding in the border. */
    CodeBlock("Panel", "codeBlock", "arc: 12; border: 8,10,8,10; background: \$Agent.codeBackground"),

    /** The text area's scroll pane in the composer: a rounded field that shows the focus ring. */
    Prompt("ScrollPane", "prompt", "arc: 28"),

    /** The slash popup's list: rows selected by a rounded pill in the selection tint, focused or not. */
    CommandList(
        "List",
        "commandList",
        "background: \$Agent.raisedBackground; selectionArc: 12; selectionInsets: 0,0,0,0; " +
            "cellMargins: 6,10,6,10; selectionForeground: \$Label.foreground; " +
            "selectionInactiveForeground: \$Label.foreground; selectionBackground: \$Agent.selectionBackground; " +
            "selectionInactiveBackground: \$Agent.selectionBackground",
    ),

    /** A neutral count pill. */
    Badge("Label", "badge", "arc: 999; border: 1,7,1,7; background: mix(\$Label.foreground,\$Panel.background,12%)"),

    /** A count pill for added lines. */
    SuccessBadge(
        "Label",
        "successBadge",
        "arc: 999; border: 1,6,1,6; foreground: \$Actions.Green; background: fade(\$Actions.Green,15%)",
    ),

    /** A count pill for removed lines. */
    DangerBadge(
        "Label",
        "dangerBadge",
        "arc: 999; border: 1,6,1,6; foreground: \$Actions.Red; background: fade(\$Actions.Red,15%)",
    ),

    /** A small bold heading in the muted color; its text is uppercased in code. */
    SectionTitle("Label", "sectionTitle", "font: -1 bold; foreground: \$Agent.mutedForeground"),

    /** The highlight behind the plan step being worked on. */
    CurrentStep("Label", "currentStep", "arc: 12; background: mix(\$Component.accentColor,\$TextArea.background,14%)"),
    ;

    /** The key [installLookAndFeel] registers [definition] under. */
    internal val defaultsKey: @NonNls String get() = "[style]$componentType.$id"
}

/** Applies [styleClass] to the component. */
internal fun SwingModifier.styleClass(styleClass: StyleClass): SwingModifier =
    clientProperty(FlatClientProperties.STYLE_CLASS, styleClass.id)
