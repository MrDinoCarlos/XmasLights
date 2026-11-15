package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.*;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeRendererAndStraightTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    private PluginTest.PackPlayer player;
    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        for(int x=0;x<16;x++) for(int z=1;z<10;z++) world.getBlockAt(x,70,z).setType(Material.STONE);
        plugin=PluginTest.loadPlugin(); player=(PluginTest.PackPlayer)server.addPlayer(); player.addAttachment(plugin,"xmaslights.use",true);
        player.teleport(new Location(world,2,74,2));
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private StrobeHostFixture host(String version) throws Exception { return host(version, true); }
    @SuppressWarnings("deprecation") private StrobeHostFixture host(String version, boolean compatible) throws Exception {
        StrobeHostFixture.embeddedPack=StrobeLightsTest.pack();
        if (!compatible) {
            // Simulate an incompatible pack, not merely a different release number.
            // The real 0.10.37 pack did not advertise the stable source ABI.
            var bytes = new java.io.ByteArrayOutputStream();
            try (var input = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(StrobeHostFixture.embeddedPack));
                 var output = new java.util.zip.ZipOutputStream(bytes)) {
                for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                    if (entry.getName().endsWith("strobelights-integration.json")) continue;
                    output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    output.write(input.readAllBytes()); output.closeEntry();
                }
            }
            StrobeHostFixture.embeddedPack = bytes.toByteArray();
        }
        return MockBukkit.loadWith(StrobeHostFixture.class,new PluginDescriptionFile("StrobeLights",version,StrobeHostFixture.class.getName()));
    }
    private LightString create(String type,double start,double length,double z,UUID chain) {
        var a=new Vector(start,74,z); var b=new Vector(start+length,74,z);
        return plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType(type),new Vector(0,0,1),chain,chain!=null,null);
    }
    private LightString create() { return create("warm",1,6,3,null); }
    private void ticks() { server.getScheduler().performTicks(16); }
    private List<BlockVector> points(LightString string) {
        return string.bulbs.keySet().stream().map(i->LightGeometry.lightPoint(string,plugin.manager().getType(string.type),i).toBlockVector()).distinct().toList();
    }
    private void nativeLevel(LightString string,int level) {
        assertFalse(points(string).isEmpty());
        for(var point:points(string)) { var block=world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()); assertEquals(Material.LIGHT,block.getType()); assertEquals(level,((Levelled)block.getBlockData()).getLevel()); }
    }
    private void noNative(LightString string) {
        assertFalse(points(string).isEmpty());
        for(var point:points(string)) assertTrue(world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType().isAir(),point.toString());
    }
    private Set<UUID> geometry(LightString string) {
        var ids=new HashSet<UUID>(); string.bulbs.values().forEach(entity->ids.add(entity.getUniqueId())); string.wires.forEach(entity->ids.add(entity.getUniqueId())); return ids;
    }
    private long carriers() { var key=new NamespacedKey(plugin,"rgb_carrier"); return world.getEntitiesByClass(ItemDisplay.class).stream().filter(entity->entity.getPersistentDataContainer().has(key)).count(); }
    private void reload() throws Exception { plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload(); ticks(); }

    @Test void nativeLightingRemainsTheFallbackWithoutStrobeLights() {
        var string=create(); ticks(); nativeLevel(string,12); assertEquals(0,carriers());
    }
    @Test void installedRendererNeverCreatesWhiteLightBlocks() throws Exception {
        host("0.10.38"); var string=create(); ticks(); noNative(string); assertTrue(carriers()>0);
        assertTrue(string.bulbLights.isEmpty());
    }
    @Test void enablingTheHostRemovesExistingOwnedLightsWithoutRespawningModels() throws Exception {
        var string=create(); ticks(); nativeLevel(string,12); var ids=geometry(string);
        host("0.10.38"); server.getScheduler().performTicks(2); noNative(string); assertEquals(ids,geometry(string));
        plugin.manager().saveData(); assertTrue(SafeYaml.load(plugin.getDataFolder().toPath().resolve("lights.yml")).getStringList("owned-light-blocks").isEmpty());
    }
    @Test void losingAndRestoringTheHostSwitchesLayersWithoutChangingDisplayEntities() throws Exception {
        var host=host("0.10.38"); var string=create(); ticks(); var ids=geometry(string); noNative(string);
        server.getPluginManager().disablePlugin(host); server.getScheduler().performTicks(2); nativeLevel(string,12); assertEquals(ids,geometry(string)); assertEquals(0,carriers());
        server.getPluginManager().enablePlugin(host); server.getScheduler().performTicks(2); noNative(string); assertEquals(ids,geometry(string));
    }
    @Test void playerPackReadinessNeverTurnsOnGlobalWhiteLighting() throws Exception {
        var host=host("0.10.38"); host.state.ready=false; var string=create(); ticks(); noNative(string); assertEquals(0,carriers());
        server.addPlayer().teleport(player.getLocation()); ticks(); noNative(string);
        host.state.ready=true; ticks(); noNative(string); assertTrue(carriers()>0);
    }
    @Test void aHostWithoutTheCarrierAbiDoesNotSilentlyReplaceRgbWithWhiteLight() throws Exception {
        host("99.0.0", false); var string=create(); ticks(); noNative(string); assertEquals(0,carriers());
    }
    @Test void theInstalled037HostRemovesOldWhiteLightsWithoutChangingTheGeometry() throws Exception {
        var string=create(); ticks(); nativeLevel(string,12); var ids=geometry(string);
        host("0.10.37", false); ticks(); noNative(string); assertEquals(0,carriers()); assertEquals(ids,geometry(string));
        plugin.manager().saveData();
        assertTrue(SafeYaml.load(plugin.getDataFolder().toPath().resolve("lights.yml")).getStringList("owned-light-blocks").isEmpty());
    }
    @Test void anUnavailableStrobePackNeverEnablesWhiteLightAndRecoversAfterReload() throws Exception {
        var host=host("0.10.38"); host.availablePack=false; var string=create(); ticks();
        noNative(string); assertEquals(0,carriers());
        host.availablePack=true; reload(); noNative(string); assertTrue(carriers()>0);
    }
    @Test void aMissingRendererPackDoesNotChangeTheGarlandBodyBrightness() throws Exception {
        var host=host("0.10.38"); host.availablePack=false;
        var string=create("garland-rgb",1,6,3,null); ticks(); noNative(string); assertEquals(0,carriers());
        assertEquals(5,plugin.getConfig().getInt("garlands.minimum-body-light"));
        for(var display:string.garlandBodies.values()) assertEquals(5,display.getBrightness().getBlockLight());
    }
    @Test void explicitLayerSettingsStillDisableRgbOrAllNativeLighting() throws Exception {
        host("0.10.38"); var string=create(); ticks(); noNative(string);
        plugin.getConfig().set("strobelights.enabled",false); reload(); nativeLevel(string,12);
        plugin.getConfig().set("light-blocks",false); reload(); noNative(string);
    }
    @Test void nativeSuppressionNeverDeletesAnAdministratorsLightOrAChangedBlock() throws Exception {
        var string=create(); ticks(); var points=points(string); var protectedPoint=points.getFirst();
        world.getBlockAt(protectedPoint.getBlockX(),protectedPoint.getBlockY(),protectedPoint.getBlockZ()).setType(Material.STONE);
        var admin=world.getBlockAt(10,73,3); var data=(Levelled)Material.LIGHT.createBlockData(); data.setLevel(7); admin.setBlockData(data,false);
        host("0.10.38"); server.getScheduler().performTicks(2);
        assertEquals(Material.STONE,world.getBlockAt(protectedPoint.getBlockX(),protectedPoint.getBlockY(),protectedPoint.getBlockZ()).getType());
        assertEquals(7,((Levelled)admin.getBlockData()).getLevel());
        for(var point:points.subList(1,points.size())) assertTrue(world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType().isAir());
    }
    @Test void existingUnloadedLightsAreCleanedWhenTheirChunkReturnsWithoutForcingItToLoad() throws Exception {
        var string=create(); ticks(); var points=points(string); world.unloadChunk(0,0);
        host("0.10.38"); ticks(); assertFalse(world.isChunkLoaded(0,0));
        world.loadChunk(0,0); plugin.manager().onChunkLoad();
        for(var point:points) assertTrue(world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType().isAir());
    }
    @Test void straightModeIsInheritedWhenThePlayerExtendsAChain() {
        var first=create(); assertNull(plugin.manager().setStraight(first,true));
        var next=create("warm",7,6,3,first.chain); assertEquals(first.chain,next.chain); assertTrue(next.straight); assertEquals(0,next.sag);
    }
    @Test void straightChoiceSurvivesSaveReloadAndRegenerationWithASchemaBackup() throws Exception {
        var string=create(); var id=string.id; plugin.manager().setStraight(string,true); plugin.manager().saveData();
        var file=plugin.getDataFolder().toPath().resolve("lights.yml"); var yaml=SafeYaml.load(file); assertEquals(4,yaml.getInt("data-version")); assertTrue(yaml.getBoolean("lights."+id+".straight"));
        yaml.set("data-version",2); SafeYaml.save(yaml,file,false); plugin.manager().loadData(); plugin.manager().scanLoadedEntities();
        var loaded=plugin.manager().getStrings().stream().filter(s->s.id.equals(id)).findFirst().orElseThrow(); assertTrue(loaded.straight);
        assertNull(plugin.manager().regenerate(loaded)); assertEquals(0,loaded.sag); plugin.reload(); ticks(); assertEquals(0,loaded.sag);
        try(var files=Files.list(file.getParent())) { assertTrue(files.anyMatch(path->path.getFileName().toString().startsWith("lights.yml.")&&path.toString().endsWith(".bak"))); }
    }
    @Test void hiddenSegmentsKeepTheirVisibilityWhenStraightModeChanges() {
        var string=create(); plugin.manager().despawn(string); plugin.manager().setStraight(string,true);
        assertTrue(string.straight); assertFalse(string.spawned); assertTrue(string.bulbs.isEmpty());
        assertNull(plugin.manager().spawn(string)); assertEquals(0,string.sag);
    }
    @Test void configUpgradeRefreshesBundledLightingCommentsButKeepsPersonalNotes() throws Exception {
        plugin.getConfig().set("config-version",6);
        plugin.getConfig().setComments("light-blocks",List.of("Replaces air with invisible minecraft:light blocks. Existing world LIGHT","blocks and solid blocks are never claimed or replaced."));
        plugin.getConfig().setComments("animated-light",List.of("My own server preference")); reload();
        assertEquals(16,plugin.getConfig().getInt("config-version"));
        assertTrue(plugin.getConfig().getComments("light-blocks").stream().anyMatch(line->line!=null&&line.contains("Vanilla fallback")));
        assertEquals(List.of("My own server preference"),plugin.getConfig().getComments("animated-light"));
        plugin.getConfig().set("config-version",6); plugin.getConfig().setComments("light-blocks",List.of("My custom light policy note")); reload();
        assertEquals(List.of("My custom light policy note"),plugin.getConfig().getComments("light-blocks"));
    }
}
