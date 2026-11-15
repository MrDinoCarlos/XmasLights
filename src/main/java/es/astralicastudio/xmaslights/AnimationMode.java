package es.astralicastudio.xmaslights;

import java.util.Locale;

public enum AnimationMode {
    STEADY,
    BLINK,
    ALTERNATE,
    CHASE,
    TWINKLE,
    SWEEP,
    BREATHE;


    public static AnimationMode parse(String string) {
        if (string == null) {
            return null;
        }
        try {
            return AnimationMode.valueOf(string.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}

