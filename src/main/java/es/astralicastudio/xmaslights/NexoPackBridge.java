package es.astralicastudio.xmaslights;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Nexo's documented external-pack input and public generator/delivery API. */
final class NexoPackBridge implements AutoCloseable {
    static final String INPUT_NAME = "XmasLights-Models.zip";
    record Installation(Path path, boolean changed) { }
    private final XmasLightsPlugin plugin;
    private final Plugin nexo;
    private final Runnable published;
    private BukkitTask task;
    private boolean closed, generated;
    private int publicationAttempts;

    NexoPackBridge(XmasLightsPlugin plugin, Plugin nexo, Runnable published) {
        this.plugin = plugin; this.nexo = nexo; this.published = published;
    }

    /** Safe to call during onLoad: no Nexo runtime API and no pack generation. */
    static Installation install(Plugin nexo, byte[] pack) throws IOException {
        return install(nexo.getDataFolder().toPath(), pack);
    }
    static Installation install(Path dataFolder, byte[] pack) throws IOException {
        Files.createDirectories(dataFolder);
        Path root = dataFolder.toRealPath();
        Path directory = root.resolve("pack/external_packs");
        // Check existing ancestors before creating anything through a symlink.
        for (Path path : new Path[]{root.resolve("pack"), directory}) {
            if (Files.exists(path) && !path.toRealPath().startsWith(root)) throw new IOException("Nexo external-pack path escapes its data folder");
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(INPUT_NAME);
        if (Files.isSymbolicLink(target)) throw new IOException("Nexo XmasLights input must not be a symbolic link");
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), pack)) return new Installation(target, false);
        Path temporary = Files.createTempFile(directory, ".xmaslights-", ".tmp");
        try {
            Files.write(temporary, pack);
            if (Files.isRegularFile(target)) Files.copy(target, target.resolveSibling(INPUT_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        return new Installation(target, true);
    }
    static boolean removeManaged(Plugin nexo) throws IOException {
        Path directory = nexo.getDataFolder().toPath();
        if (!Files.isDirectory(directory)) return false;
        Path root = directory.toRealPath(), target = root.resolve("pack/external_packs/" + INPUT_NAME);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!target.toRealPath().startsWith(root)) throw new IOException("Nexo external-pack path escapes its data folder");
        try (var zip = new java.util.zip.ZipFile(target.toFile())) {
            if (zip.getEntry("assets/xmaslights/items/garland/lights.json") == null || zip.getEntry("CREDITS.txt") == null) return false;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("assets/xmaslights/") && !java.util.Set.of("pack.mcmeta", "pack.png", "CREDITS.txt").contains(name)) return false;
            }
        }
        Files.copy(target, target.resolveSibling(INPUT_NAME + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        return Files.deleteIfExists(target);
    }

    void start(boolean changedAfterStartup, boolean autoRebuild) {
        if (changedAfterStartup && autoRebuild) whenIdle(this::rebuild);
        else if (changedAfterStartup) {
            try { waitForExternalBuild(generation()); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { fail(failure); }
        }
        else whenIdle(() -> { generated = true; watchPublication(); });
    }
    private void waitForExternalBuild(CompletableFuture<?> previous) {
        if (!active()) return;
        try {
            if (generation() != previous) { whenIdle(() -> { generated = true; watchPublication(); }); return; }
            task = Bukkit.getScheduler().runTaskLater(plugin, () -> waitForExternalBuild(previous), 20);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { fail(failure); }
    }
    private CompletableFuture<?> generation() throws ReflectiveOperationException {
        Object generator = nexo.getClass().getMethod("packGenerator").invoke(nexo);
        if (generator == null) return null;
        Object value = generator.getClass().getMethod("getPackGenFuture").invoke(generator);
        return value instanceof CompletableFuture<?> future ? future : null;
    }
    private void whenIdle(Runnable action) {
        if (!active()) return;
        try {
            CompletableFuture<?> future = generation();
            if (future != null && !future.isDone()) {
                future.whenComplete((ignored, failure) -> onMain(() -> {
                    if (failure != null) fail(failure); else whenIdle(action);
                }));
            } else if (future != null && future.isCompletedExceptionally()) {
                plugin.messages().warn("log.model-pack-nexo-generation-failed");
            } else action.run();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            // Older Nexo builds still support the documented focused reload command.
            if (!generated) action.run(); else fail(failure);
        }
    }
    private void rebuild() {
        if (!active()) return;
        try {
            Object generator = nexo.getClass().getMethod("packGenerator").invoke(nexo);
            generator.getClass().getMethod("regeneratePack").invoke(generator);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "nexo reload pack")) { fail(failure); return; }
        }
        plugin.messages().info("log.model-pack-nexo-rebuild");
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> whenIdle(() -> { generated = true; watchPublication(); }), 1);
    }
    private void watchPublication() {
        if (!active()) return;
        if (packId() != null) { published.run(); return; }
        if (++publicationAttempts == 120) plugin.messages().warn("log.model-pack-nexo-publication-pending");
        task = Bukkit.getScheduler().runTaskLater(plugin, this::watchPublication, publicationAttempts < 120 ? 20 : 100);
    }
    UUID packId() {
        if (!active() || !generated) return null;
        try {
            Object server = nexo.getClass().getMethod("packServer").invoke(nexo);
            if (server == null) return null;
            try { if (Boolean.FALSE.equals(server.getClass().getMethod("isPackUploaded").invoke(server))) return null; }
            catch (NoSuchMethodException ignored) { }
            Object info = server.getClass().getMethod("packInfo").invoke(server);
            if (info instanceof net.kyori.adventure.resource.ResourcePackInfo pack) return pack.id();
            Object id = info == null ? null : info.getClass().getMethod("id").invoke(info);
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return null; }
    }
    boolean send(Player player) {
        if (packId() == null) return false;
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoPack", true, nexo.getClass().getClassLoader());
            api.getMethod("sendPack", Player.class).invoke(null, player);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            try {
                Object server = nexo.getClass().getMethod("packServer").invoke(nexo);
                server.getClass().getMethod("sendPack", Player.class).invoke(server, player);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { fail(failure); return false; }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { fail(failure); return false; }
    }
    private boolean active() { return !closed && plugin.isEnabled() && nexo.isEnabled(); }
    private void onMain(Runnable action) { if (active()) Bukkit.getScheduler().runTask(plugin, () -> { if (active()) action.run(); }); }
    private void fail(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getTargetException() : failure;
        plugin.messages().warn("log.model-pack-unavailable", "error", String.valueOf(cause.getMessage()));
    }
    @Override public void close() { closed = true; if (task != null) task.cancel(); }
}
