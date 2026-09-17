# Notedown

Markdown notebook for Minecraft. Keep notes per world or server plus a global set, pin them to the HUD with clickable checklists, and keep them on screen while a chest is open. Client-side only, Fabric.

## Features

- Notes are plain `.md` files, one per note, editable with any external editor
- Two scopes: the current world or server, and global (shared by every world in the instance)
- Markdown: headings, bold, italic, strikethrough, inline code, code blocks, bullet and numbered lists, task lists, blockquotes, rules, links
- Pinned notes on the HUD: move, resize, scroll, tick checkboxes; any number of pins; layout remembered per note
- Pinned notes stay visible and clickable while chat or a container (chest, furnace, ...) is open
- Editor with list continuation, indent/outdent, undo/redo, live preview, and one-click insert of coordinates, chunk and biome
- Flat UI in the style of Sodium's options; rebindable keys; settings via Mod Menu

## Install

Requirements: Minecraft 26.2, Fabric Loader 0.19+, [Fabric API](https://modrinth.com/mod/fabric-api), Java 25. [Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds the settings screen.

Download from [Modrinth](https://modrinth.com/mod/notedown) or [GitHub releases](https://github.com/BlazeMV/notedown/releases) and drop the jar into `mods/`.

If you use the [Notes](https://modrinth.com/mod/notes) mod, remove it first: both bind N.

## Usage

| Action | Default key |
|---|---|
| Open notebook | N |
| Interact with pinned notes (hold) | Left Alt |
| Edit first pinned note | unbound |
| Show/hide pinned notes | unbound |
| New note | unbound |

Editor: Ctrl+S save, Ctrl+E preview, Ctrl+Z / Ctrl+Y undo and redo, Ctrl+D bold, Ctrl+I italic, Ctrl+Shift+C toggle checkbox, Enter continues lists, Tab / Shift+Tab indent. The Scope button moves the note between world and global on save.

Notebook: arrows to select, Enter to view, E to edit, Delete, Ctrl+N new, Ctrl+F search.

Hold the interact key for a cursor over pinned notes: drag the header to move, the corner to resize, wheel to scroll, click a checkbox to tick it, × to unpin, double-click the header to edit.

### Where notes live

`<instance>/notedown/`

| Folder | Scope |
|---|---|
| `global/` | every world and server |
| `local/<world folder>/` | one singleplayer world |
| `server/<address>/` | one server |

Each note is `<title>.md`. An `index.json` beside the notes stores pins. Edit the files with anything; the notebook re-reads them when opened.

Settings: `config/notedown.json`, or Mod Menu → Notedown.

## Build

Java 25.

```bash
./gradlew build
```

The jar lands in `build/libs/`. Tests: `./gradlew test`.

### Dev loop

`scripts/dev-install.sh` copies the jar into a Prism Launcher instance (`FO 26.2` by default; set `NOTEDOWN_MODS_DIR` to change it). Launch the instance as usual and read `logs/latest.log`.

Loom 1.17 with official Mojang names, no mappings dependency. The access widener opens `MultiLineEditBox` internals and must stay in the `official` namespace. commonmark-java is nested into the jar.

### Release

Push a tag `vX.Y.Z`, or run the **release** workflow from the Actions tab with a version. CI builds with that version, creates a GitHub release with the jar and generated notes, publishes the version to Modrinth, and syncs `docs/modrinth.md` to the project page. A `-beta.N` suffix publishes as a beta.

## Contributing

Issues and pull requests are welcome. For a feature, open an issue first so the design can be agreed. `./gradlew build` must stay green; the pure packages (`markdown`, `store`, `hud/PinGeometry`, `ui/Scroller`) are unit-tested, so extend those tests with any change there. Design notes live in `docs/superpowers/specs/`.

## License

[MIT](LICENSE)
