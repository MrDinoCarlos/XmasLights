package es.astralicastudio.xmaslights;

import java.util.Properties;
import java.util.Arrays;

/** The distribution target is embedded by tools/verify_matrix.py, never inferred from a filename. */
final class Compatibility {
    private Compatibility() { }
    static String minecraft() {
        try (var stream = Compatibility.class.getResourceAsStream("/compatibility.properties")) {
            if (stream == null) return "1.21.4";
            var properties = new Properties(); properties.load(stream);
            if (properties.containsKey("minecraft-versions"))
                return select(properties, org.bukkit.Bukkit.getMinecraftVersion());
            return properties.getProperty("minecraft", "1.21.4");
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    static String select(Properties properties, String running) {
        if (Arrays.asList(properties.getProperty("minecraft-versions", "").split(",")).contains(running)) return running;
        throw new IllegalStateException("This XmasLights JAR targets " + properties.getProperty("branch")
                + "; unsupported Minecraft version: " + running);
    }
}
