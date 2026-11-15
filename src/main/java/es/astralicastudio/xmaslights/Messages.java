package es.astralicastudio.xmaslights;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.util.*;

/** One translation path for chat, help, item names/lore and console diagnostics. */
public final class Messages {
    static final List<String> LANGUAGES = List.of("en", "es", "fr", "it", "de");
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.legacyAmpersand();
    private final XmasLightsPlugin plugin;
    private final YamlConfiguration english;
    private Map<String, YamlConfiguration> catalogs;
    private String defaultLanguage = "en";
    private boolean usePlayerLocale;
    record State(Map<String, YamlConfiguration> catalogs, String language, boolean playerLocale) { }
    State snapshot() { return new State(catalogs, defaultLanguage, usePlayerLocale); }
    void restore(State state) { catalogs = state.catalogs(); defaultLanguage = state.language(); usePlayerLocale = state.playerLocale(); }
    boolean hasKey(String key) { return catalogs.getOrDefault(defaultLanguage, english).isString(key) || english.isString(key); }
    Messages(XmasLightsPlugin plugin) {
        this.plugin = plugin;
        english = plugin.loadResource("lang/en.yml");
        catalogs = Map.of("en", english);
    }
    void reload(ResourceUpdater updater, YamlConfiguration config) throws Exception {
        Map<String, YamlConfiguration> next = new LinkedHashMap<>();
        for (String language : LANGUAGES) {
            YamlConfiguration catalog = updater.update("lang/" + language + ".yml", "language-version");
            validateCatalog(catalog, english);
            next.put(language, catalog);
        }
        String selected = normalize(config.getString("language.default", "en"));
        if (!LANGUAGES.contains(selected)) throw new IllegalArgumentException("language.default: " + selected);
        catalogs = Map.copyOf(next);
        defaultLanguage = selected;
        usePlayerLocale = config.getBoolean("language.use-player-locale", false);
    }
    static void validateCatalog(YamlConfiguration catalog, YamlConfiguration reference) {
        for (String key : reference.getKeys(true)) {
            if (reference.isConfigurationSection(key) || key.equals("language-version")) continue;
            if (!catalog.isString(key)) throw new ConfigProblem("text", "path", key);
            if (!placeholders(catalog.getString(key)).equals(placeholders(reference.getString(key)))) throw new ConfigProblem("placeholders", "path", key);
        }
    }
    static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        var matcher = java.util.regex.Pattern.compile("\\{[a-zA-Z][a-zA-Z0-9-]*}").matcher(text);
        while (matcher.find()) names.add(matcher.group());
        return names;
    }
    private static String normalize(String locale) { return locale.toLowerCase(Locale.ROOT).replace('-', '_').split("_")[0]; }
    String language(CommandSender sender) {
        if (usePlayerLocale && sender instanceof Player player && catalogs.containsKey(player.locale().getLanguage())) return player.locale().getLanguage();
        return defaultLanguage;
    }
    public Component text(CommandSender sender, String key, Object... replacements) { return render(language(sender), key, replacements); }
    public Component text(String key, Object... replacements) { return render(defaultLanguage, key, replacements); }
    private Component render(String language, String key, Object... replacements) {
        if (replacements.length % 2 != 0) throw new IllegalArgumentException("Replacement pairs required");
        String pattern = catalogs.getOrDefault(language, english).getString(key, english.getString(key, key));
        Component result = COLORS.deserialize(pattern).decoration(TextDecoration.ITALIC, false);
        // Substitute after parsing, in one pass: replacement values cannot inject colors or placeholders.
        Map<String, Component> values = new HashMap<>();
        for (int i = 0; i < replacements.length; i += 2) {
            Object value = replacements[i + 1];
            values.put("{" + replacements[i] + "}", value instanceof Component component ? component : Component.text(String.valueOf(value)));
        }
        if (!values.isEmpty()) result = result.replaceText(builder -> builder.match("\\{[a-zA-Z][a-zA-Z0-9-]*}")
                .replacement((match, original) -> values.getOrDefault(match.group(), original.build())));
        return result;
    }
    public void send(CommandSender sender, String key, Object... replacements) { sender.sendMessage(text(sender, "prefix").append(text(sender, key, replacements))); }
    public String plain(String key, Object... replacements) { return PlainTextComponentSerializer.plainText().serialize(text(key, replacements)); }
    public String plain(CommandSender sender, String key, Object... replacements) { return PlainTextComponentSerializer.plainText().serialize(text(sender, key, replacements)); }
    public void info(String key, Object... replacements) { plugin.getLogger().info(plain(key, replacements)); }
    public void warn(String key, Object... replacements) { plugin.getLogger().warning(plain(key, replacements)); }
}
