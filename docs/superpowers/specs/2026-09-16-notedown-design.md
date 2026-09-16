# Notedown — design spec (2026-09-16)

Goal: a Fabric client mod for Minecraft 26.2+ with a markdown notebook — multiple notes per world/server plus global ones, a flat Sodium-style UI, notes pinnable to the HUD with clickable checklists, visible while chests are open.

## Decisions

| Item | Value |
|---|---|
| Name / mod id / package | Notedown / `notedown` / `dev.blaze.notedown` (free on Modrinth and GitHub BlazeMV) |
| License | MIT |
| Loader / versions | Fabric, `>=26.2 <27.1`, single build |
| Toolchain | Java 25, Gradle 9.6 wrapper, Loom 1.17, official names (no mappings dep), Loader 0.19.5+, Fabric API 0.160+ — same as panoramix |
| Hard deps | Fabric API; `org.commonmark:commonmark:0.30.0`, `commonmark-ext-task-list-items`, `commonmark-ext-gfm-strikethrough` bundled via Loom `include` |
| Optional | Mod Menu entrypoint (compileOnly) |
| Settings | `config/notedown.json`, Gson, no config library |
| Notes data | `<gamedir>/notedown/` |
| UI | own flat widget kit (`ui` unit), no vanilla button/edit-box/list visuals anywhere |
| Mixins / AW | no mixins; one access widener with two entries on `MultiLineEditBox` (`<init>`, `textField`) |
| Repo | `~/projects/blaze/notedown`, branch `master`, CI + `scripts/dev-install.sh` as in panoramix |

## Architecture

All code under `src/client/java/dev/blaze/notedown/` (Loom `splitEnvironmentSourceSets()`), tests under `src/test/java`.

| Unit | Does | Depends on |
|---|---|---|
| `markdown` | commonmark parse → `Layout` (styled runs, lines, hit boxes) for a width and scale; task toggling; editor commands | commonmark; `TextMeasure` interface |
| `store` | note files, per-folder `index.json`, atomic save, rename/duplicate/delete, list, search, file import | filesystem |
| `scope` | current scope (global / local / server) → folder, from client state | Minecraft client |
| `hud` | pinned-note drawing, `PinGeometry`, hold-key interact screen, chat/container hooks | `markdown`, `store`, `ui`, Fabric HUD + screen events |
| `ui` | `Theme`, flat widgets, `LayoutRenderer`, `ConfirmDialog` | font + `GuiGraphicsExtractor` |
| `gui` | Notebook, View, Edit, Settings screens | all |
| `config` | settings model + holder (panoramix pattern) | Gson |
| `Notedown` | entrypoint: logger, config, keybinds, HUD registration, screen hooks, tick loop | |

Pure JVM, unit-tested: markdown layout + toggling + editor commands, store + index, slugs, scope folder mapping, `PinGeometry`, config. Screens and HUD are verified by hand in the Prism "FO 26.2" instance.

Flow: key N → Notebook lists current scope + global → View renders `layout(text, width)` → checkbox click → `toggleTask(text, i)` → store saves → HUD layout cache invalidated.

## UI style (`ui`)

Modelled on Sodium's video settings and Notes; written from scratch (Sodium is PolyForm Shield, Notes is CC-BY-NC-SA).

| Token | Value |
|---|---|
| Accent | `0xFF94E4D3` (selected tab bar, focused row, checkbox tick, links) |
| Text / disabled | `0xFFFFFFFF` / `0xFFAAAAAA`; muted `0xFFBBBBBB` for previews, dates, checked tasks |
| Panel | `0x90000000`; darker `0xB0000000`; hover `0xE0000000`; light `0x40000000` (inline code, code blocks use darker) |
| Border | `0x8000FFEE`, only on focus and frames |
| Spacing | button height 20, inner margin 5, text left padding 8, list row 24, scrollbar 7 wide, paragraph gap 4, line gap 2 |
| Background | vanilla blurred screen background |

Widgets: `FlatButton` (flat fill, hover-lit), `FlatTextField` (also the search field, via a hint), `FlatTextArea` (extends `MultiLineEditBox` via AW; overrides background/border/scrollbar), `FlatList` + `Scroller` (pure scroll math, `Theme.scrollbar` draws it), `Toggle` (tick box), `Slider`, `ConfirmDialog`, `LayoutRenderer` (draws a markdown `Layout` with a clip rect), `Messages` (i18n helper).

## Storage (`store`, `scope`)

```
<gamedir>/notedown/
  global/                        index.json + *.md
  local/<level directory name>/  index.json + *.md
  server/<ip or server name>/    index.json + *.md
```

