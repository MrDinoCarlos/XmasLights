package es.astralicastudio.xmaslights;

/** Lifecycle contract fixture; never included in the plugin JAR. */
public class DecoHostFixture extends org.bukkit.plugin.java.JavaPlugin {
    static String disposition;
    public String externalDeploymentState(java.util.UUID id) { return disposition; }
}
