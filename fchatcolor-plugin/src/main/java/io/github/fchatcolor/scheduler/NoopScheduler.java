/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 */
package io.github.fchatcolor.scheduler;

import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class NoopScheduler
implements SchedulerAdapter {
    private final Logger logger;
    private boolean warned;

    public NoopScheduler(PluginContext context) {
        this.logger = context.getLogger();
    }

    @Override
    public boolean isFoliaBased() {
        return false;
    }

    @Override
    public String name() {
        return "directo";
    }

    @Override
    public void runAsync(Runnable task) {
        this.runNow(task);
    }

    @Override
    public void runSync(Runnable task) {
        this.runNow(task);
    }

    @Override
    public void runGlobal(Runnable task) {
        this.runNow(task);
    }

    @Override
    public void runEntity(Entity entity, Runnable task) {
        this.runNow(task);
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        this.runNow(task);
    }

    @Override
    public void shutdown() {
    }

    private void runNow(Runnable task) {
        block5: {
            if (task == null) {
                return;
            }
            if (!this.warned) {
                this.warned = true;
                if (this.logger != null) {
                    this.logger.warning("No scheduler available: tasks will run on the current thread.");
                }
            }
            try {
                task.run();
            }
            catch (Throwable t) {
                if (this.logger == null) break block5;
                this.logger.warning("Error in a task executed without a scheduler: " + t);
            }
        }
    }
}

