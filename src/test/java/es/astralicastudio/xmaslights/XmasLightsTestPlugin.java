package es.astralicastudio.xmaslights;

/** Isolate network tests from the developer's running Minecraft server. */
public class XmasLightsTestPlugin extends XmasLightsPlugin {
    @Override public void onLoad() {
        try (var socket = new java.net.ServerSocket(0)) {
            var config = loadResource("config.yml");
            config.set("updates.enabled", false);
            config.set("strobelights.renderer", "shader");
            config.set("model-pack.embedded.port", socket.getLocalPort());
            SafeYaml.save(config, getDataFolder().toPath().resolve("config.yml"), false);
        } catch (Exception failure) { throw new AssertionError(failure); }
        super.onLoad();
    }
}
