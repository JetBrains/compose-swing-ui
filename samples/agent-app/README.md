# agent-app sample

A chat front end for coding agents, built to show what Compose Swing UI can do in a desktop application: animated side panes, a streaming transcript, cards that expand and settle with motion, a live look-and-feel switch between light and dark, and a language switch (English, German, Russian) with a right-to-left toggle.

## Run

```bash
./gradlew :samples:agent-app:run
```

## Agents

The app talks to a pi agent over its RPC protocol: JSON lines on the standard input and output of a process that runs in the working folder of the session. Install it with `npm install -g @earendil-works/pi-coding-agent`. Any harness that speaks the same protocol works when the pi executable setting names it.

- The pi executable is selected in Settings and defaults to `pi`. Paths containing spaces are supported. The executable applies when Settings closes; appearance, language, direction and motion apply immediately. A GUI launch may not see your shell `PATH`, so give an absolute path when the agent is not found.
- Each session has a working folder, picked when the session is created. Tool cards and the Files pane show paths relative to it.
- The model picker lists what the agent reports, with its context window for the meter.
- Each session keeps one agent process across turns. Pi stores the conversation in the app data folder, next to `agent.db`, so it resumes after an app restart. Stop, a failed turn, deleting or clearing the session, changing the pi executable, or quitting ends the process. Stop and quitting keep the stored conversation resumable.
- Every process loads a bundled pi extension that asks before `write`, `edit` and `bash`; the answer comes from the permission card. A harness with approvals of its own is answered the same way when its selection title starts with `Allow tool: ` and it offers "Approve" and "Deny".

The bundled `plan` extension registers a `plan` tool. The agent sends the complete checklist as `{"steps":[{"text":"Read the code","done":true},{"text":"Run the tests","done":false}]}`. The Plan section displays these steps as `[x] Read the code` and `[ ] Run the tests`; each call replaces the checklist.

`PI_AGENT_IT` names the command for the integration test, which skips when it is not set.

## Storage

The app keeps sessions, settings and the window layout in one SQLite database, `agent.db`, in a `Compose Swing Agent` folder under the operating system's application data folder:

| System | Folder |
| --- | --- |
| macOS | `~/Library/Application Support` |
| Windows | `%APPDATA%` |
| Linux | `$XDG_DATA_HOME`, or `~/.local/share` when it is not set |

The first run starts with no sessions. A turn's transcript items are stored when they settle, at the end of a text block or thinking block and when a tool call has its result, so a restart shows the finished part of an interrupted turn. Delete the folder to start over.

## What to look at

- `shell`: the window layout, collapsible panes with animated visibility, a draggable pane divider, the top bar and the menu bar.
- `transcript`: streaming messages, collapsible thinking, tool-call and permission cards, and the diff view.
- `composer`: a text area that grows with its content and the slash-command popup.
- `context`: the plan checklist, touched files and the context meter.
- `theme`: color tokens read from the installed look and feel, and the crossfade between light and dark.
- `i18n`: the message bundles and the language and right-to-left switch.
- `harness`: the `Agent` interface and the pi RPC agent.
- `data`: the Room database, its DAOs and the session, settings and workspace repositories; the state holders load them once at start and write changes behind.

## Dependencies

The sample alone uses these libraries; none of them is part of the Compose Swing UI modules.

- [FlatLaf](https://github.com/JFormDesigner/FlatLaf): the light and dark look and feel.
- [Ikonli](https://kordamp.org/ikonli/): the Feather icons.
- [`org.jetbrains:markdown`](https://github.com/JetBrains/markdown): parses the assistant's GitHub-flavored Markdown and generates its HTML.
- [Room 3](https://developer.android.com/jetpack/androidx/releases/room3) with the bundled SQLite driver: the database. The Room schema is committed under `schemas`.
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization): the JSON payload of each stored transcript item.

## Related

- [`../../README.md`](../../README.md) — project overview and quick start.
- [`../todo-app/README.md`](../todo-app/README.md) — a small, focused sample.
- [`../widgets-gallery/README.md`](../widgets-gallery/README.md) — the broader widget showcase.
