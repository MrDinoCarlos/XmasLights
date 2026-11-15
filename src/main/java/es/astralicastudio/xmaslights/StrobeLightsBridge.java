package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * Optional StrobeLights renderer adapter. Uses its installed carrier model and shader,
 * never its persistent strobe registry, flare effects or private Java methods; never writes world blocks.
 */
final class StrobeLightsBridge implements AutoCloseable {
    record Key(UUID string, int index) { }
    record Source(Key key, Location location, int rgb, int level, org.bukkit.util.Vector direction, double clearance) {
        Source(Key key, Location location, int rgb, int level) { this(key, location, rgb, level, new org.bukkit.util.Vector(0, -1, 0), .25); }
    }
    private record Candidate(Source source, double distance) { }
    private record Projection(SurfaceLighting.Hit hit, int payload) { }
    private static final Comparator<Candidate> NEAREST = Comparator.comparingDouble(Candidate::distance)
            .thenComparing(candidate -> candidate.source.key.string).thenComparingInt(candidate -> candidate.source.key.index);
    private static final class Carrier {
        ItemDisplay display;
        Location anchor, source;
        int payload = -1;
        int slot = -1;
    }
    private final XmasLightsPlugin plugin;
    private final NamespacedKey markerKey;
    private final Map<UUID, Map<Key, Carrier>> viewers = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> sentChunks = new HashMap<>();
    private final Map<Integer, org.bukkit.inventory.ItemStack> items = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, org.bukkit.inventory.ItemStack> entry) { return size() > 512; }
    };
    private Plugin host, rejectedHost;
    private Object packService;
    private Method resourcePack, packLoaded;
    private boolean enabled;
    private int maxPerPlayer, maxTotal, updateTicks, elapsed;
    private int displayBudget = Integer.MAX_VALUE;
    private double range, power, sampleSpacing, impactRadius, distanceFade, hysteresis;
    private SurfaceLighting surfaces;
    private final Map<UUID, String> diagnostics = new HashMap<>();
    private String readinessError;

    StrobeLightsBridge(XmasLightsPlugin plugin) { this.plugin = plugin; markerKey = new NamespacedKey(plugin, "rgb_carrier"); }
    void configure(YamlConfiguration config) {
        close(); rejectedHost = null;
        enabled = config.getBoolean("strobelights.enabled");
        maxPerPlayer = config.getInt("strobelights.max-sources-per-player"); maxTotal = config.getInt("strobelights.max-carriers-total");
        range = config.getDouble("strobelights.view-distance-chunks") * 16;
        power = config.getDouble("strobelights.intensity-scale"); sampleSpacing = config.getDouble("strobelights.source-spacing");
        updateTicks = config.getInt("strobelights.update-ticks"); elapsed = updateTicks;
        impactRadius = config.getDouble("strobelights.surfaces.max-radius"); distanceFade = config.getDouble("strobelights.surfaces.distance-fade");
        hysteresis = config.getDouble("strobelights.selection-hysteresis");
        surfaces = new SurfaceLighting(config.getDouble("strobelights.surfaces.reach"), config.getInt("strobelights.surfaces.refresh-ticks"), config.getInt("strobelights.surfaces.probes-per-update"));
    }
    double sampleSpacing() { return sampleSpacing; }
    double viewDistance() { return range + 8; }
    boolean followsChunks() { return range == 0; }
    boolean receives(Player player, Location source) {
        return player.getWorld() == source.getWorld()
                && sentChunks.computeIfAbsent(player.getUniqueId(), ignored -> player.getSentChunkKeys())
                        .contains(Chunk.getChunkKey(source.getBlockX() >> 4, source.getBlockZ() >> 4));
    }
    /** Renderer choice is global and independent of protocol/pack readiness. */
    boolean suppressesNativeLight() { return enabledHost() != null; }
    private Plugin enabledHost() {
        if (!enabled) return null;
        Plugin candidate = plugin.getServer().getPluginManager().getPlugin("StrobeLights");
        return candidate != null && candidate.isEnabled() ? candidate : null;
    }
    void limitBudget(int budget) {
        displayBudget = Math.max(0, budget);
        int retained = 0;
        for (var map : viewers.values()) {
            var entries = map.entrySet().iterator();
            while (entries.hasNext()) { var entry = entries.next(); if (retained++ >= displayBudget) { remove(entry.getValue()); entries.remove(); } }
        }
    }
    boolean due(int passedTicks) {
        if (!enabled) return false;
        elapsed += passedTicks;
        if (elapsed < updateTicks) return false;
        elapsed = 0;
        if (Bukkit.getOnlinePlayers().isEmpty()) { clearCarriers(); return false; }
        return resolve();
    }

    private boolean resolve() {
        Plugin candidate = enabledHost();
        if (candidate == null) { if (host != null) close(); return false; }
        if (candidate == rejectedHost) return false;
        if (candidate == host) return true;
        close();
        String version = candidate.getPluginMeta().getVersion().split("\\+")[0];
        try {
            // Compatibility is the advertised carrier ABI, not a hardcoded patch-version list.
            // Validate StrobeLights' own carrier contract. Its legacy capability
            // method also checks TRP's renderer and must not veto XmasLights RGB.
            verifyCarrier(candidate, Bukkit.getMinecraftVersion());
            resourcePack = candidate.getClass().getMethod("resourcePack");
            host = candidate;
            plugin.messages().info("log.strobe-connected", "version", version);
            return true;
        } catch (Exception | LinkageError failure) {
            rejectedHost = candidate; host = null;
            plugin.messages().warn("log.strobe-rgb-blocked", "error", String.valueOf(failure.getMessage()));
            return false;
        }
    }

    static void verifyCarrier(Plugin host, String minecraft) throws Exception {
        String profile = switch (minecraft) { case "1.21.7", "1.21.8" -> "1.21.6"; case "1.21.10", "1.21.11" -> "1.21.9"; default -> minecraft; };
        var input = host.getResource("embedded/StrobeLights-ResourcePack-" + minecraft + ".zip");
        if (input == null && !profile.equals(minecraft)) input = host.getResource("embedded/StrobeLights-ResourcePack-" + profile + ".zip");
        if (input == null) throw new IllegalArgumentException("StrobeLights carrier pack: " + minecraft);
        try (var zip = new ZipInputStream(input)) {
            boolean model = false, stable = false;
            for (int i = 0; i < 4096; i++) {
                var entry = zip.getNextEntry(); if (entry == null) break;
                if (entry.getName().equals("assets/strobelights/strobelights-integration.json")) {
                    byte[] bytes = zip.readNBytes(65537);
                    if (bytes.length > 65536) break;
                    var contract = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    stable = contract.has("stable_source_protocol") && contract.get("stable_source_protocol").getAsString().equals("slots3x3_depth_v1");
                    if (stable && model) return;
                    continue;
                }
                if (!entry.getName().equals("assets/strobelights/items/carrier/rgb.json")) continue;
                byte[] bytes = zip.readNBytes(65537);
                if (bytes.length > 65536) break;
                var json = com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("model");
                if (json.get("model").getAsString().equals("strobelights:item/strobe_rgb_carrier")
                        && json.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString().equals("minecraft:custom_model_data")) model = true;
                if (model && stable) return;
            }
        }
        throw new IllegalArgumentException("StrobeLights RGB carrier contract missing for Minecraft " + minecraft
                + (minecraft.equals("26.3") ? " (requires StrobeLights 0.10.43+)" : ""));
    }

    private boolean ready(Player player) {
        readinessError = null;
        try {
            Object current = resourcePack.invoke(host);
            if (current == null) return false;
            if (current != packService) { packService = current; packLoaded = current.getClass().getMethod("isLoaded", Player.class); }
            // TRP's Native/Fabulous preference controls TRP alone. XmasLights
            // needs only StrobeLights and its loaded pack, just as without TRP.
            return Boolean.TRUE.equals(packLoaded.invoke(current, player));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            readinessError = failure.toString();
            return false;
        }
    }

    List<String> diagnostic(Player player) {
        boolean resolved = resolve();
        boolean loaded = resolved && ready(player);
        List<String> lines = new ArrayList<>();
        lines.add("RGB renderer=StrobeLights shader, StrobeLights=" + (enabledHost() == null ? "unavailable" : enabledHost().getPluginMeta().getVersion())
                + ", protocol=" + resolved + ", packReady=" + loaded + ", nativeSuppressed=" + suppressesNativeLight());
        lines.add("RGB displayBudget=" + displayBudget + ", maxTotal=" + maxTotal + ", maxPerPlayer=" + maxPerPlayer
                + ", carriers=" + viewers.getOrDefault(player.getUniqueId(), Map.of()).size()
                + ", sentChunks=" + sentChunks.getOrDefault(player.getUniqueId(), Set.of()).size());
        lines.add("RGB lastUpdate: " + diagnostics.getOrDefault(player.getUniqueId(), "not sampled"));
        if (readinessError != null) lines.add("RGB readiness error: " + readinessError);
        if (packService != null) try {
            lines.add("RGB pack: " + packService.getClass().getMethod("diagnostic", Player.class).invoke(packService, player));
        } catch (NoSuchMethodException ignored) { }
        catch (ReflectiveOperationException failure) { lines.add("RGB pack diagnostic error: " + failure); }
        return lines;
    }

    void sync(List<Source> sources) {
        sync(ignored -> sources);
    }
    void sync(java.util.function.Function<List<Player>, List<Source>> sourceFactory) {
        if (displayBudget == 0 || !resolve()) { clearCarriers(); return; }
        // On Paper/Moonrise, isChunkSent can consult an obsolete vanilla tracking view
        // and return false even for received chunks. Snapshot the actual sent-key set
        // once per viewer/update; never clone it for every sampled bulb.
        sentChunks.clear();
        List<Player> eligible = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ready(player)) { eligible.add(player); sentChunks.put(player.getUniqueId(), player.getSentChunkKeys()); }
            else diagnostics.put(player.getUniqueId(), "waiting for renderer pack" + (readinessError == null ? "" : ": " + readinessError));
        }
        if (eligible.isEmpty()) { clearCarriers(); return; }
        List<Source> sources = sourceFactory.apply(eligible);
        surfaces.beginUpdate(updateTicks);
        Set<UUID> readyIds = new HashSet<>(); eligible.forEach(player -> readyIds.add(player.getUniqueId()));
        viewers.entrySet().removeIf(entry -> { if (readyIds.contains(entry.getKey())) return false; entry.getValue().values().forEach(StrobeLightsBridge::remove); return true; });
        int capacity = Math.min(maxTotal, displayBudget);
        int allocated = viewers.values().stream().mapToInt(Map::size).sum();
        Set<UUID> retainedViewers = new HashSet<>(); int remaining = capacity;
        int fairShare = Math.min(maxPerPlayer, Math.max(1, remaining / Math.max(1, eligible.size())));
        for (Player player : eligible) {
            if (remaining <= 0) break;
            retainedViewers.add(player.getUniqueId());
            Map<Key, Carrier> existing = viewers.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
            int budget = Math.min(fairShare, remaining);
            Location eye = player.getEyeLocation();
            List<Source> selected = select(sources, eye, Math.min(sources.size(), budget * 2),
                    followsChunks() ? Double.POSITIVE_INFINITY : range, existing.keySet(), hysteresis,
                    source -> receives(player, source.location));
            Map<Key, Projection> projected = new LinkedHashMap<>();
            int noSurface = 0, noEnergy = 0, errors = 0;
            String lastError = "none";
            for (Source source : selected) {
                if (projected.size() >= budget) break;
                SurfaceLighting.Hit hit = surfaces.project(source);
                if (hit == null) { noSurface++; continue; }
                int payload = surfacePayload(source, hit);
                if (source.level == 0 ? !existing.containsKey(source.key) : StrobeProtocol.peak(payload) == 0) { noEnergy++; continue; }
                projected.put(source.key, new Projection(hit, payload));
            }
            // Retire old carriers before spawning replacements. Otherwise a full
            // renderer briefly exceeds its budget and sends both lights to the shader.
            ArrayDeque<Carrier> reusable = new ArrayDeque<>();
            existing.entrySet().removeIf(entry -> { if (projected.containsKey(entry.getKey())) return false; reusable.add(entry.getValue()); return true; });
            boolean[] occupied = new boolean[128];
            for (Carrier carrier : existing.values()) if (carrier.slot >= 0) occupied[carrier.slot] = true;
            for (Carrier carrier : reusable) if (carrier.slot >= 0) occupied[carrier.slot] = true;
            for (var entry : projected.entrySet()) {
                Carrier carrier = existing.get(entry.getKey());
                if (carrier == null) {
                    carrier = reusable.pollFirst();
                    if (carrier == null) {
                        if (allocated >= capacity) continue;
                        int slot = 0; while (slot < occupied.length && occupied[slot]) slot++;
                        if (slot == occupied.length) continue;
                        carrier = new Carrier(); carrier.slot = slot; occupied[slot] = true; allocated++;
                    }
                    existing.put(entry.getKey(), carrier);
                }
                try {
                    update(player, entry.getValue().hit, StrobeProtocol.withSlot(entry.getValue().payload, carrier.slot), carrier);
                }
                catch (RuntimeException failure) {
                    errors++; lastError = failure.toString();
                    remove(carrier); existing.remove(entry.getKey()); allocated--;
                }
            }
            for (Carrier spare : reusable) { remove(spare); allocated--; }
            remaining -= existing.size();
            diagnostics.put(player.getUniqueId(), "sources=" + sources.size() + ", selected=" + selected.size()
                    + ", projected=" + projected.size() + ", noSurface=" + noSurface + ", noEnergy=" + noEnergy
                    + ", carriers=" + existing.size() + ", errors=" + errors + ", lastError=" + lastError);
        }
        viewers.entrySet().removeIf(entry -> {
            if (retainedViewers.contains(entry.getKey())) return false;
            entry.getValue().values().forEach(StrobeLightsBridge::remove); return true;
        });
    }

    static List<Source> select(List<Source> sources, Location eye, int budget, double range) {
        return select(sources, eye, budget, range, Set.of());
    }
    static List<Source> select(List<Source> sources, Location eye, int budget, double range, Set<Key> retained) {
        return select(sources, eye, budget, range, retained, 2);
    }
    static List<Source> select(List<Source> sources, Location eye, int budget, double range, Set<Key> retained, double hysteresis) {
        return select(sources, eye, budget, range, retained, hysteresis, source -> true);
    }
    private static List<Source> select(List<Source> sources, Location eye, int budget, double range, Set<Key> retained,
                                       double hysteresis, java.util.function.Predicate<Source> visible) {
        if (budget <= 0) return List.of();
        PriorityQueue<Candidate> nearest = new PriorityQueue<>(budget, NEAREST.reversed());
        for (Source source : sources) {
            if (source.location.getWorld() != eye.getWorld()) continue;
            if (!visible.test(source)) continue;
            double distance = source.location.distanceSquared(eye);
            boolean current = retained.contains(source.key);
            if (distance > Math.pow(range + (current ? 8 : 0), 2)) continue;
            // A new lamp must be meaningfully closer to displace an existing one.
            // Tiny player movements must not alternate two sources at the budget boundary.
            double score = current ? Math.pow(Math.max(0, Math.sqrt(distance) - hysteresis), 2) : distance;
            Candidate candidate = new Candidate(source, score);
            if (nearest.size() < budget) nearest.add(candidate);
            else if (NEAREST.compare(candidate, nearest.peek()) < 0) { nearest.poll(); nearest.add(candidate); }
        }
        return nearest.stream().sorted(NEAREST).map(Candidate::source).toList();
    }

    private int surfacePayload(Source source, SurfaceLighting.Hit hit) {
        double attenuation = 1 / (1 + Math.pow(hit.distance() / distanceFade, 2));
        // Camera distance must never change a static lamp's energy. Only the
        // physical lamp-to-surface distance reduces its power and footprint.
        return StrobeProtocol.stableSource(source.rgb, (int)Math.round(source.level * power * attenuation), 0, impactRadius * Math.sqrt(attenuation));
    }
    private void update(Player player, SurfaceLighting.Hit hit, int payload, Carrier carrier) {
        Location eye = player.getEyeLocation();
        Location position = hit.position();
        Location anchor = carrier.anchor;
        if (anchor == null || anchor.getWorld() != player.getWorld()
                || anchor.distanceSquared(eye) > 24 * 24
                || !anchor.getWorld().isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) anchor = new Location(player.getWorld(), Math.floor(eye.getX() / 16) * 16 + 8,
                Math.clamp(Math.floor(eye.getY() / 16) * 16 + 8, player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - 1), Math.floor(eye.getZ() / 16) * 16 + 8);
        if (!player.getWorld().isChunkLoaded(anchor.getBlockX() >> 4, anchor.getBlockZ() >> 4)) { remove(carrier); return; }
        var item = items.computeIfAbsent(payload, StrobeProtocol::marker);
        var transformation = new Transformation(position.toVector().subtract(anchor.toVector()).toVector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf());
        if (carrier.display == null || !carrier.display.isValid() || !anchor.equals(carrier.anchor)) {
            // Spawn with complete metadata before showing it. Moving the network anchor
            // and its inverse translation in separate packets momentarily moves the light.
            remove(carrier);
            carrier.display = player.getWorld().spawn(anchor, ItemDisplay.class, display -> {
                display.setPersistent(false); display.setVisibleByDefault(false); display.setGravity(false); display.setInvulnerable(true); display.setSilent(true);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD); display.setBillboard(Display.Billboard.FIXED);
                display.setBrightness(new Display.Brightness(15,15)); display.setShadowRadius(0); display.setShadowStrength(0);
                display.setViewRange(1); display.setDisplayWidth(0); display.setDisplayHeight(0);
                display.setInterpolationDelay(0); display.setInterpolationDuration(0); display.setTeleportDuration(0);
                display.setTransformation(transformation); display.setItemStack(item);
                display.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte)1);
            });
            carrier.anchor = anchor.clone(); carrier.source = position.clone(); carrier.payload = payload;
            player.showEntity(plugin, carrier.display);
        }
        if (!position.equals(carrier.source)) {
            carrier.display.setTransformation(transformation);
            carrier.source = position.clone();
        }
        if (payload != carrier.payload) {
            carrier.display.setItemStack(item); carrier.payload = payload;
        }
    }
    private static void remove(Carrier carrier) {
        if (carrier.display != null) carrier.display.remove(); carrier.display = null; carrier.payload = -1; carrier.anchor = null; carrier.source = null;
    }
    private void clearCarriers() { viewers.values().forEach(map -> map.values().forEach(StrobeLightsBridge::remove)); viewers.clear(); }
    void forgetSources(Set<UUID> strings) {
        for (var carriers : viewers.values()) carriers.entrySet().removeIf(entry -> {
            if (!strings.contains(entry.getKey().string())) return false;
            remove(entry.getValue()); return true;
        });
    }
    void forget(UUID player) { diagnostics.remove(player); sentChunks.remove(player); Map<Key, Carrier> removed = viewers.remove(player); if (removed != null) removed.values().forEach(StrobeLightsBridge::remove); }
    @Override public void close() { clearCarriers(); sentChunks.clear(); diagnostics.clear(); items.clear(); if (surfaces != null) surfaces.clear(); host = null; resourcePack = null; packService = null; packLoaded = null; }
}
