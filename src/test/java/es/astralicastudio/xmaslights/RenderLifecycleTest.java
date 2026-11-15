package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RenderLifecycleTest {
    PluginTest.TestServer server;
    XmasLightsPlugin plugin;
    org.mockbukkit.mockbukkit.world.WorldMock world;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;

    @BeforeEach void start() {
        server = MockBukkit.mock(new PluginTest.TestServer()); world = server.addSimpleWorld("world");
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x, z);
        plugin = PluginTest.loadPlugin(); player = server.addPlayer();
        player.teleport(new Location(world, 2, 74, 6));
    }
    @AfterEach void stop() { PluginTest.stopServer(); }

    LightString create(String type, int end) {
        var a = new Vector(2, 74, 6); var b = new Vector(end, 74, 6);
        return plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(),
                plugin.manager().getType(type), new Vector(0, 0, 1), null, false, "Winter");
    }
    List<Entity> parts(LightString string) {
        var result = new ArrayList<Entity>(string.wires); result.addAll(string.bulbs.values()); return result;
    }
    void unloadGeometry(int x, int z) {
        var chunk = world.getChunkAt(x, z);
        var parts = world.getEntitiesByClass(Display.class).stream().filter(e -> e.getLocation().getChunk().equals(chunk)).map(e -> (Entity)e).toList();
        server.getPluginManager().callEvent(new ChunkUnloadEvent(chunk));
        server.getPluginManager().callEvent(new EntitiesUnloadEvent(chunk, parts));
        // MockBukkit does not implement entity chunk storage; emulate nonpersistent entity eviction.
        parts.forEach(Entity::remove); world.unloadChunk(x, z);
    }

    @Test void everyBuiltInRendererUsesOnlyTemporaryDisplays() {
        for (String type : plugin.manager().getTypes().keySet()) {
            var string = create(type, 8);
            assertFalse(parts(string).isEmpty(), type);
            assertTrue(parts(string).stream().noneMatch(Entity::isPersistent), type);
            assertTrue(plugin.manager().remove(string));
        }
    }

    @Test void disableRemovesGeometryOrphansAndOwnedLightButPreservesOtherDecorations() throws Exception {
        var string = create("warm", 8); var positions = List.copyOf(string.lightBlocks);
        var foreign = world.spawn(new Location(world, 9, 74, 8), BlockDisplay.class);
        var adminLight = world.getBlockAt(9, 73, 8); adminLight.setType(Material.LIGHT);
        var orphan = world.spawn(new Location(world, 8, 74, 8), BlockDisplay.class);
        orphan.getPersistentDataContainer().set(new NamespacedKey(plugin, "string"), PersistentDataType.STRING, UUID.randomUUID().toString());
        var legacy = world.spawn(new Location(world, 7, 74, 8), BlockDisplay.class);
        legacy.getPersistentDataContainer().set(NamespacedKey.fromString("lucesnavidad:guirnalda"), PersistentDataType.STRING, "old");
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(List.of(foreign), world.getEntitiesByClass(Display.class));
        assertEquals(Material.LIGHT, adminLight.getType());
        for (var pos : positions) assertTrue(world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()).getType().isAir());
        var yaml = SafeYaml.load(plugin.getDataFolder().toPath().resolve("lights.yml"));
        assertTrue(yaml.getStringList("owned-light-blocks").isEmpty());
        assertTrue(yaml.getBoolean("lights." + string.id + ".spawned"));
        assertEquals("Winter", yaml.getString("lights." + string.id + ".group"));
        assertEquals(player.getUniqueId().toString(), yaml.getString("lights." + string.id + ".owner"));
    }

    @Test void savedSegmentsRebuildAfterRestartWithoutDuplicatingAlreadyLoadedParts() throws Exception {
        var string = create("wall-garland-rgb", 8); int expected = parts(string).size();
        var oldParts = parts(string); var id = string.id;
        string.mode = AnimationMode.CHASE; string.on = false; plugin.manager().saveData();
        oldParts.forEach(Entity::remove); plugin.manager().loadData();
        plugin.manager().scanLoadedEntities(); plugin.manager().scheduleAutoUpdate(); server.getScheduler().performTicks(5);
        var restored = plugin.manager().byId(id);
        assertEquals(expected, parts(restored).size()); assertEquals(expected, world.getEntitiesByClass(Display.class).size());
        assertEquals(AnimationMode.CHASE, restored.mode); assertFalse(restored.on);
        var ids = parts(restored).stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
        plugin.manager().onChunkLoad(); plugin.manager().onChunkLoad(); server.getScheduler().performTicks(5);
        assertEquals(ids, parts(restored).stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void returningChunksRestoreOnlyTheirMissingPartsAndNeverForceOtherChunksToLoad() {
        var string = create("warm", 30); int expected = parts(string).size();
        unloadGeometry(1, 0); assertFalse(world.isChunkLoaded(1, 0));
        var retained = parts(string).stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
        assertFalse(retained.isEmpty()); assertTrue(retained.size() < expected);
        plugin.manager().onChunkLoad(); server.getScheduler().performTicks(5);
        assertFalse(world.isChunkLoaded(1, 0)); assertEquals(retained.size(), parts(string).size());
        world.loadChunk(1, 0); server.getPluginManager().callEvent(new ChunkLoadEvent(world.getChunkAt(1, 0), false)); server.getScheduler().performTicks(5);
        assertEquals(expected, parts(string).size());
        assertTrue(parts(string).stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet()).containsAll(retained));
    }

    @Test void unloadedLightBlockOwnershipSurvivesUntilPluginShutdown() throws Exception {
        var string = create("warm", 8); var positions = List.copyOf(string.lightBlocks);
        unloadGeometry(0, 0); plugin.manager().saveData(); server.getScheduler().performTicks(20);
        assertFalse(world.isChunkLoaded(0, 0));
        assertFalse(SafeYaml.load(plugin.getDataFolder().toPath().resolve("lights.yml")).getStringList("owned-light-blocks").isEmpty());
        server.getPluginManager().disablePlugin(plugin);
        for (var pos : positions) assertTrue(world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()).getType().isAir());
    }

    @Test void hiddenSegmentsRemainHiddenWhenChunksReturn() throws Exception {
        var string = create("warm", 8); plugin.manager().despawn(string); plugin.manager().saveData();
        plugin.manager().loadData(); plugin.manager().onChunkLoad(); server.getScheduler().performTicks(5);
        assertTrue(world.getEntitiesByClass(Display.class).isEmpty()); assertFalse(plugin.manager().byId(string.id).spawned);
    }

    @Test void oldLoadedDisplaysBecomeTemporaryAndUnknownSegmentsAreRemoved() {
        var string = create("warm", 8); parts(string).forEach(e -> e.setPersistent(true));
        var orphan = world.spawn(new Location(world, 8, 74, 8), BlockDisplay.class);
        orphan.getPersistentDataContainer().set(new NamespacedKey(plugin, "string"), PersistentDataType.STRING, UUID.randomUUID().toString());
        plugin.manager().scanLoadedEntities(); server.getScheduler().performTicks(1);
        assertTrue(parts(string).stream().noneMatch(Entity::isPersistent)); assertFalse(orphan.isValid());
    }

    @Test void shutdownCleansLegacyUnloadedLightsAndDoesNotOverwriteAChangedBlock() {
        var string = create("warm", 8); var positions = List.copyOf(string.lightBlocks);
        var changed = positions.getFirst(); world.getBlockAt(changed.getBlockX(), changed.getBlockY(), changed.getBlockZ()).setType(Material.STONE);
        world.unloadChunk(0, 0); // Simulate ownership inherited from an older version, without the new unload listener.
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(Material.STONE, world.getBlockAt(changed.getBlockX(), changed.getBlockY(), changed.getBlockZ()).getType());
        for (var pos : positions.subList(1, positions.size())) assertTrue(world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()).getType().isAir());
    }

    @Test void legacyChunkCleanupStaysPendingUntilShutdownEvenIfTheSegmentWasDeleted() throws Exception {
        var string = create("warm", 30);
        var file = plugin.getDataFolder().toPath().resolve("lights.yml");
        var yaml = SafeYaml.load(file); yaml.set("render-storage", null); SafeYaml.save(yaml, file, false);
        plugin.manager().loadData(); plugin.manager().scanLoadedEntities();
        var orphan = world.spawn(new Location(world, 24, 74, 6), BlockDisplay.class);
        orphan.getPersistentDataContainer().set(new NamespacedKey(plugin, "string"), PersistentDataType.STRING, string.id.toString());
        assertTrue(plugin.manager().remove(plugin.manager().byId(string.id)));
        assertNull(SafeYaml.load(file).getString("render-storage"));
        server.getPluginManager().disablePlugin(plugin);
        assertFalse(orphan.isValid());
        assertEquals("ephemeral-v1", SafeYaml.load(file).getString("render-storage"));
    }

    @Test void cancelledWorldUnloadDoesNotStripItsLighting() {
        var string = create("warm", 8); var pos = string.lightBlocks.getFirst();
        var event = new WorldUnloadEvent(world); event.setCancelled(true); server.getPluginManager().callEvent(event);
        assertEquals(Material.LIGHT, world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ()).getType());
    }
}
