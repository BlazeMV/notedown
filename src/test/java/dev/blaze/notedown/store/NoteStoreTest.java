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
    void caseOnlyRenameKeepsSinglePin() throws Exception {
        Note n = store.create(world, "draft", "x");
        PinIndex idx = store.index(world);
        idx.put(n.fileName(), new PinIndex.Pin(0.5, 0.5, 100, 50, 1f, 0));
        idx.save();
        Note renamed = store.save(n, "Draft", "x", world);
        assertEquals("Draft.md", renamed.fileName());
        try (java.util.stream.Stream<Path> files = Files.list(world.dir())) {
            assertEquals(List.of("Draft.md"),
                    files.map(p -> p.getFileName().toString()).filter(name -> name.endsWith(NoteFiles.EXT)).sorted().toList());
        }
        assertEquals(List.of("Draft.md"), new java.util.ArrayList<>(store.index(world).pins().keySet()));
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
        PinIndex idx = store.index(global);
        idx.put(copy.fileName(), new PinIndex.Pin(0, 0, 60, 30, 1f, 0));
        idx.save();
        assertTrue(store.index(global).isPinned("Todo 2.md"));
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
