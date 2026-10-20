package org.jetbrains.compose.swing.samples.agent.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.FastOutSlowInEasing
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.RepeatMode
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.expandVertically
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.shrinkVertically
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.MenuSeparator
import org.jetbrains.compose.swing.components.menu.PopupMenu
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.iconTextGap
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.buttonGroup
import org.jetbrains.compose.swing.samples.agent.components.ToolbarButton
import org.jetbrains.compose.swing.samples.agent.components.ToolbarToggleButton
import org.jetbrains.compose.swing.samples.agent.i18n.BUNDLE
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.Session
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import org.kordamp.ikonli.feather.Feather
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JScrollPane
import javax.swing.SwingConstants

/**
 * The session list: a new-session button, then the sessions grouped by the day they started, scrolling when they
 * outgrow the pane. A session's more button, or a right-click on it, offers to rename it, delete it or clear its
 * history.
 */
@Composable
internal fun ConstrainedScope.SessionsPane(
    sessions: List<Session>,
    activeSessionId: @NonNls String?,
    onSessionSelect: (@NonNls String) -> Unit,
    onSessionRename: (id: @NonNls String, title: String) -> Unit,
    onSessionDelete: (@NonNls String) -> Unit,
    onSessionClear: (@NonNls String) -> Unit,
    onNewSession: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    today: LocalDate = rememberToday(),
    confirmDelete: () -> Boolean = { true },
) {
    val colors = LocalAgentTheme.current.colors
    val rowGroup = remember { ButtonGroup() }
    Column(
        modifier.fillMaxHeight().background(colors.background, RectangleShape).padding(top = 10),
        verticalArrangement = Arrangement.spacedBy(6),
    ) {
        ToolbarButton(
            onClick = onNewSession,
            modifier =
                SwingModifier
                    .fillMaxWidth()
                    .padding(horizontal = 8)
                    .horizontalAlignment(SwingConstants.LEADING)
                    .testTag("new-session"),
            text = message("sessions.new"),
            icon = rememberThemedIcon(Feather.EDIT),
        )
        // With no horizontal scroll bar the list is laid out at the viewport's width; the rows truncate to fit it.
        ScrollPane(
            SwingModifier.weight(1f).fillMaxWidth().border(BorderFactory.createEmptyBorder()),
            horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
        ) {
            Viewport(SwingModifier.background(colors.background)) {
                Column(SwingModifier.background(colors.background, RectangleShape)) {
                    Column(
                        SwingModifier.fillMaxWidth().padding(start = 8, end = 8, bottom = 10),
                        verticalArrangement = Arrangement.spacedBy(2),
                    ) {
                        for ((day, group) in sessions.groupBy { DayGroup.of(it.createdOn, today) }.toSortedMap()) {
                            DayHeader(message(day.messageKey))
                            for (session in group) {
                                key(session.id) {
                                    SessionRow(
                                        session,
                                        active = session.id == activeSessionId,
                                        group = rowGroup,
                                        onClick = { onSessionSelect(session.id) },
                                        onRename = { onSessionRename(session.id, it) },
                                        onDelete = { onSessionDelete(session.id) },
                                        confirmDelete = confirmDelete,
                                        onClear = { onSessionClear(session.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DayGroup(
    @param:PropertyKey(resourceBundle = BUNDLE) val messageKey: String,
) {
    Today("sessions.today"),
    Yesterday("sessions.yesterday"),
    Earlier("sessions.earlier"),
    ;

    companion object {
        fun of(
            day: LocalDate,
            today: LocalDate,
        ) = when (day) {
            today -> Today
            today.minusDays(1) -> Yesterday
            else -> Earlier
        }
    }
}

@Composable
private fun ColumnScope.DayHeader(text: @Nls String) {
    val theme = LocalAgentTheme.current
    Label(
        text,
        SwingModifier
            .padding(start = 10, top = 12, bottom = 4)
            .font(theme.fonts.small)
            .foreground(theme.colors.mutedText),
    )
}

/** One session row; deleting folds it away first and reports [onDelete] once it is gone. */
@Composable
private fun ColumnScope.SessionRow(
    session: Session,
    active: Boolean,
    group: ButtonGroup,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    confirmDelete: () -> Boolean,
    onClear: () -> Unit,
) {
    val presence = remember { MutableTransitionState(true) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    if (presence.isIdle && !presence.currentState) LaunchedEffect(Unit) { currentOnDelete() }
    AnimatedVisibility(
        presence,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        label = "session row",
    ) {
        val anchor = rememberPopupAnchor()
        val moreAnchor = rememberPopupAnchor()
        var renaming by remember { mutableStateOf(false) }
        var isMenuOpen by remember { mutableStateOf(false) }
        SessionPill(
            session,
            active,
            renaming,
            group,
            onClick = onClick,
            onRenameEnd = { title ->
                renaming = false
                title?.let(onRename)
            },
            onMore = { isMenuOpen = true },
            moreModifier = SwingModifier.popupAnchor(moreAnchor),
            modifier = SwingModifier.popupAnchor(anchor),
        )
        ContextMenu(anchor) {
            SessionMenuItems({ renaming = true }, { if (confirmDelete()) presence.targetState = false }, onClear)
        }
        PopupMenu(moreAnchor, isMenuOpen, onDismiss = { isMenuOpen = false }) {
            SessionMenuItems({ renaming = true }, { if (confirmDelete()) presence.targetState = false }, onClear)
        }
    }
}

@Composable
@SwingMenuComposable
private fun SessionMenuItems(
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    MenuItem(message("sessions.rename"), onClick = onRename)
    MenuItem(message("sessions.delete"), onClick = onDelete)
    MenuSeparator()
    MenuItem(message("sessions.clear"), onClick = onClear)
}

/**
 * The session's icon and title as a toolbar-style toggle button in [group], selected while [active]. While
 * [renaming], the title is a field; [onRenameEnd] gets the new title, or `null` when the user cancels. The more
 * button, styled by [moreModifier], calls [onMore].
 */
@Composable
private fun ConstrainedScope.SessionPill(
    session: Session,
    active: Boolean,
    renaming: Boolean,
    group: ButtonGroup,
    onClick: () -> Unit,
    onRenameEnd: (String?) -> Unit,
    onMore: () -> Unit,
    moreModifier: SwingModifier,
    modifier: SwingModifier = SwingModifier,
) {
    val colors = LocalAgentTheme.current.colors
    Row(
        modifier.fillMaxWidth().testTag(sessionRowTag(session.id)),
        horizontalArrangement = Arrangement.spacedBy(6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title = session.title.ifBlank { message("sessions.untitled") }
        if (renaming) {
            RenameField(session.title, onCommit = onRenameEnd, onCancel = { onRenameEnd(null) })
        } else {
            ToolbarToggleButton(
                selected = active,
                onSelectedChange = { if (it) onClick() },
                modifier =
                    SwingModifier
                        // Asking for no width keeps a long title from widening the list: it ends in an ellipsis.
                        .weight(1f)
                        .width(0)
                        .buttonGroup(group)
                        .styleClass(StyleClass.SessionRow)
                        .horizontalAlignment(SwingConstants.LEADING)
                        .iconTextGap(8)
                        .font(LocalAgentTheme.current.fonts.body)
                        .foreground(if (active) colors.text else colors.mutedText),
                text = title,
                icon = rememberThemedIcon(Feather.MESSAGE_SQUARE, if (active) IconTint.Accent else IconTint.Muted),
            )
        }
        if (session.isRunning) PulseDot()
        if (!renaming) {
            ToolbarButton(
                onClick = onMore,
                modifier = moreModifier.testTag(sessionMoreTag(session.id)),
                icon = rememberThemedIcon(Feather.MORE_HORIZONTAL),
                toolTip = message("sessions.more"),
            )
        }
    }
}

/** A small accent dot breathing while its session streams. Composed only then: it keeps the frame clock busy. */
@Composable
private fun RowScope.PulseDot() {
    val color = LocalAgentTheme.current.colors.accent
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse alpha",
    )
    Canvas(SwingModifier.size(8)) { drawCircle(color, alpha = alpha) }
}

internal fun sessionRowTag(id: @NonNls String): @NonNls String = "session-$id"

internal fun sessionMoreTag(id: @NonNls String): @NonNls String = "session-more-$id"

@Composable
internal fun rememberToday(clock: Clock = Clock.systemDefaultZone()): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now(clock)) }
    LaunchedEffect(clock) {
        while (true) {
            val now = ZonedDateTime.now(clock)
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(
                Duration
                    .between(now, midnight)
                    .toMillis(),
            )
            today = LocalDate.now(clock)
        }
    }
    return today
}
