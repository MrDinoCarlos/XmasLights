package es.astralicastudio.xmaslights;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Nexo first, then TRP, a configured URL or the built-in assets-only ZIP host. */
final class ModelPackService implements Listener, AutoCloseable {
    static final String FILE_NAME = "XmasLights-Models-" + Compatibility.minecraft() + ".zip";
    static final UUID PACK_ID = UUID.nameUUIDFromBytes(("XmasLights:models:" + Compatibility.minecraft()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final XmasLightsPlugin plugin;
    private byte[] bytes, sha1;
    private String url = "", earlyError;
    private boolean autoSend, useTrp, registered, assumeInstalled, required, earlyRebuild;
    private final Map<UUID, Set<UUID>> loaded = new HashMap<>();
    private final Map<UUID, UUID> requested = new HashMap<>();
    private final Set<UUID> pendingShared = new HashSet<>(), queued = new HashSet<>();
    private UUID sharedPackId;
    private Plugin host, earlyHost, dirtyNexo;
    private NexoPackBridge nexo;
    private EmbeddedModelPack embedded;
    private long lifecycle;
    ModelPackService(XmasLightsPlugin plugin) { this.plugin = plugin; }

    /** Every plugin's onLoad runs before onEnable: Nexo can read this input on its first build. */
    void prepareEarly() {
        Plugin candidate = plugin.getServer().getPluginManager().getPlugin("Nexo");
        if (candidate == null) return;
        try {
            var config = SafeYaml.load(plugin.getDataFolder().toPath().resolve("config.yml"));
            if (config.contains("config-version") && !config.isInt("config-version")) return;
            if (config.contains("model-pack.nexo.enabled") && !config.isBoolean("model-pack.nexo.enabled")) return;
            if (config.contains("model-pack.enabled") && !config.isBoolean("model-pack.enabled")) return;
            if (config.getInt("config-version", 1) > plugin.loadResource("config.yml").getInt("config-version")) return;
            if (!config.getBoolean("model-pack.enabled", true)) {
                earlyHost = candidate; earlyRebuild = NexoPackBridge.removeManaged(candidate) && candidate.isEnabled(); return;
            }
            if (!config.getBoolean("model-pack.nexo.enabled", true)) return;
            try (var input = Objects.requireNonNull(plugin.getResource("packs/" + FILE_NAME))) {
                var installation = NexoPackBridge.install(candidate, input.readAllBytes());
                earlyHost = candidate; earlyRebuild = candidate.isEnabled() && installation.changed();
            }
        } catch (Exception failure) { earlyError = String.valueOf(failure.getMessage()); }
    }
    static void validateUrl(String address) {
        address = address.trim();
        if (address.isBlank()) return;
        URI uri = URI.create(address);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("model-pack.url: HTTP(S)");
    }
    void configure() {
        close();
        var config = plugin.getConfig();
        url = config.getString("model-pack.url", "").trim();
        autoSend = config.getBoolean("model-pack.auto-send"); useTrp = config.getBoolean("model-pack.trp-overlay");
        assumeInstalled = config.getBoolean("model-pack.assume-installed"); required = config.getBoolean("model-pack.required");
        loaded.clear(); requested.clear(); pendingShared.clear(); queued.clear();
        if (earlyError != null) { plugin.messages().warn("log.model-pack-unavailable", "error", earlyError); earlyError = null; }
        if (!plugin.usesModelPack()) {
            bytes = null; sha1 = null;
            Plugin candidate = plugin.getServer().getPluginManager().getPlugin("Nexo");
            if (candidate != null) try {
                boolean removed = NexoPackBridge.removeManaged(candidate);
                if (candidate.isEnabled() && (removed || candidate == earlyHost && earlyRebuild)) {
                    host = candidate; nexo = new NexoPackBridge(plugin, candidate, () -> { }); nexo.start(true, true);
                }
            } catch (Exception failure) { plugin.messages().warn("log.model-pack-unavailable", "error", String.valueOf(failure.getMessage())); }
            earlyHost = null; earlyRebuild = false;
            for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
            return;
        }
        try (var input = Objects.requireNonNull(plugin.getResource("packs/" + FILE_NAME))) {
            bytes = input.readAllBytes(); sha1 = MessageDigest.getInstance("SHA-1").digest(bytes);
            Path folder = plugin.getDataFolder().toPath().resolve("resource-pack"); Files.createDirectories(folder);
            Path temporary = Files.createTempFile(folder, "models-", ".tmp");
            try { Files.write(temporary, bytes); Files.move(temporary, folder.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING); }
            finally { Files.deleteIfExists(temporary); }
            Plugin candidate = plugin.getServer().getPluginManager().getPlugin("Nexo");
            if (config.getBoolean("model-pack.nexo.enabled") && candidate != null && candidate.isEnabled()) {
                var installation = NexoPackBridge.install(candidate, bytes);
                if (installation.changed() || candidate == earlyHost && earlyRebuild) dirtyNexo = candidate;
                host = candidate;
                nexo = new NexoPackBridge(plugin, candidate, () -> {
                    if (dirtyNexo == candidate) dirtyNexo = null;
                    sendWaitingPlayers();
                });
                plugin.messages().info("log.model-pack-nexo", "file", installation.path());
                nexo.start(dirtyNexo == candidate, config.getBoolean("model-pack.nexo.auto-rebuild"));
            } else {
                tryRegisterTrp();
                if (!registered && url.isBlank() && config.getBoolean("model-pack.embedded.enabled")) {
                    embedded = new EmbeddedModelPack(config.getString("model-pack.embedded.bind-address"), config.getInt("model-pack.embedded.port"),
                            config.getString("model-pack.embedded.public-url", ""), bytes, sha1);
                    plugin.messages().info("log.model-pack-hosted", "port", embedded.port());
                }
            }
            earlyHost = null; earlyRebuild = false;
            for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
            if (autoSend) sendWaitingPlayers();
        } catch (Exception failure) { plugin.messages().warn("log.model-pack-unavailable", "error", String.valueOf(failure.getMessage())); }
    }
    private void tryRegisterTrp() {
        if (registered || !useTrp || bytes == null) return;
        Plugin trp = plugin.getServer().getPluginManager().getPlugin("TRPServerEdition");
        if (trp == null || !trp.isEnabled()) return;
        try {
            Object result = trp.getClass().getMethod("registerResourcePackOverlay", Plugin.class, String.class, byte[].class).invoke(trp, plugin, FILE_NAME, bytes);
            registered = result instanceof String address && !address.isBlank();
            if (registered) { sharedPackId = embeddedPackId((String) result); host = trp; plugin.messages().info("log.model-pack-shared"); }
        } catch (ReflectiveOperationException | RuntimeException failure) { plugin.messages().warn("log.model-pack-unavailable", "error", String.valueOf(failure.getMessage())); }
    }
    /** TRP's embedded provider derives its pack UUID from the SHA-1 in its URL. */
    static UUID embeddedPackId(String address) {
        String path = URI.create(address).getPath();
        if (path == null || !path.matches(".*/trpserveredition/[a-fA-F0-9]{40}\\.zip")) return null;
        return UUID.nameUUIDFromBytes(HexFormat.of().parseHex(path.substring(path.lastIndexOf('/') + 1, path.length() - 4)));
    }
    private void sendWaitingPlayers() {
        long current = lifecycle;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (current != lifecycle || !plugin.isEnabled()) return;
            for (Player player : Bukkit.getOnlinePlayers()) if (autoSend || queued.contains(player.getUniqueId())) autoRequest(player);
        });
    }
    private void autoRequest(Player player) {
        if (!ready(player) && !requested.containsKey(player.getUniqueId())) request(player, false);
    }
    void request(Player player, boolean feedback) {
        if (!plugin.usesModelPack()) { if (feedback) plugin.messages().send(player, "pack.disabled"); return; }
        if (nexo != null) {
            UUID id = nexo.packId();
            if (id == null) { queued.add(player.getUniqueId()); if (feedback) plugin.messages().send(player, "pack.preparing"); return; }
            requested.put(player.getUniqueId(), id);
            boolean sent = nexo.send(player);
            if (sent) queued.remove(player.getUniqueId()); else requested.remove(player.getUniqueId());
            if (feedback) plugin.messages().send(player, sent ? "pack.sent" : "pack.unavailable");
            return;
        }
        if (registered && host != null && host.isEnabled()) {
            try { pendingShared.add(player.getUniqueId()); host.getClass().getMethod("resendResourcePack", Plugin.class, Player.class).invoke(host, plugin, player); if (feedback) plugin.messages().send(player, "pack.sent"); return; }
            catch (ReflectiveOperationException failure) { if (feedback) plugin.messages().send(player, "pack.unavailable"); return; }
        }
        String address = embedded == null ? url : embedded.url(advertisedHost(player));
        if (address.isBlank() || sha1 == null) { if (feedback) plugin.messages().send(player, "pack.manual", "file", "plugins/XmasLights/resource-pack/" + FILE_NAME); return; }
        requested.put(player.getUniqueId(), PACK_ID);
        player.addResourcePack(PACK_ID, address, sha1, plugin.messages().plain(player, "pack.models-prompt"), required);
        if (feedback) plugin.messages().send(player, "pack.sent");
    }
    private String advertisedHost(Player player) {
        var virtual = player.getVirtualHost();
        if (virtual != null && validHost(virtual.getHostString())) return virtual.getHostString();
        String configured = plugin.getServer().getIp();
        return validHost(configured) ? configured : "127.0.0.1";
    }
    private static boolean validHost(String host) {
        if (host == null || host.isBlank() || Set.of("0.0.0.0", "::", "[::]").contains(host)) return false;
        try { return new URI("http", null, host, 80, null, null, null).getHost() != null; }
        catch (java.net.URISyntaxException failure) { return false; }
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer()); long current = lifecycle;
        if (autoSend && plugin.usesModelPack()) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (current == lifecycle && event.getPlayer().isOnline()) autoRequest(event.getPlayer()); }, 40);
    }
    boolean ready(Player player) { return plugin.usesModelPack() && (assumeInstalled || !loaded.getOrDefault(player.getUniqueId(), Set.of()).isEmpty()); }
    private void refresh(Player player) { if (plugin.manager() != null) plugin.manager().refreshModelVisibility(player); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId(); loaded.remove(id); requested.remove(id); pendingShared.remove(id); queued.remove(id);
    }
    @EventHandler public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.usesModelPack()) return;
        UUID playerId = event.getPlayer().getUniqueId(), id = event.getID();
        boolean known = loaded.getOrDefault(playerId, Set.of()).contains(id) || id.equals(requested.get(playerId));
        boolean shared = nexo != null ? id.equals(nexo.packId()) : registered && (sharedPackId != null ? sharedPackId.equals(id) : pendingShared.contains(playerId));
        if (!PACK_ID.equals(id) && !known && !shared) return;
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> { loaded.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(id); requested.remove(playerId, id); pendingShared.remove(playerId); queued.remove(playerId); }
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, DISCARDED, INVALID_URL -> {
                var packs = loaded.get(playerId); if (packs != null) { packs.remove(id); if (packs.isEmpty()) loaded.remove(playerId); }
                requested.remove(playerId, id); pendingShared.remove(playerId); queued.remove(playerId);
            }
            case ACCEPTED, DOWNLOADED -> { requested.put(playerId, id); return; }
            default -> { return; }
        }
        refresh(event.getPlayer());
    }
    @EventHandler public void onDependencyEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();
        if (name.equals("Nexo") || name.equals("TRPServerEdition") && nexo == null) {
            lifecycle++;
            Bukkit.getScheduler().runTask(plugin, this::configure);
        }
    }
    @EventHandler public void onDependencyDisable(PluginDisableEvent event) {
        if (event.getPlugin() != host) return;
        registered = false;
        close();
        loaded.values().forEach(packs -> packs.removeIf(id -> !id.equals(PACK_ID)));
        requested.clear(); pendingShared.clear();
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        Bukkit.getScheduler().runTask(plugin, this::configure);
    }
    @Override public void close() {
        lifecycle++;
        if (nexo != null) { nexo.close(); nexo = null; }
        if (embedded != null) { embedded.close(); embedded = null; }
        if (registered && host != null && host.isEnabled()) try { host.getClass().getMethod("unregisterResourcePackOverlay", Plugin.class).invoke(host, plugin); }
        catch (ReflectiveOperationException failure) { plugin.messages().warn("log.model-pack-unavailable", "error", String.valueOf(failure.getMessage())); }
        registered = false; host = null; sharedPackId = null;
    }
}
