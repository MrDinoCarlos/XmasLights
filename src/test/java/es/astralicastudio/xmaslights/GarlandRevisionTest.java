package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class GarlandRevisionTest {
    private PluginTest.TestServer server;
    private XmasLightsPlugin plugin;
    private PluginTest.PackPlayer player;
    private org.mockbukkit.mockbukkit.world.WorldMock world;
    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin(); player=(PluginTest.PackPlayer)server.addPlayer();
        player.teleport(new Location(world,1,70,1));
    }
    @AfterEach void stop() { PluginTest.stopServer(); }
    private LightString create(String id,double length,double z) {
        var a=new Vector(1,70,z); var b=new Vector(1+length,70,z);
        return plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType(id),new Vector(0,0,1),null,false,null);
    }
    private void reload() throws Exception {
        plugin.getConfig().save(plugin.getDataFolder().toPath().resolve("config.yml").toFile()); plugin.reload();
    }
    private String model(ItemDisplay display) { return display.getItemStack().getItemMeta().getItemModel().toString(); }
    @Test void namedColorsAreSmallTintedBulbsWithTheExactConfiguredRgbAndNoSkull() {
        var expected=Map.of("red",0xFF3030,"green",0x38E658,"blue",0x2878FF,"yellow",0xFFE12B,"orange",0xFF871F);
        int position=1;
        for(var entry:expected.entrySet()) {
            var type=plugin.manager().getType(entry.getKey()); assertFalse(type.isHead()); assertEquals(GarlandModel.BULB,type.model());
            assertTrue(type.size()<.3); var string=create(entry.getKey(),4,position++);
            for(var display:string.bulbs.values()) {
                var item=((ItemDisplay)display).getItemStack(); assertEquals(Material.GLOWSTONE,item.getType());
                assertEquals("xmaslights:bulb",item.getItemMeta().getItemModel().toString());
                assertEquals(entry.getValue().intValue(),item.getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB());
            }
        }
    }
    @Test void rgbGarlandsUseTheRequestedPaletteOnAllEightWrappedLedsWithoutMoreEntities() {
        var string=create("garland-rgb",4,1); var type=plugin.manager().getType("garland-rgb");
        for(var entry:string.bulbs.entrySet()) {
            var colors=((ItemDisplay)entry.getValue()).getItemStack().getItemMeta().getCustomModelDataComponent().getColors();
            assertEquals(8,colors.size());
            for(int led=0;led<8;led++) assertEquals(type.color(entry.getKey()+led),colors.get(led).asRGB());
        }
        assertEquals(string.count*2,world.getEntitiesByClass(Display.class).size());
    }
    @Test void shippedLegacyHeadsMigrateButCustomHeadsAndSavedStringsArePreserved() throws Exception {
        var string=create("yellow",4,1); UUID id=string.id;
        org.bukkit.configuration.file.YamlConfiguration original=new org.bukkit.configuration.file.YamlConfiguration();
        try(var jar=new JarFile("original/XmasLights-2.1.0.jar");var input=jar.getInputStream(jar.getJarEntry("types.yml"))) {
            original.loadFromString(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        original.set("types.red.custom",true);
        original.save(plugin.getDataFolder().toPath().resolve("types.yml").toFile()); plugin.reload(); server.getScheduler().performTicks(5);
        assertEquals(GarlandModel.BULB,plugin.manager().getType("yellow").model()); assertFalse(plugin.manager().getType("yellow").isHead());
        assertTrue(plugin.manager().getType("red").isHead()); assertNull(plugin.manager().getType("red").model());
        assertTrue(plugin.manager().getStrings().stream().anyMatch(saved->saved.id.equals(id)));
        assertEquals("xmaslights:bulb",model((ItemDisplay)string.bulbs.values().iterator().next()));
        try(var files=Files.list(plugin.getDataFolder().toPath())) { assertTrue(files.anyMatch(path->path.getFileName().toString().startsWith("types.yml.")&&path.toString().endsWith(".bak"))); }
    }
    @Test void performanceMigrationReplacesOldDefaultsAndKeepsCustomBudgets() throws Exception {
        plugin.getConfig().set("config-version",4); plugin.getConfig().set("limits.max-entities",20000);
        plugin.getConfig().set("limits.max-bulbs-per-segment",128); plugin.getConfig().set("strobelights.max-sources-per-player",5);
        reload(); assertEquals(8192,plugin.getConfig().getInt("limits.max-entities"));
        assertEquals(64,plugin.getConfig().getInt("limits.max-bulbs-per-segment")); assertEquals(5,plugin.getConfig().getInt("strobelights.max-sources-per-player"));
        assertTrue(plugin.usesModelPack());
    }
    @Test void vanillaModeUsesTwoBlockDisplaysPerModuleAndDoesNotSendAPack() throws Exception {
        plugin.getConfig().set("model-pack.enabled",false); reload();
        var string=create("garland-snowy",4,1); create("red",4,3);
        assertEquals(string.count,string.wires.size()); assertEquals(string.count,string.bulbs.size());
        assertTrue(string.wires.stream().allMatch(BlockDisplay.class::isInstance));
        assertTrue(string.bulbs.values().stream().allMatch(BlockDisplay.class::isInstance));
        assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
        server.getScheduler().performTicks(45); plugin.requestModelPack(player); assertTrue(player.packs.isEmpty());
        assertFalse(plugin.usesModelPack());
    }
    @Test void changingRendererKeepsTheSameStringAndReplacesEntitiesWithoutDuplicatingThem() throws Exception {
        var string=create("garland-classic",4,1); UUID id=string.id; var old=new ArrayList<>(string.wires);
        plugin.getConfig().set("model-pack.enabled",false); reload(); server.getScheduler().performTicks(5);
        assertEquals(id,string.id); assertTrue(old.stream().noneMatch(Entity::isValid));
        assertEquals(2*string.count,world.getEntitiesByClass(Display.class).size());
        assertTrue(string.wires.stream().allMatch(BlockDisplay.class::isInstance));
        plugin.getConfig().set("model-pack.enabled",true); reload(); server.getScheduler().performTicks(5);
        assertEquals(2*string.count,world.getEntitiesByClass(Display.class).size()); assertTrue(string.wires.stream().allMatch(ItemDisplay.class::isInstance));
    }
    @Test void wallGarlandsHaveSagAndTenMetersUseOnlyTwentyTwoDisplays() {
        var string=create("garland-classic",10,1); assertEquals(1.6,string.sag,1e-6);
        assertEquals(68.4,string.point(.5).getY(),1e-6); assertEquals(11,string.count);
        assertEquals(22,world.getEntitiesByClass(Display.class).size()); assertEquals(22,plugin.manager().geometryEntities());
    }
    @Test void longGarlandsAreCappedAtSixtyFourModules() {
        for(int x=2;x<=9;x++) for(int z=-1;z<=1;z++) world.loadChunk(x,z);
        var string=create("garland-classic",127,1); assertEquals(64,string.count);
        assertEquals(128,world.getEntitiesByClass(Display.class).size());
    }
    private void wall(Material material) { for(int x=0;x<=6;x++) for(int y=66;y<=72;y++) world.getBlockAt(x,y,0).setType(material); }
    @Test void opaqueWallRemovesRearDecorationAndRemovingItRestoresBothSidesWithoutRespawning() {
        wall(Material.STONE); var string=create("garland-classic",4,1);
        var body=string.garlandBodies.get(0); var led=(ItemDisplay)string.bulbs.get(0); UUID bodyId=body.getUniqueId(),ledId=led.getUniqueId();
        assertTrue(model(body).endsWith("body_no_back")); assertTrue(model(led).endsWith("lights_no_back"));
        var changed=world.getBlockAt(3,69,0); server.getPluginManager().callEvent(new BlockBreakEvent(changed,player)); wall(Material.AIR);
        server.getScheduler().performTicks(3);
        assertEquals("xmaslights:garland/classic/body",model(body)); assertEquals("xmaslights:garland/lights",model(led));
        assertEquals(bodyId,body.getUniqueId()); assertEquals(ledId,led.getUniqueId()); assertEquals(string.count*2,world.getEntitiesByClass(Display.class).size());
        wall(Material.GLASS); plugin.manager().backingChanged(changed); server.getScheduler().performTicks(3);
        assertEquals("xmaslights:garland/classic/body",model(body));
    }
    @Test void frontWallIsAlsoRecognizedAndUnloadedChunksAreNeverReadOrLoaded() {
        for(int x=1;x<=4;x++) for(int y=69;y<=71;y++) world.getBlockAt(x,y,1).setType(Material.STONE);
        var module=new LightGeometry.Module(new Vector(2.5,70,.69),new org.joml.Quaternionf(),1);
        assertEquals(1,GarlandBacking.hiddenSide(world,module,1));
        var unloaded=new LightGeometry.Module(new Vector(40,70,1),new org.joml.Quaternionf(),1);
        assertEquals(0,GarlandBacking.hiddenSide(world,unloaded,1)); assertFalse(world.isChunkLoaded(2,0));
    }
    @Test void reducingTheGlobalBudgetHidesOverflowAndKeepsAllSavedData() throws Exception {
        plugin.getConfig().set("limits.max-entities",256); reload();
        for(int index=0;index<12;index++) create("garland-classic",4,1+index*.5);
        assertEquals(120,world.getEntitiesByClass(Display.class).size());
        plugin.getConfig().set("limits.max-entities",64); reload(); server.getScheduler().performTicks(3);
        assertEquals(12,plugin.manager().getStrings().size()); assertEquals(6,plugin.manager().getStrings().stream().filter(string->string.spawned).count());
        assertEquals(60,world.getEntitiesByClass(Display.class).size());
        var hidden=plugin.manager().getStrings().stream().filter(string->!string.spawned).findFirst().orElseThrow();
        assertEquals("errors.limit-entities",plugin.manager().spawn(hidden));
    }
    @Test void oldColoredHeadKitsBecomeMatchingDyesAndKeepTheirAmount() {
        var old=new ItemStack(Material.PLAYER_HEAD,13); old.editMeta(meta->meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"kit"),PersistentDataType.STRING,"red"));
        player.getInventory().setItem(0,old); player.getInventory().setItem(1,new ItemStack(Material.PLAYER_HEAD)); plugin.manager().refreshLegacyKits(player);
        assertEquals(Material.RED_DYE,player.getInventory().getItem(0).getType()); assertEquals(13,player.getInventory().getItem(0).getAmount());
        assertEquals("red",plugin.manager().getKitType(player.getInventory().getItem(0))); assertEquals(Material.PLAYER_HEAD,player.getInventory().getItem(1).getType());
    }
    @Test void packHasBothSidesEightWrappedLedsCulledVariantsAndAnOriginalIcon() throws Exception {
        Map<String,byte[]> entries=new HashMap<>();
        try(var zip=new ZipInputStream(plugin.getResource("packs/"+ModelPackService.FILE_NAME))) {
            java.util.zip.ZipEntry next; while((next=zip.getNextEntry())!=null) entries.put(next.getName(),zip.readAllBytes());
        }
        var full=json(entries,"assets/xmaslights/models/item/garland_classic_body.json");
        var culled=json(entries,"assets/xmaslights/models/item/garland_classic_body_no_back.json");
        Set<String> names=new HashSet<>(); for(var element:full.getAsJsonArray("elements")) names.add(element.getAsJsonObject().get("name").getAsString());
        assertTrue(names.contains("bauble_-1_0")); assertTrue(names.contains("bauble_1_0"));
        for(var element:culled.getAsJsonArray("elements")) assertFalse(element.getAsJsonObject().get("name").getAsString().startsWith("bauble_-1"));
        var leds=json(entries,"assets/xmaslights/models/item/garland_lights.json").getAsJsonArray("elements"); assertEquals(8,leds.size());
        Set<Integer> quadrants=new HashSet<>();
        for(var element:leds) { var from=element.getAsJsonObject().getAsJsonArray("from"); quadrants.add((from.get(1).getAsDouble()>8?1:0)+(from.get(2).getAsDouble()>8?2:0)); }
        assertEquals(4,quadrants.size()); assertTrue(culled.getAsJsonArray("elements").size()<full.getAsJsonArray("elements").size());
        var icon=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(entries.get("pack.png"))); assertEquals(128,icon.getWidth()); assertEquals(128,icon.getHeight());
        assertTrue(entries.containsKey("assets/xmaslights/items/bulb.json"));
        var white=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(entries.get("assets/xmaslights/textures/item/led.png")));
        assertEquals(0xFFFFFF,white.getRGB(0,0)&0xFFFFFF);
    }
    private com.google.gson.JsonObject json(Map<String,byte[]> entries,String key) {
        return com.google.gson.JsonParser.parseString(new String(entries.get(key),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void wideBulbAndBaubleLedgesHaveFacesWhenSeenFromAboveOrBelow() throws Exception {
        Map<String,byte[]> entries=new HashMap<>();
        try(var zip=new ZipInputStream(plugin.getResource("packs/"+ModelPackService.FILE_NAME))) {
            java.util.zip.ZipEntry next; while((next=zip.getNextEntry())!=null) entries.put(next.getName(),zip.readAllBytes());
        }
        for(String model:List.of("colored_bulb","garland_classic_body","garland_snowy_body","garland_rgb_body")) {
            int wide=0;
            for(var element:json(entries,"assets/xmaslights/models/item/"+model+".json").getAsJsonArray("elements")) {
                var part=element.getAsJsonObject(); String name=part.get("name").getAsString();
                if(name.equals("tinted_bulb_1")||name.equals("bauble_-1_1")||name.equals("bauble_1_1")) {
                    assertTrue(part.getAsJsonObject("faces").has("up"),name); assertTrue(part.getAsJsonObject("faces").has("down"),name); wide++;
                }
            }
            assertEquals(model.equals("colored_bulb")?1:2,wide);
        }
    }
}
