package lifecycle;

import es.astralicastudio.xmaslights.XmasLightsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import java.lang.reflect.Proxy;
import java.util.*;

/** Checks every bundled renderer against real API implementations in both pack modes. */
public final class AllDesignsSmoke extends AbsenceSmoke {
    @Override public void onEnable() { later(40, () -> {
        world = Bukkit.getWorlds().getFirst(); loadArea();
        var plugin = (XmasLightsPlugin)Bukkit.getPluginManager().getPlugin("XmasLights");
        check(plugin != null && plugin.isEnabled(), "XmasLights did not enable");
        UUID owner = UUID.randomUUID();
        Player player = (Player)Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, method, args) -> switch(method.getName()) {
            case "getWorld" -> world; case "getUniqueId" -> owner; case "getGameMode" -> GameMode.CREATIVE;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        for (boolean packed : List.of(false, true)) {
            var config = plugin.getConfig(); config.set("model-pack.enabled", packed);
            config.set("model-pack.assume-installed", true); config.set("model-pack.auto-send", false);
            config.set("model-pack.embedded.enabled", false); config.set("updates.enabled", false);
            config.save(new java.io.File(plugin.getDataFolder(), "config.yml")); plugin.reload();
            check(plugin.manager().getTypes().size() == 51, "Incomplete type catalog");
            for (var type : plugin.manager().getTypes().values()) {
                Vector a = new Vector(3203, 86, 3205), b = new Vector(3209, 86, 3205);
                var string = plugin.manager().create(player, a, b, a.toBlockVector(), b.toBlockVector(), type, new Vector(0,0,1), null, false, null);
                check(!geometry().isEmpty(), "No displays for " + type.id());
                check(geometry().stream().noneMatch(Entity::isPersistent), "Persistent part for " + type.id());
                check(plugin.manager().remove(string), "Cannot remove " + type.id());
                check(geometry().isEmpty(), "Part left after removal: " + type.id());
            }
            SurfaceSmoke.verify(plugin, world);
        }
        passed("designs");
    }); }
}
