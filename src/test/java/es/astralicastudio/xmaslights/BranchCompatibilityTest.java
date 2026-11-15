package es.astralicastudio.xmaslights;

import org.junit.jupiter.api.Test;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class BranchCompatibilityTest {
    @Test void selectsTheRunningPatchAndRejectsOtherBranches() {
        var properties = new Properties(); properties.setProperty("branch", "1.21.X");
        properties.setProperty("minecraft-versions", "1.21.4,1.21.5,1.21.6,1.21.7,1.21.8,1.21.9,1.21.10,1.21.11");
        for (String version : properties.getProperty("minecraft-versions").split(","))
            assertEquals(version, Compatibility.select(properties, version));
        assertThrows(IllegalStateException.class, () -> Compatibility.select(properties, "26.1.2"));
        assertThrows(IllegalStateException.class, () -> Compatibility.select(properties, "1.21.3"));
    }
}
