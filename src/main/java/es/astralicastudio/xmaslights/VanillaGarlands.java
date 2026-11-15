package es.astralicastudio.xmaslights;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.block.data.BlockData;
import java.util.HashMap;
import java.util.Map;

/** Two shared block displays per module; no second representation or per-player copies. */
final class VanillaGarlands {
    private static final Map<Integer, BlockData> COLORS = new HashMap<>();
    private VanillaGarlands() { }
    static BlockData foliage(GarlandModel model) {
        return Bukkit.createBlockData(model == GarlandModel.SNOWY ? "minecraft:pale_oak_leaves[persistent=true]" : "minecraft:spruce_leaves[persistent=true]");
    }
    static BlockData light(int rgb) {
        return COLORS.computeIfAbsent(rgb, color -> {
            DyeColor closest = DyeColor.WHITE; double distance = Double.POSITIVE_INFINITY;
            for (DyeColor candidate : DyeColor.values()) {
                int value = candidate.getColor().asRGB();
                double next = Math.pow((color >> 16 & 255) - (value >> 16 & 255), 2)
                        + Math.pow((color >> 8 & 255) - (value >> 8 & 255), 2) + Math.pow((color & 255) - (value & 255), 2);
                if (next < distance) { distance = next; closest = candidate; }
            }
            return Bukkit.createBlockData("minecraft:" + closest.name().toLowerCase(java.util.Locale.ROOT) + "_concrete");
        });
    }
    static void clear() { COLORS.clear(); }
}
