package lifecycle;

import es.astralicastudio.xmaslights.XmasLightsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.*;

/** Checks actual StrobeLights item metadata and XYZ transforms; no replacement surface geometry. */
final class SurfaceSmoke {
    static void verify(XmasLightsPlugin plugin, World world) throws Exception {
        Class<?> bridgeType = Class.forName("es.astralicastudio.xmaslights.StrobeLightsBridge");
        var constructor = bridgeType.getDeclaredConstructor(XmasLightsPlugin.class); constructor.setAccessible(true);
        Object bridge = constructor.newInstance(plugin);
        var configure = bridgeType.getDeclaredMethod("configure", org.bukkit.configuration.file.YamlConfiguration.class);
        configure.setAccessible(true); configure.invoke(bridge, plugin.getConfig());
        Class<?> hitType = Class.forName("es.astralicastudio.xmaslights.SurfaceLighting$Hit");
        var hitConstructor = hitType.getDeclaredConstructor(Location.class, double.class, Vector.class); hitConstructor.setAccessible(true);
        Class<?> carrierType = Class.forName("es.astralicastudio.xmaslights.StrobeLightsBridge$Carrier");
        var carrierConstructor = carrierType.getDeclaredConstructor(); carrierConstructor.setAccessible(true);
        var update = bridgeType.getDeclaredMethod("update", Player.class, hitType, int.class, carrierType); update.setAccessible(true);
        var displayField = carrierType.getDeclaredField("display"); displayField.setAccessible(true);
        UUID owner = UUID.randomUUID();
        Player player = (Player)Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, method, args) -> switch(method.getName()) {
            case "getWorld" -> world; case "getUniqueId" -> owner; case "isChunkSent" -> false;
            case "getEyeLocation" -> new Location(world,3205,85,3205);
            case "getSentChunkKeys" -> Set.of(Chunk.getChunkKey(200,200));
            case "showEntity" -> null; default -> throw new UnsupportedOperationException(method.getName());
        });
        for (Vector normal : List.of(new Vector(0,1,0), new Vector(1,0,0), new Vector(-1,0,0), new Vector(0,0,1), new Vector(0,0,-1))) {
            Object carrier = carrierConstructor.newInstance();
            Object hit = hitConstructor.newInstance(new Location(world,3205,85,3205), 3.0, normal);
            update.invoke(bridge, player, hit, 0x880F00, carrier);
            ItemDisplay display = (ItemDisplay)displayField.get(carrier);
            AbsenceSmoke.check(display != null && !display.isPersistent(), "Invalid StrobeLights carrier");
            AbsenceSmoke.check(NamespacedKey.fromString("strobelights:carrier/rgb").equals(display.getItemStack().getItemMeta().getItemModel()), "Not StrobeLights RGB item");
            var translation=display.getTransformation().getTranslation();
            Location rendered=display.getLocation().add(translation.x,translation.y,translation.z);
            AbsenceSmoke.check(rendered.distanceSquared(new Location(world,3205,85,3205)) < .00001, "Strobe source XYZ mismatch");
            display.remove();
        }
        AbsenceSmoke.check(world.getEntitiesByClass(TextDisplay.class).isEmpty(), "Replacement surface renderer still present");
        ((AutoCloseable)bridge).close();
    }
}
