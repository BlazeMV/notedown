Notedown is a client-side markdown notebook. Keep notes for each world or server plus a global set, pin them to your screen with clickable checklists, and keep them in view while looting a chest.

## Features

- Notes are plain `.md` files you can also edit outside the game
- Per-world / per-server notes, plus global notes shared by the whole instance
- Markdown: headings, **bold**, *italic*, ~~strikethrough~~, `code`, code blocks, lists, task lists, quotes, rules, links
- Pin any number of notes to the HUD: move, resize, scroll, tick checkboxes; the layout is remembered
- Pinned notes stay visible and clickable with chat or a chest open
- Editor with list continuation, undo/redo, live preview, one-click coordinates / chunk / biome
- Flat, clean UI in the style of Sodium's options; rebindable keys; settings via Mod Menu

## Keys

| Action | Default |
|---|---|
| Open notebook | N |
| Interact with pinned notes (hold) | Left Alt |
| Edit first pinned note | unbound |
| Show/hide pinned notes | unbound |
| New note | unbound |

Hold the interact key for a cursor over your pinned notes: drag the header to move, the corner to resize, wheel to scroll, click a checkbox to tick it, × to unpin, double-click the header to edit.

In the editor: Ctrl+S save, Ctrl+E preview, Ctrl+Z / Ctrl+Y undo and redo, Ctrl+D bold, Ctrl+I italic, Ctrl+Shift+C toggle checkbox. Enter continues lists, Tab indents.

## Files

`<instance>/notedown/global/`, `local/<world>/` and `server/<address>/`, one `.md` per note. Edit them with any editor; the notebook re-reads them when opened.

## Requirements

Minecraft 26.2, Fabric Loader 0.19+, Fabric API, Java 25. Mod Menu is optional. Client-side only, works on any server.

Using the Notes mod? Remove it first, both use N.
