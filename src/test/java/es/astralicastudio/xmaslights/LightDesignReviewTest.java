package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LightDesignReviewTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    private PluginTest.PackPlayer player;
    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=7;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin(); player=(PluginTest.PackPlayer)server.addPlayer(); player.teleport(new Location(world,1,74,1));
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private LightString create(String id,int row) {
        var a=new Vector(1,74,1+row*2); var b=new Vector(7,74,1+row*2);
        return plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType(id),new Vector(0,0,1),null,false,null);
    }
    private void reload() throws Exception { plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload(); server.getScheduler().performTicks(6); }

    @Test void allFiftyOneTypesHaveTheRequestedWallOrHangingGeometry() throws Exception {
        var catalog=new com.google.gson.JsonArray(); int row=0;
        for(var type:plugin.manager().getTypes().values()) {
            var string=create(type.id(),row++);
            if(type.id().startsWith("wall-")) assertEquals(0,string.sag,type.id());
            else { assertTrue(string.sag>0,type.id()); assertTrue(string.point(.5).getY()<string.a.getY(),type.id()); }
            if(!type.garland()) {
                assertTrue(type.wire(),type.id());
                if(type.id().startsWith("wall-inline-")) assertEquals(0,type.chain(),type.id());
                else assertTrue(type.chain()>0,type.id());
                assertEquals((type.chain()>0?string.count:0)+LightGeometry.wireSegments(string.sag,.02),string.wires.size(),type.id());
            } else assertEquals(string.count,string.wires.size(),type.id());
            assertTrue(string.count<=64,type.id());
            if(System.getProperty("xmaslights.preview.manifest")!=null) catalog.add(snapshot(string,type));
        }
        assertEquals(51,row); assertTrue(plugin.manager().geometryEntities()<=4096);
        String output=System.getProperty("xmaslights.preview.manifest");
        if(output!=null) Files.writeString(Path.of(output),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(catalog));
    }
    @Test void packLampSocketsMeetTheirDropAndTheirRgbIsTheConfiguredColour() {
        int row=0;
        for(var type:plugin.manager().getTypes().values()) if(type.modelLamp()) {
            var string=create(type.id(),row++);
            for(var entry:string.bulbs.entrySet()) {
                var display=(ItemDisplay)entry.getValue(); var transform=display.getTransformation();
                if(type.chain()==0) {
                    assertEquals(LightGeometry.bulbPoint(string,type,entry.getKey()).getY(),display.getLocation().getY()+transform.getTranslation().y,1e-5,type.id());
                    continue;
                }
                double socketTop=display.getLocation().getY()+transform.getTranslation().y+type.size()*(type.model()==GarlandModel.BULB?.375:.5);
                assertEquals(LightGeometry.bulbPoint(string,type,entry.getKey()).getY()-type.chain(),socketTop,1e-5,type.id());
                assertEquals(type.color(entry.getKey()),display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB(),type.id());
            }
        }
        assertEquals(30,row);
    }
    @Test void everyCubeFamilyUsesTheNewOpaqueSocketModelAndNoGlassBlocks() {
        int row=0;
        for(var id:List.of("warm","multicolor","red-white","rainbow","wall-warm","wall-multicolor","wall-red-white","wall-rainbow")) {
            var type=plugin.manager().getType(id); assertEquals(GarlandModel.CUBE,type.model()); assertFalse(type.garland());
            assertTrue(type.bulbs().stream().noneMatch(data->data.getMaterial().name().contains("GLASS")));
            var string=create(id,row++);
            for(var bulb:string.bulbs.values()) assertEquals("xmaslights:cube",((ItemDisplay)bulb).getItemStack().getItemMeta().getItemModel().toString());
        }
    }
    @Test void shortPendantCablesAreThinAndDoNotAddAnEntityForEachModelPart() {
        var string=create("rainbow",0); var type=plugin.manager().getType("rainbow");
        int drops=0;
        for(var entity:string.wires) if(entity instanceof BlockDisplay display && display.getTransformation().getScale().y<.2) {
            var scale=display.getTransformation().getScale();
            if(Math.abs(scale.y-type.chain())<.01) { assertTrue(scale.x<=.0251); assertTrue(scale.z<=.0251); drops++; }
        }
        assertEquals(string.count,drops); assertEquals(2*(type.chain()>0?string.count:0)+LightGeometry.wireSegments(string.sag,.02),world.getEntitiesByClass(Display.class).size());
    }
    @Test void cablePiecesReallyMeetAlongTheCurveWithoutGapsOrReversedRotations() {
        var string=create("rainbow",0); int segments=LightGeometry.wireSegments(string.sag,.02), found=0;
        for(var entity:string.wires) if(entity instanceof BlockDisplay display) {
            var matrix=matrix(display); var scale=matrix.getScale(new org.joml.Vector3f());
            if(scale.z<.2) continue;
            var start=matrix.transformPosition(new org.joml.Vector3f(.5f,.5f,0));
            var end=matrix.transformPosition(new org.joml.Vector3f(.5f,.5f,1));
            var origin=display.getLocation().toVector();
            var a=origin.clone().add(new Vector(start.x,start.y,start.z)); var b=origin.clone().add(new Vector(end.x,end.y,end.z));
            int index=(int)Math.round((a.getX()-string.a.getX())/string.length()*segments);
            assertTrue(a.distance(string.point((double)index/segments))<.025);
            assertTrue(b.distance(string.point((index+1.0)/segments))<.025); found++;
        }
        assertEquals(segments,found);
    }
    @Test void wallCableAndDropsUseTheSameOffsetFromTheSupportPlane() {
        var string=create("wall-rainbow",0); var type=plugin.manager().getType("wall-rainbow");
        double expected=string.a.getZ()+type.size()*.5+.01;
        for(var entity:string.wires) assertEquals(expected,entity.getLocation().getZ(),1e-6);
        assertEquals(0,string.sag);
    }
    @Test void anExplicitStraightCustomOutlineIsStillSupported() throws Exception {
        var file=plugin.getDataFolder().toPath().resolve("types.yml"); var types=SafeYaml.load(file);
        types.set("types.wall-rainbow.sag",0); types.set("types.wall-rainbow.chain",0); types.set("types.wall-rainbow.custom",true);
        SafeYaml.save(types,file,false); plugin.reload(); var string=create("wall-rainbow",0);
        assertEquals(0,string.sag); assertEquals(1,string.wires.size()); assertEquals(0,plugin.manager().getType("wall-rainbow").chain());
    }
    @Test void oldStraightWallStringsRegenerateWithoutLosingIdentityOrGroups() throws Exception {
        var string=create("wall-multicolor",0); UUID id=string.id; string.group="Garden"; string.sag=0; string.signature="5|old-layout";
        assertTrue(plugin.manager().saveData()); plugin.manager().scheduleAutoUpdate(); server.getScheduler().performTicks(6);
        assertEquals(id,string.id); assertEquals("Garden",string.group); assertEquals(0,string.sag); assertTrue(string.spawned);
    }
    @Test void upgradesReplaceOnlyRecognisedBundledDesignsAndKeepExplicitCustomTypes() throws Exception {
        var defaults=ResourcesTest.resource("types.yml"); var previous=new org.bukkit.configuration.file.YamlConfiguration();
        previous.set("types.rainbow.blocks",List.of("minecraft:red_stained_glass","minecraft:orange_stained_glass","minecraft:yellow_stained_glass","minecraft:lime_stained_glass","minecraft:green_stained_glass","minecraft:cyan_stained_glass","minecraft:light_blue_stained_glass","minecraft:blue_stained_glass","minecraft:purple_stained_glass","minecraft:magenta_stained_glass","minecraft:pink_stained_glass"));
        previous.set("types.rainbow.size",.42); previous.set("types.rainbow.spacing",2.3);
        previous.set("types.red.model","bulb"); previous.set("types.red.blocks",defaults.get("types.red.blocks")); previous.set("types.red.chain",0);
        previous.set("types.warm.custom",true); previous.set("types.warm.blocks",List.of("minecraft:ochre_froglight")); previous.set("types.warm.size",.42);
        assertTrue(ResourceUpdater.migrateLampDesigns(previous,defaults));
        assertEquals("cube",previous.getString("types.rainbow.model")); assertEquals(.18,previous.getDouble("types.rainbow.chain")); assertEquals(2.3,previous.getDouble("types.rainbow.spacing"));
        assertEquals(0,previous.getDouble("types.red.chain")); assertNull(previous.getString("types.warm.model"));
    }
    @Test void vanillaModeKeepsTheSameCurvesAndDropsWithoutRequestingTheModelPack() throws Exception {
        plugin.getConfig().set("model-pack.enabled",false); reload(); var string=create("rainbow",0);
        assertTrue(string.sag>0); assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
        assertTrue(string.bulbs.values().stream().allMatch(BlockDisplay.class::isInstance));
        server.getScheduler().performTicks(45); assertTrue(player.packs.isEmpty());
    }
    @Test void defaultSurfaceLightingFollowsEffectsAndHasValidatedBoundedWorkSettings() throws Exception {
        var config=ResourcesTest.resource("config.yml"); var types=ResourcesTest.resource("types.yml");
        assertTrue(config.getBoolean("strobelights.surfaces.follow-effects")); assertEquals(64,config.getInt("strobelights.surfaces.probes-per-update"));
        config.set("strobelights.surfaces.probes-per-update",1.5); assertThrows(ConfigProblem.class,()->LightManager.validateSettings(config,types));
        config.set("strobelights.surfaces.probes-per-update",12); config.set("strobelights.surfaces.reach",100);
        assertThrows(ConfigProblem.class,()->LightManager.validateSettings(config,types));
    }
    private com.google.gson.JsonObject snapshot(LightString string,LightType type) {
        var json=new com.google.gson.JsonObject(); json.addProperty("id",type.id()); json.addProperty("sag",string.sag);
        var entities=new com.google.gson.JsonArray(); var all=new ArrayList<Entity>(string.wires); all.addAll(string.bulbs.values());
        for(var entity:all) {
            var display=(Display)entity; var value=new com.google.gson.JsonObject(); var t=display.getTransformation();
            var p=display.getLocation().toVector().subtract(string.a); value.add("position",array(p.getX(),p.getY(),p.getZ()));
            value.add("translation",array(t.getTranslation().x,t.getTranslation().y,t.getTranslation().z));
            value.add("scale",array(t.getScale().x,t.getScale().y,t.getScale().z));
            value.add("rotation",array(t.getLeftRotation().x,t.getLeftRotation().y,t.getLeftRotation().z,t.getLeftRotation().w));
            var raw=matrix(display).get(new float[16]); var exact=new com.google.gson.JsonArray();
            for(float component:raw) exact.add(component); value.add("matrix",exact);
            if(display instanceof ItemDisplay item) {
                value.addProperty("model",item.getItemStack().getItemMeta().getItemModel().toString());
                var colours=item.getItemStack().getItemMeta().getCustomModelDataComponent().getColors(); var rgb=new com.google.gson.JsonArray();
                for(var colour:colours) rgb.add(colour.asRGB()); value.add("colours",rgb);
            } else if(display instanceof BlockDisplay block) value.addProperty("block",block.getBlock().getAsString());
            entities.add(value);
        }
        json.add("entities",entities); return json;
    }
    @Test void oldLightingDefaultsUpgradeOnceWithABackupAndLaterEditsRemainEditable() throws Exception {
        plugin.getConfig().set("config-version",5); plugin.getConfig().set("animated-light",true); plugin.getConfig().set("strobelights.expansion",.5);
        reload(); assertTrue(plugin.getConfig().getBoolean("animated-light")); assertFalse(plugin.getConfig().contains("strobelights.expansion"));
        try(var files=Files.list(plugin.getDataFolder().toPath())) { assertTrue(files.anyMatch(file->file.getFileName().toString().startsWith("config.yml.")&&file.toString().endsWith(".bak"))); }
        plugin.getConfig().set("animated-light",true); plugin.getConfig().set("strobelights.expansion",.75); reload();
        assertTrue(plugin.getConfig().getBoolean("animated-light")); assertFalse(plugin.getConfig().contains("strobelights.expansion"));
    }
    private com.google.gson.JsonArray array(double... values) { var array=new com.google.gson.JsonArray(); for(double value:values) array.add(value); return array; }
    private org.joml.Matrix4f matrix(Display display) {
        try {
            // MockBukkit's getTransformation conjugates its left quaternion and
            // discards the actual right rotation. Read the exact matrix supplied by Paper's API.
            var field=org.mockbukkit.mockbukkit.entity.DisplayMock.class.getDeclaredField("transformationMatrix"); field.setAccessible(true);
            return new org.joml.Matrix4f((org.joml.Matrix4f)field.get(display));
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
