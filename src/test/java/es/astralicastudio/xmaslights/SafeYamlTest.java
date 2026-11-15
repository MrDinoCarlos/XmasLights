package es.astralicastudio.xmaslights;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SafeYamlTest {
    @TempDir Path directory;
    @Test void invalidYamlIsRejectedWithoutChangingTheFile() throws Exception {
        Path file = directory.resolve("config.yml"); String broken = "language: [\n";
        Files.writeString(file, broken);
        assertThrows(InvalidConfigurationException.class, () -> SafeYaml.load(file));
        assertEquals(broken, Files.readString(file));
    }
    @Test void atomicReplacementKeepsThePreviousFileAsBackup() throws Exception {
        Path file = directory.resolve("lights.yml"); Files.writeString(file, "old: true\n");
        var next = new YamlConfiguration(); next.set("new", 123);
        SafeYaml.save(next, file, true);
        assertEquals("old: true\n", Files.readString(directory.resolve("lights.yml.bak")));
        assertEquals(123, SafeYaml.load(file).getInt("new"));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
    @Test void mergePreservesValuesCustomTypesAndCommentsEvenWhenVersionMatches() throws Exception {
        var current = new YamlConfiguration();
        current.loadFromString("# User documentation\nconsume-item: false\ntypes:\n  warm:\n    name: Custom warm\n    size: 0.8\n  mine:\n    blocks: [minecraft:stone]\n");
        var defaults = new YamlConfiguration();
        defaults.loadFromString("consume-item: true\nnew-option: 42\ntypes:\n  warm:\n    name: Default warm\n    size: 0.4\n    spacing: 1.0\n");
        assertTrue(ResourceUpdater.mergeMissing(current, defaults));
        assertFalse(current.getBoolean("consume-item"));
        assertEquals("Custom warm", current.getString("types.warm.name"));
        assertEquals(.8, current.getDouble("types.warm.size"));
        assertEquals(1, current.getDouble("types.warm.spacing"));
        assertEquals(List.of("minecraft:stone"), current.getStringList("types.mine.blocks"));
        assertTrue(current.saveToString().contains("User documentation"));
        assertFalse(ResourceUpdater.mergeMissing(current, defaults));
    }
    @Test void mergeRejectsScalarWhereSectionIsRequired() {
        var current = new YamlConfiguration(); current.set("language", "es");
        var defaults = new YamlConfiguration(); defaults.set("language.default", "en");
        assertThrows(IllegalArgumentException.class, () -> ResourceUpdater.mergeMissing(current, defaults));
        assertEquals("es", current.getString("language"));
    }
    @Test void typeUpgradeDoesNotChangeAHeadsTypeIntoBlocksOrViceVersa() throws Exception {
        var current = new YamlConfiguration();
        current.loadFromString("types:\n  christmas:\n    blocks: [minecraft:stone]\n    name: My design\n  custom:\n    heads: [my-texture]\n");
        var defaults = ResourcesTest.resource("types.yml");
        assertTrue(ResourceUpdater.mergeTypes(current, defaults));
        assertFalse(current.contains("types.christmas.heads"));
        assertEquals("My design", current.getString("types.christmas.name"));
        assertEquals(List.of("my-texture"), current.getStringList("types.custom.heads"));
        assertTrue(current.isConfigurationSection("types.warm"));
        assertFalse(ResourceUpdater.mergeTypes(current, defaults));
    }
}
