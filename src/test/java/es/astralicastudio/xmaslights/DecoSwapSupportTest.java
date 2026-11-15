package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DecoSwapSupportTest {
    PluginTest.TestServer server;
    XmasLightsPlugin plugin;
    org.mockbukkit.mockbukkit.world.WorldMock world;
    org.mockbukkit.mockbukkit.entity.PlayerMock player;
    Location home, target;
    @BeforeEach void start() {
        server=MockBukkit.mock(new PluginTest.TestServer()); world=server.addSimpleWorld("world");
        for(int x=-2;x<=4;x++) for(int z=-2;z<=4;z++) world.loadChunk(x,z);
        plugin=PluginTest.loadPlugin(); player=server.addPlayer(); player.addAttachment(plugin,"xmaslights.use",true); player.teleport(new Location(world,2,74,2));
        home=new Location(world,.25,72,.75); target=new Location(world,32.75,80,32.25);
    }
    @AfterEach void stop(){PluginTest.stopServer();}
    LightString create(String type) {
        var a=new Vector(1,74,2); var b=new Vector(7,74,2);
        return plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType(type),new Vector(0,0,1),null,false,null);
    }
    List<Entity> parts(LightString s){var all=new ArrayList<Entity>(s.wires);all.addAll(s.bulbs.values());return all;}
    record Saved(Entity entity, byte[] data){}
    List<Saved> capture(LightString s){return parts(s).stream().map(e->new Saved(e,plugin.decoSwap().capture(e,home).get("state").getBytes(StandardCharsets.UTF_8))).toList();}
    List<byte[]> payloads(List<Saved> saved){return saved.stream().map(Saved::data).toList();}
    @ParameterizedTest @ValueSource(strings={"wall-warm","wall-inline-warm"})
    void continuedWallSegmentsUseTheirActualPartCountForSelectionAndCopy(String type) throws Exception {
        var first=create(type);
        var a=new Vector(7,74,2); var b=new Vector(13,74,2);
        var continued=plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),
                plugin.manager().getType(type),new Vector(0,0,1),first.chain,true,null);
        assertEquals(parts(continued).size(),plugin.manager().importCost(continued));
        assertEquals(parts(continued).size(),plugin.decoSwap().expandSelection(
                List.of(continued.bulbs.values().iterator().next().getUniqueId()),player).size());
        var copy=paste(capture(continued),UUID.randomUUID(),0);
        assertTrue(copy.skipStart);
        assertEquals(parts(continued).size(),parts(copy).size());
    }
    LightString paste(List<Saved> saved, UUID deployment, int rotation) throws Exception {
        plugin.decoSwap().validateDeployment(payloads(saved),target,rotation,player.getUniqueId());
        UUID decoration=UUID.randomUUID(); int local=0;
        for(var item:saved) {
            var source=(Display)item.entity; var relative=source.getLocation().subtract(home).toVector();
            var rotated=switch(rotation){case 90->new Vector(-relative.getZ(),relative.getY(),relative.getX());case 180->new Vector(-relative.getX(),relative.getY(),-relative.getZ());default->relative;};
            var clone=(Display)world.spawnEntity(target.clone().add(rotated),source.getType());
            clone.setTransformation(source.getTransformation());
            if(source instanceof ItemDisplay a && clone instanceof ItemDisplay b)b.setItemStack(a.getItemStack().clone());
            if(source instanceof BlockDisplay a && clone instanceof BlockDisplay b)b.setBlock(a.getBlock().clone());
            source.getPersistentDataContainer().copyTo(clone.getPersistentDataContainer(),true);
            clone.getPersistentDataContainer().set(NamespacedKey.fromString("decoswap:deployment_id"),PersistentDataType.STRING,deployment.toString());
            clone.getPersistentDataContainer().set(NamespacedKey.fromString("decoswap:decoration_id"),PersistentDataType.STRING,decoration.toString());
            clone.getPersistentDataContainer().set(NamespacedKey.fromString("decoswap:local_entity_id"),PersistentDataType.INTEGER,local);
            plugin.decoSwap().adopt(clone,item.data,target,rotation,decoration,deployment,player.getUniqueId(),local++);
        }
        plugin.decoSwap().finishDeployment(deployment);
        return plugin.manager().getStrings().stream().filter(s->deployment.equals(s.decoDeployment)).findFirst().orElseThrow();
    }
    @ParameterizedTest @ValueSource(strings={"wall-warm","wall-christmas","wall-garland-rgb","soul-lanterns"})
    void rotatedCopiesKeepTheirBehaviourAndRestoreWithoutOrphans(String type) throws Exception {
        var original=create(type); original.mode=AnimationMode.CHASE; original.group="Winter"; var saved=capture(original);
        int before=world.getEntitiesByClass(Display.class).size(); UUID deployment=UUID.randomUUID(); var copy=paste(saved,deployment,90);
        assertNotEquals(original.id,copy.id); assertNotEquals(original.chain,copy.chain); assertFalse(copy.refundable);
        assertEquals(original.type,copy.type); assertEquals(original.mode,copy.mode); assertEquals("Winter",copy.group); assertEquals(original.sag,copy.sag);
        assertEquals(new Vector(31.5,82,33),copy.a); assertEquals(plugin.manager().getType(type).wall()?new Vector(-1,0,0):new Vector(),copy.normal);
        assertEquals(new org.bukkit.util.BlockVector(30,82,33),copy.anchorA); assertEquals(original.count,copy.count);
        assertEquals(before*2,world.getEntitiesByClass(Display.class).size());
        server.getScheduler().performTicks(4); assertEquals(before*2,world.getEntitiesByClass(Display.class).size());
        assertNull(plugin.manager().regenerate(copy)); assertEquals(before*2,world.getEntitiesByClass(Display.class).size());
        for(Entity part:parts(copy))assertEquals(deployment.toString(),part.getPersistentDataContainer().get(NamespacedKey.fromString("decoswap:deployment_id"),PersistentDataType.STRING));
        plugin.decoSwap().releaseDeployment(deployment); assertEquals(before,world.getEntitiesByClass(Display.class).size());
        assertEquals(List.of(original),List.copyOf(plugin.manager().getStrings()));
    }
    @Test void selectingABulbExpandsToItsWholeSegmentAndChecksOwnership() {
        var string=create("garland-rgb"); Entity part=string.bulbs.values().iterator().next();
        var expanded=plugin.decoSwap().expandSelection(Set.of(part.getUniqueId()),player); assertEquals(parts(string).size(),expanded.size());
        var stranger=server.addPlayer();stranger.setOp(false);assertThrows(IllegalStateException.class,()->plugin.decoSwap().expandSelection(Set.of(part.getUniqueId()),stranger));
    }
    @Test void incompleteAndDuplicatePayloadsAreRejectedBeforeAnythingIsPlaced() {
        var saved=capture(create("wall-warm")); var encoded=payloads(saved); int count=world.getEntities().size();
        assertThrows(IllegalStateException.class,()->plugin.decoSwap().validateDeployment(encoded.subList(1,encoded.size()),target,0,player.getUniqueId()));
        var duplicate=new ArrayList<>(encoded);duplicate.add(encoded.getFirst());
        assertThrows(IllegalStateException.class,()->plugin.decoSwap().validateDeployment(duplicate,target,0,player.getUniqueId()));
        assertEquals(count,world.getEntities().size());assertEquals(1,plugin.manager().getStrings().size());
    }
    @Test void separateCopiesShareNoChainOrSegmentIds() throws Exception {
        var original=create("wall-garland-rgb");var saved=capture(original);
        var a=paste(saved,UUID.randomUUID(),0);var b=paste(saved,UUID.randomUUID(),180);
        assertNotEquals(a.chain,b.chain);assertNotEquals(a.id,b.id);
        plugin.decoSwap().releaseDeployment(a.decoDeployment);assertTrue(parts(b).stream().allMatch(Entity::isValid));assertTrue(parts(original).stream().allMatch(Entity::isValid));
    }
    @Test void packAndHideRemoveTheLogicalSegmentAndItsOwnedNativeLights() {
        var original=create("warm");server.getScheduler().performTicks(4); assertFalse(original.lightBlocks.isEmpty());
        var lights=List.copyOf(original.lightBlocks);plugin.decoSwap().removeSelected(Set.of(original.bulbs.values().iterator().next().getUniqueId()),player);
        assertTrue(plugin.manager().getStrings().isEmpty());assertTrue(world.getEntitiesByClass(Display.class).isEmpty());
        for(var position:lights)assertTrue(world.getBlockAt(position.getBlockX(),position.getBlockY(),position.getBlockZ()).getType().isAir());
    }
    @Test void reloadRetainsExternalOwnershipAndLegacyStraightState() throws Exception {
        var original=create("warm");plugin.manager().setStraight(original,true);original.on=false;var copy=paste(capture(original),UUID.randomUUID(),0);
        var id=copy.id;var deployment=copy.decoDeployment;plugin.manager().saveData();plugin.manager().loadData();plugin.manager().scanLoadedEntities();
        var loaded=plugin.manager().byId(id);assertEquals(deployment,loaded.decoDeployment);assertTrue(loaded.straight);assertFalse(loaded.on);
        assertFalse(loaded.decoParts.isEmpty());assertNull(plugin.manager().regenerate(loaded));assertEquals(0,loaded.sag);
        plugin.decoSwap().releaseDeployment(deployment);assertNull(plugin.manager().byId(id));
    }
    @Test void switchingTheGlobalRendererRebuildsAnImportedGarlandWithoutDuplicatingParts() throws Exception {
        var original=create("wall-garland-rgb");var saved=capture(original);
        plugin.getConfig().set("model-pack.enabled",false);
        var copy=paste(saved,UUID.randomUUID(),90);
        assertTrue(copy.bulbs.values().stream().allMatch(e->e instanceof BlockDisplay));assertEquals(copy.count*2,parts(copy).size());
        assertTrue(parts(copy).stream().allMatch(Entity::isValid));
    }
    @Test void lightBlockNormalizationLeavesForeignLightUntouched() {
        var original=create("warm");server.getScheduler().performTicks(4);
        var own=original.lightBlocks.getFirst();assertEquals("minecraft:air",plugin.decoSwap().originalBlock(world.getBlockAt(own.getBlockX(),own.getBlockY(),own.getBlockZ())));
        var foreign=world.getBlockAt(10,74,2);foreign.setType(Material.LIGHT);assertNull(plugin.decoSwap().originalBlock(foreign));
    }
    @Test void wallKitsReplaceTheRemovedStraightCommand() {
        player.setOp(true);assertTrue(server.dispatchCommand(player,"xmas give wall-warm"));
        assertEquals("wall-warm",plugin.manager().getKitType(player.getInventory().getItemInMainHand()));
        assertFalse(plugin.getCommand("xmaslights").tabComplete(player,"xl",new String[]{""}).contains("straight"));
    }
    @SuppressWarnings("deprecation") void host(String disposition) {
        DecoHostFixture.disposition=disposition;
        MockBukkit.loadWith(DecoHostFixture.class,new org.bukkit.plugin.PluginDescriptionFile("DecoSwap","0.1.18",DecoHostFixture.class.getName()));
    }
    @Test void aRestoreWhileXmasWasDisabledDoesNotRespawnItsLogicalLights() throws Exception {
        var original=create("wall-garland-rgb");var copy=paste(capture(original),UUID.randomUUID(),0);host("REMOVED");
        plugin.decoSwap().reconcileOnStartup();assertNull(plugin.manager().byId(copy.id));assertNotNull(plugin.manager().byId(original.id));
        assertTrue(parts(original).stream().allMatch(Entity::isValid));
    }
    @Test void acceptingWhileXmasWasDisabledRetainsTheLightsAndClearsOldDecoTags() throws Exception {
        var copy=paste(capture(create("wall-warm")),UUID.randomUUID(),0);var all=parts(copy);host("RETAINED");
        plugin.decoSwap().reconcileOnStartup();assertNull(copy.decoDeployment);assertNotNull(plugin.manager().byId(copy.id));
        for(var entity:all){assertTrue(entity.isValid());assertFalse(entity.getPersistentDataContainer().has(NamespacedKey.fromString("decoswap:deployment_id")));}
        assertNull(plugin.manager().regenerate(copy));assertTrue(parts(copy).stream().allMatch(Entity::isValid));
    }
    @Test void pre27GeometryGetsStableRolesBeforeItsCaptureSelectionIsBuilt() {
        var string=create("wall-warm");var old=parts(string);for(var entity:old)entity.getPersistentDataContainer().remove(plugin.manager().keyPart);
        var expanded=plugin.decoSwap().expandSelection(Set.of(old.getFirst().getUniqueId()),player);
        assertEquals(parts(string).size(),expanded.size());assertTrue(old.stream().noneMatch(Entity::isValid));
        for(var entity:parts(string)) {
            assertTrue(expanded.contains(entity.getUniqueId()));
            assertFalse(plugin.decoSwap().capture(entity,home).get("role").startsWith("legacy:"));
        }
    }
}
