package es.astralicastudio.xmaslights;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.junit.jupiter.api.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    static XmasLightsPlugin loadPlugin() {
        return MockBukkit.loadWith(XmasLightsTestPlugin.class, Objects.requireNonNull(PluginTest.class.getResourceAsStream("/plugin.yml")));
    }
    private ServerMock server;
    private XmasLightsPlugin plugin;
    @BeforeEach void start() {
        server = MockBukkit.mock(new TestServer()); server.addSimpleWorld("world");
        plugin = PluginTest.loadPlugin();
        assertTrue(plugin.isEnabled(), "Plugin must start with all default types and languages");
    }
    @AfterEach void stop() { stopServer(); }
    static void stopServer() {
        var server=MockBukkit.getMock();
        try { MockBukkit.unmock(); }
        finally { if(server instanceof TestServer test) test.modelItemFactory.close(); }
    }
    /** MockBukkit 4.35 lacks lantern states and Levelled LIGHT data; production Paper implements both. */
    @SuppressWarnings("unchecked")
    static class TestServer extends ServerMock {
        private final ModelItemFactory modelItemFactory = new ModelItemFactory();
        private int nextPlayer;
        java.util.function.Consumer<org.bukkit.entity.Entity> afterSpawn = ignored -> { };
        @Override public org.mockbukkit.mockbukkit.entity.PlayerMock addPlayer() { return addPlayer("Player" + ++nextPlayer); }
        @Override public org.mockbukkit.mockbukkit.entity.PlayerMock addPlayer(String name) {
            var player = new PackPlayer(this, name); addPlayer(player); return player;
        }
        @Override public org.mockbukkit.mockbukkit.inventory.ItemFactoryMock getItemFactory() { return modelItemFactory; }
        @Override public org.mockbukkit.mockbukkit.world.WorldMock addSimpleWorld(String name) {
            modelItemFactory.installItemTypes();
            var world = new DisplayWorld(new org.bukkit.WorldCreator(name), this); addWorld(world); return world;
        }
        @Override public org.bukkit.block.data.BlockData createBlockData(String data) {
            if (data.startsWith("minecraft:lantern") || data.startsWith("minecraft:soul_lantern")) {
                return new LanternData(data.contains("soul_lantern") ? org.bukkit.Material.SOUL_LANTERN : org.bukkit.Material.LANTERN, data.contains("hanging=true"));
            }
            // MockBukkit 4.35 also lacks persistent leaf states; production Paper supports them.
            return super.createBlockData(data.replace("lantern[hanging=true]", "lantern").replace("_leaves[persistent=true]", "_leaves"));
        }
        @Override public org.bukkit.block.data.BlockData createBlockData(org.bukkit.Material material) {
            if (material == org.bukkit.Material.LANTERN || material == org.bukkit.Material.SOUL_LANTERN) return new LanternData(material,false);
            return material == org.bukkit.Material.LIGHT ? new LightData() : super.createBlockData(material);
        }
        private static class LanternData extends org.mockbukkit.mockbukkit.block.data.BlockDataMock implements org.bukkit.block.data.type.Lantern {
            private boolean hanging, waterlogged;
            LanternData(org.bukkit.Material material, boolean hanging) { super(material); this.hanging=hanging; }
            @Override public boolean isHanging() { return hanging; }
            @Override public void setHanging(boolean value) { hanging=value; }
            @Override public boolean isWaterlogged() { return waterlogged; }
            @Override public void setWaterlogged(boolean value) { waterlogged=value; }
            @Override public String getAsString() { return getMaterial().getKey()+"[hanging="+hanging+",waterlogged="+waterlogged+"]"; }
        }
        private static class LightData extends org.mockbukkit.mockbukkit.block.data.BlockDataMock implements org.bukkit.block.data.Levelled {
            private int level;
            LightData() { super(org.bukkit.Material.LIGHT); }
            @Override public int getLevel() { return level; }
            @Override public void setLevel(int level) { this.level = level; }
            @Override public int getMaximumLevel() { return 15; }
            @Override public int getMinimumLevel() { return 0; }
        }
        private static class DisplayWorld extends org.mockbukkit.mockbukkit.world.WorldMock {
            @Override public org.mockbukkit.mockbukkit.block.BlockMock createBlock(org.mockbukkit.mockbukkit.world.Coordinate coordinate) {
                var block = super.createBlock(coordinate);
                var collision = new CollisionBlock(block.getType(), block.getLocation());
                // createBlock itself owns the cache in MockBukkit, not getBlockAt.
                try {
                    var field = org.mockbukkit.mockbukkit.world.WorldMock.class.getDeclaredField("blocks"); field.setAccessible(true);
                    ((Map<org.mockbukkit.mockbukkit.world.Coordinate, org.mockbukkit.mockbukkit.block.BlockMock>)field.get(this)).put(coordinate,collision);
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                return collision;
            }
            private final ServerMock server;
            DisplayWorld(org.bukkit.WorldCreator creator, ServerMock server) { super(creator); this.server = server; }
            @Override public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location location, Class<T> type, java.util.function.Consumer<? super T> init,
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason, boolean randomize, boolean callEvent) {
                if (type == org.bukkit.entity.TextDisplay.class) {
                    var display = new TextDisplayFixture(server); display.setLocation(location); display.setSpawnReason(reason);
                    if (init != null) init.accept(type.cast(display)); server.registerEntity(display);
                    return type.cast(display);
                }
                if (type != org.bukkit.entity.ItemDisplay.class) return super.spawn(location,type,init,reason,randomize,callEvent);
                var display = new VisibleDisplay(server, UUID.randomUUID()); display.setLocation(location); display.setSpawnReason(reason);
                if (init != null) init.accept(type.cast(display)); server.registerEntity(display);
                if(server instanceof TestServer test) test.afterSpawn.accept(display);
                return type.cast(display);
            }
        }
        /** Test-only collision support missing in MockBukkit 4.35; real Paper supplies voxel shapes. */
        private static class CollisionBlock extends org.mockbukkit.mockbukkit.block.BlockMock {
            CollisionBlock(org.bukkit.Material material, org.bukkit.Location location) { super(material, location); }
            @Override public byte getLightFromBlocks() { return getType()==org.bukkit.Material.LIGHT ? (byte)((org.bukkit.block.data.Levelled)getBlockData()).getLevel() : 0; }
            @Override public byte getLightFromSky() { return 15; }
            @Override public boolean isPassable() { return !getType().isSolid() || getType() == org.bukkit.Material.LIGHT; }
            @Override public org.bukkit.util.RayTraceResult rayTrace(org.bukkit.Location start, Vector direction, double distance, org.bukkit.FluidCollisionMode fluids) {
                if (isPassable()) return null;
                double low = getY(), high = getY() + 1;
                if (getBlockData() instanceof org.bukkit.block.data.type.Slab slab) {
                    if (slab.getType() == org.bukkit.block.data.type.Slab.Type.BOTTOM) high -= .5;
                    if (slab.getType() == org.bukkit.block.data.type.Slab.Type.TOP) low += .5;
                }
                var bounds = new org.bukkit.util.BoundingBox(getX(),low,getZ(),getX()+1,high,getZ()+1);
                if (bounds.contains(start.toVector())) return new org.bukkit.util.RayTraceResult(start.toVector(), this, org.bukkit.block.BlockFace.UP);
                // BoundingBox.rayTrace in this API build produces NaN for a parallel
                // ray on an integer edge (0 * infinity). Use a slab intersection here.
                double[] from={start.getX(),start.getY(),start.getZ()}, delta={direction.getX(),direction.getY(),direction.getZ()};
                double[] min={getX(),low,getZ()}, max={getX()+1,high,getZ()+1};
                var nearFaces=new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.WEST,org.bukkit.block.BlockFace.DOWN,org.bukkit.block.BlockFace.NORTH};
                var farFaces=new org.bukkit.block.BlockFace[]{org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.UP,org.bukkit.block.BlockFace.SOUTH};
                double near=0, far=distance; org.bukkit.block.BlockFace face=null;
                for(int axis=0;axis<3;axis++) {
                    if(Math.abs(delta[axis])<1e-12) { if(from[axis]<min[axis]||from[axis]>max[axis]) return null; continue; }
                    double a=(min[axis]-from[axis])/delta[axis], b=(max[axis]-from[axis])/delta[axis];
                    if(Math.min(a,b)>=near) { near=Math.min(a,b); face=a<b?nearFaces[axis]:farFaces[axis]; }
                    far=Math.min(far,Math.max(a,b)); if(near>far) return null;
                }
                return face==null?null:new org.bukkit.util.RayTraceResult(start.toVector().add(direction.clone().multiply(near)),this,face);
            }
        }
        /** MockBukkit's EntityMock does not implement visible-by-default. Hide existing test viewers explicitly. */
        private static class VisibleDisplay extends org.mockbukkit.mockbukkit.entity.ItemDisplayMock {
            private boolean defaultVisibility = true;
            private org.bukkit.entity.Display.Billboard billboard = org.bukkit.entity.Display.Billboard.FIXED;
            private final ServerMock server;
            VisibleDisplay(ServerMock server, UUID id) { super(server,id); this.server = server; }
            @Override public void setVisibleByDefault(boolean value) {
                defaultVisibility = value;
                var owner = server.getPluginManager().getPlugin("XmasLights");
                if (!value && owner != null) server.getOnlinePlayers().forEach(player -> player.hideEntity(owner,this));
            }
            @Override public boolean isVisibleByDefault() { return defaultVisibility; }
            @Override public void setBillboard(org.bukkit.entity.Display.Billboard value) { billboard = value; }
            @Override public org.bukkit.entity.Display.Billboard getBillboard() { return billboard; }
        }
    }
    /** MockBukkit 4.35 has no virtual host or resource-pack request implementation. */
    static class PackPlayer extends org.mockbukkit.mockbukkit.entity.PlayerMock {
        record PackRequest(UUID id, String url, byte[] hash, String prompt, boolean required) { }
        final List<PackRequest> packs = new ArrayList<>();
        Set<Long> sentChunks;
        boolean staleChunkSentProbe;
        java.net.InetSocketAddress virtualHost = java.net.InetSocketAddress.createUnresolved("127.0.0.1", 25565);
        PackPlayer(ServerMock server, String name) { super(server, name); }
        @Override public java.net.InetSocketAddress getVirtualHost() { return virtualHost; }
        @Override public boolean isChunkSent(long key) {
            if (staleChunkSentProbe) return false;
            // A loaded chunk stands in for normal delivery unless a test supplies
            // an explicit client chunk set. No chunk is loaded by this query.
            return sentChunks == null ? getWorld().isChunkLoaded((int)key, (int)(key >> 32)) : sentChunks.contains(key);
        }
        @Override public Set<Long> getSentChunkKeys() {
            if (sentChunks != null) return Set.copyOf(sentChunks);
            var keys = new HashSet<Long>();
            for (var chunk : getWorld().getLoadedChunks()) keys.add(org.bukkit.Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
            return keys;
        }
        @Override public void addResourcePack(UUID id, String url, byte[] hash, String prompt, boolean required) {
            packs.add(new PackRequest(id, url, hash == null ? null : hash.clone(), prompt, required));
        }
    }
    @Test void startupAndReloadKeepAllBuiltInTypes() throws Exception {
        assertEquals(51, plugin.manager().getTypes().size());
        plugin.reload();
        assertEquals(51, plugin.manager().getTypes().size());
        assertTrue(plugin.manager().saveData());
    }
    @Test void regularPlayersCannotUseAdministrativeCommandsOrSeeTheirCompletions() {
        var player = server.addPlayer(); player.setOp(false);
        assertTrue(server.dispatchCommand(player, "xl give warm"));
        assertTrue(player.getInventory().isEmpty());
        assertTrue(player.nextMessage().contains("permission"));
        var command = plugin.getCommand("xmaslights"); assertNotNull(command);
        assertTrue(command.tabComplete(player, "xl", new String[]{""}).isEmpty());
    }
    @Test void failedReloadKeepsActiveSettingsTypesAndTranslations() throws Exception {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        var config = SafeYaml.load(file); config.set("animation-ticks", -1); SafeYaml.save(config, file, false);
        assertThrows(ConfigProblem.class, plugin::reload);
        assertEquals(2, plugin.getConfig().getInt("animation-ticks"));
        assertEquals(51, plugin.manager().getTypes().size());
        assertTrue(plugin.manager().saveData());
    }
    @Test void playerLocaleIsOptionalAndUntrustedValuesCannotInjectFormatting() throws Exception {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        var config = SafeYaml.load(file); config.set("language.use-player-locale", true); SafeYaml.save(config, file, false); plugin.reload();
        var player = server.addPlayer(); player.setLocale(Locale.forLanguageTag("es-ES"));
        plugin.messages().send(player, "commands.owner", "count", 2, "player", "Name&c{count}");
        String text = player.nextMessage();
        assertTrue(text.contains("asignado"), text);
        assertTrue(text.contains("Name&c{count}"), text);
        player.setLocale(Locale.JAPANESE);
        assertEquals("en", plugin.messages().language(player));
    }
    @Test void ownershipBlocksOtherPlayersAndAllowsExplicitAdministrators() {
        var owner = server.addPlayer(); var other = server.addPlayer();
        owner.addAttachment(plugin, "xmaslights.use", true); other.addAttachment(plugin, "xmaslights.use", true);
        LightString string = new LightString(UUID.randomUUID(), "world", new Vector(0,70,0), new Vector(3,70,0), new Vector(), new Vector(0,70,0).toBlockVector(), new Vector(3,70,0).toBlockVector(), .2, "warm", AnimationMode.STEADY);
        string.owner = owner.getUniqueId();
        assertTrue(plugin.manager().canManage(owner, string));
        assertFalse(plugin.manager().canManage(other, string));
        string.owner = null;
        assertFalse(plugin.manager().canManage(owner, string));
        other.addAttachment(plugin, "xmaslights.manage-all", true);
        assertTrue(plugin.manager().canManage(other, string));
    }
    @Test void invalidSavedDataCannotBeOverwrittenByDisableOrSave() throws Exception {
        Path file = plugin.getDataFolder().toPath().resolve("lights.yml");
        Files.writeString(file, "lights: [not-a-section]\n");
        assertThrows(ConfigProblem.class, plugin.manager()::loadData);
        assertFalse(plugin.manager().saveData());
        assertEquals("lights: [not-a-section]\n", Files.readString(file));
    }
    @Test void legacy21DataRoundTripsWithOwnershipAndNoFreeRefunds() throws Exception {
        UUID id = UUID.randomUUID();
        var yaml = new YamlConfiguration(); String path = "lights." + id + ".";
        yaml.set("groups", List.of("Garden")); yaml.set(path + "world", "world"); yaml.set(path + "a", "1,70,1"); yaml.set(path + "b", "5,70,1");
        yaml.set(path + "anchor-a", "1,70,1"); yaml.set(path + "anchor-b", "5,70,1"); yaml.set(path + "type", "warm"); yaml.set(path + "group", "Garden");
        yaml.set(path + "mode", "blink"); yaml.set(path + "spawned", false); yaml.set(path + "on", false);
        Path file = plugin.getDataFolder().toPath().resolve("lights.yml"); SafeYaml.save(yaml, file, false);
        plugin.manager().loadData();
        LightString string = plugin.manager().getStrings().iterator().next();
        assertNull(string.owner); assertFalse(string.refundable); assertFalse(string.spawned); assertFalse(string.on);
        string.owner = UUID.randomUUID(); assertTrue(plugin.manager().saveData()); plugin.manager().loadData();
        assertEquals(string.owner, plugin.manager().getStrings().iterator().next().owner);
        assertEquals(4, SafeYaml.load(file).getInt("data-version"));
        try (var files = Files.list(file.getParent())) { assertTrue(files.anyMatch(p -> p.getFileName().toString().matches("lights.yml\\..+\\.bak"))); }
    }
    @Test void creatingLightsBuildsBoundedGeometryAndPersistsTheOwner() throws Exception {
        var world = server.getWorld("world"); assertNotNull(world);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x,z);
        var player = server.addPlayer(); player.teleport(new org.bukkit.Location(world, 1,70,1));
        LightString string = plugin.manager().create(player, new Vector(1,70,1), new Vector(5,70,1), new Vector(1,70,1).toBlockVector(), new Vector(5,70,1).toBlockVector(), plugin.manager().getType("warm"), new Vector(), null, false, null);
        assertEquals(player.getUniqueId(), string.owner); assertFalse(string.refundable);
        assertTrue(string.count <= 128); assertFalse(string.bulbs.isEmpty()); assertFalse(string.wires.isEmpty());
        assertTrue(plugin.manager().despawn(string)); assertTrue(string.bulbs.isEmpty());
        assertNull(plugin.manager().spawn(string)); assertTrue(plugin.manager().remove(string)); assertTrue(plugin.manager().getStrings().isEmpty());
    }
    @Test void overlappingLightsUseMaximumBrightnessAndDoNotDeleteEachOther() throws Exception {
        var world = server.getWorld("world"); assertNotNull(world);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x,z);
        var player = server.addPlayer(); player.teleport(new org.bukkit.Location(world, 1,70,1));
        var a = new Vector(1,70,1); var b = new Vector(5,70,1);
        LightString first = plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), plugin.manager().getType("warm"), new Vector(), null, false, null);
        LightString second = plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), plugin.manager().getType("warm"), new Vector(), null, false, null);
        var position = first.bulbLights.values().iterator().next();
        plugin.manager().setOn(first, false);
        server.getScheduler().performTicks(100);
        var block = world.getBlockAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        assertEquals(12, ((org.bukkit.block.data.Levelled)block.getBlockData()).getLevel());
        assertTrue(plugin.manager().remove(first));
        assertEquals(org.bukkit.Material.LIGHT, block.getType());
        assertTrue(plugin.manager().remove(second));
        assertEquals(org.bukkit.Material.AIR, block.getType());
    }
    @Test void preExistingWorldLightIsNeverClaimedOrDeleted() {
        var world = server.getWorld("world"); assertNotNull(world); world.loadChunk(0,0);
        var position = new Vector(1,70,1).toBlockVector();
        world.getBlockAt(1,70,1).setType(org.bukkit.Material.LIGHT);
        var lights = new LightBlocks();
        assertFalse(lights.claim(world, position)); lights.apply(Map.of());
        assertEquals(org.bukkit.Material.LIGHT, world.getBlockAt(1,70,1).getType());
    }
    @Test void orphanCleanupSurvivesUnloadedChunksAndSerialization() {
        var world = server.getWorld("world"); assertNotNull(world); world.loadChunk(0,0);
        var lights = new LightBlocks(); assertTrue(lights.claim(world, new Vector(1,70,1).toBlockVector()));
        world.unloadChunk(0,0);
        assertFalse(world.isChunkLoaded(0,0));
        lights.apply(Map.of());
        assertFalse(world.isChunkLoaded(0,0), "Cleanup must never force-load chunks");
        var yaml = new YamlConfiguration(); lights.save(yaml);
        assertEquals(1, yaml.getStringList("owned-light-blocks").size());
        var restored = new LightBlocks(); restored.load(yaml, List.of()); world.loadChunk(0,0); restored.apply(Map.of());
        assertEquals(org.bukkit.Material.AIR, world.getBlockAt(1,70,1).getType());
    }
    @Test void entityLimitRejectsNewSegmentsWithoutLosingExistingOnes() throws Exception {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        var config = SafeYaml.load(file); config.set("limits.max-entities", 64); SafeYaml.save(config, file, false); plugin.reload();
        Path typesFile = plugin.getDataFolder().toPath().resolve("types.yml");
        var types = SafeYaml.load(typesFile); types.set("types.warm.spacing", .2); SafeYaml.save(types, typesFile, false); plugin.reload();
        var world = server.getWorld("world"); assertNotNull(world);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x,z);
        var player = server.addPlayer(); player.teleport(new org.bukkit.Location(world, 1,70,1));
        var a = new Vector(1,70,1); var b = new Vector(5,70,1);
        plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), plugin.manager().getType("warm"), new Vector(), null, false, null);
        assertThrows(OperationFailure.class, () -> plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), plugin.manager().getType("warm"), new Vector(), null, false, null));
        assertEquals(1, plugin.manager().getStrings().size());
    }
    @Test void reloadRebuildsSagAndFallbackSpacingWithoutLosingIdentity() throws Exception {
        Path typesFile = plugin.getDataFolder().toPath().resolve("types.yml");
        var types = SafeYaml.load(typesFile); types.set("types.warm.spacing", null); SafeYaml.save(types, typesFile, false); plugin.reload();
        var world = server.getWorld("world"); assertNotNull(world);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.loadChunk(x,z);
        var player = server.addPlayer(); player.teleport(new org.bukkit.Location(world, 1,70,1));
        var a = new Vector(1,70,1); var b = new Vector(5,70,1);
        LightString string = plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), plugin.manager().getType("warm"), new Vector(), null, false, "Garden");
        assertEquals(4, string.count);
        Path configFile = plugin.getDataFolder().toPath().resolve("config.yml");
        var config = SafeYaml.load(configFile); config.set("sag", .2); config.set("bulb-spacing", 2); SafeYaml.save(config, configFile, false); plugin.reload();
        server.getScheduler().performTicks(5);
        assertEquals(.8, string.sag, .0001); assertEquals(2, string.count);
        assertEquals("Garden", string.group); assertEquals(player.getUniqueId(), string.owner); assertEquals(string.id, plugin.manager().getStrings().iterator().next().id);
    }
}
