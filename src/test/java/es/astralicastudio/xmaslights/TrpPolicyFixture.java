package es.astralicastudio.xmaslights;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

public class TrpPolicyFixture extends JavaPlugin {
    public boolean rgbAllowed;
    public int policyCalls;
    public boolean isFabulousRgbEnabled(UUID player) { policyCalls++; return rgbAllowed; }
}
