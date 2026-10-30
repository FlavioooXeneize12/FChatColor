package io.github.fchatcolor.modern;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorSelector;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.settings.ChatDefaults;
import io.github.fchatcolor.api.settings.PluginSettings;
import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * Chat listener for Paper-based servers
 * ({@code AsyncChatEvent}): 1.20.5+, 1.21.x, 26.x, Folia y Canvas.
 */
public final class ModernChatListener implements Listener {

    private static final java.util.concurrent.atomic.AtomicBoolean LOG_START =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicBoolean LOG_END =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private final PluginContext context;
    private final ModernColorService fallbackService;

    /** Reused serializers: factories allocate per call, instances are thread-safe. */
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    public ModernChatListener(PluginContext context) {
        this.context = context;
        this.fallbackService = new ModernColorService(context);
    }

    private ModernColorService service() {
        ColorService current = context.getColorService();
        if (current instanceof ModernColorService) {
            return (ModernColorService) current;
        }
        return fallbackService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChatLowest(io.papermc.paper.event.player.AsyncChatEvent event) {
        try { if (event.getPlayer() != null) { String raw = PLAIN_TEXT.serialize(event.message()); if (raw != null && !raw.isEmpty()) io.github.fchatcolor.api.util.LastMessageCache.set(event.getPlayer().getUniqueId(), raw); } } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (LOG_START.compareAndSet(false, true)) {
            context.getLogger().info("Chat#1 START " + event.getClass().getName()
                    + " -> player=" + (player == null ? "?" : player.getName())
                    + ", usePermission=" + (player != null && player.hasPermission(ColorSelector.PERMISO_USO))
                    + ", dbColor=" + readColorSafely(player));
        }

        if (player != null && !player.hasPermission(ColorSelector.PERMISO_USO)) {
            return;
        }

        PluginSettings settings = context.getSettings();
        ModernColorService colorService = service();
        String raw = PLAIN_TEXT.serialize(event.message());
        if (raw != null && !raw.isEmpty()) { try { io.github.fchatcolor.api.util.LastMessageCache.set(player.getUniqueId(), raw); } catch (Throwable ignored) {} }
        try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] onChat NORMAL player=" + (player == null ? "?" : player.getName()) + " raw=" + raw + " stored=" + readColorSafely(player)); } catch (Throwable ignored) {}
        if (raw == null || raw.isEmpty()) return;

        ColorMode mode = ColorSelector.resolve(player, settings, colorService);
        Component message;

        if (!needsTranslation(raw)) {
            message = event.message();
        } else {
            message = colorService.translateComponent(raw, mode);
        }

        if (ChatDefaults.APPLY_PLAYER_COLOR && player != null) {
            message = applyPlayerColor(message, player, colorService);
        }

        event.message(message);

        if (LOG_END.compareAndSet(false, true)) {
            String childInfo = "";
            try {
                java.util.List<net.kyori.adventure.text.Component> kids = message.children();
                childInfo = ", children=" + kids.size();
                if (!kids.isEmpty() && kids.get(0).color() != null) childInfo += ", child0=" + kids.get(0).color().asHexString();
            } catch (Throwable ignored) {}
            context.getLogger().info("Chat#1 END -> mode=" + mode
                    + ", finalColor=" + (message.color() == null ? "no-root-color" : message.color().asHexString())
                    + childInfo
                    + ", text=[" + PLAIN_TEXT.serialize(message) + "]");
        }
        try {
            io.papermc.paper.event.player.AsyncChatEvent e = event;
            net.kyori.adventure.text.Component finalMsg = message;
            io.papermc.paper.chat.ChatRenderer orig = e.renderer();
            e.renderer((source, displayName, msg, viewer) -> orig.render(source, displayName, finalMsg, viewer));
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onChatMonitor(io.papermc.paper.event.player.AsyncChatEvent event) {
        try {
            Player p = event.getPlayer();
            if (p == null || event.message() == null) return;
            String eff = resolveEffectiveColor(p, context.getStorage().getSelectedColor(p.getUniqueId()));
            try { String recent = io.github.fchatcolor.api.util.LastMessageCache.getColor(p.getUniqueId()); if (recent != null && !recent.isEmpty()) eff = recent; } catch (Throwable ignored) {}
            if (!RainbowColor.isRainbow(eff)) return;
            Component cur = event.message();
            String plain = PLAIN_TEXT.serialize(cur);
            if (plain == null || plain.isEmpty()) return;
            if (plain.contains("\u00A7x") || plain.contains("\u00A7c") || plain.contains("\u00A76")) return;
            boolean hexAllowed = false;
            String rainbowedLegacy = RainbowColor.colorize(plain, hexAllowed);
            String modsLegacy = "";
            try {
                String rawMods = context.getStorage().getSelectedModifiers(p.getUniqueId());
                if (rawMods != null && !rawMods.trim().isEmpty()) {
                    java.util.Set<String> modSet = parseModifiers(rawMods);
                    StringBuilder sb = new StringBuilder();
                    for (String canon : modSet) {
                        String perm = getPermissionForModifier(canon);
                        if (perm != null && !perm.isEmpty() && !p.hasPermission(perm)) continue;
                        if (canon.length() == 2 && canon.charAt(0) == '&') sb.append('\u00A7').append(Character.toLowerCase(canon.charAt(1)));
                    }
                    modsLegacy = sb.toString();
                }
            } catch (Throwable ignored) {}
            if (modsLegacy != null && !modsLegacy.isEmpty()) {
                rainbowedLegacy = rainbowWithModifiersLegacy(plain, hexAllowed, modsLegacy);
            }
            Component comp = service().translateComponent(rainbowedLegacy, hexAllowed ? ColorMode.HEX : ColorMode.LEGACY);
            java.util.Set<TextDecoration> decos = getModifierDecorations(p);
            comp = applyModifiers(comp, p);
            comp = resetNonSelectedDecorations(comp, decos);
            event.message(comp);
        } catch (Throwable ignored) {}
    }

    private String readColorSafely(Player player) {
        if (player == null) {
            return "n/a";
        }
        try {
            String color = context.getStorage().getSelectedColor(player.getUniqueId());
            return color == null || color.isEmpty() ? "none" : color;
        } catch (Throwable t) {
            return "ERROR:" + t;
        }
    }

    private boolean needsTranslation(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        if (raw.indexOf('<') >= 0) {
            return true;
        }
        if (raw.indexOf('#') >= 0) {
            return true;
        }
        char prefix = ChatDefaults.COLOR_CHARACTER;
        if (raw.indexOf(prefix) >= 0) {
            return true;
        }
        return raw.indexOf('\u00A7') >= 0 || raw.indexOf('&') >= 0;
    }

    private Component applyPlayerColor(Component message, Player player, ModernColorService colorService) {
        try {
            ColorStorage storage = context.getStorage();
            String color = storage.getSelectedColor(player.getUniqueId());
            try { String recent = io.github.fchatcolor.api.util.LastMessageCache.getColor(player.getUniqueId()); if (recent != null && !recent.isEmpty()) color = recent; } catch (Throwable ignored) {}
            String effective = resolveEffectiveColor(player, color);
            if (effective == null || effective.isEmpty()) {
                Component withMods = applyModifiers(message, player);
                return resetNonSelectedDecorations(withMods, getModifierDecorations(player));
            }

            boolean allowHex = player.hasPermission(ColorSelector.PERMISO_HEX);

            Component result;
            if (RainbowColor.isRainbow(effective)) {
                boolean legacyHex = false;
                String plain = PLAIN_TEXT.serialize(message);
                String rainbowedLegacy = RainbowColor.colorize(plain, legacyHex);
                // Añadir modifiers como códigos § si existen
                String modsLegacy = "";
                try {
                    String rawMods = context.getStorage().getSelectedModifiers(player.getUniqueId());
                    if (rawMods != null && !rawMods.trim().isEmpty()) {
                        java.util.Set<String> modSet = parseModifiers(rawMods);
                        StringBuilder sb = new StringBuilder();
                        for (String canon : modSet) {
                            String perm = getPermissionForModifier(canon);
                            if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) continue;
                            if (canon.length() == 2 && canon.charAt(0) == '&') sb.append('\u00A7').append(Character.toLowerCase(canon.charAt(1)));
                        }
                        modsLegacy = sb.toString();
                    }
                } catch (Throwable ignored) {}
                if (modsLegacy != null && !modsLegacy.isEmpty()) {
                    rainbowedLegacy = rainbowWithModifiersLegacy(plain, legacyHex, modsLegacy);
                }
                result = service().translateComponent(rainbowedLegacy, ColorMode.LEGACY);
                Set<TextDecoration> decos = getModifierDecorations(player);
                result = resetNonSelectedDecorations(result, decos);
                try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] rainbow OK player=" + player.getName() + " plain=" + plain + " out=" + rainbowedLegacy.replace('§', '&')); } catch (Throwable ignored) {}
            } else if (io.github.fchatcolor.api.util.GradientColor.isGradient(effective)) {
                java.util.List<String> palette = io.github.fchatcolor.api.util.GradientColor.parseColors(effective);
                if (palette.isEmpty()) {
                    result = message;
                } else {
                    Set<TextDecoration> decos = getModifierDecorations(player);
                    result = applyGradientWithMods(message, palette, decos, colorService, player);
                    result = resetNonSelectedDecorations(result, decos);
                    try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] gradient OK player=" + player.getName() + " colors=" + palette.size()); } catch (Throwable ignored) {}
                }
            } else {
                TextColor textColor = colorService.resolveTextColor(effective, allowHex);
                if (textColor == null) {
                    result = message;
                    try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] color FAIL player=" + player.getName() + " effective=" + effective + " (resolveTextColor null)"); } catch (Throwable ignored) {}
                } else {
                    result = message.colorIfAbsent(textColor);
                    try { if (context.getSettings() != null && context.getSettings().isDebug()) context.getLogger().info("[FChatColor-DEBUG] color OK player=" + player.getName() + " effective=" + effective + " hex=" + textColor.asHexString()); } catch (Throwable ignored) {}
                }
                result = applyModifiers(result, player);
                result = resetNonSelectedDecorations(result, getModifierDecorations(player));
            }
            return result;
        } catch (Throwable t) {
            try { context.getLogger().warning("[FChatColor-DEBUG] applyPlayerColor ERROR player=" + player.getName() + ": " + t); } catch (Throwable ignored) {}
            context.getLogger().warning("Could not read the color of player "
                    + player.getName() + ": " + t.getMessage());
            return message;
        }
    }

    private Component applyModifiers(Component message, Player player) {
        Set<TextDecoration> decos = getModifierDecorations(player);
        if (decos.isEmpty()) return message;
        Component out = message;
        for (TextDecoration d : decos) {
            out = out.decoration(d, true);
        }
        return out;
    }

    private Component resetNonSelectedDecorations(Component comp, Set<TextDecoration> selected) {
        Component out = comp;
        for (TextDecoration d : TextDecoration.values()) {
            if (d == TextDecoration.ITALIC && selected.contains(TextDecoration.ITALIC)) continue;
            if (d == TextDecoration.BOLD && selected.contains(TextDecoration.BOLD)) continue;
            if (d == TextDecoration.UNDERLINED && selected.contains(TextDecoration.UNDERLINED)) continue;
            if (d == TextDecoration.STRIKETHROUGH && selected.contains(TextDecoration.STRIKETHROUGH)) continue;
            if (d == TextDecoration.OBFUSCATED && selected.contains(TextDecoration.OBFUSCATED)) continue;
            // Only reset decorations that could leak from prefix (bold/italic etc.)
            if (d == TextDecoration.BOLD || d == TextDecoration.ITALIC || d == TextDecoration.UNDERLINED || d == TextDecoration.STRIKETHROUGH || d == TextDecoration.OBFUSCATED) {
                out = out.decoration(d, false);
            }
        }
        return out;
    }

    private Set<TextDecoration> getModifierDecorations(Player player) {
        Set<TextDecoration> result = new HashSet<TextDecoration>();
        try {
            String raw = context.getStorage().getSelectedModifiers(player.getUniqueId());
            if (raw == null || raw.trim().isEmpty()) return result;
            Set<String> set = parseModifiers(raw);
            for (String canon : set) {
                String perm = getPermissionForModifier(canon);
                if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) continue;
                TextDecoration dec = toDecoration(canon);
                if (dec != null) result.add(dec);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private TextDecoration toDecoration(String canon) {
        if (canon == null || canon.length() != 2) return null;
        char c = Character.toLowerCase(canon.charAt(1));
        if (c == 'l') return TextDecoration.BOLD;
        if (c == 'm') return TextDecoration.STRIKETHROUGH;
        if (c == 'n') return TextDecoration.UNDERLINED;
        if (c == 'o') return TextDecoration.ITALIC;
        if (c == 'k') return TextDecoration.OBFUSCATED;
        return null;
    }

    private Set<String> parseModifiers(String raw) {
        Set<String> set = new HashSet<String>();
        if (raw == null || raw.trim().isEmpty()) return set;
        String[] parts = raw.split(",");
        for (String p : parts) {
            p = p.trim().toLowerCase();
            if (p.isEmpty()) continue;
            if (p.contains("&") && p.length() > 2 && p.indexOf(',') == -1) {
                String[] sub = p.split("&");
                for (String s : sub) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    String canon = canonicalizeModifier("&" + s);
                    if (canon != null) set.add(canon);
                }
            } else {
                String canon = canonicalizeModifier(p);
                if (canon == null && p.length() == 1) canon = canonicalizeModifier("&" + p);
                if (canon != null) set.add(canon);
            }
        }
        return set;
    }

    private String canonicalizeModifier(String value) {
        if (value == null || value.isEmpty()) return null;
        String clean = value.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
        if (clean.length() != 1) return null;
        char c = Character.toLowerCase(clean.charAt(0));
        if (c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'k' || c == 'r') return "&" + c;
        return null;
    }

    private String getPermissionForModifier(String canon) {
        return io.github.fchatcolor.api.util.CatalogReflector.modifierPermission(context.getPlugin(), canon);
    }

    private String resolveEffectiveColor(Player player, String stored) {
        if (RainbowColor.isRainbow(stored)) return stored;
        if (io.github.fchatcolor.api.util.GradientColor.isGradient(stored)) {
            if (!io.github.fchatcolor.api.util.GradientColor.parseColors(stored).isEmpty()) return stored;
        }
        if (stored != null && !stored.isEmpty()) {
            String perm = getPermissionForStored(stored);
            if (perm == null || perm.isEmpty() || player.isOp() || player.hasPermission("fchatcolor.admin") || player.hasPermission("fchatcolor.*") || player.hasPermission("fchatcolor.color.*") || player.hasPermission(perm)) {
                return stored;
            }
        }
        try {
            java.util.Map<String, String> permMap = context.getSettings().getDefaultPermissionColors();
            if (permMap != null) {
                for (java.util.Map.Entry<String, String> e : permMap.entrySet()) {
                    String perm = e.getKey();
                    String col = e.getValue();
                    if (perm != null && col != null && !perm.isEmpty() && !col.isEmpty() && player.hasPermission(perm)) {
                        String resolved = resolveDefaultColor(col);
                        if (resolved != null && !resolved.isEmpty()) return resolved;
                    }
                }
            }
        } catch (Throwable ignored) {}
        String def = null;
        try {
            def = context.getSettings().getDefaultColor();
        } catch (Throwable ignored) {}
        return resolveDefaultColor(def);
    }

    private String getPermissionForStored(String stored) {
        return io.github.fchatcolor.api.util.CatalogReflector.colorPermission(context.getPlugin(), stored);
    }

    private String resolveDefaultColor(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("disabled") || s.equalsIgnoreCase("off")) return null;
        if (io.github.fchatcolor.api.util.GradientColor.isGradient(s)
                && !io.github.fchatcolor.api.util.GradientColor.parseColors(s).isEmpty()) return io.github.fchatcolor.api.util.GradientColor.encode(io.github.fchatcolor.api.util.GradientColor.parseColors(s));
        String catalogValue = io.github.fchatcolor.api.util.CatalogReflector.colorValue(context.getPlugin(), s);
        if (catalogValue != null) return catalogValue;
        Integer rgb = HexColor.parseRgb(s);
        if (rgb != null) return HexColor.toHex(rgb);
        String clean = s.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00A7")) clean = clean.substring(1);
        if (HexColor.isLegacyCode(clean)) return HexColor.normalizeLegacyCode(clean);
        if (RainbowColor.isRainbow(s)) return "rainbow";
        return s;
    }

    private Component applyRainbow(Component message, boolean allowHex) {
        // Build rainbow with modifiers applied per char
        Set<TextDecoration> decos = getModifierDecorationsForRainbow();
        return Component.empty().children(rainbowChildren(message, allowHex, new int[]{0}, decos));
    }

    private Set<TextDecoration> getModifierDecorationsForRainbow() {
        // Use same logic but we need player context; reuse last player's decorations? Instead fetch via context player? For simplicity reuse getModifierDecorations for current player via thread-local? We'll just call getModifierDecorations with a dummy? Better pass player.
        // This method is called inside applyPlayerColor where player is available, but rainbowChildren needs decos.
        // So we will pass decos explicitly; this helper is not used directly.
        return new HashSet<TextDecoration>();
    }

    private List<Component> rainbowChildren(Component component, boolean allowHex, int[] index, Set<TextDecoration> decos) {
        List<Component> out = new ArrayList<Component>();
        // For rainbow we need decorations as well
        Set<TextDecoration> effectiveDecos = decos;
        if (component instanceof net.kyori.adventure.text.TextComponent) {
            net.kyori.adventure.text.TextComponent text =
                    (net.kyori.adventure.text.TextComponent) component;
            String content = text.content();
            if (!content.isEmpty()) {
                for (int i = 0; i < content.length(); i++) {
                    char ch = content.charAt(i);
                    // Preserve existing § codes? For rainbow, override unless char is space? Keep spaces tinted as well for continuous hue
                    TextColor tint = rainbowColor(index[0]++, allowHex);
                    Component c = Component.text(String.valueOf(ch))
                            .style(text.style().color(tint));
                    for (TextDecoration d : effectiveDecos) {
                        c = c.decoration(d, true);
                    }
                    out.add(c);
                }
            }
        } else if (!component.equals(Component.empty())) {
            out.add(component.children(java.util.Collections.<Component>emptyList()));
        }
        for (Component child : component.children()) {
            out.addAll(rainbowChildren(child, allowHex, index, effectiveDecos));
        }
        return out;
    }

    // Overload to maintain compatibility: if called without decos, fetch from player via context? But we will override applyRainbow to include decos
    private List<Component> rainbowChildren(Component component, boolean allowHex, int[] index) {
        return rainbowChildren(component, allowHex, index, new HashSet<TextDecoration>());
    }

    private TextColor rainbowColor(int index, boolean allowHex) {
        return TextColor.color(allowHex
                ? RainbowColor.rgbAt(index)
                : RainbowColor.legacyRgbAt(index));
    }

    // Fix applyRainbow to include modifiers: we need player reference. We'll override logic in applyPlayerColor to pass decos
    private Component applyRainbowWithMods(Component message, boolean allowHex, Set<TextDecoration> decos) {
        return Component.empty().children(rainbowChildren(message, allowHex, new int[]{0}, decos));
    }

    private Component applyGradientWithMods(Component message, java.util.List<String> palette, Set<TextDecoration> decos, ModernColorService colorService, Player player) {
        boolean allowHex = player.hasPermission(ColorSelector.PERMISO_HEX);
        java.util.List<TextColor> tints = new java.util.ArrayList<TextColor>(palette.size());
        for (String entry : palette) {
            TextColor tc = colorService.resolveTextColor(entry, allowHex);
            if (tc == null) {
                try { tc = TextColor.color(HexColor.parseRgb(entry)); } catch (Throwable ignored) { tc = null; }
            }
            tints.add(tc);
        }
        return Component.empty().children(gradientChildren(message, palette, tints, new int[]{0}, decos));
    }

    private List<Component> gradientChildren(Component component, java.util.List<String> palette, java.util.List<TextColor> tints, int[] index, Set<TextDecoration> decos) {
        List<Component> out = new ArrayList<Component>();
        if (component instanceof net.kyori.adventure.text.TextComponent) {
            net.kyori.adventure.text.TextComponent text =
                    (net.kyori.adventure.text.TextComponent) component;
            String content = text.content();
            if (!content.isEmpty()) {
                for (int i = 0; i < content.length(); i++) {
                    char ch = content.charAt(i);
                    TextColor tint = tints.get(Math.floorMod(index[0]++, tints.size()));
                    Component c;
                    if (tint == null) {
                        c = Component.text(String.valueOf(ch)).style(text.style());
                    } else {
                        c = Component.text(String.valueOf(ch)).style(text.style().color(tint));
                    }
                    for (TextDecoration d : decos) {
                        c = c.decoration(d, true);
                    }
                    out.add(c);
                }
            }
        } else if (!component.equals(Component.empty())) {
            out.add(component.children(java.util.Collections.<Component>emptyList()));
        }
        for (Component child : component.children()) {
            out.addAll(gradientChildren(child, palette, tints, index, decos));
        }
        return out;
    }

    private String rainbowWithModifiersLegacy(String text, boolean hex, String modifiers) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() * 8);
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00A7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (hex) out.append(RainbowColor.sectionHexAt(visible));
            else out.append('\u00A7').append(RainbowColor.legacyAt(visible));
            if (modifiers != null && !modifiers.isEmpty()) out.append(modifiers);
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    // Patch applyPlayerColor rainbow path to use decos
    // We already have logic but need to wire correctly: modify applyPlayerColor to call applyRainbowWithMods

}
