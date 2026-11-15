package es.astralicastudio.xmaslights;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One-time copy of LucesNavidad data. Source files remain untouched except for a marker. */
final class LegacyMigration {
    private static final String MARKER = "MIGRATED-TO-XMASLIGHTS.txt";
    private static final Map<String, String> TYPE_IDS = Map.ofEntries(Map.entry("calidas", "warm"), Map.entry("multicolor", "multicolor"), Map.entry("rojiblancas", "red-white"), Map.entry("arcoiris", "rainbow"), Map.entry("navidad", "christmas"), Map.entry("rojas", "red"), Map.entry("verdes", "green"), Map.entry("azules", "blue"), Map.entry("amarillas", "yellow"), Map.entry("naranjas", "orange"), Map.entry("blancas", "white"), Map.entry("farolillos", "lanterns"), Map.entry("farolillos-alma", "soul-lanterns"), Map.entry("farolillos-mixtos", "mixed-lanterns"), Map.entry("pared-calidas", "wall-warm"), Map.entry("pared-multicolor", "wall-multicolor"), Map.entry("pared-rojiblancas", "wall-red-white"), Map.entry("pared-arcoiris", "wall-rainbow"), Map.entry("pared-navidad", "wall-christmas"), Map.entry("pared-amarillas", "wall-yellow"), Map.entry("pared-blancas", "wall-white"), Map.entry("pared-farolillos", "wall-lanterns"));
    private static final Set<String> STILL_BUILT_IN = Set.of("calidas", "multicolor", "rojiblancas", "arcoiris", "navidad", "rojas", "verdes", "azules", "amarillas", "naranjas", "farolillos", "farolillos-alma", "farolillos-mixtos", "pared-calidas", "pared-multicolor", "pared-rojiblancas", "pared-arcoiris", "pared-navidad", "pared-amarillas", "pared-farolillos");
    private static final Map<String, String> CONFIG_KEYS = new LinkedHashMap<String, String>();
    private static final Map<String, String> TYPE_KEYS = new LinkedHashMap<String, String>();
    private static final Map<String, String> MODES = Map.of("fijo", "steady", "parpadeo", "blink", "alterno", "alternate", "persecucion", "chase", "centelleo", "twinkle", "barrido", "sweep", "respiracion", "breathe");


    private LegacyMigration() { }
    static String translateTypeId(String id) {
        return id == null ? null : TYPE_IDS.getOrDefault(id.toLowerCase(Locale.ROOT), id);
    }

