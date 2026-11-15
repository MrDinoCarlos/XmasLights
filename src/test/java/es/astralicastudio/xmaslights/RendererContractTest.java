package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RendererContractTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    @BeforeEach void start() {
        server = MockBukkit.mock(new PluginTest.TestServer()); world = server.addSimpleWorld("world");
        world.loadChunk(0, 0); plugin = PluginTest.loadPlugin();
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    @Test void withoutStrobeLightsItNeverInventsSurfaceGeometry() throws Exception {
        var player=server.addPlayer(); player.teleport(new Location(world,2,74,2));
        world.getBlockAt(2,70,2).setType(Material.STONE);
        var source=new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(UUID.randomUUID(),0),new Location(world,2.5,74,2.5),0xFF0000,12);
        try(var bridge=new StrobeLightsBridge(plugin)) {
            var config=ResourcesTest.resource("config.yml");
            config.set("strobelights.renderer","portable"); // Retired config must never resurrect the rejected renderer.
            bridge.configure(config); bridge.sync(List.of(source));
            assertTrue(world.getEntitiesByClass(org.bukkit.entity.Display.class).isEmpty());
            assertFalse(bridge.suppressesNativeLight());
        }
    }
    @Test void migrationConvertsUnitsPreservesCustomValuesAndRemovesUnusedSettings() throws Exception {
        var current = ResourcesTest.resource("config.yml"); var defaults = ResourcesTest.resource("config.yml");
        current.set("config-version", 13); current.set("view-range", 2.5);
        current.set("strobelights.view-distance", 96); current.set("strobelights.expansion", .75);
        assertTrue(ResourceUpdater.migrateVisibility(current, defaults));
        assertEquals(10, current.getDouble("view-distance-chunks"));
        assertEquals(6, current.getDouble("strobelights.view-distance-chunks"));
        for (String key : List.of("view-range", "strobelights.view-distance", "strobelights.expansion")) assertFalse(current.contains(key));
        assertFalse(ResourceUpdater.migrateVisibility(current, defaults));
    }
}
