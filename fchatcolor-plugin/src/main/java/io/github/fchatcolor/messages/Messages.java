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
    private static final Pattern BARE_HEX = Pattern.compile("(?i)(?<![&<])#([0-9A-Fa-f]{6})(?![0-9A-Fa-f])");
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
        String rawCheck = this.raw(key);
        // If message contains {color_name} and caller didn't provide it, inject it BEFORE translation so following &a! is correctly translated
        if (rawCheck != null && rawCheck.contains("{color_name}") && !containsPlaceholder(placeholders, "color_name")) {
            String[] extra = resolveColorPlaceholders(to);
            // extra is {color_name, colored+reset, color_plain, plain} — we want raw displayName before translation for correct &a handling
            // Instead use raw displayName (with MiniMessage) for pre-translation replacement
            String rawDisplay = resolveRawColorDisplayName(to);
            if (rawDisplay != null) {
                // Build final by replacing {color_name} in raw before prefix/translate, then handle other placeholders normally
                String rawWithColor = rawCheck.replace("{color_name}", rawDisplay);
                String withPrefix2 = this.applyPrefix(rawWithColor);
                String translated2 = this.translateInternal(withPrefix2);
                // Apply remaining placeholders (excluding color_name)
                String result2 = applyPlaceholders(translated2, placeholders);
                // Also handle {color_plain} if present
                if (result2.contains("{color_plain}")) {
                    String plain = stripColorForMessage(rawDisplay);
                    result2 = result2.replace("{color_plain}", plain);
                }
                for (String line : result2.split("\n")) {
                    to.sendMessage(line);
                }
                return;
            }
        }
        // Fallback for other cases / explicit placeholders
        boolean needsColorName = rawCheck != null && (rawCheck.contains("{color_name}") || rawCheck.contains("{color_plain}"));
        if (needsColorName && !containsPlaceholder(placeholders, "color_name") && !containsPlaceholder(placeholders, "color_plain")) {
            String[] extra = resolveColorPlaceholders(to);
            if (extra != null && extra.length > 0) {
                String[] merged = new String[placeholders.length + extra.length];
                System.arraycopy(placeholders, 0, merged, 0, placeholders.length);
                System.arraycopy(extra, 0, merged, placeholders.length, extra.length);
                placeholders = merged;
            }
        }
        String text = this.get(key, placeholders);
        for (String line : text.split("\n")) {
            to.sendMessage(line);
        }
    }

    private boolean containsPlaceholder(String[] placeholders, String name) {
        if (placeholders == null) return false;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String k = placeholders[i];
            if (k == null) continue;
            String bare = k;
            if (bare.length() > 1 && bare.charAt(0) == '{' && bare.charAt(bare.length()-1) == '}') bare = bare.substring(1, bare.length()-1);
            if (bare.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String[] resolveColorPlaceholders(CommandSender to) {
        if (!(to instanceof org.bukkit.entity.Player)) return new String[0];
        org.bukkit.entity.Player p = (org.bukkit.entity.Player) to;
        try {
            io.github.fchatcolor.api.context.PluginContext ctx = null;
            if (this.plugin instanceof io.github.fchatcolor.FChatColorPlugin) {
                ctx = ((io.github.fchatcolor.FChatColorPlugin) this.plugin).getContext();
            }
            if (ctx == null) return new String[0];
            String stored = null;
            try { stored = ctx.getStorage().getSelectedColor(p.getUniqueId()); } catch (Throwable ignored) {}
            String effective = stored;
            // Fallback to defaults like chat listener
            if (effective == null || effective.isEmpty() || !hasPermissionForColor(ctx, p, effective)) {
                effective = resolveEffectiveColorForMessages(ctx, p, stored);
            }
            if (effective == null || effective.isEmpty()) return new String[]{"{color_name}", "", "{color_plain}", ""};
            io.github.fchatcolor.colors.ColorCatalog catalog = null;
            try {
                if (this.plugin instanceof io.github.fchatcolor.FChatColorPlugin) catalog = ((io.github.fchatcolor.FChatColorPlugin) this.plugin).getColors();
            } catch (Throwable ignored) {}
            String display = null;
            if (catalog != null) {
                io.github.fchatcolor.colors.ColorCatalog.Entry e = catalog.findByValue(effective);
                if (e == null) e = catalog.find(effective);
                if (e != null) display = e.getDisplayName();
            }
            if (display == null || display.isEmpty()) display = effective;
            String plain = stripColorForMessage(display);
            String colored = translateInternal(display);
            // Append reset so "{color_name}!" doesn't tint "!" — template can still do "&r&a!" for green "!"
            if (!colored.endsWith("\u00A7r") && !colored.endsWith("&r")) colored = colored + "\u00A7r";
            return new String[]{"{color_name}", colored, "{color_plain}", plain};
        } catch (Throwable t) {
            return new String[0];
        }
    }

    private String resolveRawColorDisplayName(CommandSender to) {
        if (!(to instanceof org.bukkit.entity.Player)) return null;
        org.bukkit.entity.Player p = (org.bukkit.entity.Player) to;
        try {
            io.github.fchatcolor.api.context.PluginContext ctx = null;
            if (this.plugin instanceof io.github.fchatcolor.FChatColorPlugin) ctx = ((io.github.fchatcolor.FChatColorPlugin) this.plugin).getContext();
            if (ctx == null) return null;
            String stored = null;
            try { stored = ctx.getStorage().getSelectedColor(p.getUniqueId()); } catch (Throwable ignored) {}
            String effective = stored;
            if (effective == null || effective.isEmpty() || !hasPermissionForColor(ctx, p, effective)) {
                effective = resolveEffectiveColorForMessages(ctx, p, stored);
            }
            if (effective == null || effective.isEmpty()) return null;
            io.github.fchatcolor.colors.ColorCatalog catalog = null;
            try { if (this.plugin instanceof io.github.fchatcolor.FChatColorPlugin) catalog = ((io.github.fchatcolor.FChatColorPlugin) this.plugin).getColors(); } catch (Throwable ignored) {}
            String display = null;
            if (catalog != null) {
                io.github.fchatcolor.colors.ColorCatalog.Entry e = catalog.findByValue(effective);
                if (e == null) e = catalog.find(effective);
                if (e != null) display = e.getDisplayName();
            }
            if (display == null || display.isEmpty()) display = effective;
            // Append reset so "{color_name}Hola" doesn't tint Hola — template can still do "&aHola" for aqua
            if (!display.toLowerCase(java.util.Locale.ROOT).contains("<reset>") && !display.contains("\u00A7r")) {
                display = display + "<reset>";
            }
            return display;
        } catch (Throwable t) { return null; }
    }

    private boolean hasPermissionForColor(io.github.fchatcolor.api.context.PluginContext ctx, org.bukkit.entity.Player p, String stored) {
        try {
            io.github.fchatcolor.colors.ColorCatalog catalog = null;
            if (this.plugin instanceof io.github.fchatcolor.FChatColorPlugin) catalog = ((io.github.fchatcolor.FChatColorPlugin) this.plugin).getColors();
            if (catalog != null) {
                io.github.fchatcolor.colors.ColorCatalog.Entry e = catalog.findByValue(stored);
                if (e == null) e = catalog.find(stored);
                if (e != null) {
                    String perm = e.getPermission();
                    return perm == null || perm.isEmpty() || p.hasPermission(perm);
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private String resolveEffectiveColorForMessages(io.github.fchatcolor.api.context.PluginContext ctx, org.bukkit.entity.Player p, String stored) {
        try {
            java.util.Map<String,String> permMap = ctx.getSettings().getDefaultPermissionColors();
            if (permMap != null) {
                for (java.util.Map.Entry<String,String> e : permMap.entrySet()) {
                    String perm = e.getKey();
                    String val = e.getValue();
                    if (perm != null && val != null && !perm.isEmpty() && !val.isEmpty() && p.hasPermission(perm)) {
                        String r = resolveDefaultColorForMessages(val);
                        if (r != null && !r.isEmpty()) return r;
                    }
                }
            }
        } catch (Throwable ignored) {}
        String def = null;
        try { def = ctx.getSettings().getDefaultColor(); } catch (Throwable ignored) {}
        return resolveDefaultColorForMessages(def);
    }

    private String resolveDefaultColorForMessages(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("disabled") || s.equalsIgnoreCase("off")) return null;
        try {
            io.github.fchatcolor.colors.ColorCatalog catalog = null;
            if (this.plugin instanceof io.github.fchatcolor.FChatColorPlugin) catalog = ((io.github.fchatcolor.FChatColorPlugin) this.plugin).getColors();
            if (catalog != null) {
                io.github.fchatcolor.colors.ColorCatalog.Entry e = catalog.find(s);
                if (e != null) return e.getValue();
                Integer rgb = io.github.fchatcolor.api.util.HexColor.parseRgb(s);
                if (rgb != null) return io.github.fchatcolor.api.util.HexColor.toHex(rgb);
                String clean = s;
                if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
                if (io.github.fchatcolor.api.util.HexColor.isLegacyCode(clean)) return io.github.fchatcolor.api.util.HexColor.normalizeLegacyCode(clean);
                if (io.github.fchatcolor.api.util.RainbowColor.isRainbow(s)) return "rainbow";
            }
        } catch (Throwable ignored) {}
        Integer rgb = io.github.fchatcolor.api.util.HexColor.parseRgb(s);
        if (rgb != null) return io.github.fchatcolor.api.util.HexColor.toHex(rgb);
        String clean = s.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
        if (io.github.fchatcolor.api.util.HexColor.isLegacyCode(clean)) return io.github.fchatcolor.api.util.HexColor.normalizeLegacyCode(clean);
        if (io.github.fchatcolor.api.util.RainbowColor.isRainbow(s)) return "rainbow";
        return s;
    }

    private String stripColorForMessage(String input) {
        if (input == null) return "";
        String s = input.replaceAll("<[^>]*>", "");
        s = s.replaceAll("(?i)gradient:", "");
        s = s.replaceAll("(?i)&#[0-9A-Fa-f]{6}", "");
        s = s.replaceAll("(?i)#[0-9A-Fa-f]{6}", "");
        s = s.replaceAll("(?i)&[0-9A-Fa-fk-or]", "");
        s = s.replaceAll("\u00A7[0-9A-Fa-fk-or]", "");
        return s.trim();
    }

    public void sendRaw(CommandSender to, String text) {
        for (String line : translateInternal(text).split("\n")) {
            to.sendMessage(line);
        }
    }

    public void sendUsage(CommandSender to) {
        java.util.List<String> user = null;
        java.util.List<String> admin = null;
        try {
            if (this.messagesFile != null) {
                if (this.messagesFile.isList("usage.user") || this.messagesFile.isConfigurationSection("usage")) {
                    user = this.messagesFile.getStringList("usage.user");
                }
                if (this.messagesFile.isList("usage.admin") || this.messagesFile.isConfigurationSection("usage")) {
                    admin = this.messagesFile.getStringList("usage.admin");
                }
            }
        } catch (Throwable ignored) {
        }
        boolean hasSection = (user != null && !user.isEmpty()) || (admin != null && !admin.isEmpty());
        if (!hasSection) {
            // Fallback to legacy flat "usage" key
            this.send(to, "usage");
            return;
        }
        if (user == null) user = java.util.Collections.emptyList();
        if (admin == null) admin = java.util.Collections.emptyList();
        // If file has empty sections (e.g. defaults missing), use built-in defaults
        if (user.isEmpty() && admin.isEmpty()) {
            this.send(to, "usage");
            return;
        }
        boolean isAdmin = false;
        try { isAdmin = to.isOp() || to.hasPermission("fchatcolor.admin"); } catch (Throwable ignored) {
            try { isAdmin = to.hasPermission("fchatcolor.admin"); } catch (Throwable ignored2) {}
        }
        if (!isAdmin && !(to instanceof org.bukkit.entity.Player)) isAdmin = true;
        java.util.List<String> toSend = isAdmin ? admin : user;
        // Fallback if chosen section empty
        if (toSend == null || toSend.isEmpty()) toSend = isAdmin ? user : admin;
        for (String line : toSend) {
            String prefixed = this.applyPrefix(line);
            String translated = this.translateInternal(prefixed);
            for (String part : translated.split("\n")) {
                to.sendMessage(part);
            }
        }
    }

    public static String translate(String text) {
        if (text == null) {
            return "";
        }
        String normalized = io.github.fchatcolor.api.util.HexColor.replaceBareHexOutsideTags(text);
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
        String normalized = io.github.fchatcolor.api.util.HexColor.replaceBareHexOutsideTags(text);
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
        DEFAULTS.put("usage", "{prefix}&e/fchatcolor set <color> &7- choose your chat color\n&e/fchatcolor clear &7- remove your color\n&e/fchatcolor modifier set <modifier> &7- toggle a chat modifier\n&e/fchatcolor set <player> <color> &7- (console) set the color of a player\n&e/fchatcolor modifier set <player> <modifier> &7- (console) toggle modifier for a player\n&e/fchatcolor menu <player> - Open the menu for yourself or for a user\n&e/fchatcolor reload &7- reload the configuration");
        DEFAULTS.put("usage.user", "\n{prefix} <white>- Ayuda\n\n&e/chatcolor set <color> &7- choose your chat color\n&e/chatcolor &7- open the menu\n");
        DEFAULTS.put("usage.admin", "\n{prefix}\n\n&e/fchatcolor set <color> &7- choose your chat color\n&e/fchatcolor clear &7- remove your color\n&e/fchatcolor modifier set <modifier> &7- toggle a chat modifier\n&e/fchatcolor set <player> <color> &7- (console) set the color of a player\n&e/fchatcolor modifier set <player> <modifier> &7- (console) toggle modifier for a player\n&e/fchatcolor menu <player> <gray>- Open the menu for yourself or for a user\n&e/fchatcolor reload &7- reload the configuration\n");
        DEFAULTS.put("color-set", "{prefix}&aChat color updated: {color}&r &7({value})");
        DEFAULTS.put("color-set-other", "{prefix}&aSet the chat color of {player}: {color}&r &7({value})");
        DEFAULTS.put("color-cleared", "{prefix}&aYour chat color has been removed; the server default will be used.");
        DEFAULTS.put("color-cleared-other", "{prefix}&aRemoved the chat color of {player}.");
        DEFAULTS.put("color-invalid", "{prefix}&cInvalid color. Examples: &f#FF5555&c, &f&9&c or a name from colors.yml.");
        DEFAULTS.put("no-color-permission", "{prefix}&cYou don't have permission to use the color {color}.");
        DEFAULTS.put("no-modifier-permission", "{prefix}&cYou don't have permission to use the modifier {modifier}.");
        DEFAULTS.put("player-not-found", "{prefix}&cPlayer &f{player} &cwas not found online.");
        DEFAULTS.put("mode-disabled", "{prefix}&cThat color mode is disabled on this server.");
        DEFAULTS.put("reload-done", "{prefix}&aConfiguration, messages, colors and services reloaded successfully.");
        DEFAULTS.put("modifier-set", "{prefix}&aModifier {modifier} enabled: {name}");
        DEFAULTS.put("modifier-removed", "{prefix}&cModifier {modifier} disabled: {name}");
    }
}
