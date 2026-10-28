/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.entity.Player
 */
package io.github.fchatcolor.papi;

import io.github.fchatcolor.FChatColorPlugin;
import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import io.github.fchatcolor.colors.ColorCatalog;
import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class FChatColorExpansion
extends PlaceholderExpansion {
    private final FChatColorPlugin plugin;

    public FChatColorExpansion(FChatColorPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean register(FChatColorPlugin plugin) {
        try {
            return new FChatColorExpansion(plugin).register();
        }
        catch (Throwable t) {
            plugin.getLogger().warning("Could not register the PlaceholderAPI expansion: " + t);
            return false;
        }
    }

    public String getIdentifier() {
        return "fchatcolor";
    }

    public String getAuthor() {
        return "Fresa Network";
    }

    public String getVersion() {
        return this.plugin.getPluginVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null || params.isEmpty()) {
            return null;
        }
        try {
            Player online = player.getPlayer();
            boolean permUse = online == null || online.hasPermission("fchatcolor.chat.use");
            boolean permHex = online != null && online.hasPermission("fchatcolor.chat.hex");
            return FChatColorExpansion.resolve(this.plugin.getContext(), this.plugin.getColors(), player.getUniqueId(), permUse, permHex, params);
        }
        catch (Throwable t) {
            this.plugin.getLogger().warning("Placeholder fchatcolor_" + params + " failed: " + t);
            return null;
        }
    }

    public String onPlaceholderRequest(Player player, String params) {
        return this.onRequest((OfflinePlayer)player, params);
    }

    public static String resolve(PluginContext context, UUID playerId, boolean permUse, boolean permHex, String params) {
        return FChatColorExpansion.resolve(context, null, playerId, permUse, permHex, params);
    }

    public static String resolve(PluginContext context, ColorCatalog catalog, UUID playerId, boolean permUse, boolean permHex, String params) {
        String effective;
        if (context == null || playerId == null || params == null) {
            return null;
        }
        ColorService service = context.getColorService();
        if (service == null) {
            return null;
        }
        String key = params.toLowerCase(Locale.ROOT).trim();
        String stored = FChatColorExpansion.readStoredColor(context, playerId);
        if ("has_color".equals(key)) {
            return String.valueOf(stored != null && !stored.isEmpty());
        }
        if ("color_hex".equals(key)) {
            return stored == null ? "" : stored;
        }
        if ("color_name".equals(key)) {
            return FChatColorExpansion.findColorName(catalog, stored, service, permHex);
        }
        if (!permUse || stored == null || stored.isEmpty()) {
            return "".equals(key) || "color".equals(key) || "color_legacy".equals(key) || "color_preview".equals(key) ? "" : null;
        }
        String string = effective = RainbowColor.isRainbow(stored) ? RainbowColor.currentHex() : stored;
        if ("color".equals(key)) {
            return FChatColorExpansion.translateStored(effective, permHex, service, ColorMode.HEX);
        }
        if ("color_legacy".equals(key)) {
            return FChatColorExpansion.translateStored(effective, false, service, ColorMode.LEGACY);
        }
        if ("color_preview".equals(key)) {
            return FChatColorExpansion.translateStored(effective, permHex, service, ColorMode.HEX) + "\u2588\u2588\u2588\u2588\u2588\u00a7r";
        }
        return null;
    }

    private static String findColorName(ColorCatalog catalog, String stored, ColorService service, boolean allowHex) {
        if (catalog == null || stored == null || stored.isEmpty()) {
            return "";
        }
        ColorCatalog.Entry entry = catalog.findByValue(stored);
        if (entry == null) {
            return "";
        }
        return FChatColorExpansion.translateDisplay(entry.getDisplayName(), service, allowHex);
    }

    private static String translateDisplay(String display, ColorService service, boolean allowHex) {
        if (display == null || display.isEmpty()) {
            return "";
        }
        ColorMode mode = service.supports(ColorMode.MINIMESSAGE) ? ColorMode.MINIMESSAGE : (allowHex && service.supports(ColorMode.HEX) ? ColorMode.HEX : ColorMode.LEGACY);
        return service.translate(display, mode);
    }

    private static String readStoredColor(PluginContext context, UUID playerId) {
        try {
            String color = context.getStorage().getSelectedColor(playerId);
            return color == null || color.isEmpty() ? null : color;
        }
        catch (Throwable t) {
            context.getLogger().warning("Could not read the color for the placeholder: " + t);
            return null;
        }
    }

    private static String translateStored(String stored, boolean allowHex, ColorService service, ColorMode desiredColor) {
        if (!stored.startsWith("#")) {
            return service.translate("&" + stored, ColorMode.LEGACY);
        }
        if (desiredColor == ColorMode.LEGACY || !allowHex) {
            Integer rgb = HexColor.parseRgb(stored);
            if (rgb == null) {
                return "";
            }
            char nearest = HexColor.nearestLegacyCode(rgb);
            return service.translate("&" + nearest, ColorMode.LEGACY);
        }
        return service.translate("&" + stored, ColorMode.HEX);
    }
}

