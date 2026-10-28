package io.github.fchatcolor.modern;

import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Adaptador de tareas para Folia y forks compatibles (Canvas).
 *
 * <p>Folia NO has hilo principal: cada región has el suyo y hay que usar
 * los schedulers regionizados. En Paper "normal" estas APIs también existen y
 * delegan en el hilo principal, así que el adaptador es válido en cualquier
 * servidor con la API de Folia presente.</p>
 *
 * <p>Clase cargada solo when la reflection confirma que estas APIs existen;
 * si algo falla, cada método degrada a la siguiente estrategia segura.</p>
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final Logger logger;
    private boolean warned;

    public FoliaSchedulerAdapter(PluginContext context) {
        this.plugin = context.getPlugin();
        this.logger = context.getLogger();
    }

    @Override
    public boolean isFoliaBased() {
        return true;
    }

    @Override
    public String name() {
        return "folia";
    }

    @Override
    public void runAsync(Runnable task) {
        if (task == null) {
            return;
        }
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } catch (Throwable t) {
            warnOnce(t);
            task.run();
        }
    }

    @Override
    public void runSync(Runnable task) {
        runGlobal(task);
    }

    @Override
    public void runGlobal(Runnable task) {
        if (task == null) {
            return;
        }
        try {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } catch (Throwable t) {
            warnOnce(t);
            task.run();
        }
    }

    @Override
    public void runEntity(Entity entity, Runnable task) {
        if (task == null) {
            return;
        }
        if (entity == null) {
            runGlobal(task);
            return;
        }
        try {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), () -> {
                // La entidad desapareció (retirada): se descarta la tarea, nada crítico
            });
        } catch (Throwable t) {
            warnOnce(t);
            runGlobal(task);
        }
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        if (task == null) {
            return;
        }
        if (location == null) {
            runGlobal(task);
            return;
        }
        try {
            Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
        } catch (Throwable t) {
            warnOnce(t);
            runGlobal(task);
        }
    }

    @Override
    public void shutdown() {
        try {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // El servidor ya se está apagando
        }
        try {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // Igualmente irrelevante durante el apagado
        }
    }

    private void warnOnce(Throwable t) {
        if (warned) {
            return;
        }
        warned = true;
        if (logger != null) {
            logger.warning("Could not use the Folia scheduler, falling back: " + t);
        }
    }
}
