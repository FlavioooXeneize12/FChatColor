package io.github.fchatcolor.messages;

import io.github.fchatcolor.FChatColorPlugin;
import io.github.fchatcolor.api.FChatColorAPI;
import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class Messages {
    private static final Map<String, String> DEFAULTS = new HashMap<String, String>();
    private static final Pattern BARE_HEX = Pattern.compile("(?<!&)#([0-9A-Fa-f]{6})\\b");
    private final JavaPlugin plugin;
    private volatile FileConfiguration messagesFile;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    public void reload() {
        File file = new File(this.plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            try {
                this.plugin.saveResource("messages.yml", false);
            } catch (Throwable t) {
                this.plugin.getLogger().warning("Could not create messages.yml: " + t.getMessage());
            }
        }
        this.messagesFile = YamlConfiguration.loadConfiguration(file);
    }

    public String raw(String key) {
        String value = Messages.readMessage(this.messagesFile, key, DEFAULTS.get(key));
        if (value == null) {
            value = key;
        }
        return value;
    }

    public static String readMessage(FileConfiguration file, String key, String fallback) {
        Object value = file == null ? null : file.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List) {
            return Messages.joinLines((List) value);
        }
        return fallback;
    }

    public static String joinLines(List<?> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); ++i) {
            Object line;
            if (i > 0) {
                builder.append('\n');
            }
            if ((line = lines.get(i)) == null) continue;
            builder.append(line);
        }
        return builder.toString();
    }

    public String get(String key) {
        return translateInternal(this.applyPrefix(this.raw(key)));
    }

    public String get(String key, String... placeholders) {
        String base = translateInternal(this.applyPrefix(this.raw(key)));
        return Messages.applyPlaceholders(base, placeholders);
    }

    public void send(CommandSender to, String key, String... placeholders) {
        String text = this.get(key, placeholders);
        for (String line : text.split("\n")) {
            to.sendMessage(line);
        }
    }

    public void sendRaw(CommandSender to, String text) {
        for (String line : translateInternal(text).split("\n")) {
            to.sendMessage(line);
        }
    }

    public static String translate(String text) {
        if (text == null) {
            return "";
        }
        String normalized = BARE_HEX.matcher(text).replaceAll("&#$1");
        // Try active ColorService via API (covers hex, MiniMessage, legacy)
        try {
            FChatColorAPI api = FChatColorAPI.get();
            if (api != null && api.getColorService() != null) {
                ColorService service = api.getColorService();
                return service.translate(normalized, ColorMode.MINIMESSAGE);
            }
        } catch (Throwable ignored) {
        }
        // Fallback: legacy ChatColor
        return ChatColor.translateAlternateColorCodes('&', normalized);
    }

    private String translateInternal(String text) {
        if (text == null) {
            return "";
        }
        String normalized = BARE_HEX.matcher(text).replaceAll("&#$1");
        // Prefer plugin's live ColorService (most accurate, reflects reload)
        try {
            if (this.plugin instanceof FChatColorPlugin) {
                ColorService service = ((FChatColorPlugin) this.plugin).getColorService();
                if (service != null) {
                    return service.translate(normalized, ColorMode.MINIMESSAGE);
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback to static path (API)
        return Messages.translate(text);
    }

    private String applyPrefix(String message) {
        return message.replace("{prefix}", this.raw("prefix"));
    }

    private static String applyPlaceholders(String message, String... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return message;
        }
        String result = message;
        int i = 0;
        while (i + 1 < placeholders.length) {
            String name = placeholders[i];
            String value = placeholders[i + 1];
            if (name != null && value != null) {
                String bare = name;
                if (bare.length() > 1 && bare.charAt(0) == '{' && bare.charAt(bare.length() - 1) == '}') {
                    bare = bare.substring(1, bare.length() - 1);
                }
                result = result.replace("{" + bare + "}", value);
            }
            i += 2;
        }
        return result;
    }

    static {
        DEFAULTS.put("prefix", "&8[&bFChatColor&8] &r");
        DEFAULTS.put("no-permission", "{prefix}&cYou don't have permission to do that.");
        DEFAULTS.put("player-only", "{prefix}&cThis command can only be used in game.");
        DEFAULTS.put("unknown-command", "{prefix}&cUnknown subcommand. Use &f/fchatcolor help&c.");
        DEFAULTS.put("usage", "{prefix}&e/fchatcolor set <color> &7- choose your chat color\n&e/fchatcolor clear &7- remove your color\n&e/fchatcolor set <player> <color> &7- (console) set the color of a player\n&e/fchatcolor reload &7- reload the configuration");
        DEFAULTS.put("color-set", "{prefix}&aChat color updated: {color}&r &7({value})");
        DEFAULTS.put("color-set-other", "{prefix}&aSet the chat color of {player}: {color}&r &7({value})");
        DEFAULTS.put("color-cleared", "{prefix}&aYour chat color has been removed; the server default will be used.");
        DEFAULTS.put("color-cleared-other", "{prefix}&aRemoved the chat color of {player}.");
        DEFAULTS.put("color-invalid", "{prefix}&cInvalid color. Examples: &f#FF5555&c, &f&9&c or a name from colors.yml.");
        DEFAULTS.put("no-color-permission", "{prefix}&cYou don't have permission to use the color {color}.");
        DEFAULTS.put("player-not-found", "{prefix}&cPlayer &f{player} &cwas not found online.");
        DEFAULTS.put("mode-disabled", "{prefix}&cThat color mode is disabled on this server.");
        DEFAULTS.put("storage-error", "{prefix}&cCould not save the color. Try again in a few seconds.");
        DEFAULTS.put("reload-done", "{prefix}&aConfiguration, messages, colors and services reloaded successfully.");
        DEFAULTS.put("no-storage", "{prefix}&cThe database is not available right now.");
    }
}
