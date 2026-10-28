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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfig
implements PluginSettings {
    private final JavaPlugin plugin;
    private volatile boolean legacyEnabled = true;
    private volatile boolean hexEnabled = true;
    private volatile boolean miniMessageEnabled = true;
    private volatile long cacheMillis = 30000L;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        FileConfiguration config = this.plugin.getConfig();
        this.legacyEnabled = config.getBoolean("chat.modes.legacy", true);
        this.hexEnabled = config.getBoolean("chat.modes.hex", true);
        this.miniMessageEnabled = config.getBoolean("chat.modes.minimessage", true);
        this.cacheMillis = Math.max(0L, config.getLong("database.cache-millis", 60000L));
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