- Note = one `.md` file. Title = filename without extension. Body = file content, UTF-8, `\n` normalised on load and save. Rename moves the file.
- Filename rules: illegal chars → `_`, empty → `Untitled`, collisions → ` 2`, ` 3`… (case-insensitive check).
- `index.json`: `{ "version": 1, "pins": { "<file>.md": { "x": 0.78, "y": 0.35, "w": 180, "h": 120, "scale": 1.0, "scroll": 0 } } }`. `x`/`y` are fractions of screen size, `w`/`h` GUI px. Entries for missing files are pruned on load; missing index = no pins. Atomic write (tmp + move); unreadable → moved to `.bad`, rebuilt empty.
- Scope: singleplayer → `local/<level directory name>`; multiplayer → `server/<ip>`, Realms/LAN → `server/<server name>`; names filename-filtered. Title screen → global only; new notes there go to global.
- Listing = current scope + global; pinned first, then last-modified desc. Search matches title and body, case-insensitive. Changing scope in the editor moves the file.
- Refresh: notes are re-read when the Notebook opens; pinned notes are re-read on Notebook open, after in-game edits and toggles, and on join/disconnect. No polling, no watcher.
- Actions: duplicate → `Title 2.md`; copy text to clipboard; delete → permanent after confirm.
- Import file…: `TinyFileDialogs.tinyfd_openFileDialog` (multi-select, filter `*.md;*.txt`), each file copied into the current scope as `<basename>.md` with collision suffix, decoded UTF-8 with replacement, imported note selected in the list. Dialog runs on the render thread (macOS requirement).
- Notes over 1 MB open in View only; the editor refuses with a message.

## Markdown (`markdown`)

Supported:

