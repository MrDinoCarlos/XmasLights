package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    private static final String HASH = "8e717b485d95b3bd14db1350c55a29da99ee1d3195c050617088a9677ac42";
    @Test void textureHashesUrlsAndBase64UseOnlyTheMinecraftTextureHost() {
        String url = "https://textures.minecraft.net/texture/" + HASH;
        assertEquals(url, TextureHeads.textureUri(HASH).toString());
        assertEquals(url, TextureHeads.textureUri(url.replace("https:", "http:")).toString());
        String base64 = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(StandardCharsets.UTF_8));
        assertEquals(url, TextureHeads.textureUri(base64).toString());
    }
    @ParameterizedTest @ValueSource(strings = {"http://127.0.0.1/texture/aaaaaaaaaaaaaaaaaaaa", "https://textures.minecraft.net.evil.test/texture/aaaaaaaaaaaaaaaaaaaa", "file:///tmp/skin", "https://textures.minecraft.net:443/texture/aaaaaaaaaaaaaaaaaaaa", "https://textures.minecraft.net/texture/aaaaaaaaaaaaaaaaaaaa?url=local", "https://evil@textures.minecraft.net/texture/aaaaaaaaaaaaaaaaaaaa", "eyJ!!!"})
    void unsafeTexturesAreRejected(String texture) { assertThrows(RuntimeException.class, () -> TextureHeads.textureUri(texture)); }
    @ParameterizedTest @ValueSource(strings = {"NaN,0,0", "1,Infinity,2", "0,0,-Infinity", "30000001,0,0", "0,2049,0", "1,2", "1,2,3,4"})
    void unsafeCoordinatesAreRejected(String input) { assertThrows(IllegalArgumentException.class, () -> LightManager.parseVec(input)); }
    @ParameterizedTest @ValueSource(strings = {"all", "NONE", "../test", "a.b", "a:b", "", "name with space"})
    void unsafeGroupNamesAreRejected(String input) { assertFalse(LightManager.validGroup(input)); }
}
