/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.Server
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package io.github.fchatcolor;

import io.github.fchatcolor.ServiceLocator;
import io.github.fchatcolor.api.FChatColorAPI;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.server.ServerDetector;
import io.github.fchatcolor.api.server.ServerInfo;
import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.colors.ColorCatalog;
import io.github.fchatcolor.command.FChatColorCommand;
import io.github.fchatcolor.context.PluginContextImpl;
import io.github.fchatcolor.menu.MenuConfig;
import io.github.fchatcolor.menu.MenuListener;
import io.github.fchatcolor.messages.Messages;
import io.github.fchatcolor.settings.PluginConfig;
import io.github.fchatcolor.storage.PlayerCacheListener;
import io.github.fchatcolor.storage.StorageFactory;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class FChatColorPlugin
extends JavaPlugin {
    private ServerInfo serverInfo;
    private PluginConfig settings;
    private Messages messages;
    private ColorCatalog colors;
    private MenuConfig menu;
    private ColorStorage storage;
    private PluginContextImpl context;
    private Listener chatListener;

    public void onEnable() {
        this.saveDefaultConfig();
        this.settings = new PluginConfig(this);
        this.messages = new Messages(this);
        this.colors = new ColorCatalog(new File(this.getDataFolder(), "colors.yml"), this.getLogger());
        this.colors.reload();
        this.colors.registerPermissions(this.getServer().getPluginManager());
        this.menu = new MenuConfig(new File(this.getDataFolder(), "menu.yml"), this.getLogger());
        this.menu.reload();
        this.serverInfo = ServerDetector.detect();
        this.storage = StorageFactory.create(this.settings.getDatabaseConfig(), this.getDataFolder(), this.getLogger(), this.settings.getCacheMillis());
        try {
            this.storage.init();
        }
        catch (Throwable t) {
            this.getLogger().log(Level.SEVERE, "Could not initialize the database (" + (Object)((Object)this.settings.getDatabaseConfig().getType()) + "): " + t.getMessage(), t);
            this.getServer().getPluginManager().disablePlugin((Plugin)this);
            return;
        }
        this.context = new PluginContextImpl((Plugin)this, this.serverInfo, this.settings, this.storage);
        this.context.setColorService(ServiceLocator.createColorService(this.context));
        this.context.setScheduler(ServiceLocator.createScheduler(this.context));
        this.chatListener = ServiceLocator.createChatListener(this.context);
        if (this.chatListener != null) {
            this.getServer().getPluginManager().registerEvents(this.chatListener, (Plugin)this);
        } else {
            this.getLogger().severe("No chat listener was registered: chat translation will not work.");
        }
        this.getServer().getPluginManager().registerEvents((Listener)new MenuListener(), (Plugin)this);
        try {
            this.getServer().getPluginManager().registerEvents((Listener)new PlayerCacheListener(this.storage, this.context.getScheduler()), (Plugin)this);
        }
        catch (Throwable t) {
            this.getLogger().log(Level.FINE, "Could not register cache listener", t);
        }
        PluginCommand command = null;
        try {
            command = this.getCommand("fchatcolor");
        }
        catch (UnsupportedOperationException ignored) {
            command = null;
        }
        catch (Throwable ignored) {
            command = null;
        }
        if (command == null) {
            command = this.registerCommandViaCode();
        }
        if (command != null) {
            FChatColorCommand executor = new FChatColorCommand(this);
            command.setExecutor((CommandExecutor)executor);
            command.setTabCompleter((TabCompleter)executor);
        } else {
            this.getLogger().warning("Command 'fchatcolor' could not be registered (neither via yml nor programmatically).");
        }
        FChatColorAPI.install(new FChatColorAPI(this.context.getColorService(), this.serverInfo, this.storage));
        this.registerPlaceholderHook();
        this.logStartupBanner();
    }

    private void logStartupBanner() {
        String[] banner = new String[]{"&a\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2557  \u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2557      \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2557 \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557", "&a\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d\u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u255a\u2550\u2550\u2588\u2588\u2554\u2550\u2550\u255d\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d\u2588\u2588\u2554\u2550\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2551     \u2588\u2588\u2554\u2550\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u2588\u2588\u2554\u2550\u2550\u2550\u2550\u255d", "&a\u2588\u2588\u2588\u2588\u2588\u2557  \u2588\u2588\u2551     \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551   \u2588\u2588\u2551   \u2588\u2588\u2551     \u2588\u2588\u2551   \u2588\u2588\u2551\u2588\u2588\u2551     \u2588\u2588\u2551   \u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255d\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557", "&a\u2588\u2588\u2554\u2550\u2550\u255d  \u2588\u2588\u2551     \u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2551\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2551   \u2588\u2588\u2551   \u2588\u2588\u2551     \u2588\u2588\u2551   \u2588\u2588\u2551\u2588\u2588\u2551     \u2588\u2588\u2551   \u2588\u2588\u2551\u2588\u2588\u2554\u2550\u2550\u2588\u2588\u2557\u255a\u2550\u2550\u2550\u2550\u2588\u2588\u2551", "&a\u2588\u2588\u2551     \u255a\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2551  \u2588\u2588\u2551   \u2588\u2588\u2551   \u255a\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u255a\u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255d\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2557\u255a\u2588\u2588\u2588\u2588\u2588\u2588\u2554\u255d\u2588\u2588\u2551  \u2588\u2588\u2551\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2551", "&a\u255a\u2550\u255d      \u255a\u2550\u2550\u2550\u2550\u2550\u255d\u255a\u2550\u255d  \u255a\u2550\u255d\u255a\u2550\u255d  \u255a\u2550\u255d   \u255a\u2550\u255d    \u255a\u2550\u2550\u2550\u2550\u2550\u255d \u255a\u2550\u2550\u2550\u2550\u2550\u255d \u255a\u2550\u2550\u2550\u2550\u2550\u2550\u255d \u255a\u2550\u2550\u2550\u2550\u2550\u255d \u255a\u2550\u255d  \u255a\u2550\u255d\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u255d"};
        ConsoleCommandSender console = this.getServer().getConsoleSender();
        for (String line : banner) {
            console.sendMessage(FChatColorPlugin.translateColor(line));
        }
        String server = this.serverInfo != null ? this.serverInfo.getPlatform().name() : "UNKNOWN";
        String version = this.serverInfo != null && this.serverInfo.getVersion() != null ? this.serverInfo.getVersion().toNormalizedString() : this.getServer().getBukkitVersion();
        String folia = this.serverInfo != null && this.serverInfo.hasFoliaSchedulers() ? "&a\u2714" : "&c\u274c";
        String db = this.storage != null ? this.storage.getType().name() : this.settings.getDatabaseConfig().getType().name();
        String chat = this.chatListener != null ? "&a\u2714" : "&c\u274c";
        int loaded = this.colors != null ? this.colors.entries().size() : 0;
        console.sendMessage(FChatColorPlugin.translateColor("&fServer Detected: &a" + server));
        console.sendMessage(FChatColorPlugin.translateColor("&fServer Version: &e" + version));
        console.sendMessage(FChatColorPlugin.translateColor("&fFolia Support: " + folia));
        console.sendMessage(FChatColorPlugin.translateColor("&fDatabase: &f" + db));
        console.sendMessage(FChatColorPlugin.translateColor("&fChat Listener: " + chat));
        console.sendMessage(FChatColorPlugin.translateColor("&fColors loaded: &b" + loaded));
    }

    private static String translateColor(String text) {
        return ChatColor.translateAlternateColorCodes((char)'&', (String)text);
    }

    private PluginCommand registerCommandViaCode() {
        try {
            Class<?> lifecycleEventsClass = Class.forName("io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents");
            Object commandsEvent = lifecycleEventsClass.getField("COMMANDS").get(null);
            Object lifecycleManager = ((Object)((Object)this)).getClass().getMethod("getLifecycleManager", new Class[0]).invoke((Object)this, new Object[0]);
            Method register = null;
            for (Method m : lifecycleManager.getClass().getMethods()) {
                if (!m.getName().equals("registerEventHandler") || m.getParameterTypes().length != 2) continue;
                register = m;
                break;
            }
            if (register != null) {
                Class<?> consumerClass = Class.forName("java.util.function.Consumer");
                Object handler = Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class[]{consumerClass}, new InvocationHandler(){

                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (args != null && args.length == 1 && args[0] != null) {
                            try {
                                Object object = args[0].getClass().getMethod("registrar", new Class[0]).invoke(args[0], new Object[0]);
                            }
                            catch (Throwable throwable) {
                                // empty catch block
                            }
                        }
                        return null;
                    }
                });
                register.invoke(lifecycleManager, commandsEvent, handler);
            }
        }
        catch (Throwable lifecycleEventsClass) {
            // empty catch block
        }
        try {
            Constructor ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);
            PluginCommand cmd = (PluginCommand)ctor.newInstance(new Object[]{"fchatcolor", this});
            cmd.setAliases(Arrays.asList("fcolor", "fc", "chatcolor"));
            cmd.setDescription("Manage your chat color.");
            cmd.setUsage("/fchatcolor <set|clear|reload|help>");
            cmd.setPermission("fchatcolor.command.color");
            cmd.setPermissionMessage("\u00a7cYou don't have permission.");
            Server server = this.getServer();
            Method getCommandMap = server.getClass().getMethod("getCommandMap", new Class[0]);
            Object commandMap = getCommandMap.invoke((Object)server, new Object[0]);
            Method register = commandMap.getClass().getMethod("register", String.class, Command.class);
            register.invoke(commandMap, this.getDescription().getName(), cmd);
            return cmd;
        }
        catch (Throwable t) {
            this.getLogger().log(Level.FINE, "Programmatic command registration failed", t);
            return null;
        }
    }

    private void registerPlaceholderHook() {
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            this.getLogger().info("PlaceholderAPI not installed: %fchatcolor_* placeholders are unavailable.");
            return;
        }
        try {
            Class<?> expansion = Class.forName("io.github.fchatcolor.papi.FChatColorExpansion");
            Object registered = expansion.getMethod("register", FChatColorPlugin.class).invoke(null, new Object[]{this});
            if (Boolean.TRUE.equals(registered)) {
                this.getLogger().info("PlaceholderAPI detected: %fchatcolor_color%, %fchatcolor_color_legacy%, %fchatcolor_color_hex%, %fchatcolor_color_name%, %fchatcolor_color_preview% and %fchatcolor_has_color% are active.");
            } else {
                this.getLogger().warning("PlaceholderAPI is present but the expansion could not be registered.");
            }
        }
        catch (Throwable t) {
            this.getLogger().log(Level.WARNING, "Could not register the PlaceholderAPI expansion", t);
        }
    }

    private String describeHandledEvent(Listener listener) {
        try {
            for (Method method : listener.getClass().getMethods()) {
                if (method.getAnnotation(EventHandler.class) == null || method.getParameterTypes().length != 1) continue;
                return method.getParameterTypes()[0].getName();
            }
            return "no @EventHandler methods";
        }
        catch (Throwable t) {
            return "unknown (" + t + ")";
        }
    }

    public void onDisable() {
        if (this.context != null && this.context.getScheduler() != null) {
            try {
                this.context.getScheduler().shutdown();
            }
            catch (Throwable t) {
                this.getLogger().log(Level.FINE, "Error while shutting down the scheduler", t);
            }
        }
        if (this.storage != null) {
            try {
                this.storage.close();
            }
            catch (Throwable t) {
                this.getLogger().log(Level.FINE, "Error while closing the database", t);
            }
        }
        FChatColorAPI.uninstall();
    }

    public void reloadPlugin() {
        this.reloadConfig();
        this.settings.reload();
        this.messages.reload();
        this.colors.reload();
        this.colors.registerPermissions(this.getServer().getPluginManager());
        this.menu.reload();
        ColorService reloaded = ServiceLocator.createColorService(this.context);
        this.context.setColorService(reloaded);
        FChatColorAPI.install(new FChatColorAPI(reloaded, this.serverInfo, this.storage));
        this.getLogger().info("Configuration reloaded (color service: " + reloaded.name() + ", colors: " + this.colors.entries().size() + ")");
    }

    public PluginContextImpl getContext() {
        return this.context;
    }

    public ServerInfo getServerInfo() {
        return this.serverInfo;
    }

    public PluginConfig getPluginSettings() {
        return this.settings;
    }

    public Messages getMessages() {
        return this.messages;
    }

    public ColorCatalog getColors() {
        return this.colors;
    }

    public MenuConfig getMenu() {
        return this.menu;
    }

    public ColorStorage getStorage() {
        return this.storage;
    }

    public SchedulerAdapter getScheduler() {
        return this.context == null ? null : this.context.getScheduler();
    }

    public ColorService getColorService() {
        return this.context == null ? null : this.context.getColorService();
    }

    public String getPluginVersion() {
        return this.getDescription().getVersion();
    }
}

