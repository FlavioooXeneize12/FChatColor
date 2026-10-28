package io.github.fchatcolor.api.server;

/**
 * Server detection result: Minecraft version, fork and
 * real capabilities found via reflection.
 */
public final class ServerInfo {

    private final MinecraftVersion version;
    private final ServerPlatform platform;
    private final String bukkitName;
    private final String bukkitVersion;
    private final String fullVersion;

    private final boolean folia;
    private final boolean canvas;
    private final boolean paper;
    private final boolean adventure;
    private final boolean minimessage;
    private final boolean legacyHex;
    private final boolean modernChatEvent;
    private final boolean foliaSchedulers;

    public ServerInfo(MinecraftVersion version,
                      ServerPlatform platform,
                      String bukkitName,
                      String bukkitVersion,
                      String fullVersion,
                      boolean folia,
                      boolean canvas,
                      boolean paper,
                      boolean adventure,
                      boolean minimessage,
                      boolean legacyHex,
                      boolean modernChatEvent,
                      boolean foliaSchedulers) {
        this.version = version;
        this.platform = platform;
        this.bukkitName = bukkitName;
        this.bukkitVersion = bukkitVersion;
        this.fullVersion = fullVersion;
        this.folia = folia;
        this.canvas = canvas;
        this.paper = paper;
        this.adventure = adventure;
        this.minimessage = minimessage;
        this.legacyHex = legacyHex;
        this.modernChatEvent = modernChatEvent;
        this.foliaSchedulers = foliaSchedulers;
    }

    public MinecraftVersion getVersion() {
        return version;
    }

    public ServerPlatform getPlatform() {
        return platform;
    }

    /** Nombre corto reportado por {@code Bukkit.getName()} ("CraftBukkit", "Paper", "Folia"...). */
    public String getBukkitName() {
        return bukkitName;
    }

    /** {@code Bukkit.getBukkitVersion()}, por ejemplo {@code "1.21.4-R0.1-SNAPSHOT"}. */
    public String getBukkitVersion() {
        return bukkitVersion;
    }

    /** {@code Bukkit.getVersion()}, el string largo de la build. */
    public String getFullVersion() {
        return fullVersion;
    }

    /** Running on Folia (or a fork like Canvas). */
    public boolean isFolia() {
        return folia;
    }

    /** Running on Canvas. */
    public boolean isCanvas() {
        return canvas;
    }

    /** Paper API is available. */
    public boolean isPaper() {
        return paper;
    }

    /** Exists {@code net.kyori.adventure.text.Component} (Adventure disponible). */
    public boolean hasAdventure() {
        return adventure;
    }

    /** Exists {@code net.kyori.adventure.text.minimessage.MiniMessage}. */
    public boolean hasMiniMessage() {
        return minimessage;
    }

    /** Exists {@code org.bukkit.ChatColor#of(String)} (colores hex nativos, 1.16+). */
    public boolean hasLegacyHex() {
        return legacyHex;
    }

    /** Exists {@code io.papermc.paper.event.player.AsyncChatEvent}. */
    public boolean hasModernChatEvent() {
        return modernChatEvent;
    }

    /** Existsn los schedulers regionizados de Folia en este runtime. */
    public boolean hasFoliaSchedulers() {
        return foliaSchedulers;
    }

    /** Should we use the modern module (Adventure/MiniMessage)? */
    public boolean isModern() {
        return hasAdventure() || hasMiniMessage();
    }

    /** Human-readable summary for the log del servidor. */
    public String describe() {
        return "Minecraft " + version.toNormalizedString()
                + " | plataforma: " + platform
                + " (" + bukkitName + ")"
                + " | paper=" + paper
                + ", adventure=" + adventure
                + ", minimessage=" + minimessage
                + ", hexLegacy=" + legacyHex
                + ", foliaSchedulers=" + foliaSchedulers
                + ", modernChatEvent=" + modernChatEvent;
    }
}
