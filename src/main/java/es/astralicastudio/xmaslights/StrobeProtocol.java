package es.astralicastudio.xmaslights;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/** Native wire codec and StrobeLights 0.10.38 stable slots. Shader assets stay in StrobeLights. */
final class StrobeProtocol {
    private static final int[] SLOT_TAGS = {8, 9, 11, 12, 14, 15, 6, 7};
    private StrobeProtocol() { }
    static int encode(int rgb, int level, double expansion) {
        int power = Math.clamp(level, 0, 15);
        // Keep the technical signature even at zero energy. Black tint 0 loses
        // the shader carrier and changes its render path during blink effects.
        int size = Math.clamp((int)Math.round(expansion / .25) - 1, 0, 15);
        int red = (int)Math.round((rgb >>> 16 & 255) * power / 255.0);
        int green = (int)Math.round((rgb >>> 8 & 255) * power / 255.0);
        int blue = (int)Math.round((rgb & 255) * power / 255.0);
        return 0xA00005 | size << 16 | red << 12 | green << 8 | blue << 4;
    }
    /** Native 0.10.37 shader radius in blocks, including its 4-bit quantisation. */
    static double radius(int payload) {
        if (stable(payload)) return .25 * Math.pow(32, ((payload >>> 16) & 15) / 15.0);
        int peak = Math.max((payload >>> 12) & 15, Math.max((payload >>> 8) & 15, (payload >>> 4) & 15));
        return (3 + 15 * Math.pow(peak / 15.0, .65)) * (((payload >>> 16) & 15) + 1) * .25;
    }
    static boolean stable(int payload) {
        int tag = (payload >>> 20) & 15;
        return tag == 6 || tag == 7 || tag == 8 || tag == 9 || tag == 11 || tag == 12 || tag == 14 || tag == 15;
    }
    static int peak(int payload) {
        int shift = stable(payload) ? 0 : 4;
        return Math.max((payload >>> (shift + 8)) & 15, Math.max((payload >>> (shift + 4)) & 15, (payload >>> shift) & 15));
    }
    /** Stable source slots decouple world position, radius and intensity from screen projection. */
    static int stableSource(int rgb, int level, int slot, double radiusLimit) {
        int power = Math.clamp(level, 0, 15);
        double radius = Math.clamp(radiusLimit, .25, 8);
        int band = Math.clamp((int)Math.floor(Math.log(radius / .25) / Math.log(32) * 15 + 1e-9), 0, 15);
        int red = (int)Math.round((rgb >>> 16 & 255) * power / 255.0);
        int green = (int)Math.round((rgb >>> 8 & 255) * power / 255.0);
        int blue = (int)Math.round((rgb & 255) * power / 255.0);
        return withSlot((8 << 20) | (band << 16) | (red << 8) | (green << 4) | blue, slot);
    }
    static int withSlot(int payload, int slot) {
        if (slot < 0 || slot >= 128) throw new IllegalArgumentException("source slot");
        return (SLOT_TAGS[slot / 16] << 20) | (slot & 15) << 12 | (payload & 0xF0FFF);
    }
    static int surface(int rgb, int level, double expansion, double radiusLimit) {
        for (int energy = Math.clamp(level, 0, 15); energy > 0; energy--) {
            int payload = encode(rgb, energy, expansion);
            if (radius(payload) <= radiusLimit) return payload;
        }
        return encode(rgb, 0, expansion);
    }
    static ItemStack marker(int encoded) {
        ItemStack stack = new ItemStack(Material.LIME_STAINED_GLASS);
        stack.editMeta(meta -> {
            var data = meta.getCustomModelDataComponent();
            data.setFloats(java.util.List.of(4_000_000f));
            data.setColors(java.util.List.of(Color.fromRGB(encoded)));
            meta.setCustomModelDataComponent(data);
            meta.setItemModel(java.util.Objects.requireNonNull(NamespacedKey.fromString("strobelights:carrier/rgb")));
        });
        return stack;
    }
}
