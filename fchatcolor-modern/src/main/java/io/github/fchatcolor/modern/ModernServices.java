package io.github.fchatcolor.modern;

import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import org.bukkit.event.Listener;

/**
 * Punto de entrada del módulo modern.
 *
 * <p>El módulo principal lo invoca via reflection únicamente when
 * {@code ServerInfo} confirma que Adventure/MiniMessage/Folia-schedulers
 * están presentes. Si la carga falla (p. ej. servidor 1.8), el llamador
 * cae automáticamente al módulo legacy.</p>
 */
public final class ModernServices {

    private ModernServices() {
    }

    /** Createstes el servicio de color basado en Adventure/MiniMessage. */
    public static ColorService createColorService(PluginContext context) {
        return new ModernColorService(context);
    }

    /**
     * Createstes el adaptador de scheduler Folia/Paper.
     *
     * <p>En Paper "normal" los region schedulers delegan en el hilo principal,
     * por lo que es seguro en cualquier servidor con esta API disponible.</p>
     */
    public static SchedulerAdapter createScheduler(PluginContext context) {
        return new FoliaSchedulerAdapter(context);
    }

    /** Createstes el listener de {@code AsyncChatEvent} de Paper. */
    public static Listener createChatListener(PluginContext context) {
        return new ModernChatListener(context);
    }
}
