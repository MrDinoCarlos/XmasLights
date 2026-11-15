package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OffEffectsTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    private Player player;

    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer());world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.loadChunk(x,z);
        for(int x=0;x<16;x++)for(int z=0;z<8;z++)world.getBlockAt(x,70,z).setType(Material.STONE);
        plugin=PluginTest.loadPlugin();player=server.addPlayer();player.teleport(new Location(world,2,74,2));
    }
    @AfterEach void stop(){PluginTest.stopServer();}
    private LightString create(String id) {
        var a=new Vector(1,74,2.5);var b=new Vector(7,74,2.5);
        return plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType(id),new Vector(),null,false,null);
    }
    private void ticks(int count){server.getScheduler().performTicks(count);}
    private Map<Integer,Object> colours(LightString string) {
        var result=new HashMap<Integer,Object>();
        string.bulbs.forEach((index,display)->{
            if(display instanceof ItemDisplay item){
                var meta=item.getItemStack().getItemMeta();
                result.put(index,List.of(meta.getItemModel(),meta.getCustomModelDataComponent().getColors().stream().map(Color::asRGB).toList()));
            }else result.put(index,((BlockDisplay)display).getBlock().getAsString());
        });
        return result;
    }
    private Set<UUID> geometry(LightString string) {
        var result=new HashSet<UUID>();string.bulbs.values().forEach(entity->result.add(entity.getUniqueId()));
        string.wires.forEach(entity->result.add(entity.getUniqueId()));return result;
    }
    private long carriers() {
        var key=new NamespacedKey(plugin,"rgb_carrier");
        return world.getEntitiesByClass(ItemDisplay.class).stream().filter(entity->entity.getPersistentDataContainer().has(key)).count();
    }
    private Map<UUID,Integer> rgbColours() {
        var key=new NamespacedKey(plugin,"rgb_carrier");var result=new HashMap<UUID,Integer>();
        for(var display:world.getEntitiesByClass(ItemDisplay.class))if(display.getPersistentDataContainer().has(key))
            result.put(display.getUniqueId(),display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB()&0xFFF);
        return result;
    }
    @SuppressWarnings("deprecation") private void host() throws Exception {
        StrobeHostFixture.embeddedPack=StrobeLightsTest.pack();
        MockBukkit.loadWith(StrobeHostFixture.class,new PluginDescriptionFile("StrobeLights","0.10.38",StrobeHostFixture.class.getName()));
    }
    private void reload() throws Exception {
        plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile());plugin.reload();
    }
    private void assertOff(LightString string) {
        assertTrue(string.idle,"OFF strings should leave the animation update loop");
        for(var display:string.bulbs.values())assertEquals(2,display.getBrightness().getBlockLight());
    }

    @Test void offChaseGarlandsFreezeTheirColoursInsteadOfContinuingTheMode() {
        var string=create("garland-rgb");plugin.manager().setMode(string,AnimationMode.CHASE);ticks(22);
        var before=colours(string);var ids=geometry(string);
        plugin.manager().setOn(string,false);ticks(4);ticks(60);
        assertEquals(before,colours(string));assertOff(string);assertEquals(ids,geometry(string));
    }
    @Test void everyModeStaysInactiveWhileOffInBothPackedAndVanillaRenderers() throws Exception {
        for(boolean packed:new boolean[]{true,false}){
            plugin.getConfig().set("model-pack.enabled",packed);reload();
            for(String id:List.of("garland-rgb","wall-inline-rainbow")){
                var string=create(id);plugin.manager().setMode(string,AnimationMode.CHASE);ticks(22);
                plugin.manager().setOn(string,false);ticks(4);var frozen=colours(string);
                for(var mode:AnimationMode.values()){
                    plugin.manager().setMode(string,mode);ticks(32);
                    assertEquals(frozen,colours(string),id+" / "+mode+" / packed="+packed);assertOff(string);
                }
            }
        }
    }
    @Test void offRemovesPrivateRgbCarriersAndOnResumesTheSavedModeWithoutReplacingModels() throws Exception {
        host();assertTrue(plugin.getConfig().getBoolean("strobelights.surfaces.follow-effects"));
        var string=create("garland-rgb");plugin.manager().setMode(string,AnimationMode.CHASE);ticks(22);
        assertTrue(carriers()>0);var ids=geometry(string);
        plugin.manager().setOn(string,false);ticks(4);assertEquals(0,carriers());assertOff(string);
        var frozen=colours(string);ticks(60);assertEquals(frozen,colours(string));assertEquals(0,carriers());
        plugin.manager().setOn(string,true);ticks(22);assertTrue(carriers()>0);
        assertEquals(AnimationMode.CHASE,string.mode);assertEquals(ids,geometry(string));
        var lit=colours(string);var rgb=rgbColours();ticks(10);assertNotEquals(lit,colours(string));
        assertEquals(rgb.keySet(),rgbColours().keySet());assertNotEquals(rgb,rgbColours());
    }
    @Test void daylightPausesEffectsAndRgbUntilNightReturns() throws Exception {
        host();plugin.getConfig().set("night-only",true);reload();world.setTime(18000);
        var string=create("garland-rgb");plugin.manager().setMode(string,AnimationMode.CHASE);ticks(22);assertTrue(carriers()>0);
        var frozen=colours(string);world.setTime(6000);ticks(4);assertOff(string);assertEquals(0,carriers());
        ticks(60);assertEquals(frozen,colours(string));assertEquals(0,carriers());
        world.setTime(18000);ticks(22);assertTrue(carriers()>0);assertFalse(string.idle);
    }
    @Test void offImmediatelyRemovesOwnedVanillaLightEvenWithProgressiveSwitchEnabled() throws Exception {
        plugin.getConfig().set("animated-light",true);reload();
        var string=create("wall-inline-rainbow");plugin.manager().setMode(string,AnimationMode.CHASE);ticks(22);
        assertTrue(string.lightBlocks.stream().anyMatch(point->world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType()==Material.LIGHT));
        plugin.manager().setOn(string,false);ticks(2);assertOff(string);
        for(var point:string.lightBlocks)assertTrue(world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType().isAir());
        ticks(40);assertOff(string);
        plugin.manager().setOn(string,true);ticks(40);
        assertTrue(string.lightBlocks.stream().anyMatch(point->world.getBlockAt(point.getBlockX(),point.getBlockY(),point.getBlockZ()).getType()==Material.LIGHT));
    }
    @Test void upgradeEnablesBundledRgbEffectsButPreservesACustomSteadyOverride() throws Exception {
        String key="strobelights.surfaces.follow-effects";
        plugin.getConfig().set("config-version",10);plugin.getConfig().set(key,false);
        plugin.getConfig().setComments(key,List.of("false: steady surface colour; bulb animation still runs normally."));reload();
        assertEquals(16,plugin.getConfig().getInt("config-version"));assertTrue(plugin.getConfig().getBoolean(key));
        plugin.getConfig().set("config-version",10);plugin.getConfig().set(key,false);
        plugin.getConfig().setComments(key,List.of("My server deliberately keeps projected lighting steady"));reload();
        assertFalse(plugin.getConfig().getBoolean(key));
        assertEquals(List.of("My server deliberately keeps projected lighting steady"),plugin.getConfig().getComments(key));
    }
}
