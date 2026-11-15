package lifecycle;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real Paper inspector, deliberately loadable without XmasLights. */
public class AbsenceSmoke extends JavaPlugin {
    protected World world;
    protected final NamespacedKey segment = Objects.requireNonNull(NamespacedKey.fromString("xmaslights:string"));
    protected Properties expected = new Properties();
    @Override public void onEnable() { later(40, () -> {
        world = Bukkit.getWorlds().getFirst();
        try (var reader = Files.newBufferedReader(Path.of("expected.properties"))) { expected.load(reader); }
        loadArea(); later(20, () -> { verifyAbsent(); passed("absent"); });
    }); }
    protected void loadArea() {
        for (int x = 199; x <= 202; x++) for (int z = 199; z <= 201; z++) world.loadChunk(x, z);
    }
    protected List<Display> geometry() {
        return world.getEntitiesByClass(Display.class).stream().filter(e -> e.getPersistentDataContainer().has(segment)).toList();
    }
    protected void verifyAbsent() {
        check(geometry().isEmpty(), "Remaining XmasLights displays: " + geometry().size());
        for (String pos : expected.getProperty("lights").split("\\|")) if (!pos.isBlank()) {
            String[] parts = pos.split("=")[0].split(";");
            check(world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])).getType().isAir(), "Remaining native LIGHT: " + pos);
        }
        check(world.getBlockAt(3209, 100, 3209).getType() == Material.LIGHT, "Foreign LIGHT removed");
        check(world.getEntitiesByClass(BlockDisplay.class).stream().anyMatch(e -> e.getPersistentDataContainer().has(NamespacedKey.fromString("lifecyclesmoke:foreign"))), "Foreign display removed");
    }
    protected void later(long ticks, Checked action) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); }
            catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "LIFECYCLE FAILED", failure);
                try { Files.writeString(Path.of("failed.txt"), failure.toString()); } catch (IOException ignored) { }
                Bukkit.shutdown();
            }
        }, ticks);
    }
    protected void passed(String stage) throws IOException {
        Files.writeString(Path.of(stage + "-passed.txt"), "Paper " + Bukkit.getMinecraftVersion() + " lifecycle PASS\n");
        getLogger().info("LIFECYCLE PASS: " + stage); Bukkit.shutdown();
    }
    protected static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @FunctionalInterface protected interface Checked { void run() throws Exception; }
}
