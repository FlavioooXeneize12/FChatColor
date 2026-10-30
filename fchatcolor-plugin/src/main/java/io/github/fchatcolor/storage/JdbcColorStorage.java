/*
 * Decompiled with CFR 0.152.
 */
package io.github.fchatcolor.storage;

import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.api.storage.DatabaseConfig;
import io.github.fchatcolor.api.storage.DatabaseType;
import io.github.fchatcolor.api.storage.StorageException;
import io.github.fchatcolor.storage.SimpleConnectionPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JdbcColorStorage
implements ColorStorage {
    private static final long CACHE_ENTRY_TTL_MS = 60000L;
    private static final String NO_COLOR = "";
    private final DatabaseConfig config;
    private final String dataFolder;
    private final Logger logger;
    private final long cacheMillis;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<UUID, CacheEntry>();
    private final Map<UUID, CacheEntry> modifiersCache = new ConcurrentHashMap<UUID, CacheEntry>();
    private volatile SimpleConnectionPool pool;
    private volatile boolean connected;

    public JdbcColorStorage(DatabaseConfig config, String dataFolder, Logger logger, long cacheMillis) {
        this.config = config;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.cacheMillis = Math.max(0L, cacheMillis);
    }

    @Override
    public void init() throws StorageException {
        SimpleConnectionPool newPool = new SimpleConnectionPool(this.config, this.dataFolder);
        newPool.init();
        this.pool = newPool;
        this.connected = true;
        this.createTable();
        try { this.ensureWideColorColumn(); } catch (Throwable ignored) {}
        this.logger.info("Database " + (Object)((Object)this.config.getType()) + " ready: " + newPool.getUrl() + " (table " + this.quote(this.config.sanitizeTable()) + ")");
    }

    @Override
    public void close() {
        this.connected = false;
        SimpleConnectionPool current = this.pool;
        if (current != null) {
            current.close();
        }
        this.cache.clear();
        this.modifiersCache.clear();
    }

    @Override
    public DatabaseType getType() {
        return this.config.getType();
    }

    @Override
    public boolean isConnected() {
        if (!this.connected) {
            return false;
        }
        SimpleConnectionPool current = this.pool;
        return current != null && !current.isClosed();
    }

    @Override
    public String getSelectedColor(UUID playerId) {
        String string;
        CacheEntry entry;
        if (playerId == null) {
            return null;
        }
        if (this.pool == null || !this.connected) {
            return null;
        }
        if (this.cacheMillis > 0L && (entry = this.cache.get(playerId)) != null && !entry.isExpired(this.cacheMillis)) {
            return NO_COLOR.equals(entry.color) ? null : entry.color;
        }
        String sql = "SELECT color FROM " + this.quote(this.config.sanitizeTable()) + " WHERE uuid = ?";
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = this.pool.borrow();
            statement = connection.prepareStatement(sql);
            statement.setString(1, playerId.toString());
            resultSet = statement.executeQuery();
            String color = resultSet.next() ? this.normalize(resultSet.getString(1)) : NO_COLOR;
            this.putCache(playerId, color);
            string = NO_COLOR.equals(color) ? null : color;
        }
        catch (SQLException ex) {
            try {
                this.cache.remove(playerId);
                throw this.storageError("read", playerId, ex);
            }
            catch (Throwable throwable) {
                JdbcColorStorage.closeQuietly(resultSet);
                JdbcColorStorage.closeQuietly(statement);
                this.release(connection);
                throw throwable;
            }
        }
        JdbcColorStorage.closeQuietly(resultSet);
        JdbcColorStorage.closeQuietly(statement);
        this.release(connection);
        return string;
    }

    @Override
    public void setSelectedColor(UUID playerId, String color) {
        if (playerId == null) {
            return;
        }
        if (this.pool == null || !this.connected) {
            throw new StorageException("The database is not connected");
        }
        String normalized = this.normalize(color);
        if (normalized == null || normalized.isEmpty()) {
            this.clearSelectedColor(playerId);
            return;
        }
        this.putCache(playerId, normalized);
        String sql = this.config.getType() == DatabaseType.MYSQL ? this.upsertMysql() : this.upsertSqlite();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = this.pool.borrow();
            statement = connection.prepareStatement(sql);
            statement.setString(1, playerId.toString());
            statement.setString(2, normalized);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
        catch (SQLException ex) {
            try {
                this.cache.remove(playerId);
                throw this.storageError("save", playerId, ex);
            }
            catch (Throwable throwable) {
                JdbcColorStorage.closeQuietly(statement);
                this.release(connection);
                throw throwable;
            }
        }
        JdbcColorStorage.closeQuietly(statement);
        this.release(connection);
    }

    @Override
    public void clearSelectedColor(UUID playerId) {
        if (playerId == null) {
            return;
        }
        if (this.pool == null || !this.connected) {
            return;
        }
        this.cache.remove(playerId);
        String sql = "DELETE FROM " + this.quote(this.config.sanitizeTable()) + " WHERE uuid = ?";
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = this.pool.borrow();
            statement = connection.prepareStatement(sql);
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
        catch (SQLException ex) {
            try {
                throw this.storageError("delete", playerId, ex);
            }
            catch (Throwable throwable) {
                JdbcColorStorage.closeQuietly(statement);
                this.release(connection);
                throw throwable;
            }
        }
        JdbcColorStorage.closeQuietly(statement);
        this.release(connection);
    }

    @Override
    public String getSelectedModifiers(UUID playerId) {
        if (playerId == null) return null;
        if (this.pool == null || !this.connected) return null;
        CacheEntry entry;
        if (this.cacheMillis > 0L && (entry = this.modifiersCache.get(playerId)) != null && !entry.isExpired(this.cacheMillis)) {
            return NO_COLOR.equals(entry.color) ? null : entry.color;
        }
        String sql = "SELECT modifiers FROM " + this.quote(this.config.sanitizeTable()) + " WHERE uuid = ?";
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = this.pool.borrow();
            statement = connection.prepareStatement(sql);
            statement.setString(1, playerId.toString());
            resultSet = statement.executeQuery();
            String mods = resultSet.next() ? this.normalize(resultSet.getString(1)) : NO_COLOR;
            if (mods == null) mods = NO_COLOR;
            this.putModifiersCache(playerId, mods);
            return NO_COLOR.equals(mods) ? null : mods;
        } catch (SQLException ex) {
            // Column may not exist yet (old table), try to add it
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("no such column")) {
                try { ensureModifiersColumn(); } catch (Throwable ignored) {}
                return null;
            }
            try {
                this.modifiersCache.remove(playerId);
                throw this.storageError("read modifiers", playerId, ex);
            } catch (Throwable throwable) {
                JdbcColorStorage.closeQuietly(resultSet);
                JdbcColorStorage.closeQuietly(statement);
                this.release(connection);
                throw throwable;
            }
        } finally {
            JdbcColorStorage.closeQuietly(resultSet);
            JdbcColorStorage.closeQuietly(statement);
            this.release(connection);
        }
    }

    @Override
    public void setSelectedModifiers(UUID playerId, String modifiers) {
        if (playerId == null) return;
        if (this.pool == null || !this.connected) throw new StorageException("The database is not connected");
        String normalized = this.normalize(modifiers);
        if (normalized == null) normalized = "";
        // Keep empty string for no modifiers, but cache as NO_COLOR equivalent
        String cacheVal = normalized.isEmpty() ? NO_COLOR : normalized;
        this.putModifiersCache(playerId, cacheVal);
        String sql = this.config.getType() == DatabaseType.MYSQL ? this.upsertModifiersMysql() : this.upsertModifiersSqlite();
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = this.pool.borrow();
            statement = connection.prepareStatement(sql);
            statement.setString(1, playerId.toString());
            // For modifiers upsert we need to set color as well? Ensure row exists, keep color if already there
            // We use INSERT ... ON CONFLICT to set modifiers, but need to handle color not null
            // Simpler: try to update modifiers, if no row, insert with empty color
            // For now, use upsert that sets modifiers and keeps color
            statement.setString(2, cacheVal);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("no such column")) {
                try { ensureModifiersColumn(); } catch (Throwable ignored) {}
                // Retry once
                try {
                    if (statement != null) JdbcColorStorage.closeQuietly(statement);
                    if (connection != null) this.release(connection);
                    connection = this.pool.borrow();
                    statement = connection.prepareStatement(sql);
                    statement.setString(1, playerId.toString());
                    statement.setString(2, cacheVal);
                    statement.setLong(3, System.currentTimeMillis());
                    statement.executeUpdate();
                    return;
                } catch (Throwable retry) {
                    this.modifiersCache.remove(playerId);
                    throw this.storageError("save modifiers", playerId, ex);
                }
            }
            try {
                this.modifiersCache.remove(playerId);
                throw this.storageError("save modifiers", playerId, ex);
            } catch (Throwable throwable) {
                JdbcColorStorage.closeQuietly(statement);
                this.release(connection);
                throw throwable;
            }
        } finally {
            JdbcColorStorage.closeQuietly(statement);
            this.release(connection);
        }
    }

    @Override
    public void clearSelectedModifiers(UUID playerId) {
        if (playerId == null) return;
        if (this.pool == null || !this.connected) return;
        this.modifiersCache.remove(playerId);
        // Set modifiers to empty string
        setSelectedModifiers(playerId, "");
    }

    private void createTable() throws StorageException {
        String table = this.quote(this.config.sanitizeTable());
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table).append(" (uuid VARCHAR(36) NOT NULL, color VARCHAR(255) NOT NULL, modifiers VARCHAR(255) NOT NULL DEFAULT '', ").append("updated_at BIGINT NOT NULL, PRIMARY KEY (uuid))");
        if (this.config.getType() == DatabaseType.MYSQL) {
            sql.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
        Connection connection = null;
        Statement statement = null;
        try {
            connection = this.pool.borrow();
            statement = connection.createStatement();
            statement.execute(sql.toString());
        }
        catch (SQLException ex) {
            try {
                throw new StorageException("Could not create the table " + table + ": " + ex.getMessage(), ex);
            }
            catch (Throwable throwable) {
                JdbcColorStorage.closeQuietly(statement);
                this.release(connection);
                throw throwable;
            }
        }
        JdbcColorStorage.closeQuietly(statement);
        this.release(connection);
    }

    private String upsertMysql() {
        String table = this.quote(this.config.sanitizeTable());
        return "INSERT INTO " + table + " (uuid, color, updated_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE color = VALUES(color), updated_at = VALUES(updated_at)";
    }

    private String upsertSqlite() {
        String table = this.quote(this.config.sanitizeTable());
        return "INSERT INTO " + table + " (uuid, color, updated_at) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET color = excluded.color, updated_at = excluded.updated_at";
    }

    private String quote(String identifier) {
        return this.config.getType() == DatabaseType.MYSQL ? "`" + identifier + "`" : "\"" + identifier + "\"";
    }

    public void evict(UUID playerId) {
        if (playerId != null) {
            this.cache.remove(playerId);
            this.modifiersCache.remove(playerId);
        }
    }

    private void putModifiersCache(UUID playerId, String mods) {
        if (this.cacheMillis <= 0L) return;
        this.modifiersCache.put(playerId, new CacheEntry(mods, System.currentTimeMillis()));
        if (this.modifiersCache.size() > 5000) {
            // simple trim, reuse same logic
            this.trimCacheIfLarge();
        }
    }

    private void ensureModifiersColumn() {
        String table = this.quote(this.config.sanitizeTable());
        Connection c = null;
        Statement s = null;
        try {
            c = this.pool.borrow();
            s = c.createStatement();
            // Check if column exists by trying to select it; if fails, add it
            try {
                s.execute("SELECT modifiers FROM " + table + " LIMIT 1");
                return; // exists
            } catch (SQLException ignored) {}
            // Add column
            if (this.config.getType() == DatabaseType.MYSQL) {
                s.execute("ALTER TABLE " + table + " ADD COLUMN modifiers VARCHAR(255) NOT NULL DEFAULT ''");
            } else {
                s.execute("ALTER TABLE " + table + " ADD COLUMN modifiers VARCHAR(255) NOT NULL DEFAULT ''");
            }
        } catch (Throwable ignored) {
        } finally {
            JdbcColorStorage.closeQuietly(s);
            this.release(c);
        }
    }

    private String upsertModifiersMysql() {
        String table = this.quote(this.config.sanitizeTable());
        return "INSERT INTO " + table + " (uuid, color, modifiers, updated_at) VALUES (?, '', ?, ?) ON DUPLICATE KEY UPDATE modifiers = VALUES(modifiers), updated_at = VALUES(updated_at)";
    }

    private String upsertModifiersSqlite() {
        String table = this.quote(this.config.sanitizeTable());
        return "INSERT INTO " + table + " (uuid, color, modifiers, updated_at) VALUES (?, '', ?, ?) ON CONFLICT(uuid) DO UPDATE SET modifiers = excluded.modifiers, updated_at = excluded.updated_at";
    }

    private void putCache(UUID playerId, String color) {
        if (this.cacheMillis <= 0L) {
            return;
        }
        this.cache.put(playerId, new CacheEntry(color, System.currentTimeMillis()));
        this.trimCacheIfLarge();
    }

    private void trimCacheIfLarge() {
        if (this.cache.size() <= 5000) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, CacheEntry>> iterator = this.cache.entrySet().iterator();
        while (iterator.hasNext() && this.cache.size() > 2500) {
            Map.Entry<UUID, CacheEntry> entry = iterator.next();
            if (!entry.getValue().isExpired(Math.max(this.cacheMillis, 60000L))) continue;
            iterator.remove();
        }
    }

    private String normalize(String color) {
        if (color == null) {
            return null;
        }
        String clean = color.trim();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() > 255 ? clean.substring(0, 255) : clean;
    }

    /** Widens legacy color VARCHAR(64) columns so gradient values fit (MySQL only; SQLite ignores lengths). */
    private void ensureWideColorColumn() {
        if (this.config.getType() != DatabaseType.MYSQL) return;
        String table = this.quote(this.config.sanitizeTable());
        Connection c = null;
        Statement s = null;
        try {
            c = this.pool.borrow();
            s = c.createStatement();
            try {
                s.execute("ALTER TABLE " + table + " MODIFY color VARCHAR(255) NOT NULL");
            } catch (SQLException ignored) {}
        } catch (Throwable ignored) {
        } finally {
            JdbcColorStorage.closeQuietly(s);
            this.release(c);
        }
    }

    private StorageException storageError(String action, UUID playerId, SQLException ex) {
        this.logger.log(Level.WARNING, "Could not " + action + " the color of player " + playerId + " in " + (Object)((Object)this.config.getType()) + ": " + ex.getMessage());
        return new StorageException("Database error while trying to " + action + " the color", ex);
    }

    private void release(Connection connection) {
        SimpleConnectionPool current = this.pool;
        if (current != null && connection != null) {
            current.giveBack(connection);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static final class CacheEntry {
        private final String color;
        private final long timestamp;

        private CacheEntry(String color, long timestamp) {
            this.color = color;
            this.timestamp = timestamp;
        }

        private boolean isExpired(long ttlMillis) {
            return ttlMillis <= 0L || System.currentTimeMillis() - this.timestamp >= ttlMillis;
        }
    }
}

