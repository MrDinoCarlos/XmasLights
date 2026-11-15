package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class SurfaceLightingTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin();
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private StrobeLightsBridge.Source lamp(int index,double x) {
        return new StrobeLightsBridge.Source(new StrobeLightsBridge.Key(new UUID(0,42),index),new Location(world,x,74,2.5),0xFF3030,12);
    }
    private void floor(int y) { for(int x=0;x<32;x++) world.getBlockAt(x,y,2).setType(Material.STONE); }
    @SuppressWarnings("deprecation") private StrobeLightsBridge bridge() throws Exception {
        StrobeHostFixture.embeddedPack=StrobeLightsTest.pack();
        MockBukkit.loadWith(StrobeHostFixture.class,new PluginDescriptionFile("StrobeLights","0.10.38",StrobeHostFixture.class.getName()));
        var bridge=new StrobeLightsBridge(plugin); var config=ResourcesTest.resource("config.yml"); config.set("strobelights.renderer","shader"); bridge.configure(config); return bridge;
    }
    private Location rendered(ItemDisplay display) {
        var t=display.getTransformation().getTranslation(); return display.getLocation().add(t.x,t.y,t.z);
    }
    private int payload(ItemDisplay display) { return display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB(); }

    @Test void floorImpactIsOnTheBlockFaceAndNotAtTheLamp() {
        floor(70); var hit=SurfaceLighting.trace(lamp(0,2.5).location(),new Vector(0,-1,0),8);
        assertNotNull(hit); assertEquals(new Location(world,2.5,71.04,2.5),hit.position()); assertEquals(3,hit.distance(),1e-9);
    }
    @Test void partialBlocksUseTheirActualCollisionHeight() {
        var slab=(Slab)Material.STONE_SLAB.createBlockData(); slab.setType(Slab.Type.BOTTOM); world.getBlockAt(2,70,2).setBlockData(slab);
        var hit=SurfaceLighting.trace(lamp(0,2.5).location(),new Vector(0,-1,0),8);
        assertNotNull(hit); assertEquals(70.54,hit.position().getY(),1e-9);
    }
    @Test void firstWallStopsTheRayBeforeTheBlocksBehindIt() {
        world.getBlockAt(4,74,2).setType(Material.STONE); world.getBlockAt(6,74,2).setType(Material.STONE);
        var hit=SurfaceLighting.trace(lamp(0,2.5).location(),new Vector(1,0,0),8);
        assertNotNull(hit); assertEquals(3.96,hit.position().getX(),1e-9); assertEquals(1.5,hit.distance(),1e-9);
    }
    @Test void emptyAirBeyondReachAndEmbeddedSourcesHaveNoImpact() {
        assertNull(SurfaceLighting.trace(new Location(world,2.5,100,2.5),new Vector(0,-1,0),8));
        world.getBlockAt(2,74,2).setType(Material.STONE);
        assertNull(SurfaceLighting.trace(lamp(0,2.5).location(),new Vector(0,-1,0),8));
    }
    @Test void raysNeverGenerateOrLoadTheNextChunk() {
        world.unloadChunk(1,0); int chunks=world.getChunkCount();
        assertNull(SurfaceLighting.trace(new Location(world,15.5,74,2.5),new Vector(1,0,0),8));
        assertFalse(world.isChunkLoaded(1,0)); assertEquals(chunks,world.getChunkCount());
    }
    @Test void probesAreCachedAndWorkPerUpdateIsBoundedAcrossViewers() {
        floor(70); var surfaces=new SurfaceLighting(8,20,2); surfaces.beginUpdate(4);
        assertNotNull(surfaces.project(lamp(0,2.5))); assertNotNull(surfaces.project(lamp(1,3.5))); assertNull(surfaces.project(lamp(2,4.5)));
        surfaces.beginUpdate(4);
        assertNotNull(surfaces.project(lamp(0,2.5))); assertNotNull(surfaces.project(lamp(1,3.5))); assertNotNull(surfaces.project(lamp(2,4.5)));
    }
    @Test void wallFittingsKeepAnImpactOnTheirNearbySupportingWall() {
        floor(70); world.getBlockAt(2,73,1).setType(Material.STONE);
        var source=new StrobeLightsBridge.Source(lamp(0,2.5).key(),new Location(world,2.5,73.8,2.2),0xFFFFFF,12,new Vector(0,0,-1),.5);
        var surfaces=new SurfaceLighting(8,20,2); surfaces.beginUpdate(4); var hit=surfaces.project(source);
        assertNotNull(hit); assertEquals(73.8,hit.position().getY(),1e-9); assertEquals(2.04,hit.position().getZ(),1e-9); assertTrue(hit.distance()<1);
    }
    @Test void realCarrierRendersAtTheImpactWithEnoughEnergyAndABoundedRadius() throws Exception {
        floor(70); var player=server.addPlayer(); player.teleport(new Location(world,2,74,2));
        try(var bridge=bridge()) {
            var source=lamp(0,2.5); bridge.sync(List.of(source)); var display=world.getEntitiesByClass(ItemDisplay.class).iterator().next();
            assertEquals(71.04,rendered(display).getY(),1e-5);
            assertTrue(StrobeProtocol.peak(payload(display))>=6);
            assertTrue(StrobeProtocol.radius(payload(display))<=2.25); assertTrue(StrobeProtocol.stable(payload(display)));
        }
    }
    @Test void walkingAcrossAChunkBoundaryDoesNotTeleportOrReplaceTheCarrier() throws Exception {
        floor(70); var player=server.addPlayer(); player.teleport(new Location(world,15.8,74,2));
        try(var bridge=bridge()) {
            bridge.sync(List.of(lamp(0,15.5))); var display=world.getEntitiesByClass(ItemDisplay.class).iterator().next();
            UUID id=display.getUniqueId(); Location anchor=display.getLocation(), impact=rendered(display);
            player.teleport(new Location(world,16.2,74,2)); bridge.sync(List.of(lamp(0,15.5)));
            var after=world.getEntitiesByClass(ItemDisplay.class).iterator().next();
            assertEquals(id,after.getUniqueId()); assertEquals(anchor,after.getLocation()); assertEquals(impact,rendered(after));
        }
    }
    @Test void stableSelectionHasHysteresisButStillAdmitsAMuchCloserLight() {
        var first=lamp(0,4); var second=lamp(1,4.1); var eye=new Location(world,0,74,2.5);
        assertEquals(first.key(),StrobeLightsBridge.select(List.of(first,second),eye,1,24,Set.of(first.key())).getFirst().key());
        var close=lamp(2,.5);
        assertEquals(close.key(),StrobeLightsBridge.select(List.of(first,close),eye,1,24,Set.of(first.key())).getFirst().key());
    }
    @Test void nearbyWallLightsKeepUsefulEnergyInsteadOfCollapsingToATinyRadius() throws Exception {
        world.getBlockAt(2,73,1).setType(Material.STONE);var player=server.addPlayer();player.teleport(new Location(world,2,74,4));
        try(var bridge=bridge()) {
            var source=new StrobeLightsBridge.Source(lamp(0,2.5).key(),new Location(world,2.5,73.8,2.2),0xFF3030,12,new Vector(0,0,-1),.5);
            bridge.sync(List.of(source));var display=world.getEntitiesByClass(ItemDisplay.class).iterator().next();
            assertEquals(2.04,rendered(display).getZ(),1e-5);assertTrue(StrobeProtocol.peak(payload(display))>=7);assertTrue(StrobeProtocol.radius(payload(display))>=1.9);
            assertTrue(StrobeProtocol.radius(payload(display))<=2.25);
        }
    }
    @Test void impactsKeepExactlyTheSameEnergyAtThirtyTwoThrough176Blocks() throws Exception {
        floor(70);var player=server.addPlayer();player.teleport(new Location(world,2,74,2));
        try(var bridge=bridge()) {
            bridge.sync(List.of(lamp(0,2.5)));
            int expected=payload(world.getEntitiesByClass(ItemDisplay.class).iterator().next());
            for(int distance:new int[]{32,64,96,128,176}) {
                world.loadChunk((2+distance)>>4,0); // Paper always loads the player's own chunk.
                player.teleport(new Location(world,2+distance,74,2));bridge.sync(List.of(lamp(0,2.5)));
                assertEquals(1,world.getEntitiesByClass(ItemDisplay.class).size());
                var display=world.getEntitiesByClass(ItemDisplay.class).iterator().next();assertTrue(StrobeProtocol.peak(payload(display))>=6);assertEquals(71.04,rendered(display).getY(),1e-5);
                assertEquals(expected,payload(display));
            }
        }
    }
    @Test void smallWalkingMovementsDoNotChangeTheImpactOrItsEnergy() throws Exception {
        floor(70);var player=server.addPlayer();player.teleport(new Location(world,2,74,2));
        try(var bridge=bridge()) {
            bridge.sync(List.of(lamp(0,2.5)));var first=world.getEntitiesByClass(ItemDisplay.class).iterator().next();
            var id=first.getUniqueId();int energy=payload(first);var impact=rendered(first);
            for(int step=1;step<=20;step++) {
                player.teleport(new Location(world,2+step*.1,74,2));bridge.sync(List.of(lamp(0,2.5)));
                var after=world.getEntitiesByClass(ItemDisplay.class).iterator().next();assertEquals(id,after.getUniqueId());assertEquals(energy,payload(after));assertEquals(impact,rendered(after));
            }
        }
    }
    @Test void pineFoliageHasOnlyASmallConfigurableMinimumAndRevertsWhenOff() {
        var player=server.addPlayer();var a=new Vector(1,74,2);var b=new Vector(7,74,2);
        var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType("wall-garland-rgb"),new Vector(0,0,1),null,false,null);
        for(var body:string.garlandBodies.values())assertEquals(5,body.getBrightness().getBlockLight());
        plugin.manager().setOn(string,false);server.getScheduler().performTicks(25);
        for(var body:string.garlandBodies.values())assertNull(body.getBrightness());
    }
    @Test void distantSurfacesHaveSmallerDimmerImpactsWithTheInstalledQuantisation() {
        int near=StrobeProtocol.surface(0xFFFFFF,9,.25,2.25), far=StrobeProtocol.surface(0xFFFFFF,3,.25,1.6);
        assertTrue(StrobeProtocol.radius(far)<StrobeProtocol.radius(near)); assertTrue((far&0xFFF0)<(near&0xFFF0));
        assertEquals(0,StrobeProtocol.surface(0xFFFFFF,12,.25,.8)&0xFFF0);
        assertEquals(0xA00005,StrobeProtocol.encode(0xFFFFFF,0,.25));
    }
    @Test void radiusMathMatchesTheActualStrobePackWhenProvided() throws Exception {
        assertEquals(4.5,StrobeProtocol.radius(StrobeProtocol.encode(0xFFFFFF,15,.25)),1e-9);
        String path=System.getProperty("strobelights.test.jar"); if(path==null) return;
        try(var jar=new JarFile(path); var input=jar.getInputStream(jar.getJarEntry("embedded/StrobeLights-ResourcePack-1.21.4.zip")); var zip=new ZipInputStream(input)) {
            java.util.zip.ZipEntry entry; boolean found=false;
            while((entry=zip.getNextEntry())!=null) if(entry.getName().equals("assets/minecraft/shaders/include/utils.glsl")) {
                String shader=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(shader.contains("#define LIGHTR 18.0")); assertTrue(shader.contains("#define MIN_LIGHTR 3.0")); assertTrue(shader.contains("#define RADIUS_CURVE 0.65")); found=true;
            }
            assertTrue(found);
        }
    }
    @Test void anExplicitSteadyFloorOverrideKeepsRgbConstantWhileTheBulbsBlink() throws Exception {
        floor(70); var player=server.addPlayer(); player.teleport(new Location(world,2,74,2));
        try(var unused=bridge()) {
            plugin.getConfig().set("strobelights.surfaces.follow-effects",false);
            plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload();
            var a=new Vector(1,74,2.5); var b=new Vector(7,74,2.5);
            var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType("warm"),new Vector(),null,false,null);
            plugin.manager().setMode(string,AnimationMode.BLINK); server.getScheduler().performTicks(16);
            var before=carrierPayloads(); assertFalse(before.isEmpty()); assertEquals(2,string.bulbs.get(0).getBrightness().getBlockLight());
            server.getScheduler().performTicks(10);
            assertEquals(before,carrierPayloads()); assertEquals(15,string.bulbs.get(0).getBrightness().getBlockLight());
            assertTrue(string.bulbLights.isEmpty());
            for(int i:string.bulbs.keySet()) {
                var point=LightGeometry.lightPoint(string,plugin.manager().getType(string.type),i);
                assertTrue(world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType().isAir());
            }
        }
    }
    @Test void defaultRgbFollowsBlinkAndKeepsTheCarrierDuringItsDarkFrame() throws Exception {
        floor(70); var player=server.addPlayer(); player.teleport(new Location(world,2,74,2));
        try(var unused=bridge()) {
            assertTrue(plugin.getConfig().getBoolean("strobelights.surfaces.follow-effects"));
            var a=new Vector(1,74,2.5); var b=new Vector(7,74,2.5);
            var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType("warm"),new Vector(),null,false,null);
            plugin.manager().setMode(string,AnimationMode.BLINK); server.getScheduler().performTicks(8); var on=carrierPayloads(); assertFalse(on.isEmpty());
            server.getScheduler().performTicks(4); var off=carrierPayloads(); assertEquals(on.keySet(),off.keySet());
            assertTrue(off.values().stream().allMatch(colour->StrobeProtocol.stable(colour)&&StrobeProtocol.peak(colour)==0));
        }
    }
    private Map<UUID,Integer> carrierPayloads() {
        var values=new HashMap<UUID,Integer>(); var key=new NamespacedKey(plugin,"rgb_carrier");
        for(var display:world.getEntitiesByClass(ItemDisplay.class)) if(display.getPersistentDataContainer().has(key)) values.put(display.getUniqueId(),payload(display));
        return values;
    }
    /** A carrier contract fixture only; the release validation also uses the installed real JAR. */
    private static class StrobeHostFixturePack {
        static byte[] bytes() throws Exception {
            var bytes=new java.io.ByteArrayOutputStream();
            try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
                zip.putNextEntry(new java.util.zip.ZipEntry("assets/strobelights/items/carrier/rgb.json"));
                zip.write("{\"model\":{\"model\":\"strobelights:item/strobe_rgb_carrier\",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":0}]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        }
    }
}
