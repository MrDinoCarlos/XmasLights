package es.astralicastudio.xmaslights;

import net.kyori.adventure.resource.ResourcePackInfo;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Contract fixture only; does not bundle or emulate Nexo's pack implementation. */
public class NexoHostFixture extends JavaPlugin {
    public final Generator generator = new Generator();
    public final PackServer server = new PackServer();
    public byte[] inputAtEnable;
    public int runtimeCalls;
    @Override public void onEnable() { inputAtEnable = input(); }
    public Generator packGenerator() { if (!isEnabled()) throw new IllegalStateException("Runtime API called before Nexo enable"); runtimeCalls++; return generator; }
    public PackServer packServer() { if (!isEnabled()) throw new IllegalStateException("Runtime API called before Nexo enable"); runtimeCalls++; return server; }
    private byte[] input() {
        try { Path path=getDataFolder().toPath().resolve("pack/external_packs/"+NexoPackBridge.INPUT_NAME); return Files.isRegularFile(path)?Files.readAllBytes(path):null; }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    public final class Generator {
        public CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        public int rebuilds;
        public boolean delayed;
        public byte[] rebuiltInput;
        public CompletableFuture<Void> getPackGenFuture() { return future; }
        public void regeneratePack() {
            rebuilds++; rebuiltInput=input(); server.id=UUID.randomUUID();
            future=delayed?new CompletableFuture<>():CompletableFuture.completedFuture(null);
        }
    }
    public static final class PackServer {
        public UUID id = UUID.randomUUID();
        public boolean uploaded = true;
        public final List<UUID> sent = new ArrayList<>();
        public boolean isPackUploaded() { return uploaded; }
        public ResourcePackInfo packInfo() { return ResourcePackInfo.resourcePackInfo(id,URI.create("https://nexo.example.test/pack.zip"),"0123456789abcdef0123456789abcdef01234567"); }
        public void sendPack(Player player) { sent.add(player.getUniqueId()); }
    }
}
