package es.astralicastudio.xmaslights;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** A real test JavaPlugin with only the public pack-readiness contract, never a strobe manager. */
public class StrobeHostFixture extends JavaPlugin {
    static byte[] embeddedPack;
    public boolean availablePack = true;
    public final PackState state = new PackState();
    public int managerCalls;
    public int stableProtocol = 1;
    public int protocolCalls;
    // Legacy host method aggregates TRP's capability, rather than just StrobeLights' own pack.
    public int stableSourceProtocol() { protocolCalls++; return stableProtocol; }
    public static class PackState { boolean ready = true; public boolean isLoaded(Player player) { return ready; } }
    public PackState resourcePack() { return state; }
    public Object manager() { managerCalls++; throw new AssertionError("Renderer integration must not access the strobe registry"); }
    @Override public InputStream getResource(String name) {
        return name.startsWith("embedded/") ? availablePack ? new ByteArrayInputStream(embeddedPack) : null : super.getResource(name);
    }
}
