package es.astralicastudio.xmaslights;

import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Public Paper profiles only; no NMS, profile completion or HTTP requests from plugin code. */
final class TextureHeads {
    private TextureHeads() { }
    static URI textureUri(String input) {
        if (input == null || input.length() > 8192) throw new IllegalArgumentException("texture");
        String value = input.trim();
        if (value.startsWith("eyJ")) value = JsonParser.parseString(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        if (value.matches("[a-fA-F0-9]{20,64}")) value = "https://textures.minecraft.net/texture/" + value;
        URI uri = URI.create(value);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().matches("/texture/[a-fA-F0-9]{20,64}")) throw new IllegalArgumentException("texture");
        return URI.create("https://textures.minecraft.net" + uri.getPath());
    }
    static ItemStack create(String input) throws Exception {
        URI uri = textureUri(input);
        var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8)));
        var textures = profile.getTextures();
        textures.setSkin(uri.toURL());
        profile.setTextures(textures);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(profile));
        return head;
    }
}
