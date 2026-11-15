package es.astralicastudio.xmaslights;

import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ModelPackDeliveryTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    @BeforeEach void start() { server=MockBukkit.mock(new PluginTest.TestServer()); server.addSimpleWorld("world"); }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private void load() { plugin=PluginTest.loadPlugin(); assertTrue(plugin.isEnabled()); }
    @SuppressWarnings("deprecation") private NexoHostFixture nexo() {
        return MockBukkit.loadWith(NexoHostFixture.class,new PluginDescriptionFile("Nexo","1.27.2",NexoHostFixture.class.getName()));
    }
    private PluginTest.PackPlayer player() { return (PluginTest.PackPlayer)server.addPlayer(); }
    private byte[] pack() throws Exception { try(var input=plugin.getResource("packs/"+ModelPackService.FILE_NAME)) { return input.readAllBytes(); } }
    private void saveSettings() throws Exception { plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload(); }
    private void status(PluginTest.PackPlayer player,UUID id,PlayerResourcePackStatusEvent.Status status) {
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,id,status));
    }
    @Test void standalonePlayersAutomaticallyReceiveADownloadablePackWithTheCorrectHash() throws Exception {
        load(); var player=player(); server.getScheduler().performTicks(45);
        assertEquals(1,player.packs.size()); var request=player.packs.getFirst(); assertEquals(ModelPackService.PACK_ID,request.id());
        try(var client=HttpClient.newHttpClient()) {
            var response=client.send(HttpRequest.newBuilder(URI.create(request.url())).timeout(Duration.ofSeconds(5)).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,response.statusCode()); assertArrayEquals(pack(),response.body());
            assertArrayEquals(MessageDigest.getInstance("SHA-1").digest(response.body()),request.hash());
        }
        status(player,request.id(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        assertTrue(plugin.modelPackReady(player)); server.getScheduler().performTicks(45); assertEquals(1,player.packs.size());
    }
    @Test void advertisedHostUsesThePlayersLoginAddressAndSupportsAPublicOverride() throws Exception {
        load(); var player=player(); player.virtualHost=InetSocketAddress.createUnresolved("play.example.test",25565);
        server.getScheduler().performTicks(45); assertTrue(player.packs.getFirst().url().startsWith("http://play.example.test:"+plugin.getConfig().getInt("model-pack.embedded.port")+"/"));
        plugin.getConfig().set("model-pack.embedded.public-url","https://packs.example.test/minecraft"); saveSettings();
        plugin.requestModelPack(player); assertTrue(player.packs.getLast().url().startsWith("https://packs.example.test/minecraft/xmaslights/"));
    }
    @Test void autoSendCanBeDisabledWithoutDisablingManualRequests() throws Exception {
        load(); plugin.getConfig().set("model-pack.auto-send",false); saveSettings(); var player=player();
        server.getScheduler().performTicks(45); assertTrue(player.packs.isEmpty()); plugin.requestModelPack(player); assertEquals(1,player.packs.size());
    }
    @Test void explicitHostingDoesNotKeepAnEmbeddedListenerRunning() throws Exception {
        load(); plugin.getConfig().set("model-pack.url","https://cdn.example.test/models.zip"); saveSettings();
        try(var socket=new java.net.ServerSocket()) { socket.setReuseAddress(true); socket.bind(new InetSocketAddress("0.0.0.0",plugin.getConfig().getInt("model-pack.embedded.port"))); }
        var player=player(); server.getScheduler().performTicks(45); assertEquals("https://cdn.example.test/models.zip",player.packs.getFirst().url());
    }
    @Test void earlyPreparationMakesTheInputAvailableBeforeNexoEnableWithoutRuntimeCalls() throws Exception {
        var nexo=nexo(); server.getPluginManager().disablePlugin(nexo); int calls=nexo.runtimeCalls;
        load(); assertEquals(calls,nexo.runtimeCalls);
        assertArrayEquals(pack(),Files.readAllBytes(nexo.getDataFolder().toPath().resolve("pack/external_packs/"+NexoPackBridge.INPUT_NAME)));
        server.getPluginManager().enablePlugin(nexo); assertArrayEquals(pack(),nexo.inputAtEnable);
        var player=player(); server.getScheduler().performTicks(45);
        assertEquals(0,nexo.generator.rebuilds); assertEquals(List.of(player.getUniqueId()),nexo.server.sent); assertTrue(player.packs.isEmpty());
    }
    @Test void directNexoDeliveryWorksWithoutTrpAndOnlyAcceptsItsOwnPackStatus() throws Exception {
        var nexo=nexo(); load(); var player=player(); server.getScheduler().performTicks(45);
        assertEquals(1,nexo.generator.rebuilds); assertArrayEquals(pack(),nexo.generator.rebuiltInput);
        assertTrue(player.packs.isEmpty()); assertEquals(List.of(player.getUniqueId()),nexo.server.sent);
        status(player,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED); assertFalse(plugin.modelPackReady(player));
        status(player,nexo.server.id,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED); assertTrue(plugin.modelPackReady(player));
        status(player,ModelPackService.PACK_ID,PlayerResourcePackStatusEvent.Status.DISCARDED); assertTrue(plugin.modelPackReady(player));
        status(player,nexo.server.id,PlayerResourcePackStatusEvent.Status.DISCARDED); assertFalse(plugin.modelPackReady(player));
        plugin.reload(); server.getScheduler().performTicks(3); assertEquals(1,nexo.generator.rebuilds);
    }
    @Test void changesWaitForTheCurrentNexoGenerationAndAQueuedRequestIsSentAfterUpload() throws Exception {
        var nexo=nexo(); nexo.generator.future=new CompletableFuture<>(); nexo.generator.delayed=true; nexo.server.uploaded=false;
        load(); var player=player(); plugin.requestModelPack(player); server.getScheduler().performTicks(3);
        assertEquals(0,nexo.generator.rebuilds); assertTrue(nexo.server.sent.isEmpty());
        nexo.generator.future.complete(null); server.getScheduler().performTicks(3); assertEquals(1,nexo.generator.rebuilds);
        assertTrue(nexo.server.sent.isEmpty()); nexo.generator.future.complete(null); server.getScheduler().performTicks(3);
        assertTrue(nexo.server.sent.isEmpty()); server.getScheduler().performTicks(2405);
        assertTrue(nexo.server.sent.isEmpty()); nexo.server.uploaded=true; server.getScheduler().performTicks(102);
        assertEquals(List.of(player.getUniqueId()),nexo.server.sent); assertTrue(player.packs.isEmpty());
    }
    @Test void disablingXmasLightsPreventsLateGenerationCallbacksFromRebuildingNexo() {
        var nexo=nexo(); nexo.generator.future=new CompletableFuture<>(); load();
        server.getPluginManager().disablePlugin(plugin); nexo.generator.future.complete(null); server.getScheduler().performTicks(3);
        assertEquals(0,nexo.generator.rebuilds);
    }
    @Test void vanillaModeRemovesOnlyTheManagedNexoInputAndNeverRequestsItsPack() throws Exception {
        var nexo=nexo(); load(); var player=player(); server.getScheduler().performTicks(5);
        Path managed=nexo.getDataFolder().toPath().resolve("pack/external_packs/"+NexoPackBridge.INPUT_NAME);
        Path other=managed.resolveSibling("Other.zip"); Files.writeString(other,"other pack"); assertTrue(Files.exists(managed));
        plugin.getConfig().set("model-pack.enabled",false); saveSettings(); server.getScheduler().performTicks(5);
        assertFalse(Files.exists(managed)); assertTrue(Files.exists(managed.resolveSibling(NexoPackBridge.INPUT_NAME+".bak")));
        assertEquals("other pack",Files.readString(other)); assertEquals(2,nexo.generator.rebuilds);
        int sent=nexo.server.sent.size(); plugin.requestModelPack(player); server.getScheduler().performTicks(45);
        assertEquals(sent,nexo.server.sent.size()); assertFalse(plugin.modelPackReady(player));
    }
    @Test void aReloadWhileWaitingDoesNotLoseThePendingNexoRebuild() throws Exception {
        var nexo=nexo(); nexo.generator.future=new CompletableFuture<>(); load();
        plugin.reload(); var player=player(); assertEquals(0,nexo.generator.rebuilds);
        nexo.generator.future.complete(null); server.getScheduler().performTicks(5);
        assertEquals(1,nexo.generator.rebuilds); assertEquals(List.of(player.getUniqueId()),nexo.server.sent);
    }
    @Test void disablingAutomaticRebuildWaitsForAnAdministratorsNextGeneration() throws Exception {
        var nexo=nexo(); nexo.generator.future=new CompletableFuture<>(); load();
        plugin.getConfig().set("model-pack.nexo.auto-rebuild",false); saveSettings(); var player=player();
        nexo.generator.future.complete(null); server.getScheduler().performTicks(45);
        assertEquals(0,nexo.generator.rebuilds); assertTrue(nexo.server.sent.isEmpty());
        nexo.generator.regeneratePack(); server.getScheduler().performTicks(22);
        assertEquals(1,nexo.generator.rebuilds); assertEquals(List.of(player.getUniqueId()),nexo.server.sent);
    }
    @Test void nexoTakesPriorityOverTrpAndDoesNotRegisterTheSameAssetsTwice() {
        var nexo=nexo(); TrpPackFixture.address="https://example.test/combined.zip";
        @SuppressWarnings("deprecation") var trp=MockBukkit.loadWith(TrpPackFixture.class,new PluginDescriptionFile("TRPServerEdition","test",TrpPackFixture.class.getName()));
        load(); var player=player(); server.getScheduler().performTicks(45);
        assertEquals(0,trp.registrations); assertEquals(1,nexo.generator.rebuilds); assertEquals(List.of(player.getUniqueId()),nexo.server.sent); assertTrue(player.packs.isEmpty());
    }
}
