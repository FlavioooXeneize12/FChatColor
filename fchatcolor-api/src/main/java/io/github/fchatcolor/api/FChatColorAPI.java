package io.github.fchatcolor.api;

import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.server.ServerInfo;
import io.github.fchatcolor.api.storage.ColorStorage;

/**
 * Fachada pública del plugin: permite que otros plugins obtengan el servicio
 * de color, la info del servidor detectada y el almacenamiento.
 *
 * <pre>
 * FChatColorAPI api = FChatColorAPI.get();
 * String clean = api.getColorService().translate(mensaje, ColorMode.HEX);
 * </pre>
 */
public final class FChatColorAPI {

    private static volatile FChatColorAPI instance;

    private final ColorService colorService;
    private final ServerInfo serverInfo;
    private final ColorStorage storage;

    public FChatColorAPI(ColorService colorService, ServerInfo serverInfo, ColorStorage storage) {
        this.colorService = colorService;
        this.serverInfo = serverInfo;
        this.storage = storage;
    }

    /** Instalada por el plugin en {@code onEnable()}. {@code null} si no está cargado. */
    public static FChatColorAPI get() {
        return instance;
    }

    /** Solo para uso interno del plugin. */
    public static void install(FChatColorAPI api) {
        instance = api;
    }

    /** Solo para uso interno del plugin (se llama en {@code onDisable()}). */
    public static void uninstall() {
        instance = null;
    }

    public ColorService getColorService() {
        return colorService;
    }

    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    public ColorStorage getStorage() {
        return storage;
    }
}
