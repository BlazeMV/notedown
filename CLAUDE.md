# Notedown

- build: `./gradlew build` → `build/libs/notedown-1.0.0.jar`; tests: `./gradlew test`
- dev install: `scripts/dev-install.sh` (copies jar into Prism `FO 26.2` mods folder). Remove `Notes-*.jar` there first: both bind N.
- JDK 25 (javac) lives at `/Users/blaze/Library/Application Support/PrismLauncher/java/java-runtime-epsilon`; registered in user-level `~/.gradle/gradle.properties`, not committed. Use its `javap` for API checks; the system `javap` is Java 23.
- Loom uses official Mojang names by default (Loom 1.17+ on 26.x) — no mappings dependency
- client-only mod: all code under `src/client/java`. `markdown`, `store`, `hud/PinGeometry`, `ui/Scroller` have no Minecraft imports and are unit-tested.
- `notedown.accesswidener` opens `MultiLineEditBox` ctor + `textField` and the protected `MultilineTextField$StringView` class; its namespace must stay `official`. commonmark jars are nested via Loom `include`.
