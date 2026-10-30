package io.github.fchatcolor.api.storage;

import java.util.UUID;

/**
 * Almacenamiento del color elegido por cada jugador.
 *
 * <p>Implementación concreta: {@code JdbcColorStorage} (MySQL/SQLite) en el
 * módulo del plugin. Todas las operaciones deben ser thread-safe porque el
 * chat asíncrono consulta este servicio desde distintos hilos.</p>
 */
public interface ColorStorage {

    /** Opens the connection and creates the table if needed. */
    void init() throws StorageException;

    /** Closes all idle connections. */
    void close();

    DatabaseType getType();

    /** {@code true} si hay conexión disponible. */
    boolean isConnected();

    /**
     * Devuelve el color guardado para el jugador.
     *
     * @param playerId UUID del jugador
     * @return color as {@code #RRGGBB} o código legacy (p. ej. {@code "a"}),
     *         o {@code null} if the player hasn't picked one
     */
    String getSelectedColor(UUID playerId);

    /** Saves (or updates) the player's chosen color. */
    void setSelectedColor(UUID playerId, String color);

    /** Removes the player's stored color. */
    void clearSelectedColor(UUID playerId);

    /** Returns the stored modifiers for the player (e.g. "bold,italic" or "&l,&o") or null. */
    String getSelectedModifiers(UUID playerId);

    /** Saves the player's modifiers. */
    void setSelectedModifiers(UUID playerId, String modifiers);

    /** Removes the player's stored modifiers. */
    void clearSelectedModifiers(UUID playerId);
}
