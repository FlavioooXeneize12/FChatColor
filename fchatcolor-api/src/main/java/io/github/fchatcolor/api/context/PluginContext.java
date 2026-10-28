package io.github.fchatcolor.api.context;

import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.server.ServerInfo;
import io.github.fchatcolor.api.settings.PluginSettings;
import io.github.fchatcolor.api.storage.ColorStorage;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Shared context between the main module and the color implementations
 * legacy/modern. Evita que legacy dependa del plugin principal (y viceversa),
 * keeps modules decoupled.
 */
public interface PluginContext {

    /** Instancia del plugin (para schedulers, comandos y permisos). */
    Plugin getPlugin();

    /** Server info discovered via reflection. */
    ServerInfo getServerInfo();

    /** Active color service (legacy or modern). */
    ColorService getColorService();

    /** Adaptador de scheduler activo (Bukkit o Folia/Canvas). */
    SchedulerAdapter getScheduler();

    /** Storage for player chat colors. */
    ColorStorage getStorage();

    /** Ajustes del plugin. */
    PluginSettings getSettings();

    /** Logger del plugin. */
    Logger getLogger();
}
