package es.astralicastudio.xmaslights;

import org.bukkit.DyeColor;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

final class LightColors {
    private LightColors() { }
    static int parse(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("RGB: #RRGGBB");
        return Integer.parseInt(value.substring(1), 16);
    }
    static List<Integer> palette(ConfigurationSection config, List<BlockData> blocks, String id) {
        if (config.contains("rgb")) return config.getStringList("rgb").stream().map(LightColors::parse).toList();
        if (!blocks.isEmpty()) return blocks.stream().map(block -> fromMaterial(block.getMaterial().name())).toList();
        return switch (id) {
            case "red" -> List.of(0xFF3838); case "green" -> List.of(0x52DE68);
            case "blue" -> List.of(0x4E83FF); case "yellow", "wall-yellow" -> List.of(0xFFE462);
            case "orange" -> List.of(0xFF9538);
            case "christmas", "wall-christmas" -> List.of(0xFF3838, 0x52DE68, 0xFFE462);
            default -> List.of(0xFFD38A);
        };
    }
    static int fromMaterial(String name) {
        if (name.startsWith("SOUL_")) return 0x66DDFF;
        for (DyeColor color : DyeColor.values()) if (name.startsWith(color.name() + "_")) return color.getColor().asRGB();
        return 0xFFD38A;
    }
}
