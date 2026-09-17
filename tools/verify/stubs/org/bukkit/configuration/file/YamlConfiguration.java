package org.bukkit.configuration.file;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
public class YamlConfiguration {
    private final Map<String, Object> values = new LinkedHashMap<>();
    public void load(File file) throws Exception {
        values.clear();
        if (file == null || !file.isFile()) return;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int separator = trimmed.indexOf(':');
            if (separator < 0) continue;
            values.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
    }
    public void save(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        values.forEach((key, value) -> sb.append(key).append(": ").append(value).append('\n'));
        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static YamlConfiguration loadConfiguration(File file) {
        YamlConfiguration c = new YamlConfiguration();
        try { c.load(file); } catch (Exception ignored) { }
        return c;
    }
    public void addDefault(String key, Object value) { values.putIfAbsent(key, value); }
    public boolean isSet(String key) { return values.containsKey(key); }
    public void set(String key, Object value) { values.put(key, value); }
    public boolean getBoolean(String key, boolean def) { Object v = values.get(key); return v == null ? def : Boolean.parseBoolean(v.toString()); }
    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public int getInt(String key, int def) { try { return Integer.parseInt(values.getOrDefault(key, def).toString()); } catch (Exception e) { return def; } }
    public String getString(String key, String def) { Object v = values.get(key); return v == null ? def : v.toString(); }
    public Options options() { return new Options(); }
    public static class Options { public void copyDefaults(boolean value) { } }
}
