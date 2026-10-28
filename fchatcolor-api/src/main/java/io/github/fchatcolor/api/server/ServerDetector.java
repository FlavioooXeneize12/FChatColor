package io.github.fchatcolor.api.server;

import io.github.fchatcolor.api.util.Reflection;
import org.bukkit.Bukkit;

import java.util.Locale;

/**
 * Server detection via reflection.
 *
 * <p>Nothing here depends de APIs de Paper/Folia a nivel de compilación: todo se
 * sondea con {@link Reflection#classExists(String)}, de modo que esta clase
 * reflected, so it works on 1.8 through 1.21.x and year-based builds (26.x).
 */
public final class ServerDetector {

    // Probe classes
    private static final String PAPER_CONFIGURATION = "io.papermc.paper.configuration.Configuration";
    private static final String PAPER_LEGACY_CONFIG = "com.destroystokyo.paper.PaperConfig";
    private static final String FOLIA_REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String FOLIA_SCHEDULER = "io.papermc.paper.threadedregions.scheduler.RegionScheduler";
    private static final String ADVENTURE_COMPONENT = "net.kyori.adventure.text.Component";
    private static final String MINIMESSAGE = "net.kyori.adventure.text.minimessage.MiniMessage";
    private static final String PAPER_CHAT_EVENT = "io.papermc.paper.event.player.AsyncChatEvent";
    private static final String PURPUR_CONFIG = "org.purpurmc.purpur.PurpurConfig";
    private static final String PUFFERFISH_CONFIG = "gg.pufferfish.pufferfish.PufferfishConfig";

    private ServerDetector() {
    }

    /** Runs full detection. Never throws. */
    public static ServerInfo detect() {
        String bukkitName = safe(() -> Bukkit.getName(), "unknown");
        String bukkitVersion = safe(() -> Bukkit.getBukkitVersion(), "unknown");
        String fullVersion = safe(() -> Bukkit.getVersion(), "unknown");

        MinecraftVersion version = MinecraftVersion.parse(bukkitVersion);
        if (version.isUnknown()) {
            version = MinecraftVersion.parse(fullVersion);
        }

        boolean canvas = containsIgnoreCase(bukkitName, "canvas");
        boolean folia = Reflection.classExists(FOLIA_REGIONIZED_SERVER) || containsIgnoreCase(bukkitName, "folia");
        boolean paper = Reflection.classExists(PAPER_CONFIGURATION)
                || Reflection.classExists(PAPER_LEGACY_CONFIG)
                || containsIgnoreCase(fullVersion, "paper")
                || folia
                || canvas;

        ServerPlatform platform = resolvePlatform(bukkitName, fullVersion, folia, canvas, paper);

        boolean adventure = Reflection.classExists(ADVENTURE_COMPONENT);
        boolean minimessage = Reflection.classExists(MINIMESSAGE);
        boolean legacyHex = hasLegacyHexSupport();
        boolean modernChatEvent = Reflection.classExists(PAPER_CHAT_EVENT);
        boolean foliaSchedulers = Reflection.classExists(FOLIA_SCHEDULER);

        return new ServerInfo(version, platform, bukkitName, bukkitVersion, fullVersion,
                folia, canvas, paper, adventure, minimessage, legacyHex,
                modernChatEvent, foliaSchedulers);
    }

    /**
     * Legacy hex colors: desde la 1.16 {@code org.bukkit.ChatColor#of(String)}
     * convierte "#RRGGBB" en la secuencia {@code §x§R§R§G§G§B§B}. Lo detectamos
     * via reflection para no compilar contra esa version.
     */
    private static boolean hasLegacyHexSupport() {
        Class<?> chatColor = Reflection.findClass("org.bukkit.ChatColor");
        if (chatColor == null) {
            return false;
        }
        return Reflection.methodExists(chatColor, "of", String.class);
    }

    private static ServerPlatform resolvePlatform(String bukkitName,
                                                  String fullVersion,
                                                  boolean folia,
                                                  boolean canvas,
                                                  boolean paper) {
        String name = safeLower(bukkitName);
        String version = safeLower(fullVersion);

        if (canvas) {
            return ServerPlatform.CANVAS;
        }
        if (folia) {
            return ServerPlatform.FOLIA;
        }
        if (name.contains("purpur") || version.contains("purpur") || Reflection.classExists(PURPUR_CONFIG)) {
            return ServerPlatform.PURPUR;
        }
        if (name.contains("pufferfish") || version.contains("pufferfish") || Reflection.classExists(PUFFERFISH_CONFIG)) {
            return ServerPlatform.PUFFERFISH;
        }
        if (paper || name.contains("paper") || version.contains("paper")) {
            return ServerPlatform.PAPER;
        }
        if (name.contains("spigot") || version.contains("spigot")) {
            return ServerPlatform.SPIGOT;
        }
        if (name.contains("craftbukkit") || version.contains("craftbukkit")) {
            return ServerPlatform.CRAFTBUKKIT;
        }
        if (name.equals("vanilla") || name.contains("vanilla")) {
            return ServerPlatform.VANILLA;
        }
        return ServerPlatform.UNKNOWN;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private interface Supplier {
        String get();
    }

    private static String safe(Supplier supplier, String fallback) {
        try {
            String value = supplier.get();
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
