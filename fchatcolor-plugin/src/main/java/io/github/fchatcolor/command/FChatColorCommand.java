/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package io.github.fchatcolor.command;

import io.github.fchatcolor.FChatColorPlugin;
import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.settings.PluginSettings;
import io.github.fchatcolor.api.storage.StorageException;
import io.github.fchatcolor.api.util.GradientColor;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import io.github.fchatcolor.colors.ColorCatalog;
import io.github.fchatcolor.menu.ChatColorMenu;
import io.github.fchatcolor.messages.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class FChatColorCommand
implements CommandExecutor,
TabCompleter {
    private static final String PERM_COLOR = "fchatcolor.command.color";
    private static final String PERM_ADMIN = "fchatcolor.admin";
    private static final List<String> SUBCOMANDOS = Arrays.asList("menu", "set", "clear", "modifier", "reload", "help");
    private static final List<String> HEX_SUGGESTIONS = Arrays.asList("#FF5555", "#55FF55", "#5555FF", "#FFFF55", "#FF55FF");
    private final FChatColorPlugin plugin;
    private final Messages messages;

    public FChatColorCommand(FChatColorPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                this.openMenu((Player)sender);
            } else {
                this.messages.sendUsage(sender);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("menu") || sub.equals("gui")) {
            if (!(sender instanceof Player)) {
                this.messages.send(sender, "player-only", new String[0]);
                return true;
            }
            this.openMenu((Player)sender);
            return true;
        }
        if (sub.equals("set") || sub.equals("color")) {
            return this.handleSet(sender, args);
        }
        if (sub.equals("clear") || sub.equals("none") || sub.equals("remove")) {
            return this.handleClear(sender, args);
        }
        if (sub.equals("modifier") || sub.equals("modifiers") || sub.equals("mod")) {
            return this.handleModifier(sender, args);
        }
        if (sub.equals("reload")) {
            return this.handleReload(sender);
        }
        if (!sub.equals("help")) {
            this.messages.send(sender, "unknown-command", new String[0]);
            return true;
        }
        this.messages.sendUsage(sender);
        return true;
    }

    private void openMenu(Player player) {
        if (!this.plugin.getPluginSettings().isMenuEnabled()) {
            this.messages.sendUsage((CommandSender)player);
            return;
        }
        if (!this.senderHasColorPermission((CommandSender)player)) {
            this.messages.send((CommandSender)player, "no-permission", new String[0]);
            return;
        }
        new ChatColorMenu(this.plugin).open(player, 0);
    }

    private boolean senderHasColorPermission(CommandSender sender) {
        return sender.hasPermission(PERM_COLOR);
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        String nameForMessage;
        String preview;
        ColorMode requiredMode;
        String colorArgument;
        Player target;
        if (!sender.hasPermission(PERM_COLOR)) {
            this.messages.send(sender, "no-permission", new String[0]);
            return true;
        }
        if (sender instanceof Player) {
            if (args.length < 2) {
                this.messages.sendUsage(sender);
                return true;
            }
            target = (Player)sender;
            colorArgument = args[1].trim();
        } else {
            if (args.length < 3) {
                this.messages.sendUsage(sender);
                return true;
            }
            target = this.findPlayer(args[1]);
            if (target == null) {
                this.messages.send(sender, "player-not-found", "{player}", args[1]);
                return true;
            }
            colorArgument = args[2].trim();
        }
        PluginSettings settings = this.plugin.getContext().getSettings();
        ColorService service = this.plugin.getContext().getColorService();
        ColorCatalog catalog = this.plugin.getColors();
        String canonical = null;
        ColorCatalog.Entry entry = catalog.find(colorArgument);
        if (entry != null) {
            if (!entry.getPermission().isEmpty() && !sender.hasPermission(entry.getPermission())) {
                this.messages.send(sender, "no-color-permission", "{color}", io.github.fchatcolor.messages.Messages.translate(entry.getDisplayName()));
                return true;
            }
            canonical = entry.getValue();
        } else {
            canonical = this.canonicalizeRaw(colorArgument);
            if (canonical != null && canonical.startsWith("#") && !sender.hasPermission("fchatcolor.chat.hex")) {
                this.messages.send(sender, "no-permission", new String[0]);
                return true;
            }
        }
        if (canonical == null) {
            this.messages.send(sender, "color-invalid", new String[0]);
            return true;
        }
        boolean isHex = canonical.startsWith("#");
        boolean isGradient = GradientColor.isGradient(canonical);
        if (isGradient && !isHex) {
            java.util.List<String> palette = GradientColor.parseColors(canonical);
            isHex = GradientColor.containsHex(palette);
            if (isHex && !sender.hasPermission("fchatcolor.chat.hex")) {
                this.messages.send(sender, "no-permission", new String[0]);
                return true;
            }
        }
        ColorMode colorMode = requiredMode = isHex ? ColorMode.HEX : ColorMode.LEGACY;
        if (!settings.isModeEnabled(requiredMode) || !service.supports(requiredMode)) {
            this.messages.send(sender, "mode-disabled", new String[0]);
            return true;
        }
        try {
            this.plugin.getContext().getStorage().setSelectedColor(target.getUniqueId(), canonical);
            try { io.github.fchatcolor.api.util.LastMessageCache.setColor(target.getUniqueId(), canonical); } catch (Throwable ignored) {}
        }
        catch (StorageException ex) {
            this.plugin.getLogger().warning("Could not save color for " + target.getName() + ": " + ex.getMessage());
            return true;
        }
        if (entry != null) {
            preview = this.buildPreview(entry, service, sender);
            nameForMessage = entry.getDisplayName();
        } else {
            preview = this.buildPreview(canonical, service, sender);
            nameForMessage = canonical;
        }
        if (target.equals((Object)sender)) {
            this.messages.send(sender, "color-set", "{color}", preview, "{value}", canonical, "{name}", nameForMessage);
        } else {
            this.messages.send(sender, "color-set-other", "{player}", target.getName(), "{color}", preview, "{value}", canonical, "{name}", nameForMessage);
        }
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        Player target;
        if (!sender.hasPermission(PERM_COLOR)) {
            this.messages.send(sender, "no-permission", new String[0]);
            return true;
        }
        if (sender instanceof Player) {
            target = (Player)sender;
        } else {
            if (args.length < 2) {
                this.messages.sendUsage(sender);
                return true;
            }
            target = this.findPlayer(args[1]);
            if (target == null) {
                this.messages.send(sender, "player-not-found", "{player}", args[1]);
                return true;
            }
        }
        try {
            this.plugin.getContext().getStorage().clearSelectedColor(target.getUniqueId());
            try { io.github.fchatcolor.api.util.LastMessageCache.setColor(target.getUniqueId(), null); } catch (Throwable ignored) {}
        }
        catch (StorageException ex) {
            this.plugin.getLogger().warning("Could not clear color for " + target.getName() + ": " + ex.getMessage());
            return true;
        }
        if (target.equals((Object)sender)) {
            this.messages.send(sender, "color-cleared", new String[0]);
        } else {
            this.messages.send(sender, "color-cleared-other", "{player}", target.getName());
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            this.messages.send(sender, "no-permission", new String[0]);
            return true;
        }
        try {
            this.plugin.reloadPlugin();
            this.messages.send(sender, "reload-done", new String[0]);
        }
        catch (Throwable t) {
            this.plugin.getLogger().severe("Reload failed: " + t);
        }
        return true;
    }

    private boolean handleModifier(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_COLOR)) {
            this.messages.send(sender, "no-permission", new String[0]);
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("set")) {
            this.messages.sendUsage(sender);
            return true;
        }
        return this.handleModifierSet(sender, args);
    }

    private boolean handleModifierSet(CommandSender sender, String[] args) {
        Player target;
        String modifierArg;
        if (sender instanceof Player) {
            if (args.length == 3) {
                target = (Player) sender;
                modifierArg = args[2].trim();
            } else if (args.length == 4) {
                target = this.findPlayer(args[2]);
                if (target == null) {
                    this.messages.send(sender, "player-not-found", "{player}", args[2]);
                    return true;
                }
                modifierArg = args[3].trim();
            } else {
                this.messages.sendUsage(sender);
                return true;
            }
        } else {
            if (args.length == 4) {
                target = this.findPlayer(args[2]);
                if (target == null) {
                    this.messages.send(sender, "player-not-found", "{player}", args[2]);
                    return true;
                }
                modifierArg = args[3].trim();
            } else {
                this.messages.sendUsage(sender);
                return true;
            }
        }
        ColorCatalog catalog = this.plugin.getColors();
        ColorCatalog.Entry entry = catalog.findModifier(modifierArg);
        if (entry == null) {
            // Try canonicalize to give better error
            String canon = ColorCatalog.canonicalizeModifier(modifierArg);
            if (canon != null) entry = catalog.findModifierByValue(canon);
        }
        if (entry == null) {
            this.messages.send(sender, "color-invalid", new String[0]);
            return true;
        }
        if (!entry.getPermission().isEmpty() && !sender.hasPermission(entry.getPermission())) {
            this.messages.send(sender, "no-modifier-permission", "{modifier}", entry.getName());
            return true;
        }
        final Player finalTarget = target;
        final ColorCatalog.Entry finalEntry = entry;
        final CommandSender finalSender = sender;
        // Async toggle
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    String currentMods = plugin.getContext().getStorage().getSelectedModifiers(finalTarget.getUniqueId());
                    java.util.Set<String> set = new java.util.HashSet<String>();
                    if (currentMods != null && !currentMods.trim().isEmpty()) {
                        String[] parts = currentMods.split(",");
                        for (String p : parts) {
                            p = p.trim().toLowerCase(java.util.Locale.ROOT);
                            if (p.isEmpty()) continue;
                            String cv = ColorCatalog.canonicalizeModifier(p);
                            if (cv == null) cv = ColorCatalog.canonicalizeModifier("&" + p);
                            if (cv == null) continue;
                            set.add(cv.toLowerCase(java.util.Locale.ROOT));
                        }
                    }
                    String canonEntry = finalEntry.getValue().toLowerCase(java.util.Locale.ROOT);
                    // Normalize to &x form for comparison
                    String normCanon = ColorCatalog.canonicalizeModifier(canonEntry);
                    if (normCanon != null) canonEntry = normCanon.toLowerCase(java.util.Locale.ROOT);
                    else if (!canonEntry.startsWith("&")) canonEntry = "&" + canonEntry;
                    boolean wasSelected = set.contains(canonEntry);
                    if (wasSelected) set.remove(canonEntry);
                    else set.add(canonEntry);
                    StringBuilder sb = new StringBuilder();
                    for (String v : set) {
                        String cv = ColorCatalog.canonicalizeModifier(v);
                        if (cv == null) cv = ColorCatalog.canonicalizeModifier("&" + v);
                        if (cv == null) continue;
                        String store = cv.startsWith("&") ? cv.substring(1) : cv;
                        if (sb.length() > 0) sb.append(",");
                        sb.append(store);
                    }
                    String toSave = sb.toString();
                    if (toSave.isEmpty()) plugin.getContext().getStorage().clearSelectedModifiers(finalTarget.getUniqueId());
                    else plugin.getContext().getStorage().setSelectedModifiers(finalTarget.getUniqueId(), toSave);
                    // Send message on main thread via scheduler if available
                    Runnable sync = new Runnable() {
                        @Override
                        public void run() {
                            String display = finalEntry.getDisplayName();
                            // Strip tags for preview? Use translate via service if available
                            try {
                                ColorService cs = plugin.getContext().getColorService();
                                if (cs != null) display = cs.translate(display, ColorMode.MINIMESSAGE);
                                else display = Messages.translate(display);
                            } catch (Throwable ignored) {
                                display = finalEntry.getName();
                            }
                            if (wasSelected) {
                                if (finalTarget.equals(finalSender)) messages.send(finalSender, "modifier-removed", "{modifier}", finalEntry.getName(), "{name}", display);
                                else messages.send(finalSender, "modifier-removed", "{modifier}", finalEntry.getName(), "{name}", display, "{player}", finalTarget.getName());
                            } else {
                                if (finalTarget.equals(finalSender)) messages.send(finalSender, "modifier-set", "{modifier}", finalEntry.getName(), "{name}", display);
                                else messages.send(finalSender, "modifier-set", "{modifier}", finalEntry.getName(), "{name}", display, "{player}", finalTarget.getName());
                            }
                        }
                    };
                    try {
                        if (plugin.getScheduler() != null) plugin.getScheduler().runEntity(finalTarget, sync);
                        else sync.run();
                    } catch (Throwable t) { sync.run(); }
                } catch (StorageException ex) {
                    plugin.getLogger().warning("Could not toggle modifier for " + finalTarget.getName() + ": " + ex.getMessage());
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Could not toggle modifier for " + finalTarget.getName() + ": " + ex.getMessage());
                }
            }
        };
        if (plugin.getScheduler() != null) plugin.getScheduler().runAsync(task);
        else task.run();
        return true;
    }

    private Player findPlayer(String name) {
        return this.plugin.getServer().getPlayerExact(name.trim());
    }

    private String canonicalizeRaw(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        Integer rgb = HexColor.parseRgb(input);
        if (rgb != null) {
            return HexColor.toHex(rgb);
        }
        String clean = input.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00a7")) {
            clean = clean.substring(1);
        }
        if (HexColor.isLegacyCode(clean)) {
            return HexColor.normalizeLegacyCode(clean);
        }
        return null;
    }

    private String buildPreview(ColorCatalog.Entry entry, ColorService service, CommandSender sender) {
        String canonical = entry.getValue();
        String plain = entry.getDisplayName().replaceAll("<[^>]*>", "").trim();
        if (plain.isEmpty()) {
            plain = entry.getName();
        }
        if (RainbowColor.isRainbow(canonical)) {
            return RainbowColor.colorize(plain, sender != null && sender.hasPermission("fchatcolor.chat.hex"));
        }
        if (GradientColor.isGradient(canonical)) {
            java.util.List<String> palette = GradientColor.parseColors(canonical);
            if (!palette.isEmpty()) return GradientColor.colorize(plain, palette, sender != null && sender.hasPermission("fchatcolor.chat.hex"));
        }
        if (canonical.startsWith("#")) {
            return service.translate("&#" + canonical.substring(1) + plain, ColorMode.HEX);
        }
        return service.translate("&" + canonical + plain, ColorMode.LEGACY);
    }

    private String buildPreview(String canonical, ColorService service, CommandSender sender) {
        if (RainbowColor.isRainbow(canonical)) {
            return RainbowColor.colorize(canonical, sender != null && sender.hasPermission("fchatcolor.chat.hex"));
        }
        if (GradientColor.isGradient(canonical)) {
            java.util.List<String> palette = GradientColor.parseColors(canonical);
            if (!palette.isEmpty()) return GradientColor.colorize(canonical, palette, sender != null && sender.hasPermission("fchatcolor.chat.hex"));
        }
        if (canonical.startsWith("#")) {
            return service.translate("&#" + canonical.substring(1) + canonical, ColorMode.HEX);
        }
        return service.translate("&" + canonical + canonical, ColorMode.LEGACY);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String sub;
        ArrayList<String> result = new ArrayList<String>();
        boolean console = !(sender instanceof Player);
        String string = sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (console && (sub.equals("set") || sub.equals("color"))) {
            if (args.length == 2) {
                this.addOnlinePlayerNames(args[1], result);
            } else if (args.length == 3) {
                this.addColorSuggestions(sender, args[2], result);
            }
            return result;
        }
        if (console && (sub.equals("clear") || sub.equals("none") || sub.equals("remove")) && args.length == 2) {
            this.addOnlinePlayerNames(args[1], result);
            return result;
        }
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            for (String s : SUBCOMANDOS) {
                if (!s.startsWith(prefix)) continue;
                result.add(s);
            }
            return result;
        }
        if (args.length == 2 && (sub.equals("set") || sub.equals("color"))) {
            this.addColorSuggestions(sender, args[1], result);
        }
        if (sub.equals("modifier") || sub.equals("modifiers") || sub.equals("mod")) {
            if (args.length == 2) {
                if ("set".startsWith(args[1].toLowerCase(Locale.ROOT))) result.add("set");
                return result;
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
                // Player self: modifier name; console: player name
                if (sender instanceof Player) {
                    this.addModifierSuggestions(sender, args[2], result);
                } else {
                    this.addOnlinePlayerNames(args[2], result);
                }
                return result;
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                this.addModifierSuggestions(sender, args[3], result);
                return result;
            }
        }
        return result;
    }

    private void addOnlinePlayerNames(String typed, List<String> result) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (!player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            result.add(player.getName());
        }
    }

    private void addColorSuggestions(CommandSender sender, String typed, List<String> result) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        for (ColorCatalog.Entry entry : this.plugin.getColors().entries()) {
            if (!entry.getPermission().isEmpty() && !sender.hasPermission(entry.getPermission()) || !entry.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            result.add(entry.getName());
        }
        if (sender.hasPermission("fchatcolor.chat.hex")) {
            for (String hex : HEX_SUGGESTIONS) {
                if (!hex.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
                result.add(hex);
            }
        }
    }

    private void addModifierSuggestions(CommandSender sender, String typed, List<String> result) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        for (ColorCatalog.Entry entry : this.plugin.getColors().modifierEntries()) {
            if (!entry.getPermission().isEmpty() && !sender.hasPermission(entry.getPermission())) continue;
            if (!entry.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            result.add(entry.getName());
        }
    }
}

