package es.astralicastudio.xmaslights;

import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class UpdateNotificationTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private UpdateChecker checker;
    @BeforeEach void start() throws Exception {
        server = MockBukkit.mock(new PluginTest.TestServer()); server.addSimpleWorld("world");
        plugin = PluginTest.loadPlugin(); checker = new UpdateChecker(plugin); checker.configure();
        var field = UpdateChecker.class.getDeclaredField("available"); field.setAccessible(true);
        field.set(checker, new UpdateChecker.Release("2.10.0", "release1"));
    }
    @AfterEach void stop() { checker.close(); PluginTest.stopServer(); }
    private void join(org.bukkit.entity.Player player) {
        checker.join(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component)null));
    }
    @Test void loginNotifiesAdminsAndDoesNotNotifyOrdinaryPlayers() {
        var admin = server.addPlayer(); admin.setOp(true);
        var ordinary = server.addPlayer(); ordinary.setOp(false);
        join(admin); join(ordinary); server.getScheduler().performTicks(60);
        String notice = admin.nextMessage(); assertNotNull(notice);
        assertTrue(notice.contains("2.10.0")); assertTrue(notice.contains("https://modrinth.com/plugin/xmas-lights/version/release1"));
        assertNull(ordinary.nextMessage());
    }
    @Test void disablingCheckerInvalidatesAnAlreadyQueuedLoginNotification() {
        var admin = server.addPlayer(); admin.setOp(true); join(admin); checker.close();
        server.getScheduler().performTicks(60); assertNull(admin.nextMessage());
    }
}
