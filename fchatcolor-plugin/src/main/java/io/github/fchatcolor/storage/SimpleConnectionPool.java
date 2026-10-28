/*
 * Decompiled with CFR 0.152.
 */
package io.github.fchatcolor.storage;

import io.github.fchatcolor.api.storage.DatabaseConfig;
import io.github.fchatcolor.api.storage.DatabaseType;
import io.github.fchatcolor.api.storage.StorageException;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimpleConnectionPool {
    private static final int BORROW_TIMEOUT_SECONDS = 5;
    private final DatabaseConfig config;
    private final String url;
    private final Properties properties;
    private final LinkedBlockingQueue<Connection> idle = new LinkedBlockingQueue();
    private final AtomicInteger openConnections = new AtomicInteger();
    private volatile boolean closed;

    public SimpleConnectionPool(DatabaseConfig config, String dataFolderAbsolutePath) {
        this.config = config;
        this.url = config.buildUrl(dataFolderAbsolutePath);
        this.properties = new Properties();
        if (config.getType() == DatabaseType.MYSQL) {
            this.properties.setProperty("user", config.getUsername());
            this.properties.setProperty("password", config.getPassword());
            this.properties.setProperty("cachePrepStmts", "true");
            this.properties.setProperty("prepStmtCacheSize", "250");
            this.properties.setProperty("prepStmtCacheSqlLimit", "2048");
            this.properties.setProperty("useServerPrepStmts", "true");
            this.properties.setProperty("cacheResultSetMetadata", "true");
            this.properties.setProperty("rewriteBatchedStatements", "true");
        }
    }

    public void init() throws StorageException {
        File dbFile;
        File parent;
        String file;
        try {
            Class.forName(this.config.getDriverClass());
        }
        catch (ClassNotFoundException ex) {
            throw new StorageException("JDBC driver not found: " + this.config.getDriverClass() + ". Was the jar packaged correctly?", ex);
        }
        if (!(this.config.getType() != DatabaseType.SQLITE || (file = this.url.substring("jdbc:sqlite:".length())).startsWith(":memory:") || (parent = (dbFile = new File(file)).getParentFile()) == null || parent.exists() || parent.mkdirs())) {
            throw new StorageException("Could not create the database directory: " + parent);
        }
        Connection connection = null;
        try {
            connection = this.create();
        }
        catch (SQLException ex) {
            throw new StorageException("Could not connect to " + (Object)((Object)this.config.getType()) + " (" + this.url + "): " + ex.getMessage(), ex);
        }
        finally {
            if (connection != null) {
                this.closeQuietly(connection);
                this.openConnections.decrementAndGet();
            }
        }
    }

    public Connection borrow() throws SQLException {
        if (this.closed) {
            throw new SQLException("The connection pool is closed");
        }
        Connection connection = this.idle.poll();
        while (connection != null) {
            if (this.isValid(connection)) {
                return connection;
            }
            this.discard(connection);
            connection = this.idle.poll();
        }
        if (this.openConnections.incrementAndGet() <= this.config.getPoolSize()) {
            try {
                return this.create();
            }
            catch (SQLException ex) {
                this.openConnections.decrementAndGet();
                throw ex;
            }
        }
        this.openConnections.decrementAndGet();
        try {
            connection = this.idle.poll(5L, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a connection", ex);
        }
        if (connection == null) {
            throw new SQLException("Connection pool exhausted (" + this.config.getPoolSize() + " max) for " + this.url);
        }
        if (!this.isValid(connection)) {
            this.discard(connection);
            throw new SQLException("The connection returned to the pool is no longer valid");
        }
        return connection;
    }

    public void giveBack(Connection connection) {
        if (connection == null) {
            return;
        }
        if (this.closed) {
            this.closeQuietly(connection);
            this.openConnections.decrementAndGet();
            return;
        }
        try {
            if (connection.isClosed()) {
                this.closeQuietly(connection);
                this.openConnections.decrementAndGet();
                return;
            }
        }
        catch (SQLException ignored) {
            this.closeQuietly(connection);
            this.openConnections.decrementAndGet();
            return;
        }
        if (!this.idle.offer(connection)) {
            this.closeQuietly(connection);
            this.openConnections.decrementAndGet();
        }
    }

    public void close() {
        this.closed = true;
        Connection connection = this.idle.poll();
        while (connection != null) {
            this.closeQuietly(connection);
            this.openConnections.decrementAndGet();
            connection = this.idle.poll();
        }
    }

    public boolean isClosed() {
        return this.closed;
    }

    public String getUrl() {
        return this.url;
    }

    private Connection create() throws SQLException {
        Connection c = DriverManager.getConnection(this.url, this.properties);
        if (this.config.getType() == DatabaseType.SQLITE) {
            try {
                c.createStatement().execute("PRAGMA journal_mode=WAL");
                c.createStatement().execute("PRAGMA synchronous=NORMAL");
                c.createStatement().execute("PRAGMA temp_store=MEMORY");
                c.createStatement().execute("PRAGMA cache_size=-64000");
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return c;
    }

    private boolean isValid(Connection connection) {
        try {
            if (connection.isClosed()) {
                return false;
            }
            return connection.isValid(1);
        }
        catch (Throwable t) {
            return false;
        }
    }

    private void discard(Connection connection) {
        this.closeQuietly(connection);
        this.openConnections.decrementAndGet();
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

