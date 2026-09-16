# Notedown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fabric 26.2+ client mod with a markdown notebook: per-world/server and global notes, flat Sodium-style UI, notes pinnable to the HUD with clickable checklists, visible while chests are open.

**Architecture:** Pure-JVM units (markdown parse → layout, task toggling, editor commands, store + pin index, pin geometry, scroll math) have no Minecraft imports and are JUnit-tested. Minecraft-facing units (`ui` widget kit, screens, HUD) sit on top and are verified by hand in the Prism "FO 26.2" instance. No mixins; one access widener with two entries.

**Tech Stack:** Java 25, Gradle 9.6 wrapper, Fabric Loom 1.17.+ (official names, no mappings dep), Fabric Loader 0.19.5, Fabric API 0.160.0+26.2, Mod Menu 20.0.2 (compileOnly), commonmark-java 0.30.0 (+ task-list-items, gfm-strikethrough) nested via Loom `include`, Gson (from Minecraft), LWJGL tinyfd (from Minecraft), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-16-notedown-design.md`

## Global Constraints

- Mod id `notedown`, package `dev.blaze.notedown`, MIT license, `minecraft ">=26.2 <27.1"`, `java ">=25"`, `fabricloader ">=0.19.0"`, `fabric-api "*"`.
- Client-only mod: `"environment": "client"`, all code under `src/client/java` (Loom `splitEnvironmentSourceSets()`), tests under `src/test/java`.
- No config or UI library. No vanilla button/edit-box/list visuals: every screen and the HUD draw through the `ui` kit. Mod Menu is an optional entrypoint.
- No mixins. One access widener `src/client/resources/notedown.accesswidener` (namespace `official`) with three entries: `MultiLineEditBox` `<init>` and `textField`, and the class `MultilineTextField$StringView` (protected in 26.2; needed for the selection range).
- Every user-visible string goes through `assets/notedown/lang/en_us.json` via `Messages.t(key, args)` (`notedown.` prefix) or `Component.translatable` for `key.*` entries.
- Notes live in `<gamedir>/notedown/`, settings in `config/notedown.json`.
- Commits: single line, brief, lower-case. Branch `master`.
- Dev loop: `./gradlew build` then `scripts/dev-install.sh` copies the jar into the Prism instance; launch from Prism; check `minecraft/logs/latest.log`. Remove `Notes-*.jar` from that mods folder first (both bind N).
- JDK 25 with `javac` lives at `/Users/blaze/Library/Application Support/PrismLauncher/java/java-runtime-epsilon` and is registered in `~/.gradle/gradle.properties` (`org.gradle.java.installations.paths`), not committed. `javap` for API checks: `"/Users/blaze/Library/Application Support/PrismLauncher/java/java-runtime-epsilon/bin/javap" -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly-deobf/26.2/minecraft-clientonly-deobf-26.2.jar:~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/26.2/minecraft-common-deobf-26.2.jar -p <class>` (the system `javap` is Java 23 and cannot read these classes).
- 26.2 API facts (verified with javap, do not re-derive):
  - `GuiGraphicsExtractor`: `fill(x1,y1,x2,y2,argb)`, `text(Font, String|Component|FormattedCharSequence, x, y, argb)` (shadow) and the same with a trailing `boolean shadow`, `centeredText(Font, String|Component, cx, y, argb)`, `enableScissor(x1,y1,x2,y2)`, `disableScissor()`, `pose()` → `org.joml.Matrix3x2fStack` with `pushMatrix()`, `translate(float,float)`, `scale(float,float)`, `popMatrix()`, `guiWidth()`, `guiHeight()`.
  - `Font`: `width(String)`, `width(FormattedText)` (a `Component` is one), `lineHeight` (9), `plainSubstrByWidth(String, int)`, `split(FormattedText, int)` → `List<FormattedCharSequence>`.
  - Text styling: `Style.EMPTY.withBold(Boolean).withItalic(Boolean).withStrikethrough(Boolean).withUnderlined(Boolean).withColor(int)`; `Component.literal(String)`, `Component.translatable(String)`, `Component.empty()`, `MutableComponent.withStyle(Style)`, `.append(Component)`, `.withStyle(ChatFormatting)`.
  - `Screen`: `protected Screen(Component)`, `init()`, `extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, float)` (default draws background then widgets), `extractBackground(GuiGraphicsExtractor, mouseX, mouseY, float)` (default = blurred + dimmed world in-game), `extractTransparentBackground(GuiGraphicsExtractor)`, `keyPressed(KeyEvent)`, `keyReleased(KeyEvent)`, `charTyped(CharacterEvent)`, `mouseClicked(MouseButtonEvent, boolean doubleClick)`, `mouseReleased(MouseButtonEvent)`, `mouseDragged(MouseButtonEvent, double dx, double dy)`, `mouseScrolled(double mx, double my, double h, double v)`, `onClose()`, `shouldCloseOnEsc()`, `isPauseScreen()`, `tick()`, `removed()`, `addRenderableWidget(T)`, `addWidget(T)`, `clearWidgets()`, `rebuildWidgets()`, `setInitialFocus(GuiEventListener)`, `setFocused(GuiEventListener)`, `getFocused()`, fields `width`, `height`, `font`, `minecraft`, `title`.
  - `AbstractWidget(int x, int y, int w, int h, Component)`: abstract `extractWidgetRenderState(GuiGraphicsExtractor, mouseX, mouseY, float)` and `updateWidgetNarration(NarrationElementOutput)`; `onClick(MouseButtonEvent, boolean doubleClick)`, `onRelease(MouseButtonEvent)`, `onDrag(MouseButtonEvent, double, double)`, `mouseScrolled(...)`, `keyPressed(KeyEvent)`, `isHovered()`, `isHoveredOrFocused()`, `isFocused()`, `setFocused(boolean)`, `isMouseOver(double,double)`, `active`, `visible`, `getX/getY/getWidth/getHeight`, `setX/setY/setWidth/setHeight/setSize`, `getMessage()/setMessage(Component)`, `setTooltip(Tooltip.create(Component))`, static `playButtonClickSound(SoundManager)`.
  - `AbstractButton(int,int,int,int,Component)`: abstract `onPress(InputWithModifiers)`, protected abstract `extractContents(GuiGraphicsExtractor, mouseX, mouseY, float)`, `defaultButtonNarrationText(NarrationElementOutput)`. Click sound is played by `AbstractWidget.mouseClicked`.
  - `EditBox(Font, x, y, w, h, Component)`: `setBordered(boolean)`, `setHint(Component)`, `setMaxLength(int)`, `setResponder(Consumer<String>)`, `getValue()/setValue(String)`, `setTextColor(int)`, `setCanLoseFocus(boolean)`, `moveCursorToEnd(boolean)`, public `extractWidgetRenderState(...)`.
  - `MultiLineEditBox` private ctor `(Font, x, y, w, h, Component placeholder, Component message, int textColor, boolean textShadow, int cursorColor, boolean showBackground, boolean showDecorations)` (opened by AW); `getValue()`, `setValue(String)`, `setValueListener(Consumer<String>)`, `setCharacterLimit(int)`, protected `extractContents`, `extractDecorations(GuiGraphicsExtractor)`; from `AbstractTextAreaWidget`: protected `extractBackground(GuiGraphicsExtractor)`, `extractBorder(GuiGraphicsExtractor, x, y, w, h)`; from `AbstractScrollArea`: `scrollAmount()`, `setScrollAmount(double)`, `maxScrollAmount()`, protected `scrollBarX()`, public `scrollBarY()`, protected `scrollerHeight()`, protected `extractScrollbar(GuiGraphicsExtractor, mouseX, mouseY)`. Private field `textField` (opened by AW) is a `MultilineTextField`: `insertText(String)`, `cursor()`, `seekCursor(Whence.ABSOLUTE, int)`, `getSelected()` → `StringView(beginIndex(), endIndex())`, `hasSelection()`, `value()`, `setValue(String)`.
  - Input records: `KeyEvent(key(), scancode(), modifiers())`, `MouseButtonEvent(x(), y(), button(), modifiers())`, `CharacterEvent(codepoint())`; both key and mouse events implement `InputWithModifiers`: `hasControlDown()`, `hasShiftDown()`, `hasAltDown()`, `isEscape()`, `isConfirmation()`, `isSelection()`, `isUp()`, `isDown()`, `isCopy()`, `isPaste()`. Mouse buttons: `InputConstants.MOUSE_BUTTON_LEFT` (0). Key codes: `org.lwjgl.glfw.GLFW.GLFW_KEY_*`.
  - `KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)`, `KeyMapping.Category.register(Identifier)`, `isDown()`, `consumeClick()`, `matches(KeyEvent)`, `getTranslatedKeyMessage()`, `getName()`, `setKey(InputConstants.Key)`, `getDefaultKey()`, `isDefault()`, `isUnbound()`, `same(KeyMapping)`, static `resetMapping()`; `InputConstants.getKey(KeyEvent)`, `InputConstants.UNKNOWN`, `InputConstants.Type.MOUSE.getOrCreate(int)`, `InputConstants.Key.getValue()`, `InputConstants.isKeyDown(Window, int)`; Fabric `KeyMappingHelper.registerKeyMapping(KeyMapping)`, `KeyMappingHelper.getBoundKeyOf(KeyMapping)`; `Options.keyMappings`, `Options.save()`.
  - `Minecraft.getInstance()`: `font`, `gui.setScreen(Screen)`, `gui.screen()`, `gui.hud.isHidden()`, `gui.chatListener().handleSystemMessage(Component, false)`, `level`, `player`, `isLocalServer()`, `getSingleplayerServer().getWorldPath(LevelResource.ICON_FILE)` (world dir = its parent), `getCurrentServer()` → `ServerData` with public `ip`, `name`, `type()` ∈ `ServerData.Type.{LAN, REALM, OTHER}`, `getWindow().getGuiScaledWidth()/getGuiScaledHeight()`, `keyboardHandler.setClipboard(String)`, `getSoundManager()`, `options`.
  - Player/world: `player.getBlockX()/getBlockY()/getBlockZ()`, `player.blockPosition()`, `player.chunkPosition().x()/z()`, `level.getBiome(BlockPos)` → `Holder<Biome>`, `holder.unwrapKey()` → `Optional<ResourceKey<Biome>>`, `key.identifier().toLanguageKey("biome")`, `I18n.get(String, Object...)`.
  - `Util.getPlatform().openUri(URI)`, `openUri(String)`, `openPath(Path)`; `Identifier.fromNamespaceAndPath(ns, path)`; `FabricLoader.getInstance().getGameDir()/getConfigDir()`.
  - Fabric events: `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier, HudElement)` where `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`; `ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> …)`; `ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tick) -> …)`; `ScreenMouseEvents.allowMouseClick(screen).register((s, MouseButtonEvent) -> boolean)`; `ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> boolean)`; `ClientTickEvents.END_CLIENT_TICK.register(mc -> …)`; `ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> …)`, `ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> …)`.
  - Screen classes: `net.minecraft.client.gui.screens.ChatScreen`, `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen`.
  - tinyfd: `org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(CharSequence title, CharSequence defaultPath /*nullable*/, org.lwjgl.PointerBuffer filters, CharSequence filterDescription, boolean allowMultiple)` → `String` (paths joined by `|`) or `null`; filters built with `org.lwjgl.system.MemoryStack.stackPush()`, `stack.mallocPointer(n)`, `stack.UTF8("*.md")`.
  - commonmark 0.30: `Parser.builder().extensions(List.of(TaskListItemsExtension.create(), StrikethroughExtension.create())).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()`, `parser.parse(String)` → `Node`; nodes: `Document`, `Heading.getLevel()`, `Paragraph`, `BlockQuote`, `BulletList`, `OrderedList.getMarkerStartNumber()` (`Integer`, nullable), `ListBlock.isTight()`, `ListItem`, `org.commonmark.ext.task.list.items.TaskListItemMarker.isChecked()` (first child of the item's first `Paragraph`), `FencedCodeBlock.getLiteral()`, `IndentedCodeBlock.getLiteral()`, `ThematicBreak`, `HtmlBlock.getLiteral()`, `Text.getLiteral()`, `Emphasis`, `StrongEmphasis`, `org.commonmark.ext.gfm.strikethrough.Strikethrough`, `Code.getLiteral()`, `Link.getDestination()`, `Image`, `HardLineBreak`, `SoftLineBreak`, `HtmlInline.getLiteral()`; `Node.getFirstChild()`, `getNext()`, `getSourceSpans()` → `List<SourceSpan>` with `getLineIndex()`.

---

## File map

| Path | Responsibility |
|---|---|
| `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat` | Loom build, Java 25 toolchain, nested commonmark, AW, JUnit |
| `scripts/dev-install.sh`, `scripts/gen_icon.py` | copy jar to Prism instance; generate the 128×128 icon PNG |
| `.github/workflows/build.yml`, `LICENSE`, `.gitignore`, `CLAUDE.md` | CI, MIT, ignores, operational notes |
| `src/client/resources/fabric.mod.json`, `notedown.accesswidener`, `assets/notedown/lang/en_us.json`, `assets/notedown/icon.png` | mod metadata, AW, strings, icon |
| `src/client/java/dev/blaze/notedown/Notedown.java` | `ClientModInitializer`: logger, store, layout cache, config, keybinds, HUD element, screen hooks, tick loop |
| `.../NotedownKeys.java` | five `KeyMapping`s |
| `.../NotedownModMenu.java` | Mod Menu entrypoint → `SettingsScreen` |
| `.../config/NotedownConfig.java`, `config/ConfigHolder.java` | JSON model + atomic load/save; static access |
| `.../markdown/CheckedTaskStyle.java`, `TextMeasure.java`, `TextStyle.java`, `Layout.java`, `MarkdownParser.java`, `Layouter.java`, `LayoutCache.java`, `TaskToggler.java`, `EditorCommands.java`, `UndoStack.java` | parse, layout model, layout engine, cache, task toggling, editor helpers, undo |
| `.../store/NoteScope.java`, `ScopeDir.java`, `NoteFiles.java`, `Note.java`, `PinIndex.java`, `NoteStore.java` | scopes, filename rules, note record, pins index, file store |
| `.../scope/CurrentScope.java` | Minecraft state → `List<ScopeDir>` |
| `.../ui/Theme.java`, `Messages.java`, `FontMeasure.java`, `Scroller.java`, `FlatButton.java`, `FlatTextField.java`, `FlatTextArea.java`, `FlatList.java`, `Toggle.java`, `Slider.java`, `ConfirmDialog.java`, `LayoutRenderer.java`, `MarkdownView.java` | flat widget kit, i18n helper, markdown drawing |
| `.../gui/GameInfo.java`, `FileDialogs.java`, `NotebookScreen.java`, `ViewScreen.java`, `EditScreen.java`, `SettingsScreen.java`, `KeyBindRow.java` | screens |
| `.../hud/PinGeometry.java`, `PinnedNotes.java`, `PinRenderer.java`, `PinnedHudElement.java`, `HudInteractScreen.java`, `ScreenHooks.java` | pinned notes |
| `src/test/java/dev/blaze/notedown/...` | JUnit tests for the pure classes |

Task order is compile order: every task leaves `./gradlew build` green.

---

### Task 0: Project scaffold that builds an empty mod

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`, `LICENSE`, `CLAUDE.md`, `.github/workflows/build.yml`, `scripts/dev-install.sh`, `scripts/gen_icon.py`, `src/client/resources/fabric.mod.json`, `src/client/resources/notedown.accesswidener`, `src/client/resources/assets/notedown/lang/en_us.json`, `src/client/resources/assets/notedown/icon.png` (generated), `src/client/java/dev/blaze/notedown/Notedown.java`
- Existing: `.gitignore` (already committed)

**Interfaces:**
- Produces: `Notedown.MOD_ID = "notedown"`, `Notedown.LOGGER` (`org.slf4j.Logger`), `Notedown.id(String)` → `Identifier`.

- [ ] **Step 1: Gradle files**

`settings.gradle`
```groovy
pluginManagement { repositories { maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }; mavenCentral(); gradlePluginPortal() } }
rootProject.name = 'notedown'
```

`gradle.properties`
```
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=26.2
loader_version=0.19.5
fabric_api_version=0.160.0+26.2
modmenu_version=20.0.2
commonmark_version=0.30.0
mod_version=1.0.0
maven_group=dev.blaze
archives_base_name=notedown
```

`build.gradle`
```groovy
plugins { id 'net.fabricmc.fabric-loom' version '1.17.+' }
version = project.mod_version
group = project.maven_group
base { archivesName = project.archives_base_name }
repositories { maven { url = 'https://maven.terraformersmc.com/' } }
loom {
    splitEnvironmentSourceSets()
    mods { "notedown" { sourceSet sourceSets.main; sourceSet sourceSets.client } }
    accessWidenerPath = file('src/client/resources/notedown.accesswidener')
}
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    compileOnly "com.terraformersmc:modmenu:${project.modmenu_version}"
    ['commonmark', 'commonmark-ext-task-list-items', 'commonmark-ext-gfm-strikethrough'].each { name ->
        implementation "org.commonmark:${name}:${project.commonmark_version}"
        include "org.commonmark:${name}:${project.commonmark_version}"
    }
    testImplementation platform('org.junit:junit-bom:5.13.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
sourceSets.test {
    compileClasspath += sourceSets.client.output + sourceSets.client.compileClasspath
    runtimeClasspath += sourceSets.client.output + sourceSets.client.runtimeClasspath
}
def modVersion = project.version
tasks.withType(ProcessResources).configureEach {
    // fabric.mod.json lives in the client source set; processResources alone misses it
    inputs.property 'version', modVersion
    filteringCharset = 'UTF-8'
    filesMatching('fabric.mod.json') { expand 'version': modVersion }
}
tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8'; options.release = 25 }
java { toolchain { languageVersion = JavaLanguageVersion.of(25) }; withSourcesJar() }
tasks.test { useJUnitPlatform() }
jar { from('LICENSE') { rename { "${it}_${base.archivesName.get()}" } } }
```

Wrapper: copy `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat` verbatim from `~/projects/blaze/panoramix/` (Gradle 9.6.0). Run `chmod +x gradlew`.

- [ ] **Step 2: Mod resources**

`src/client/resources/fabric.mod.json`
```json
{
  "schemaVersion": 1,
  "id": "notedown",
  "version": "${version}",
  "name": "Notedown",
  "description": "Markdown notebook: per-world and global notes, checklists, pinnable to the HUD.",
  "authors": ["blaze"],
  "contact": { "sources": "https://github.com/BlazeMV/notedown", "issues": "https://github.com/BlazeMV/notedown/issues" },
  "license": "MIT",
  "icon": "assets/notedown/icon.png",
  "environment": "client",
  "entrypoints": {
    "client": ["dev.blaze.notedown.Notedown"],
    "modmenu": ["dev.blaze.notedown.NotedownModMenu"]
  },
  "accessWidener": "notedown.accesswidener",
  "depends": { "fabricloader": ">=0.19.0", "fabric-api": "*", "minecraft": ">=26.2 <27.1", "java": ">=25" },
  "suggests": { "modmenu": "*" }
}
```
(The `modmenu` entrypoint class arrives in Task 13; Fabric Loader only resolves entrypoints when Mod Menu is present and asks for them, and the dev run has no Mod Menu, so the build stays green until then.)

`src/client/resources/notedown.accesswidener`
```
accessWidener v2 named
accessible method net/minecraft/client/gui/components/MultiLineEditBox <init> (Lnet/minecraft/client/gui/Font;IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;IZIZZ)V
accessible field net/minecraft/client/gui/components/MultiLineEditBox textField Lnet/minecraft/client/gui/components/MultilineTextField;
```

`src/client/resources/assets/notedown/lang/en_us.json` (complete; later tasks only consume keys)
```json
{
  "key.categories.notedown.main": "Notedown",
  "key.notedown.open": "Open notebook",
  "key.notedown.interact": "Interact with pinned notes (hold)",
  "key.notedown.quick_edit": "Edit first pinned note",
  "key.notedown.toggle_pinned": "Show/hide pinned notes",
  "key.notedown.new_note": "New note",
  "notedown.scope.global": "Global",
  "notedown.scope.local": "World",
  "notedown.scope.server": "Server",
  "notedown.button.new": "New",
  "notedown.button.import": "Import…",
  "notedown.button.settings": "Settings",
  "notedown.button.view": "View",
  "notedown.button.edit": "Edit",
  "notedown.button.pin": "Pin",
  "notedown.button.unpin": "Unpin",
  "notedown.button.duplicate": "Duplicate",
  "notedown.button.copy": "Copy text",
  "notedown.button.delete": "Delete",
  "notedown.button.back": "Back",
  "notedown.button.done": "Done",
  "notedown.button.save": "Save",
  "notedown.button.preview": "Preview",
  "notedown.button.source": "Source",
  "notedown.button.cancel": "Cancel",
  "notedown.button.open": "Open",
  "notedown.button.discard": "Discard",
  "notedown.button.insert_coords": "Coordinates",
  "notedown.button.insert_chunk": "Chunk",
  "notedown.button.insert_biome": "Biome",
  "notedown.button.scope": "Scope: %s",
  "notedown.button.open_folder": "Open notes folder",
  "notedown.button.open_notebook": "Open notebook",
  "notedown.screen.notebook.title": "Notes",
  "notedown.screen.notebook.search": "Search notes…",
  "notedown.screen.notebook.empty": "No notes yet. Press New.",
  "notedown.screen.notebook.scope": "%s: %s",
  "notedown.screen.view.title": "Note",
  "notedown.screen.edit.new": "New note",
  "notedown.screen.edit.edit": "Edit note",
  "notedown.screen.edit.title_hint": "Title",
  "notedown.screen.edit.body_hint": "Write markdown here…",
  "notedown.screen.settings.title": "Notedown settings",
  "notedown.screen.settings.keybinds": "Keybinds",
  "notedown.screen.interact.title": "Pinned notes",
  "notedown.screen.interact.hint": "Release %s to resume playing",
  "notedown.list.modified": "Modified %s",
  "notedown.list.pinned": "pinned",
  "notedown.dialog.delete_title": "Delete note?",
  "notedown.dialog.delete_body": "\"%s\" will be deleted. This cannot be undone.",
  "notedown.dialog.discard_title": "Discard changes?",
  "notedown.dialog.discard_body": "Unsaved changes to \"%s\" will be lost.",
  "notedown.dialog.link_title": "Open link?",
  "notedown.dialog.link_body": "%s",
  "notedown.option.pinned_opacity": "Pinned background opacity",
  "notedown.option.pinned_scale": "Pinned text scale",
  "notedown.option.show_in_containers": "Show pinned notes in chests",
  "notedown.option.show_with_chat": "Show pinned notes with chat open",
  "notedown.option.checked_style": "Checked tasks",
  "notedown.option.checked_style.strike_muted": "Strike + dim",
  "notedown.option.checked_style.muted": "Dim",
  "notedown.option.checked_style.none": "Plain",
  "notedown.option.show_insert_buttons": "Show Coordinates/Chunk/Biome buttons",
  "notedown.option.selecting": "> %s <",
  "notedown.option.reset": "Reset",
  "notedown.message.imported": "Imported %s note(s).",
  "notedown.message.import_failed": "Import failed, see latest.log.",
  "notedown.message.save_failed": "Save failed, see latest.log.",
  "notedown.message.too_large": "Note is larger than 1 MB; edit it in an external editor.",
  "notedown.message.copied": "Note copied to clipboard.",
  "notedown.message.no_pinned": "No pinned notes.",
  "notedown.message.pinned_hidden": "Pinned notes hidden.",
  "notedown.message.pinned_shown": "Pinned notes shown."
}
```

`scripts/gen_icon.py` (run once: `python3 scripts/gen_icon.py`; commit the PNG)
```python
#!/usr/bin/env python3
import os
import struct
import zlib

SIZE = 128
BG = (0x1E, 0x2A, 0x44, 0xFF)
PAPER = (0xF4, 0xF1, 0xE8, 0xFF)
LINE = (0xB0, 0xAD, 0xA4, 0xFF)
ACCENT = (0x94, 0xE4, 0xD3, 0xFF)


def pixel(x, y):
    # paper sheet 80x96 centred, with three ruled lines and a teal tick on the second line
    px, py = x - 24, y - 16
    if 0 <= px < 80 and 0 <= py < 96:
        for row in (28, 48, 68):
            if row <= py < row + 4 and 12 <= px < 68:
                if row == 48 and 12 <= px < 24:
                    return ACCENT
                return LINE
        if row_tick(px, py):
            return ACCENT
        return PAPER
    return BG


def row_tick(px, py):
    # a 12x12 tick mark to the left of the first rule
    tx, ty = px - 12, py - 20
    if not (0 <= tx < 12 and 0 <= ty < 12):
        return False
    return (2 <= tx < 6 and ty == tx + 4) or (6 <= tx < 12 and ty == 14 - tx)


def write_png(path):
    raw = bytearray()
    for y in range(SIZE):
        raw.append(0)
        for x in range(SIZE):
            raw.extend(pixel(x, y))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', ihdr))
        f.write(chunk(b'IDAT', zlib.compress(bytes(raw), 9)))
        f.write(chunk(b'IEND', b''))


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    write_png(os.path.join(root, 'src', 'client', 'resources', 'assets', 'notedown', 'icon.png'))
```

