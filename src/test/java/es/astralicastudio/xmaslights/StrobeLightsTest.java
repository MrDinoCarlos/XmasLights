package es.astralicastudio.xmaslights;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class StrobeLightsTest {
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.ServerMock server;
    private StrobeHostFixture host;
    private StrobeHostFixture.PackState state;
    @BeforeEach void start() {
        server = MockBukkit.mock(new PluginTest.TestServer()); server.addSimpleWorld("world");
        server.getWorld("world").loadChunk(0,0);
        plugin = PluginTest.loadPlugin();
    }
    @AfterEach void stop() { PluginTest.stopServer(); }

    static byte[] pack() throws Exception {
        String realJar = System.getProperty("strobelights.test.jar");
        if (realJar != null) try (var jar = new JarFile(realJar)) {
            try (var input = jar.getInputStream(Objects.requireNonNull(jar.getJarEntry("embedded/StrobeLights-ResourcePack-1.21.4.zip")))) { return input.readAllBytes(); }
        }
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("assets/strobelights/items/carrier/rgb.json"));
            zip.write("{\"model\":{\"model\":\"strobelights:item/strobe_rgb_carrier\",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":0}]}}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/strobelights/strobelights-integration.json"));
            zip.write("{\"stable_source_protocol\":\"slots3x3_depth_v1\"}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    @SuppressWarnings("deprecation")
    private void installHost() throws Exception {
        StrobeHostFixture.embeddedPack = pack();
        host = MockBukkit.loadWith(StrobeHostFixture.class, new PluginDescriptionFile("StrobeLights", "0.10.38", StrobeHostFixture.class.getName()));
        state = host.state;
    }
    private StrobeLightsBridge bridge(int perPlayer, int total) throws Exception {
        var config = ResourcesTest.resource("config.yml"); config.set("strobelights.renderer","shader"); config.set("strobelights.max-sources-per-player", perPlayer); config.set("strobelights.max-carriers-total", total);
        var bridge = new StrobeLightsBridge(plugin); bridge.configure(config); return bridge;
    }
    private StrobeLightsBridge.Source source(int index, double x, int level) {
        // A real impact target, three blocks below the lamp; no source is placed in air.
        server.getWorld("world").getBlockAt((int)Math.floor(x),66,2).setType(org.bukkit.Material.STONE);
        return new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(new UUID(0,1), index), new Location(server.getWorld("world"), x,70,2), 0xFF0000, level);
    }
    @Test void wireFormatMatchesTheInstalledStrobeLightsSourceEncoding() {
        assertEquals(0xA3FFF5, StrobeProtocol.encode(0xFFFFFF,15,1));
        assertEquals(0xAFF005, StrobeProtocol.encode(0xFF0000,15,4));
        assertEquals(0xA10F05, StrobeProtocol.encode(0x00FF00,15,.5));
        assertEquals(0xA000F5, StrobeProtocol.encode(0x0000FF,15,.25));
        assertEquals(0xA12575, StrobeProtocol.encode(0x336699,12,.5));
        assertEquals(0xA30005, StrobeProtocol.encode(0xFFFFFF,0,1));
    }
    @Test @SuppressWarnings("deprecation") void acceptsCurrentHostWithoutAnUntestedVersionOverride() throws Exception {
        StrobeHostFixture.embeddedPack = pack();
        host = MockBukkit.loadWith(StrobeHostFixture.class, new PluginDescriptionFile("StrobeLights", "0.10.43", StrobeHostFixture.class.getName()));
        var player = server.addPlayer(); player.teleport(new Location(server.getWorld("world"), 2, 70, 2));
        try (var bridge = bridge(4, 8)) {
            bridge.sync(List.of(source(0, 3, 12)));
            assertEquals(1, server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
            assertEquals(0, host.protocolCalls);
            assertEquals(0, host.managerCalls);
        }
    }
    @Test @SuppressWarnings("deprecation") void acceptsFuturePatchOnlyWhenItsCarrierAbiMatches() throws Exception {
        StrobeHostFixture.embeddedPack = pack();
        host = MockBukkit.loadWith(StrobeHostFixture.class, new PluginDescriptionFile("StrobeLights", "0.10.99", StrobeHostFixture.class.getName()));
        var player = server.addPlayer(); player.teleport(new Location(server.getWorld("world"), 2, 70, 2));
        try (var bridge = bridge(4, 8)) {
            bridge.sync(List.of(source(0, 3, 12)));
            assertEquals(1, server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
            assertEquals(0, host.protocolCalls);
            assertEquals(0, host.managerCalls);
        }
    }
    @Test void installedCarrierContractIsValidatedBeforeRendering() throws Exception {
        installHost(); assertDoesNotThrow(() -> StrobeLightsBridge.verifyCarrier(host,"1.21.4"));
        host.availablePack = false;
        assertThrows(IllegalArgumentException.class, () -> StrobeLightsBridge.verifyCarrier(host,"1.21.4"));
    }
    @Test void diagnosticsDistinguishPackReadinessSurfacesAndBudget() throws Exception {
        installHost(); var player = server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        try (var bridge = bridge(4,8)) {
            state.ready = false; bridge.sync(List.of(source(0,3,12)));
            assertTrue(String.join("\n", bridge.diagnostic(player)).contains("packReady=false"));
            state.ready = true; bridge.sync(List.of(source(0,3,12)));
            String active = String.join("\n", bridge.diagnostic(player));
            assertTrue(active.contains("projected=1")); assertTrue(active.contains("carriers=1"));
            var noFloor = new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(new UUID(0,1),99),new Location(server.getWorld("world"),5,100,5),0xFF0000,12);
            bridge.sync(List.of(noFloor));
            assertTrue(String.join("\n", bridge.diagnostic(player)).contains("noSurface=1"));
            bridge.limitBudget(0); bridge.sync(List.of(source(0,3,12)));
            assertTrue(String.join("\n", bridge.diagnostic(player)).contains("displayBudget=0"));
        }
    }
    @Test void aPackWithoutTheStableExtensionIsRejectedBeforeSendingAnyCarriers() throws Exception {
        installHost();
        var bytes = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(StrobeHostFixture.embeddedPack));
             var output = new ZipOutputStream(bytes)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (entry.getName().equals("assets/strobelights/strobelights-integration.json")) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(output); output.closeEntry();
            }
        }
        StrobeHostFixture.embeddedPack = bytes.toByteArray();
        server.addPlayer();
        try(var bridge=bridge(4,8)){bridge.sync(List.of(source(0,2,12)));assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty());}
    }
    @Test void changingSelectedSourcesReusesTheSameEntitiesAndSlots() throws Exception {
        installHost();var player=server.addPlayer();player.teleport(new Location(server.getWorld("world"),2,70,2));
        try(var bridge=bridge(2,2)){
            bridge.sync(List.of(source(0,2,12),source(1,4,12)));
            var before=server.getWorld("world").getEntitiesByClass(ItemDisplay.class).stream().map(ItemDisplay::getUniqueId).collect(java.util.stream.Collectors.toSet());
            bridge.sync(List.of(source(2,6,12),source(3,8,12)));
            var after=server.getWorld("world").getEntitiesByClass(ItemDisplay.class).stream().map(ItemDisplay::getUniqueId).collect(java.util.stream.Collectors.toSet());
            assertEquals(2,before.size());assertEquals(before,after);
        }
    }
    @Test void absentDependencyCreatesNoRendererEntities() throws Exception {
        server.addPlayer(); var bridge = bridge(4,8); bridge.sync(List.of(source(0,2,12)));
        assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty());
        assertFalse(bridge.due(2)); bridge.close();
    }
    @Test void rendererUsesPrivateNonPersistentCarriersWithoutAStrobeRegistry() throws Exception {
        installHost(); var player = server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        var bridge = bridge(4,8); bridge.sync(List.of(source(0,3,12)));
        var entities = server.getWorld("world").getEntitiesByClass(ItemDisplay.class); assertEquals(1,entities.size());
        var display = entities.iterator().next(); assertFalse(display.isPersistent()); assertFalse(display.isVisibleByDefault()); assertTrue(player.canSee(display));
        assertEquals("strobelights:carrier/rgb", display.getItemStack().getItemMeta().getItemModel().toString());
        UUID entityId = display.getUniqueId();
        bridge.sync(List.of(source(0,3,0))); assertEquals(entityId,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).iterator().next().getUniqueId());
        int off=display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB();
        assertTrue(StrobeProtocol.stable(off));assertEquals(0,StrobeProtocol.peak(off));
        server.getPluginManager().disablePlugin(host); bridge.sync(List.of(source(0,3,12)));
        assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty()); bridge.close();
        assertEquals(0,host.managerCalls);
    }
    @Test void packReadinessAndViewerBudgetsAreEnforced() throws Exception {
        installHost(); var first = server.addPlayer(); var second = server.addPlayer();
        first.teleport(new Location(server.getWorld("world"),2,70,2)); second.teleport(first.getLocation());
        var bridge = bridge(4,2); bridge.sync(List.of(source(0,2,10),source(1,3,10),source(2,4,10)));
        assertEquals(2,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
        for (var entity : server.getWorld("world").getEntitiesByClass(ItemDisplay.class)) assertNotEquals(first.canSee(entity),second.canSee(entity));
        state.ready=false; bridge.sync(List.of(source(0,2,10))); assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty()); bridge.close();
    }
    @Test void nearestSelectionIsBoundedByDistanceWorldAndBudget() {
        var eye = new Location(server.getWorld("world"),0,70,2);
        var sources = List.of(source(0,8,12),source(1,2,12),source(2,4,12),source(3,80,12));
        assertEquals(List.of(1,2), StrobeLightsBridge.select(sources,eye,2,32).stream().map(s->s.key().index()).toList());
        assertTrue(StrobeLightsBridge.select(sources,eye,0,32).isEmpty());
    }
    @Test void rgbCarriersImmediatelyRespectTheRemainingGlobalDisplayBudget() throws Exception {
        installHost(); var player=server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        var bridge=bridge(8,16); var sources=List.of(source(0,2,10),source(1,3,10),source(2,4,10),source(3,5,10));
        bridge.sync(sources); assertEquals(4,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
        bridge.limitBudget(2); assertEquals(2,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
        bridge.limitBudget(0); bridge.sync(sources); assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty());
        bridge.limitBudget(3); bridge.sync(sources); assertEquals(3,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size()); bridge.close();
    }
    @Test void samplingDoesNotLoseEveryOtherColorOfAnRgbGarland() {
        int stride = LightGeometry.sourceStride(1.5,.75,4); assertEquals(3,stride);
        Set<Integer> colors = new HashSet<>(); for (int i=0;i<4;i++) colors.add((i*stride)%4);
        assertEquals(Set.of(0,1,2,3),colors);
    }
    @Test void sourcesAreNotBuiltWhenNoViewerHasLoadedTheRendererPack() throws Exception {
        installHost(); server.addPlayer(); state.ready=false; var bridge=bridge(4,8);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        bridge.sync(viewers->{calls.incrementAndGet();return List.of(source(0,2,10));});
        assertEquals(0,calls.get()); bridge.close();
    }
    @Test void replacingSelectedSourcesNeverTemporarilyExceedsTheCarrierBudget() throws Exception {
        installHost(); var player=server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        var peak=new java.util.concurrent.atomic.AtomicInteger();
        ((PluginTest.TestServer)server).afterSpawn=entity->peak.accumulateAndGet(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size(),Math::max);
        try(var bridge=bridge(2,2)) {
            bridge.sync(List.of(source(0,2,10),source(1,3,10))); assertEquals(2,peak.get());
            bridge.sync(List.of(source(2,4,10),source(3,5,10)));
            assertEquals(2,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size()); assertEquals(2,peak.get());
        }
    }
    @Test @SuppressWarnings("deprecation") void trpNativeGraphicsProfileDoesNotAffectXmasRgbCarriers() throws Exception {
        installHost(); var player=server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        var policy=MockBukkit.loadWith(TrpPolicyFixture.class,new PluginDescriptionFile("TRPServerEdition","test",TrpPolicyFixture.class.getName()));
        try(var bridge=bridge(4,8)) {
            var sources=List.of(source(0,3,10));
            bridge.sync(sources);
            var entities=server.getWorld("world").getEntitiesByClass(ItemDisplay.class);
            assertEquals(1,entities.size(),"TRP's default Native profile must not block XmasLights RGB");
            var display=entities.iterator().next(); UUID id=display.getUniqueId();
            int payload=display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB();
            for(boolean allowed:List.of(true,false)) {
                policy.rgbAllowed=allowed; bridge.sync(sources);
                assertEquals(1,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
                assertTrue(display.isValid()); assertEquals(id,display.getUniqueId());
                assertEquals(payload,display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB());
                assertTrue(player.canSee(display));
            }
            server.getPluginManager().disablePlugin(policy); bridge.sync(sources);
            assertTrue(display.isValid());
            assertEquals(0,policy.policyCalls,"XmasLights must not query TRP graphics preferences");
        }
    }
    @Test @SuppressWarnings("deprecation") void trpDependentHostCapabilityDoesNotVetoItsOwnStableCarrierPack() throws Exception {
        installHost(); host.stableProtocol=0;
        MockBukkit.loadWith(TrpPolicyFixture.class,new PluginDescriptionFile("TRPServerEdition","0.4.14",TrpPolicyFixture.class.getName()));
        var player=server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        try(var bridge=bridge(4,8)) {
            bridge.sync(List.of(source(0,3,10)));
            assertEquals(1,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
            assertEquals(0,host.protocolCalls,"Validate StrobeLights' own pack, not its TRP-dependent aggregate capability");
        }
    }
    @Test @SuppressWarnings("deprecation") void trpDoesNotBypassStrobePackReadiness() throws Exception {
        installHost();
        var policy=MockBukkit.loadWith(TrpPolicyFixture.class,new PluginDescriptionFile("TRPServerEdition","test",TrpPolicyFixture.class.getName()));
        policy.rgbAllowed=true;
        var player=server.addPlayer(); player.teleport(new Location(server.getWorld("world"),2,70,2));
        try(var bridge=bridge(4,8)) {
            var sources=List.of(source(0,3,10)); state.ready=false; bridge.sync(sources);
            assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty());
            state.ready=true; bridge.sync(sources);
            assertEquals(1,server.getWorld("world").getEntitiesByClass(ItemDisplay.class).size());
            state.ready=false; bridge.sync(sources);
            assertTrue(server.getWorld("world").getEntitiesByClass(ItemDisplay.class).isEmpty());
            assertEquals(0,policy.policyCalls);
        }
    }
}