    static void run(XmasLightsPlugin plugin) throws Exception {
        if (plugin.getServer().getPluginManager().getPlugin("LucesNavidad") != null) {
            throw new IllegalStateException(plugin.messages().plain("errors.legacy-plugin"));
        }
        Path target = plugin.getDataFolder().toPath();
        Path legacy = target.resolveSibling("LucesNavidad");
        if (!Files.isDirectory(legacy) || Files.exists(legacy.resolve(MARKER))) return;
        for (String file : List.of("config.yml", "types.yml", "lights.yml")) if (Files.exists(target.resolve(file))) return;
        plugin.messages().info("log.migration-start");
        Files.createDirectories(target);
        Path staging = Files.createTempDirectory(target, ".legacy-import-");
        List<Path> installed = new ArrayList<>();
        try {
            YamlConfiguration config = SafeYaml.load(legacy.resolve("config.yml"));
            YamlConfiguration nextConfig = plugin.loadResource("config.yml");
            for (var entry : CONFIG_KEYS.entrySet()) if (config.contains(entry.getKey()) && !config.isConfigurationSection(entry.getKey())) nextConfig.set(entry.getValue(), config.get(entry.getKey()));
            SafeYaml.save(nextConfig, staging.resolve("config.yml"), false);
            int types = migrateTypes(plugin, config, legacy, staging);
            int strings = migrateLights(legacy, staging);
            // Parse everything first. A partial installation is rolled back if any move fails.
            for (String file : List.of("config.yml", "types.yml", "lights.yml")) {
                Path prepared = staging.resolve(file);
                if (!Files.exists(prepared)) continue;
                Path destination = target.resolve(file);
                Files.move(prepared, destination); installed.add(destination);
            }
            Files.writeString(legacy.resolve(MARKER), plugin.messages().plain("migration.marker") + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            plugin.messages().info("log.migration-done", "strings", strings, "types", types);
        } catch (Exception failure) {
            for (Path file : installed) Files.deleteIfExists(file);
            throw failure;
        } finally {
            for (String file : List.of("config.yml", "types.yml", "lights.yml")) Files.deleteIfExists(staging.resolve(file));
            Files.deleteIfExists(staging);
        }
    }

    private static int migrateTypes(XmasLightsPlugin plugin, YamlConfiguration config, Path legacy, Path staging) throws Exception {
        YamlConfiguration result = plugin.loadResource("types.yml");
        int count = 0;
        for (ConfigurationSection source : List.of(sectionOrEmpty(config, "tipos"), sectionOrEmpty(SafeYaml.load(legacy.resolve("tipos.yml")), "tipos"))) {
            for (String id : source.getKeys(false)) {
                ConfigurationSection type = Objects.requireNonNull(source.getConfigurationSection(id));
                if (STILL_BUILT_IN.contains(id.toLowerCase(Locale.ROOT)) && !type.getBoolean("personalizado")) continue;
                String path = "types." + translateTypeId(id);
                result.set(path, null);
                for (String key : type.getKeys(false)) {
                    if (type.isConfigurationSection(key)) continue;
                    String translated = TYPE_KEYS.getOrDefault(key, key);
                    Object value = type.get(key);
                    if (translated.equals("style")) value = "pared".equalsIgnoreCase(String.valueOf(value)) ? "wall" : "hanging";
                    result.set(path + "." + translated, value);
                }
                result.set(path + ".custom", true);
                count++;
            }
        }
        SafeYaml.save(result, staging.resolve("types.yml"), false);
        return count;
    }

    private static int migrateLights(Path legacy, Path staging) throws Exception {
        Path file = legacy.resolve("guirnaldas.yml");
        if (!Files.exists(file)) return 0;
        YamlConfiguration source = SafeYaml.load(file), result = new YamlConfiguration();
        result.set("groups", source.getStringList("grupos"));
        ConfigurationSection strings = sectionOrEmpty(source, "guirnaldas");
        int count = 0;
        Map<String, String> keys = Map.ofEntries(
            Map.entry("mundo", "world"), Map.entry("a", "a"), Map.entry("b", "b"), Map.entry("normal", "normal"),
            Map.entry("ancla-a", "anchor-a"), Map.entry("ancla-b", "anchor-b"), Map.entry("caida", "sag"),
            Map.entry("grupo", "group"), Map.entry("cadena", "chain"), Map.entry("encendida", "on"),
            Map.entry("bombillas", "bulbs"), Map.entry("visible", "spawned"), Map.entry("inicio-omitido", "skip-start"),
            Map.entry("generacion", "generation"), Map.entry("bloques-luz", "light-blocks"));
        for (String id : strings.getKeys(false)) {
            var string = Objects.requireNonNull(strings.getConfigurationSection(id));
            String path = "lights." + id + ".";
            for (var key : keys.entrySet()) if (string.contains(key.getKey())) result.set(path + key.getValue(), string.get(key.getKey()));
            result.set(path + "type", translateTypeId(string.getString("tipo")));
            String mode = string.getString("modo", "fijo");
            result.set(path + "mode", MODES.getOrDefault(mode.toLowerCase(Locale.ROOT), "steady"));
            count++;
        }
        SafeYaml.save(result, staging.resolve("lights.yml"), false);
        return count;
    }

    private static ConfigurationSection sectionOrEmpty(YamlConfiguration yaml, String key) {
        if (yaml.contains(key) && !yaml.isConfigurationSection(key)) throw new IllegalArgumentException(key);
        var section = yaml.getConfigurationSection(key);
        return section == null ? new YamlConfiguration() : section;
    }

    static {
        CONFIG_KEYS.put("longitud-maxima", "max-length");
        CONFIG_KEYS.put("caida", "sag");
        CONFIG_KEYS.put("separacion-bombillas", "bulb-spacing");
        CONFIG_KEYS.put("consumir-item", "consume-item");
        CONFIG_KEYS.put("bloques-luz", "light-blocks");
        CONFIG_KEYS.put("nivel-luz", "light-level");
        CONFIG_KEYS.put("luz-animada", "animated-light");
        CONFIG_KEYS.put("brillo-encendido", "brightness-on");
        CONFIG_KEYS.put("brillo-apagado", "brightness-off");
        CONFIG_KEYS.put("ticks-animacion", "animation-ticks");
        CONFIG_KEYS.put("pasos-efecto", "effect-steps");
        CONFIG_KEYS.put("periodo-respiracion", "breathe-period");
        CONFIG_KEYS.put("encendido-progresivo", "progressive-switch");
        CONFIG_KEYS.put("velocidad-progresiva", "progressive-speed");
        CONFIG_KEYS.put("solo-de-noche", "night-only");
        CONFIG_KEYS.put("distancia-vision", "view-range");
        CONFIG_KEYS.put("rejilla-pared", "wall-grid");
        CONFIG_KEYS.put("cabeza-centro-y", "head-center-y");
        CONFIG_KEYS.put("cable.bloque", "wire.block");
        CONFIG_KEYS.put("cable.grosor", "wire.thickness");
        CONFIG_KEYS.put("cable.precision", "wire.precision");
        TYPE_KEYS.put("nombre", "name");
        TYPE_KEYS.put("estilo", "style");
        TYPE_KEYS.put("cable", "wire");
        TYPE_KEYS.put("item", "item");
        TYPE_KEYS.put("tamano", "size");
        TYPE_KEYS.put("separacion", "spacing");
        TYPE_KEYS.put("cadena", "chain");
        TYPE_KEYS.put("nivel-luz", "light-level");
        TYPE_KEYS.put("bloques", "blocks");
        TYPE_KEYS.put("cabezas", "heads");
        TYPE_KEYS.put("personalizado", "custom");
    }
}
