/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.plugin.java.JavaPlugin
 */
package io.github.fchatcolor.settings;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.settings.PluginSettings;
import io.github.fchatcolor.api.storage.DatabaseConfig;
import io.github.fchatcolor.api.storage.DatabaseType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PluginConfig
implements PluginSettings {
    private final JavaPlugin plugin;
    private volatile boolean legacyEnabled = true;
    private volatile boolean hexEnabled = true;
    private volatile boolean miniMessageEnabled = true;
    private volatile boolean menuEnabled = true;
    private volatile String defaultColor = "white";
    private volatile Map<String, String> defaultPermColors = Collections.emptyMap();
    private volatile long cacheMillis = 30000L;
    private volatile boolean debug = false;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        FileConfiguration config = this.plugin.getConfig();
        this.legacyEnabled = config.getBoolean("chat.modes.legacy", true);
        this.hexEnabled = config.getBoolean("chat.modes.hex", true);
        this.miniMessageEnabled = config.getBoolean("chat.modes.minimessage", true);
        this.menuEnabled = config.getBoolean("menu.enabled", true);
        this.debug = config.getBoolean("chat.debug", false);
        String def = config.getString("chat.default-color", null);
        if (def == null) def = config.getString("chat.default_color", null);
        if (def == null) def = config.getString("default-color", "white");
        this.defaultColor = def != null ? def.trim() : "white";
        this.defaultPermColors = loadDefaultPermColors(config);
        this.cacheMillis = Math.max(0L, config.getLong("database.cache-millis", 60000L));
        if (this.debug) {
            try { this.plugin.getLogger().info("[FChatColor-DEBUG] default-color=" + this.defaultColor + " default-permission-colors=" + this.defaultPermColors); } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean isModeEnabled(ColorMode mode) {
        if (mode == null) {
            return false;
        }
        switch (mode) {
            case LEGACY: {
                return this.legacyEnabled;
            }
            case HEX: {
                return this.hexEnabled;
            }
            case MINIMESSAGE: {
                return this.miniMessageEnabled;
            }
        }
        return false;
    }

    @Override
    public long getCacheMillis() {
        return this.cacheMillis;
    }

    @Override
    public boolean isMenuEnabled() {
        return this.menuEnabled;
    }

    @Override
    public String getDefaultColor() {
        return this.defaultColor;
    }

    @Override
    public Map<String, String> getDefaultPermissionColors() {
        return this.defaultPermColors;
    }

    @Override
    public boolean isDebug() {
        return this.debug;
    }

    private static Map<String, String> loadDefaultPermColors(FileConfiguration config) {
        // Supports:
        // chat.default-permission-colors:
        //   "fchatcolor.default-color.red": "red"
        // and also list form:
        // chat.default-permission-colors:
        //   - permission: "fchatcolor.default-color.red"
        //     color: "red"
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationSection sec = config.getConfigurationSection("chat.default-permission-colors");
        if (sec == null) sec = config.getConfigurationSection("chat.default_permission_colors");
        if (sec == null) sec = config.getConfigurationSection("chat.default-colors");
        if (sec != null) {
            // Try map form (keys are permissions, values are colors).
            // NOTE: must use getValues(false) because sec.get(key) re-splits
            // quoted dotted keys ("fchatcolor.default-color.red") and misses them.
            for (Map.Entry<String, Object> e : sec.getValues(false).entrySet()) {
                Object val = e.getValue();
                if (val instanceof String) {
                    String perm = e.getKey() == null ? "" : e.getKey().trim();
                    String col = ((String) val).trim();
                    if (!perm.isEmpty() && !col.isEmpty()) out.put(perm, col);
                }
                // Nested maps (unquoted dotted keys) are handled by the deep fallback below.
            }
            // If we got nothing due to dot splitting, try to read raw values via getValues
            if (out.isEmpty()) {
                // Attempt to read via getValues(true) and reconstruct permissions with dots
                // getValues(true) returns flattened dotted keys
                for (Map.Entry<String, Object> e : sec.getValues(true).entrySet()) {
                    String fullKey = e.getKey(); // e.g. "fchatcolor.default-color.red"
                    Object v = e.getValue();
                    if (v instanceof String && fullKey.contains(".")) {
                        // This is likely a leaf permission -> color
                        // Reconstruct permission as "fchatcolor.default-color.red" if it looks like permission
                        if (fullKey.startsWith("fchatcolor.") || fullKey.contains(".default")) {
                            out.put(fullKey, ((String) v).trim());
                        }
                    }
                }
            }
        }
        // List form fallback: chat.default-permission-colors as List<Map>
        if (out.isEmpty()) {
            List<?> list = config.getList("chat.default-permission-colors");
            if (list == null) list = config.getList("chat.default_permission_colors");
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) o;
                        Object p = m.get("permission");
                        Object c = m.get("color");
                        if (c == null) c = m.get("value");
                        if (p instanceof String && c instanceof String) {
                            String perm = ((String) p).trim();
                            String col = ((String) c).trim();
                            if (!perm.isEmpty() && !col.isEmpty()) out.put(perm, col);
                        }
                    } else if (o instanceof String) {
                        // String like "fchatcolor.default-color.red: red"
                        String s = ((String) o).trim();
                        int colon = s.indexOf(':');
                        int eq = s.indexOf('=');
                        int sep = colon >= 0 ? colon : eq;
                        if (sep > 0) {
                            String perm = s.substring(0, sep).trim();
                            String col = s.substring(sep + 1).trim();
                            if (!perm.isEmpty() && !col.isEmpty()) out.put(perm, col);
                        }
                    }
                }
            }
        }
        return out.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(out);
    }

    public DatabaseConfig getDatabaseConfig() {
        FileConfiguration config = this.plugin.getConfig();
        DatabaseType type = DatabaseType.parse(config.getString("database.type", "SQLITE"), DatabaseType.SQLITE);
        DatabaseConfig.Builder builder = DatabaseConfig.builder().type(type).table(config.getString("database.table", "fchatcolor_colors")).poolSize(config.getInt("database.pool-size", 10)).host(config.getString("database.mysql.host", "localhost")).port(config.getInt("database.mysql.port", 3306)).database(config.getString("database.mysql.database", "fchatcolor")).username(config.getString("database.mysql.username", "root")).password(config.getString("database.mysql.password", "")).sqliteFile(config.getString("database.sqlite.file", "data.db"));
        String jdbcProperties = config.getString("database.mysql.properties", "");
        if (jdbcProperties != null && !jdbcProperties.trim().isEmpty()) {
            for (String pair : jdbcProperties.split(";")) {
                int equal = pair.indexOf(61);
                if (equal <= 0) continue;
                builder.property(pair.substring(0, equal).trim(), pair.substring(equal + 1).trim());
            }
        }
        return builder.build();
    }
}