| Element | Syntax | Rendered |
|---|---|---|
| Headings | `#` `##` `###`; `####`+ | scaled 1.5× / 1.25× / 1.1× bold; bold at 1× |
| Emphasis | `**b**` `*i*` `_i_` `***bi***` | Minecraft bold / italic |
| Strikethrough | `~~x~~` | strikethrough |
| Inline code | `` `x` `` | light fill, muted text |
| Code block | fenced / indented | darker panel, no wrap, clipped |
| Bullet list | `-` `*` `+`, nested | `•` `◦` `▪` by depth, 10 px per level |
| Ordered list | `1.` | number from start value |
| Task list | `- [ ]` `- [x]` | 9×9 box, accent tick; checked text muted + struck (setting) |
| Blockquote | `> x` | 2 px accent bar, muted, indented |
| Rule | `---` | 1 px line |
| Link | `[t](url)` | underlined accent; click → confirm dialog → `Util.getPlatform().openUri` |
| Breaks | blank line; trailing `\` or two spaces | paragraph gap / line break |
| Not in v1 | tables, images, HTML, footnotes | literal text |

Pipeline:
1. `MarkdownParser`: commonmark + task-list + strikethrough, source spans on. Never throws.
2. `Layouter.layout(doc, width, scale, LayoutOptions, TextMeasure)` → immutable `Layout { lines, hitBoxes, height }`. `Line { x, y, runs, decoration }`, `Run { text, style, x }`, `HitBox { rect, TASK(index) | LINK(url) }`. Greedy word wrap with per-style widths; over-long words break per character. Headings measured at scale.
3. `TaskToggler.toggleLine(source, lineIndex)` flips `[ ]` ↔ `[x]` on that source line; nothing else changes. Task hit boxes carry the list item's zero-based source line (from commonmark source spans), so code-block look-alikes never count.
4. `EditorCommands` (pure): `continueList(text, cursor)` on Enter (`- `, `- [ ] `, `1. ` auto-increment; empty item removes marker), `indent/outdent` on Tab / Shift+Tab, `wrapSelection` for Ctrl+D / Ctrl+I (Ctrl+B is Minecraft's narrator hotkey), `toggleTaskAtLine` for Ctrl+Shift+C, `titleFromBody` (first heading or first line, trimmed, max 40).
5. `LayoutCache`: keyed by content hash + width + scale, small LRU.

`TextMeasure`: `int width(String text, boolean bold)`. In-game impl wraps `Font`; tests use a fixed-width fake (6 px, 7 px bold).

## Screens and keybinds (`gui`)

Keybinds, category `Notedown`, rebindable:

| Action | Default |
|---|---|
| Open notebook | N |
| Interact with pinned notes (hold) | Left Alt |
| Quick-edit first pinned note, body focused | unbound |
| Show / hide all pinned notes | unbound |
| New note | unbound |

Notebook: search field on top, left action column, right list. Rows: title, scope badge, first line, modified date. Click selects; double-click / Enter opens View. Actions on the selected note: View, Edit, Pin/Unpin, Duplicate, Delete. Also New, Import file…, Settings. Keys: Up/Down, Enter, E, Delete, Ctrl+N, Ctrl+F, Esc. Empty state text. Reachable via N in-game and from the Mod Menu settings screen.

View: rendered markdown, wheel / scrollbar / PageUp/PageDown scrolling, checkboxes toggle and save immediately, links confirm. Actions: Edit, Pin/Unpin, Duplicate, Copy text, Delete, Back. Keys: E, P, Esc.

Edit: title field, `FlatTextArea` body, left toolbar: Preview toggle (Ctrl+E, in-place swap keeping scroll), Insert Coords / Chunk / Biome at cursor (`x, y, z`; `cx, cz`; biome name — hidden by setting), Scope toggle (Global / Local or Server), Save (Ctrl+S), Back (Esc, confirm when dirty). Editor commands from the markdown unit, snapshot undo/redo (Ctrl+Z / Ctrl+Y, 100 entries). Empty title on save → `titleFromBody`. New note title defaults to `Untitled`.

Settings (own flat screen, also the Mod Menu screen): pinned background opacity, default text scale, show pinned in containers, show pinned with chat, checked-task style (strike + muted / muted / none), show insert buttons, keybind rows. Buttons: Open notebook, Open notes folder, Import file….

ConfirmDialog: one flat modal for delete, discard changes, open link.

## Pinned HUD (`hud`)

- Registered with `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, …)`.
- Pin panel at `(x·screenW, y·screenH)`, `w×h` GUI px, clamped on screen, background alpha from config, content laid out at `w − 2·padding` and the pin's scale, clipped, per-pin scroll offset persisted.
- Visible when HUD not hidden (F1) and: no screen, or `ChatScreen` (setting), or `HudInteractScreen`. `AbstractContainerScreen` (setting) draws pins itself in `ScreenEvents.afterExtract` and the HUD path skips them. Any other screen hides pins. Global hide toggle persisted in config.
- `PinGeometry` (pure): fraction ↔ px, clamp, drag, resize (min 60×30), default placement for a new pin (right edge, stacked below existing pins).
- Interact: on tick, hold key down with no screen → `mc.setScreen(new HudInteractScreen())` (transparent, no blur, not pausing). Release or Esc → `setScreen(null)`. In it each pin shows a header strip (title, unpin ×), resize corner, thin scrollbar. Checkbox click → toggle + save + relayout; link click → confirm; wheel → scroll; header drag → move; corner drag → resize; header double-click → Edit. Geometry saved on release.
- Chat and container screens: on `ScreenEvents.AFTER_INIT`, register `ScreenMouseEvents.allowMouseClick` / `allowMouseScroll`; clicks on checkbox/link hit boxes and wheel over a pin are consumed by the pin. No move/resize there.
- Lifecycle: `ClientPlayConnectionEvents` join/disconnect → scope reset, pins reloaded from the new folders.

## Config (`config/notedown.json`)

| Key | Default |
|---|---|
| `pinnedBackgroundOpacity` | 0.5 |
| `pinnedTextScale` | 1.0 (applied to new pins) |
| `showPinnedInContainers` | true |
| `showPinnedWithChat` | true |
| `checkedTaskStyle` | `STRIKE_MUTED` |
| `showInsertButtons` | true |
| `pinnedHidden` | false |

Load/save via the panoramix `ConfigHolder` pattern (defaults, clamp, quarantine on parse failure, atomic write).

## Error handling

| Case | Behaviour |
|---|---|
| unreadable `index.json` | moved to `.bad`, rebuilt empty, logged |
| unreadable note file | skipped, logged |
| save failure | chat message + log, editor stays open with content |
| note > 1 MB | View only, editor refuses with message |
| file dialog cancelled / failed | nothing, failure logged |
| pin file deleted externally | pin pruned on next reload |

## Testing

- JUnit 5: `Layouter` (wrap, nesting, headings, hit boxes, CRLF, non-ASCII), `TaskToggler` round-trips, `EditorCommands`, `NoteStore` (list, save, rename, collisions, duplicate, delete, index round-trip, import copy), scope folder mapping, `PinGeometry`, `NotedownConfig`.
- Manual in FO 26.2: notebook / view / edit flows, pin move + resize + scroll, checkbox clicks in HUD, chat and chest, world/server switching, Mod Menu entry, GUI scale 1–4.
