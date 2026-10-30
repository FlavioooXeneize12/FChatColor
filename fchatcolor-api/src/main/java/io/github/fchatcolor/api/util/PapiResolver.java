package io.github.fchatcolor.api.util;

import org.bukkit.OfflinePlayer;
import java.lang.reflect.Method;

/**
 * Resuelve placeholders de PlaceholderAPI sobre cadenas literales.
 * Trabaja con {@code String} porque PlaceholderAPI opera sobre texto
 * literal, no sobre Componentes.
 *
 * <p>Usa reflection para no requerir dependencia dura en compilación.
 * Si PlaceholderAPI no está instalado, devuelve el texto sin modificar.</p>
 */
public final class PapiResolver {

    private static volatile Method setPlaceholders;
    private static volatile boolean triedInit;

    private PapiResolver() {}

    private static synchronized void init() {
        if (triedInit) return;
        triedInit = true;
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholders = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (Throwable ignored) {
            setPlaceholders = null;
        }
    }

    public static boolean isAvailable() {
        if (!triedInit) init();
        return setPlaceholders != null;
    }

    /**
     * Resuelve todos los placeholders PAPI en el texto literal.
     * Si PAPI no está disponible o el texto es null, lo devuelve tal cual.
     */
    public static String resolve(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) return text;
        if (!isAvailable()) return text;
        try {
            return (String) setPlaceholders.invoke(null, player, text);
        } catch (Throwable t) {
            return text;
        }
    }

    public static String resolve(org.bukkit.entity.Player player, String text) {
        return resolve((OfflinePlayer) player, text);
    }
}
