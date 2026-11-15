package es.astralicastudio.xmaslights;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.Locale;

/** Two batched item models per module: naturally shaded foliage and animated LEDs. */
public enum GarlandModel {
    CLASSIC(812300), SNOWY(812302), RGB(812304), BULB(812320), CUBE(812322);
    private final int bodyData;
    GarlandModel(int bodyData) { this.bodyData = bodyData; }
    public static GarlandModel parse(String value) {
        return value == null || value.isBlank() ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
    public ItemStack body() { return body(0); }
    public ItemStack body(int hiddenSide) { return item(Material.PAPER, bodyData, null, "garland/" + name().toLowerCase(Locale.ROOT) + "/body" + suffix(hiddenSide)); }
    public ItemStack lights(int color) { return lights(color, 0); }
    public ItemStack lights(int color, int hiddenSide) { return lights(java.util.List.of(color), 0, hiddenSide); }
    public ItemStack lights(java.util.List<Integer> palette, int phase, int hiddenSide) {
        ItemStack stack = item(Material.GLOWSTONE, 812310, null, "garland/lights" + suffix(hiddenSide));
        stack.editMeta(meta -> {
            var data = meta.getCustomModelDataComponent();
            var colors = new java.util.ArrayList<org.bukkit.Color>(8);
            for (int led = 0; led < 8; led++) colors.add(org.bukkit.Color.fromRGB(palette.get(Math.floorMod(phase + led, palette.size()))));
            data.setColors(colors); meta.setCustomModelDataComponent(data);
        });
        return stack;
    }
    private static String suffix(int hiddenSide) { return hiddenSide < 0 ? "_no_back" : hiddenSide > 0 ? "_no_front" : ""; }
    public boolean lamp() { return this == BULB || this == CUBE; }
    public ItemStack bulb(int color) { return bulb(color, false); }
    public ItemStack bulb(int color, boolean inline) {
        return item(Material.GLOWSTONE, bodyData, color, (this == CUBE ? "cube" : "bulb") + (inline ? "_inline" : ""));
    }
    private static ItemStack item(Material material, int modelData, Integer color, String model) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            var data = meta.getCustomModelDataComponent();
            data.setFloats(java.util.List.of((float)modelData));
            if (color != null) data.setColors(java.util.List.of(org.bukkit.Color.fromRGB(color)));
            meta.setCustomModelDataComponent(data);
            meta.setItemModel(java.util.Objects.requireNonNull(org.bukkit.NamespacedKey.fromString("xmaslights:" + model)));
        });
        // Displays are only shown to viewers who have loaded the model pack.
        return stack;
    }
}