- [ ] **Step 3: Entrypoint, license, CI, dev-install, CLAUDE.md**

`src/client/java/dev/blaze/notedown/Notedown.java`
```java
package dev.blaze.notedown;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Notedown implements ClientModInitializer {
    public static final String MOD_ID = "notedown";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Notedown loaded");
    }
}
```

`LICENSE`: MIT text, `Copyright (c) 2026 blaze` (copy from `~/projects/blaze/panoramix/LICENSE`).

`.github/workflows/build.yml`
```yaml
name: build

on:
  push:
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
      - uses: actions/upload-artifact@v4
        with:
          name: notedown
          path: build/libs/*.jar
```

`scripts/dev-install.sh` (`chmod +x`)
```bash
#!/usr/bin/env bash
set -euo pipefail
MODS="${NOTEDOWN_MODS_DIR:-$HOME/Library/Application Support/PrismLauncher/instances/FO 26.2/minecraft/mods}"
JAR=$(ls build/libs/notedown-*.jar | grep -v sources | head -1)
rm -f "$MODS"/notedown-*.jar
cp "$JAR" "$MODS/" && echo "installed $(basename "$JAR") -> $MODS"
```

`CLAUDE.md`
```markdown
# Notedown

- build: `./gradlew build` → `build/libs/notedown-1.0.0.jar`; tests: `./gradlew test`
- dev install: `scripts/dev-install.sh` (copies jar into Prism `FO 26.2` mods folder). Remove `Notes-*.jar` there first: both bind N.
- JDK 25 (javac) lives at `/Users/blaze/Library/Application Support/PrismLauncher/java/java-runtime-epsilon`; registered in user-level `~/.gradle/gradle.properties`, not committed. Use its `javap` for API checks; the system `javap` is Java 23.
- Loom uses official Mojang names by default (Loom 1.17+ on 26.x) — no mappings dependency
- client-only mod: all code under `src/client/java`. `markdown`, `store`, `hud/PinGeometry`, `ui/Scroller` have no Minecraft imports and are unit-tested.
- `notedown.accesswidener` opens `MultiLineEditBox` ctor + `textField`; commonmark jars are nested via Loom `include`.
```

- [ ] **Step 4: Build**

Run: `cd /Users/blaze/projects/blaze/notedown && python3 scripts/gen_icon.py && ./gradlew build`
Expected: BUILD SUCCESSFUL, `build/libs/notedown-1.0.0.jar` exists and `unzip -l build/libs/notedown-1.0.0.jar | grep -c 'META-INF/jars/commonmark'` prints `3`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "scaffold fabric 26.2 mod"
```

---

### Task 1: Config model and holder

**Files:**
- Create: `src/client/java/dev/blaze/notedown/markdown/CheckedTaskStyle.java`, `src/client/java/dev/blaze/notedown/config/NotedownConfig.java`, `src/client/java/dev/blaze/notedown/config/ConfigHolder.java`
- Test: `src/test/java/dev/blaze/notedown/config/NotedownConfigTest.java`

**Interfaces:**
- Produces: `enum CheckedTaskStyle { STRIKE_MUTED, MUTED, NONE }`; `NotedownConfig` public fields `float pinnedBackgroundOpacity`, `float pinnedTextScale`, `boolean showPinnedInContainers`, `boolean showPinnedWithChat`, `CheckedTaskStyle checkedTaskStyle`, `boolean showInsertButtons`, `boolean pinnedHidden`; `static NotedownConfig load(Path, Consumer<String>)`, `void save(Path) throws IOException`, `NotedownConfig clamp()`; `ConfigHolder.get()`, `ConfigHolder.save()`, `ConfigHolder.path()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/blaze/notedown/config/NotedownConfigTest.java`
```java
package dev.blaze.notedown.config;

