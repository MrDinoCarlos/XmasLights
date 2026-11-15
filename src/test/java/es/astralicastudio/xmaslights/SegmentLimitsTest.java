package es.astralicastudio.xmaslights;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SegmentLimitsTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private WorldMock world;
    private PlayerMock player;

    @BeforeEach void start() {
        server = MockBukkit.mock(new PluginTest.TestServer());
        world = server.addSimpleWorld("world");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x, z);
        plugin = PluginTest.loadPlugin();
        player = server.addPlayer();
        player.teleport(new Location(world, 1, 70, 1));
    }

    @AfterEach void stop() { PluginTest.stopServer(); }

    private void reload() throws Exception {
        plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile());
        plugin.reload();
    }

    private LightString create(PlayerMock owner, boolean skipStart) {
        Vector a = new Vector(1, 70, 1), b = new Vector(1.5, 70, 1);
        return plugin.manager().create(owner, a, b, a.toBlockVector(), b.toBlockVector(),
                plugin.manager().getType("wall-inline-warm"), new Vector(0, 0, 1), null, skipStart, null);
    }

    private LightString imported(UUID owner) {
        var string = new LightString(UUID.randomUUID(), "world", new Vector(1, 70, 1), new Vector(1.5, 70, 1),
                new Vector(0, 0, 1), new Vector(1, 70, 1).toBlockVector(), new Vector(1.5, 70, 1).toBlockVector(),
                0, "wall-inline-warm", AnimationMode.STEADY);
        string.owner = owner;
        return string;
    }

    @Test void defaultBudgetAllowsOnePlayerPastBothOldSegmentCaps() {
        for (int i = 0; i < 257; i++) create(player, false);
        assertEquals(257, plugin.manager().getStrings().size());
        assertEquals(771, world.getEntitiesByClass(Display.class).size());
        assertEquals(771, plugin.manager().geometryEntities());
        var saved = assertDoesNotThrow(() -> SafeYaml.load(plugin.getDataFolder().toPath().resolve("lights.yml")));
        assertEquals(257, saved.getConfigurationSection("lights").getKeys(false).size());
    }

    @Test void configuredPlayerCapCountsHiddenSegmentsAndReportsTheRightCause() throws Exception {
        plugin.getConfig().set("limits.max-strings-per-player", 1);
        reload();
        plugin.manager().despawn(create(player, false));
        var failure = assertThrows(OperationFailure.class, () -> create(player, false));
        assertEquals("errors.limit-player-strings", failure.getMessage());
        assertEquals("errors.limit-player-strings", plugin.manager().importLimitError(List.of(imported(player.getUniqueId()))));
        var other = server.addPlayer();
        other.teleport(player.getLocation());
        assertDoesNotThrow(() -> create(other, false));
        assertEquals(2, plugin.manager().getStrings().size());
    }

    @Test void configuredGlobalCapReportsTheRightCauseForPlacementAndImport() throws Exception {
        plugin.getConfig().set("limits.max-strings", 1);
        reload();
        create(player, false);
        var failure = assertThrows(OperationFailure.class, () -> create(player, false));
        assertEquals("errors.limit-strings", failure.getMessage());
        assertEquals("errors.limit-strings", plugin.manager().importLimitError(List.of(imported(UUID.randomUUID()))));
        assertEquals(1, plugin.manager().getStrings().size());
    }

    @Test void disabledCountCapsAlsoAllowLargeImportBatches() {
        var imported = java.util.stream.IntStream.range(0, 257).mapToObj(i -> imported(player.getUniqueId())).toList();
        assertNull(plugin.manager().importLimitError(imported));
        imported.forEach(plugin.manager()::insertImported);
        assertEquals(257, plugin.manager().getStrings().size());
        assertEquals(771, plugin.manager().geometryEntities());
    }

    @Test void configuredPlayerCapValidatesTheWholeImportBatch() throws Exception {
        plugin.getConfig().set("limits.max-strings-per-player", 1);
        reload();
        assertEquals("errors.limit-player-strings", plugin.manager().importLimitError(
                List.of(imported(player.getUniqueId()), imported(player.getUniqueId()))));
        assertTrue(plugin.manager().getStrings().isEmpty());
    }

    @Test void skippedJunctionBulbsDoNotConsumeTheEntityBudget() throws Exception {
        plugin.getConfig().set("limits.max-entities", 64);
        reload();
        for (int i = 0; i < 32; i++) create(player, true);
        assertEquals(64, world.getEntitiesByClass(Display.class).size());
        assertEquals(64, plugin.manager().geometryEntities());
        plugin.manager().enforceEntityBudget();
        assertTrue(plugin.manager().getStrings().stream().allMatch(string -> string.spawned));
        var failure = assertThrows(OperationFailure.class, () -> create(player, true));
        assertEquals("errors.limit-entities", failure.getMessage());
        assertEquals("errors.limit-entities", plugin.manager().importLimitError(List.of(imported(player.getUniqueId()))));
        assertEquals(32, plugin.manager().getStrings().size());
    }

    @Test void oldDefaultCountCapsMigrateWithABackupAndKeepExistingLights() throws Exception {
        var string = create(player, false);
        plugin.getConfig().set("config-version", 11);
        plugin.getConfig().set("limits.max-strings", 256);
        plugin.getConfig().set("limits.max-strings-per-player", 64);
        reload();
        assertEquals(0, plugin.getConfig().getInt("limits.max-strings"));
        assertEquals(0, plugin.getConfig().getInt("limits.max-strings-per-player"));
        assertEquals(8192, plugin.getConfig().getInt("limits.max-entities"));
        assertSame(string, plugin.manager().byId(string.id));
        try (var files = Files.list(plugin.getDataFolder().toPath())) {
            var backups = files.filter(path -> path.getFileName().toString().startsWith("config.yml.") && path.toString().endsWith(".bak")).toList();
            assertTrue(backups.stream().anyMatch(path -> {
                try {
                    var backup = SafeYaml.load(path);
                    return backup.getInt("limits.max-strings") == 256 && backup.getInt("limits.max-strings-per-player") == 64;
                } catch (Exception failure) { throw new AssertionError(failure); }
            }));
        }
    }

    @Test void migrationKeepsCustomCapsAndDoesNotRepeatForCurrentConfigs() throws Exception {
        plugin.getConfig().set("config-version", 11);
        plugin.getConfig().set("limits.max-strings", 12);
        plugin.getConfig().set("limits.max-strings-per-player", 3);
        reload();
        assertEquals(12, plugin.getConfig().getInt("limits.max-strings"));
        assertEquals(3, plugin.getConfig().getInt("limits.max-strings-per-player"));
        plugin.getConfig().set("limits.max-strings", 256);
        plugin.getConfig().set("limits.max-strings-per-player", 64);
        reload();
        assertEquals(256, plugin.getConfig().getInt("limits.max-strings"));
        assertEquals(64, plugin.getConfig().getInt("limits.max-strings-per-player"));
    }
}
