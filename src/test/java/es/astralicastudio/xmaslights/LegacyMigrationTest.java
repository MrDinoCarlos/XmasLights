package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyMigrationTest {
    @TempDir Path directory;
    private XmasLightsPlugin plugin;
    @BeforeEach void start() throws Exception {
        var server = MockBukkit.mock();
        plugin = mock(XmasLightsPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.resolve("XmasLights").toFile());
        when(plugin.getServer()).thenReturn(server); when(plugin.getLogger()).thenReturn(Logger.getLogger("XmasLights-migration-test"));
        when(plugin.loadResource(anyString())).thenAnswer(call -> ResourcesTest.resource(call.getArgument(0)));
        Messages messages = new Messages(plugin); when(plugin.messages()).thenReturn(messages);
        Files.createDirectories(directory.resolve("LucesNavidad"));
    }
    @AfterEach void stop() { MockBukkit.unmock(); }
    @Test void migrationCopiesConfigCustomTypesGroupsAndLightsWithoutDeletingOriginals() throws Exception {
        Path old = directory.resolve("LucesNavidad"); UUID id = UUID.randomUUID();
        Files.writeString(old.resolve("config.yml"), "longitud-maxima: 40\nconsumir-item: true\n");
        Files.writeString(old.resolve("tipos.yml"), "tipos:\n  especial:\n    personalizado: true\n    nombre: Mi luz\n    bloques: [minecraft:stone]\n    estilo: pared\n");
        var lights = new org.bukkit.configuration.file.YamlConfiguration();
        lights.set("grupos", List.of("Garden")); String path = "guirnaldas." + id + ".";
        lights.set(path + "mundo", "world"); lights.set(path + "tipo", "calidas"); lights.set(path + "modo", "parpadeo"); lights.set(path + "grupo", "Garden");
        lights.set(path + "a", "1,70,1"); lights.set(path + "b", "5,70,1"); lights.set(path + "ancla-a", "1,70,1"); lights.set(path + "ancla-b", "5,70,1");
        SafeYaml.save(lights, old.resolve("guirnaldas.yml"), false);
        LegacyMigration.run(plugin);
        Path next = directory.resolve("XmasLights");
        assertEquals(40, SafeYaml.load(next.resolve("config.yml")).getInt("max-length"));
        assertTrue(SafeYaml.load(next.resolve("config.yml")).getBoolean("consume-item"));
        assertEquals("wall", SafeYaml.load(next.resolve("types.yml")).getString("types.especial.style"));
        assertEquals("Mi luz", SafeYaml.load(next.resolve("types.yml")).getString("types.especial.name"));
        assertEquals("warm", SafeYaml.load(next.resolve("lights.yml")).getString("lights." + id + ".type"));
        assertEquals("blink", SafeYaml.load(next.resolve("lights.yml")).getString("lights." + id + ".mode"));
        assertTrue(Files.exists(old.resolve("guirnaldas.yml"))); assertTrue(Files.exists(old.resolve("MIGRATED-TO-XMASLIGHTS.txt")));
    }
    @Test void invalidLegacyYamlStopsTheImportWithoutInstallingPartialFiles() throws Exception {
        Path old = directory.resolve("LucesNavidad");
        Files.writeString(old.resolve("config.yml"), "longitud-maxima: 40\n");
        Files.writeString(old.resolve("tipos.yml"), "tipos: [\n");
        assertThrows(Exception.class, () -> LegacyMigration.run(plugin));
        assertFalse(Files.exists(directory.resolve("XmasLights/config.yml")));
        assertFalse(Files.exists(old.resolve("MIGRATED-TO-XMASLIGHTS.txt")));
        assertEquals("tipos: [\n", Files.readString(old.resolve("tipos.yml")));
    }
}
