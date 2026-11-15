package es.astralicastudio.xmaslights;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.nio.file.*;

/** End-to-end source generation with a connected offline protocol client, not a mocked Player. */
public final class PlayerSourceSmoke extends JavaPlugin implements Listener {
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (!Files.exists(Path.of("player-passed.txt"))) fail(new AssertionError("Client probe timed out")); }, 2000);
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer(); World world = player.getWorld();
        try {
            player.setGameMode(GameMode.SPECTATOR);
            for (int x=199; x<=201; x++) for (int z=199; z<=201; z++) world.loadChunk(x,z);
            for (int x=3200; x<3216; x++) for (int z=3200; z<3216; z++) world.getBlockAt(x,80,z).setType(Material.STONE);
            for (int x=3200; x<3216; x++) for (int y=81; y<89; y++) world.getBlockAt(x,y,3212).setType(Material.STONE);
            player.teleport(new Location(world,3204,84,3204));
            var plugin = (XmasLightsPlugin)Bukkit.getPluginManager().getPlugin("XmasLights");
            Vector a = new Vector(3202,84,3205), b = new Vector(3210,84,3205);
            plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType("red"),new Vector(),null,false,null);
            a = new Vector(3202,85,3211.7); b = new Vector(3210,85,3211.7);
            plugin.manager().create(player,a,b,a.toBlockVector(),b.toBlockVector(),plugin.manager().getType("wall-red"),new Vector(0,0,-1),null,false,null);
            Bukkit.getScheduler().runTaskLater(this, () -> inspect(plugin, player), 100);
        } catch(Throwable failure) { fail(failure); }
    }
    private void inspect(XmasLightsPlugin plugin, Player player) {
        try {
            var diagnostic = LightManager.class.getDeclaredMethod("rgbDiagnostic",Player.class); diagnostic.setAccessible(true);
            for (String line : (java.util.List<String>)diagnostic.invoke(plugin.manager(),player)) getLogger().info(line);
            for (LightString string : plugin.manager().getStrings()) {
                var bulbs=(java.util.Map<Integer,Display>)read(string,"bulbs");
                getLogger().info("SOURCE STATE on="+read(string,"on")+", spawned="+read(string,"spawned")+", count="+read(string,"count")+", stride="+read(string,"rgbStride")+", linkedBulbs="+bulbs.size());
                var point = Class.forName("es.astralicastudio.xmaslights.LightGeometry").getDeclaredMethod("lightPoint",LightString.class,LightType.class,int.class); point.setAccessible(true);
                for (var bulb : bulbs.entrySet()) {
                    var source = ((Vector)point.invoke(null,string,plugin.manager().getType((String)read(string,"type")),bulb.getKey())).toLocation(player.getWorld());
                    long key=Chunk.getChunkKey(source.getBlockX()>>4,source.getBlockZ()>>4);
                    boolean sent=player.isChunkSent(key);
                    getLogger().info("BULB index="+bulb.getKey()+", valid="+bulb.getValue().isValid()+", loaded="+source.getWorld().isChunkLoaded(source.getBlockX()>>4,source.getBlockZ()>>4)+", isChunkSent="+sent+", actualSentSet="+player.getSentChunkKeys().contains(key)+", at="+source);
                }
            }
            var marker=new NamespacedKey(plugin,"rgb_carrier");
            var carriers=player.getWorld().getEntitiesByClass(ItemDisplay.class).stream().filter(d->d.getPersistentDataContainer().has(marker)).toList();
            if (carriers.isEmpty()) throw new AssertionError("No actual StrobeLights RGB carriers for a connected player");
            if (!player.getWorld().getEntitiesByClass(TextDisplay.class).isEmpty()) throw new AssertionError("Replacement TextDisplay renderer still present");
            boolean floor=false, wall=false;
            var captured=new com.google.gson.JsonArray();
            carriers=carriers.stream().sorted(java.util.Comparator.comparingInt(d->(d.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB()>>12)&15)).toList();
            for (ItemDisplay display:carriers) {
                if (!NamespacedKey.fromString("strobelights:carrier/rgb").equals(display.getItemStack().getItemMeta().getItemModel()))
                    throw new AssertionError("Not StrobeLights' own RGB carrier");
                var t=display.getTransformation().getTranslation(); Location position=display.getLocation().add(t.x,t.y,t.z);
                floor |= Math.abs(position.getY()-81.04)<.001;
                wall |= Math.abs(position.getZ()-3211.96)<.001;
                var sample=new com.google.gson.JsonObject();
                sample.addProperty("payload",display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors().getFirst().asRGB());
                var relative=position.toVector().subtract(player.getEyeLocation().toVector());
                var xyz=new com.google.gson.JsonArray(); xyz.add(relative.getX()); xyz.add(relative.getY()); xyz.add(relative.getZ());
                sample.add("position",xyz); captured.add(sample);
            }
            if (!floor || !wall) throw new AssertionError("Missing StrobeLights floor/wall source: floor="+floor+", wall="+wall);
            var host=Bukkit.getPluginManager().getPlugin("StrobeLights");
            Object pack=host.getClass().getMethod("resourcePack").invoke(host);
            if (!Boolean.TRUE.equals(pack.getClass().getMethod("isLoaded",Player.class).invoke(pack,player)))
                throw new AssertionError("Real StrobeLights did not confirm its downloaded pack");
            Files.writeString(Path.of("player-passed.txt"),"PASS actual StrobeLights carriers="+carriers.size()+", floor=true, wall=true, TextDisplay=0");
            Files.writeString(Path.of("strobe-runtime-sources.json"),captured.toString());
            Bukkit.shutdown();
        } catch(Throwable failure) { fail(failure); }
    }
    private Object read(Object target,String name) throws Exception {
        var field=target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private void fail(Throwable failure) {
        getLogger().log(java.util.logging.Level.SEVERE,"PLAYER PROBE FAILED",failure);
        try { Files.writeString(Path.of("player-failed.txt"),failure.toString()); } catch(Exception ignored) { }
        Bukkit.shutdown();
    }
}
