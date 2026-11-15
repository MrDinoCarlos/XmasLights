package es.astralicastudio.xmaslights;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Adds missing leaves on every load, preserving custom values, types and comments. */
final class ResourceUpdater {
    private final XmasLightsPlugin plugin;
    ResourceUpdater(XmasLightsPlugin plugin) { this.plugin = plugin; }
    YamlConfiguration update(String resource, String versionKey) throws Exception {
        Path path = plugin.getDataFolder().toPath().resolve(resource);
        YamlConfiguration defaults = plugin.loadResource(resource);
        if (!Files.exists(path)) {
            SafeYaml.save(defaults, path, false);
            return defaults;
        }
        YamlConfiguration current = SafeYaml.load(path);
        if (current.contains(versionKey) && !current.isInt(versionKey)) throw new ConfigProblem("integer", "path", versionKey);
        if (current.getInt(versionKey) > defaults.getInt(versionKey)) throw new IllegalStateException(resource + ": " + plugin.messages().plain("errors.newer-schema"));
        boolean changed = false;
        if (current.getInt(versionKey, 1) < 13 && resource.equals("config.yml")) {
            var previous = java.util.Map.<String, Number>of("strobelights.view-distance", 192.0,
                    "strobelights.max-sources-per-player", 32, "strobelights.max-carriers-total", 128,
                    "strobelights.surfaces.probes-per-update", 16);
            for (var entry : previous.entrySet()) if (current.isSet(entry.getKey()) && current.getDouble(entry.getKey()) == entry.getValue().doubleValue()) {
                current.set(entry.getKey(), defaults.get(entry.getKey())); current.setComments(entry.getKey(), defaults.getComments(entry.getKey())); changed = true;
            }
        }
        if (current.getInt(versionKey, 1) < 12 && resource.equals("config.yml")) {
            var previous = java.util.Map.of("limits.max-strings", 256, "limits.max-strings-per-player", 64);
            for (var entry : previous.entrySet()) if (current.isInt(entry.getKey()) && current.getInt(entry.getKey()) == entry.getValue()) {
                current.set(entry.getKey(), defaults.get(entry.getKey())); current.setComments(entry.getKey(), defaults.getComments(entry.getKey())); changed = true;
            }
        }
        if (current.getInt(versionKey, 1) < 11 && resource.equals("config.yml")) {
            String key = "strobelights.surfaces.follow-effects";
            boolean bundled = current.getComments(key).stream().anyMatch(line -> line != null &&
                    line.equals("false: steady surface colour; bulb animation still runs normally."));
            if (bundled && !current.getBoolean(key)) {
                current.set(key, defaults.get(key)); current.setComments(key, defaults.getComments(key)); changed = true;
            }
            key = "progressive-switch";
            if (current.getComments(key).contains("Switch bulbs on/off sequentially. This is separate from the animation mode.")) {
                current.setComments(key, defaults.getComments(key)); changed = true;
            }
        }
        if (current.getInt(versionKey, 1) < 10 && resource.equals("config.yml")) {
            for (String key : java.util.List.of("light-blocks", "strobelights")) {
                boolean bundled = current.getComments(key).stream().anyMatch(line -> line != null &&
                        (line.startsWith("Replaces air with invisible minecraft:light blocks.")
                                || line.startsWith("Requires an enabled StrobeLights ")));
                if (bundled && !current.getComments(key).equals(defaults.getComments(key))) {
                    current.setComments(key, defaults.getComments(key)); changed = true;
                }
            }
        }
        if (current.getInt(versionKey, 1) < 9 && resource.equals("config.yml")) {
            var previous = java.util.Map.<String, Number>of("strobelights.view-distance", 96, "strobelights.intensity-scale", .90,
                    "strobelights.surfaces.max-radius", 3.25, "strobelights.surfaces.distance-fade", 12);
            for (var entry : previous.entrySet()) if (current.isSet(entry.getKey()) && current.getDouble(entry.getKey()) == entry.getValue().doubleValue()) {
                current.set(entry.getKey(), defaults.get(entry.getKey())); current.setComments(entry.getKey(), defaults.getComments(entry.getKey())); changed = true;
            }
        }
        if (current.getInt(versionKey, 1) < 8 && resource.equals("config.yml")) {
            var previous = java.util.Map.<String, Number>of("strobelights.view-distance", 24, "strobelights.max-sources-per-player", 16,
                    "strobelights.intensity-scale", .75, "strobelights.surfaces.max-radius", 2.25,
                    "strobelights.surfaces.distance-fade", 6, "strobelights.surfaces.probes-per-update", 12);
            for (var entry : previous.entrySet()) if (current.isSet(entry.getKey()) && current.getDouble(entry.getKey()) == entry.getValue().doubleValue()) {
                current.set(entry.getKey(), defaults.get(entry.getKey())); current.setComments(entry.getKey(), defaults.getComments(entry.getKey())); changed = true;
            }
        }
        if (current.getInt(versionKey, 1) < 7 && resource.equals("types.yml") && current.isConfigurationSection("types")) {
            for (String id : current.getConfigurationSection("types").getKeys(false)) {
                String typePath = "types." + id;
                if (id.startsWith("wall-") && defaults.isConfigurationSection(typePath) && !current.getBoolean(typePath + ".custom") && !current.contains(typePath + ".sag")) {
                    current.set(typePath + ".sag", 0); changed = true;
                }
            }
        }
        if (current.getInt(versionKey, 1) < 5 && resource.equals("config.yml")) changed |= migratePerformance(current, defaults);
        if (current.getInt(versionKey, 1) < 5 && resource.equals("types.yml")) changed |= migrateColoredHeads(current, defaults);
        if (current.getInt(versionKey, 1) < 6 && resource.equals("types.yml")) changed |= migrateLampDesigns(current, defaults);
        if (current.getInt(versionKey, 1) < 6 && resource.equals("config.yml") && current.getDouble("strobelights.expansion") == .5) {
            current.set("strobelights.expansion", defaults.getDouble("strobelights.expansion")); changed = true;
        }
        if (current.getInt(versionKey, 1) < 6 && resource.equals("config.yml") && current.getBoolean("animated-light")) {
            current.set("animated-light", defaults.getBoolean("animated-light")); changed = true;
        }
        if (current.getInt(versionKey, 1) < 7 && resource.equals("config.yml")) {
            for (String key : java.util.List.of("light-blocks", "strobelights")) {
                boolean bundled = current.getComments(key).stream().anyMatch(line -> line != null &&
                        (line.startsWith("Replaces air with invisible minecraft:light blocks.")
                                || line.equals("Without the plugin/pack, ordinary LIGHT-block illumination remains available.")));
                if (bundled && !current.getComments(key).equals(defaults.getComments(key))) {
                    current.setComments(key, defaults.getComments(key)); changed = true;
                }
            }
        }
        if (resource.equals("config.yml")) changed |= migrateVisibility(current, defaults);
        if (resource.equals("config.yml") && current.getInt(versionKey, 1) < 15) {
            for (var entry : java.util.Map.<String, Object>of("animated-light", false,
                    "view-distance-chunks", 6, "limits.max-entities", 4096).entrySet()) {
                Object old = current.get(entry.getKey());
                boolean bundled = old instanceof Number n && entry.getValue() instanceof Number previous
                        ? n.doubleValue() == previous.doubleValue() : java.util.Objects.equals(old, entry.getValue());
                if (bundled) {
                    current.set(entry.getKey(), defaults.get(entry.getKey()));
                    current.setComments(entry.getKey(), defaults.getComments(entry.getKey())); changed = true;
                }
            }
        }
        if (resource.equals("config.yml") && current.contains("strobelights.renderer")) {
            current.set("strobelights.renderer", null); changed = true;
        }
        changed |= resource.equals("types.yml") ? mergeTypes(current, defaults) : mergeMissing(current, defaults);
        if (current.getInt(versionKey) != defaults.getInt(versionKey)) {
            current.set(versionKey, defaults.get(versionKey));
            changed = true;
        }
        if (changed) {
            SafeYaml.versionBackup(path);
            SafeYaml.save(current, path, false);
        }
        return current;
    }
    static boolean migrateVisibility(YamlConfiguration current, YamlConfiguration defaults) {
        boolean changed = false;
        for (String old : java.util.List.of("view-range", "strobelights.view-distance")) {
            if (!current.contains(old)) continue;
            if (!(current.get(old) instanceof Number n) || !Double.isFinite(n.doubleValue())
                    || n.doubleValue() < 0 || (old.equals("view-range") && (n.doubleValue() < .1 || n.doubleValue() > 8))
                    || (!old.equals("view-range") && (n.doubleValue() > 1024 || n.doubleValue() > 0 && n.doubleValue() < 4)))
                throw new IllegalArgumentException(old + ": invalid legacy distance");
            String key = old.equals("view-range") ? "view-distance-chunks" : "strobelights.view-distance-chunks";
            // The old display multiplier represented 64 blocks per unit.
            if (!current.contains(key) || current.getInt("config-version", 1) < 14)
                current.set(key, old.equals("view-range") ? n.doubleValue() * 4 : n.doubleValue() / 16);
            current.setComments(key, defaults.getComments(key)); current.set(old, null); changed = true;
        }
        if (current.contains("strobelights.expansion")) { current.set("strobelights.expansion", null); changed = true; }
        return changed;
    }
    static boolean mergeMissing(YamlConfiguration current, YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                if (current.contains(key, true) && !current.isConfigurationSection(key)) throw new ConfigProblem("section", "path", key);
                continue;
            }
            if (!current.contains(key, true)) {
                current.set(key, Objects.requireNonNull(defaults.get(key)));
                current.setComments(key, defaults.getComments(key));
                changed = true;
            }
        }
        for (String key : defaults.getKeys(true)) {
            if (current.getComments(key).isEmpty() && !defaults.getComments(key).isEmpty()) {
                current.setComments(key, defaults.getComments(key));
                changed = true;
            }
        }
        return changed;
    }
    static boolean mergeTypes(YamlConfiguration current, YamlConfiguration defaults) {
        boolean changed = false;
        if (current.contains("types") && !current.isConfigurationSection("types")) throw new ConfigProblem("section", "path", "types");
        var bundledTypes = Objects.requireNonNull(defaults.getConfigurationSection("types"));
        for (String id : bundledTypes.getKeys(false)) {
            String path = "types." + id;
            if (!current.contains(path, true)) {
                var type = Objects.requireNonNull(bundledTypes.getConfigurationSection(id));
                for (String key : type.getKeys(true)) if (!type.isConfigurationSection(key)) {
                    current.set(path + "." + key, type.get(key));
                    current.setComments(path + "." + key, type.getComments(key));
                }
                changed = true;
            } else {
                if (!current.isConfigurationSection(path)) throw new ConfigProblem("section", "path", path);
                // Adding heads/blocks to an existing type can silently change its renderer.
                if (!current.contains(path + ".name-key", true) && !current.getBoolean(path + ".custom")
                        && Objects.equals(current.getString(path + ".name"), defaults.getString(path + ".name"))) {
                    current.set(path + ".name-key", defaults.getString(path + ".name-key"));
                    changed = true;
                }
            }
        }
        return changed;
    }
    static boolean migratePerformance(YamlConfiguration current, YamlConfiguration defaults) {
        var old = java.util.Map.<String, Number>of("limits.max-entities", 20000, "limits.max-bulbs-per-segment", 128,
                "limits.max-strings", 512, "strobelights.update-ticks", 2, "strobelights.source-spacing", 1.5,
                "strobelights.max-sources-per-player", 32, "strobelights.max-carriers-total", 256, "strobelights.view-distance", 32.0);
        boolean changed = false;
        for (var entry : old.entrySet()) if (current.isSet(entry.getKey()) && current.getDouble(entry.getKey()) == entry.getValue().doubleValue()) {
            current.set(entry.getKey(), defaults.get(entry.getKey())); changed = true;
        }
        return changed;
    }
    static boolean migrateLampDesigns(YamlConfiguration current, YamlConfiguration defaults) {
        var glass = java.util.Map.of(
                "warm", java.util.List.of("minecraft:ochre_froglight"),
                "multicolor", java.util.List.of("minecraft:red_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass", "minecraft:magenta_stained_glass"),
                "red-white", java.util.List.of("minecraft:red_stained_glass", "minecraft:white_stained_glass"),
                "rainbow", java.util.List.of("minecraft:red_stained_glass", "minecraft:orange_stained_glass", "minecraft:yellow_stained_glass", "minecraft:lime_stained_glass", "minecraft:green_stained_glass", "minecraft:cyan_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:blue_stained_glass", "minecraft:purple_stained_glass", "minecraft:magenta_stained_glass", "minecraft:pink_stained_glass"));
        boolean changed = false;
        for (String id : java.util.Objects.requireNonNull(defaults.getConfigurationSection("types")).getKeys(false)) {
            String path = "types." + id;
            if (!current.isConfigurationSection(path) || current.getBoolean(path + ".custom") || id.startsWith("garland-")) continue;
            String base = id.startsWith("wall-") ? id.substring(5) : id;
            boolean cube = glass.containsKey(base) && !current.contains(path + ".model") && !current.contains(path + ".heads")
                    && current.getStringList(path + ".blocks").equals(glass.get(base))
                    && current.getDouble(path + ".size") == (id.startsWith("wall-") ? .32 : .42);
            if (cube) {
                for (String key : java.util.List.of("model", "size", "blocks")) current.set(path + "." + key, defaults.get(path + "." + key));
                if (!current.contains(path + ".rgb")) current.set(path + ".rgb", defaults.get(path + ".rgb"));
                changed = true;
            }
            boolean bundledLamp = cube || "bulb".equals(current.getString(path + ".model"))
                    && current.getStringList(path + ".blocks").equals(defaults.getStringList(path + ".blocks"));
            if ((bundledLamp || id.equals("wall-lanterns")) && !current.contains(path + ".chain")) {
                current.set(path + ".chain", defaults.get(path + ".chain")); changed = true;
            }
            if (id.equals("wall-lanterns") && current.getStringList(path + ".blocks").equals(java.util.List.of("minecraft:lantern"))) {
                current.set(path + ".blocks", defaults.getStringList(path + ".blocks"));
                current.set(path + ".wire", true); changed = true;
            }
        }
        return changed;
    }
    static boolean migrateColoredHeads(YamlConfiguration current, YamlConfiguration defaults) {
        String red = "8e717b485d95b3bd14db1350c55a29da99ee1d3195c050617088a9677ac42";
        String green = "47a985c31ad461e322b39c433eae79ff46fb64800cdecadf2f6d0f9727252";
        String yellow = "91ff6a55e971931eb0626565dad7771419fae9887aa6d52bf1995717860c";
        var old = java.util.Map.of("red", java.util.List.of(red), "green", java.util.List.of(green), "yellow", java.util.List.of(yellow),
                "wall-yellow", java.util.List.of(yellow), "christmas", java.util.List.of(red, green, yellow), "wall-christmas", java.util.List.of(red, green, yellow),
                "blue", java.util.List.of("3f73a3dd94a89994757df9be6330cc3e84839230de91d45bfdac1927272fc"),
                "orange", java.util.List.of("26c406242f5bf9786d883cb6a2581ce299c66f4ed8897c8b85769e4d2a3831"));
        boolean changed = false;
        for (String id : java.util.List.of("garland-classic", "garland-snowy", "garland-rgb", "garland-draped")) {
            String path = "types." + id;
            if (!current.getBoolean(path + ".custom") && current.getDouble(path + ".spacing") == .75
                    && Objects.equals(current.getString(path + ".model"), defaults.getString(path + ".model"))) {
                current.set(path + ".spacing", defaults.getDouble(path + ".spacing")); changed = true;
            }
        }
        for (var entry : old.entrySet()) {
            String path = "types." + entry.getKey();
            if (current.getBoolean(path + ".custom") || current.contains(path + ".model") || !current.getStringList(path + ".heads").equals(entry.getValue())) continue;
            current.set(path + ".heads", null); current.set(path + ".model", "bulb");
            current.set(path + ".blocks", defaults.getStringList(path + ".blocks"));
            if (!current.contains(path + ".rgb")) current.set(path + ".rgb", defaults.getStringList(path + ".rgb"));
            if (!current.contains(path + ".item")) current.set(path + ".item", defaults.getString(path + ".item"));
            double originalSize = entry.getKey().startsWith("wall-") ? .37 : .44;
            if (current.getDouble(path + ".size", originalSize) == originalSize) current.set(path + ".size", defaults.getDouble(path + ".size"));
            changed = true;
        }
        return changed;
    }
}
