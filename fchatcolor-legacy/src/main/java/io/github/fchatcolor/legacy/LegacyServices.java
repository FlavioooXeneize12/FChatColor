package io.github.fchatcolor.legacy;

import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import org.bukkit.event.Listener;

/**
 * Punto de entrada del módulo legacy.
 *
 * <p>El módulo principal NUNCA referencia estas clases directamente: las
 * invoca via reflection ({@code Class.forName(...)}) para poder cargar este
 * módulo únicamente when corresponde. Esto es lo que permite tener un solo
 * jar que funcione igual en 1.8 que en 1.21.x.</p>
 */
public final class LegacyServices {

    private LegacyServices() {
    }

    /** Createstes el servicio de color basado en {@code ChatColor} de Bukkit. */
    public static ColorService createColorService(PluginContext context) {
        return new LegacyColorService(context);
    }

    /** Createstes el adaptador de scheduler basado en {@code BukkitScheduler}. */
    public static SchedulerAdapter createScheduler(PluginContext context) {
        return new LegacySchedulerAdapter(context);
    }

    /** Createstes el listener de {@code AsyncPlayerChatEvent} (servidores 1.8 en adelante). */
    public static Listener createChatListener(PluginContext context) {
        return new LegacyChatListener(context);
    }
}
