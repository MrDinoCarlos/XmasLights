package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class GarlandFacingTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin();
    }
    @AfterEach void stop() { PluginTest.stopServer(); }

    @ParameterizedTest @CsvSource({"0,1,false","0,-1,false","1,0,false","-1,0,false",
            "0,1,true","0,-1,true","1,0,true","-1,0,true"})
    void clientRenderedFrontAndCulledSideMatchTheSupportingWall(int nx,int nz,boolean reversed) {
        var player=server.addPlayer(); var normal=new Vector(nx,0,nz);
        var tangent=new Vector(nz,0,-nx); var origin=new Vector(8.5,74.5,8.5);
        for(int along=-4;along<=4;along++) for(int y=72;y<=76;y++) {
            var p=origin.clone().add(tangent.clone().multiply(along));
            world.getBlockAt(p.getBlockX(),y,p.getBlockZ()).setType(Material.STONE);
        }
        var face=origin.clone().add(normal.clone().multiply(.5));
        var a=face.clone().subtract(tangent.clone().multiply(2)); var b=face.clone().add(tangent.clone().multiply(2));
        if(reversed) { var swap=a; a=b; b=swap; }
        var type=plugin.manager().getType("wall-garland-classic");
        var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),type,normal,null,false,null);
        for(int index=0;index<string.count;index++) {
            var body=string.garlandBodies.get(index); var led=(ItemDisplay)string.bulbs.get(index);
            assertEquals(-1,GarlandBacking.hiddenSide(world,LightGeometry.module(string,type,index),type.size()));
            assertTrue(body.getItemStack().getItemMeta().getItemModel().getKey().endsWith("body_no_back"));
            assertTrue(led.getItemStack().getItemMeta().getItemModel().getKey().endsWith("lights_no_back"));
            for(var display:new ItemDisplay[]{body,led}) {
                // Minecraft 1.21.4 ItemDisplayRenderer applies an extra Y=180°
                // after the display transformation, even for transform NONE.
                var actual=matrix(display).transformDirection(new Vector3f(0,0,-1)).normalize();
                assertEquals(1,actual.dot(normal.toVector3f()),1e-5,
                        "The surviving front must face away from the wall after Minecraft's item rotation");
            }
        }
    }
    @Test void existingGarlandsWithTheOldGeometrySignatureAreRebuiltOnReload() throws Exception {
        var player=server.addPlayer(); var a=new Vector(2,74,8); var b=new Vector(6,74,8);
        var string=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),
                plugin.manager().getType("wall-garland-classic"),new Vector(0,0,1),null,false,null);
        var old=string.garlandBodies.get(0); var id=string.id;
        old.setTransformationMatrix(matrix(old).rotateY((float)Math.PI));
        string.signature=string.signature.replace("|garland-facing=2","");
        plugin.manager().saveData(); plugin.reload(); server.getScheduler().performTicks(8);
        assertEquals(id,string.id); assertEquals(a,string.a); assertEquals(b,string.b);
        assertFalse(old.isValid()); assertTrue(string.signature.contains("|garland-facing=2"));
        assertEquals(1,matrix(string.garlandBodies.get(0)).transformDirection(new Vector3f(0,0,-1)).normalize().z,1e-5);
    }
    private org.joml.Matrix4f matrix(ItemDisplay display) {
        try {
            // MockBukkit's decomposition conjugates the left quaternion and
            // drops the right one. Use the exact matrix sent by Paper's API.
            var field=org.mockbukkit.mockbukkit.entity.DisplayMock.class.getDeclaredField("transformationMatrix"); field.setAccessible(true);
            return new org.joml.Matrix4f((org.joml.Matrix4f)field.get(display));
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
