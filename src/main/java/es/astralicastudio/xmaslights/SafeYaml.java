package es.astralicastudio.xmaslights;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

/** Strict reads and atomic replacement: invalid files must never become empty defaults. */
final class SafeYaml {
    private SafeYaml() { }
    static YamlConfiguration load(Path path) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        if (Files.exists(path)) yaml.load(path.toFile());
        return yaml;
    }
    static void save(YamlConfiguration yaml, Path path, boolean backup) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            if (backup && Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    static void versionBackup(Path path) throws IOException {
        if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + "." + Instant.now().toString().replace(':', '-') + ".bak"));
    }
}
