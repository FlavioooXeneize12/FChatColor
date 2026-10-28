package io.github.fchatcolor.api.storage;

import java.util.Locale;

/**
 * Supported database engines.
 */
public enum DatabaseType {

    /** MySQL/MariaDB via JDBC (shaded driver). */
    MYSQL,

    /** SQLite via JDBC (local file, no server). */
    SQLITE;

    /**
     * Interpreta el valor de la config ({@code "mysql"}, {@code "mariadb"},
     * {@code "sqlite"}, {@code "sql"}...).
     *
     * @param texto        valor leído de la config
     * @param predeterminado valor si no se reconoce
     */
    public static DatabaseType parse(String texto, DatabaseType predeterminado) {
        if (texto == null) {
            return predeterminado;
        }
        String clean = texto.trim().toLowerCase(Locale.ROOT);
        if (clean.equals("mysql") || clean.equals("mariadb") || clean.equals("maria")) {
            return MYSQL;
        }
        if (clean.equals("sqlite") || clean.equals("sql") || clean.equals("local")) {
            return SQLITE;
        }
        return predeterminado;
    }
}
