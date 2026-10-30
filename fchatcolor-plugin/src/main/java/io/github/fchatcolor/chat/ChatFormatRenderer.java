package io.github.fchatcolor.chat;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.util.PapiResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Renderiza formatos de chat que contienen simultáneamente tags de MiniMessage
 * y placeholders de PlaceholderAPI.
 *
 * <p>Orden de procesamiento (crítico por compatibilidad String vs Component):</p>
 * <ol>
 *   <li>Formato literal {@code String} con MiniMessage + {@code %placeholders%} (ej. {@code "<gray>[%luckperms_prefix%] %player_name% » %fchatcolor_color%%message%"})</li>
 *   <li>{@link PapiResolver} sobre el {@code String} literal (PAPI es String - String, deja intactos los tags {@code <...>})</li>
 *   <li>{@link ColorService} en modo {@link ColorMode#MINIMESSAGE} (si está disponible) deserializa el resultado a Component.
 *       En legacy (1.8) el mismo pipeline traduce los tags a códigos {@code §}.</li>
 * </ol>
 *
 * <p>No rompe comportamiento existente: si PlaceholderAPI no está, se salta el paso 2;
 * si MiniMessage no está (legacy), el servicio hace fallback a legacy/hex.
 * Evita conversiones extra: PAPI trabaja sobre String literal, MiniMessage sobre el String ya resuelto.</p>
 */
public final class ChatFormatRenderer {

    private ChatFormatRenderer() {}

    /** Renderiza a String legacy/hex (seguro en 1.8 y moderno, sin referencia dura a Adventure). */
    public static String renderToString(PluginContext context, String literalFormat, OfflinePlayer player) {
        if (literalFormat == null) return "";
        String afterPapi = PapiResolver.resolve(player, literalFormat);
        ColorService service = context != null ? context.getColorService() : null;
        if (service != null) {
            ColorMode mode = service.supports(ColorMode.MINIMESSAGE) ? ColorMode.MINIMESSAGE
                    : service.supports(ColorMode.HEX) ? ColorMode.HEX : ColorMode.LEGACY;
            return service.translate(afterPapi, mode);
        }
        // Fallback sin servicio
        try {
            Class<?> cc = Class.forName("org.bukkit.ChatColor");
            Method m = cc.getMethod("translateAlternateColorCodes", char.class, String.class);
            return (String) m.invoke(null, '&', afterPapi);
        } catch (Throwable t) {
            return afterPapi;
        }
    }

    /** Renderiza a Component (solo en servidores modernos con Adventure). Usa reflection para no romper 1.8. */
    public static Object renderToComponent(PluginContext context, String literalFormat, OfflinePlayer player) {
        if (literalFormat == null) {
            try { return Class.forName("net.kyori.adventure.text.Component").getMethod("empty").invoke(null); } catch (Throwable t) { return null; }
        }
        String afterPapi = PapiResolver.resolve(player, literalFormat);
        ColorService service = context != null ? context.getColorService() : null;
        if (service != null) {
            ColorMode mode = service.supports(ColorMode.MINIMESSAGE) ? ColorMode.MINIMESSAGE
                    : service.supports(ColorMode.HEX) ? ColorMode.HEX : ColorMode.LEGACY;
            // Intentar translateComponent via reflection (ModernColorService)
            try {
                Method m = service.getClass().getMethod("translateComponent", String.class, ColorMode.class);
                return m.invoke(service, afterPapi, mode);
            } catch (NoSuchMethodException ns) {
                // Legacy service solo tiene translate(String) -> deserializar a Component si Adventure existe
                String legacy = service.translate(afterPapi, mode);
                try {
                    Class<?> ser = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                    Object instance = ser.getMethod("legacySection").invoke(null);
                    Method deser = instance.getClass().getMethod("deserialize", String.class);
                    return deser.invoke(instance, legacy);
                } catch (Throwable t2) {
                    return legacy;
                }
            } catch (Throwable t) {
                return afterPapi;
            }
        }
        // Sin servicio: intentar LegacyComponentSerializer
        try {
            Class<?> ser = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object instance = ser.getMethod("legacyAmpersand").invoke(null);
            Method deser = instance.getClass().getMethod("deserialize", String.class);
            return deser.invoke(instance, afterPapi);
        } catch (Throwable t) {
            return afterPapi;
        }
    }

    public static String renderForPlayerToString(PluginContext context, Player player, String literalFormat) {
        return renderToString(context, literalFormat, (OfflinePlayer) player);
    }

    /** Variante String para legacy (AsyncPlayerChatEvent): formato literal + mensaje ya coloreado con §. */
    public static String renderWithMessageString(PluginContext context, String literalFormat, OfflinePlayer player, String coloredMessage) {
        if (literalFormat == null || literalFormat.isEmpty()) return coloredMessage;
        int idx = literalFormat.toLowerCase(java.util.Locale.ROOT).indexOf("%message%");
        if (idx < 0) return renderToString(context, literalFormat, player);
        String before = literalFormat.substring(0, idx);
        String after = literalFormat.substring(idx + "%message%".length());
        String pre = renderToString(context, before, player);
        String suf = renderToString(context, after, player);
        return pre + (coloredMessage != null ? coloredMessage : "") + suf;
    }

    /** Renderiza formato con <tags> + %placeholders% + %message% (message es Component ya coloreado). Mantiene colores del mensaje. */
    public static Object renderWithMessage(PluginContext context, String literalFormat, OfflinePlayer player, Object messageComponent) {
        if (literalFormat == null || literalFormat.isEmpty()) return messageComponent;
        // Split literal on %message% (case-insensitive)
        int idx = literalFormat.toLowerCase(java.util.Locale.ROOT).indexOf("%message%");
        if (idx < 0) {
            // No %message% token: render whole format as PAPI+MiniMessage
            return renderToComponent(context, literalFormat, player);
        }
        String before = literalFormat.substring(0, idx);
        String after = literalFormat.substring(idx + "%message%".length());
        Object prefix = renderToComponent(context, before, player);
        Object suffix = renderToComponent(context, after, player);
        // Combine prefix + message + suffix via reflection (Component.append)
        try {
            Class<?> compClass = Class.forName("net.kyori.adventure.text.Component");
            java.lang.reflect.Method append = compClass.getMethod("append", compClass);
            Object result = prefix;
            result = append.invoke(result, messageComponent);
            result = append.invoke(result, suffix);
            return result;
        } catch (Throwable t) {
            // Fallback: legacy string concat
            String preStr = renderToString(context, before, player);
            String sufStr = renderToString(context, after, player);
            String msgStr = "";
            try {
                Class<?> plain = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
                Object ser = plain.getMethod("plainText").invoke(null);
                java.lang.reflect.Method serM = ser.getClass().getMethod("serialize", Class.forName("net.kyori.adventure.text.Component"));
                msgStr = (String) serM.invoke(ser, messageComponent);
            } catch (Throwable ignored) {}
            return renderToComponent(context, preStr + msgStr + sufStr, player);
        }
    }
}
