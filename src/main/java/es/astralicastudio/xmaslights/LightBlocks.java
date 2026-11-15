package es.astralicastudio.xmaslights;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BlockVector;
import java.util.*;

/** Owns only air replaced by this plugin. Overlaps use the brightest bulb, once per tick. */
final class LightBlocks {
    record Position(String world, int x, int y, int z) {
        static Position at(String world, BlockVector vector) { return new Position(world, vector.getBlockX(), vector.getBlockY(), vector.getBlockZ()); }
        String encode() { return world + ";" + x + ";" + y + ";" + z; }
        static Position decode(String input) {
            String[] parts = input.split(";", -1);
            if (parts.length != 4 || parts[0].isBlank()) throw new IllegalArgumentException("light position: " + input);
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            if (Math.abs((long)x) > 30_000_000 || Math.abs((long)z) > 30_000_000 || y < -2048 || y > 2048) throw new IllegalArgumentException(input);
            return new Position(parts[0], x, y, z);
        }
        boolean loaded(World world) { return y >= world.getMinHeight() && y < world.getMaxHeight() && world.isChunkLoaded(x >> 4, z >> 4); }
    }
    private final Map<Position, Material> owned = new LinkedHashMap<>();

    Material original(org.bukkit.block.Block block) {
        return block.getType() == Material.LIGHT ? owned.get(new Position(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) : null;
    }

    boolean claim(World world, BlockVector vector) {
        Position pos = Position.at(world.getName(), vector);
        if (!pos.loaded(world)) return false;
        var block = world.getBlockAt(pos.x, pos.y, pos.z);
        if (owned.containsKey(pos)) return block.getType() == Material.LIGHT;
        if (!block.getType().isAir()) return false; // An existing LIGHT block belongs to the world/admin.
        owned.put(pos, block.getType());
        Levelled data = (Levelled)Material.LIGHT.createBlockData();
        data.setLevel(0);
        block.setBlockData(data, false);
        return true;
    }

    void apply(Map<Position, Integer> desired) {
        Map<String, World> worlds = new HashMap<>();
        var iterator = owned.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Position pos = entry.getKey();
            World world = worlds.computeIfAbsent(pos.world, Bukkit::getWorld);
            if (world == null || !pos.loaded(world)) continue; // Retain cleanup across chunk/world unloads and restarts.
            var block = world.getBlockAt(pos.x, pos.y, pos.z);
            Integer level = desired.get(pos);
            if (block.getType() != Material.LIGHT) {
                iterator.remove(); // A player/plugin changed the block; never overwrite it.
            } else if (level == null) {
                block.setType(entry.getValue(), false);
                iterator.remove();
            } else {
                Levelled data = (Levelled)block.getBlockData();
                if (data.getLevel() != level) { data.setLevel(level); block.setBlockData(data, false); }
            }
        }
    }

    /** Restore only our blocks, while the world is still active (shutdown/world-unload). */
    void releaseChunk(World world, int x, int z) {
        var iterator = owned.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Position pos = entry.getKey();
            if (!pos.world.equals(world.getName()) || pos.x >> 4 != x || pos.z >> 4 != z) continue;
            var block = world.getBlockAt(pos.x, pos.y, pos.z);
            if (block.getType() == Material.LIGHT) block.setType(entry.getValue(), false);
            iterator.remove();
        }
    }

    /** Ownership survives chunk unloads. Visit exactly those chunks when the plugin stops. */
    void releaseAll() { for (World world : Bukkit.getWorlds()) releaseWorld(world); }

    void releaseWorld(World world) {
        for (Position pos : List.copyOf(owned.keySet())) {
            if (!pos.world.equals(world.getName()) || pos.y < world.getMinHeight() || pos.y >= world.getMaxHeight()) continue;
            world.getChunkAt(pos.x >> 4, pos.z >> 4);
            releaseChunk(world, pos.x >> 4, pos.z >> 4);
        }
    }

    void load(YamlConfiguration yaml, Collection<LightString> strings) {
        owned.clear();
        for (String encoded : yaml.getStringList("owned-light-blocks")) {
            int split = encoded.lastIndexOf('=');
            Position pos = Position.decode(encoded.substring(0, split));
            Material original = Material.valueOf(encoded.substring(split + 1));
            if (!original.isAir()) throw new IllegalArgumentException(encoded);
            owned.put(pos, original);
        }
        // Version 2.1 stored block ownership per string. Preserve it during migration.
        if (!yaml.contains("owned-light-blocks")) for (LightString string : strings) {
            for (BlockVector vector : string.lightBlocks) owned.putIfAbsent(Position.at(string.world, vector), Material.AIR);
        }
    }

    void save(YamlConfiguration yaml) {
        yaml.set("owned-light-blocks", owned.entrySet().stream().map(entry -> entry.getKey().encode() + "=" + entry.getValue().name()).toList());
    }
}
