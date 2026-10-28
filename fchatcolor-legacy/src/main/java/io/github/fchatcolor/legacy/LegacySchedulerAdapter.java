package io.github.fchatcolor.legacy;

import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Adaptador de tareas basado en {@code BukkitScheduler}.
 *
 * <p>Válido en todos los servidores clásicos (1.8 → 1.21.x sin Folia), donde
 * existe un único hilo principal. En Folia/Canvas se usa
 * {@code FoliaSchedulerAdapter} en su lugar.</p>
 */
public final class LegacySchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;

    public LegacySchedulerAdapter(PluginContext context) {
        this.plugin = context.getPlugin();
    }

    @Override
    public boolean isFoliaBased() {
        return false;
    }

    @Override
    public String name() {
        return "bukkit";
    }

    @Override
    public void runAsync(Runnable task) {
        if (task == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
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
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runEntity(Entity entity, Runnable task) {
        // Bukkit clasico no has scheduler por entidad: usamos el hilo principal
        runGlobal(task);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        // Sin regiones en Bukkit clasico: usamos el hilo principal
        runGlobal(task);
    }

    @Override
    public void shutdown() {
        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // El servidor ya se está apagando
        }
    }
}
