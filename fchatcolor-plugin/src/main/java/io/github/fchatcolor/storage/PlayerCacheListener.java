/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package io.github.fchatcolor.storage;

import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.storage.JdbcColorStorage;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerCacheListener
implements Listener {
    private final ColorStorage storage;
    private final SchedulerAdapter scheduler;

    public PlayerCacheListener(ColorStorage storage, SchedulerAdapter scheduler) {
        this.storage = storage;
        this.scheduler = scheduler;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onJoin(PlayerJoinEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        Runnable task = new Runnable(){
            @Override
            public void run() {
                try {
                    storage.getSelectedColor(id);
                    try { storage.getSelectedModifiers(id); } catch (Throwable ignored2) {}
                }
                catch (Throwable throwable) {
                }
            }
        };
        if (this.scheduler != null) {
            this.scheduler.runAsync(task);
        } else {
            task.run();
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onQuit(PlayerQuitEvent event) {
        try {
            if (this.storage instanceof JdbcColorStorage) {
                ((JdbcColorStorage)this.storage).evict(event.getPlayer().getUniqueId());
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

