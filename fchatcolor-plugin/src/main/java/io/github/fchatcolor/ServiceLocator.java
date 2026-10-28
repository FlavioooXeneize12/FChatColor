/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.Listener
 */
package io.github.fchatcolor;

import io.github.fchatcolor.FallbackColorService;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.server.ServerInfo;
import io.github.fchatcolor.api.util.Reflection;
import io.github.fchatcolor.scheduler.NoopScheduler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.Listener;

public final class ServiceLocator {
    public static final String LEGACY_SERVICES = "io.github.fchatcolor.legacy.LegacyServices";
    public static final String MODERN_SERVICES = "io.github.fchatcolor.modern.ModernServices";

    private ServiceLocator() {
    }

    public static ColorService createColorService(PluginContext context) {
        ColorService legacy;
        Logger logger = context.getLogger();
        ServerInfo info = context.getServerInfo();
        if (info.isModern()) {
            ColorService modern = ServiceLocator.invokeColorService(MODERN_SERVICES, context, logger);
            if (modern != null) {
                return modern;
            }
            logger.warning("Could not load the modern color service; falling back to the legacy one.");
        }
        if ((legacy = ServiceLocator.invokeColorService(LEGACY_SERVICES, context, logger)) != null) {
            return legacy;
        }
        logger.log(Level.SEVERE, "Could not load any color service; using the basic fallback.");
        return new FallbackColorService(context);
    }

    public static SchedulerAdapter createScheduler(PluginContext context) {
        SchedulerAdapter bukkit;
        Logger logger = context.getLogger();
        ServerInfo info = context.getServerInfo();
        if (info.hasFoliaSchedulers()) {
            SchedulerAdapter folia = ServiceLocator.invokeScheduler(MODERN_SERVICES, context, logger);
            if (folia != null) {
                return folia;
            }
            logger.warning("Could not load the Folia scheduler; falling back to the Bukkit one.");
        }
        if ((bukkit = ServiceLocator.invokeScheduler(LEGACY_SERVICES, context, logger)) != null) {
            return bukkit;
        }
        return new NoopScheduler(context);
    }

    public static Listener createChatListener(PluginContext context) {
        Listener legacy;
        Logger logger = context.getLogger();
        ServerInfo info = context.getServerInfo();
        if (info.hasModernChatEvent()) {
            Listener modern = ServiceLocator.invokeChatListener(MODERN_SERVICES, context, logger);
            if (modern != null) {
                return modern;
            }
            logger.warning("Could not load the modern chat listener; trying the legacy one.");
        }
        if ((legacy = ServiceLocator.invokeChatListener(LEGACY_SERVICES, context, logger)) != null) {
            return legacy;
        }
        logger.log(Level.SEVERE, "Could not load any chat listener.");
        return null;
    }

    private static ColorService invokeColorService(String className, PluginContext context, Logger logger) {
        try {
            Class<?> services = Class.forName(className);
            Object service = services.getMethod("createColorService", PluginContext.class).invoke(null, context);
            return (ColorService)service;
        }
        catch (Throwable t) {
            logger.log(Level.WARNING, "Could not create the color service of " + className + " -> " + ServiceLocator.rootCause(t), t);
            return null;
        }
    }

    private static SchedulerAdapter invokeScheduler(String className, PluginContext context, Logger logger) {
        try {
            Class<?> services = Class.forName(className);
            Object scheduler = services.getMethod("createScheduler", PluginContext.class).invoke(null, context);
            return (SchedulerAdapter)scheduler;
        }
        catch (Throwable t) {
            logger.log(Level.WARNING, "Could not create the scheduler of " + className + " -> " + ServiceLocator.rootCause(t), t);
            return null;
        }
    }

    private static Listener invokeChatListener(String className, PluginContext context, Logger logger) {
        try {
            Class<?> services = Class.forName(className);
            Object listener = services.getMethod("createChatListener", PluginContext.class).invoke(null, context);
            return (Listener)listener;
        }
        catch (Throwable t) {
            logger.log(Level.WARNING, "Could not create the chat listener of " + className + " -> " + ServiceLocator.rootCause(t), t);
            return null;
        }
    }

    private static String rootCause(Throwable t) {
        Throwable current;
        for (current = t; current.getCause() != null && current.getCause() != current; current = current.getCause()) {
        }
        return current.getClass().getName() + (current.getMessage() == null ? "" : ": " + current.getMessage());
    }

    public static boolean isModuleAvailable(String servicesClassName) {
        return Reflection.classExists(servicesClassName);
    }
}

