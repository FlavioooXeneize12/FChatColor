/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.plugin.Plugin
 */
package io.github.fchatcolor.context;

import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.server.ServerInfo;
import io.github.fchatcolor.api.settings.PluginSettings;
import io.github.fchatcolor.api.storage.ColorStorage;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class PluginContextImpl
implements PluginContext {
    private final Plugin plugin;
    private final ServerInfo serverInfo;
    private final PluginSettings settings;
    private final ColorStorage storage;
    private volatile ColorService colorService;
    private volatile SchedulerAdapter scheduler;

    public PluginContextImpl(Plugin plugin, ServerInfo serverInfo, PluginSettings settings, ColorStorage storage) {
        this.plugin = plugin;
        this.serverInfo = serverInfo;
        this.settings = settings;
        this.storage = storage;
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    @Override
    public ColorService getColorService() {
        return this.colorService;
    }

    @Override
    public SchedulerAdapter getScheduler() {
        return this.scheduler;
    }

    @Override
    public ColorStorage getStorage() {
        return this.storage;
    }

    @Override
    public PluginSettings getSettings() {
        return this.settings;
    }

    @Override
    public Logger getLogger() {
        return this.plugin.getLogger();
    }

    public void setColorService(ColorService colorService) {
        this.colorService = colorService;
    }

    public void setScheduler(SchedulerAdapter scheduler) {
        this.scheduler = scheduler;
    }
}

