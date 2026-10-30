package io.github.fchatcolor.api.util;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;

/**
 * Utilidad central para formatos de chat con MiniMessage + PlaceholderAPI.
 * Orden: literal(String con <tags> + %placeholders%) -> PAPI resolve(String) -> ColorService translate(MINIMESSAGE).
 * Compatible con legacy (≥1.8) y moderno (1.21) sin capas extra.
 */
public final class ChatFormatUtil {

    private ChatFormatUtil() {}

    public static String renderToString(PluginContext ctx, String literal, OfflinePlayer player) {
        if (literal == null) return "";
        String afterPapi = PapiResolver.resolve(player, literal);
        ColorService service = ctx != null ? ctx.getColorService() : null;
        if (service != null) {
            ColorMode mode = service.supports(ColorMode.MINIMESSAGE) ? ColorMode.MINIMESSAGE
                    : service.supports(ColorMode.HEX) ? ColorMode.HEX : ColorMode.LEGACY;
            return service.translate(afterPapi, mode);
        }
        try {
            Class<?> cc = Class.forName("org.bukkit.ChatColor");
            Method m = cc.getMethod("translateAlternateColorCodes", char.class, String.class);
            return (String) m.invoke(null, '&', afterPapi);
        } catch (Throwable t) { return afterPapi; }
    }

    public static Object renderToComponent(PluginContext ctx, String literal, OfflinePlayer player) {
        if (literal == null) {
            try { return Class.forName("net.kyori.adventure.text.Component").getMethod("empty").invoke(null); } catch (Throwable t) { return null; }
        }
        String afterPapi = PapiResolver.resolve(player, literal);
        ColorService service = ctx != null ? ctx.getColorService() : null;
        if (service != null) {
            ColorMode mode = service.supports(ColorMode.MINIMESSAGE) ? ColorMode.MINIMESSAGE
                    : service.supports(ColorMode.HEX) ? ColorMode.HEX : ColorMode.LEGACY;
            try {
                Method m = service.getClass().getMethod("translateComponent", String.class, ColorMode.class);
                return m.invoke(service, afterPapi, mode);
            } catch (NoSuchMethodException ns) {
                String legacy = service.translate(afterPapi, mode);
                try {
                    Class<?> ser = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                    Object inst = ser.getMethod("legacySection").invoke(null);
                    return inst.getClass().getMethod("deserialize", String.class).invoke(inst, legacy);
                } catch (Throwable t2) { return legacy; }
            } catch (Throwable t) { return afterPapi; }
        }
        try {
            Class<?> ser = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object inst = ser.getMethod("legacyAmpersand").invoke(null);
            return inst.getClass().getMethod("deserialize", String.class).invoke(inst, afterPapi);
        } catch (Throwable t) { return afterPapi; }
    }

    public static Object renderWithMessage(PluginContext ctx, String literalFormat, OfflinePlayer player, Object messageComponent) {
        if (literalFormat == null || literalFormat.isEmpty()) return messageComponent;
        int idx = literalFormat.toLowerCase(java.util.Locale.ROOT).indexOf("%message%");
        if (idx < 0) return renderToComponent(ctx, literalFormat, player);
        String before = literalFormat.substring(0, idx);
        String after = literalFormat.substring(idx + "%message%".length());
        Object pre = renderToComponent(ctx, before, player);
        Object suf = renderToComponent(ctx, after, player);
        try {
            Class<?> comp = Class.forName("net.kyori.adventure.text.Component");
            java.lang.reflect.Method append = comp.getMethod("append", comp);
            Object res = append.invoke(pre, messageComponent);
            res = append.invoke(res, suf);
            return res;
        } catch (Throwable t) {
            String preStr = renderToString(ctx, before, player);
            String sufStr = renderToString(ctx, after, player);
            String msgStr = "";
            try {
                Class<?> plain = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
                Object ser = plain.getMethod("plainText").invoke(null);
                msgStr = (String) ser.getClass().getMethod("serialize", Class.forName("net.kyori.adventure.text.Component")).invoke(ser, messageComponent);
            } catch (Throwable ignored) {}
            return renderToComponent(ctx, preStr + msgStr + sufStr, player);
        }
    }
}
