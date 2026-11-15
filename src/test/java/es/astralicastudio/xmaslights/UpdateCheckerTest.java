package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    private String release(String id, String version, String kind, String minecraft, String loader) {
        return "{\"id\":\"" + id + "\",\"version_number\":\"" + version + "\",\"version_type\":\"" + kind
                + "\",\"game_versions\":[\"" + minecraft + "\"],\"loaders\":[\"" + loader + "\"]}";
    }
    @Test void selectsHighestCompatibleStableReleaseRegardlessOfApiOrdering() {
        String json = "[" + release("a", "2.10.0+mc.v.1.21.4", "release", "1.21.4", "paper") + ","
                + release("b", "2.9.1", "release", "1.21.4", "paper") + ","
                + release("c", "3.0.0", "beta", "1.21.4", "paper") + ","
                + release("d", "4.0.0", "release", "26.3", "paper") + ","
                + release("e", "5.0.0", "release", "1.21.4", "fabric") + "]";
        assertEquals(new UpdateChecker.Release("2.10.0+mc.v.1.21.4", "a"), UpdateChecker.select(json, "2.9.0", "1.21.4"));
    }
    @Test void ignoresSameVersionOlderVersionsAndUnknownVersionFormats() {
        for (String version : new String[]{"2.9.0", "2.8.8", "2.9.0+mc.v.26.3", "dev", "3.0.0-beta"})
            assertNull(UpdateChecker.select("[" + release("a", version, "release", "26.3", "paper") + "]", "2.9.0", "26.3"));
        assertTrue(UpdateChecker.compare("2.10.0", "2.9.9") > 0);
        assertEquals(0, UpdateChecker.compare("2.9", "2.9.0"));
    }
    @Test void responseCannotInjectAnExternalUpdateUrl() {
        assertNull(UpdateChecker.select("[" + release("../evil", "3.0.0", "release", "26.3", "paper") + "]", "2.9.0", "26.3"));
    }
}
