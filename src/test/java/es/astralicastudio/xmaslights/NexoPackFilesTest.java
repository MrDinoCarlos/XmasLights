package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class NexoPackFilesTest {
    @TempDir Path folder;
    @Test void installationIsIdempotentAndDoesNotTouchOtherNexoResources() throws Exception {
        Files.createDirectories(folder.resolve("pack/external_packs"));
        Path other=folder.resolve("pack/external_packs/Other.zip"); Files.writeString(other,"unrelated");
        Path settings=folder.resolve("settings.yml"); Files.writeString(settings,"custom: true\n");
        var first=NexoPackBridge.install(folder,new byte[]{1,2,3}); assertTrue(first.changed());
        var timestamp=Files.getLastModifiedTime(first.path());
        var second=NexoPackBridge.install(folder,new byte[]{1,2,3}); assertFalse(second.changed());
        assertEquals(timestamp,Files.getLastModifiedTime(second.path())); assertEquals("unrelated",Files.readString(other));
        assertEquals("custom: true\n",Files.readString(settings));
    }
    @Test void updatingAnInputPreservesItsPreviousBytesAndLeavesNoTemporaryFiles() throws Exception {
        var first=NexoPackBridge.install(folder,new byte[]{1,2,3});
        var second=NexoPackBridge.install(folder,new byte[]{4,5,6}); assertTrue(second.changed());
        assertArrayEquals(new byte[]{4,5,6},Files.readAllBytes(first.path()));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(first.path().resolveSibling(NexoPackBridge.INPUT_NAME+".bak")));
        try(var paths=Files.list(first.path().getParent())) { assertTrue(paths.noneMatch(path->path.toString().endsWith(".tmp"))); }
    }
}
