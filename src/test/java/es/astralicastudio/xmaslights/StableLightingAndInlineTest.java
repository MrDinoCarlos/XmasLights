package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StableLightingAndInlineTest {
    PluginTest.TestServer server;XmasLightsPlugin plugin;org.mockbukkit.mockbukkit.world.WorldMock world;
    @BeforeEach void start(){server=MockBukkit.mock(new PluginTest.TestServer());world=server.addSimpleWorld("world");for(int x=-1;x<=1;x++)for(int z=-1;z<=4;z++)world.loadChunk(x,z);plugin=PluginTest.loadPlugin();}
    @AfterEach void stop(){PluginTest.stopServer();}
    @Test void allWallFamiliesHaveAMatchingCenteredVersionWithoutDropDisplays(){
        var player=server.addPlayer();int row=0;
        for(var type:plugin.manager().getTypes().values())if(type.id().startsWith("wall-inline-")){
            var original=plugin.manager().getType(type.id().replace("wall-inline-","wall-"));assertNotNull(original);
            assertEquals(original.colors(),type.colors());assertEquals(original.model(),type.model());assertEquals(0,type.chain());assertEquals(0,type.sag());assertTrue(type.wall());
            var a=new Vector(1,74,1+row++*2);var b=a.clone().add(new Vector(6,0,0));
            var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),type,new Vector(0,0,1),null,false,null);
            if(type.garland())assertEquals(string.count,string.wires.size());
            else{assertEquals(1,string.wires.size());assertEquals(string.count+1,string.bulbs.size()+string.wires.size());}
        }
        assertEquals(17,row);
    }
    @Test void everyStableSlotIsUniqueAndPreservesRequestedColourAndRadius(){
        Set<Integer> values=new HashSet<>();
        for(int slot=0;slot<128;slot++){
            int value=StrobeProtocol.stableSource(0xFFCC66,9,slot,2.25);assertTrue(values.add(value));assertTrue(StrobeProtocol.stable(value));
            assertEquals(9,(value>>>8)&15);assertEquals(7,(value>>>4)&15);assertEquals(4,value&15);assertEquals(2,StrobeProtocol.radius(value),1e-8);
        }
        assertThrows(IllegalArgumentException.class,()->StrobeProtocol.stableSource(0xFFFFFF,10,128,2));
    }
    @Test void inlineLampsUseSocketlessModelsAndKeepTheirRgbOriginOnTheCable(){
        var player=server.addPlayer();int row=0;
        for(var type:plugin.manager().getTypes().values())if(type.modelLamp()&&type.wall()&&type.chain()==0){
            var a=new Vector(1,74,1+row++*2);var b=a.clone().add(new Vector(6,0,0));
            var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),type,new Vector(0,0,1),null,false,null);
            for(var entry:string.bulbs.entrySet()){
                var display=(ItemDisplay)entry.getValue();
                assertTrue(display.getItemStack().getItemMeta().getItemModel().getKey().endsWith("_inline"),type.id());
                assertEquals(type.color(entry.getKey()),display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB());
                assertEquals(LightGeometry.bulbPoint(string,type,entry.getKey()).getY(),LightGeometry.lightPoint(string,type,entry.getKey()).getY(),1e-6);
            }
        }
        assertEquals(10,row);
    }
    @Test void inlineResourceGeometryHasNoSocketAndItsLuminousBodyIsCentered() throws Exception {
        var models=new HashMap<String,com.google.gson.JsonObject>();
        try(var input=new java.util.zip.ZipInputStream(plugin.getResource("packs/XmasLights-Models-1.21.4.zip"))){
            for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry()){
                if(entry.getName().matches("assets/xmaslights/models/item/colored_(bulb|cube)(_inline)?\\.json"))
                    models.put(entry.getName(),com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        for(String shape:List.of("bulb","cube")){
            String path="assets/xmaslights/models/item/colored_"+shape;
            var inline=models.get(path+"_inline.json").getAsJsonArray("elements");
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(var value:inline){
                var element=value.getAsJsonObject();assertFalse(element.get("name").getAsString().contains("socket"));
                low=Math.min(low,element.getAsJsonArray("from").get(1).getAsDouble());
                high=Math.max(high,element.getAsJsonArray("to").get(1).getAsDouble());
            }
            assertEquals(8,(low+high)/2,1e-9);
            assertTrue(models.get(path+".json").getAsJsonArray("elements").asList().stream()
                .anyMatch(value->value.getAsJsonObject().get("name").getAsString().contains("socket")));
        }
    }
    @Test void nativeInlineLanternBodiesAreCenteredOnTheCableRatherThanByTheirBlockBounds(){
        var player=server.addPlayer();int row=0;
        for(String id:List.of("wall-inline-lanterns","wall-inline-soul-lanterns","wall-inline-mixed-lanterns")){
            var type=plugin.manager().getType(id);var a=new Vector(1,74,1+row++*2);var b=a.clone().add(new Vector(6,0,0));
            var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),type,new Vector(0,0,1),null,false,null);
            for(var entry:string.bulbs.entrySet()){
                var display=(BlockDisplay)entry.getValue();var transform=display.getTransformation();
                assertFalse(((org.bukkit.block.data.type.Lantern)display.getBlock()).isHanging());
                assertEquals(LightGeometry.bulbPoint(string,type,entry.getKey()).getY(),display.getLocation().getY()+transform.getTranslation().y+transform.getScale().y*.21875,1e-5);
            }
        }
    }
    @Test void dimmingAStableSourceDoesNotChangeItsFootprint(){
        int bright=StrobeProtocol.stableSource(0xFF3030,12,7,2.25),dim=StrobeProtocol.stableSource(0xFF3030,2,7,2.25);
        assertEquals(StrobeProtocol.radius(bright),StrobeProtocol.radius(dim));assertTrue(StrobeProtocol.peak(bright)>StrobeProtocol.peak(dim));
        assertEquals(0,StrobeProtocol.peak(StrobeProtocol.stableSource(0xFF3030,0,7,2.25)));
    }
    @Test void bundled27LightingIsMigratedButCustomSettingsArePreserved() throws Exception {
        var config=plugin.getConfig();config.set("config-version",8);config.set("strobelights.intensity-scale",.90);config.set("strobelights.view-distance",96.0);
        config.set("strobelights.surfaces.max-radius",3.25);config.set("strobelights.surfaces.distance-fade",5.0);
        config.save(plugin.getDataFolder().toPath().resolve("config.yml").toFile());plugin.reload();
        assertEquals(16,plugin.getConfig().getInt("config-version"));assertEquals(.55,plugin.getConfig().getDouble("strobelights.intensity-scale"));
        assertEquals(0,plugin.getConfig().getDouble("strobelights.view-distance-chunks"));assertEquals(2.25,plugin.getConfig().getDouble("strobelights.surfaces.max-radius"));assertEquals(5,plugin.getConfig().getDouble("strobelights.surfaces.distance-fade"));
    }
}
