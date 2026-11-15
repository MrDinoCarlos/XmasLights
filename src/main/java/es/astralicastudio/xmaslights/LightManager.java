package es.astralicastudio.xmaslights;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public final class LightManager {
    static final Pattern GROUP_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private final XmasLightsPlugin plugin;
    private final NamespacedKey keyString, keyIndex, keyKit, keyGeneration;
    private final NamespacedKey keyModel, keyModule, keyCovered;
    final NamespacedKey keyPart;
    private final NamespacedKey legacyString = Objects.requireNonNull(NamespacedKey.fromString("lucesnavidad:guirnalda"));
    private final NamespacedKey legacyKit = Objects.requireNonNull(NamespacedKey.fromString("lucesnavidad:kit"));
    private final File dataFile;
    private final Map<UUID, LightString> strings = new LinkedHashMap<>();
    private final Map<String, LightType> types = new LinkedHashMap<>();
    private final Set<String> groups = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<UUID, String> editing = new HashMap<>();
    private final Set<UUID> noChaining = new HashSet<>();
    private final LightBlocks realLights = new LightBlocks();
    private final StrobeLightsBridge coloredLights;
    private final Map<String, ItemStack> modelItems = new HashMap<>();
    private boolean realLightsDirty = true;
    private boolean nativeLightSuppressed;
    private final ArrayDeque<UUID> updateQueue = new ArrayDeque<>();
    private record BackingChunk(String world, int x, int z) { }
    // Retain the old locations even if their logical segments are deleted during this session.
    private final Set<BackingChunk> legacyGeometryChunks = new LinkedHashSet<>();
    private final Set<BackingChunk> dirtyBackings = new HashSet<>();
    private final Set<String> dirtyBackingWorlds = new HashSet<>();
    private final Set<UUID> backingQueue = new LinkedHashSet<>();
    private BukkitTask animationTask, updateTask;
    private boolean dataReady, closing;
    private long step;
    private int maxLength, lightLevel, brightOn, brightOff, animationTicks, effectSteps, breathePeriod, progressiveSpeed;
    private int maxBulbs, maxStrings, maxPerPlayer, maxEntities, updatesPerTick, bodyLightFloor;
    private double sagFactor, garlandSag, bulbSpacing, wallGrid, headCenterY, wirePrecision;
    private boolean consumeItem, placeLightBlocks, animatedLight, progressive, nightOnly, surfaceEffects;
    private float viewRange, wireThickness;
    private BlockData wireBlock, chainBlock;
    private String globalSignature;

    public LightManager(XmasLightsPlugin plugin) {
        this.plugin = plugin;
        keyString = new NamespacedKey(plugin, "string");
        keyIndex = new NamespacedKey(plugin, "index");
        keyKit = new NamespacedKey(plugin, "kit");
        keyGeneration = new NamespacedKey(plugin, "generation");
        keyModel = new NamespacedKey(plugin, "garland_model");
        keyModule = new NamespacedKey(plugin, "garland_module"); keyCovered = new NamespacedKey(plugin, "covered_side");
        keyPart = new NamespacedKey(plugin, "part");
        dataFile = new File(plugin.getDataFolder(), "lights.yml");
        coloredLights = new StrobeLightsBridge(plugin);
    }

    static void validateSettings(YamlConfiguration config, YamlConfiguration types) {
        range(config, "max-length", 2, 128); range(config, "sag", 0, 1); range(config, "bulb-spacing", .1, 16);
        range(config, "light-level", 0, 15); range(config, "brightness-on", 0, 15); range(config, "brightness-off", 0, 15);
        range(config, "animation-ticks", 1, 1200); range(config, "effect-steps", 1, 1200); range(config, "breathe-period", 4, 24000);
        range(config, "progressive-speed", 1, 256); range(config, "view-distance-chunks", .4, 64); range(config, "wall-grid", 0, 1);
        range(config, "head-center-y", -2, 2); range(config, "wire.thickness", .005, 1); range(config, "wire.precision", .002, 1);
        range(config, "limits.max-bulbs-per-segment", 1, 256); range(config, "limits.max-strings", 0, 4096);
        range(config, "limits.max-strings-per-player", 0, 1024); range(config, "limits.max-entities", 64, 100000);
        range(config, "updates-per-tick", 1, 16);
        range(config, "strobelights.update-ticks", 1, 20); range(config, "strobelights.max-sources-per-player", 1, 128);
        range(config, "strobelights.max-carriers-total", 1, 2048); range(config, "strobelights.view-distance-chunks", 0, 64);
        range(config, "strobelights.selection-hysteresis", 0, 32); range(config, "garlands.minimum-body-light", 0, 15);
        range(config, "strobelights.source-spacing", .5, 16);
        range(config, "strobelights.intensity-scale", 0, 1);
        range(config, "strobelights.surfaces.reach", .5, 16); range(config, "strobelights.surfaces.max-radius", .25, 8);
        range(config, "strobelights.surfaces.distance-fade", .5, 16); range(config, "strobelights.surfaces.refresh-ticks", 4, 200);
        range(config, "strobelights.surfaces.probes-per-update", 1, 64);
        range(config, "model-pack.embedded.port", 1, 65535);
        range(config, "garlands.sag", 0, 1);
        if (config.getDouble("model-pack.embedded.port") != Math.rint(config.getDouble("model-pack.embedded.port"))) throw new ConfigProblem("integer", "path", "model-pack.embedded.port");
        for (String key : List.of("strobelights.update-ticks", "strobelights.max-sources-per-player", "strobelights.max-carriers-total", "strobelights.surfaces.refresh-ticks", "strobelights.surfaces.probes-per-update")) {
            if (config.getDouble(key) != Math.rint(config.getDouble(key))) throw new ConfigProblem("integer", "path", key);
        }
        for (String key : List.of("max-length", "light-level", "brightness-on", "brightness-off", "animation-ticks", "effect-steps", "breathe-period", "progressive-speed", "limits.max-bulbs-per-segment", "limits.max-strings", "limits.max-strings-per-player", "limits.max-entities", "updates-per-tick")) {
            if (config.getDouble(key) != Math.rint(config.getDouble(key))) throw new ConfigProblem("integer", "path", key);
        }
        if (config.getDouble("garlands.minimum-body-light") != Math.rint(config.getDouble("garlands.minimum-body-light"))) throw new ConfigProblem("integer", "path", "garlands.minimum-body-light");
        for (String key : List.of("consume-item", "light-blocks", "animated-light", "progressive-switch", "night-only", "language.use-player-locale", "strobelights.enabled", "strobelights.allow-untested-version", "strobelights.surfaces.follow-effects", "model-pack.enabled", "model-pack.auto-send", "model-pack.trp-overlay", "model-pack.assume-installed", "model-pack.required", "model-pack.nexo.enabled", "model-pack.nexo.auto-rebuild", "model-pack.embedded.enabled")) {
            if (!config.isBoolean(key)) throw new ConfigProblem("boolean", "path", key);
        }
        range(config, "updates.check-interval-hours", 1, 168);
        for (String key : List.of("updates.enabled", "updates.notify-admins"))
            if (!config.isBoolean(key)) throw new ConfigProblem("boolean", "path", key);
        Bukkit.createBlockData(Objects.requireNonNull(config.getString("wire.block")));
        ModelPackService.validateUrl(config.getString("model-pack.url", ""));
        EmbeddedModelPack.validateBase(config.getString("model-pack.embedded.public-url", ""));
        if (!config.isString("model-pack.embedded.bind-address") || config.getString("model-pack.embedded.bind-address").isBlank()) throw new IllegalArgumentException("model-pack.embedded.bind-address");
        ConfigurationSection section = types.getConfigurationSection("types");
        if (section == null || section.getKeys(false).isEmpty()) throw new IllegalArgumentException("types: empty");
        for (String id : section.getKeys(false)) {
            if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("types." + id);
            ConfigurationSection type = Objects.requireNonNull(section.getConfigurationSection(id));
            GarlandModel model = GarlandModel.parse(type.getString("model"));
            if (type.contains("rgb")) {
                if (!type.isList("rgb")) throw new ConfigProblem("list", "path", "types." + id + ".rgb");
                if (type.getStringList("rgb").isEmpty() || type.getStringList("rgb").size() > 32) throw new IllegalArgumentException("types." + id + ".rgb");
                type.getStringList("rgb").forEach(LightColors::parse);
            }
            for (String key : List.of("blocks", "heads")) if (type.contains(key) && !type.isList(key)) throw new ConfigProblem("list", "path", "types." + id + "." + key);
            for (String key : List.of("custom", "wire")) if (type.contains(key) && !type.isBoolean(key)) throw new ConfigProblem("boolean", "path", "types." + id + "." + key);
            if (type.contains("size")) range(type, "size", .05, 4);
            if (type.contains("spacing")) range(type, "spacing", .1, 16);
            if (type.contains("chain")) range(type, "chain", 0, 4);
            if (type.contains("sag")) range(type, "sag", 0, 1);
            if (type.contains("light-level")) range(type, "light-level", 0, 15);
            if (!Set.of("hanging", "wall").contains(type.getString("style", "hanging"))) throw new IllegalArgumentException("types." + id + ".style");
            for (String data : type.getStringList("blocks")) Bukkit.createBlockData(data);
            for (String texture : type.getStringList("heads")) TextureHeads.textureUri(texture);
            if (model == null && type.getStringList("blocks").isEmpty() && type.getStringList("heads").isEmpty()) throw new IllegalArgumentException("types." + id + ": blocks/heads");
            Material item = Material.matchMaterial(type.getString("item", type.getStringList("heads").isEmpty() ? "STRING" : "PLAYER_HEAD"));
            if (item == null || !item.isItem() || item.isAir()) throw new IllegalArgumentException("types." + id + ".item");
        }
    }

    private static void range(ConfigurationSection config, String key, double min, double max) {
        Object value = config.get(key);
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() < min || number.doubleValue() > max) {
            throw new ConfigProblem("range", "path", key, "min", min, "max", max);
        }
    }

    public void loadConfig() throws Exception {
        var config = plugin.getConfig();
        Map<String, LightType> nextTypes = new LinkedHashMap<>();
        Map<String, ItemStack> headCache = new HashMap<>();
        var defaults = plugin.loadResource("types.yml");
        var section = Objects.requireNonNull(plugin.getTypesConfig().getConfigurationSection("types"));
        for (String id : section.getKeys(false)) {
            ConfigurationSection type = Objects.requireNonNull(section.getConfigurationSection(id));
            List<BlockData> blocks = new ArrayList<>();
            for (String block : type.getStringList("blocks")) blocks.add(Bukkit.createBlockData(block));
            List<ItemStack> heads = new ArrayList<>();
            for (String texture : type.getStringList("heads")) {
                if (!headCache.containsKey(texture)) headCache.put(texture, TextureHeads.create(texture));
                heads.add(headCache.get(texture));
            }
            String name = type.getString("name", id);
            String nameKey = type.getString("name-key");
            if (type.getBoolean("custom") || !Objects.equals(name, defaults.getString("types." + id + ".name", id))) nameKey = null;
            if (nameKey != null && !plugin.messages().hasKey(nameKey)) throw new IllegalArgumentException("types." + id + ".name-key: " + nameKey);
            Material item = Objects.requireNonNull(Material.matchMaterial(type.getString("item", heads.isEmpty() ? "STRING" : "PLAYER_HEAD")));
            boolean wall = "wall".equals(type.getString("style", "hanging"));
            nextTypes.put(id, new LightType(id, LegacyComponentSerializer.legacyAmpersand().deserialize(name), nameKey, item,
                    (float)type.getDouble("size", .2), List.copyOf(blocks), List.copyOf(heads), wall, type.getBoolean("wire", !wall),
                    type.getDouble("spacing", -1), type.getDouble("chain", 0), type.getInt("light-level", -1), signature(type), GarlandModel.parse(type.getString("model")), LightColors.palette(type, blocks, id), type.getDouble("sag", -1)));
        }
        // Build all types before replacing live settings, so invalid textures cannot partially clear the registry.
        types.clear(); types.putAll(nextTypes);
        maxLength = config.getInt("max-length"); sagFactor = config.getDouble("sag"); garlandSag = config.getDouble("garlands.sag"); bulbSpacing = config.getDouble("bulb-spacing");
        consumeItem = config.getBoolean("consume-item"); placeLightBlocks = config.getBoolean("light-blocks"); lightLevel = config.getInt("light-level");
        animatedLight = config.getBoolean("animated-light"); brightOn = config.getInt("brightness-on"); brightOff = config.getInt("brightness-off");
        animationTicks = config.getInt("animation-ticks"); effectSteps = config.getInt("effect-steps"); breathePeriod = config.getInt("breathe-period");
        surfaceEffects = config.getBoolean("strobelights.surfaces.follow-effects");
        progressive = config.getBoolean("progressive-switch"); progressiveSpeed = config.getInt("progressive-speed"); nightOnly = config.getBoolean("night-only");
        viewRange = (float)(config.getDouble("view-distance-chunks") / 4); wallGrid = config.getDouble("wall-grid"); headCenterY = config.getDouble("head-center-y");
        bodyLightFloor = config.getInt("garlands.minimum-body-light");
        wireBlock = Bukkit.createBlockData(Objects.requireNonNull(config.getString("wire.block")));
        Material chain = Material.matchMaterial("CHAIN");
        if (chain == null) chain = Material.matchMaterial("IRON_CHAIN");
        chainBlock = Objects.requireNonNull(chain, "CHAIN/IRON_CHAIN").createBlockData();
        wireThickness = (float)config.getDouble("wire.thickness"); wirePrecision = config.getDouble("wire.precision");
        maxBulbs = config.getInt("limits.max-bulbs-per-segment"); maxStrings = config.getInt("limits.max-strings");
        maxPerPlayer = config.getInt("limits.max-strings-per-player"); maxEntities = config.getInt("limits.max-entities"); updatesPerTick = config.getInt("updates-per-tick");
        globalSignature = "7|" + wireBlock.getAsString() + "|" + wireThickness + "|" + wirePrecision + "|" + headCenterY + "|" + placeLightBlocks + "|" + lightLevel + "|" + viewRange + "|" + maxBulbs + "|" + sagFactor + "|" + bulbSpacing + "|" + garlandSag + "|" + plugin.usesModelPack();
        modelItems.clear(); VanillaGarlands.clear(); realLightsDirty = true; coloredLights.configure(config);
        for (LightString string : strings.values()) { string.idle = false; string.rgbStride = 0; }
    }

    private static String signature(ConfigurationSection section) {
        StringBuilder text = new StringBuilder();
        for (String key : new TreeSet<>(section.getKeys(true))) {
            if (!section.isConfigurationSection(key) && !Set.of("name", "name-key", "item", "custom").contains(key)) text.append(key).append('=').append(section.get(key)).append(';');
        }
        return text.toString();
    }
    private String fullSignature(LightType type, LightString string) { return globalSignature + "#" + type.signature() + "|straight=" + string.straight
            + (type.garland() && plugin.usesModelPack() ? "|garland-facing=2" : ""); }
    public Collection<LightString> getStrings() { return Collections.unmodifiableCollection(strings.values()); }
    public Map<String, LightType> getTypes() { return Collections.unmodifiableMap(types); }
    public LightType getType(String id) { return id == null ? null : types.get(id.toLowerCase(Locale.ROOT)); }
    public int getMaxLength() { return maxLength; }
    public boolean isConsumeItem() { return consumeItem; }
    public double getWallGrid() { return wallGrid; }
    public Component typeName(CommandSender sender, LightType type) { return type.nameKey() == null ? type.name() : plugin.messages().text(sender, type.nameKey()); }

    public ItemStack createKit(LightType type, int amount, CommandSender recipient) {
        ItemStack item = type.isHead() && type.item() == Material.PLAYER_HEAD ? type.heads().getFirst().clone() : new ItemStack(type.item());
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        List<Component> lore = new ArrayList<>();
        for (String key : List.of(type.wall() ? "kit.wall" : "kit.hanging", "kit.points", "kit.extend", "kit.remove", "kit.finish")) lore.add(plugin.messages().text(recipient, key));
        item.editMeta(meta -> {
            meta.displayName(typeName(recipient, type).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore); meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyKit, PersistentDataType.STRING, type.id());
        });
        return item;
    }
    void refreshLegacyKits(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack previous = inventory.getItem(slot);
            if (previous == null || previous.getType() != Material.PLAYER_HEAD) continue;
            LightType type = getType(getKitType(previous));
            if (type != null && type.model() == GarlandModel.BULB && type.item() != Material.PLAYER_HEAD)
                inventory.setItem(slot, createKit(type, previous.getAmount(), player));
        }
    }

    public void giveKits(Player player, LightType type, int amount) {
        while (amount > 0) {
            ItemStack item = createKit(type, amount, player);
            amount -= item.getAmount();
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    public String getKitType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        String id = data.get(keyKit, PersistentDataType.STRING);
        return id != null ? id : LegacyMigration.translateTypeId(data.get(legacyKit, PersistentDataType.STRING));
    }

    public boolean canManage(CommandSender sender, LightString string) {
        return sender.hasPermission("xmaslights.manage-all") || (sender instanceof Player player && player.hasPermission("xmaslights.use") && player.getUniqueId().equals(string.owner));
    }
    LightString byId(UUID id) { return strings.get(id); }
    LightString byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(keyString, PersistentDataType.STRING);
        try { return id == null ? null : strings.get(UUID.fromString(id)); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    String importError(LightString string) { return spawnError(string, getType(string.type)); }
    int importCost(LightString string) { return Math.max(string.importBudget, estimatedEntities(string, getType(string.type))); }
    int importCount(LightString string) { return bulbCount(string, getType(string.type)); }
    String importLimitError(Collection<LightString> imported) {
        if (maxStrings > 0 && strings.size() + imported.size() > maxStrings) return "errors.limit-strings";
        long cost = geometryEntities();
        Map<UUID, Long> owners = new HashMap<>();
        for (LightString string : strings.values()) owners.merge(string.owner, 1L, Long::sum);
        for (LightString string : imported) {
            cost += importCost(string);
            if (maxPerPlayer > 0 && owners.merge(string.owner, 1L, Long::sum) > maxPerPlayer) return "errors.limit-player-strings";
        }
        return cost > maxEntities ? "errors.limit-entities" : null;
    }
    void insertImported(LightString string) {
        String error = importLimitError(List.of(string));
        if (error != null) throw new OperationFailure(error);
        coloredLights.limitBudget(Math.max(0, maxEntities - geometryEntities() - importCost(string)));
        strings.put(string.id, string);
        if (string.group != null) string.group = canonicalGroup(string.group);
    }
    void adoptImported(Display entity, LightString string) {
        entity.getPersistentDataContainer().set(keyString, PersistentDataType.STRING, string.id.toString());
        entity.getPersistentDataContainer().set(keyGeneration, PersistentDataType.INTEGER, string.generation);
        plugin.decoSwap().stamp(entity, string);
        register(entity); realLightsDirty = true;
    }
    void finishImported(LightString string) { string.importBudget = 0; string.signature = fullSignature(getType(string.type), string); realLightsDirty = true; }
    Material originalLight(Block block) { return realLights.original(block); }
    public static boolean validGroup(String name) { return name != null && GROUP_NAME.matcher(name).matches() && !Set.of("all", "none").contains(name.toLowerCase(Locale.ROOT)); }

    public LightString create(Player player, Vector a, Vector b, BlockVector anchorA, BlockVector anchorB, LightType type,
                              Vector normal, UUID chain, boolean skipStart, String group) {
        if (maxStrings > 0 && strings.size() >= maxStrings) throw new OperationFailure("errors.limit-strings");
        if (maxPerPlayer > 0 && strings.values().stream().filter(s -> player.getUniqueId().equals(s.owner)).count() >= maxPerPlayer) throw new OperationFailure("errors.limit-player-strings");
        double distance = a.distance(b);
        if (!Double.isFinite(distance) || distance < .5 || distance > maxLength) throw new OperationFailure("errors.distance");
        double sag = sagFor(type, a, b);
        LightString string = new LightString(UUID.randomUUID(), player.getWorld().getName(), a.clone(), b.clone(), type.wall() ? normal.clone() : new Vector(), anchorA.clone(), anchorB.clone(), sag, type.id(), AnimationMode.STEADY);
        string.owner = player.getUniqueId();
        string.refundable = consumeItem && player.getGameMode() != GameMode.CREATIVE;
        if (chain != null) {
            LightString previous = getChain(chain).stream().filter(s -> canManage(player, s)).findFirst().orElse(null);
            if (previous != null && player.getUniqueId().equals(previous.owner)) {
                string.chain = chain; string.group = previous.group; string.mode = previous.mode; string.on = previous.on; string.straight = previous.straight;
            }
        }
        if (string.straight) string.sag = 0;
        if (group != null) string.group = canonicalGroup(group);
        string.skipStart = skipStart;
        String error = spawnError(string, type);
        if (error != null) throw new OperationFailure(error);
        strings.put(string.id, string);
        try { spawnGeometry(player.getWorld(), string, type); }
        catch (RuntimeException failure) { strings.remove(string.id); clearFromWorld(string); throw failure; }
        if (!saveData()) { strings.remove(string.id); clearFromWorld(string); throw new OperationFailure("errors.save"); }
        return string;
    }

    private int bulbCount(LightString string, LightType type) {
        double spacing = type.spacing() > 0 ? type.spacing() : bulbSpacing;
        if (type.garland()) return Math.min(maxBulbs, Math.max(1, (int)Math.ceil(LightGeometry.curveLength(string) / spacing)));
        double length = LightGeometry.curveLength(string);
        return Math.min(maxBulbs, type.wall() ? Math.max(2, (int)Math.round(length / spacing) + 1) : Math.max(1, (int)Math.floor(length / spacing)));
    }
    private double sagFor(LightType type, Vector a, Vector b) {
        return Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ()) * (type.sag() >= 0 ? type.sag() : type.garland() ? garlandSag : sagFactor);
    }
    int geometryEntities() {
        int total = 0;
        for (LightString string : strings.values()) if (string.spawned && getType(string.type) != null)
            total += Math.max(importCost(string), string.wires.size() + string.bulbs.size());
        return total;
    }
    void enforceEntityBudget() {
        int used = 0, hidden = 0;
        for (LightString string : strings.values()) {
            LightType type = getType(string.type);
            if (!string.spawned || type == null) continue;
            int cost = Math.max(estimatedEntities(string, type), string.wires.size() + string.bulbs.size());
            if (used + cost > maxEntities) { despawn(string); hidden++; } else used += cost;
        }
        coloredLights.limitBudget(Math.max(0, maxEntities - used));
        if (hidden > 0) { plugin.messages().warn("log.entity-budget", "count", hidden, "limit", maxEntities); saveData(); }
    }
    private int estimatedEntities(LightString string, LightType type) {
        int bulbs = bulbCount(string, type);
        if (type.garland()) return bulbs * 2;
        if (type.wall() && string.skipStart) bulbs--;
        return bulbs * (type.chain() > 0 ? 2 : 1) + (type.wire() ? LightGeometry.wireSegments(string.sag, wirePrecision) : 0);
    }
    private String spawnError(LightString string, LightType type) {
        World world = Bukkit.getWorld(string.world);
        if (world == null) return "errors.world-unloaded";
        if (type == null) return "errors.type-missing";
        if (string.length() > 256 || !Double.isFinite(string.length()) || string.length() < .01) return "errors.distance";
        double minY = Math.min(string.a.getY(), string.b.getY()) - string.sag - type.chain() - type.size();
        if (minY < world.getMinHeight() || Math.max(string.a.getY(), string.b.getY()) + type.size() >= world.getMaxHeight()) return "errors.height";
        int minX = ((int)Math.floor(Math.min(string.a.getX(), string.b.getX()) - 3)) >> 4;
        int maxX = ((int)Math.floor(Math.max(string.a.getX(), string.b.getX()) + 3)) >> 4;
        int minZ = ((int)Math.floor(Math.min(string.a.getZ(), string.b.getZ()) - 3)) >> 4;
        int maxZ = ((int)Math.floor(Math.max(string.a.getZ(), string.b.getZ()) + 3)) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!world.isChunkLoaded(x, z)) return "errors.chunks-unloaded";
        long total = estimatedEntities(string, type);
        for (LightString other : strings.values()) if (other != string && other.spawned && getType(other.type) != null)
            total += Math.max(importCost(other), other.wires.size() + other.bulbs.size());
        return total > maxEntities ? "errors.limit-entities" : null;
    }

    private void spawnGeometry(World world, LightString string, LightType type) {
        coloredLights.limitBudget(Math.max(0, maxEntities - geometryEntities()));
        string.signature = fullSignature(type, string); string.idle = false;
        if (type.wire() && !type.garland()) {
            int segments = LightGeometry.wireSegments(string.sag, wirePrecision);
            Vector offset = type.wall() ? string.normal.clone().multiply(type.size() * .5 + .01) : new Vector();
            for (int i = 1; i <= segments; i++) spawnWire(world, string, i - 1, string.point((i - 1.0) / segments).add(offset), string.point((double)i / segments).add(offset));
        }
        string.count = bulbCount(string, type);
        string.rgbStride = 0;
        boolean nativeLight = usesNativeLight();
        for (int i = type.wall() && !type.garland() && string.skipStart ? 1 : 0; i < string.count; i++) {
            Vector point = LightGeometry.bulbPoint(string, type, i);
            if (!type.garland() && type.chain() > 0) spawnChain(world, string, type, i, point);
            spawnBulb(world, string, type, i, point);
            Vector light = LightGeometry.lightPoint(string, type, i);
            BlockVector block = light.toBlockVector();
            if (nativeLight && realLights.claim(world, block)) {
                if (!string.lightBlocks.contains(block)) string.lightBlocks.add(block);
                string.bulbLights.put(i, block);
            }
        }
        realLightsDirty = true;
        if (plugin.decoSwap() != null) plugin.decoSwap().geometryChanged(string);
    }

    private void spawnWire(World world, LightString string, int part, Vector a, Vector b) {
        Vector direction = b.clone().subtract(a);
        float length = (float)direction.length();
        if (length < .0001) return;
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.toVector3f().normalize());
        Vector3f translation = rotation.transform(new Vector3f(-wireThickness / 2, -wireThickness / 2, 0));
        Transformation transform = new Transformation(translation, rotation, new Vector3f(wireThickness, wireThickness, length + wireThickness / 2), new Quaternionf());
        spawnDisplay(world, string, "wire:" + part, a.toLocation(world), BlockDisplay.class, display -> { display.setBlock(wireBlock); setupDisplay(display, string, -1, transform); });
    }
    private void spawnChain(World world, LightString string, LightType type, int part, Vector point) {
        float size = type.size(), chain = (float)type.chain();
        boolean lantern = type.bulbs().stream().anyMatch(data -> data instanceof Lantern);
        float width = lantern ? size : Math.min(.025f, wireThickness);
        Transformation transform = new Transformation(new Vector3f(-width / 2, -chain, -width / 2), new Quaternionf(), new Vector3f(width, chain + .004f, width), new Quaternionf());
        spawnDisplay(world, string, "drop:" + part, point.toLocation(world), BlockDisplay.class, display -> { display.setBlock(lantern ? chainBlock : wireBlock); setupDisplay(display, string, -1, transform); });
    }
    private void spawnBulb(World world, LightString string, LightType type, int index, Vector point) {
        float size = type.size();
        if (type.garland()) {
            var module = LightGeometry.module(string, type, index);
            if (!plugin.usesModelPack()) {
                var body = new Transformation(new Vector3f(), module.rotation(), new Vector3f(module.length() + .03f, .37f * size, .37f * size), new Quaternionf());
                body.getTranslation().set(module.rotation().transform(new Vector3f(-body.getScale().x / 2, -.185f * size, -.185f * size)));
                spawnDisplay(world, string, "body:" + index, module.center().toLocation(world), BlockDisplay.class, display -> {
                    display.setBlock(VanillaGarlands.foliage(type.model())); display.getPersistentDataContainer().set(keyModule, PersistentDataType.INTEGER, index); setupDisplay(display, string, -1, body);
                    refreshGarlandBodyLight(display, string);
                });
                var local = LightGeometry.vanillaLedOffset(index, size).sub(new Vector3f(.045f * size));
                var led = new Transformation(module.rotation().transform(local), module.rotation(), new Vector3f(.09f * size), new Quaternionf());
                spawnDisplay(world, string, "bulb:" + index, module.center().toLocation(world), BlockDisplay.class, display -> {
                    display.setBlock(VanillaGarlands.light(type.color(index))); setupDisplay(display, string, index, led);
                });
                string.shownVariant.put(index, Math.floorMod(index, type.variants()));
                return;
            }
            // Minecraft's ItemDisplayRenderer adds Y=180 even for transform NONE.
            // Cancel it before scale so backing probes and visible model sides agree.
            var transform = new Transformation(new Vector3f(), module.rotation(), new Vector3f(module.length(), size, size),
                    new Quaternionf().rotateY((float)Math.PI));
            int covered = GarlandBacking.hiddenSide(world, module, size);
            spawnDisplay(world, string, "body:" + index, module.center().toLocation(world), ItemDisplay.class, display -> {
                display.setItemStack(modelItems.computeIfAbsent(type.model().name() + ":body:" + covered, ignored -> type.model().body(covered)));
                display.getPersistentDataContainer().set(keyModel, PersistentDataType.BYTE, (byte)1);
                display.getPersistentDataContainer().set(keyModule, PersistentDataType.INTEGER, index);
                display.getPersistentDataContainer().set(keyCovered, PersistentDataType.BYTE, (byte)covered);
                string.garlandBodies.put(index, display);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                setupDisplay(display, string, -1, transform);
                refreshGarlandBodyLight(display, string);
            });
            spawnDisplay(world, string, "bulb:" + index, module.center().toLocation(world), ItemDisplay.class, display -> {
                display.setItemStack(modelLight(type, index, covered)); display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.getPersistentDataContainer().set(keyModel, PersistentDataType.BYTE, (byte)1);
                display.getPersistentDataContainer().set(keyCovered, PersistentDataType.BYTE, (byte)covered);
                setupDisplay(display, string, index, transform);
            });
        } else if (type.modelLamp() && plugin.usesModelPack()) {
            float vertical = (float)(LightGeometry.bulbCenter(string, type, index).getY() - point.getY());
            var transform = new Transformation(new Vector3f(0, vertical, 0), new Quaternionf(), new Vector3f(size), new Quaternionf());
            spawnDisplay(world, string, "bulb:" + index, point.toLocation(world), ItemDisplay.class, display -> {
                display.setItemStack(modelLight(type, index)); display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.getPersistentDataContainer().set(keyModel, PersistentDataType.BYTE, (byte)1);
                setupDisplay(display, string, index, transform);
            });
        } else if (type.isHead()) {
            float vertical = (float)(LightGeometry.bulbCenter(string, type, index).getY() - point.getY());
            Transformation transform = new Transformation(new Vector3f(0, vertical + (float)(size * headCenterY), 0), new Quaternionf(), new Vector3f(size * 2), new Quaternionf());
            Location location = point.toLocation(world); location.setYaw(headYaw(string));
            spawnDisplay(world, string, "bulb:" + index, location, ItemDisplay.class, display -> {
                display.setItemStack(type.heads().get(Math.floorMod(index, type.heads().size())));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                setupDisplay(display, string, index, transform);
            });
        } else {
            float vertical = (float)(LightGeometry.bulbCenter(string, type, index).getY() - point.getY());
            Vector3f translation = new Vector3f(-size / 2, vertical - size / 2, -size / 2);
            Transformation transform = new Transformation(translation, new Quaternionf(), new Vector3f(size), new Quaternionf());
            spawnDisplay(world, string, "bulb:" + index, point.toLocation(world), BlockDisplay.class, display -> { display.setBlock(bulbData(type, string, index)); setupDisplay(display, string, index, transform); });
        }
        string.shownVariant.put(index, Math.floorMod(index, type.variants()));
    }
    /** The world contains a disposable view of lights.yml, never the source of truth. */
    private <T extends Display> void spawnDisplay(World world, LightString string, String part, Location location,
                                                 Class<T> type, java.util.function.Consumer<T> setup) {
        if (closing || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        if (part.startsWith("bulb:")) {
            Display existing = string.bulbs.get(Integer.parseInt(part.substring(5)));
            if (existing != null && existing.isValid()) return;
        } else for (Entity existing : string.wires)
            if (existing.isValid() && part.equals(existing.getPersistentDataContainer().get(keyPart, PersistentDataType.STRING))) return;
        world.spawn(location, type, display -> {
            display.getPersistentDataContainer().set(keyPart, PersistentDataType.STRING, part);
            setup.accept(display);
        });
    }
    private ItemStack modelLight(LightType type, int index) {
        return modelLight(type, index, 0);
    }
    private ItemStack modelLight(LightType type, int index, int covered) {
        int rgb = type.color(index);
        int phase = Math.floorMod(index, type.colors().size());
        return modelItems.computeIfAbsent(type.id() + ":" + covered + ":" + phase, ignored -> type.modelLamp() ? type.model().bulb(rgb, type.wall() && type.chain() == 0) : type.model().lights(type.colors(), phase, covered));
    }
    private void setupDisplay(Display display, LightString string, int index, Transformation transform) {
        display.setTransformation(transform); display.setViewRange(viewRange); display.setPersistent(false);
        if (display.getPersistentDataContainer().has(keyModel)) {
            display.setVisibleByDefault(false);
            for (Player player : Bukkit.getOnlinePlayers()) updateModelVisibility(player, display);
        }
        display.getPersistentDataContainer().set(keyString, PersistentDataType.STRING, string.id.toString());
        display.getPersistentDataContainer().set(keyIndex, PersistentDataType.INTEGER, index);
        display.getPersistentDataContainer().set(keyGeneration, PersistentDataType.INTEGER, string.generation);
        if (index >= 0) display.getPersistentDataContainer().set(keyPart, PersistentDataType.STRING, "bulb:" + index);
        else if (display.getPersistentDataContainer().has(keyModule, PersistentDataType.INTEGER)) display.getPersistentDataContainer().set(keyPart, PersistentDataType.STRING, "body:" + display.getPersistentDataContainer().get(keyModule, PersistentDataType.INTEGER));
        if (plugin.decoSwap() != null) plugin.decoSwap().stamp(display, string);
        if (index < 0) string.wires.add(display);
        else {
            int brightness = shownOn(string, index) ? brightOn : brightOff;
            display.setBrightness(new Display.Brightness(brightness, brightness));
            string.bulbs.put(index, display);
        }
    }
    private static float headYaw(LightString string) {
        double x = string.normal.getX(), z = string.normal.getZ();
        if (string.normal.lengthSquared() < 1e-6 || Math.abs(string.normal.getY()) >= .5) { x = -(string.b.getZ() - string.a.getZ()); z = string.b.getX() - string.a.getX(); }
        return Math.abs(x) + Math.abs(z) < 1e-6 ? 0 : (float)Math.toDegrees(Math.atan2(-x, z));
    }
    private BlockData bulbData(LightType type, LightString string, int index) {
        if (type.bulbs().isEmpty() && type.modelLamp()) return VanillaGarlands.light(type.color(index));
        BlockData block = type.bulbs().get(Math.floorMod(index, type.bulbs().size()));
        if (type.wall() && block instanceof Lantern original) {
            Lantern lantern = (Lantern)original.clone(); lantern.setHanging(type.chain() > 0 || string.normal.getY() < -.5); return lantern;
        }
        return block;
    }

    public boolean remove(LightString string) { return removeAll(List.of(string)); }
    boolean removeAll(Collection<LightString> selected) {
        for (LightString string : selected) strings.remove(string.id);
        if (!saveData()) { for (LightString string : selected) strings.put(string.id, string); return false; }
        selected.forEach(this::clearFromWorld);
        return true;
    }
    private void clearFromWorld(LightString string) {
        coloredLights.forgetSources(Set.of(string.id));
        string.bulbs.values().forEach(Entity::remove); string.wires.forEach(Entity::remove);
        string.bulbs.clear(); string.wires.clear(); string.garlandBodies.clear(); string.shownVariant.clear(); string.lightBlocks.clear(); string.bulbLights.clear();
        realLightsDirty = true;
        // Unloaded entities are removed by their generation/ID when their chunk is loaded.
        refreshRealLights();
        if (plugin.decoSwap() != null) plugin.decoSwap().geometryChanged(string);
    }
    public boolean despawn(LightString string) {
        if (!string.spawned) return false;
        string.spawned = false; string.generation++; clearFromWorld(string); return true;
    }
    public String spawn(LightString string) {
        if (string.spawned) return null;
        LightType type = getType(string.type);
        String error = spawnError(string, type); if (error != null) return error;
        string.spawned = true; string.switchedAt = string.on ? step : step - string.count - 4;
        try { spawnGeometry(Objects.requireNonNull(Bukkit.getWorld(string.world)), string, type); return null; }
        catch (RuntimeException failure) { despawn(string); plugin.messages().warn("log.render-failed", "id", string.id, "error", failure.getMessage()); return "errors.render"; }
    }
    public String regenerate(LightString string) {
        if (!string.spawned) return null;
        LightType type = getType(string.type);
        double previousSag = string.sag;
        if (type != null) string.sag = string.straight ? 0 : sagFor(type, string.a, string.b);
        String error = spawnError(string, type);
        if (error != null) { string.sag = previousSag; return error; }
        despawn(string); return spawn(string);
    }
    public String setStraight(LightString string, boolean straight) {
        LightType type = getType(string.type);
        if (type == null) return "errors.type-missing";
        boolean previous = string.straight, visible = string.spawned;
        double sag = string.sag;
        string.straight = straight;
        string.sag = straight ? 0 : sagFor(type, string.a, string.b);
        String error = visible ? regenerate(string) : null;
        if (error != null) {
            string.straight = previous; string.sag = sag;
            if (visible && !string.spawned) spawn(string);
        }
        return error;
    }
    public void scheduleAutoUpdate() {
        if (closing) return;
        enforceEntityBudget();
        updateQueue.clear();
        for (LightString string : strings.values()) {
            string.wires.removeIf(entity -> !entity.isValid());
            string.bulbs.values().removeIf(entity -> !entity.isValid());
            string.garlandBodies.values().removeIf(entity -> !entity.isValid());
            LightType type = getType(string.type);
            if (string.spawned && type != null && (!fullSignature(type, string).equals(string.signature)
                    || string.wires.size() + string.bulbs.size() < estimatedEntities(string, type))) updateQueue.add(string.id);
        }
        if (updateQueue.isEmpty() || updateTask != null) return;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (int i = 0; i < updatesPerTick && !updateQueue.isEmpty(); i++) {
                LightString string = strings.get(updateQueue.poll());
                if (string != null && string.spawned) {
                    LightType type = getType(string.type); World world = Bukkit.getWorld(string.world);
                    if (type == null || world == null) continue;
                    if (!fullSignature(type, string).equals(string.signature)) regenerate(string);
                    else spawnGeometry(world, string, type); // Recreate only missing parts in loaded chunks.
                }
            }
            if (updateQueue.isEmpty()) { saveData(); updateTask.cancel(); updateTask = null; }
        }, 1, 1);
    }
    public void onChunkLoad() {
        if (closing) return;
        realLightsDirty = true; refreshRealLights(); scheduleAutoUpdate();
    }
    public void onWorldUnload(World world) {
        retireLegacyGeometry(world);
        realLights.releaseWorld(world);
    }
    public int removeChain(UUID chain) {
        List<LightString> removed = getChain(chain); return removeAll(removed) ? removed.size() : 0;
    }
    public void refund(Player player, Collection<LightString> removed) {
        if (!consumeItem || player.getGameMode() == GameMode.CREATIVE) return;
        for (LightString string : removed) if (string.refundable && getType(string.type) != null) {
            string.refundable = false; giveKits(player, getType(string.type), 1);
        }
    }
    public void setOn(LightString string, boolean on) {
        if (string.on != on) {
            string.on = on; string.switchedAt = step; string.idle = false; realLightsDirty = true;
            if (!on) coloredLights.forgetSources(Set.of(string.id));
        }
    }
    public void setMode(LightString string, AnimationMode mode) {
        if (string.mode == mode) return;
        string.mode = mode;
        if (string.on) { string.idle = false; realLightsDirty = true; }
    }
    public String canonicalGroup(String name) {
        if (!validGroup(name)) throw new IllegalArgumentException("group: " + name);
        for (String existing : groups) if (existing.equalsIgnoreCase(name)) return existing;
        groups.add(name); return name;
    }
    public boolean groupExists(String name) { return name != null && groups.contains(name); }
    public boolean createGroup(String name) { if (groupExists(name)) return false; canonicalGroup(name); saveData(); return true; }
    public int deleteGroup(String name) {
        List<LightString> removed = getGroup(name);
        String original = groups.stream().filter(group -> group.equalsIgnoreCase(name)).findFirst().orElse(name);
        groups.remove(name);
        if (!removeAll(removed)) { groups.add(original); return -1; }
        editing.values().removeIf(value -> value.equalsIgnoreCase(name)); return removed.size();
    }
    public boolean renameGroup(String oldName, String newName) {
        if (!groupExists(oldName) || (groupExists(newName) && !oldName.equalsIgnoreCase(newName)) || !validGroup(newName)) return false;
        for (LightString string : strings.values()) if (oldName.equalsIgnoreCase(string.group)) string.group = newName;
        groups.remove(oldName); groups.add(newName); editing.replaceAll((id, value) -> value.equalsIgnoreCase(oldName) ? newName : value); saveData(); return true;
    }
    public boolean isChaining(UUID player) { return !noChaining.contains(player); }
    public boolean toggleChaining(UUID player) { if (noChaining.remove(player)) return true; noChaining.add(player); return false; }
    public String getEditing(UUID player) { return editing.get(player); }
    public void setEditing(UUID player, String group) { if (group == null) editing.remove(player); else editing.put(player, canonicalGroup(group)); }
    public void forgetPlayer(UUID player) { editing.remove(player); noChaining.remove(player); coloredLights.forget(player); }
    public void closeIntegrations() { coloredLights.close(); }
    public void shutdown() {
        if (closing) return;
        closing = true; stopAnimation();
        if (!dataReady) { closeIntegrations(); return; }
        try { closeIntegrations(); }
        finally {
            try {
                // Old versions wrote displays into chunk files. Visit only their recorded bounds once.
                for (World world : Bukkit.getWorlds()) retireLegacyGeometry(world);
                realLights.releaseAll();
            } finally {
                for (World world : Bukkit.getWorlds()) for (Display display : world.getEntitiesByClass(Display.class))
                    if (ownedDisplay(display)) display.remove();
                for (LightString string : strings.values()) {
                    string.wires.clear(); string.bulbs.clear(); string.garlandBodies.clear(); string.shownVariant.clear();
                }
                saveData(); // Keep logical visibility, groups, owners and effects for the next startup.
            }
        }
    }
    private boolean ownedDisplay(Entity entity) {
        var data = entity.getPersistentDataContainer();
        return data.has(keyString) || data.has(legacyString) || data.has(new NamespacedKey(plugin, "rgb_carrier"));
    }
    private void retireLegacyGeometry(World world) {
        for (BackingChunk pos : List.copyOf(legacyGeometryChunks)) if (pos.world.equals(world.getName())) {
            for (Entity entity : world.getChunkAt(pos.x, pos.z).getEntities())
                if (entity instanceof Display && ownedDisplay(entity)) entity.remove();
            legacyGeometryChunks.remove(pos);
        }
    }
    public List<LightString> getGroup(String group) { return strings.values().stream().filter(s -> group.equalsIgnoreCase(s.group)).toList(); }
    public List<LightString> getChain(UUID chain) { return strings.values().stream().filter(s -> chain.equals(s.chain)).toList(); }
    public Set<String> getGroupNames() { return Collections.unmodifiableSet(groups); }

    public void scanLoadedEntities() { for (World world : Bukkit.getWorlds()) for (Display display : world.getEntitiesByClass(Display.class)) register(display); }
    public void register(Entity entity) {
        if (closing || !(entity instanceof Display display)) return;
        var data = entity.getPersistentDataContainer();
        String id = data.get(keyString, PersistentDataType.STRING);
        if (id == null && !data.has(legacyString, PersistentDataType.STRING)) return;
        display.setPersistent(false); // Also convert displays saved by previous versions.
        if (data.has(keyModel)) { entity.setVisibleByDefault(false); for (Player player : Bukkit.getOnlinePlayers()) updateModelVisibility(player, entity); }
        LightString string = null;
        try { if (id != null) string = strings.get(UUID.fromString(id)); } catch (IllegalArgumentException ignored) { }
        int generation = data.getOrDefault(keyGeneration, PersistentDataType.INTEGER, 0);
        if (string == null || !string.spawned || generation != string.generation || !entity.getWorld().getName().equals(string.world)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // An external serializer may adopt a newly loaded clone before this task runs.
                LightString current = byEntity(entity);
                if (entity.isValid() && (current == null || !current.spawned || data.getOrDefault(keyGeneration, PersistentDataType.INTEGER, 0) != current.generation || !entity.getWorld().getName().equals(current.world))) entity.remove();
            }); return;
        }
        int index = data.getOrDefault(keyIndex, PersistentDataType.INTEGER, -1);
        String part = data.get(keyPart, PersistentDataType.STRING);
        if (index >= 0) {
            Display existing = string.bulbs.get(index);
            if (existing != null && existing.isValid() && !existing.getUniqueId().equals(entity.getUniqueId())) { entity.remove(); return; }
        } else if (part != null) for (Entity existing : string.wires)
            if (existing.isValid() && !existing.getUniqueId().equals(entity.getUniqueId())
                    && part.equals(existing.getPersistentDataContainer().get(keyPart, PersistentDataType.STRING))) { entity.remove(); return; }
        if (part == null) string.signature = null; // Upgrade geometry that predates stable part roles.
        if (plugin.decoSwap() != null) plugin.decoSwap().loaded(entity, string);
        if (index < 0) {
            string.wires.add(entity);
            Integer module = data.get(keyModule, PersistentDataType.INTEGER);
            if (module != null && entity instanceof ItemDisplay body) { string.garlandBodies.put(module, body); backingQueue.add(string.id); }
        }
        else if (index < string.count) { string.bulbs.put(index, display); string.shownVariant.remove(index); string.idle = false; }
    }
    public void unregister(Entity entity) {
        String id = entity.getPersistentDataContainer().get(keyString, PersistentDataType.STRING);
        if (id == null) return;
        try {
            LightString string = strings.get(UUID.fromString(id));
            if (string != null) { string.wires.remove(entity); string.bulbs.values().removeIf(display -> display.getUniqueId().equals(entity.getUniqueId())); string.garlandBodies.values().removeIf(display -> display.getUniqueId().equals(entity.getUniqueId())); }
        } catch (IllegalArgumentException ignored) { }
    }
    private void updateModelVisibility(Player player, Entity entity) {
        boolean visible = plugin.modelPackReady(player);
        if (visible && !player.canSee(entity)) player.showEntity(plugin, entity);
        else if (!visible && player.canSee(entity)) player.hideEntity(plugin, entity);
    }
    void refreshModelVisibility(Player player) {
        for (LightString string : strings.values()) {
            for (Entity entity : string.wires) if (entity.getPersistentDataContainer().has(keyModel)) updateModelVisibility(player, entity);
            for (Entity entity : string.bulbs.values()) if (entity.getPersistentDataContainer().has(keyModel)) updateModelVisibility(player, entity);
        }
    }
    void backingChanged(Block block) {
        if (!plugin.usesModelPack()) return;
        String world = block.getWorld().getName();
        if (dirtyBackings.size() >= 256) dirtyBackingWorlds.add(world);
        else dirtyBackings.add(new BackingChunk(world, block.getX() >> 4, block.getZ() >> 4));
    }
    private void refreshBackings() {
        if (!plugin.usesModelPack()) { dirtyBackings.clear(); dirtyBackingWorlds.clear(); backingQueue.clear(); return; }
        if (!dirtyBackings.isEmpty() || !dirtyBackingWorlds.isEmpty()) {
            for (LightString string : strings.values()) {
                LightType type = getType(string.type);
                if (!string.spawned || type == null || !type.garland()) continue;
                int minX = (int)Math.floor(Math.min(string.a.getX(), string.b.getX()) - 3) >> 4;
                int maxX = (int)Math.floor(Math.max(string.a.getX(), string.b.getX()) + 3) >> 4;
                int minZ = (int)Math.floor(Math.min(string.a.getZ(), string.b.getZ()) - 3) >> 4;
                int maxZ = (int)Math.floor(Math.max(string.a.getZ(), string.b.getZ()) + 3) >> 4;
                if (dirtyBackingWorlds.contains(string.world) || dirtyBackings.stream().anyMatch(chunk -> chunk.world.equals(string.world)
                        && chunk.x >= minX && chunk.x <= maxX && chunk.z >= minZ && chunk.z <= maxZ)) backingQueue.add(string.id);
            }
            dirtyBackings.clear(); dirtyBackingWorlds.clear();
        }
        for (int updated = 0; updated < updatesPerTick && !backingQueue.isEmpty(); updated++) {
            var next = backingQueue.iterator(); UUID id = next.next(); next.remove();
            LightString string = strings.get(id);
            if (string == null || !string.spawned) continue;
            LightType type = getType(string.type); World world = Bukkit.getWorld(string.world);
            if (type == null || !type.garland() || world == null) continue;
            for (var entry : string.garlandBodies.entrySet()) {
                int index = entry.getKey(); ItemDisplay body = entry.getValue();
                if (!body.isValid() || !(string.bulbs.get(index) instanceof ItemDisplay led) || !led.isValid()) continue;
                int covered = GarlandBacking.hiddenSide(world, LightGeometry.module(string, type, index), type.size());
                if (covered == body.getPersistentDataContainer().getOrDefault(keyCovered, PersistentDataType.BYTE, (byte)0)) continue;
                body.setItemStack(modelItems.computeIfAbsent(type.model().name() + ":body:" + covered, ignored -> type.model().body(covered)));
                led.setItemStack(modelLight(type, string.shownVariant.getOrDefault(index, index), covered));
                body.getPersistentDataContainer().set(keyCovered, PersistentDataType.BYTE, (byte)covered);
                led.getPersistentDataContainer().set(keyCovered, PersistentDataType.BYTE, (byte)covered);
            }
        }
    }
    public LightString findLookedAt(Player player, double range) {
        Vector origin = player.getEyeLocation().toVector(), direction = player.getEyeLocation().getDirection().normalize();
        LightString selected = null; double closest = .75;
        for (LightString string : strings.values()) {
            if (!string.spawned || !string.world.equals(player.getWorld().getName())) continue;
            Vector middle = string.a.clone().add(string.b).multiply(.5);
            if (middle.distance(origin) > string.length() / 2 + range + string.sag) continue;
            int samples = Math.min(1024, Math.max(8, (int)Math.ceil(string.length() * 4)));
            for (int i = 0; i <= samples; i++) {
                Vector delta = string.point((double)i / samples).subtract(origin);
                double along = delta.dot(direction);
                if (along < 0 || along > range) continue;
                double distance = delta.subtract(direction.clone().multiply(along)).length();
                if (distance < closest) { closest = distance; selected = string; }
            }
        }
        return selected;
    }
    public List<LightString> findByAnchor(Block block) { return strings.values().stream().filter(s -> s.isAnchoredTo(block)).toList(); }

    public void startAnimation() { stopAnimation(); animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, animationTicks, animationTicks); }
    public void stopAnimation() {
        if (animationTask != null) { animationTask.cancel(); animationTask = null; }
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    }
    private void tick() {
        step++;
        refreshBackings();
        if (step % Math.max(1, 20 / Math.max(1, animationTicks)) == 0) {
            for (LightString string : strings.values()) {
                LightType type = getType(string.type);
                if (string.spawned && type != null && type.garland()) for (Entity body : string.wires)
                    if (body instanceof Display display && display.isValid()) refreshGarlandBodyLight(display, string);
            }
        }
        for (LightString string : strings.values()) {
            if (string.idle && !string.on) continue;
            LightType type = getType(string.type); World world = Bukkit.getWorld(string.world);
            if (!string.spawned || type == null || world == null || string.bulbs.isEmpty()) continue;
            boolean forcedOff = nightOnly && isDay(world);
            if (forcedOff != string.lastForcedOff) realLightsDirty = true;
            if (string.idle && forcedOff == string.lastForcedOff) continue;
            string.lastForcedOff = forcedOff;
            boolean active = string.on && !forcedOff;
            if (active && ((animatedLight && string.mode != AnimationMode.STEADY) || (progressive && (step - string.switchedAt) * progressiveSpeed <= string.count + 2L))) realLightsDirty = true;
            var iterator = string.bulbs.entrySet().iterator();
            while (iterator.hasNext()) {
                var bulb = iterator.next(); Display display = bulb.getValue(); int index = bulb.getKey();
                if (!display.isValid()) { iterator.remove(); continue; }
                double intensity = !shownOn(string, index) || forcedOff ? 0 : modeIntensity(string, index, type.variants());
                int brightness = brightOff + (int)Math.round((brightOn - brightOff) * intensity);
                var current = display.getBrightness();
                if (current == null || current.getBlockLight() != brightness || current.getSkyLight() != brightness) display.setBrightness(new Display.Brightness(brightness, brightness));
                if (!active || !shownOn(string, index)) continue; // Keep the last palette while OFF, including pending switch-on bulbs.
                int variant = Math.floorMod(index + (string.mode == AnimationMode.CHASE ? (int)(step / effectSteps) : 0), type.variants());
                if (!Objects.equals(string.shownVariant.get(index), variant)) {
                    if (type.model() != null && display instanceof ItemDisplay item) item.setItemStack(modelLight(type, variant, item.getPersistentDataContainer().getOrDefault(keyCovered, PersistentDataType.BYTE, (byte)0)));
                    else if (type.isHead() && display instanceof ItemDisplay item) item.setItemStack(type.heads().get(Math.floorMod(variant, type.heads().size())));
                    else if (!type.isHead() && display instanceof BlockDisplay block) block.setBlock(type.garland() ? VanillaGarlands.light(type.color(variant)) : bulbData(type, string, variant));
                    string.shownVariant.put(index, variant);
                }
            }
            string.idle = !active || (string.mode == AnimationMode.STEADY && (!progressive || (step - string.switchedAt) * progressiveSpeed > string.count + 2L));
        }
        refreshRealLights();
        if (coloredLights.due(animationTicks)) {
            coloredLights.limitBudget(Math.max(0, maxEntities - geometryEntities()));
            coloredLights.sync(this::rgbSources);
        }
    }
    private List<StrobeLightsBridge.Source> rgbSources(List<Player> viewers) {
        List<StrobeLightsBridge.Source> sources = new ArrayList<>();
        for (LightString string : strings.values()) {
            if (!string.on) continue;
            LightType type = getType(string.type); World world = Bukkit.getWorld(string.world);
            if (!string.spawned || type == null || world == null || string.bulbs.isEmpty()) continue;
            if (nightOnly && isDay(world)) continue;
            Vector center = string.a.clone().add(string.b).multiply(.5);
            double radius = string.length() / 2 + string.sag + type.chain() + type.size() + coloredLights.viewDistance();
            if (viewers.stream().noneMatch(player -> player.getWorld() == world
                    && (coloredLights.followsChunks() || player.getEyeLocation().toVector().distanceSquared(center) <= radius * radius))) continue;
            if (string.rgbStride == 0) {
                double curve = LightGeometry.curveLength(string);
                double distance = curve / Math.max(1, type.wall() && !type.garland() ? string.count - 1 : string.count);
                string.rgbStride = LightGeometry.sourceStride(coloredLights.sampleSpacing(), distance, type.colors().size());
                string.rgbClearance = type.garland() ? Math.hypot(.5 * type.size(), .5 * curve / string.count) : type.size() * .8;
            }
            int stride = string.rgbStride;
            Vector direction = type.wall() && string.normal.lengthSquared() > .5 ? string.normal.clone().multiply(-1) : new Vector(0, -1, 0);
            for (var bulb : string.bulbs.entrySet()) {
                int index = bulb.getKey();
                if (index % stride != 0 || !bulb.getValue().isValid()) continue;
                int variant = Math.floorMod(index + (surfaceEffects && string.mode == AnimationMode.CHASE ? (int)(step / effectSteps) : 0), type.variants());
                int rgb = type.color(variant); if (rgb == 0) continue;
                double intensity = !shownOn(string, index) ? 0 : surfaceEffects ? modeIntensity(string, index, type.variants()) : 1;
                int level = (int)Math.round((type.light() >= 0 ? type.light() : lightLevel) * intensity);
                Location source = LightGeometry.lightPoint(string, type, index).toLocation(world);
                if (world.isChunkLoaded(source.getBlockX() >> 4, source.getBlockZ() >> 4)
                        && viewers.stream().anyMatch(player -> coloredLights.receives(player, source)))
                    sources.add(new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(string.id, index), source, rgb, level, direction, string.rgbClearance));
            }
        }
        return sources;
    }
    List<String> rgbDiagnostic(Player player) {
        var lines = new ArrayList<String>();
        lines.add("RGB player=" + player.getName() + ", world=" + player.getWorld().getName()
                + ", savedSegments=" + strings.size() + ", geometry=" + geometryEntities() + "/" + maxEntities);
        lines.addAll(coloredLights.diagnostic(player));
        return lines;
    }
    private boolean usesNativeLight() { return placeLightBlocks && !coloredLights.suppressesNativeLight(); }
    private void refreshGarlandBodyLight(Display display, LightString string) {
        World world = display.getWorld();
        var at = display.getLocation();
        if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        int minimum = string.on && !(nightOnly && isDay(world)) ? bodyLightFloor : 0;
        Display.Brightness wanted = null;
        if (minimum > 0) {
            Block block = world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            wanted = new Display.Brightness(Math.max(minimum, block.getLightFromBlocks()), block.getLightFromSky());
        }
        if (!Objects.equals(display.getBrightness(), wanted)) display.setBrightness(wanted);
    }
    private void refreshRealLights() {
        boolean suppressed = !usesNativeLight();
        if (suppressed != nativeLightSuppressed) { nativeLightSuppressed = suppressed; realLightsDirty = true; }
        if (!realLightsDirty && step % Math.max(1, 20 / Math.max(1, animationTicks)) != 0) return;
        if (suppressed) {
            realLights.apply(Map.of()); // Only our owned LIGHT blocks are restored to their original air.
            realLightsDirty = false;
            return;
        }
        Map<LightBlocks.Position, Integer> levels = new HashMap<>();
        for (LightString string : strings.values()) {
            if (!string.spawned || !string.on) continue;
            LightType type = getType(string.type); World world = Bukkit.getWorld(string.world);
            if (world == null || type == null) continue;
            boolean forcedOff = nightOnly && isDay(world);
            if (forcedOff) continue;
            // Restore the fallback without rebuilding display entities when the host disappears.
            for (var bulb : string.bulbs.entrySet()) {
                if (!bulb.getValue().isValid()) continue;
                int index = bulb.getKey();
                BlockVector point = string.bulbLights.get(index);
                if (point == null) point = LightGeometry.lightPoint(string, type, index).toBlockVector();
                if (!realLights.claim(world, point)) continue;
                string.bulbLights.put(index, point);
                if (!string.lightBlocks.contains(point)) string.lightBlocks.add(point);
                double intensity = !shownOn(string, index) || forcedOff ? 0 : animatedLight ? modeIntensity(string, index, type.variants()) : 1;
                int level = (int)Math.round((type != null && type.light() >= 0 ? type.light() : lightLevel) * intensity);
                levels.merge(LightBlocks.Position.at(string.world, point), level, Math::max);
            }
            // Old versions sometimes stored only the block list, without bulb indices.
            for (BlockVector vector : string.lightBlocks) if (realLights.claim(world, vector)) levels.putIfAbsent(LightBlocks.Position.at(string.world, vector), string.on && !forcedOff ? lightLevel : 0);
        }
        realLights.apply(levels);
        realLightsDirty = false;
    }
    private boolean shownOn(LightString string, int index) { return string.on && (!progressive || (step - string.switchedAt) * progressiveSpeed >= index + 1L); }
    private double modeIntensity(LightString string, int index, int variants) {
        long phase = step / effectSteps;
        return switch (string.mode) {
            case STEADY -> 1;
            case BLINK -> phase % 2 == 0 ? 1 : 0;
            case ALTERNATE -> (index + phase) % 2 == 0 ? 1 : 0;
            case CHASE -> variants > 1 || (index + phase) % 3 != 0 ? 1 : 0;
            case TWINKLE -> random01(string.id.hashCode(), index, phase) > .2 ? 1 : 0;
            case SWEEP -> { long point = step % (2L * Math.max(1, string.count)); yield point < string.count ? (index <= point ? 1 : 0) : (index > point - string.count ? 1 : 0); }
            case BREATHE -> .5 + .5 * Math.sin(Math.PI * 2 * step / breathePeriod);
        };
    }
    private static double random01(int seed, int index, long phase) { long value = seed * -7046029254386353131L + index * -4417276706812531889L + phase * 1609587929392839161L; value ^= value >>> 33; value *= -49064778989728563L; value ^= value >>> 33; return (value >>> 11) * 0x1.0p-53; }
    private static boolean isDay(World world) { long time = Math.floorMod(world.getTime(), 24000); return time < 12300 || time > 23850; }

    public void loadData() throws Exception {
        dataReady = false;
        var yaml = SafeYaml.load(dataFile.toPath());
        if (yaml.contains("lights") && !yaml.isConfigurationSection("lights")) throw new ConfigProblem("section", "path", "lights");
        if (yaml.contains("groups") && !yaml.isList("groups")) throw new ConfigProblem("list", "path", "groups");
        if (yaml.contains("owned-light-blocks") && !yaml.isList("owned-light-blocks")) throw new ConfigProblem("list", "path", "owned-light-blocks");
        if (yaml.contains("legacy-geometry-chunks") && !yaml.isList("legacy-geometry-chunks")) throw new ConfigProblem("list", "path", "legacy-geometry-chunks");
        if (yaml.contains("data-version") && !yaml.isInt("data-version")) throw new ConfigProblem("integer", "path", "data-version");
        if (yaml.getInt("data-version", 1) > 4) throw new IllegalArgumentException(plugin.messages().plain("errors.newer-schema"));
        strings.clear(); groups.clear();
        for (String group : yaml.getStringList("groups")) canonicalGroup(group);
        var lights = yaml.getConfigurationSection("lights");
        if (lights != null) for (String id : lights.getKeys(false)) {
            var section = Objects.requireNonNull(lights.getConfigurationSection(id));
            for (String key : List.of("on", "spawned", "skip-start", "refundable", "straight")) if (section.contains(key) && !section.isBoolean(key)) throw new ConfigProblem("boolean", "path", "lights." + id + "." + key);
            for (String key : List.of("bulbs", "generation")) if (section.contains(key) && !section.isInt(key)) throw new ConfigProblem("integer", "path", "lights." + id + "." + key);
            if (section.contains("light-blocks") && !section.isList("light-blocks")) throw new ConfigProblem("list", "path", "lights." + id + ".light-blocks");
            AnimationMode mode = AnimationMode.parse(section.getString("mode", "steady"));
            if (mode == null) throw new IllegalArgumentException("lights." + id + ".mode");
            LightString string = new LightString(UUID.fromString(id), Objects.requireNonNull(section.getString("world")), parseVec(section.getString("a")), parseVec(section.getString("b")),
                    parseVec(section.getString("normal", "0,0,0")), parseVec(section.getString("anchor-a")).toBlockVector(), parseVec(section.getString("anchor-b")).toBlockVector(),
                    section.getDouble("sag"), Objects.requireNonNull(section.getString("type")), mode);
            if (string.world.isBlank() || !Double.isFinite(string.sag) || string.sag < 0 || string.sag > 256 || string.length() > 256 || string.length() < .01 || string.normal.lengthSquared() > 1.001) throw new IllegalArgumentException("lights." + id);
            string.group = section.getString("group"); if (string.group != null) string.group = canonicalGroup(string.group);
            if (section.contains("chain")) string.chain = UUID.fromString(Objects.requireNonNull(section.getString("chain")));
            if (section.contains("owner")) string.owner = UUID.fromString(Objects.requireNonNull(section.getString("owner")));
            string.refundable = section.getBoolean("refundable", false);
            string.straight = section.getBoolean("straight", false);
            if (section.contains("decoswap.deployment")) {
                string.decoDeployment = UUID.fromString(section.getString("decoswap.deployment"));
                string.decoDecoration = UUID.fromString(section.getString("decoswap.decoration"));
                var parts = section.getConfigurationSection("decoswap.parts");
                if (parts != null) for (String role : parts.getKeys(false)) {
                    if (!parts.isInt(role) || role.length() > 80) throw new IllegalArgumentException("decoswap.parts");
                    string.decoParts.put(role, parts.getInt(role));
                }
            }
            string.on = section.getBoolean("on", true); string.count = section.getInt("bulbs", 0);
            if (string.count < 0 || string.count > 4096) throw new IllegalArgumentException("lights." + id + ".bulbs");
            string.spawned = section.getBoolean("spawned", true); string.skipStart = section.getBoolean("skip-start");
            string.generation = section.getInt("generation"); string.signature = section.getString("signature");
            for (String encoded : section.getStringList("light-blocks")) {
                int split = encoded.indexOf(':');
                BlockVector position = parseVec(split < 0 ? encoded : encoded.substring(split + 1)).toBlockVector();
                if (!string.lightBlocks.contains(position)) string.lightBlocks.add(position);
                if (split >= 0) {
                    int index = Integer.parseInt(encoded.substring(0, split));
                    if (index < 0 || index >= Math.max(1, string.count)) throw new IllegalArgumentException(encoded);
                    string.bulbLights.put(index, position);
                }
            }
            strings.put(string.id, string);
        }
        realLights.load(yaml, strings.values());
        for (String encoded : yaml.getStringList("legacy-geometry-chunks")) {
            String[] parts = encoded.split(";", -1);
            if (parts.length != 3 || parts[0].isBlank()) throw new IllegalArgumentException(encoded);
            int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
            if (Math.abs((long)x) > 1_875_001 || Math.abs((long)z) > 1_875_001) throw new IllegalArgumentException(encoded);
            legacyGeometryChunks.add(new BackingChunk(parts[0], x, z));
        }
        if (dataFile.exists() && !"ephemeral-v1".equals(yaml.getString("render-storage"))) for (LightString string : strings.values()) {
            int minX = (int)Math.floor(Math.min(string.a.getX(), string.b.getX()) - 3) >> 4;
            int maxX = (int)Math.floor(Math.max(string.a.getX(), string.b.getX()) + 3) >> 4;
            int minZ = (int)Math.floor(Math.min(string.a.getZ(), string.b.getZ()) - 3) >> 4;
            int maxZ = (int)Math.floor(Math.max(string.a.getZ(), string.b.getZ()) + 3) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) legacyGeometryChunks.add(new BackingChunk(string.world, x, z));
        }
        if (dataFile.exists() && yaml.getInt("data-version", 1) < 4) SafeYaml.versionBackup(dataFile.toPath());
        dataReady = true;
    }
    public boolean saveData() {
        if (!dataReady) return false; // Failed startup must never overwrite the user's file.
        var yaml = new YamlConfiguration(); yaml.set("data-version", 4); yaml.set("groups", new ArrayList<>(groups));
        if (legacyGeometryChunks.isEmpty()) yaml.set("render-storage", "ephemeral-v1");
        else yaml.set("legacy-geometry-chunks", legacyGeometryChunks.stream().map(pos -> pos.world + ";" + pos.x + ";" + pos.z).toList());
        for (LightString string : strings.values()) {
            String path = "lights." + string.id + ".";
            yaml.set(path + "world", string.world); yaml.set(path + "a", vecToString(string.a)); yaml.set(path + "b", vecToString(string.b)); yaml.set(path + "normal", vecToString(string.normal));
            yaml.set(path + "anchor-a", vecToString(string.anchorA)); yaml.set(path + "anchor-b", vecToString(string.anchorB)); yaml.set(path + "sag", string.sag);
            yaml.set(path + "type", string.type); yaml.set(path + "mode", string.mode.id()); yaml.set(path + "group", string.group); yaml.set(path + "chain", string.chain.toString());
            yaml.set(path + "owner", string.owner == null ? null : string.owner.toString()); yaml.set(path + "refundable", string.refundable);
            yaml.set(path + "straight", string.straight);
            if (string.decoDeployment != null) {
                yaml.set(path + "decoswap.deployment", string.decoDeployment.toString());
                yaml.set(path + "decoswap.decoration", string.decoDecoration.toString());
                for (var entry : string.decoParts.entrySet()) yaml.set(path + "decoswap.parts." + entry.getKey(), entry.getValue());
            }
            yaml.set(path + "on", string.on); yaml.set(path + "bulbs", string.count); yaml.set(path + "spawned", string.spawned); yaml.set(path + "skip-start", string.skipStart);
            yaml.set(path + "generation", string.generation); yaml.set(path + "signature", string.signature);
            List<String> blocks = new ArrayList<>(); Set<BlockVector> indexed = new HashSet<>();
            for (var entry : string.bulbLights.entrySet()) { blocks.add(entry.getKey() + ":" + vecToString(entry.getValue())); indexed.add(entry.getValue()); }
            for (BlockVector vector : string.lightBlocks) if (!indexed.contains(vector)) blocks.add(vecToString(vector));
            yaml.set(path + "light-blocks", blocks);
        }
        realLights.save(yaml);
        try { SafeYaml.save(yaml, dataFile.toPath(), true); return true; }
        catch (Exception failure) { plugin.messages().warn("log.save-failed", "error", failure.getMessage()); return false; }
    }
    private static String vecToString(Vector vector) { return vector.getX() + "," + vector.getY() + "," + vector.getZ(); }
    static Vector parseVec(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("null vector");
        String[] parts = encoded.split(",", -1); if (parts.length != 3) throw new IllegalArgumentException(encoded);
        double x = Double.parseDouble(parts[0].trim()), y = Double.parseDouble(parts[1].trim()), z = Double.parseDouble(parts[2].trim());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 2048) throw new IllegalArgumentException(encoded);
        return new Vector(x, y, z);
    }
}
