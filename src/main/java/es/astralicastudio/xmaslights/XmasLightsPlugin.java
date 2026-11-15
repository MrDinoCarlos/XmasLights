package es.astralicastudio.xmaslights;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class XmasLightsPlugin extends JavaPlugin {
    private Messages messages;
    private YamlConfiguration settings;
    private YamlConfiguration types;
    private LightManager manager;
    private XmasLightsListener listener;
    private ModelPackService modelPack;
    private DecoSwapSupport decoSwap;
    private UpdateChecker updates;
    @Override public void onLoad() {
        modelPack = new ModelPackService(this);
        modelPack.prepareEarly();
    }
    @Override public void onEnable() {
        messages = new Messages(this);
        try {
            LegacyMigration.run(this);
            loadSettings();
            manager = new LightManager(this);
            manager.loadConfig();
            decoSwap = new DecoSwapSupport(this);
            manager.loadData();
            decoSwap.reconcileOnStartup();
            manager.scanLoadedEntities();
            manager.enforceEntityBudget();
            if (modelPack == null) modelPack = new ModelPackService(this);
            getServer().getPluginManager().registerEvents(modelPack, this);
            modelPack.configure();
            manager.startAnimation();
            getServer().getScheduler().runTaskLater(this, manager::scheduleAutoUpdate, 40L);
            listener = new XmasLightsListener(this, manager);
            getServer().getPluginManager().registerEvents(listener, this);
            getServer().getOnlinePlayers().forEach(manager::refreshLegacyKits);
            var command = Objects.requireNonNull(getCommand("xmaslights"), "plugin.yml: xmaslights");
            var executor = new XmasLightsCommand(this, manager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            updates = new UpdateChecker(this);
            getServer().getPluginManager().registerEvents(updates, this);
            updates.configure();
            localizeMetadata();
            messages.info("log.enabled", "strings", manager.getStrings().size(), "types", manager.getTypes().size());
        } catch (Exception failure) {
            getLogger().severe(messages.plain("log.start-failed", "error", diagnostic(failure)));
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if (updates != null) updates.close();
        try { if (manager != null) manager.shutdown(); }
        finally { if (modelPack != null) modelPack.close(); }
    }
    private void loadSettings() throws Exception {
        ResourceUpdater updater = new ResourceUpdater(this);
        YamlConfiguration nextConfig = updater.update("config.yml", "config-version");
        YamlConfiguration nextTypes = updater.update("types.yml", "types-version");
        LightManager.validateSettings(nextConfig, nextTypes);
        messages.reload(updater, nextConfig);
        settings = nextConfig;
        types = nextTypes;
    }
    public void reload() throws Exception {
        YamlConfiguration oldSettings = settings, oldTypes = types;
        Messages.State oldMessages = messages.snapshot();
        try {
            loadSettings();
            manager.loadConfig();
        } catch (Exception failure) {
            settings = oldSettings; types = oldTypes; messages.restore(oldMessages);
            throw failure;
        }
        listener.clearPending();
        localizeMetadata();
        manager.startAnimation();
        manager.scheduleAutoUpdate();
        modelPack.configure();
        updates.configure();
        getServer().getOnlinePlayers().forEach(manager::refreshLegacyKits);
    }
    @Override public YamlConfiguration getConfig() { return settings == null ? new YamlConfiguration() : settings; }
    public YamlConfiguration getTypesConfig() { return types; }
    public Messages messages() { return messages; }
    public LightManager manager() { return manager; }
    /** Versioned, optional interface used by DecoSwap without linking either plugin's classes. */
    public DecoSwapSupport decoSwap() { return decoSwap; }
    public void requestModelPack(org.bukkit.entity.Player player) { modelPack.request(player, true); }
    boolean modelPackReady(org.bukkit.entity.Player player) { return modelPack != null && modelPack.ready(player); }
    boolean usesModelPack() { return getConfig().getBoolean("model-pack.enabled", true); }
    private void localizeMetadata() {
        var command = getCommand("xmaslights");
        if (command != null) command.setDescription(messages.plain("metadata.command-description"));
        for (String id : java.util.List.of("use", "admin", "manage-all", "manage-groups")) {
            var permission = getServer().getPluginManager().getPermission("xmaslights." + id);
            if (permission != null) permission.setDescription(messages.plain("permissions." + id));
        }
    }
    String diagnostic(Exception failure) { return failure instanceof ConfigProblem problem ? problem.localized(messages) : String.valueOf(failure.getMessage()); }
    YamlConfiguration loadResource(String resource) {
        try (var stream = Objects.requireNonNull(getResource(resource), resource); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            yaml.load(reader);
            return yaml;
        } catch (Exception failure) { throw new IllegalStateException(resource, failure); }
    }
    static void copySection(org.bukkit.configuration.ConfigurationSection source, YamlConfiguration target, String path) {
        for (String key : source.getKeys(true)) if (!source.isConfigurationSection(key)) target.set(path + "." + key, source.get(key));
    }
}
