package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RenderDistanceTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private PluginTest.PackPlayer player;
    private org.mockbukkit.mockbukkit.world.WorldMock world;

    @BeforeEach @SuppressWarnings("deprecation") void start() throws Exception {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin();
        StrobeHostFixture.embeddedPack=StrobeLightsTest.pack();
        MockBukkit.loadWith(StrobeHostFixture.class,new PluginDescriptionFile("StrobeLights","0.10.38",StrobeHostFixture.class.getName()));
        player=(PluginTest.PackPlayer)server.addPlayer(); player.teleport(new Location(world,2,74,2));
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private StrobeLightsBridge.Source lamp(double x) {
        world.loadChunk((int)Math.floor(x)>>4,0); world.getBlockAt((int)Math.floor(x),70,2).setType(Material.STONE);
        return new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(new UUID(0,42),0),new Location(world,x,74,2.5),0xFF3030,12);
    }
    private StrobeLightsBridge bridge(Double limit) throws Exception {
        var config=ResourcesTest.resource("config.yml"); config.set("strobelights.renderer","shader");
        if(limit!=null) config.set("strobelights.view-distance-chunks",limit / 16);
        var bridge=new StrobeLightsBridge(plugin); bridge.configure(config); return bridge;
    }
    private int payload(ItemDisplay display) { return display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB(); }

    @Test void actualSentKeysWorkWhenTheLegacyChunkProbeIncorrectlyReturnsFalse() throws Exception {
        var source=lamp(2.5); player.staleChunkSentProbe=true;
        player.sentChunks=Set.of(Chunk.getChunkKey(0,0));
        assertFalse(player.isChunkSent(Chunk.getChunkKey(0,0)));
        try(var bridge=bridge(null)) {
            bridge.sync(List.of(source)); assertEquals(1,world.getEntitiesByClass(ItemDisplay.class).size());
            player.sentChunks=Set.of(); bridge.sync(List.of(source));
            assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
        }
    }

    @Test void thirtyChunkSourcesRenderOnlyAfterTheirChunkHasBeenSent() throws Exception {
        var source=lamp(482.5); player.sentChunks=new HashSet<>(Set.of(Chunk.getChunkKey(0,0)));
        try(var bridge=bridge(null)) {
            bridge.sync(List.of(source)); assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
            player.sentChunks.add(Chunk.getChunkKey(30,0)); bridge.sync(List.of(source));
            assertEquals(1,world.getEntitiesByClass(ItemDisplay.class).size(),"A source 30 chunks away must not be cut at 192 blocks");
            var display=world.getEntitiesByClass(ItemDisplay.class).iterator().next();
            assertTrue(player.canSee(display)); assertTrue(StrobeProtocol.peak(payload(display))>0);
            var translated=display.getLocation().add(display.getTransformation().getTranslation().x,
                    display.getTransformation().getTranslation().y,display.getTransformation().getTranslation().z);
            assertEquals(482.5,translated.getX(),1e-4); assertEquals(71.04,translated.getY(),1e-4);
            player.sentChunks.remove(Chunk.getChunkKey(30,0)); bridge.sync(List.of(source));
            assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty()); assertTrue(world.isChunkLoaded(30,0));
        }
    }
    @Test void cameraDistanceAndDiagonalChunksDoNotReduceSourceEnergy() throws Exception {
        var source=lamp(2.5); player.sentChunks=new HashSet<>(Set.of(Chunk.getChunkKey(0,0)));
        try(var bridge=bridge(null)) {
            bridge.sync(List.of(source)); int expected=payload(world.getEntitiesByClass(ItemDisplay.class).iterator().next());
            for(int[] chunks:new int[][]{{16,0},{30,0},{30,30},{32,32}}) {
                world.loadChunk(chunks[0],chunks[1]); player.teleport(new Location(world,2+chunks[0]*16,74,2+chunks[1]*16));
                bridge.sync(List.of(source)); assertEquals(1,world.getEntitiesByClass(ItemDisplay.class).size());
                assertEquals(expected,payload(world.getEntitiesByClass(ItemDisplay.class).iterator().next()));
            }
        }
    }
    @Test void theFull128SourceCapacityCanBeFilledAtThirtyChunks() throws Exception {
        var sources=new ArrayList<StrobeLightsBridge.Source>();
        for(int index=0;index<128;index++) {
            var source=lamp(482.5+(index%8)*.05);
            sources.add(new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(new UUID(0,42),index),
                    source.location(),source.rgb(),source.level()));
        }
        player.sentChunks=Set.of(Chunk.getChunkKey(0,0),Chunk.getChunkKey(30,0));
        try(var bridge=bridge(null)) {
            bridge.sync(sources); bridge.sync(sources);
            assertEquals(128,world.getEntitiesByClass(ItemDisplay.class).size());
            assertEquals(128,world.getEntitiesByClass(ItemDisplay.class).stream().map(this::payload).distinct().count());
        }
    }
    @Test void anExplicitBlockRangeStillAllowsAnAdministratorToLimitRgb() throws Exception {
        var source=lamp(112.5);
        try(var bridge=bridge(64.0)) {
            bridge.sync(List.of(source)); assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
            player.teleport(new Location(world,60,74,2)); world.loadChunk(3,0); bridge.sync(List.of(source));
            assertEquals(1,world.getEntitiesByClass(ItemDisplay.class).size());
        }
    }
    @Test void lightManagerDoesNotDiscardDistantStringsBeforeTheBridgeCanSeeThem() {
        for(int x=0;x<10;x++) world.getBlockAt(x,70,2).setType(Material.STONE);
        var a=new Vector(1,74,2.5); var b=new Vector(7,74,2.5);
        plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType("warm"),new Vector(),null,false,null);
        server.getScheduler().performTicks(32);
        var marker=new NamespacedKey(plugin,"rgb_carrier");
        assertTrue(world.getEntitiesByClass(ItemDisplay.class).stream().anyMatch(d->d.getPersistentDataContainer().has(marker)));
        world.loadChunk(30,30); player.sentChunks=Set.of(Chunk.getChunkKey(0,0),Chunk.getChunkKey(30,30));
        player.teleport(new Location(world,482,74,482)); server.getScheduler().performTicks(12);
        assertTrue(world.getEntitiesByClass(ItemDisplay.class).stream().anyMatch(d->d.getPersistentDataContainer().has(marker)),
                "LightManager must retain strings in the client's sent chunks, including diagonal views");
    }
    @Test void oldBundledDistanceAndCapacityDefaultsMigrateToSentChunks() throws Exception {
        var config=plugin.getConfig(); config.set("config-version",12);
        config.set("strobelights.view-distance",192.0); config.set("strobelights.max-sources-per-player",32);
        config.set("strobelights.max-carriers-total",128); config.set("strobelights.surfaces.probes-per-update",16);
        config.save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload();
        assertEquals(16,plugin.getConfig().getInt("config-version"));
        assertEquals(0,plugin.getConfig().getDouble("strobelights.view-distance-chunks"));
        assertEquals(128,plugin.getConfig().getInt("strobelights.max-sources-per-player"));
        assertEquals(512,plugin.getConfig().getInt("strobelights.max-carriers-total"));
        assertEquals(64,plugin.getConfig().getInt("strobelights.surfaces.probes-per-update"));
    }
    @Test void customDistanceAndCapacitySettingsArePreserved() throws Exception {
        var config=plugin.getConfig(); config.set("config-version",12);
        config.set("strobelights.view-distance",96.0); config.set("strobelights.max-sources-per-player",5);
        config.set("strobelights.max-carriers-total",20); config.set("strobelights.surfaces.probes-per-update",7);
        config.save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload();
        assertEquals(6,plugin.getConfig().getDouble("strobelights.view-distance-chunks"));
        assertEquals(5,plugin.getConfig().getInt("strobelights.max-sources-per-player"));
        assertEquals(20,plugin.getConfig().getInt("strobelights.max-carriers-total"));
        assertEquals(7,plugin.getConfig().getInt("strobelights.surfaces.probes-per-update"));
    }
    @Test void automaticAndExplicitDistanceSettingsAreValidated() throws Exception {
        var config=ResourcesTest.resource("config.yml"); config.set("strobelights.renderer","shader"); var types=ResourcesTest.resource("types.yml");
        for(double value:new double[]{0,1,30,64}) {
            config.set("strobelights.view-distance-chunks",value); assertDoesNotThrow(()->LightManager.validateSettings(config,types));
        }
        for(double value:new double[]{-1,64.1,Double.NaN}) {
            config.set("strobelights.view-distance-chunks",value); assertThrows(ConfigProblem.class,()->LightManager.validateSettings(config,types));
        }
    }
}
