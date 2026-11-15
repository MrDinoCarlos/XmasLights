package es.astralicastudio.xmaslights;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Public overlay API contract used by the optional provider integration. */
public class TrpPackFixture extends JavaPlugin {
    static String address;
    int registrations, requests;
    public String registerResourcePackOverlay(Plugin owner, String file, byte[] pack) { registrations++; return address; }
    public void resendResourcePack(Plugin owner, Player player) { requests++; }
    public void unregisterResourcePackOverlay(Plugin owner) { }
}
