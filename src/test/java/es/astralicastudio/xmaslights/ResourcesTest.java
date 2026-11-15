package es.astralicastudio.xmaslights;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourcesTest {
    @Test void decorativePackDoesNotContainReplacementSurfaceAssets() throws Exception {
        try (var input = Objects.requireNonNull(ResourcesTest.class.getResourceAsStream("/packs/XmasLights-Models-1.21.4.zip"));
             var archive = new java.util.zip.ZipInputStream(input)) {
            for (var entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                assertFalse(entry.getName().contains("/font/surface"));
                assertFalse(entry.getName().contains("/shaders/"));
            }
        }
        assertFalse(resource("config.yml").contains("strobelights.renderer"));
    }
    static YamlConfiguration resource(String name) throws Exception {
        try (var stream = Objects.requireNonNull(ResourcesTest.class.getResourceAsStream("/" + name));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var yaml = new YamlConfiguration(); yaml.load(reader); return yaml;
        }
    }
    @Test void allLanguagesHaveExactlyTheSameKeysAndPlaceholders() throws Exception {
        var english = resource("lang/en.yml");
        for (String language : Messages.LANGUAGES) {
            var catalog = resource("lang/" + language + ".yml");
            assertEquals(english.getKeys(true), catalog.getKeys(true), language);
            assertDoesNotThrow(() -> Messages.validateCatalog(catalog, english), language);
        }
    }
    @Test void allModesBuiltInNamesAndHelpEntriesAreTranslated() throws Exception {
        var catalog = resource("lang/en.yml"); var types = resource("types.yml");
        for (AnimationMode mode : AnimationMode.values()) assertTrue(catalog.isString("modes." + mode.id()), mode.id());
        for (String id : Objects.requireNonNull(types.getConfigurationSection("types")).getKeys(false)) {
            assertTrue(catalog.isString(types.getString("types." + id + ".name-key", "MISSING")), id);
        }
        for (String action : List.of("give", "remove", "spawn", "despawn", "regenerate", "on", "off", "mode", "group", "groups", "chain", "save", "types", "reload", "owner", "pack")) assertTrue(catalog.isString("help." + action), action);
    }
    @Test void defaultsAndCreditsAreCorrect() throws Exception {
        assertEquals("en", resource("config.yml").getString("language.default"));
        assertFalse(resource("config.yml").getBoolean("language.use-player-locale"));
        assertEquals("1.21.4", resource("plugin.yml").getString("api-version"));
        assertEquals(List.of("AstralicaStudio", "MrDino"), resource("plugin.yml").getStringList("authors"));
    }
    @Test void missingPlaceholdersAreRejected() throws Exception {
        var english = resource("lang/en.yml"); var edited = resource("lang/es.yml");
        edited.set("commands.given", "Entregado.");
        assertThrows(IllegalArgumentException.class, () -> Messages.validateCatalog(edited, english));
    }
    @Test void literalMessageReferencesInEverySourceFileExistInEnglish() throws Exception {
        var english = resource("lang/en.yml");
        var pattern = java.util.regex.Pattern.compile("\"((?:errors|commands|log|help|usage|groups|kit|labels|placement|names|validation|pack)\\.[a-z][a-z0-9-]*)\"");
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = pattern.matcher(java.nio.file.Files.readString(file));
                while (matcher.find()) if (!matcher.group(1).endsWith("-") && !Set.of("pack.mcmeta","pack.png").contains(matcher.group(1))) assertTrue(english.isString(matcher.group(1)), file + ": " + matcher.group(1));
            }
        }
    }
    @Test void originalIdsAndTheirTranslatedNamesArePreservedAcrossTheDesignReview() throws Exception {
        var current=resource("types.yml");
        try(var jar=new java.util.jar.JarFile("original/XmasLights-2.1.0.jar"); var stream=jar.getInputStream(jar.getJarEntry("types.yml"))) {
            var original=new YamlConfiguration(); original.loadFromString(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
            assertEquals(20,original.getConfigurationSection("types").getKeys(false).size());
            for(String id:original.getConfigurationSection("types").getKeys(false)) {
                assertTrue(current.isConfigurationSection("types."+id),id);
                assertEquals(original.getString("types."+id+".name"),current.getString("types."+id+".name"),id);
            }
        }
    }
}
