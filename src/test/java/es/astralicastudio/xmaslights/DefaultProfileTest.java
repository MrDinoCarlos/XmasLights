package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class DefaultProfileTest {
    private XmasLightsPlugin plugin;
    @BeforeEach void start() {
        var server = MockBukkit.mock(new PluginTest.TestServer()); server.addSimpleWorld("world"); plugin = PluginTest.loadPlugin();
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private void reload() throws Exception {
        plugin.getConfig().save(new java.io.File(plugin.getDataFolder(), "config.yml")); plugin.reload();
    }
    @Test void freshInstallUsesRequestedDefaults() {
        assertTrue(plugin.getConfig().getBoolean("animated-light"));
        assertEquals(21, plugin.getConfig().getDouble("view-distance-chunks"));
        assertEquals(8192, plugin.getConfig().getInt("limits.max-entities"));
    }
    @Test void rejectedPortableSettingIsRemovedOnUpgradeWithoutChangingRequestedDefaults() throws Exception {
        plugin.getConfig().set("config-version",15);
        plugin.getConfig().set("strobelights.renderer","portable"); reload();
        assertFalse(plugin.getConfig().contains("strobelights.renderer"));
        assertEquals(16,plugin.getConfig().getInt("config-version")); freshInstallUsesRequestedDefaults();
    }
    @Test void oldBundledProfileIsMigratedAndLaterOverridesArePreserved() throws Exception {
        var config = plugin.getConfig(); config.set("config-version",14);
        config.set("animated-light",false); config.set("view-distance-chunks",6); config.set("limits.max-entities",4096); reload();
        freshInstallUsesRequestedDefaults(); assertEquals(16, plugin.getConfig().getInt("config-version"));
        config = plugin.getConfig(); config.set("animated-light",false); config.set("view-distance-chunks",12); config.set("limits.max-entities",6000); reload();
        assertFalse(plugin.getConfig().getBoolean("animated-light")); assertEquals(12,plugin.getConfig().getDouble("view-distance-chunks"));
        assertEquals(6000,plugin.getConfig().getInt("limits.max-entities"));
    }
}
