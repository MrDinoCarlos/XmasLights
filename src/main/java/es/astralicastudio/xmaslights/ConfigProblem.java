package es.astralicastudio.xmaslights;

/** Our validation diagnostics are translatable; external YAML/API exceptions retain their details. */
final class ConfigProblem extends IllegalArgumentException {
    private final String key;
    private final Object[] values;
    ConfigProblem(String key, Object... values) { super(key + ": " + java.util.Arrays.toString(values)); this.key = key; this.values = values; }
    String localized(Messages messages) { return messages.plain("validation." + key, values); }
}
