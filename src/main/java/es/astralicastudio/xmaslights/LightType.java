package es.astralicastudio.xmaslights;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

public record LightType(String id, Component name, String nameKey, Material item, float size, List<BlockData> bulbs, List<ItemStack> heads, boolean wall, boolean wire, double spacing, double chain, int light, String signature, GarlandModel model, List<Integer> colors, double sag) {
    public boolean isHead() {
        return !this.heads.isEmpty();
    }
    public boolean garland() { return model != null && !model.lamp(); }
    public boolean modelLamp() { return model != null && model.lamp(); }

    public int variants() {
        if (model != null) return colors.size();
        int geometry = this.isHead() ? this.heads.size() : this.bulbs.size();
        int a = colors.size(), b = geometry;
        while (b != 0) { int next = a % b; a = b; b = next; }
        // Complete both cycles when a custom RGB palette and model list have different lengths.
        return Math.multiplyExact(colors.size() / a, geometry);
    }
    public int color(int variant) { return colors.get(Math.floorMod(variant, colors.size())); }
}

