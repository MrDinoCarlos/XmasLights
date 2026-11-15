package es.astralicastudio.xmaslights;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Only the HTTP request runs off-thread; configuration, messages and players stay on the server thread. */
final class UpdateChecker implements Listener, AutoCloseable {
    record Release(String version, String id) { }
    private final XmasLightsPlugin plugin;
    private BukkitTask task;
    private Release available;
    private String announced;
    private long generation;
    private boolean notifyAdmins;
    UpdateChecker(XmasLightsPlugin plugin) { this.plugin = plugin; }
    void configure() {
        close();
        notifyAdmins = plugin.getConfig().getBoolean("updates.notify-admins");
        if (!plugin.getConfig().getBoolean("updates.enabled")) return;
        long epoch = generation;
        String minecraft = Bukkit.getMinecraftVersion(), installed = plugin.getPluginMeta().getVersion();
        String filter = URLEncoder.encode(new Gson().toJson(List.of(minecraft)), StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.modrinth.com/v2/project/SjN2N7xv/version?include_changelog=false&game_versions=" + filter);
        long period = Math.round(plugin.getConfig().getDouble("updates.check-interval-hours") * 72000);
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "AstralicaStudio/XmasLights/" + installed + " (https://modrinth.com/plugin/xmas-lights)").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String body;
                try (var input = response.body()) {
                    if (response.statusCode() != 200) throw new java.io.IOException("HTTP " + response.statusCode());
                    byte[] bytes = input.readNBytes(1048577);
                    if (bytes.length > 1048576) throw new java.io.IOException("Response exceeds 1 MiB");
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
                Release result = select(body, installed, minecraft);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (epoch != generation) return;
                    available = result;
                    if (result != null && !result.id.equals(announced)) {
                        announced = result.id;
                        plugin.messages().info("updates.available", "version", result.version, "url", url(result));
                        if (notifyAdmins) Bukkit.getOnlinePlayers().forEach(this::notifyPlayer);
                    }
                });
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                // Failures are diagnostic only; a network outage never disables the lights.
                plugin.getLogger().fine("Modrinth update check: " + failure);
            }
        }, 20, period);
    }
    static Release select(String body, String installed, String minecraft) {
        Release best = null;
        for (JsonElement entry : JsonParser.parseString(body).getAsJsonArray()) {
            var value = entry.getAsJsonObject();
            if (!"release".equals(value.get("version_type").getAsString())
                    || !contains(value.getAsJsonArray("game_versions"), minecraft)
                    || !contains(value.getAsJsonArray("loaders"), "paper")) continue;
            String version = value.get("version_number").getAsString();
            String id = value.get("id").getAsString();
            if (!id.matches("[A-Za-z0-9]{1,64}")) continue;
            if (compare(version, installed) > 0 && (best == null || compare(version, best.version) > 0)) best = new Release(version, id);
        }
        return best;
    }
    private static boolean contains(JsonArray array, String value) {
        return array != null && array.asList().stream().anyMatch(item -> value.equals(item.getAsString()));
    }
    // Distribution suffixes (+mc.v.X) do not change the plugin version. Unknown version formats are ignored.
    static int compare(String a, String b) {
        String left = a.split("\\+", 2)[0], right = b.split("\\+", 2)[0];
        if (!left.matches("[0-9]+(?:\\.[0-9]+){1,3}") || !right.matches("[0-9]+(?:\\.[0-9]+){1,3}")) return 0;
        String[] x = left.split("\\."), y = right.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = new java.math.BigInteger(i < x.length ? x[i] : "0").compareTo(new java.math.BigInteger(i < y.length ? y[i] : "0"));
            if (c != 0) return c;
        }
        return 0;
    }
    private static String url(Release release) { return "https://modrinth.com/plugin/xmas-lights/version/" + release.id; }
    private void notifyPlayer(Player player) {
        if (available != null && player.isOnline() && player.hasPermission("xmaslights.admin"))
            plugin.messages().send(player, "updates.available", "version", available.version, "url", url(available));
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        long epoch = generation;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (epoch == generation && notifyAdmins) notifyPlayer(event.getPlayer()); }, 60);
    }
    @Override public void close() { generation++; if (task != null) task.cancel(); task = null; available = null; }
}
