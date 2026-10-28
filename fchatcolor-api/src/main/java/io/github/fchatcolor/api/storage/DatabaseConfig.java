package io.github.fchatcolor.api.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Database connection config, independent from
 * concrete implementation (MySQL or SQLite).
 */
public final class DatabaseConfig {

    private final DatabaseType type;

    // MySQL / MariaDB
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // Comunes
    private final String table;
    private final int poolSize;

    // SQLite
    private final String sqliteFile;

    private final Map<String, String> properties;

    private DatabaseConfig(Builder builder) {
        this.type = builder.type;
        this.host = builder.host;
        this.port = builder.port;
        this.database = builder.database;
        this.username = builder.username;
        this.password = builder.password;
        this.table = builder.table;
        this.poolSize = builder.poolSize;
        this.sqliteFile = builder.sqliteFile;
        this.properties = Collections.unmodifiableMap(new HashMap<String, String>(builder.properties));
    }

    public DatabaseType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /** Nombre de la tabla (se sanea antes de usarse en SQL). */
    public String getTable() {
        return table;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /** Ruta del archivo .db de SQLite (relativa a la carpeta del plugin). */
    public String getSqliteFile() {
        return sqliteFile;
    }

    /** Propiedades JDBC extra (se concatenan a la URL). */
    public Map<String, String> getProperties() {
        return properties;
    }

    /** JDBC driver class for the current type. */
    public String getDriverClass() {
        return type == DatabaseType.MYSQL ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC";
    }

    /** Construye la URL JDBC correspondiente al tipo configurado. */
    public String buildUrl(String dataFolderAbsolutePath) {
        if (type == DatabaseType.MYSQL) {
            StringBuilder url = new StringBuilder("jdbc:mysql://")
                    .append(host).append(':').append(port)
                    .append('/').append(database);
            StringBuilder params = new StringBuilder(
                    "useSSL=false&autoReconnect=true&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC");
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                params.append('&').append(entry.getKey()).append('=').append(entry.getValue());
            }
            return url.append('?').append(params).toString();
        }
        String file = sqliteFile;
        if (!file.contains("/") && !file.contains("\\") && !file.startsWith(":memory:")) {
            file = dataFolderAbsolutePath + java.io.File.separator + file;
        }
        return "jdbc:sqlite:" + file;
    }

    /** Sanitiza el nombre de tabla para poder concatenarlo en SQL (solo [a-zA-Z0-9_]). */
    public String sanitizeTable() {
        String clean = table == null ? "fchatcolor_colors" : table.trim().replaceAll("[^a-zA-Z0-9_]", "");
        return clean.isEmpty() ? "fchatcolor_colors" : clean;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DatabaseType type = DatabaseType.SQLITE;
        private String host = "localhost";
        private int port = 3306;
        private String database = "fchatcolor";
        private String username = "root";
        private String password = "";
        private String table = "fchatcolor_colors";
        private int poolSize = 10;
        private String sqliteFile = "data.db";
        private final Map<String, String> properties = new HashMap<String, String>();

        public Builder type(DatabaseType type) {
            this.type = type == null ? DatabaseType.SQLITE : type;
            return this;
        }

        public Builder host(String host) {
            if (host != null && !host.trim().isEmpty()) {
                this.host = host.trim();
            }
            return this;
        }

        public Builder port(int port) {
            if (port > 0 && port <= 65535) {
                this.port = port;
            }
            return this;
        }

        public Builder database(String database) {
            if (database != null && !database.trim().isEmpty()) {
                this.database = database.trim();
            }
            return this;
        }

        public Builder username(String username) {
            this.username = username == null ? "" : username;
            return this;
        }

        public Builder password(String password) {
            this.password = password == null ? "" : password;
            return this;
        }

        public Builder table(String table) {
            if (table != null && !table.trim().isEmpty()) {
                this.table = table.trim();
            }
            return this;
        }

        public Builder poolSize(int poolSize) {
            if (poolSize > 0) {
                this.poolSize = Math.min(poolSize, 32);
            }
            return this;
        }

        public Builder sqliteFile(String sqliteFile) {
            if (sqliteFile != null && !sqliteFile.trim().isEmpty()) {
                this.sqliteFile = sqliteFile.trim();
            }
            return this;
        }

        public Builder property(String key, String value) {
            if (key != null && value != null) {
                this.properties.put(key.trim(), value.trim());
            }
            return this;
        }

        public DatabaseConfig build() {
            return new DatabaseConfig(this);
        }
    }
}
