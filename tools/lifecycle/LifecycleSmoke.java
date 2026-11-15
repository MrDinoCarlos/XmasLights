package lifecycle;

import es.astralicastudio.xmaslights.XmasLightsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** Checks real chunk entity serialization, unload, restart and explicit plugin disable. */
public class LifecycleSmoke extends AbsenceSmoke {
    private XmasLightsPlugin lights;
    @Override public void onEnable() { later(40, () -> {
        world = Bukkit.getWorlds().getFirst(); lights = (XmasLightsPlugin)Bukkit.getPluginManager().getPlugin("XmasLights");
        check(lights != null && lights.isEnabled(), "XmasLights did not enable");
        loadArea();
        SurfaceSmoke.verify(lights, world);
        if (System.getProperty("xl.smoke.stage").equals("create")) create();
        else {
            try (var reader = Files.newBufferedReader(Path.of("expected.properties"))) { expected.load(reader); }
            later(60, () -> {
                verifyPresent(); Bukkit.getPluginManager().disablePlugin(lights); verifyAbsent(); passed("restore");
            });
        }
    }); }
    private void create() throws Exception {
        UUID owner = UUID.randomUUID();
        Player player = (Player)Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, method, args) -> switch (method.getName()) {
            case "getWorld" -> world; case "getUniqueId" -> owner; case "getGameMode" -> GameMode.CREATIVE;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        for (String type : List.of("warm", "wall-garland-rgb")) {
            int y = type.equals("warm") ? 90 : 94;
            var a = new Vector(3202, y, 3206); var b = new Vector(type.equals("warm") ? 3230 : 3210, y, 3206);
            lights.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), lights.manager().getType(type), new Vector(0, 0, 1), null, false, "Winter");
        }
        var foreign = world.spawn(new Location(world, 3209, 100, 3209), BlockDisplay.class);
        foreign.setBlock(Material.GOLD_BLOCK.createBlockData());
        foreign.getPersistentDataContainer().set(NamespacedKey.fromString("lifecyclesmoke:foreign"), PersistentDataType.BYTE, (byte)1);
        world.getBlockAt(3209, 100, 3209).setType(Material.LIGHT);
        later(20, () -> {
            lights.manager().saveData();
            var data = YamlConfiguration.loadConfiguration(new File(lights.getDataFolder(), "lights.yml"));
            expected.setProperty("count", String.valueOf(geometry().size()));
            expected.setProperty("lights", String.join("|", data.getStringList("owned-light-blocks")));
            check(!expected.getProperty("lights").isBlank(), "No native LIGHT to test");
            try (var writer = Files.newBufferedWriter(Path.of("expected.properties"))) { expected.store(writer, "Lifecycle smoke"); }
            verifyPresent(); world.save();
            // Remove neighbouring plugin tickets too: Paper's loadChunk ticket covers a radius.
            for (int x = 199; x <= 202; x++) for (int z = 199; z <= 201; z++) world.unloadChunkRequest(x, z);
            later(120, () -> {
                if (world.isChunkLoaded(201, 200)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "paper debug chunks");
                check(!world.isChunkLoaded(201, 200), "Chunk remained loaded after releasing test tickets");
                loadArea();
                later(40, () -> {
                    verifyPresent();
                    lights.manager().onChunkLoad(); later(10, () -> { verifyPresent(); world.save(); passed("create"); });
                });
            });
        });
    }
    private void verifyPresent() {
        check(geometry().size() == Integer.parseInt(expected.getProperty("count")), "Missing/duplicate displays: " + geometry().size() + " expected " + expected.getProperty("count"));
        check(geometry().stream().noneMatch(Entity::isPersistent), "A display is persistent");
        check(lights.manager().getStrings().size() == 2, "Logical segments lost");
    }
}