import dev.blaze.notedown.markdown.CheckedTaskStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotedownConfigTest {

    @TempDir
    Path dir;

    @Test
    void defaultsWhenMissing() {
        List<String> problems = new ArrayList<>();
        NotedownConfig c = NotedownConfig.load(dir.resolve("notedown.json"), problems::add);
        assertEquals(0.5f, c.pinnedBackgroundOpacity);
        assertEquals(1.0f, c.pinnedTextScale);
        assertTrue(c.showPinnedInContainers);
        assertTrue(c.showPinnedWithChat);
        assertEquals(CheckedTaskStyle.STRIKE_MUTED, c.checkedTaskStyle);
        assertTrue(c.showInsertButtons);
        assertFalse(c.pinnedHidden);
        assertTrue(problems.isEmpty());
    }

    @Test
    void roundTrips() throws Exception {
        Path file = dir.resolve("notedown.json");
        NotedownConfig c = new NotedownConfig();
        c.pinnedBackgroundOpacity = 0.8f;
        c.checkedTaskStyle = CheckedTaskStyle.NONE;
        c.pinnedHidden = true;
        c.save(file);
        NotedownConfig back = NotedownConfig.load(file, msg -> fail(msg));
        assertEquals(0.8f, back.pinnedBackgroundOpacity);
        assertEquals(CheckedTaskStyle.NONE, back.checkedTaskStyle);
        assertTrue(back.pinnedHidden);
        assertFalse(Files.exists(dir.resolve("notedown.json.tmp")));
    }

    @Test
    void clampsOutOfRange() throws Exception {
        Path file = dir.resolve("notedown.json");
        Files.writeString(file, "{\"pinnedBackgroundOpacity\": 7, \"pinnedTextScale\": 0.01, \"checkedTaskStyle\": null}");
        NotedownConfig c = NotedownConfig.load(file, msg -> fail(msg));
        assertEquals(1.0f, c.pinnedBackgroundOpacity);
        assertEquals(0.5f, c.pinnedTextScale);
        assertEquals(CheckedTaskStyle.STRIKE_MUTED, c.checkedTaskStyle);
    }

    @Test
    void reportsUnreadableFile() throws Exception {
        Path file = dir.resolve("notedown.json");
        Files.writeString(file, "{ not json");
        List<String> problems = new ArrayList<>();
        NotedownConfig c = NotedownConfig.load(file, problems::add);
        assertEquals(1, problems.size());
        assertEquals(0.5f, c.pinnedBackgroundOpacity);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'dev.blaze.notedown.config.NotedownConfigTest'`
Expected: compilation error, `NotedownConfig` does not exist.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/markdown/CheckedTaskStyle.java`
```java
package dev.blaze.notedown.markdown;

public enum CheckedTaskStyle {
    STRIKE_MUTED, MUTED, NONE;

    public CheckedTaskStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
```

`src/client/java/dev/blaze/notedown/config/NotedownConfig.java`
```java
package dev.blaze.notedown.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.blaze.notedown.markdown.CheckedTaskStyle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class NotedownConfig {

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public float pinnedBackgroundOpacity = 0.5f;
    public float pinnedTextScale = 1.0f;
    public boolean showPinnedInContainers = true;
    public boolean showPinnedWithChat = true;
    public CheckedTaskStyle checkedTaskStyle = CheckedTaskStyle.STRIKE_MUTED;
    public boolean showInsertButtons = true;
    public boolean pinnedHidden = false;

    public static NotedownConfig load(Path file, Consumer<String> onProblem) {
        if (!Files.exists(file)) {
            return new NotedownConfig().clamp();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            NotedownConfig config = GSON.fromJson(reader, NotedownConfig.class);
            return (config != null ? config : new NotedownConfig()).clamp();
        } catch (IOException | JsonParseException e) {
            onProblem.accept("Failed to load config from " + file.getFileName() + ": " + e.getMessage());
            return new NotedownConfig().clamp();
        }
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(this, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public NotedownConfig clamp() {
        pinnedBackgroundOpacity = Math.max(0f, Math.min(1f, pinnedBackgroundOpacity));
        pinnedTextScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, pinnedTextScale));
        if (checkedTaskStyle == null) {
            checkedTaskStyle = CheckedTaskStyle.STRIKE_MUTED;
        }
        return this;
    }
}
```

`src/client/java/dev/blaze/notedown/config/ConfigHolder.java`
```java
package dev.blaze.notedown.config;

import dev.blaze.notedown.Notedown;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigHolder {

    private static NotedownConfig instance;

    private ConfigHolder() {}

    public static NotedownConfig get() {
        if (instance == null) {
            Path file = path();
            boolean existed = Files.exists(file);
            boolean[] hadProblem = {false};
            instance = NotedownConfig.load(file, msg -> {
                hadProblem[0] = true;
                Notedown.LOGGER.warn(msg);
            });
            if (!existed || hadProblem[0]) {
                if (hadProblem[0]) {
                    quarantine(file);
                }
                Notedown.LOGGER.info("Writing default config to {}", file);
                persist(file);
            }
        }
        return instance;
    }

    public static void save() {
        get();
        persist(path());
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("notedown.json");
    }

    private static void persist(Path file) {
        try {
            instance.save(file);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to save config", e);
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to preserve unreadable config", e);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'dev.blaze.notedown.config.NotedownConfigTest'`
Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "config model and holder"
```

---

### Task 2: Scopes and filename rules

**Files:**
- Create: `src/client/java/dev/blaze/notedown/store/NoteScope.java`, `store/ScopeDir.java`, `store/NoteFiles.java`
- Test: `src/test/java/dev/blaze/notedown/store/NoteFilesTest.java`

**Interfaces:**
- Produces: `enum NoteScope { GLOBAL, LOCAL, SERVER }` with `String folder()` and `String langKey()`; `record ScopeDir(NoteScope kind, Path dir)`; `NoteFiles.EXT = ".md"`, `NoteFiles.sanitize(String)`, `NoteFiles.titleOf(Path)`, `NoteFiles.isNote(Path)`, `NoteFiles.uncolliding(Path dir, String title, Path except)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/blaze/notedown/store/NoteFilesTest.java`
```java
package dev.blaze.notedown.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoteFilesTest {

    @TempDir
    Path dir;

    @Test
    void sanitizeReplacesIllegalCharacters() {
        assertEquals("a_b_c_d_e_f_g_h_i", NoteFiles.sanitize("a/b\\c:d*e?f\"g<h>i"));
        assertEquals("tabs and newlines", NoteFiles.sanitize("tabs\tand\nnewlines"));
        assertEquals("trimmed", NoteFiles.sanitize("  trimmed  "));
        assertEquals("no trailing dots", NoteFiles.sanitize("no trailing dots..."));
    }

    @Test
    void sanitizeFallsBackToUntitled() {
        assertEquals("Untitled", NoteFiles.sanitize(""));
        assertEquals("Untitled", NoteFiles.sanitize("   "));
        assertEquals("Untitled", NoteFiles.sanitize("..."));
        assertEquals("Untitled", NoteFiles.sanitize(null));
    }

    @Test
    void titleOfStripsExtension() {
        assertEquals("Starter House", NoteFiles.titleOf(Path.of("x", "Starter House.md")));
        assertEquals("Odd.name", NoteFiles.titleOf(Path.of("Odd.name.md")));
    }

    @Test
    void isNoteChecksExtensionAndFileType() throws Exception {
        Path note = Files.writeString(dir.resolve("a.MD"), "x");
        Path txt = Files.writeString(dir.resolve("b.txt"), "x");
        Path folder = Files.createDirectory(dir.resolve("c.md"));
        assertTrue(NoteFiles.isNote(note));
        assertFalse(NoteFiles.isNote(txt));
        assertFalse(NoteFiles.isNote(folder));
    }

    @Test
    void uncollidingAppendsCounter() throws Exception {
        Files.writeString(dir.resolve("Base.md"), "");
        Files.writeString(dir.resolve("base 2.md"), "");
        assertEquals(dir.resolve("Base 3.md"), NoteFiles.uncolliding(dir, "Base", null));
        assertEquals(dir.resolve("Fresh.md"), NoteFiles.uncolliding(dir, "Fresh", null));
    }

    @Test
    void uncollidingKeepsOwnFile() throws Exception {
        Path own = Files.writeString(dir.resolve("Base.md"), "");
        assertEquals(own, NoteFiles.uncolliding(dir, "Base", own));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'dev.blaze.notedown.store.NoteFilesTest'`
Expected: compilation error, `NoteFiles` does not exist.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/store/NoteScope.java`
```java
package dev.blaze.notedown.store;

public enum NoteScope {
    GLOBAL("global", "notedown.scope.global"),
    LOCAL("local", "notedown.scope.local"),
    SERVER("server", "notedown.scope.server");

    private final String folder;
    private final String langKey;

    NoteScope(String folder, String langKey) {
        this.folder = folder;
        this.langKey = langKey;
    }

    public String folder() {
        return folder;
    }

    public String langKey() {
        return langKey;
    }
}
```

`src/client/java/dev/blaze/notedown/store/ScopeDir.java`
```java
package dev.blaze.notedown.store;

import java.nio.file.Path;

public record ScopeDir(NoteScope kind, Path dir) {

    public boolean isGlobal() {
        return kind == NoteScope.GLOBAL;
    }

    public String label() {
        return isGlobal() ? "" : dir.getFileName().toString();
    }
}
```

`src/client/java/dev/blaze/notedown/store/NoteFiles.java`
```java
package dev.blaze.notedown.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class NoteFiles {

    public static final String EXT = ".md";
    public static final String UNTITLED = "Untitled";

    private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Pattern TRAILING_DOTS = Pattern.compile("\\.+$");

    private NoteFiles() {}

    public static String sanitize(String title) {
        if (title == null) {
            return UNTITLED;
        }
        String s = ILLEGAL.matcher(title).replaceAll("_").strip();
        s = TRAILING_DOTS.matcher(s).replaceAll("").strip();
        return s.isEmpty() ? UNTITLED : s;
    }

    public static String titleOf(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(EXT) ? name.substring(0, name.length() - EXT.length()) : name;
    }

    public static boolean isNote(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXT);
    }

    public static Path uncolliding(Path dir, String title, Path except) {
        Path candidate = dir.resolve(title + EXT);
        int n = 2;
        while (existsIgnoreCase(dir, candidate) && !isSame(candidate, except)) {
            candidate = dir.resolve(title + " " + n++ + EXT);
        }
        return candidate;
    }

    private static boolean isSame(Path a, Path b) {
        return b != null && a.getFileName().toString().equalsIgnoreCase(b.getFileName().toString())
                && a.toAbsolutePath().getParent().equals(b.toAbsolutePath().getParent());
    }

    private static boolean existsIgnoreCase(Path dir, Path candidate) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        String wanted = candidate.getFileName().toString();
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(wanted));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'dev.blaze.notedown.store.NoteFilesTest'`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "note scopes and filename rules"
```

---

### Task 3: Note store, pin index, current scope

**Files:**
- Create: `src/client/java/dev/blaze/notedown/store/Note.java`, `store/PinIndex.java`, `store/NoteStore.java`, `src/client/java/dev/blaze/notedown/scope/CurrentScope.java`
- Test: `src/test/java/dev/blaze/notedown/store/PinIndexTest.java`, `src/test/java/dev/blaze/notedown/store/NoteStoreTest.java`

**Interfaces:**
- Consumes: `NoteScope`, `ScopeDir`, `NoteFiles` (Task 2).
- Produces: `record Note(ScopeDir scope, Path file, String title, String body, long lastModified)` with `String fileName()`; `PinIndex` with nested `record Pin(double x, double y, int w, int h, float scale, int scroll)` (+ `withPosition`, `withSize`, `withScroll`), `static PinIndex load(Path dir, Consumer<String> onProblem)`, `Map<String, Pin> pins()`, `Optional<Pin> get(String)`, `boolean isPinned(String)`, `void put(String, Pin)`, `void remove(String)`, `void rename(String, String)`, `void save() throws IOException`; `NoteStore(Path root, Consumer<String> warn)` with `root()`, `dir(NoteScope, String)`, `list(ScopeDir)`, `read(ScopeDir, String title)`, `create(ScopeDir, title, body)`, `save(Note, newTitle, newBody, ScopeDir)`, `duplicate(Note)`, `delete(Note)`, `importFile(ScopeDir, Path)`, `index(ScopeDir)`, static `search(List<Note>, String)`, static `normalize(String)`, static `preview(String body)`; `CurrentScope.dirs(NoteStore, Minecraft)` → `List<ScopeDir>` (current first, global last), `CurrentScope.global(NoteStore)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/dev/blaze/notedown/store/PinIndexTest.java`
```java
package dev.blaze.notedown.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PinIndexTest {

    @TempDir
    Path dir;

    @Test
    void emptyWhenMissing() {
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        assertTrue(idx.pins().isEmpty());
        assertFalse(idx.isPinned("a.md"));
    }

    @Test
    void roundTripsInInsertionOrder() throws Exception {
        Files.writeString(dir.resolve("b.md"), "");
        Files.writeString(dir.resolve("a.md"), "");
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        idx.put("b.md", new PinIndex.Pin(0.5, 0.25, 180, 120, 1.0f, 0));
        idx.put("a.md", new PinIndex.Pin(0.1, 0.1, 60, 30, 1.5f, 12));
        idx.save();
        PinIndex back = PinIndex.load(dir, msg -> fail(msg));
        assertEquals(List.of("b.md", "a.md"), new ArrayList<>(back.pins().keySet()));
        assertEquals(new PinIndex.Pin(0.1, 0.1, 60, 30, 1.5f, 12), back.get("a.md").orElseThrow());
    }

    @Test
    void prunesMissingFilesOnLoad() throws Exception {
        Files.writeString(dir.resolve("keep.md"), "");
        Files.writeString(dir.resolve(PinIndex.FILE),
                "{\"version\":1,\"pins\":{\"keep.md\":{\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"scale\":1,\"scroll\":0},"
                        + "\"gone.md\":{\"x\":0,\"y\":0,\"w\":100,\"h\":50,\"scale\":1,\"scroll\":0}}}");
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        assertEquals(List.of("keep.md"), new ArrayList<>(idx.pins().keySet()));
    }

    @Test
    void quarantinesUnreadableIndex() throws Exception {
        Files.writeString(dir.resolve(PinIndex.FILE), "{ nope");
        List<String> problems = new ArrayList<>();
        PinIndex idx = PinIndex.load(dir, problems::add);
        assertTrue(idx.pins().isEmpty());
        assertEquals(1, problems.size());
        assertTrue(Files.exists(dir.resolve(PinIndex.FILE + ".bad")));
        assertFalse(Files.exists(dir.resolve(PinIndex.FILE)));
    }

    @Test
    void renameKeepsPin() throws Exception {
        Files.writeString(dir.resolve("old.md"), "");
        PinIndex idx = PinIndex.load(dir, msg -> fail(msg));
        idx.put("old.md", new PinIndex.Pin(0.2, 0.2, 100, 50, 1f, 0));
        idx.rename("old.md", "new.md");
        assertFalse(idx.isPinned("old.md"));
        assertTrue(idx.isPinned("new.md"));
        idx.remove("new.md");
        assertTrue(idx.pins().isEmpty());
    }
}
```

`src/test/java/dev/blaze/notedown/store/NoteStoreTest.java`
```java
package dev.blaze.notedown.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoteStoreTest {

    @TempDir
    Path root;

    NoteStore store;
    ScopeDir global;
    ScopeDir world;

    @BeforeEach
    void setUp() {
        store = new NoteStore(root, msg -> fail(msg));
        global = store.dir(NoteScope.GLOBAL, null);
        world = store.dir(NoteScope.LOCAL, "blaze/forever");
    }

    @Test
    void dirsAreCreatedAndSanitized() {
        assertEquals(root.resolve("global"), global.dir());
        assertEquals(root.resolve("local").resolve("blaze_forever"), world.dir());
        assertTrue(Files.isDirectory(world.dir()));
        assertEquals(root.resolve("server").resolve("play.example.com_25565"),
                store.dir(NoteScope.SERVER, "play.example.com:25565").dir());
    }

    @Test
    void createListReadNormalizesLineEndings() throws Exception {
        Note n = store.create(world, "Starter House", "Lantern x45\r\nCobweb x12\r");
        assertEquals("Starter House", n.title());
        assertEquals("Lantern x45\nCobweb x12\n", n.body());
        assertEquals("Starter House.md", n.fileName());
        List<Note> listed = store.list(world);
        assertEquals(1, listed.size());
        assertEquals("Lantern x45\nCobweb x12\n", store.read(world, "Starter House").orElseThrow().body());
    }

    @Test
    void listSortsNewestFirstAndSkipsNonNotes() throws Exception {
        Note old = store.create(global, "Old", "1");
        Files.setLastModifiedTime(old.file(), java.nio.file.attribute.FileTime.fromMillis(1_000_000));
        store.create(global, "New", "2");
        Files.writeString(global.dir().resolve("readme.txt"), "ignored");
        List<String> titles = store.list(global).stream().map(Note::title).toList();
        assertEquals(List.of("New", "Old"), titles);
    }

    @Test
    void saveRenamesAndKeepsPin() throws Exception {
        Note n = store.create(world, "Draft", "body");
        PinIndex idx = store.index(world);
        idx.put(n.fileName(), new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 0));
        idx.save();
        Note renamed = store.save(n, "Final", "body 2", world);
        assertEquals("Final.md", renamed.fileName());
        assertFalse(Files.exists(n.file()));
        assertEquals("body 2", Files.readString(renamed.file()));
        assertTrue(store.index(world).isPinned("Final.md"));
        assertFalse(store.index(world).isPinned("Draft.md"));
    }

    @Test
    void saveWithSameTitleKeepsFile() throws Exception {
        Note n = store.create(world, "Same", "a");
        Note again = store.save(n, "Same", "b", world);
        assertEquals(n.file(), again.file());
        assertEquals("b", again.body());
    }

    @Test
    void saveAcrossScopesMovesFileAndPin() throws Exception {
        Note n = store.create(world, "Moving", "x");
        PinIndex idx = store.index(world);
        idx.put(n.fileName(), new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 3));
        idx.save();
        Note moved = store.save(n, "Moving", "x", global);
        assertEquals(global.dir().resolve("Moving.md"), moved.file());
        assertFalse(Files.exists(n.file()));
        assertTrue(store.index(global).isPinned("Moving.md"));
        assertFalse(store.index(world).isPinned("Moving.md"));
        assertEquals(3, store.index(global).get("Moving.md").orElseThrow().scroll());
    }

    @Test
    void duplicateDeleteAndCollisions() throws Exception {
        Note n = store.create(global, "Todo", "- [ ] a");
        Note copy = store.duplicate(n);
        assertEquals("Todo 2", copy.title());
        assertEquals("- [ ] a", copy.body());
        store.index(global).put(copy.fileName(), new PinIndex.Pin(0, 0, 60, 30, 1f, 0));
        store.index(global).save();
        store.delete(copy);
        assertFalse(Files.exists(copy.file()));
        assertFalse(store.index(global).isPinned("Todo 2.md"));
        assertEquals(1, store.list(global).size());
    }

    @Test
    void importsTxtAsMarkdown() throws Exception {
        Path src = Files.writeString(root.resolve("shopping list.txt"), "milk\r\neggs");
        Note imported = store.importFile(world, src);
        assertEquals("shopping list", imported.title());
        assertEquals("milk\neggs", imported.body());
        assertEquals(world.dir().resolve("shopping list.md"), imported.file());
        assertTrue(Files.exists(src));
    }

    @Test
    void searchMatchesTitleOrBodyCaseInsensitively() throws Exception {
        Note a = store.create(global, "Farm", "carrots and Potatoes");
        Note b = store.create(global, "Nether", "blaze rods");
        List<Note> all = List.of(a, b);
        assertEquals(List.of(a), NoteStore.search(all, "POTATO"));
        assertEquals(List.of(b), NoteStore.search(all, "neth"));
        assertEquals(all, NoteStore.search(all, "  "));
    }

    @Test
    void previewIsFirstNonEmptyLineWithoutMarkers() {
        assertEquals("Lantern x45", NoteStore.preview("\n\n# Lantern x45\nmore"));
        assertEquals("do it", NoteStore.preview("- [ ] **do** _it_"));
        assertEquals("", NoteStore.preview("   \n"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'dev.blaze.notedown.store.*'`
Expected: compilation errors for `Note`, `PinIndex`, `NoteStore`.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/store/Note.java`
```java
package dev.blaze.notedown.store;

import java.nio.file.Path;

public record Note(ScopeDir scope, Path file, String title, String body, long lastModified) {

    public String fileName() {
        return file.getFileName().toString();
    }

    public boolean sameFile(Note other) {
        return other != null && file.toAbsolutePath().normalize().equals(other.file.toAbsolutePath().normalize());
    }
}
```

`src/client/java/dev/blaze/notedown/store/PinIndex.java`
```java
package dev.blaze.notedown.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class PinIndex {

    public static final String FILE = "index.json";

    public record Pin(double x, double y, int w, int h, float scale, int scroll) {
        public Pin withPosition(double nx, double ny) {
            return new Pin(nx, ny, w, h, scale, scroll);
        }

        public Pin withSize(int nw, int nh) {
            return new Pin(x, y, nw, nh, scale, scroll);
        }

        public Pin withScroll(int s) {
            return new Pin(x, y, w, h, scale, s);
        }

        public Pin withScale(float s) {
            return new Pin(x, y, w, h, s, scroll);
        }
    }

    private static final class Data {
        int version = 1;
        LinkedHashMap<String, Pin> pins = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Data data;

    private PinIndex(Path file, Data data) {
        this.file = file;
        this.data = data;
    }

    public static PinIndex load(Path dir, Consumer<String> onProblem) {
        Path file = dir.resolve(FILE);
        Data data = new Data();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Data read = GSON.fromJson(reader, Data.class);
                if (read != null && read.pins != null) {
                    data = read;
                }
            } catch (IOException | JsonParseException e) {
                onProblem.accept("Unreadable " + file + ": " + e.getMessage());
                quarantine(file);
                data = new Data();
            }
        }
        data.pins.keySet().removeIf(name -> !Files.isRegularFile(dir.resolve(name)));
        return new PinIndex(file, data);
    }

    public Map<String, Pin> pins() {
        return Collections.unmodifiableMap(data.pins);
    }

    public Optional<Pin> get(String fileName) {
        return Optional.ofNullable(data.pins.get(fileName));
    }

    public boolean isPinned(String fileName) {
        return data.pins.containsKey(fileName);
    }

    public void put(String fileName, Pin pin) {
        data.pins.put(fileName, pin);
    }

    public void remove(String fileName) {
        data.pins.remove(fileName);
    }

    public void rename(String oldName, String newName) {
        Pin pin = data.pins.remove(oldName);
        if (pin != null) {
            data.pins.put(newName, pin);
        }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(FILE + ".bad"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // the next save overwrites it anyway
        }
    }
}
```

`src/client/java/dev/blaze/notedown/store/NoteStore.java`
```java
package dev.blaze.notedown.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class NoteStore {

    public static final long MAX_EDITABLE_BYTES = 1024 * 1024;

    private static final Pattern MARKERS = Pattern.compile("^\\s*(?:#{1,6}\\s+|>\\s*|(?:[-*+]|\\d+[.)])\\s+(?:\\[[ xX]\\]\\s+)?)|[*_`~]");

    private final Path root;
    private final Consumer<String> warn;

    public NoteStore(Path root, Consumer<String> warn) {
        this.root = root;
        this.warn = warn;
    }

    public Path root() {
        return root;
    }

    public ScopeDir dir(NoteScope kind, String name) {
        Path dir = kind == NoteScope.GLOBAL
                ? root.resolve(kind.folder())
                : root.resolve(kind.folder()).resolve(NoteFiles.sanitize(name));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ScopeDir(kind, dir);
    }

    public List<Note> list(ScopeDir scope) {
        List<Note> notes = new ArrayList<>();
        if (!Files.isDirectory(scope.dir())) {
            return notes;
        }
        try (Stream<Path> files = Files.list(scope.dir())) {
            files.filter(NoteFiles::isNote).forEach(file -> load(scope, file).ifPresent(notes::add));
        } catch (IOException e) {
            warn.accept("Cannot list " + scope.dir() + ": " + e.getMessage());
        }
        notes.sort(Comparator.comparingLong(Note::lastModified).reversed());
        return notes;
    }

    public Optional<Note> read(ScopeDir scope, String title) {
        Path file = scope.dir().resolve(title + NoteFiles.EXT);
        return NoteFiles.isNote(file) ? load(scope, file) : Optional.empty();
    }

    public Note create(ScopeDir scope, String title, String body) throws IOException {
        Path file = NoteFiles.uncolliding(scope.dir(), NoteFiles.sanitize(title), null);
        return write(scope, file, body);
    }

    public Note save(Note note, String newTitle, String newBody, ScopeDir newScope) throws IOException {
        boolean sameScope = newScope.dir().equals(note.scope().dir());
        Path target = NoteFiles.uncolliding(newScope.dir(), NoteFiles.sanitize(newTitle), sameScope ? note.file() : null);
        if (!target.equals(note.file())) {
            Files.createDirectories(newScope.dir());
            Files.move(note.file(), target, StandardCopyOption.REPLACE_EXISTING);
            movePin(note, newScope, target.getFileName().toString());
        }
        return write(newScope, target, newBody);
    }

    public Note duplicate(Note note) throws IOException {
        return create(note.scope(), note.title(), note.body());
    }

    public void delete(Note note) throws IOException {
        Files.deleteIfExists(note.file());
        PinIndex idx = index(note.scope());
        if (idx.isPinned(note.fileName())) {
            idx.remove(note.fileName());
            idx.save();
        }
    }

    public Note importFile(ScopeDir scope, Path source) throws IOException {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        String body = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        return create(scope, title, body);
    }

    public PinIndex index(ScopeDir scope) {
        return PinIndex.load(scope.dir(), warn);
    }

    public static List<Note> search(List<Note> notes, String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return notes;
        }
        return notes.stream()
                .filter(n -> n.title().toLowerCase(Locale.ROOT).contains(q) || n.body().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    public static String normalize(String body) {
        return body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String preview(String body) {
        for (String line : normalize(body).split("\n")) {
            String cleaned = MARKERS.matcher(line).replaceAll("").strip();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return "";
    }

    private void movePin(Note note, ScopeDir newScope, String newName) throws IOException {
        PinIndex from = index(note.scope());
        Optional<PinIndex.Pin> pin = from.get(note.fileName());
        if (pin.isEmpty()) {
            return;
        }
        from.remove(note.fileName());
        from.save();
        PinIndex to = newScope.dir().equals(note.scope().dir()) ? from : index(newScope);
        to.put(newName, pin.get());
        to.save();
    }

    private Note write(ScopeDir scope, Path file, String body) throws IOException {
        String normalized = normalize(body);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, normalized, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Note(scope, file, NoteFiles.titleOf(file), normalized, Files.getLastModifiedTime(file).toMillis());
    }

    private Optional<Note> load(ScopeDir scope, Path file) {
        try {
            String body = normalize(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            return Optional.of(new Note(scope, file, NoteFiles.titleOf(file), body, Files.getLastModifiedTime(file).toMillis()));
        } catch (IOException e) {
            warn.accept("Skipping unreadable note " + file + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
```

`src/client/java/dev/blaze/notedown/scope/CurrentScope.java`
```java
package dev.blaze.notedown.scope;

import dev.blaze.notedown.store.NoteScope;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.ScopeDir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayList;
import java.util.List;

public final class CurrentScope {

    private CurrentScope() {}

    /** Current world/server scope first (when in one), global last. */
    public static List<ScopeDir> dirs(NoteStore store, Minecraft mc) {
        List<ScopeDir> out = new ArrayList<>(2);
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            String world = mc.getSingleplayerServer().getWorldPath(LevelResource.ICON_FILE).getParent().getFileName().toString();
            out.add(store.dir(NoteScope.LOCAL, world));
        } else if (mc.getCurrentServer() != null) {
            ServerData server = mc.getCurrentServer();
            boolean byName = server.type() == ServerData.Type.LAN || server.type() == ServerData.Type.REALM;
            out.add(store.dir(NoteScope.SERVER, byName ? server.name : server.ip));
        }
        out.add(global(store));
        return out;
    }

    public static ScopeDir global(NoteStore store) {
        return store.dir(NoteScope.GLOBAL, null);
    }
}
```

Add to `Notedown.java` (replace the class body's fields/methods, keep `onInitializeClient` logging):
```java
    private static NoteStore store;

    public static NoteStore store() {
        if (store == null) {
            Path root = FabricLoader.getInstance().getGameDir().resolve(MOD_ID);
            store = new NoteStore(root, msg -> LOGGER.warn(msg));
        }
        return store;
    }
```
with imports `dev.blaze.notedown.store.NoteStore`, `net.fabricmc.loader.api.FabricLoader`, `java.nio.file.Path`.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'dev.blaze.notedown.store.*'`
Expected: 15 tests pass (5 PinIndex, 10 NoteStore).

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` → BUILD SUCCESSFUL.
```bash
git add -A && git commit -m "note store, pin index, current scope"
```

---

### Task 4: Markdown model, parser and layout engine (paragraphs, inline styles, headings, code, rules)

**Files:**
- Create: `src/client/java/dev/blaze/notedown/markdown/TextMeasure.java`, `markdown/TextStyle.java`, `markdown/Layout.java`, `markdown/MarkdownParser.java`, `markdown/Layouter.java`
- Test: `src/test/java/dev/blaze/notedown/markdown/LayouterTest.java`

**Interfaces:**
- Consumes: `CheckedTaskStyle` (Task 1).
- Produces: `interface TextMeasure { int width(String text, boolean bold); int lineHeight(); }`; `record TextStyle(bold, italic, strike, underline, code, muted, String link)` with `NORMAL` and `withX(...)`; `record Layout(List<Line> lines, List<HitBox> hitBoxes, int height)` with nested `Run(text, style, x)`, `Line(x, y, scale, runs, deco, quoted, width)`, `Deco(kind, label, checked, sourceLine)`, `DecoKind`, `HitKind`, `HitBox(kind, x, y, w, h, sourceLine, url)`; `MarkdownParser.parse(String)` → `Node`; `Layouter.layout(Node, int width, CheckedTaskStyle, TextMeasure)` → `Layout`; constants `Layouter.MARKER_WIDTH = 12`, `TASK_BOX = 9`, `QUOTE_INDENT = 8`, `CODE_PAD = 4`, `PARAGRAPH_GAP = 4`, `LINE_GAP = 2`.
- Layout coordinate rule: all `x`/`y` are unscaled pixels relative to the layout origin. A heading line has `scale > 1`; its `runs[i].x` is in the line's own scaled space and the renderer applies `translate(line.x, line.y)` then `scale(line.scale)` before drawing runs. `Line.width` is always unscaled pixels (already multiplied by the scale), like `x` and `y`. Non-heading lines have `scale == 1`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/dev/blaze/notedown/markdown/LayouterTest.java`
```java
package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayouterTest {

    /** 6 px per code point, 7 px bold, 9 px line height: close to Minecraft's default font. */
    static final TextMeasure FAKE = new TextMeasure() {
        @Override
        public int width(String text, boolean bold) {
            return text.codePointCount(0, text.length()) * (bold ? 7 : 6);
        }

        @Override
        public int lineHeight() {
            return 9;
        }
    };

    static Layout layout(String md, int width) {
        return Layouter.layout(MarkdownParser.parse(md), width, CheckedTaskStyle.STRIKE_MUTED, FAKE);
    }

    static List<String> texts(Layout l) {
        return l.lines().stream().map(line -> line.text().strip()).toList();
    }

    @Test
    void singleLineParagraph() {
        Layout l = layout("hello world", 200);
        assertEquals(List.of("hello world"), texts(l));
        assertEquals(1, l.lines().getFirst().runs().size());
        assertEquals(0, l.lines().getFirst().y());
        assertEquals(11, l.height());
    }

    @Test
    void wrapsAtWordBoundaries() {
        Layout l = layout("aaa bbb ccc", 40);
        assertEquals(List.of("aaa", "bbb", "ccc"), texts(l));
        assertEquals(List.of(0, 11, 22), l.lines().stream().map(Layout.Line::y).toList());
        assertEquals(33, l.height());
    }

    @Test
    void breaksOverlongWordsPerCharacter() {
        Layout l = layout("abcdefghij", 30);
        assertEquals(List.of("abcde", "fghij"), texts(l));
    }

    @Test
    void inlineStylesBecomeRuns() {
        Layout l = layout("**b** *i* ~~s~~ `c`", 300);
        List<Layout.Run> runs = l.lines().getFirst().runs();
        assertEquals(7, runs.size());
        assertTrue(runs.get(0).style().bold());
        assertEquals("b", runs.get(0).text());
        assertTrue(runs.get(2).style().italic());
        assertTrue(runs.get(4).style().strike());
        assertTrue(runs.get(6).style().code());
        assertEquals(7 + 6, runs.get(2).x());
    }

    @Test
    void headingsAreScaledAndBold() {
        Layout l = layout("# Title\ntext", 300);
        Layout.Line h = l.lines().get(0);
        assertEquals(1.5f, h.scale());
        assertTrue(h.runs().getFirst().style().bold());
        assertEquals(14 + 2 + 4, l.lines().get(1).y());
        assertEquals(1.25f, layout("## Two", 300).lines().getFirst().scale());
        assertEquals(1.1f, layout("### Three", 300).lines().getFirst().scale());
        assertEquals(1f, layout("#### Four", 300).lines().getFirst().scale());
    }

    @Test
    void hardBreakSplitsSoftBreakJoins() {
        assertEquals(List.of("a", "b"), texts(layout("a\\\nb", 300)));
        assertEquals(List.of("a b"), texts(layout("a\nb", 300)));
    }

    @Test
    void paragraphsAreSeparatedByGap() {
        Layout l = layout("a\n\nb", 300);
        assertEquals(9 + 2 + 4, l.lines().get(1).y());
    }

    @Test
    void codeBlockLinesKeepSpacingAndDecoration() {
        Layout l = layout("```\nx\n  y\n```", 300);
        assertEquals(2, l.lines().size());
        for (Layout.Line line : l.lines()) {
            assertEquals(Layout.DecoKind.CODE, line.deco().kind());
            assertEquals(Layouter.CODE_PAD, line.x());
            assertTrue(line.runs().getFirst().style().code());
        }
        assertEquals("  y", l.lines().get(1).runs().getFirst().text());
    }

    @Test
    void thematicBreakIsRuleLine() {
        Layout l = layout("a\n\n---\n\nb", 300);
        assertEquals(Layout.DecoKind.RULE, l.lines().get(1).deco().kind());
        assertTrue(l.lines().get(1).runs().isEmpty());
    }

    @Test
    void linksProduceHitBoxes() {
        Layout l = layout("[go](http://x)", 300);
        assertEquals(1, l.hitBoxes().size());
        Layout.HitBox hit = l.hitBoxes().getFirst();
        assertEquals(Layout.HitKind.LINK, hit.kind());
        assertEquals("http://x", hit.url());
        assertEquals(0, hit.x());
        assertEquals(12, hit.w());
        assertEquals(9, hit.h());
        assertTrue(hit.contains(5, 4));
        assertFalse(hit.contains(13, 4));
    }

    @Test
    void htmlIsRenderedLiterally() {
        assertEquals(List.of("<b>raw</b>"), texts(layout("<b>raw</b>", 300)));
    }

    @Test
    void emptyDocumentHasNoLines() {
        Layout l = layout("", 300);
        assertTrue(l.lines().isEmpty());
        assertEquals(0, l.height());
    }

    @Test
    void tinyWidthIsClampedNotCrashing() {
        assertFalse(layout("word", 1).lines().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'dev.blaze.notedown.markdown.LayouterTest'`
Expected: compilation errors, classes missing.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/markdown/TextMeasure.java`
```java
package dev.blaze.notedown.markdown;

public interface TextMeasure {
    int width(String text, boolean bold);

    int lineHeight();
}
```

`src/client/java/dev/blaze/notedown/markdown/TextStyle.java`
```java
package dev.blaze.notedown.markdown;

public record TextStyle(boolean bold, boolean italic, boolean strike, boolean underline, boolean code, boolean muted, String link) {

    public static final TextStyle NORMAL = new TextStyle(false, false, false, false, false, false, null);

    public TextStyle withBold(boolean v) {
        return new TextStyle(v, italic, strike, underline, code, muted, link);
    }

    public TextStyle withItalic(boolean v) {
        return new TextStyle(bold, v, strike, underline, code, muted, link);
    }

    public TextStyle withStrike(boolean v) {
        return new TextStyle(bold, italic, v, underline, code, muted, link);
    }

    public TextStyle withUnderline(boolean v) {
        return new TextStyle(bold, italic, strike, v, code, muted, link);
    }

    public TextStyle withCode(boolean v) {
        return new TextStyle(bold, italic, strike, underline, v, muted, link);
    }

    public TextStyle withMuted(boolean v) {
        return new TextStyle(bold, italic, strike, underline, code, v, link);
    }

    public TextStyle withLink(String url) {
        return new TextStyle(bold, italic, strike, underline, code, muted, url);
    }
}
```

`src/client/java/dev/blaze/notedown/markdown/Layout.java`
```java
package dev.blaze.notedown.markdown;

import java.util.List;

public record Layout(List<Line> lines, List<HitBox> hitBoxes, int height) {

    public static final Layout EMPTY = new Layout(List.of(), List.of(), 0);

    public enum DecoKind { NONE, BULLET, NUMBER, TASK, CODE, RULE }

    public enum HitKind { TASK, LINK }

    public record Deco(DecoKind kind, String label, boolean checked, int sourceLine) {
        public static final Deco NONE = new Deco(DecoKind.NONE, "", false, -1);
        public static final Deco CODE = new Deco(DecoKind.CODE, "", false, -1);
        public static final Deco RULE = new Deco(DecoKind.RULE, "", false, -1);

        public static Deco bullet(String label) {
            return new Deco(DecoKind.BULLET, label, false, -1);
        }

        public static Deco number(String label) {
            return new Deco(DecoKind.NUMBER, label, false, -1);
        }

        public static Deco task(boolean checked, int sourceLine) {
            return new Deco(DecoKind.TASK, "", checked, sourceLine);
        }
    }

    public record Run(String text, TextStyle style, int x) {}

    public record Line(int x, int y, float scale, List<Run> runs, Deco deco, boolean quoted, int width) {
        public Line withDeco(Deco d) {
            return new Line(x, y, scale, runs, d, quoted, width);
        }

        public Line withQuoted(boolean q) {
            return new Line(x, y, scale, runs, deco, q, width);
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) {
                sb.append(r.text());
            }
            return sb.toString();
        }
    }

    public record HitBox(HitKind kind, int x, int y, int w, int h, int sourceLine, String url) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
```

`src/client/java/dev/blaze/notedown/markdown/MarkdownParser.java`
```java
package dev.blaze.notedown.markdown;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.List;

public final class MarkdownParser {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TaskListItemsExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private MarkdownParser() {}

    public static Node parse(String markdown) {
        return PARSER.parse(markdown == null ? "" : markdown);
    }
}
```

`src/client/java/dev/blaze/notedown/markdown/Layouter.java` (Task 5 adds the list and quote cases)
```java
package dev.blaze.notedown.markdown;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayList;
import java.util.List;

public final class Layouter {

    public static final int MARKER_WIDTH = 12;
    public static final int TASK_BOX = 9;
    public static final int QUOTE_INDENT = 8;
    public static final int CODE_PAD = 4;
    public static final int PARAGRAPH_GAP = 4;
    public static final int LINE_GAP = 2;
    public static final int MIN_WIDTH = 20;

    private static final float[] HEADING_SCALE = {1.5f, 1.25f, 1.1f, 1f, 1f, 1f};

    private final TextMeasure measure;
    private final int width;
    private final CheckedTaskStyle checkedStyle;
    private final List<Layout.Line> lines = new ArrayList<>();
    private final List<Layout.HitBox> hits = new ArrayList<>();
    private int y;

    private Layouter(TextMeasure measure, int width, CheckedTaskStyle checkedStyle) {
        this.measure = measure;
        this.width = width;
        this.checkedStyle = checkedStyle;
    }

    public static Layout layout(Node document, int width, CheckedTaskStyle checkedStyle, TextMeasure measure) {
        Layouter l = new Layouter(measure, Math.max(MIN_WIDTH, width), checkedStyle);
        l.blocks(document, 0, 0, false);
        int height = l.lines.isEmpty() ? 0 : Math.max(0, l.y - PARAGRAPH_GAP);
        return new Layout(List.copyOf(l.lines), List.copyOf(l.hits), height);
    }

    private void blocks(Node parent, int indent, int depth, boolean tight) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            block(n, indent, depth, tight);
        }
    }

    private void block(Node n, int indent, int depth, boolean tight) {
        switch (n) {
            case Heading h -> {
                float scale = HEADING_SCALE[Math.min(HEADING_SCALE.length, Math.max(1, h.getLevel())) - 1];
                paragraph(inlines(h, TextStyle.NORMAL.withBold(true)), indent, scale);
                gap();
            }
            case Paragraph p -> {
                paragraph(inlines(p, TextStyle.NORMAL), indent, 1f);
                if (!tight) {
                    gap();
                }
            }
            case FencedCodeBlock c -> code(c.getLiteral(), indent);
            case IndentedCodeBlock c -> code(c.getLiteral(), indent);
            case ThematicBreak t -> {
                lines.add(new Layout.Line(indent, y, 1f, List.of(), Layout.Deco.RULE, false, width - indent));
                y += measure.lineHeight() + LINE_GAP;
                gap();
            }
            case HtmlBlock h -> {
                paragraph(List.of(new Layout.Run(h.getLiteral().strip(), TextStyle.NORMAL, 0)), indent, 1f);
                gap();
            }
            default -> {
                paragraph(inlines(n, TextStyle.NORMAL), indent, 1f);
                gap();
            }
        }
    }

    private void gap() {
        y += PARAGRAPH_GAP;
    }

    private void code(String literal, int indent) {
        String text = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
        int x = indent + CODE_PAD;
        for (String raw : text.split("\n", -1)) {
            String line = raw.replace("\t", "    ");
            List<Layout.Run> runs = line.isEmpty() ? List.of() : List.of(new Layout.Run(line, TextStyle.NORMAL.withCode(true), 0));
            lines.add(new Layout.Line(x, y, 1f, runs, Layout.Deco.CODE, false, width - indent - 2 * CODE_PAD));
            y += measure.lineHeight() + LINE_GAP;
        }
        gap();
    }

    private List<Layout.Run> inlines(Node container, TextStyle base) {
        List<Layout.Run> out = new ArrayList<>();
        collectInlines(container, base, out);
        return out;
    }

    private void collectInlines(Node parent, TextStyle style, List<Layout.Run> out) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
            switch (n) {
                case Text t -> out.add(new Layout.Run(t.getLiteral(), style, 0));
                case SoftLineBreak s -> out.add(new Layout.Run(" ", style, 0));
                case HardLineBreak h -> out.add(new Layout.Run("\n", style, 0));
                case Emphasis e -> collectInlines(e, style.withItalic(true), out);
                case StrongEmphasis e -> collectInlines(e, style.withBold(true), out);
                case Strikethrough s -> collectInlines(s, style.withStrike(true), out);
                case Code c -> out.add(new Layout.Run(c.getLiteral(), style.withCode(true), 0));
                case Link l -> collectInlines(l, style.withLink(l.getDestination()), out);
                case HtmlInline h -> out.add(new Layout.Run(h.getLiteral(), style, 0));
                case TaskListItemMarker m -> { }
                default -> collectInlines(n, style, out);
            }
        }
    }

    /** Word-wraps runs into lines starting at indent. */
    private void paragraph(List<Layout.Run> runs, int indent, float scale) {
        Wrapper w = new Wrapper(indent, scale);
        for (Layout.Run run : runs) {
            w.add(run.text(), run.style());
        }
        w.finish();
    }

    private final class Wrapper {
        private final int indent;
        private final float scale;
        private final int avail;
        private List<Layout.Run> runs = new ArrayList<>();
        private int cx;

        Wrapper(int indent, float scale) {
            this.indent = indent;
            this.scale = scale;
            this.avail = Math.max(1, Math.round((width - indent) / scale));
        }

        void add(String text, TextStyle style) {
            if (text.equals("\n")) {
                flush();
                return;
            }
            for (String token : text.split("(?<=\\s)(?=\\S)")) {
                if (cx > 0 && cx + measure.width(token.stripTrailing(), style.bold()) > avail) {
                    flush();
                }
                if (cx == 0) {
                    token = token.stripLeading();
                    if (token.isEmpty()) {
                        continue;
                    }
                }
                int w = measure.width(token, style.bold());
                if (w > avail) {
                    addBroken(token, style);
                } else {
                    append(token, style, w);
                }
            }
        }

        private void addBroken(String token, TextStyle style) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < token.length()) {
                int cp = token.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                i += Character.charCount(cp);
                if (cx + measure.width(sb + ch, style.bold()) > avail && (cx > 0 || !sb.isEmpty())) {
                    append(sb.toString(), style, measure.width(sb.toString(), style.bold()));
                    sb.setLength(0);
                    flush();
                }
                sb.append(ch);
            }
            if (!sb.isEmpty()) {
                append(sb.toString(), style, measure.width(sb.toString(), style.bold()));
            }
        }

        private void append(String text, TextStyle style, int w) {
            if (text.isEmpty()) {
                return;
            }
            if (!runs.isEmpty() && runs.getLast().style().equals(style)) {
                Layout.Run last = runs.getLast();
                runs.set(runs.size() - 1, new Layout.Run(last.text() + text, style, last.x()));
            } else {
                runs.add(new Layout.Run(text, style, cx));
            }
            cx += w;
        }

        void flush() {
            int lh = Math.round(measure.lineHeight() * scale);
            for (Layout.Run run : runs) {
                if (run.style().link() != null) {
                    int w = Math.round(measure.width(run.text(), run.style().bold()) * scale);
                    hits.add(new Layout.HitBox(Layout.HitKind.LINK, indent + Math.round(run.x() * scale), y, w, lh, -1, run.style().link()));
                }
            }
            lines.add(new Layout.Line(indent, y, scale, List.copyOf(runs), Layout.Deco.NONE, false, Math.round(cx * scale)));
            y += lh + LINE_GAP;
            runs = new ArrayList<>();
            cx = 0;
        }

        void finish() {
            if (!runs.isEmpty()) {
                flush();
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'dev.blaze.notedown.markdown.LayouterTest'`
Expected: 13 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "markdown model, parser and layout engine"
```

---

### Task 5: Lists, tasks, quotes, task toggling, layout cache

**Files:**
- Modify: `src/client/java/dev/blaze/notedown/markdown/Layouter.java`
- Create: `markdown/TaskToggler.java`, `markdown/LayoutCache.java`
- Test: extend `LayouterTest.java`; create `src/test/java/dev/blaze/notedown/markdown/TaskTogglerTest.java`, `LayoutCacheTest.java`

**Interfaces:**
- Produces: `TaskToggler.toggleLine(String body, int lineIndex)` → new body (unchanged when that line is not a task); `LayoutCache.get(String body, int width, CheckedTaskStyle, TextMeasure)` → `Layout` (cached), `LayoutCache.clear()`. Task hit boxes carry `sourceLine` = zero-based line index of the list item in the note body (bodies are `\n`-normalized by `NoteStore`).

- [ ] **Step 1: Write the failing tests**

Append to `LayouterTest.java`:
```java
    @Test
    void bulletListMarkersAndIndent() {
        Layout l = layout("- a\n- b", 300);
        assertEquals(List.of("a", "b"), texts(l));
        for (Layout.Line line : l.lines()) {
            assertEquals(Layout.DecoKind.BULLET, line.deco().kind());
            assertEquals("•", line.deco().label());
            assertEquals(Layouter.MARKER_WIDTH, line.x());
        }
        assertEquals(11, l.lines().get(1).y());
    }

    @Test
    void nestedListsIndentAndCycleBullets() {
        Layout l = layout("- a\n  - b\n    - c", 300);
        assertEquals(Layouter.MARKER_WIDTH * 2, l.lines().get(1).x());
        assertEquals("◦", l.lines().get(1).deco().label());
        assertEquals("▪", l.lines().get(2).deco().label());
    }

    @Test
    void orderedListsNumberFromStart() {
        Layout l = layout("3. a\n4. b", 300);
        assertEquals("3.", l.lines().get(0).deco().label());
        assertEquals("4.", l.lines().get(1).deco().label());
        assertEquals(Layout.DecoKind.NUMBER, l.lines().get(0).deco().kind());
        assertEquals(16, l.lines().get(0).x());
    }

    @Test
    void taskItemsProduceDecoAndHitBoxes() {
        Layout l = layout("- [ ] a\n- [x] b", 300);
        assertEquals(Layout.DecoKind.TASK, l.lines().get(0).deco().kind());
        assertFalse(l.lines().get(0).deco().checked());
        assertTrue(l.lines().get(1).deco().checked());
        assertEquals(0, l.lines().get(0).deco().sourceLine());
        assertEquals(1, l.lines().get(1).deco().sourceLine());
        assertEquals(2, l.hitBoxes().size());
        Layout.HitBox second = l.hitBoxes().get(1);
        assertEquals(Layout.HitKind.TASK, second.kind());
        assertEquals(0, second.x());
        assertEquals(11, second.y());
        assertEquals(Layouter.TASK_BOX, second.w());
        assertEquals(1, second.sourceLine());
        assertTrue(l.lines().get(1).runs().getFirst().style().strike());
        assertTrue(l.lines().get(1).runs().getFirst().style().muted());
        assertFalse(l.lines().get(0).runs().getFirst().style().strike());
    }

    @Test
    void checkedStyleNoneLeavesTextPlain() {
        Layout l = Layouter.layout(MarkdownParser.parse("- [x] b"), 300, CheckedTaskStyle.NONE, FAKE);
        assertFalse(l.lines().getFirst().runs().getFirst().style().strike());
        assertFalse(l.lines().getFirst().runs().getFirst().style().muted());
    }

    @Test
    void taskSourceLineSurvivesPrecedingContent() {
        Layout l = layout("# Head\n\ntext\n\n- [ ] later", 300);
        assertEquals(4, l.hitBoxes().getFirst().sourceLine());
    }

    @Test
    void tasksInsideCodeBlocksAreNotTasks() {
        assertTrue(layout("```\n- [ ] x\n```", 300).hitBoxes().isEmpty());
    }

    @Test
    void blockquotesAreMarkedAndIndented() {
        Layout l = layout("> q", 300);
        assertTrue(l.lines().getFirst().quoted());
        assertEquals(Layouter.QUOTE_INDENT, l.lines().getFirst().x());
    }

    @Test
    void looseListsGetParagraphGaps() {
        assertEquals(11, layout("- a\n- b", 300).lines().get(1).y());
        assertEquals(15, layout("- a\n\n- b", 300).lines().get(1).y());
    }

    @Test
    void emptyListItemStillGetsMarker() {
        Layout l = layout("-", 300);
        assertEquals(1, l.lines().size());
        assertEquals(Layout.DecoKind.BULLET, l.lines().getFirst().deco().kind());
        assertTrue(l.lines().getFirst().runs().isEmpty());
    }

    @Test
    void continuationParagraphInItemHasNoMarker() {
        Layout l = layout("- a\n\n  second", 300);
        assertEquals(Layout.DecoKind.NONE, l.lines().get(1).deco().kind());
        assertEquals(Layouter.MARKER_WIDTH, l.lines().get(1).x());
    }
```

`src/test/java/dev/blaze/notedown/markdown/TaskTogglerTest.java`
```java
package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskTogglerTest {

    @Test
    void togglesOnlyTheGivenLine() {
        String body = "- [ ] a\n- [x] b\n- [ ] c";
        assertEquals("- [x] a\n- [x] b\n- [ ] c", TaskToggler.toggleLine(body, 0));
        assertEquals("- [ ] a\n- [ ] b\n- [ ] c", TaskToggler.toggleLine(body, 1));
    }

    @Test
    void acceptsOtherMarkersAndIndentation() {
        assertEquals("  * [x] a", TaskToggler.toggleLine("  * [ ] a", 0));
        assertEquals("1. [ ] a", TaskToggler.toggleLine("1. [X] a", 0));
    }

    @Test
    void ignoresNonTaskLinesAndBadIndexes() {
        assertEquals("plain\n- [ ] a", TaskToggler.toggleLine("plain\n- [ ] a", 0));
        assertEquals("- [ ] a", TaskToggler.toggleLine("- [ ] a", 5));
        assertEquals("- [ ] a", TaskToggler.toggleLine("- [ ] a", -1));
    }

    @Test
    void preservesTrailingNewline() {
        assertEquals("- [x] a\n", TaskToggler.toggleLine("- [ ] a\n", 0));
    }
}
```

`src/test/java/dev/blaze/notedown/markdown/LayoutCacheTest.java`
```java
package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LayoutCacheTest {

    @Test
    void reusesLayoutForSameInputs() {
        LayoutCache cache = new LayoutCache();
        Layout a = cache.get("hi", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE);
        Layout b = cache.get("hi", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE);
        assertSame(a, b);
        assertNotSame(a, cache.get("hi", 120, CheckedTaskStyle.NONE, LayouterTest.FAKE));
        assertNotSame(a, cache.get("hi", 100, CheckedTaskStyle.MUTED, LayouterTest.FAKE));
        assertNotSame(a, cache.get("ho", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE));
        cache.clear();
        assertNotSame(a, cache.get("hi", 100, CheckedTaskStyle.NONE, LayouterTest.FAKE));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'dev.blaze.notedown.markdown.*'`
Expected: list tests fail (markers missing), `TaskToggler`/`LayoutCache` do not compile.

- [ ] **Step 3: Implement**

In `Layouter.java` add imports `org.commonmark.node.BlockQuote`, `BulletList`, `OrderedList`, `ListBlock`, `ListItem` and the field `private static final String[] BULLETS = {"•", "◦", "▪"};`. Insert these three cases into `block(...)` before `default`:
```java
            case BlockQuote q -> quote(q, indent, depth);
            case BulletList l -> {
                list(l, indent, depth, null);
                if (!tight) {
                    gap();
                }
            }
            case OrderedList l -> {
                list(l, indent, depth, l.getMarkerStartNumber() == null ? 1 : l.getMarkerStartNumber());
                if (!tight) {
                    gap();
                }
            }
```
Add these methods:
```java
    private void quote(BlockQuote q, int indent, int depth) {
        int start = lines.size();
        blocks(q, indent + QUOTE_INDENT, depth, false);
        for (int i = start; i < lines.size(); i++) {
            lines.set(i, lines.get(i).withQuoted(true));
        }
    }

    private void list(ListBlock list, int indent, int depth, Integer start) {
        int number = start == null ? 0 : start;
        boolean tight = list.isTight();
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem li)) {
                continue;
            }
            TaskListItemMarker marker = taskMarker(li);
            String label = start == null ? BULLETS[depth % BULLETS.length] : (number++) + ".";
            int markerWidth = marker == null && start != null ? Math.max(MARKER_WIDTH, measure.width(label, false) + 4) : MARKER_WIDTH;
            int textIndent = indent + markerWidth;
            TextStyle base = marker == null ? TextStyle.NORMAL : checkedStyle(marker.isChecked());
            int before = lines.size();
            for (Node child = li.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Paragraph p) {
                    paragraph(inlines(p, base), textIndent, 1f);
                    if (!tight) {
                        gap();
                    }
                } else {
                    block(child, textIndent, depth + 1, tight);
                }
            }
            if (lines.size() == before) {
                lines.add(new Layout.Line(textIndent, y, 1f, List.of(), Layout.Deco.NONE, false, 0));
                y += measure.lineHeight() + LINE_GAP;
                if (!tight) {
                    gap();
                }
            }
            Layout.Line first = lines.get(before);
            int sourceLine = li.getSourceSpans().isEmpty() ? -1 : li.getSourceSpans().getFirst().getLineIndex();
            Layout.Deco deco = marker != null ? Layout.Deco.task(marker.isChecked(), sourceLine)
                    : start == null ? Layout.Deco.bullet(label) : Layout.Deco.number(label);
            lines.set(before, first.withDeco(deco));
            if (marker != null) {
                int boxY = first.y() + (measure.lineHeight() - TASK_BOX) / 2;
                hits.add(new Layout.HitBox(Layout.HitKind.TASK, first.x() - MARKER_WIDTH, boxY, TASK_BOX, TASK_BOX, sourceLine, null));
            }
        }
    }

    private static TaskListItemMarker taskMarker(ListItem li) {
        return li.getFirstChild() instanceof Paragraph p && p.getFirstChild() instanceof TaskListItemMarker m ? m : null;
    }

    private TextStyle checkedStyle(boolean checked) {
        if (!checked) {
            return TextStyle.NORMAL;
        }
        return switch (checkedStyle) {
            case STRIKE_MUTED -> TextStyle.NORMAL.withStrike(true).withMuted(true);
            case MUTED -> TextStyle.NORMAL.withMuted(true);
            case NONE -> TextStyle.NORMAL;
        };
    }
```

`src/client/java/dev/blaze/notedown/markdown/TaskToggler.java`
```java
package dev.blaze.notedown.markdown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TaskToggler {

    private static final Pattern TASK = Pattern.compile("^(\\s*(?:[-*+]|\\d+[.)])\\s+\\[)([ xX])(\\].*)$");

    private TaskToggler() {}

    /** Flips the checkbox on the given zero-based line; returns the body unchanged if that line is not a task. */
    public static String toggleLine(String body, int lineIndex) {
        if (lineIndex < 0) {
            return body;
        }
        String[] lines = body.split("\n", -1);
        if (lineIndex >= lines.length) {
            return body;
        }
        Matcher m = TASK.matcher(lines[lineIndex]);
        if (!m.matches()) {
            return body;
        }
        String box = m.group(2).equals(" ") ? "x" : " ";
        lines[lineIndex] = m.group(1) + box + m.group(3);
        return String.join("\n", lines);
    }
}
```
`src/client/java/dev/blaze/notedown/markdown/LayoutCache.java`
```java
package dev.blaze.notedown.markdown;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LayoutCache {

    private static final int CAPACITY = 16;

    private record Key(String body, int width, CheckedTaskStyle style) {}

    private final Map<Key, Layout> cache = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Layout> eldest) {
            return size() > CAPACITY;
        }
    };

    public Layout get(String body, int width, CheckedTaskStyle style, TextMeasure measure) {
        Key key = new Key(body, width, style);
        Layout layout = cache.get(key);
        if (layout == null) {
            layout = Layouter.layout(MarkdownParser.parse(body), width, style, measure);
            cache.put(key, layout);
        }
        return layout;
    }

    public void clear() {
        cache.clear();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests 'dev.blaze.notedown.markdown.*'`
Expected: all Layouter (24), TaskToggler (4), LayoutCache (1) tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "lists, tasks, quotes, task toggling, layout cache"
```

---

### Task 6: Editor commands and undo stack

**Files:**
- Create: `src/client/java/dev/blaze/notedown/markdown/EditorCommands.java`, `markdown/UndoStack.java`
- Test: `src/test/java/dev/blaze/notedown/markdown/EditorCommandsTest.java`, `UndoStackTest.java`

**Interfaces:**
- Produces: `record EditorCommands.Edit(String text, int cursor)`; `EditorCommands.enter(text, cursor)`, `indent(text, cursor, boolean outdent)`, `wrap(text, selStart, selEnd, String marker)`, `toggleTask(text, cursor)`, `titleFromBody(body)` → `String`; `UndoStack(String initial, int limit, long coalesceMs)` with `record(String value, long nowMs)`, `Optional<String> undo()`, `Optional<String> redo()`, `String current()`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/dev/blaze/notedown/markdown/EditorCommandsTest.java`
```java
package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditorCommandsTest {

    @Test
    void enterContinuesBulletList() {
        EditorCommands.Edit e = EditorCommands.enter("- apples", 8);
        assertEquals("- apples\n- ", e.text());
        assertEquals(11, e.cursor());
    }

    @Test
    void enterContinuesTaskAndNumberedLists() {
        assertEquals("- [x] done\n- [ ] ", EditorCommands.enter("- [x] done", 10).text());
        assertEquals("  2. two\n  3. ", EditorCommands.enter("  2. two", 8).text());
        assertEquals("1) a\n2) ", EditorCommands.enter("1) a", 4).text());
    }

    @Test
    void enterOnEmptyItemRemovesMarker() {
        EditorCommands.Edit e = EditorCommands.enter("- a\n- ", 6);
        assertEquals("- a\n", e.text());
        assertEquals(4, e.cursor());
        assertEquals("- a\n", EditorCommands.enter("- a\n- [ ] ", 10).text());
    }

    @Test
    void enterMidLineSplitsPlainText() {
        EditorCommands.Edit e = EditorCommands.enter("hello world", 5);
        assertEquals("hello\n world", e.text());
        assertEquals(6, e.cursor());
        assertEquals("\n", EditorCommands.enter("", 0).text());
    }

    @Test
    void indentAndOutdent() {
        assertEquals(new EditorCommands.Edit("  - a", 5), EditorCommands.indent("- a", 3, false));
        assertEquals(new EditorCommands.Edit("- a", 3), EditorCommands.indent("  - a", 5, true));
        assertEquals(new EditorCommands.Edit("- a", 0), EditorCommands.indent(" - a", 1, true));
        assertEquals(new EditorCommands.Edit("x\n  y", 5), EditorCommands.indent("x\ny", 3, false));
    }

    @Test
    void wrapSelectionAndUnwrap() {
        assertEquals(new EditorCommands.Edit("a **b** c", 7), EditorCommands.wrap("a b c", 2, 3, "**"));
        assertEquals(new EditorCommands.Edit("a b c", 3), EditorCommands.wrap("a **b** c", 4, 5, "**"));
        assertEquals(new EditorCommands.Edit("a b c", 3), EditorCommands.wrap("a **b** c", 2, 7, "**"));
        assertEquals(new EditorCommands.Edit("**", 1), EditorCommands.wrap("", 0, 0, "*"));
    }

    @Test
    void toggleTaskCyclesStates() {
        assertEquals("- [ ] plain", EditorCommands.toggleTask("plain", 3).text());
        assertEquals("- [ ] item", EditorCommands.toggleTask("- item", 3).text());
        assertEquals("- [x] item", EditorCommands.toggleTask("- [ ] item", 3).text());
        assertEquals("- [ ] item", EditorCommands.toggleTask("- [x] item", 3).text());
        EditorCommands.Edit e = EditorCommands.toggleTask("a\nb", 3);
        assertEquals("a\n- [ ] b", e.text());
        assertEquals(9, e.cursor());
    }

    @Test
    void titleFromBody() {
        assertEquals("Starter House", EditorCommands.titleFromBody("\n\n# Starter House\nx"));
        assertEquals("do it", EditorCommands.titleFromBody("- [ ] **do** _it_"));
        assertEquals("", EditorCommands.titleFromBody("  \n"));
        assertEquals(40, EditorCommands.titleFromBody("x".repeat(60)).length());
    }
}
```

`src/test/java/dev/blaze/notedown/markdown/UndoStackTest.java`
```java
package dev.blaze.notedown.markdown;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UndoStackTest {

    @Test
    void undoAndRedo() {
        UndoStack s = new UndoStack("", 100, 0);
        s.record("a", 0);
        s.record("ab", 5000);
        assertEquals(Optional.of("a"), s.undo());
        assertEquals(Optional.of(""), s.undo());
        assertEquals(Optional.empty(), s.undo());
        assertEquals(Optional.of("a"), s.redo());
        assertEquals(Optional.of("ab"), s.redo());
        assertEquals(Optional.empty(), s.redo());
    }

    @Test
    void recordingClearsRedoAndIgnoresNoChange() {
        UndoStack s = new UndoStack("", 100, 0);
        s.record("a", 0);
        s.undo();
        s.record("b", 5000);
        assertEquals(Optional.empty(), s.redo());
        s.record("b", 6000);
        assertEquals(Optional.of(""), s.undo());
    }

    @Test
    void coalescesQuickSingleCharacterTyping() {
        UndoStack s = new UndoStack("", 100, 700);
        s.record("h", 0);
        s.record("he", 100);
        s.record("hey", 200);
        assertEquals(Optional.of(""), s.undo());
        assertEquals("", s.current());
    }

    @Test
    void doesNotCoalesceAfterPause() {
        UndoStack s = new UndoStack("", 100, 700);
        s.record("h", 0);
        s.record("he", 1000);
        assertEquals(Optional.of("h"), s.undo());
    }

    @Test
    void limitDropsOldest() {
        UndoStack s = new UndoStack("0", 2, 0);
        s.record("1", 0);
        s.record("2", 5000);
        s.record("3", 10000);
        assertEquals(Optional.of("2"), s.undo());
        assertEquals(Optional.of("1"), s.undo());
        assertEquals(Optional.empty(), s.undo());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'dev.blaze.notedown.markdown.EditorCommandsTest' --tests 'dev.blaze.notedown.markdown.UndoStackTest'`
Expected: compilation errors.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/markdown/EditorCommands.java`
```java
package dev.blaze.notedown.markdown;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditorCommands {

    public record Edit(String text, int cursor) {}

    public static final int MAX_TITLE = 40;

    private static final Pattern LIST_LINE = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])(\\s+)(\\[[ xX]\\]\\s+)?(.*)$");
    private static final Pattern TASK_BOX = Pattern.compile("^(\\s*(?:[-*+]|\\d+[.)])\\s+\\[)([ xX])(\\].*)$");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");
    private static final Pattern LEADING_MARKERS = Pattern.compile("^(?:[-*+]|\\d+[.)])\\s+(?:\\[[ xX]\\]\\s*)?");
    private static final Pattern INLINE_MARKERS = Pattern.compile("[*_`~>]");

    private EditorCommands() {}

    public static Edit enter(String text, int cursor) {
        int lineStart = lineStart(text, cursor);
        Matcher m = LIST_LINE.matcher(text.substring(lineStart, cursor));
        if (!m.matches()) {
            return insert(text, cursor, "\n");
        }
        if (m.group(5).isEmpty()) {
            return new Edit(text.substring(0, lineStart) + text.substring(cursor), lineStart);
        }
        String marker = m.group(2);
        if (Character.isDigit(marker.charAt(0))) {
            String delimiter = marker.substring(marker.length() - 1);
            int n = Integer.parseInt(marker.substring(0, marker.length() - 1));
            marker = (n + 1) + delimiter;
        }
        String prefix = "\n" + m.group(1) + marker + m.group(3) + (m.group(4) != null ? "[ ] " : "");
        return insert(text, cursor, prefix);
    }

    public static Edit indent(String text, int cursor, boolean outdent) {
        int ls = lineStart(text, cursor);
        if (!outdent) {
            return new Edit(text.substring(0, ls) + "  " + text.substring(ls), cursor + 2);
        }
        int remove = 0;
        while (remove < 2 && ls + remove < text.length() && text.charAt(ls + remove) == ' ') {
            remove++;
        }
        return new Edit(text.substring(0, ls) + text.substring(ls + remove), Math.max(ls, cursor - remove));
    }

    public static Edit wrap(String text, int selStart, int selEnd, String marker) {
        int a = Math.min(selStart, selEnd);
        int b = Math.max(selStart, selEnd);
        String sel = text.substring(a, b);
        int ml = marker.length();
        if (sel.length() >= 2 * ml && sel.startsWith(marker) && sel.endsWith(marker)) {
            String inner = sel.substring(ml, sel.length() - ml);
            return new Edit(text.substring(0, a) + inner + text.substring(b), a + inner.length());
        }
        if (a >= ml && b + ml <= text.length() && text.startsWith(marker, a - ml) && text.startsWith(marker, b)) {
            return new Edit(text.substring(0, a - ml) + sel + text.substring(b + ml), a - ml + sel.length());
        }
        String wrapped = text.substring(0, a) + marker + sel + marker + text.substring(b);
        return new Edit(wrapped, sel.isEmpty() ? a + ml : a + ml + sel.length() + ml);
    }

    public static Edit toggleTask(String text, int cursor) {
        int ls = lineStart(text, cursor);
        int le = lineEnd(text, cursor);
        String line = text.substring(ls, le);
        Matcher box = TASK_BOX.matcher(line);
        String replaced;
        if (box.matches()) {
            replaced = box.group(1) + (box.group(2).equals(" ") ? "x" : " ") + box.group(3);
        } else {
            Matcher li = LIST_LINE.matcher(line);
            replaced = li.matches() ? li.group(1) + li.group(2) + li.group(3) + "[ ] " + li.group(5) : "- [ ] " + line;
        }
        String result = text.substring(0, ls) + replaced + text.substring(le);
        return new Edit(result, Math.min(result.length(), cursor + (replaced.length() - line.length())));
    }

    public static String titleFromBody(String body) {
        for (String raw : body.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                line = h.group(1);
            }
            line = LEADING_MARKERS.matcher(line).replaceFirst("");
            line = INLINE_MARKERS.matcher(line).replaceAll("").strip();
            if (line.isEmpty()) {
                continue;
            }
            return line.length() > MAX_TITLE ? line.substring(0, MAX_TITLE).strip() : line;
        }
        return "";
    }

    static int lineStart(String text, int cursor) {
        if (cursor <= 0) {
            return 0;
        }
        return text.lastIndexOf('\n', cursor - 1) + 1;
    }

    static int lineEnd(String text, int cursor) {
        int end = text.indexOf('\n', cursor);
        return end < 0 ? text.length() : end;
    }

    private static Edit insert(String text, int at, String s) {
        return new Edit(text.substring(0, at) + s + text.substring(at), at + s.length());
    }
}
```

`src/client/java/dev/blaze/notedown/markdown/UndoStack.java`
```java
package dev.blaze.notedown.markdown;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class UndoStack {

    private final Deque<String> undo = new ArrayDeque<>();
    private final Deque<String> redo = new ArrayDeque<>();
    private final int limit;
    private final long coalesceMs;
    private String current;
    private long lastRecord = Long.MIN_VALUE / 2;

    public UndoStack(String initial, int limit, long coalesceMs) {
        this.current = initial;
        this.limit = limit;
        this.coalesceMs = coalesceMs;
    }

    public String current() {
        return current;
    }

    public void record(String value, long nowMs) {
        if (value.equals(current)) {
            return;
        }
        boolean quickTyping = nowMs - lastRecord < coalesceMs && Math.abs(value.length() - current.length()) == 1;
        if (!quickTyping || undo.isEmpty()) {
            undo.push(current);
            while (undo.size() > limit) {
                undo.removeLast();
            }
        }
        current = value;
        redo.clear();
        lastRecord = nowMs;
    }

    public Optional<String> undo() {
        if (undo.isEmpty()) {
            return Optional.empty();
        }
        redo.push(current);
        current = undo.pop();
        lastRecord = Long.MIN_VALUE / 2;
        return Optional.of(current);
    }

    public Optional<String> redo() {
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        undo.push(current);
        current = redo.pop();
        lastRecord = Long.MIN_VALUE / 2;
        return Optional.of(current);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: all tests pass (config 4, store 21, markdown 42).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "editor commands and undo stack"
```

---

### Task 7: Flat UI kit

**Files:**
- Create: `src/client/java/dev/blaze/notedown/ui/Theme.java`, `ui/Messages.java`, `ui/FontMeasure.java`, `ui/Scroller.java`, `ui/FlatButton.java`, `ui/FlatTextField.java`, `ui/FlatList.java`, `ui/Toggle.java`, `ui/Slider.java`, `ui/ConfirmDialog.java`
- Test: `src/test/java/dev/blaze/notedown/ui/ScrollerTest.java`

**Interfaces:**
- Produces: `Theme` constants (`ACCENT`, `ACCENT_DARK`, `TEXT`, `TEXT_MUTED`, `TEXT_DISABLED`, `PANEL`, `PANEL_DARK`, `PANEL_HOVER`, `PANEL_LIGHT`, `HIGHLIGHT`, `BORDER`, `BUTTON_HEIGHT = 20`, `MARGIN = 5`, `PAD = 8`, `ROW_HEIGHT = 24`, `SCROLLBAR = 7`) and helpers `border(g, x1, y1, x2, y2, color)`, `scrollbar(g, x, y, trackH, Scroller, boolean active)`, `withAlpha(int rgb, float alpha)`, `ellipsize(Font, String, int width)`; `Messages.t(key, args...)` → `Component` with `notedown.` prefix, `Messages.chat(Minecraft, Component)`; `FontMeasure(Font)` implements `TextMeasure`; `Scroller` with `update(contentH, viewH)`, `amount()`, `max()`, `visible()`, `scrollBy(int)`, `scrollTo(int)`, `pageBy(int dir)`, `thumbHeight(track)`, `thumbY(track)`, `dragTo(track, thumbTop)`; `FlatButton(x, y, w, h, Component, Runnable)` with `leftAlign(boolean)`, `selected(boolean)` (chainable); `FlatTextField(Font, x, y, w, h, Component hint)`; `FlatList<T>(x, y, w, h, rowHeight, RowRenderer<T>, Consumer<T> onSelect, Consumer<T> onActivate)` with `setItems(List<T>, T keep)`, `selected()`, `select(int)`, `setBounds(x,y,w,h)`; `Toggle(x, y, w, Component, boolean, Consumer<Boolean>)`; `Slider(x, y, w, Component, min, max, step, value, Function<Float,String>, Consumer<Float>)`; `ConfirmDialog(Screen parent, Component title, Component body, Component yesLabel, Runnable onYes)`.

- [ ] **Step 1: Write the failing Scroller test**

`src/test/java/dev/blaze/notedown/ui/ScrollerTest.java`
```java
package dev.blaze.notedown.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrollerTest {

    @Test
    void noScrollWhenContentFits() {
        Scroller s = new Scroller();
        s.update(50, 100);
        assertFalse(s.visible());
        assertEquals(0, s.max());
        s.scrollBy(30);
        assertEquals(0, s.amount());
        assertEquals(100, s.thumbHeight(100));
    }

    @Test
    void clampsAndTracksThumb() {
        Scroller s = new Scroller();
        s.update(400, 100);
        assertTrue(s.visible());
        assertEquals(300, s.max());
        s.scrollBy(-10);
        assertEquals(0, s.amount());
        s.scrollBy(1000);
        assertEquals(300, s.amount());
        assertEquals(25, s.thumbHeight(100));
        assertEquals(75, s.thumbY(100));
        s.scrollTo(150);
        assertEquals(37, s.thumbY(100));
    }

    @Test
    void dragMapsThumbToAmount() {
        Scroller s = new Scroller();
        s.update(400, 100);
        s.dragTo(100, 75);
        assertEquals(300, s.amount());
        s.dragTo(100, 0);
        assertEquals(0, s.amount());
        s.dragTo(100, 500);
        assertEquals(300, s.amount());
    }

    @Test
    void pageMovesByViewHeightMinusOverlap() {
        Scroller s = new Scroller();
        s.update(400, 100);
        s.pageBy(1);
        assertEquals(90, s.amount());
        s.pageBy(-1);
        assertEquals(0, s.amount());
    }

    @Test
    void updateKeepsAmountInRange() {
        Scroller s = new Scroller();
        s.update(400, 100);
        s.scrollTo(300);
        s.update(150, 100);
        assertEquals(50, s.amount());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'dev.blaze.notedown.ui.ScrollerTest'`
Expected: compilation error.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/ui/Scroller.java`
```java
package dev.blaze.notedown.ui;

public final class Scroller {

    private static final int MIN_THUMB = 8;
    private static final int PAGE_OVERLAP = 10;

    private int content;
    private int view;
    private int amount;

    public void update(int contentHeight, int viewHeight) {
        content = Math.max(0, contentHeight);
        view = Math.max(1, viewHeight);
        scrollTo(amount);
    }

    public int amount() {
        return amount;
    }

    public int max() {
        return Math.max(0, content - view);
    }

    public boolean visible() {
        return content > view;
    }

    public void scrollBy(int delta) {
        scrollTo(amount + delta);
    }

    public void scrollTo(int value) {
        amount = Math.max(0, Math.min(max(), value));
    }

    public void pageBy(int direction) {
        scrollBy(direction * Math.max(1, view - PAGE_OVERLAP));
    }

    public int thumbHeight(int track) {
        return visible() ? Math.max(MIN_THUMB, (int) ((long) track * view / content)) : track;
    }

    public int thumbY(int track) {
        int room = track - thumbHeight(track);
        return max() == 0 || room <= 0 ? 0 : (int) ((long) room * amount / max());
    }

    public void dragTo(int track, int thumbTop) {
        int room = track - thumbHeight(track);
        if (room <= 0) {
            return;
        }
        scrollTo((int) Math.round((double) Math.max(0, Math.min(room, thumbTop)) * max() / room));
    }
}
```

`src/client/java/dev/blaze/notedown/ui/Theme.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Theme {

    public static final int ACCENT = 0xFF94E4D3;
    public static final int ACCENT_DARK = 0xFF7A9E9E;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFBBBBBB;
    public static final int TEXT_DISABLED = 0xFFAAAAAA;
    public static final int TEXT_CODE = 0xFFDDDDDD;
    public static final int PANEL = 0x90000000;
    public static final int PANEL_DARK = 0xB0000000;
    public static final int PANEL_HOVER = 0xE0000000;
    public static final int PANEL_LIGHT = 0x40000000;
    public static final int HIGHLIGHT = 0x08FFFFFF;
    public static final int BORDER = 0x8000FFEE;

    public static final int BUTTON_HEIGHT = 20;
    public static final int MARGIN = 5;
    public static final int PAD = 8;
    public static final int ROW_HEIGHT = 24;
    public static final int SCROLLBAR = 7;

    private Theme() {}

    public static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int trackHeight, Scroller scroller, boolean active) {
        g.fill(x, y, x + SCROLLBAR, y + trackHeight, PANEL_LIGHT);
        int thumbY = y + scroller.thumbY(trackHeight);
        g.fill(x, thumbY, x + SCROLLBAR, thumbY + scroller.thumbHeight(trackHeight), active ? ACCENT : TEXT_MUTED);
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String dots = "…";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(dots))) + dots;
    }
}
```

`src/client/java/dev/blaze/notedown/ui/Messages.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Messages {

    private Messages() {}

    public static void chat(Minecraft mc, Component message) {
        mc.gui.chatListener().handleSystemMessage(message, false);
    }

    public static Component t(String key, Object... args) {
        return Component.translatable("notedown." + key, args);
    }
}
```

`src/client/java/dev/blaze/notedown/ui/FontMeasure.java`
```java
package dev.blaze.notedown.ui;

import dev.blaze.notedown.markdown.TextMeasure;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public record FontMeasure(Font font) implements TextMeasure {

    private static final Style BOLD = Style.EMPTY.withBold(true);

    @Override
    public int width(String text, boolean bold) {
        return bold ? font.width(Component.literal(text).withStyle(BOLD)) : font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }
}
```

`src/client/java/dev/blaze/notedown/ui/FlatButton.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class FlatButton extends AbstractButton {

    private final Runnable action;
    private boolean leftAlign;
    private boolean selected;

    public FlatButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    public FlatButton leftAlign(boolean value) {
        leftAlign = value;
        return this;
    }

    public FlatButton selected(boolean value) {
        selected = value;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        int bg = !active ? Theme.PANEL_LIGHT : isHoveredOrFocused() ? Theme.PANEL_HOVER : Theme.PANEL;
        g.fill(x1, y1, x2, y2, bg);
        Font font = Minecraft.getInstance().font;
        int color = active ? Theme.TEXT : Theme.TEXT_DISABLED;
        int ty = y1 + (getHeight() - 8) / 2;
        if (leftAlign) {
            g.text(font, getMessage(), x1 + Theme.PAD, ty, color);
        } else {
            g.centeredText(font, getMessage(), x1 + getWidth() / 2, ty, color);
        }
        if (selected) {
            g.fill(x1, y2 - 1, x2, y2, Theme.ACCENT);
        }
        if (isFocused() && active) {
            Theme.border(g, x1, y1, x2, y2, Theme.BORDER);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
```

`src/client/java/dev/blaze/notedown/ui/FlatTextField.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class FlatTextField extends EditBox {

    private final int panelX;
    private final int panelY;
    private final int panelW;
    private final int panelH;

    /** Panel spans the given box; the vanilla (unbordered) text box sits inside it, vertically centred. */
    public FlatTextField(Font font, int x, int y, int w, int h, Component hint) {
        super(font, x + Theme.PAD, y + (h - 8) / 2, w - 2 * Theme.PAD, h - (h - 8) / 2, hint);
        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;
        setBordered(false);
        setHint(hint);
        setMaxLength(512);
        setTextColor(Theme.TEXT);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, isFocused() ? Theme.PANEL_DARK : Theme.PANEL);
        if (isFocused()) {
            Theme.border(g, panelX, panelY, panelX + panelW, panelY + panelH, Theme.BORDER);
        }
        super.extractWidgetRenderState(g, mouseX, mouseY, partial);
    }
}
```

`src/client/java/dev/blaze/notedown/ui/FlatList.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class FlatList<T> extends AbstractWidget {

    public interface RowRenderer<T> {
        void render(GuiGraphicsExtractor g, T item, int x, int y, int w, int h, boolean selected, boolean hovered);
    }

    private final int rowHeight;
    private final RowRenderer<T> renderer;
    private final Consumer<T> onSelect;
    private final Consumer<T> onActivate;
    private final Scroller scroller = new Scroller();
    private List<T> items = List.of();
    private int selected = -1;
    private boolean draggingBar;

    public FlatList(int x, int y, int w, int h, int rowHeight, RowRenderer<T> renderer, Consumer<T> onSelect, Consumer<T> onActivate) {
        super(x, y, w, h, Component.empty());
        this.rowHeight = rowHeight;
        this.renderer = renderer;
        this.onSelect = onSelect;
        this.onActivate = onActivate;
    }

    public void setItems(List<T> newItems, T keep) {
        items = List.copyOf(newItems);
        selected = keep == null ? -1 : items.indexOf(keep);
        scroller.update(items.size() * rowHeight, getHeight());
        ensureVisible();
    }

    public Optional<T> selected() {
        return selected >= 0 && selected < items.size() ? Optional.of(items.get(selected)) : Optional.empty();
    }

    public void select(int index) {
        if (items.isEmpty()) {
            selected = -1;
            return;
        }
        selected = Math.max(0, Math.min(items.size() - 1, index));
        ensureVisible();
        onSelect.accept(items.get(selected));
    }

    public void setBounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        scroller.update(items.size() * rowHeight, h);
    }

    private int rowWidth() {
        return getWidth() - (scroller.visible() ? Theme.SCROLLBAR + 2 : 0);
    }

    private int barX() {
        return getX() + getWidth() - Theme.SCROLLBAR;
    }

    private boolean overBar(double mx, double my) {
        return scroller.visible() && mx >= barX() && mx < barX() + Theme.SCROLLBAR && my >= getY() && my < getY() + getHeight();
    }

    private int indexAt(double my) {
        int i = (int) ((my - getY() + scroller.amount()) / rowHeight);
        return i >= 0 && i < items.size() ? i : -1;
    }

    private void ensureVisible() {
        if (selected < 0) {
            return;
        }
        int top = selected * rowHeight;
        if (top < scroller.amount()) {
            scroller.scrollTo(top);
        } else if (top + rowHeight > scroller.amount() + getHeight()) {
            scroller.scrollTo(top + rowHeight - getHeight());
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        scroller.update(items.size() * rowHeight, getHeight());
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        g.fill(x1, y1, x2, y2, Theme.PANEL);
        g.enableScissor(x1, y1, x2, y2);
        int rowW = rowWidth();
        boolean mouseInside = isMouseOver(mouseX, mouseY) && !overBar(mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            int ry = y1 + i * rowHeight - scroller.amount();
            if (ry + rowHeight < y1 || ry > y2) {
                continue;
            }
            boolean isSelected = i == selected;
            boolean hovered = mouseInside && mouseY >= ry && mouseY < ry + rowHeight;
            if (isSelected) {
                g.fill(x1, ry, x1 + rowW, ry + rowHeight, Theme.PANEL_HOVER);
                g.fill(x1, ry, x1 + 2, ry + rowHeight, Theme.ACCENT);
            } else if (hovered) {
                g.fill(x1, ry, x1 + rowW, ry + rowHeight, Theme.PANEL_DARK);
            }
            renderer.render(g, items.get(i), x1 + Theme.PAD, ry, rowW - 2 * Theme.PAD, rowHeight, isSelected, hovered);
        }
        g.disableScissor();
        if (scroller.visible()) {
            Theme.scrollbar(g, barX(), y1, getHeight(), scroller, draggingBar);
        }
        if (isFocused()) {
            Theme.border(g, x1, y1, x2, y2, Theme.BORDER);
        }
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean doubleClick) {
        if (overBar(e.x(), e.y())) {
            draggingBar = true;
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
            return;
        }
        int i = indexAt(e.y());
        if (i < 0) {
            return;
        }
        select(i);
        if (doubleClick) {
            onActivate.accept(items.get(i));
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar) {
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        draggingBar = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroller.scrollBy((int) Math.round(-vertical * rowHeight));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused() || items.isEmpty()) {
            return false;
        }
        if (e.isUp()) {
            select(selected - 1);
            return true;
        }
        if (e.isDown()) {
            select(selected + 1);
            return true;
        }
        if (e.isConfirmation()) {
            selected().ifPresent(onActivate);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

`src/client/java/dev/blaze/notedown/ui/Toggle.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class Toggle extends AbstractButton {

    private static final int BOX = 10;

    private final Consumer<Boolean> onChange;
    private boolean on;

    public Toggle(int x, int y, int w, Component label, boolean on, Consumer<Boolean> onChange) {
        super(x, y, w, Theme.BUTTON_HEIGHT, label);
        this.on = on;
        this.onChange = onChange;
    }

    public boolean isOn() {
        return on;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        on = !on;
        onChange.accept(on);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        if (isHoveredOrFocused()) {
            g.fill(x1, y1, x1 + getWidth(), y1 + getHeight(), Theme.HIGHLIGHT);
        }
        g.text(Minecraft.getInstance().font, getMessage(), x1 + Theme.PAD, y1 + (getHeight() - 8) / 2, active ? Theme.TEXT : Theme.TEXT_DISABLED);
        int bx = x1 + getWidth() - Theme.PAD - BOX;
        int by = y1 + (getHeight() - BOX) / 2;
        Theme.border(g, bx, by, bx + BOX, by + BOX, on ? Theme.ACCENT : Theme.TEXT_MUTED);
        if (on) {
            g.fill(bx + 2, by + 2, bx + BOX - 2, by + BOX - 2, Theme.ACCENT);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
```

`src/client/java/dev/blaze/notedown/ui/Slider.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Slider extends AbstractWidget {

    private static final int TRACK_W = 90;
    private static final int TRACK_H = 10;
    private static final int THUMB_W = 4;

    private final float min;
    private final float max;
    private final float step;
    private final Function<Float, String> format;
    private final Consumer<Float> onChange;
    private float value;
    private boolean dragging;

    public Slider(int x, int y, int w, Component label, float min, float max, float step, float value,
                  Function<Float, String> format, Consumer<Float> onChange) {
        super(x, y, w, Theme.BUTTON_HEIGHT, label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.format = format;
        this.onChange = onChange;
    }

    private int trackX() {
        return getX() + getWidth() - Theme.PAD - TRACK_W;
    }

    private void set(float v) {
        float snapped = Math.round(v / step) * step;
        snapped = Math.max(min, Math.min(max, snapped));
        if (snapped != value) {
            value = snapped;
            onChange.accept(value);
        }
    }

    private void setFromMouse(double mx) {
        double f = (mx - trackX() - THUMB_W / 2.0) / (TRACK_W - THUMB_W);
        set(min + (float) Math.max(0, Math.min(1, f)) * (max - min));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        if (isHoveredOrFocused()) {
            g.fill(x1, y1, x1 + getWidth(), y1 + getHeight(), Theme.HIGHLIGHT);
        }
        Font font = Minecraft.getInstance().font;
        int ty = y1 + (getHeight() - 8) / 2;
        g.text(font, getMessage(), x1 + Theme.PAD, ty, active ? Theme.TEXT : Theme.TEXT_DISABLED);
        int tx = trackX();
        int tyTrack = y1 + (getHeight() - TRACK_H) / 2;
        g.fill(tx, tyTrack, tx + TRACK_W, tyTrack + TRACK_H, Theme.PANEL_LIGHT);
        int thumbX = tx + Math.round((value - min) / (max - min) * (TRACK_W - THUMB_W));
        g.fill(thumbX, tyTrack, thumbX + THUMB_W, tyTrack + TRACK_H, dragging || isFocused() ? Theme.ACCENT : Theme.TEXT_MUTED);
        String label = format.apply(value);
        g.text(font, label, tx - 6 - font.width(label), ty, Theme.TEXT_MUTED);
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean doubleClick) {
        dragging = true;
        setFromMouse(e.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent e, double dx, double dy) {
        if (dragging) {
            setFromMouse(e.x());
        }
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        dragging = false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) {
            return false;
        }
        if (e.isLeft()) {
            set(value - step);
            return true;
        }
        if (e.isRight()) {
            set(value + step);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

`src/client/java/dev/blaze/notedown/ui/ConfirmDialog.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class ConfirmDialog extends Screen {

    private static final int BOX_W = 260;

    private final Screen parent;
    private final Component body;
    private final Component yesLabel;
    private final Runnable onYes;
    private List<FormattedCharSequence> bodyLines = List.of();
    private int boxH;

    public ConfirmDialog(Screen parent, Component title, Component body, Component yesLabel, Runnable onYes) {
        super(title);
        this.parent = parent;
        this.body = body;
        this.yesLabel = yesLabel;
        this.onYes = onYes;
    }

    @Override
    protected void init() {
        bodyLines = font.split(body, BOX_W - 2 * Theme.PAD);
        boxH = Theme.PAD + font.lineHeight + 6 + bodyLines.size() * (font.lineHeight + 2) + 10 + Theme.BUTTON_HEIGHT + Theme.PAD;
        int x = (width - BOX_W) / 2;
        int y = (height - boxH) / 2;
        int by = y + boxH - Theme.PAD - Theme.BUTTON_HEIGHT;
        int bw = (BOX_W - 3 * Theme.PAD) / 2;
        addRenderableWidget(new FlatButton(x + Theme.PAD, by, bw, Theme.BUTTON_HEIGHT, yesLabel, this::confirm));
        addRenderableWidget(new FlatButton(x + 2 * Theme.PAD + bw, by, bw, Theme.BUTTON_HEIGHT, Messages.t("button.cancel"), this::onClose));
    }

    private void confirm() {
        onYes.run();
        if (minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = (width - BOX_W) / 2;
        int y = (height - boxH) / 2;
        g.fill(x, y, x + BOX_W, y + boxH, Theme.PANEL_DARK);
        Theme.border(g, x, y, x + BOX_W, y + boxH, Theme.BORDER);
        g.text(font, title, x + Theme.PAD, y + Theme.PAD, Theme.TEXT);
        int ty = y + Theme.PAD + font.lineHeight + 6;
        for (FormattedCharSequence line : bodyLines) {
            g.text(font, line, x + Theme.PAD, ty, Theme.TEXT_MUTED);
            ty += font.lineHeight + 2;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
```

- [ ] **Step 4: Run tests and build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, `ScrollerTest` 5 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "flat ui kit"
```

---

### Task 8: Markdown drawing, markdown view widget, flat text area

**Files:**
- Create: `src/client/java/dev/blaze/notedown/ui/LayoutRenderer.java`, `ui/MarkdownView.java`, `ui/FlatTextArea.java`
- Modify: `Notedown.java` (add `layouts()`)

**Interfaces:**
- Consumes: `Layout`, `Layouter` constants, `LayoutCache`, `CheckedTaskStyle`, `ConfigHolder`, `Theme`, `Scroller`, `FontMeasure`.
- Produces: `LayoutRenderer.draw(g, font, layout, originX, originY, float scale, clipX1, clipY1, clipX2, clipY2)`; `MarkdownView(x, y, w, h, LayoutCache, IntConsumer onToggleTask, Consumer<String> onLink)` with `setBody(String)`, `setBounds(x,y,w,h)`, `scrollToTop()`; `FlatTextArea(Font, x, y, w, h, Component placeholder)` with `insertAtCursor(String)`, `cursor()`, `selectionStart()`, `selectionEnd()`, `replaceAll(String text, int cursor)`; `Notedown.layouts()` → shared `LayoutCache`.

- [ ] **Step 1: Implement**

`src/client/java/dev/blaze/notedown/ui/LayoutRenderer.java`
```java
package dev.blaze.notedown.ui;

import dev.blaze.notedown.markdown.Layout;
import dev.blaze.notedown.markdown.Layouter;
import dev.blaze.notedown.markdown.TextStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class LayoutRenderer {

    private LayoutRenderer() {}

    /** Draws layout with its origin at (originX, originY), scaled by scale, clipped to the given screen rect. */
    public static void draw(GuiGraphicsExtractor g, Font font, Layout layout, int originX, int originY, float scale,
                            int clipX1, int clipY1, int clipX2, int clipY2) {
        g.enableScissor(clipX1, clipY1, clipX2, clipY2);
        g.pose().pushMatrix();
        g.pose().translate(originX, originY);
        g.pose().scale(scale, scale);
        for (Layout.Line line : layout.lines()) {
            float top = originY + line.y() * scale;
            float bottom = top + (font.lineHeight * line.scale() + Layouter.LINE_GAP) * scale;
            if (bottom < clipY1 || top > clipY2) {
                continue;
            }
            drawLine(g, font, line);
        }
        g.pose().popMatrix();
        g.disableScissor();
    }

    private static void drawLine(GuiGraphicsExtractor g, Font font, Layout.Line line) {
        int lh = Math.round(font.lineHeight * line.scale());
        int x = line.x();
        int y = line.y();
        if (line.quoted()) {
            int bx = x - Layouter.QUOTE_INDENT + 2;
            g.fill(bx, y - 1, bx + 2, y + lh + Layouter.LINE_GAP, Theme.ACCENT_DARK);
        }
        switch (line.deco().kind()) {
            case BULLET, NUMBER -> g.text(font, line.deco().label(), x - Layouter.MARKER_WIDTH, y, Theme.TEXT_MUTED);
            case TASK -> {
                int bx = x - Layouter.MARKER_WIDTH;
                int by = y + (font.lineHeight - Layouter.TASK_BOX) / 2;
                boolean checked = line.deco().checked();
                Theme.border(g, bx, by, bx + Layouter.TASK_BOX, by + Layouter.TASK_BOX, checked ? Theme.ACCENT : Theme.TEXT_MUTED);
                if (checked) {
                    g.fill(bx + 2, by + 2, bx + Layouter.TASK_BOX - 2, by + Layouter.TASK_BOX - 2, Theme.ACCENT);
                }
            }
            case CODE -> g.fill(x - Layouter.CODE_PAD, y - 1, x + line.width() + Layouter.CODE_PAD, y + lh + Layouter.LINE_GAP + 1, Theme.PANEL_DARK);
            case RULE -> {
                g.fill(x, y + lh / 2, x + line.width(), y + lh / 2 + 1, Theme.TEXT_DISABLED);
                return;
            }
            case NONE -> { }
        }
        if (line.scale() != 1f) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(line.scale(), line.scale());
            drawRuns(g, font, line, 0, 0);
            g.pose().popMatrix();
        } else {
            drawRuns(g, font, line, x, y);
        }
    }

    private static void drawRuns(GuiGraphicsExtractor g, Font font, Layout.Line line, int baseX, int baseY) {
        for (Layout.Run run : line.runs()) {
            int rx = baseX + run.x();
            Component text = styled(run);
            if (run.style().code()) {
                g.fill(rx - 1, baseY - 1, rx + font.width(text) + 1, baseY + font.lineHeight, Theme.PANEL_LIGHT);
            }
            g.text(font, text, rx, baseY, color(run.style(), line.quoted()));
        }
    }

    private static Component styled(Layout.Run run) {
        TextStyle s = run.style();
        Style style = Style.EMPTY.withBold(s.bold()).withItalic(s.italic()).withStrikethrough(s.strike())
                .withUnderlined(s.underline() || s.link() != null);
        return Component.literal(run.text()).withStyle(style);
    }

    private static int color(TextStyle s, boolean quoted) {
        if (s.link() != null) {
            return Theme.ACCENT;
        }
        if (s.muted() || quoted) {
            return Theme.TEXT_MUTED;
        }
        return s.code() ? Theme.TEXT_CODE : Theme.TEXT;
    }
}
```

`src/client/java/dev/blaze/notedown/ui/MarkdownView.java`
```java
package dev.blaze.notedown.ui;

import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.markdown.Layout;
import dev.blaze.notedown.markdown.LayoutCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MarkdownView extends AbstractWidget {

    private static final int WHEEL_STEP = 20;

    private final LayoutCache cache;
    private final IntConsumer onToggleTask;
    private final Consumer<String> onLink;
    private final Scroller scroller = new Scroller();
    private String body = "";
    private Layout layout = Layout.EMPTY;
    private boolean draggingBar;

    public MarkdownView(int x, int y, int w, int h, LayoutCache cache, IntConsumer onToggleTask, Consumer<String> onLink) {
        super(x, y, w, h, Component.empty());
        this.cache = cache;
        this.onToggleTask = onToggleTask;
        this.onLink = onLink;
    }

    public void setBody(String newBody) {
        body = newBody == null ? "" : newBody;
        relayout();
    }

    public void setBounds(int x, int y, int w, int h) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        relayout();
    }

    public void scrollToTop() {
        scroller.scrollTo(0);
    }

    private void relayout() {
        Font font = Minecraft.getInstance().font;
        int contentW = getWidth() - 2 * Theme.PAD - Theme.SCROLLBAR - 2;
        layout = cache.get(body, contentW, ConfigHolder.get().checkedTaskStyle, new FontMeasure(font));
        scroller.update(layout.height() + 2 * Theme.PAD, getHeight());
    }

    private int barX() {
        return getX() + getWidth() - Theme.SCROLLBAR;
    }

    private boolean overBar(double mx, double my) {
        return scroller.visible() && mx >= barX() && mx < barX() + Theme.SCROLLBAR && my >= getY() && my < getY() + getHeight();
    }

    private Optional<Layout.HitBox> hitAt(double mx, double my) {
        double lx = mx - (getX() + Theme.PAD);
        double ly = my - (getY() + Theme.PAD) + scroller.amount();
        return layout.hitBoxes().stream().filter(h -> h.contains(lx, ly)).findFirst();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x1 = getX();
        int y1 = getY();
        int x2 = x1 + getWidth();
        int y2 = y1 + getHeight();
        g.fill(x1, y1, x2, y2, Theme.PANEL);
        LayoutRenderer.draw(g, Minecraft.getInstance().font, layout, x1 + Theme.PAD, y1 + Theme.PAD - scroller.amount(), 1f,
                x1, y1, x2 - (scroller.visible() ? Theme.SCROLLBAR : 0), y2);
        if (scroller.visible()) {
            Theme.scrollbar(g, barX(), y1, getHeight(), scroller, draggingBar);
        }
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean doubleClick) {
        if (overBar(e.x(), e.y())) {
            draggingBar = true;
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
            return;
        }
        hitAt(e.x(), e.y()).ifPresent(hit -> {
            if (hit.kind() == Layout.HitKind.TASK) {
                onToggleTask.accept(hit.sourceLine());
            } else {
                onLink.accept(hit.url());
            }
        });
    }

    @Override
    protected void onDrag(MouseButtonEvent e, double dx, double dy) {
        if (draggingBar) {
            scroller.dragTo(getHeight(), (int) (e.y() - getY() - scroller.thumbHeight(getHeight()) / 2));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        draggingBar = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroller.scrollBy((int) Math.round(-vertical * WHEEL_STEP));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (!isFocused()) {
            return false;
        }
        switch (e.key()) {
            case GLFW.GLFW_KEY_PAGE_UP -> scroller.pageBy(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroller.pageBy(1);
            case GLFW.GLFW_KEY_HOME -> scroller.scrollTo(0);
            case GLFW.GLFW_KEY_END -> scroller.scrollTo(scroller.max());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

`src/client/java/dev/blaze/notedown/ui/FlatTextArea.java`
```java
package dev.blaze.notedown.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.network.chat.Component;

public final class FlatTextArea extends MultiLineEditBox {

    public FlatTextArea(Font font, int x, int y, int w, int h, Component placeholder) {
        super(font, x, y, w, h, placeholder, Component.empty(), Theme.TEXT, true, Theme.TEXT, true, true);
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor g) {
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isFocused() ? Theme.PANEL_DARK : Theme.PANEL);
    }

    @Override
    protected void extractBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (isFocused()) {
            Theme.border(g, getX(), getY(), getX() + getWidth(), getY() + getHeight(), Theme.BORDER);
        }
    }

    @Override
    protected void extractScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (maxScrollAmount() <= 0) {
            return;
        }
        int x = scrollBarX();
        g.fill(x, getY(), x + Theme.SCROLLBAR, getY() + getHeight(), Theme.PANEL_LIGHT);
        g.fill(x, scrollBarY(), x + Theme.SCROLLBAR, scrollBarY() + scrollerHeight(), Theme.TEXT_MUTED);
    }

    public void insertAtCursor(String text) {
        textField.insertText(text);
    }

    public int cursor() {
        return textField.cursor();
    }

    public int selectionStart() {
        return textField.getSelected().beginIndex();
    }

    public int selectionEnd() {
        return textField.getSelected().endIndex();
    }

    public void replaceAll(String text, int cursor) {
        setValue(text);
        textField.seekCursor(Whence.ABSOLUTE, Math.max(0, Math.min(cursor, text.length())));
    }
}
```

Add to `Notedown.java`:
```java
    private static final LayoutCache LAYOUTS = new LayoutCache();

    public static LayoutCache layouts() {
        return LAYOUTS;
    }
```
with import `dev.blaze.notedown.markdown.LayoutCache`.

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. If Loom rejects the access widener namespace, change the first line of `notedown.accesswidener` to `accessWidener v2 official` and rebuild (that is the only accepted alternative).

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "markdown renderer, view widget, flat text area"
```

---

### Task 9: Keybinds, game info, edit screen, entrypoint wiring

**Files:**
- Create: `src/client/java/dev/blaze/notedown/NotedownKeys.java`, `gui/GameInfo.java`, `gui/EditScreen.java`
- Modify: `Notedown.java`

**Interfaces:**
- Consumes: `NoteStore`, `Note`, `ScopeDir`, `NoteFiles`, `CurrentScope`, `ConfigHolder`, `EditorCommands`, `UndoStack`, `TaskToggler`, `FlatButton`, `FlatTextField`, `FlatTextArea`, `MarkdownView`, `ConfirmDialog`, `Messages`, `Theme`.
- Produces: `NotedownKeys.OPEN/INTERACT/QUICK_EDIT/TOGGLE_PINNED/NEW_NOTE`, `NotedownKeys.register()`, `NotedownKeys.all()`, `NotedownKeys.isPhysicallyDown(Minecraft, KeyMapping)`; `GameInfo.inWorld(mc)`, `coords(mc)`, `chunk(mc)`, `biome(mc)`; `EditScreen(Screen returnTo, Note note /*nullable*/, ScopeDir defaultScope)` and `static EditScreen.open(Screen returnTo, Note note, ScopeDir scope)` (refuses notes over `NoteStore.MAX_EDITABLE_BYTES` with a chat message). Save returns to `returnTo` (`null` = back to the game). Shortcuts: Ctrl+S save, Ctrl+E preview, Ctrl+Z / Ctrl+Y undo/redo, Ctrl+D bold, Ctrl+I italic, Ctrl+Shift+C toggle task, Enter continues lists, Tab / Shift+Tab indent. (Ctrl+B is Minecraft's global narrator hotkey, hence Ctrl+D for bold.)

- [ ] **Step 1: Keybinds and game info**

`src/client/java/dev/blaze/notedown/NotedownKeys.java`
```java
package dev.blaze.notedown;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class NotedownKeys {
    public static KeyMapping OPEN;
    public static KeyMapping INTERACT;
    public static KeyMapping QUICK_EDIT;
    public static KeyMapping TOGGLE_PINNED;
    public static KeyMapping NEW_NOTE;

    private NotedownKeys() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Notedown.id("main"));
        OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
        INTERACT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.interact", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));
        QUICK_EDIT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.quick_edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        TOGGLE_PINNED = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.toggle_pinned", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        NEW_NOTE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.notedown.new_note", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
    }

    public static List<KeyMapping> all() {
        return List.of(OPEN, INTERACT, QUICK_EDIT, TOGGLE_PINNED, NEW_NOTE);
    }

    /** True while the bound keyboard key is held, even when a screen is open. */
    public static boolean isPhysicallyDown(Minecraft mc, KeyMapping key) {
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
        return bound.getType() == InputConstants.Type.KEYSYM && !key.isUnbound()
                && InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
    }
}
```

`src/client/java/dev/blaze/notedown/gui/GameInfo.java`
```java
package dev.blaze.notedown.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

public final class GameInfo {

    private GameInfo() {}

    public static boolean inWorld(Minecraft mc) {
        return mc.level != null && mc.player != null;
    }

    public static String coords(Minecraft mc) {
        return mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
    }

    public static String chunk(Minecraft mc) {
        ChunkPos c = mc.player.chunkPosition();
        return c.x() + ", " + c.z();
    }

    public static String biome(Minecraft mc) {
        return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(key -> {
            String langKey = key.identifier().toLanguageKey("biome");
            String name = I18n.get(langKey);
            return name.equals(langKey) ? prettify(key.identifier().getPath()) : name;
        }).orElse("Unknown");
    }

    static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: Edit screen**

`src/client/java/dev/blaze/notedown/gui/EditScreen.java`
```java
package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.markdown.EditorCommands;
import dev.blaze.notedown.markdown.TaskToggler;
import dev.blaze.notedown.markdown.UndoStack;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteFiles;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.ScopeDir;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.FlatTextArea;
import dev.blaze.notedown.ui.FlatTextField;
import dev.blaze.notedown.ui.MarkdownView;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class EditScreen extends Screen {

    private static final int SIDE_W = 110;
    private static final int TOP = 12;
    private static final int UNDO_LIMIT = 100;
    private static final long COALESCE_MS = 700;

    private final Screen returnTo;
    private final NoteStore store;
    private final ScopeDir currentScope;
    private final ScopeDir globalScope;
    private final UndoStack undo;
    private Note note;
    private ScopeDir scope;
    private String title;
    private String body;
    private int cursor;
    private String savedTitle;
    private String savedBody;
    private ScopeDir savedScope;
    private boolean previewing;
    private boolean applying;
    private FlatTextField titleField;
    private FlatTextArea bodyArea;
    private MarkdownView preview;
    private FlatButton previewButton;
    private FlatButton scopeButton;

    public EditScreen(Screen returnTo, Note note, ScopeDir defaultScope) {
        super(Messages.t(note == null ? "screen.edit.new" : "screen.edit.edit"));
        this.returnTo = returnTo;
        this.store = Notedown.store();
        this.note = note;
        List<ScopeDir> dirs = CurrentScope.dirs(store, Minecraft.getInstance());
        this.globalScope = dirs.getLast();
        this.currentScope = dirs.size() > 1 ? dirs.getFirst() : null;
        this.scope = note != null ? note.scope() : defaultScope;
        this.title = note != null ? note.title() : "";
        this.body = note != null ? note.body() : "";
        this.savedTitle = title;
        this.savedBody = body;
        this.savedScope = scope;
        this.undo = new UndoStack(body, UNDO_LIMIT, COALESCE_MS);
    }

    /** Opens the editor unless the note is too large for the in-game text box. */
    public static void open(Screen returnTo, Note note, ScopeDir scope) {
        Minecraft mc = Minecraft.getInstance();
        if (note != null && note.body().length() > NoteStore.MAX_EDITABLE_BYTES) {
            Messages.chat(mc, Messages.t("message.too_large"));
            return;
        }
        mc.gui.setScreen(new EditScreen(returnTo, note, scope));
    }

    @Override
    protected void init() {
        int x = Theme.MARGIN;
        int y = TOP;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.save"), this::saveAndClose));
        y += 25;
        previewButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, previewLabel(), this::togglePreview));
        y += 25;
        Minecraft mc = minecraft;
        if (ConfigHolder.get().showInsertButtons && GameInfo.inWorld(mc)) {
            y += 5;
            addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.insert_coords"), () -> insert(GameInfo.coords(mc))));
            y += 25;
            addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.insert_chunk"), () -> insert(GameInfo.chunk(mc))));
            y += 25;
            addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.insert_biome"), () -> insert(GameInfo.biome(mc))));
            y += 25;
        }
        y += 5;
        scopeButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, scopeLabel(), this::toggleScope));
        scopeButton.active = currentScope != null;
        addRenderableWidget(new FlatButton(x, height - Theme.MARGIN - Theme.BUTTON_HEIGHT, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.back"), this::onClose));

        int cx = Theme.MARGIN * 2 + SIDE_W;
        int cw = width - cx - Theme.MARGIN;
        titleField = new FlatTextField(font, cx, TOP, cw, Theme.BUTTON_HEIGHT, Messages.t("screen.edit.title_hint"));
        titleField.setValue(title);
        titleField.setResponder(v -> title = v);
        addRenderableWidget(titleField);

        int by = TOP + Theme.BUTTON_HEIGHT + 6;
        int bh = height - by - Theme.MARGIN;
        bodyArea = new FlatTextArea(font, cx, by, cw, bh, Messages.t("screen.edit.body_hint"));
        bodyArea.replaceAll(body, cursor);
        bodyArea.setValueListener(this::onBodyChanged);
        addRenderableWidget(bodyArea);

        preview = new MarkdownView(cx, by, cw, bh, Notedown.layouts(), this::toggleTaskInPreview, this::openLink);
        preview.setBody(body);
        addRenderableWidget(preview);
        applyPreviewState();
        setInitialFocus(previewing ? preview : bodyArea);
    }

    @Override
    public void removed() {
        if (bodyArea != null) {
            cursor = bodyArea.cursor();
        }
    }

    private Component previewLabel() {
        return Messages.t(previewing ? "button.source" : "button.preview");
    }

    private Component scopeLabel() {
        return Messages.t("button.scope", Component.translatable(scope.kind().langKey()));
    }

    private void applyPreviewState() {
        bodyArea.visible = !previewing;
        bodyArea.active = !previewing;
        preview.visible = previewing;
        preview.active = previewing;
        previewButton.setMessage(previewLabel());
    }

    private void togglePreview() {
        previewing = !previewing;
        if (previewing) {
            preview.setBody(body);
        }
        applyPreviewState();
        setFocused(previewing ? preview : bodyArea);
    }

    private void toggleScope() {
        if (currentScope == null) {
            return;
        }
        scope = scope.isGlobal() ? currentScope : globalScope;
        scopeButton.setMessage(scopeLabel());
    }

    private void insert(String text) {
        if (previewing) {
            togglePreview();
        }
        bodyArea.insertAtCursor(text);
        setFocused(bodyArea);
    }

    private void onBodyChanged(String value) {
        body = value;
        if (!applying) {
            undo.record(value, System.currentTimeMillis());
        }
    }

    private void toggleTaskInPreview(int line) {
        String toggled = TaskToggler.toggleLine(body, line);
        bodyArea.replaceAll(toggled, Math.min(cursor, toggled.length()));
        preview.setBody(body);
    }

    private void openLink(String url) {
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.link_title"), Messages.t("dialog.link_body", url),
                Messages.t("button.open"), () -> Util.getPlatform().openUri(url)));
    }

    private boolean dirty() {
        return !title.equals(savedTitle) || !body.equals(savedBody) || !scope.dir().equals(savedScope.dir());
    }

    private boolean save() {
        String effectiveTitle = title.isBlank() ? EditorCommands.titleFromBody(body) : title;
        try {
            note = note == null ? store.create(scope, effectiveTitle, body) : store.save(note, effectiveTitle, body, scope);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to save note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
            return false;
        }
        title = note.title();
        titleField.setValue(title);
        savedTitle = title;
        savedBody = note.body();
        body = savedBody;
        savedScope = scope;
        return true;
    }

    private void saveAndClose() {
        if (save()) {
            close();
        }
    }

    private void close() {
        minecraft.gui.setScreen(returnTo);
    }

    @Override
    public void onClose() {
        if (!dirty()) {
            close();
            return;
        }
        String shown = title.isBlank() ? NoteFiles.UNTITLED : title;
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.discard_title"), Messages.t("dialog.discard_body", shown),
                Messages.t("button.discard"), this::close));
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean ctrl = e.hasControlDown();
        if (ctrl && e.key() == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (ctrl && e.key() == GLFW.GLFW_KEY_E) {
            togglePreview();
            return true;
        }
        if (!previewing && getFocused() == bodyArea) {
            if (ctrl && e.key() == GLFW.GLFW_KEY_Z && !e.hasShiftDown()) {
                apply(undo.undo());
                return true;
            }
            if (ctrl && (e.key() == GLFW.GLFW_KEY_Y || (e.key() == GLFW.GLFW_KEY_Z && e.hasShiftDown()))) {
                apply(undo.redo());
                return true;
            }
            if (ctrl && e.key() == GLFW.GLFW_KEY_D) {
                command(EditorCommands.wrap(body, bodyArea.selectionStart(), bodyArea.selectionEnd(), "**"));
                return true;
            }
            if (ctrl && e.key() == GLFW.GLFW_KEY_I) {
                command(EditorCommands.wrap(body, bodyArea.selectionStart(), bodyArea.selectionEnd(), "*"));
                return true;
            }
            if (ctrl && e.hasShiftDown() && e.key() == GLFW.GLFW_KEY_C) {
                command(EditorCommands.toggleTask(body, bodyArea.cursor()));
                return true;
            }
            if ((e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) && !ctrl && !e.hasShiftDown()) {
                command(EditorCommands.enter(body, bodyArea.cursor()));
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_TAB) {
                command(EditorCommands.indent(body, bodyArea.cursor(), e.hasShiftDown()));
                return true;
            }
        }
        return super.keyPressed(e);
    }

    private void command(EditorCommands.Edit edit) {
        bodyArea.replaceAll(edit.text(), edit.cursor());
    }

    private void apply(Optional<String> value) {
        value.ifPresent(v -> {
            applying = true;
            bodyArea.replaceAll(v, Math.min(bodyArea.cursor(), v.length()));
            applying = false;
        });
    }
}
```

- [ ] **Step 3: Wire the entrypoint**

Replace `Notedown.java` with:
```java
package dev.blaze.notedown;

import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.gui.EditScreen;
import dev.blaze.notedown.markdown.LayoutCache;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.NoteStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Notedown implements ClientModInitializer {
    public static final String MOD_ID = "notedown";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final LayoutCache LAYOUTS = new LayoutCache();
    private static NoteStore store;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static NoteStore store() {
        if (store == null) {
            Path root = FabricLoader.getInstance().getGameDir().resolve(MOD_ID);
            store = new NoteStore(root, msg -> LOGGER.warn(msg));
        }
        return store;
    }

    public static LayoutCache layouts() {
        return LAYOUTS;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Notedown loaded");
        ConfigHolder.get();
        NotedownKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(Notedown::tick);
    }

    private static void tick(Minecraft mc) {
        while (NotedownKeys.NEW_NOTE.consumeClick()) {
            if (mc.level != null) {
                EditScreen.open(null, null, CurrentScope.dirs(store(), mc).getFirst());
            }
        }
    }
}
```

- [ ] **Step 4: Build, install, verify by hand**

Run: `./gradlew build && scripts/dev-install.sh`, remove `Notes-*.jar` from the Prism mods folder, launch FO 26.2, open a world. In Options → Controls bind "New note" (Notedown category) to a key, press it:
- Editor opens over a blurred world with flat panels; typing works, hint text shows when empty.
- Type `- [ ] first`, press Enter → next line starts with `- [ ] `; press Enter on the empty item → marker removed. Tab / Shift+Tab indent the line. Ctrl+D wraps selection in `**`.
- Preview button (and Ctrl+E) shows rendered markdown; clicking the checkbox there flips `[ ]` in the source.
- Coordinates / Chunk / Biome insert at the cursor. Scope button toggles World/Global.
- Save with an empty title → file named from the first line appears in `minecraft/notedown/local/<world>/`. Esc with changes → discard dialog.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "keybinds, edit screen, entrypoint wiring"
```

---

### Task 10: Pinned HUD core

**Files:**
- Create: `src/client/java/dev/blaze/notedown/hud/PinGeometry.java`, `hud/PinnedNotes.java`, `hud/PinRenderer.java`, `hud/PinnedHudElement.java`
- Modify: `Notedown.java`, `gui/EditScreen.java`
- Test: `src/test/java/dev/blaze/notedown/hud/PinGeometryTest.java`

**Interfaces:**
- Consumes: `PinIndex.Pin`, `NoteStore`, `Note`, `ScopeDir`, `CurrentScope`, `LayoutCache`, `Layouter`, `LayoutRenderer`, `FontMeasure`, `Theme`, `TaskToggler`.
- Produces: `PinGeometry` (`MIN_W = 60`, `MIN_H = 30`, `PAD = 5`, `HEADER = 12`, `HANDLE = 8`; `record Rect(x, y, w, h)` with `contains`, `right()`, `bottom()`; `rect(Pin, screenW, screenH)`, `moved(Pin, Rect, dx, dy, screenW, screenH)`, `resized(...)`, `defaultPin(index, screenW, screenH, scale)`, `header(Rect)`, `handle(Rect)`, `closeBox(Rect)`, `maxScroll(contentHeight, scale, Rect, interact)`); `PinnedNotes.Entry` (public fields `scope`, `note`, `pin`, `scroll`), `PinnedNotes.entries()`, `reload()`, `clear()`, `isPinned(Note)`, `toggle(Note, screenW, screenH)`, `unpin(Entry)`, `saveGeometry()`, `first()`; `PinRenderer.draw(g, mc, boolean interact, mouseX, mouseY)`, `PinRenderer.hitTest(mx, my, screenW, screenH, interact)` → `Optional<Hit>` where `record Hit(Entry entry, HitKind kind, int sourceLine, String url, Rect rect)` and `enum HitKind { HEADER, CLOSE, HANDLE, TASK, LINK, BODY }`, `PinRenderer.toggleTask(Entry, line)`, `PinRenderer.scroll(Entry, double vertical, screenW, screenH, interact)`; `PinnedHudElement` (Fabric `HudElement`).

- [ ] **Step 1: Write the failing test**

`src/test/java/dev/blaze/notedown/hud/PinGeometryTest.java`
```java
package dev.blaze.notedown.hud;

import dev.blaze.notedown.store.PinIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PinGeometryTest {

    @Test
    void rectScalesFractionsAndClampsOnScreen() {
        PinIndex.Pin pin = new PinIndex.Pin(0.5, 0.25, 100, 50, 1f, 0);
        assertEquals(new PinGeometry.Rect(200, 60, 100, 50), PinGeometry.rect(pin, 400, 240));
        PinIndex.Pin offscreen = new PinIndex.Pin(0.95, 0.95, 100, 50, 1f, 0);
        assertEquals(new PinGeometry.Rect(300, 190, 100, 50), PinGeometry.rect(offscreen, 400, 240));
        PinIndex.Pin tiny = new PinIndex.Pin(0, 0, 1, 1, 1f, 0);
        assertEquals(new PinGeometry.Rect(0, 0, PinGeometry.MIN_W, PinGeometry.MIN_H), PinGeometry.rect(tiny, 400, 240));
    }

    @Test
    void movedStaysOnScreenAndStoresFractions() {
        PinIndex.Pin pin = new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 0);
        PinGeometry.Rect r = PinGeometry.rect(pin, 400, 240);
        PinIndex.Pin moved = PinGeometry.moved(pin, r, 1000, -1000, 400, 240);
        assertEquals(0.75, moved.x(), 1e-9);
        assertEquals(0.0, moved.y(), 1e-9);
        assertEquals(100, moved.w());
    }

    @Test
    void resizedRespectsMinimumAndScreen() {
        PinIndex.Pin pin = new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 0);
        PinGeometry.Rect r = PinGeometry.rect(pin, 400, 240);
        assertEquals(new PinIndex.Pin(0.5, 0.5, PinGeometry.MIN_W, PinGeometry.MIN_H, 1f, 0), PinGeometry.resized(pin, r, -500, -500, 400, 240));
        PinIndex.Pin grown = PinGeometry.resized(pin, r, 500, 500, 400, 240);
        assertEquals(200, grown.w());
        assertEquals(120, grown.h());
    }

    @Test
    void defaultPinsStackDownTheRightEdge() {
        PinIndex.Pin a = PinGeometry.defaultPin(0, 400, 240, 1.5f);
        PinIndex.Pin b = PinGeometry.defaultPin(1, 400, 240, 1.5f);
        PinGeometry.Rect ra = PinGeometry.rect(a, 400, 240);
        PinGeometry.Rect rb = PinGeometry.rect(b, 400, 240);
        assertEquals(400 - ra.w() - 6, ra.x());
        assertEquals(6, ra.y());
        assertEquals(1.5f, a.scale());
        assertTrue(rb.y() > ra.bottom());
        assertTrue(rb.bottom() <= 240);
    }

    @Test
    void regionsAndScroll() {
        PinGeometry.Rect r = new PinGeometry.Rect(10, 20, 100, 60);
        assertEquals(new PinGeometry.Rect(10, 20, 100, PinGeometry.HEADER), PinGeometry.header(r));
        assertEquals(new PinGeometry.Rect(102, 72, 8, 8), PinGeometry.handle(r));
        assertEquals(new PinGeometry.Rect(98, 20, 12, 12), PinGeometry.closeBox(r));
        assertEquals(0, PinGeometry.maxScroll(40, 1f, r, false));
        assertEquals(50, PinGeometry.maxScroll(100, 1f, r, false));
        assertEquals(62, PinGeometry.maxScroll(100, 1f, r, true));
        assertEquals(150, PinGeometry.maxScroll(100, 2f, r, false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'dev.blaze.notedown.hud.PinGeometryTest'`
Expected: compilation error.

- [ ] **Step 3: Implement**

`src/client/java/dev/blaze/notedown/hud/PinGeometry.java`
```java
package dev.blaze.notedown.hud;

import dev.blaze.notedown.store.PinIndex;

public final class PinGeometry {

    public static final int MIN_W = 60;
    public static final int MIN_H = 30;
    public static final int PAD = 5;
    public static final int HEADER = 12;
    public static final int HANDLE = 8;
    public static final int EDGE = 6;
    public static final int GAP = 6;

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }
    }

    private PinGeometry() {}

    public static Rect rect(PinIndex.Pin pin, int screenW, int screenH) {
        int w = clamp(pin.w(), MIN_W, Math.max(MIN_W, screenW));
        int h = clamp(pin.h(), MIN_H, Math.max(MIN_H, screenH));
        int x = clamp((int) Math.round(pin.x() * screenW), 0, Math.max(0, screenW - w));
        int y = clamp((int) Math.round(pin.y() * screenH), 0, Math.max(0, screenH - h));
        return new Rect(x, y, w, h);
    }

    public static PinIndex.Pin moved(PinIndex.Pin pin, Rect r, int dx, int dy, int screenW, int screenH) {
        int nx = clamp(r.x() + dx, 0, Math.max(0, screenW - r.w()));
        int ny = clamp(r.y() + dy, 0, Math.max(0, screenH - r.h()));
        return pin.withPosition((double) nx / screenW, (double) ny / screenH);
    }

    public static PinIndex.Pin resized(PinIndex.Pin pin, Rect r, int dx, int dy, int screenW, int screenH) {
        int nw = clamp(r.w() + dx, MIN_W, Math.max(MIN_W, screenW - r.x()));
        int nh = clamp(r.h() + dy, MIN_H, Math.max(MIN_H, screenH - r.y()));
        return pin.withSize(nw, nh);
    }

    public static PinIndex.Pin defaultPin(int index, int screenW, int screenH, float scale) {
        int w = clamp(screenW / 4, MIN_W, 200);
        int h = clamp(screenH / 3, MIN_H, 120);
        int x = Math.max(0, screenW - w - EDGE);
        int y = Math.min(Math.max(0, screenH - h), EDGE + index * (h + GAP));
        return new PinIndex.Pin((double) x / screenW, (double) y / screenH, w, h, scale, 0);
    }

    public static Rect header(Rect r) {
        return new Rect(r.x(), r.y(), r.w(), HEADER);
    }

    public static Rect handle(Rect r) {
        return new Rect(r.right() - HANDLE, r.bottom() - HANDLE, HANDLE, HANDLE);
    }

    public static Rect closeBox(Rect r) {
        return new Rect(r.right() - HEADER, r.y(), HEADER, HEADER);
    }

    public static int maxScroll(int contentHeight, float scale, Rect r, boolean interact) {
        int view = r.h() - 2 * PAD - (interact ? HEADER : 0);
        return Math.max(0, Math.round(contentHeight * scale) - view);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
```

`src/client/java/dev/blaze/notedown/hud/PinnedNotes.java`
```java
package dev.blaze.notedown.hud;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteFiles;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.PinIndex;
import dev.blaze.notedown.store.ScopeDir;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PinnedNotes {

    public static final class Entry {
        public final ScopeDir scope;
        public final Note note;
        public PinIndex.Pin pin;
        public int scroll;

        Entry(ScopeDir scope, Note note, PinIndex.Pin pin) {
            this.scope = scope;
            this.note = note;
            this.pin = pin;
            this.scroll = pin.scroll();
        }
    }

    private static List<Entry> entries = new ArrayList<>();

    private PinnedNotes() {}

    public static List<Entry> entries() {
        return entries;
    }

    /** Re-reads pins and note bodies for the current scope; keeps in-memory scroll positions of surviving pins. */
    public static void reload() {
        Minecraft mc = Minecraft.getInstance();
        NoteStore store = Notedown.store();
        Map<Path, Integer> scrolls = new HashMap<>();
        for (Entry e : entries) {
            scrolls.put(e.note.file().toAbsolutePath().normalize(), e.scroll);
        }
        List<Entry> out = new ArrayList<>();
        if (mc.level != null) {
            for (ScopeDir scope : CurrentScope.dirs(store, mc)) {
                PinIndex idx = store.index(scope);
                for (Map.Entry<String, PinIndex.Pin> pin : idx.pins().entrySet()) {
                    String title = NoteFiles.titleOf(scope.dir().resolve(pin.getKey()));
                    store.read(scope, title).ifPresent(note -> {
                        Entry entry = new Entry(scope, note, pin.getValue());
                        Integer kept = scrolls.get(note.file().toAbsolutePath().normalize());
                        if (kept != null) {
                            entry.scroll = kept;
                        }
                        out.add(entry);
                    });
                }
            }
        }
        entries = out;
    }

    public static void clear() {
        entries = new ArrayList<>();
    }

    public static Optional<Entry> first() {
        return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getFirst());
    }

    public static boolean isPinned(Note note) {
        return Notedown.store().index(note.scope()).isPinned(note.fileName());
    }

    public static void toggle(Note note, int screenW, int screenH) throws IOException {
        PinIndex idx = Notedown.store().index(note.scope());
        if (idx.isPinned(note.fileName())) {
            idx.remove(note.fileName());
        } else {
            idx.put(note.fileName(), PinGeometry.defaultPin(entries.size(), screenW, screenH, ConfigHolder.get().pinnedTextScale));
        }
        idx.save();
        reload();
    }

    public static void unpin(Entry entry) throws IOException {
        PinIndex idx = Notedown.store().index(entry.scope);
        idx.remove(entry.note.fileName());
        idx.save();
        reload();
    }

    /** Writes every entry's position, size and scroll back to its index.json. */
    public static void saveGeometry() {
        Map<Path, PinIndex> indexes = new LinkedHashMap<>();
        for (Entry e : entries) {
            PinIndex idx = indexes.computeIfAbsent(e.scope.dir(), d -> Notedown.store().index(e.scope));
            idx.put(e.note.fileName(), e.pin.withScroll(e.scroll));
        }
        for (PinIndex idx : indexes.values()) {
            try {
                idx.save();
            } catch (IOException ex) {
                Notedown.LOGGER.error("Failed to save pinned note layout", ex);
            }
        }
    }
}
```

`src/client/java/dev/blaze/notedown/hud/PinRenderer.java`
```java
package dev.blaze.notedown.hud;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.markdown.Layout;
import dev.blaze.notedown.markdown.Layouter;
import dev.blaze.notedown.markdown.TaskToggler;
import dev.blaze.notedown.ui.FontMeasure;
import dev.blaze.notedown.ui.LayoutRenderer;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class PinRenderer {

    public enum HitKind { HEADER, CLOSE, HANDLE, TASK, LINK, BODY }

    public record Hit(PinnedNotes.Entry entry, HitKind kind, int sourceLine, String url, PinGeometry.Rect rect) {}

    private static final int WHEEL_STEP = 10;

    private PinRenderer() {}

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, boolean interact, double mouseX, double mouseY) {
        NotedownConfig cfg = ConfigHolder.get();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        Font font = mc.font;
        FontMeasure measure = new FontMeasure(font);
        for (PinnedNotes.Entry e : PinnedNotes.entries()) {
            PinGeometry.Rect r = PinGeometry.rect(e.pin, sw, sh);
            Layout layout = layout(e, r, cfg, measure);
            int maxScroll = PinGeometry.maxScroll(layout.height(), e.pin.scale(), r, interact);
            e.scroll = Math.max(0, Math.min(maxScroll, e.scroll));
            g.fill(r.x(), r.y(), r.right(), r.bottom(), Theme.withAlpha(0x000000, cfg.pinnedBackgroundOpacity));
            int contentTop = r.y() + PinGeometry.PAD + (interact ? PinGeometry.HEADER : 0);
            LayoutRenderer.draw(g, font, layout, r.x() + PinGeometry.PAD, contentTop - e.scroll, e.pin.scale(),
                    r.x(), contentTop, r.right(), r.bottom() - PinGeometry.PAD);
            if (interact) {
                drawChrome(g, font, e, r, maxScroll, r.contains(mouseX, mouseY));
            }
        }
    }

    private static Layout layout(PinnedNotes.Entry e, PinGeometry.Rect r, NotedownConfig cfg, FontMeasure measure) {
        int innerW = Math.max(Layouter.MIN_WIDTH, Math.round((r.w() - 2 * PinGeometry.PAD) / e.pin.scale()));
        return Notedown.layouts().get(e.note.body(), innerW, cfg.checkedTaskStyle, measure);
    }

    private static void drawChrome(GuiGraphicsExtractor g, Font font, PinnedNotes.Entry e, PinGeometry.Rect r, int maxScroll, boolean hovered) {
        PinGeometry.Rect header = PinGeometry.header(r);
        g.fill(header.x(), header.y(), header.right(), header.bottom(), Theme.PANEL_DARK);
        g.text(font, Theme.ellipsize(font, e.note.title(), r.w() - PinGeometry.HEADER - 6), r.x() + 3, r.y() + 2, Theme.TEXT_MUTED);
        PinGeometry.Rect close = PinGeometry.closeBox(r);
        g.centeredText(font, "×", close.x() + close.w() / 2, close.y() + 2, Theme.TEXT);
        PinGeometry.Rect handle = PinGeometry.handle(r);
        g.fill(handle.right() - 2, handle.bottom() - 6, handle.right(), handle.bottom(), Theme.ACCENT);
        g.fill(handle.right() - 6, handle.bottom() - 2, handle.right(), handle.bottom(), Theme.ACCENT);
        Theme.border(g, r.x(), r.y(), r.right(), r.bottom(), hovered ? Theme.ACCENT : Theme.BORDER);
        if (maxScroll > 0) {
            int track = r.h() - PinGeometry.HEADER - 2 * PinGeometry.PAD;
            int thumb = Math.max(6, (int) ((long) track * track / (track + maxScroll)));
            int ty = r.y() + PinGeometry.HEADER + PinGeometry.PAD + (int) ((long) (track - thumb) * e.scroll / maxScroll);
            g.fill(r.right() - 3, ty, r.right() - 1, ty + thumb, Theme.TEXT_MUTED);
        }
    }

    public static Optional<Hit> hitTest(double mx, double my, int screenW, int screenH, boolean interact) {
        NotedownConfig cfg = ConfigHolder.get();
        FontMeasure measure = new FontMeasure(Minecraft.getInstance().font);
        List<PinnedNotes.Entry> entries = PinnedNotes.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            PinnedNotes.Entry e = entries.get(i);
            PinGeometry.Rect r = PinGeometry.rect(e.pin, screenW, screenH);
            if (!r.contains(mx, my)) {
                continue;
            }
            if (interact) {
                if (PinGeometry.closeBox(r).contains(mx, my)) {
                    return Optional.of(new Hit(e, HitKind.CLOSE, -1, null, r));
                }
                if (PinGeometry.header(r).contains(mx, my)) {
                    return Optional.of(new Hit(e, HitKind.HEADER, -1, null, r));
                }
                if (PinGeometry.handle(r).contains(mx, my)) {
                    return Optional.of(new Hit(e, HitKind.HANDLE, -1, null, r));
                }
            }
            Layout layout = layout(e, r, cfg, measure);
            int contentTop = r.y() + PinGeometry.PAD + (interact ? PinGeometry.HEADER : 0);
            double lx = (mx - (r.x() + PinGeometry.PAD)) / e.pin.scale();
            double ly = (my - contentTop + e.scroll) / e.pin.scale();
            for (Layout.HitBox h : layout.hitBoxes()) {
                if (h.contains(lx, ly)) {
                    HitKind kind = h.kind() == Layout.HitKind.TASK ? HitKind.TASK : HitKind.LINK;
                    return Optional.of(new Hit(e, kind, h.sourceLine(), h.url(), r));
                }
            }
            return Optional.of(new Hit(e, HitKind.BODY, -1, null, r));
        }
        return Optional.empty();
    }

    public static void toggleTask(PinnedNotes.Entry e, int line) {
        try {
            String toggled = TaskToggler.toggleLine(e.note.body(), line);
            Notedown.store().save(e.note, e.note.title(), toggled, e.scope);
            PinnedNotes.reload();
        } catch (IOException ex) {
            Notedown.LOGGER.error("Failed to save note", ex);
            Messages.chat(Minecraft.getInstance(), Messages.t("message.save_failed"));
        }
    }

    public static void scroll(PinnedNotes.Entry e, double vertical, int screenW, int screenH, boolean interact) {
        PinGeometry.Rect r = PinGeometry.rect(e.pin, screenW, screenH);
        Layout layout = layout(e, r, ConfigHolder.get(), new FontMeasure(Minecraft.getInstance().font));
        int max = PinGeometry.maxScroll(layout.height(), e.pin.scale(), r, interact);
        e.scroll = Math.max(0, Math.min(max, e.scroll - (int) Math.round(vertical * WHEEL_STEP)));
    }
}
```

`src/client/java/dev/blaze/notedown/hud/PinnedHudElement.java`
```java
package dev.blaze.notedown.hud;

import dev.blaze.notedown.config.ConfigHolder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

public final class PinnedHudElement implements HudElement {

    public static boolean pinsVisible(Minecraft mc) {
        return mc.level != null && !mc.gui.hud.isHidden() && !ConfigHolder.get().pinnedHidden && !PinnedNotes.entries().isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!pinsVisible(mc)) {
            return;
        }
        Screen screen = mc.gui.screen();
        if (screen != null && !(screen instanceof ChatScreen && ConfigHolder.get().showPinnedWithChat)) {
            return;
        }
        PinRenderer.draw(g, mc, false, -1, -1);
    }
}
```

In `Notedown.onInitializeClient()` add after `NotedownKeys.register();`:
```java
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("pinned"), new PinnedHudElement());
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> PinnedNotes.reload());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> PinnedNotes.clear());
```
and in `tick(...)`:
```java
        while (NotedownKeys.TOGGLE_PINNED.consumeClick()) {
            NotedownConfig cfg = ConfigHolder.get();
            cfg.pinnedHidden = !cfg.pinnedHidden;
            ConfigHolder.save();
            Messages.chat(mc, Messages.t(cfg.pinnedHidden ? "message.pinned_hidden" : "message.pinned_shown"));
        }
```
with imports `dev.blaze.notedown.config.NotedownConfig`, `dev.blaze.notedown.hud.PinnedHudElement`, `dev.blaze.notedown.hud.PinnedNotes`, `dev.blaze.notedown.ui.Messages`, `net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents`, `net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry`, `net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements`.

In `EditScreen.save()` add `PinnedNotes.reload();` right after `savedScope = scope;` (import `dev.blaze.notedown.hud.PinnedNotes`).

- [ ] **Step 4: Tests, build, verify by hand**

Run: `./gradlew build && scripts/dev-install.sh` → `PinGeometryTest` 5 tests pass, BUILD SUCCESSFUL.
By hand: create `minecraft/notedown/local/<world>/index.json` with `{"version":1,"pins":{"<an existing note>.md":{"x":0.7,"y":0.1,"w":160,"h":100,"scale":1.0,"scroll":0}}}`, join the world → the note renders top-right with markdown, background at 50 % alpha; F1 hides it; the "Show/hide pinned notes" key (bind it first) toggles it with a chat message; editing that note in the editor and saving updates the HUD; opening chat keeps it visible, opening the inventory hides it (chest overlay comes in Task 12).

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "pinned hud core"
```

---

### Task 11: Notebook and view screens, file import

**Files:**
- Create: `src/client/java/dev/blaze/notedown/gui/FileDialogs.java`, `gui/ViewScreen.java`, `gui/NotebookScreen.java`
- Modify: `Notedown.java`

**Interfaces:**
- Consumes: everything above.
- Produces: `FileDialogs.openNoteFiles()` → `List<Path>`; `ViewScreen(Screen parent, Note)`; `NotebookScreen(Screen parent)`; `NotedownKeys.OPEN` opens the notebook in-game.

- [ ] **Step 1: File dialog**

`src/client/java/dev/blaze/notedown/gui/FileDialogs.java`
```java
package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileDialogs {

    private FileDialogs() {}

    /** Native open-file dialog for .md/.txt files; empty when cancelled. Runs on the calling (render) thread. */
    public static List<Path> openNoteFiles() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.md"));
            filters.put(stack.UTF8("*.txt"));
            filters.flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog("Import notes", (CharSequence) null, filters,
                    "Markdown or text (*.md, *.txt)", true);
            if (result == null || result.isBlank()) {
                return List.of();
            }
            List<Path> paths = new ArrayList<>();
            for (String part : result.split("\\|")) {
                if (!part.isBlank()) {
                    paths.add(Path.of(part));
                }
            }
            return paths;
        } catch (RuntimeException e) {
            Notedown.LOGGER.error("File dialog failed", e);
            return List.of();
        }
    }
}
```

- [ ] **Step 2: View screen**

`src/client/java/dev/blaze/notedown/gui/ViewScreen.java`
```java
package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.hud.PinnedNotes;
import dev.blaze.notedown.markdown.TaskToggler;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.MarkdownView;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ViewScreen extends Screen {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
    private static final int SIDE_W = 110;
    private static final int TOP = 12;

    private final Screen parent;
    private final NoteStore store = Notedown.store();
    private Note note;
    private MarkdownView view;
    private FlatButton pinButton;

    public ViewScreen(Screen parent, Note note) {
        super(Component.literal(note.title()));
        this.parent = parent;
        this.note = note;
    }

    @Override
    protected void init() {
        int x = Theme.MARGIN;
        int y = TOP;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.edit"), this::edit));
        y += 25;
        pinButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, pinLabel(), this::togglePin));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.duplicate"), this::duplicate));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.copy"), this::copyText));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.delete"), this::delete));
        addRenderableWidget(new FlatButton(x, height - Theme.MARGIN - Theme.BUTTON_HEIGHT, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.back"), this::onClose));

        int cx = Theme.MARGIN * 2 + SIDE_W;
        int cw = width - cx - Theme.MARGIN;
        int vy = TOP + font.lineHeight + 8;
        view = new MarkdownView(cx, vy, cw, height - vy - Theme.MARGIN, Notedown.layouts(), this::toggleTask, this::openLink);
        view.setBody(note.body());
        addRenderableWidget(view);
        setInitialFocus(view);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int cx = Theme.MARGIN * 2 + SIDE_W;
        int cw = width - cx - Theme.MARGIN;
        String date = Messages.t("list.modified", DATE.format(Instant.ofEpochMilli(note.lastModified()).atZone(ZoneId.systemDefault()))).getString();
        int dateW = font.width(date);
        String badge = "(" + Component.translatable(note.scope().kind().langKey()).getString() + ")";
        String title = Theme.ellipsize(font, note.title(), cw - dateW - font.width(badge) - 12);
        g.text(font, title, cx, TOP, Theme.TEXT);
        g.text(font, badge, cx + font.width(title) + 4, TOP, Theme.TEXT_MUTED);
        g.text(font, date, cx + cw - dateW, TOP, Theme.TEXT_DISABLED);
    }

    private Component pinLabel() {
        return Messages.t(PinnedNotes.isPinned(note) ? "button.unpin" : "button.pin");
    }

    private void edit() {
        EditScreen.open(parent, note, note.scope());
    }

    private void togglePin() {
        try {
            PinnedNotes.toggle(note, width, height);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to update pins", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
        pinButton.setMessage(pinLabel());
    }

    private void duplicate() {
        try {
            store.duplicate(note);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to duplicate note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
            return;
        }
        minecraft.gui.setScreen(parent);
    }

    private void copyText() {
        minecraft.keyboardHandler.setClipboard(note.body());
        Messages.chat(minecraft, Messages.t("message.copied"));
    }

    private void delete() {
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.delete_title"), Messages.t("dialog.delete_body", note.title()),
                Messages.t("button.delete"), () -> {
                    try {
                        store.delete(note);
                        PinnedNotes.reload();
                    } catch (IOException e) {
                        Notedown.LOGGER.error("Failed to delete note", e);
                        Messages.chat(minecraft, Messages.t("message.save_failed"));
                    }
                    minecraft.gui.setScreen(parent);
                }));
    }

    private void toggleTask(int line) {
        try {
            note = store.save(note, note.title(), TaskToggler.toggleLine(note.body(), line), note.scope());
            view.setBody(note.body());
            PinnedNotes.reload();
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to save note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
    }

    private void openLink(String url) {
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.link_title"), Messages.t("dialog.link_body", url),
                Messages.t("button.open"), () -> Util.getPlatform().openUri(url)));
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_E && !e.hasControlDown()) {
            edit();
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_P && !e.hasControlDown()) {
            togglePin();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
```

- [ ] **Step 3: Notebook screen**

`src/client/java/dev/blaze/notedown/gui/NotebookScreen.java`
```java
package dev.blaze.notedown.gui;

import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.hud.PinnedNotes;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.store.Note;
import dev.blaze.notedown.store.NoteStore;
import dev.blaze.notedown.store.ScopeDir;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.FlatList;
import dev.blaze.notedown.ui.FlatTextField;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class NotebookScreen extends Screen {

    private static final int SIDE_W = 110;
    private static final int TOP = 12;
    private static final int NOTE_ROW = 36;

    private final Screen parent;
    private final NoteStore store = Notedown.store();
    private List<ScopeDir> scopes = List.of();
    private List<Note> all = List.of();
    private Set<Path> pinned = Set.of();
    private String query = "";
    private Note keep;
    private FlatTextField search;
    private FlatList<Note> list;
    private final List<FlatButton> selectionButtons = new ArrayList<>();
    private FlatButton pinButton;

    public NotebookScreen(Screen parent) {
        super(Messages.t("screen.notebook.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        scopes = CurrentScope.dirs(store, minecraft);
        selectionButtons.clear();
        int x = Theme.MARGIN;
        int y = TOP;
        search = new FlatTextField(font, x, y, width - 2 * Theme.MARGIN, Theme.BUTTON_HEIGHT, Messages.t("screen.notebook.search"));
        search.setValue(query);
        search.setResponder(q -> {
            query = q;
            filter();
        });
        addRenderableWidget(search);
        y += Theme.BUTTON_HEIGHT + 8;
        int listY = y;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.new"), this::newNote));
        y += 25;
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.import"), this::importFiles));
        y += 25;
        y += 10;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.view"), () -> selected().ifPresent(this::view))));
        y += 25;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.edit"), () -> selected().ifPresent(this::edit))));
        y += 25;
        pinButton = addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.pin"), () -> selected().ifPresent(this::togglePin)));
        selectionButtons.add(pinButton);
        y += 25;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.duplicate"), () -> selected().ifPresent(this::duplicate))));
        y += 25;
        selectionButtons.add(addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.delete"), () -> selected().ifPresent(this::delete))));
        addRenderableWidget(new FlatButton(x, height - Theme.MARGIN - Theme.BUTTON_HEIGHT, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.done"), this::onClose));

        int lx = Theme.MARGIN * 2 + SIDE_W;
        list = new FlatList<>(lx, listY, width - lx - Theme.MARGIN, height - listY - Theme.MARGIN, NOTE_ROW,
                this::renderRow, n -> updateButtons(), this::view);
        addRenderableWidget(list);
        reload();
        setInitialFocus(list);
    }

    private void reload() {
        PinnedNotes.reload();
        List<Note> notes = new ArrayList<>();
        Set<Path> pins = new HashSet<>();
        for (ScopeDir scope : scopes) {
            notes.addAll(store.list(scope));
            for (String name : store.index(scope).pins().keySet()) {
                pins.add(scope.dir().resolve(name).toAbsolutePath().normalize());
            }
        }
        pinned = pins;
        notes.sort(Comparator.comparing((Note n) -> !isPinned(n)).thenComparing(Comparator.comparingLong(Note::lastModified).reversed()));
        all = notes;
        filter();
    }

    private boolean isPinned(Note n) {
        return pinned.contains(n.file().toAbsolutePath().normalize());
    }

    private void filter() {
        List<Note> shown = NoteStore.search(all, query);
        list.setItems(shown, null);
        if (keep != null) {
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).sameFile(keep)) {
                    list.select(i);
                    break;
                }
            }
        }
        updateButtons();
    }

    private Optional<Note> selected() {
        return list.selected();
    }

    private void updateButtons() {
        Optional<Note> sel = selected();
        keep = sel.orElse(keep);
        for (FlatButton b : selectionButtons) {
            b.active = sel.isPresent();
        }
        pinButton.setMessage(Messages.t(sel.map(this::isPinned).orElse(false) ? "button.unpin" : "button.pin"));
    }

    private void renderRow(GuiGraphicsExtractor g, Note n, int x, int y, int w, int h, boolean selected, boolean hovered) {
        String badge = "(" + Component.translatable(n.scope().kind().langKey()).getString() + ")"
                + (isPinned(n) ? " · " + Messages.t("list.pinned").getString() : "");
        String title = Theme.ellipsize(font, n.title(), w - font.width(badge) - 6);
        g.text(font, title, x, y + 4, Theme.TEXT);
        g.text(font, badge, x + font.width(title) + 4, y + 4, Theme.TEXT_MUTED);
        g.text(font, Theme.ellipsize(font, NoteStore.preview(n.body()), w), x, y + 15, Theme.TEXT_MUTED);
        String date = Messages.t("list.modified", ViewScreen.DATE.format(Instant.ofEpochMilli(n.lastModified()).atZone(ZoneId.systemDefault()))).getString();
        g.text(font, date, x, y + 25, Theme.TEXT_DISABLED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (all.isEmpty()) {
            g.centeredText(font, Messages.t("screen.notebook.empty"), list.getX() + list.getWidth() / 2, list.getY() + list.getHeight() / 2 - 4, Theme.TEXT_MUTED);
        }
        if (scopes.size() > 1) {
            ScopeDir current = scopes.getFirst();
            Component label = Messages.t("screen.notebook.scope", Component.translatable(current.kind().langKey()), current.label());
            g.text(font, Theme.ellipsize(font, label.getString(), SIDE_W), Theme.MARGIN, height - Theme.MARGIN - Theme.BUTTON_HEIGHT - 12, Theme.TEXT_DISABLED);
        }
    }

    private void newNote() {
        EditScreen.open(this, null, scopes.getFirst());
    }

    private void view(Note n) {
        keep = n;
        minecraft.gui.setScreen(new ViewScreen(this, n));
    }

    private void edit(Note n) {
        keep = n;
        EditScreen.open(this, n, n.scope());
    }

    private void togglePin(Note n) {
        try {
            PinnedNotes.toggle(n, width, height);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to update pins", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
        keep = n;
        reload();
    }

    private void duplicate(Note n) {
        try {
            keep = store.duplicate(n);
        } catch (IOException e) {
            Notedown.LOGGER.error("Failed to duplicate note", e);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
        reload();
    }

    private void delete(Note n) {
        minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.delete_title"), Messages.t("dialog.delete_body", n.title()),
                Messages.t("button.delete"), () -> {
                    try {
                        store.delete(n);
                    } catch (IOException e) {
                        Notedown.LOGGER.error("Failed to delete note", e);
                        Messages.chat(minecraft, Messages.t("message.save_failed"));
                    }
                    keep = null;
                }));
    }

    private void importFiles() {
        int count = 0;
        for (Path file : FileDialogs.openNoteFiles()) {
            try {
                keep = store.importFile(scopes.getFirst(), file);
                count++;
            } catch (IOException e) {
                Notedown.LOGGER.error("Failed to import {}", file, e);
                Messages.chat(minecraft, Messages.t("message.import_failed"));
            }
        }
        if (count > 0) {
            Messages.chat(minecraft, Messages.t("message.imported", count));
            reload();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.hasControlDown() && e.key() == GLFW.GLFW_KEY_N) {
            newNote();
            return true;
        }
        if (e.hasControlDown() && e.key() == GLFW.GLFW_KEY_F) {
            setFocused(search);
            return true;
        }
        if (getFocused() == list) {
            if (e.key() == GLFW.GLFW_KEY_E) {
                selected().ifPresent(this::edit);
                return true;
            }
            if (e.key() == GLFW.GLFW_KEY_DELETE) {
                selected().ifPresent(this::delete);
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
```

In `Notedown.tick(...)` add:
```java
        while (NotedownKeys.OPEN.consumeClick()) {
            if (mc.level != null) {
                mc.gui.setScreen(new NotebookScreen(null));
            }
        }
```
with import `dev.blaze.notedown.gui.NotebookScreen`.

- [ ] **Step 4: Build, install, verify by hand**

Run: `./gradlew build && scripts/dev-install.sh`, launch, join a world, press N:
- Notebook: search on top, flat side buttons, list rows with title, `(World)`/`(Global)` badge, preview line, date. Typing in search filters live. Arrow keys move the selection, Enter opens View, double-click opens View, E edits, Delete asks to confirm.
- View: rendered markdown with scrollbar for long notes, clicking a checkbox flips it and the file changes on disk. Pin/Unpin toggles a HUD pin (visible after Done). Copy text puts the body on the clipboard.
- Import…: the macOS file dialog opens; picking a `.txt` creates a note in the current world scope and selects it.
- Pinned notes come first in the list and show `· pinned`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "notebook and view screens, file import"
```

---

### Task 12: Hold-key interaction, chat and container hooks, quick-edit key

**Files:**
- Create: `src/client/java/dev/blaze/notedown/hud/HudInteractScreen.java`, `hud/ScreenHooks.java`
- Modify: `Notedown.java`

**Interfaces:**
- Consumes: `PinRenderer`, `PinnedNotes`, `PinGeometry`, `NotedownKeys`, `EditScreen.open`, `ConfirmDialog`, `PinnedHudElement.pinsVisible`.
- Produces: `HudInteractScreen` (transparent, non-pausing; closes when the interact key is released or on Esc); `ScreenHooks.register()`.

- [ ] **Step 1: Interact screen**

`src/client/java/dev/blaze/notedown/hud/HudInteractScreen.java`
```java
package dev.blaze.notedown.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.NotedownKeys;
import dev.blaze.notedown.gui.EditScreen;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.util.Optional;

public final class HudInteractScreen extends Screen {

    private PinRenderer.Hit dragging;
    private double lastX;
    private double lastY;

    public HudInteractScreen() {
        super(Messages.t("screen.interact.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        PinRenderer.draw(g, minecraft, true, mouseX, mouseY);
        Component hint = Messages.t("screen.interact.hint", NotedownKeys.INTERACT.getTranslatedKeyMessage());
        g.centeredText(font, hint, width / 2, height - 30, Theme.TEXT_MUTED);
    }

    @Override
    public void tick() {
        if (!NotedownKeys.isPhysicallyDown(minecraft, NotedownKeys.INTERACT)) {
            onClose();
        }
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (NotedownKeys.INTERACT.matches(e)) {
            onClose();
            return true;
        }
        return super.keyReleased(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(e.x(), e.y(), width, height, true);
        if (hit.isEmpty()) {
            return false;
        }
        PinRenderer.Hit h = hit.get();
        switch (h.kind()) {
            case CLOSE -> unpin(h.entry());
            case HEADER -> {
                if (doubleClick) {
                    PinnedNotes.saveGeometry();
                    EditScreen.open(null, h.entry().note, h.entry().scope);
                } else {
                    startDrag(h, e);
                }
            }
            case HANDLE -> startDrag(h, e);
            case TASK -> PinRenderer.toggleTask(h.entry(), h.sourceLine());
            case LINK -> minecraft.gui.setScreen(new ConfirmDialog(this, Messages.t("dialog.link_title"), Messages.t("dialog.link_body", h.url()),
                    Messages.t("button.open"), () -> Util.getPlatform().openUri(h.url())));
            case BODY -> { }
        }
        return true;
    }

    private void startDrag(PinRenderer.Hit h, MouseButtonEvent e) {
        dragging = h;
        lastX = e.x();
        lastY = e.y();
    }

    private void unpin(PinnedNotes.Entry entry) {
        try {
            PinnedNotes.unpin(entry);
        } catch (IOException ex) {
            Notedown.LOGGER.error("Failed to unpin note", ex);
            Messages.chat(minecraft, Messages.t("message.save_failed"));
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) {
            return false;
        }
        int mdx = (int) Math.round(e.x() - lastX);
        int mdy = (int) Math.round(e.y() - lastY);
        if (mdx == 0 && mdy == 0) {
            return true;
        }
        PinnedNotes.Entry entry = dragging.entry();
        PinGeometry.Rect r = PinGeometry.rect(entry.pin, width, height);
        entry.pin = dragging.kind() == PinRenderer.HitKind.HEADER
                ? PinGeometry.moved(entry.pin, r, mdx, mdy, width, height)
                : PinGeometry.resized(entry.pin, r, mdx, mdy, width, height);
        lastX = e.x();
        lastY = e.y();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging == null) {
            return false;
        }
        dragging = null;
        PinnedNotes.saveGeometry();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(mouseX, mouseY, width, height, true);
        if (hit.isEmpty()) {
            return false;
        }
        PinRenderer.scroll(hit.get().entry(), vertical, width, height, true);
        return true;
    }

    @Override
    public void onClose() {
        PinnedNotes.saveGeometry();
        minecraft.gui.setScreen(null);
    }
}
```

- [ ] **Step 2: Chat and container hooks**

`src/client/java/dev/blaze/notedown/hud/ScreenHooks.java`
```java
package dev.blaze.notedown.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.ui.ConfirmDialog;
import dev.blaze.notedown.ui.Messages;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;

import java.util.Optional;

public final class ScreenHooks {

    private ScreenHooks() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            NotedownConfig cfg = ConfigHolder.get();
            boolean container = screen instanceof AbstractContainerScreen<?> && cfg.showPinnedInContainers;
            boolean chat = screen instanceof ChatScreen && cfg.showPinnedWithChat;
            if (!container && !chat) {
                return;
            }
            if (container) {
                ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, tick) -> {
                    if (PinnedHudElement.pinsVisible(client)) {
                        PinRenderer.draw(g, client, false, mouseX, mouseY);
                    }
                });
            }
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !PinnedHudElement.pinsVisible(client)) {
                    return true;
                }
                Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(event.x(), event.y(), s.width, s.height, false);
                if (hit.isEmpty()) {
                    return true;
                }
                PinRenderer.Hit h = hit.get();
                switch (h.kind()) {
                    case TASK -> PinRenderer.toggleTask(h.entry(), h.sourceLine());
                    case LINK -> client.gui.setScreen(new ConfirmDialog(s, Messages.t("dialog.link_title"), Messages.t("dialog.link_body", h.url()),
                            Messages.t("button.open"), () -> Util.getPlatform().openUri(h.url())));
                    default -> { }
                }
                return false;
            });
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> {
                if (!PinnedHudElement.pinsVisible(client)) {
                    return true;
                }
                Optional<PinRenderer.Hit> hit = PinRenderer.hitTest(mouseX, mouseY, s.width, s.height, false);
                if (hit.isEmpty()) {
                    return true;
                }
                PinRenderer.scroll(hit.get().entry(), vertical, s.width, s.height, false);
                return false;
            });
            ScreenEvents.remove(screen).register(s -> PinnedNotes.saveGeometry());
        });
    }
}
```

- [ ] **Step 3: Wire keys**

In `Notedown`: add field `private static boolean interactWasDown;`, call `ScreenHooks.register();` in `onInitializeClient()` after the HUD element registration, and extend `tick(...)`:
```java
        boolean interactDown = NotedownKeys.INTERACT.isDown();
        if (interactDown && !interactWasDown && mc.gui.screen() == null && PinnedHudElement.pinsVisible(mc)) {
            mc.gui.setScreen(new HudInteractScreen());
        }
        interactWasDown = interactDown;
        while (NotedownKeys.QUICK_EDIT.consumeClick()) {
            if (mc.level != null) {
                PinnedNotes.first().ifPresentOrElse(
                        e -> EditScreen.open(null, e.note, e.scope),
                        () -> Messages.chat(mc, Messages.t("message.no_pinned")));
            }
        }
```
with imports `dev.blaze.notedown.hud.HudInteractScreen`, `dev.blaze.notedown.hud.ScreenHooks`.

- [ ] **Step 4: Build, install, verify by hand**

Run: `./gradlew build && scripts/dev-install.sh`, launch, join a world with a pinned checklist:
- Hold Left Alt: cursor appears, pins gain a header strip with title and ×, a teal corner handle, and the hint line at the bottom. Drag the header → the pin moves and stays on screen; drag the corner → resizes down to 60×30; wheel → scrolls a long note; click a checkbox → it flips and the file changes; × unpins; double-click the header → editor opens. Release Alt → back to the game, geometry persists across relaunch.
- Open chat (T): pins stay visible, clicking a checkbox flips it, wheel over a pin scrolls it, clicks elsewhere still reach chat.
- Open a chest: pins draw on top of the chest UI undimmed; checkbox clicks work and do not move items; clicks on the pin body do nothing; disabling "Show pinned notes in chests" in the config file hides them there.
- Bind "Edit first pinned note" → opens the editor with the body focused; with no pins it chats "No pinned notes."

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "hud interaction, chat and container hooks"
```

---

### Task 13: Settings screen, keybind rows, Mod Menu entry

**Files:**
- Create: `src/client/java/dev/blaze/notedown/gui/KeyBindRow.java`, `gui/SettingsScreen.java`, `src/client/java/dev/blaze/notedown/NotedownModMenu.java`
- Modify: `gui/NotebookScreen.java`

**Interfaces:**
- Consumes: `NotedownConfig`, `ConfigHolder`, `CheckedTaskStyle.next()`, `NotedownKeys.all()`, `Toggle`, `Slider`, `FlatButton`, `NotebookScreen`, `FileDialogs`, `NoteStore.importFile`.
- Produces: `SettingsScreen(Screen parent)`; `KeyBindRow`; `NotedownModMenu` (`ModMenuApi`).

- [ ] **Step 1: Implement**

`src/client/java/dev/blaze/notedown/gui/KeyBindRow.java`
```java
package dev.blaze.notedown.gui;

import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class KeyBindRow {

    private static final int RESET_W = 60;

    private final KeyMapping key;
    private final SettingsScreen screen;
    private final FlatButton change;
    private final FlatButton reset;

    KeyBindRow(KeyMapping key, SettingsScreen screen, int x, int y, int w) {
        this.key = key;
        this.screen = screen;
        this.change = new FlatButton(x, y, w - RESET_W - 4, Theme.BUTTON_HEIGHT, Component.empty(), () -> screen.startSelecting(key)).leftAlign(true);
        this.reset = new FlatButton(x + w - RESET_W, y, RESET_W, Theme.BUTTON_HEIGHT, Messages.t("option.reset"), this::resetToDefault);
        refresh();
    }

    FlatButton changeButton() {
        return change;
    }

    FlatButton resetButton() {
        return reset;
    }

    void refresh() {
        Component value = key.getTranslatedKeyMessage();
        if (conflicted()) {
            value = value.copy().withStyle(ChatFormatting.RED);
        }
        if (screen.isSelecting(key)) {
            value = Messages.t("option.selecting", value);
        }
        change.setMessage(Component.empty().append(Component.translatable(key.getName())).append(": ").append(value));
        reset.active = !key.isDefault();
    }

    private void resetToDefault() {
        key.setKey(key.getDefaultKey());
        screen.keysChanged();
    }

    private boolean conflicted() {
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != key && !other.isUnbound() && other.same(key)) {
                return true;
            }
        }
        return false;
    }
}
```

`src/client/java/dev/blaze/notedown/gui/SettingsScreen.java`
```java
package dev.blaze.notedown.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blaze.notedown.Notedown;
import dev.blaze.notedown.NotedownKeys;
import dev.blaze.notedown.config.ConfigHolder;
import dev.blaze.notedown.config.NotedownConfig;
import dev.blaze.notedown.scope.CurrentScope;
import dev.blaze.notedown.ui.FlatButton;
import dev.blaze.notedown.ui.Messages;
import dev.blaze.notedown.ui.Slider;
import dev.blaze.notedown.ui.Theme;
import dev.blaze.notedown.ui.Toggle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingsScreen extends Screen {

    private static final int COL_W = 300;
    private static final int GAP = 10;
    private static final int TOP = 12;
    private static final int BOTTOM_W = 110;

    private final Screen parent;
    private final List<KeyBindRow> keyRows = new ArrayList<>();
    @Nullable
    private KeyMapping selecting;
    private FlatButton checkedStyleButton;
    private int leftX;
    private int rightX;

    public SettingsScreen(Screen parent) {
        super(Messages.t("screen.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        NotedownConfig cfg = ConfigHolder.get();
        keyRows.clear();
        selecting = null;
        int totalW = Math.min(width - 2 * Theme.MARGIN, 2 * COL_W + GAP);
        int colW = (totalW - GAP) / 2;
        leftX = (width - totalW) / 2;
        rightX = leftX + colW + GAP;
        int y = TOP + font.lineHeight + 6;

        addRenderableWidget(new Slider(leftX, y, colW, Messages.t("option.pinned_opacity"), 0f, 1f, 0.05f, cfg.pinnedBackgroundOpacity,
                v -> Math.round(v * 100) + "%", v -> {
                    cfg.pinnedBackgroundOpacity = v;
                    ConfigHolder.save();
                }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Slider(leftX, y, colW, Messages.t("option.pinned_scale"), NotedownConfig.MIN_SCALE, NotedownConfig.MAX_SCALE, 0.1f,
                cfg.pinnedTextScale, v -> String.format(Locale.ROOT, "%.1f×", v), v -> {
                    cfg.pinnedTextScale = v;
                    ConfigHolder.save();
                }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Toggle(leftX, y, colW, Messages.t("option.show_in_containers"), cfg.showPinnedInContainers, v -> {
            cfg.showPinnedInContainers = v;
            ConfigHolder.save();
        }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Toggle(leftX, y, colW, Messages.t("option.show_with_chat"), cfg.showPinnedWithChat, v -> {
            cfg.showPinnedWithChat = v;
            ConfigHolder.save();
        }));
        y += Theme.ROW_HEIGHT;
        addRenderableWidget(new Toggle(leftX, y, colW, Messages.t("option.show_insert_buttons"), cfg.showInsertButtons, v -> {
            cfg.showInsertButtons = v;
            ConfigHolder.save();
        }));
        y += Theme.ROW_HEIGHT;
        checkedStyleButton = addRenderableWidget(new FlatButton(leftX, y, colW, Theme.BUTTON_HEIGHT, checkedLabel(cfg), () -> {
            cfg.checkedTaskStyle = cfg.checkedTaskStyle.next();
            ConfigHolder.save();
            Notedown.layouts().clear();
            checkedStyleButton.setMessage(checkedLabel(cfg));
        }).leftAlign(true));

        int ry = TOP + font.lineHeight + 6;
        for (KeyMapping key : NotedownKeys.all()) {
            KeyBindRow row = new KeyBindRow(key, this, rightX, ry, colW);
            addRenderableWidget(row.changeButton());
            addRenderableWidget(row.resetButton());
            keyRows.add(row);
            ry += Theme.ROW_HEIGHT;
        }

        int by = height - Theme.MARGIN - Theme.BUTTON_HEIGHT;
        addRenderableWidget(new FlatButton(leftX, by, BOTTOM_W, Theme.BUTTON_HEIGHT, Messages.t("button.open_notebook"),
                () -> minecraft.gui.setScreen(new NotebookScreen(this))));
        addRenderableWidget(new FlatButton(leftX + BOTTOM_W + 5, by, BOTTOM_W, Theme.BUTTON_HEIGHT, Messages.t("button.open_folder"),
                () -> Util.getPlatform().openPath(Notedown.store().root())));
        addRenderableWidget(new FlatButton(leftX + 2 * (BOTTOM_W + 5), by, BOTTOM_W, Theme.BUTTON_HEIGHT, Messages.t("button.import"), this::importFiles));
        addRenderableWidget(new FlatButton(leftX + totalW - BOTTOM_W, by, BOTTOM_W, Theme.BUTTON_HEIGHT, Messages.t("button.done"), this::onClose));
    }

    private static Component checkedLabel(NotedownConfig cfg) {
        return Component.empty().append(Messages.t("option.checked_style")).append(": ")
                .append(Messages.t("option.checked_style." + cfg.checkedTaskStyle.name().toLowerCase(Locale.ROOT)));
    }

    private void importFiles() {
        int count = 0;
        for (Path file : FileDialogs.openNoteFiles()) {
            try {
                Notedown.store().importFile(CurrentScope.dirs(Notedown.store(), minecraft).getFirst(), file);
                count++;
            } catch (IOException e) {
                Notedown.LOGGER.error("Failed to import {}", file, e);
                Messages.chat(minecraft, Messages.t("message.import_failed"));
            }
        }
        if (count > 0) {
            Messages.chat(minecraft, Messages.t("message.imported", count));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        g.text(font, title, leftX, TOP, Theme.TEXT);
        g.text(font, Messages.t("screen.settings.keybinds"), rightX, TOP, Theme.TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (selecting == null) {
            return super.mouseClicked(e, doubleClick);
        }
        selecting.setKey(InputConstants.Type.MOUSE.getOrCreate(e.button()));
        selecting = null;
        keysChanged();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (selecting == null) {
            return super.keyPressed(e);
        }
        selecting.setKey(e.key() == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(e));
        selecting = null;
        keysChanged();
        return true;
    }

    boolean isSelecting(KeyMapping key) {
        return selecting == key;
    }

    void startSelecting(KeyMapping key) {
        selecting = key;
        refreshKeyRows();
    }

    void keysChanged() {
        minecraft.options.save();
        KeyMapping.resetMapping();
        refreshKeyRows();
    }

    private void refreshKeyRows() {
        for (KeyBindRow row : keyRows) {
            row.refresh();
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
```

`src/client/java/dev/blaze/notedown/NotedownModMenu.java`
```java
package dev.blaze.notedown;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.blaze.notedown.gui.SettingsScreen;

public final class NotedownModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<SettingsScreen> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
```

In `NotebookScreen.init()`, after the Import button (`y += 25;`) and before `y += 10;`, add:
```java
        addRenderableWidget(new FlatButton(x, y, SIDE_W, Theme.BUTTON_HEIGHT, Messages.t("button.settings"), () -> minecraft.gui.setScreen(new SettingsScreen(this))));
        y += 25;
```

- [ ] **Step 2: Build, install, verify by hand**

Run: `./gradlew build && scripts/dev-install.sh`, launch:
- Title screen → Mods → Notedown → Configure: settings screen in the flat style, two columns, sliders drag and show `50%` / `1.0×`, tick boxes toggle, "Checked tasks: Strike + dim" cycles through three values, keybind rows show current keys, clicking one and pressing a key rebinds it (Esc unbinds), Reset restores. Open notebook from here shows global notes only; Open notes folder opens Finder; Import… works.
- In-game notebook has a Settings button. Changing "Checked tasks" re-renders pinned checklists immediately.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "settings screen and mod menu entry"
```

---

### Task 14: Final verification

**Files:**
- Modify only if the checks below find problems.

- [ ] **Step 1: Lang key completeness**

Run from the repo root:
```bash
diff <(grep -rhoE 'Messages\.t\("[a-z_.]+"' src/client/java | sed -E 's/Messages\.t\("/notedown./; s/"$//' | sort -u) <(python3 -c "import json;print('\n'.join(sorted(k for k in json.load(open('src/client/resources/assets/notedown/lang/en_us.json')) if k.startswith('notedown.'))))" ) | grep '^<' || echo "all keys present"
```
Expected: `all keys present` (lines starting with `<` are keys used in code but missing from `en_us.json`; add them).

- [ ] **Step 2: Full build and tests**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all JUnit tests pass, one jar in `build/libs/` plus the sources jar.

- [ ] **Step 3: Manual checklist in FO 26.2**

Run `scripts/dev-install.sh`, launch, and tick each:
- [ ] Notebook opens with N in a world; on the title screen only via Mods → Notedown → Configure → Open notebook (global notes only).
- [ ] New note → Save with an empty title names the file from the first line; the file lands in `notedown/local/<world>/`.
- [ ] Scope toggle moves an existing note between `local/<world>` and `global` and keeps its pin.
- [ ] Rename to a title that already exists → ` 2` suffix; delete asks first; duplicate creates `Title 2`.
- [ ] Editor: Enter continues `- `, `- [ ] `, `1. `; Enter on an empty item removes it; Tab/Shift+Tab; Ctrl+D / Ctrl+I wrap; Ctrl+Shift+C toggles the task; Ctrl+Z / Ctrl+Y; Ctrl+S saves without leaving; Esc with changes asks to discard.
- [ ] Preview toggle renders headings (3 sizes), bold/italic/strike, inline code, code block, bullets/numbers, checkboxes, quote bar, rule, link (click asks to open).
- [ ] View: checkbox click writes the file; scrollbar drag, wheel, PageUp/PageDown.
- [ ] Pin from View and from the Notebook; pins persist across world leave/join and game restart; a pin from another world is not shown here.
- [ ] Hold Alt: move, resize, scroll, tick, unpin, double-click to edit; release returns to play.
- [ ] Chat open: pins visible and clickable when the setting is on, hidden when off.
- [ ] Chest open: pins drawn on top and clickable when the setting is on; item tooltips still on top of pins.
- [ ] F1 hides pins; toggle key hides/shows with a chat message.
- [ ] GUI scale 1, 2, 3, 4 and Auto: no cut-off text in the notebook, editor, settings; pins stay on screen.
- [ ] Import…: file dialog opens, `.txt` and `.md` files become notes; cancel does nothing.
- [ ] A note larger than 1 MB opens in View but the editor refuses with a chat message.
- [ ] `latest.log` shows no Notedown warnings or stack traces during the above.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "release 1.0.0"
```

---

## Done criteria

- `./gradlew build` is green on macOS and in GitHub Actions; every pure unit (`config`, `store`, `markdown`, `ui/Scroller`, `hud/PinGeometry`) has passing JUnit tests.
- The manual checklist in Task 14 is fully ticked on the Prism "FO 26.2" instance.
- Every user-visible string resolves from `en_us.json` (no raw `notedown.` keys on screen).
- No vanilla button, edit box or list visuals appear anywhere in the mod's screens; the only vanilla-looking element is the blurred background.
- Spec sections (storage layout, markdown table, keybinds, HUD rules, config keys, error handling) match the built behaviour; any deviation is edited into the spec in the same commit.
