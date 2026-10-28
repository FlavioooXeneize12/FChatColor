package io.github.fchatcolor.api.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Task runner abstraction for classic Bukkit and
 * Folia/Canvas (where there is no single main thread).
 *
 * <p>En servidores Folia está prohibido usar {@code Bukkit.getScheduler()}
 * fuera del hilo correcto; por eso el plugin elige la implementación en
 * runtime: {@code LegacySchedulerAdapter} (Bukkit) o
 * {@code FoliaSchedulerAdapter} (region schedulers).</p>
 */
public interface SchedulerAdapter {

    /** Is this Folia region scheduler based? */
    boolean isFoliaBased();

    /** Nombre corto para logs ("bukkit" / "folia"). */
    String name();

    /** Runs the task async. */
    void runAsync(Runnable task);

    /** Runs the task sync (on Folia that's the global thread). */
    void runSync(Runnable task);

    /** Runs the task on the global region (cross-platform main thread). */
    void runGlobal(Runnable task);

    /**
     * Runs the task on the entity's region thread.
     *
     * @param entity entidad (p. ej. un jugador); si la implementación no lo soporta,
     *               se delega en {@link #runGlobal(Runnable)}
     * @param task   tarea a ejecutar
     */
    void runEntity(Entity entity, Runnable task);

    /**
     * Runs the task on the location's region thread.
     *
     * @param location ubicación objetivo
     * @param task     tarea a ejecutar
     */
    void runAtLocation(Location location, Runnable task);

    /** Cancela todas las tareas pendientes del plugin. */
    void shutdown();
}
