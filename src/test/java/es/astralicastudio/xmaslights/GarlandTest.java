package es.astralicastudio.xmaslights;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class GarlandTest {
    private org.mockbukkit.mockbukkit.ServerMock server;
    private XmasLightsPlugin plugin;
    private org.mockbukkit.mockbukkit.entity.PlayerMock player;
    @BeforeEach void start() {
        server = MockBukkit.mock(new PluginTest.TestServer()); var world = server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin(); player=server.addPlayer(); player.teleport(new Location(world,1,70,1));
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private LightString create(String type, Vector a, Vector b, UUID chain, boolean continued) {
        return plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType(type),new Vector(0,0,1),chain,continued,null);
    }
    @Test void detailedGeometryKeepsTwoDisplaysAndAddsOnlySoftFoliageBrightness() {
        LightString string=create("garland-classic",new Vector(1,70,1),new Vector(5,70,1),null,false);
        assertEquals(5,string.count); assertEquals(string.count,string.wires.size()); assertEquals(string.count,string.bulbs.size());
        for(var entity:string.wires) { assertEquals(5,((ItemDisplay)entity).getBrightness().getBlockLight()); assertFalse(entity.isVisibleByDefault()); }
        for(var display:string.bulbs.values()) { assertEquals(15,display.getBrightness().getBlockLight()); assertFalse(display.isVisibleByDefault()); }
        UUID id=string.bulbs.get(0).getUniqueId(); plugin.manager().setMode(string,AnimationMode.BLINK); server.getScheduler().performTicks(10);
        assertEquals(id,string.bulbs.get(0).getUniqueId()); assertEquals(2,string.bulbs.get(0).getBrightness().getBlockLight());
        for(var entity:string.wires) assertEquals(5,((ItemDisplay)entity).getBrightness().getBlockLight());
    }
    @Test void packStatusOnlyRevealsModelsToThePlayerWhoLoadedThem() {
        var other=server.addPlayer(); other.teleport(player.getLocation());
        LightString string=create("garland-classic",new Vector(1,70,1),new Vector(5,70,1),null,false);
        var body=string.wires.iterator().next(); assertFalse(player.canSee(body)); assertFalse(other.canSee(body));
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        assertFalse(player.canSee(body));
        UUID pack=UUID.nameUUIDFromBytes("XmasLights:models:1.21.4".getBytes(StandardCharsets.UTF_8));
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,pack,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        assertTrue(player.canSee(body)); assertFalse(other.canSee(body));
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,pack,PlayerResourcePackStatusEvent.Status.DISCARDED));
        assertFalse(player.canSee(body));
    }
    @Test void verticalWallModelsFaceTheClickedWallNormal() {
        LightString string=create("garland-classic",new Vector(2,66,1),new Vector(2,70,1),null,false);
        var module=LightGeometry.module(string,plugin.manager().getType(string.type),0);
        var front=module.rotation().transform(new org.joml.Vector3f(0,0,1));
        var tangent=module.rotation().transform(new org.joml.Vector3f(1,0,0));
        assertEquals(1,front.z,1e-5); assertEquals(1,tangent.y,1e-5); assertEquals(0,string.sag);
    }
    @Test void continuingAnOutlineDoesNotDropItsFirstFoliageModule() {
        LightString first=create("garland-classic",new Vector(1,70,1),new Vector(5,70,1),null,false);
        LightString second=create("garland-classic",new Vector(5,70,1),new Vector(9,70,1),first.chain,true);
        assertTrue(second.skipStart); assertEquals(second.count,second.bulbs.size()); assertEquals(second.count,second.wires.size());
    }
    @Test void modelPackIsNamespacedAndEveryModelTextureReferenceResolves() throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(var zip=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/packs/"+ModelPackService.FILE_NAME)))) {
            java.util.zip.ZipEntry entry; while((entry=zip.getNextEntry())!=null) entries.put(entry.getName(),zip.readAllBytes());
        }
        assertTrue(entries.keySet().stream().noneMatch(path->path.startsWith("assets/minecraft/")||path.startsWith("assets/strobelights/")||path.contains("/shaders/")));
        for(var entry:entries.entrySet()) if(entry.getKey().contains("/models/")&&entry.getKey().endsWith(".json")) {
            var model=com.google.gson.JsonParser.parseString(new String(entry.getValue(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(model.getAsJsonArray("elements").size()<=48,entry.getKey());
            for(var texture:model.getAsJsonObject("textures").entrySet()) {
                String path="assets/"+texture.getValue().getAsString().replace(':','/')+".png";
                path=path.replace("assets/xmaslights/", "assets/xmaslights/textures/");
                assertTrue(entries.containsKey(path),path);
            }
        }
        assertTrue(entries.containsKey("assets/xmaslights/items/garland/classic/body.json"));
        assertTrue(entries.containsKey("assets/xmaslights/items/garland/lights.json"));
    }
    @Test void standalonePackUrlsRejectLocalFilesAndCredentials() {
        assertDoesNotThrow(()->ModelPackService.validateUrl("")); assertDoesNotThrow(()->ModelPackService.validateUrl("https://example.test/models.zip"));
        assertThrows(IllegalArgumentException.class,()->ModelPackService.validateUrl("file:///C:/models.zip"));
        assertThrows(IllegalArgumentException.class,()->ModelPackService.validateUrl("https://user:password@example.test/models.zip"));
    }
    @Test void drapedGarlandUsesItsOwnSagWithoutChangingTheOriginalLights() {
        LightString draped=create("garland-draped",new Vector(1,70,1),new Vector(5,70,1),null,false);
        LightString original=create("warm",new Vector(1,70,3),new Vector(5,70,3),null,false);
        assertEquals(.8,draped.sag,1e-6); assertEquals(.32,original.sag,1e-6);
    }
    private void packStatus(UUID id, PlayerResourcePackStatusEvent.Status status) {
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(player,id,status));
    }
    @Test @SuppressWarnings("deprecation") void lateSharedProviderOnlyAcceptsItsPackAndHidesModelsWhenRemoved() {
        TrpPackFixture.address="https://example.test/trpserveredition/0123456789abcdef0123456789abcdef01234567.zip";
        var host=MockBukkit.loadWith(TrpPackFixture.class,new org.bukkit.plugin.PluginDescriptionFile("TRPServerEdition","test",TrpPackFixture.class.getName()));
        server.getScheduler().performTicks(25);
        assertEquals(1,host.registrations); assertTrue(host.requests>0);
        var string=create("garland-classic",new Vector(1,70,1),new Vector(5,70,1),null,false);
        var body=string.wires.iterator().next(); UUID unrelated=UUID.randomUUID();
        packStatus(unrelated,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED); assertFalse(player.canSee(body));
        UUID pack=UUID.nameUUIDFromBytes(HexFormat.of().parseHex("0123456789abcdef0123456789abcdef01234567"));
        packStatus(pack,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED); assertTrue(player.canSee(body));
        packStatus(unrelated,PlayerResourcePackStatusEvent.Status.DISCARDED); assertTrue(player.canSee(body));
        packStatus(pack,PlayerResourcePackStatusEvent.Status.DISCARDED); assertFalse(player.canSee(body));
        packStatus(pack,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED); assertTrue(player.canSee(body));
        server.getPluginManager().disablePlugin(host); assertFalse(player.canSee(body));
    }
    @Test @SuppressWarnings("deprecation") void externallyManagedSharedPackKeepsItsIdentityAfterLoading() {
        TrpPackFixture.address="https://example.test/combined.zip";
        MockBukkit.loadWith(TrpPackFixture.class,new org.bukkit.plugin.PluginDescriptionFile("TRPServerEdition","test",TrpPackFixture.class.getName()));
        server.getScheduler().performTicks(25);
        var string=create("garland-classic",new Vector(1,70,1),new Vector(5,70,1),null,false);
        var body=string.wires.iterator().next(); UUID pack=UUID.randomUUID();
        packStatus(pack,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED); assertTrue(player.canSee(body));
        packStatus(UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.DISCARDED); assertTrue(player.canSee(body));
        packStatus(pack,PlayerResourcePackStatusEvent.Status.DISCARDED); assertFalse(player.canSee(body));
    }
    @Test void longerRgbPalettesKeepHeadModelsValidThroughPlacementAndChase() throws Exception {
        var palette=List.of("#FF0000","#00FF00","#0000FF","#FFFFFF");
        plugin.getTypesConfig().set("types.red.rgb",palette);
        plugin.getTypesConfig().set("types.christmas.rgb",palette);
        for(String id:List.of("red","christmas")) {
            plugin.getTypesConfig().set("types."+id+".model",null);
            plugin.getTypesConfig().set("types."+id+".blocks",null);
        }
        String texture="8e717b485d95b3bd14db1350c55a29da99ee1d3195c050617088a9677ac42";
        plugin.getTypesConfig().set("types.red.heads",List.of(texture));
        plugin.getTypesConfig().set("types.christmas.heads",List.of(texture,"47a985c31ad461e322b39c433eae79ff46fb64800cdecadf2f6d0f9727252","91ff6a55e971931eb0626565dad7771419fae9887aa6d52bf1995717860c"));
        plugin.manager().loadConfig();
        var red=create("red",new Vector(1,70,1),new Vector(7,70,1),null,false);
        assertEquals(red.count,red.bulbs.size());
        var type=plugin.manager().getType("christmas"); assertEquals(12,type.variants());
        var string=create("christmas",new Vector(1,70,3),new Vector(9,70,3),null,false);
        for(var entry:string.bulbs.entrySet()) assertTrue(((ItemDisplay)entry.getValue()).getItemStack().isSimilar(type.heads().get(entry.getKey()%3)));
        plugin.manager().setMode(string,AnimationMode.CHASE); server.getScheduler().performTicks(20);
        for(var entry:string.bulbs.entrySet()) assertTrue(((ItemDisplay)entry.getValue()).getItemStack().isSimilar(type.heads().get((entry.getKey()+2)%3)));
    }
}
