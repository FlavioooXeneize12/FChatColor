package io.github.fchatcolor.api.util;

import java.lang.reflect.Method;

/**
 * Cached reflection access to the plugin's {@code ColorCatalog}.
 *
 * <p>The legacy/modern modules cannot reference the plugin classes directly,
 * so they resolved {@code getColors()} / {@code findByValue()} / ... via
 * reflection <b>on every chat message</b> ({@code Class.getMethod} walks the
 * class hierarchy under a lock). This helper resolves each {@link Method}
 * once per class and reuses it, keeping hot-path lookups to a couple of
 * cheap {@code Method.invoke} calls.</p>
 *
 * <p>Only {@link Method} objects are cached (never data): the catalog instance
 * is fetched fresh on every call, so {@code /fchatcolor reload} keeps working.
 * Thread-safe for concurrent chat threads. Java 8 compatible, no Bukkit API.</p>
 */
public final class CatalogReflector {

    private static final Object LOCK = new Object();

    private static volatile Class<?> cachedPluginClass;
    private static volatile Method mGetColors;
    private static volatile Class<?> cachedColorsClass;
    private static volatile Method mFind;
    private static volatile Method mFindByValue;
    private static volatile Method mFindModifier;
    private static volatile Method mFindModifierByValue;
    private static volatile Method mGetPermission;
    private static volatile Method mGetValue;

    private CatalogReflector() {
    }

    private static boolean ensure(Object plugin) {
        if (plugin == null) return false;
        try {
            Class<?> pc = plugin.getClass();
            if (pc == cachedPluginClass && mGetColors != null) return true;
            synchronized (LOCK) {
                if (pc == cachedPluginClass && mGetColors != null) return true;
                Method getColors;
                Object colors;
                try {
                    getColors = pc.getMethod("getColors");
                    colors = getColors.invoke(plugin);
                } catch (Throwable t) {
                    return false;
                }
                if (colors == null) return false;
                Class<?> cc = colors.getClass();
                Method find = methodOrNull(cc, "find", String.class);
                Method findByValue = methodOrNull(cc, "findByValue", String.class);
                Method findModifier = methodOrNull(cc, "findModifier", String.class);
                Method findModifierByValue = methodOrNull(cc, "findModifierByValue", String.class);
                Method getPermission = null;
                Method getValue = null;
                // Resolve entry accessors from any entry the catalog can return
                try {
                    Object sample = null;
                    if (findByValue != null) {
                        try { sample = findByValue.invoke(colors, "rainbow"); } catch (Throwable ignored) {}
                    }
                    if (sample == null && find != null) {
                        try { sample = find.invoke(colors, "rainbow"); } catch (Throwable ignored) {}
                    }
                    if (sample != null) {
                        getPermission = methodOrNull(sample.getClass(), "getPermission");
                        getValue = methodOrNull(sample.getClass(), "getValue");
                    }
                } catch (Throwable ignored) {
                }
                if (find == null && findByValue == null) return false;
                mFind = find;
                mFindByValue = findByValue;
                mFindModifier = findModifier;
                mFindModifierByValue = findModifierByValue;
                mGetPermission = getPermission;
                mGetValue = getValue;
                cachedColorsClass = cc;
                mGetColors = getColors;
                cachedPluginClass = pc;
                return true;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method methodOrNull(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invoke(Method m, Object target, Object... args) {
        if (m == null || target == null) return null;
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object colorsOf(Object plugin) {
        if (!ensure(plugin)) return null;
        return invoke(mGetColors, plugin);
    }

    private static String permissionOf(Object entry) {
        Object p = invoke(mGetPermission, entry);
        return p == null ? null : p.toString();
    }

    /** Permission node for a stored color value (via {@code findByValue}, then {@code find}). May return null. */
    public static String colorPermission(Object plugin, String stored) {
        if (stored == null || stored.isEmpty()) return null;
        Object colors = colorsOf(plugin);
        if (colors == null) return null;
        Object entry = invoke(mFindByValue, colors, stored);
        if (entry == null) entry = invoke(mFind, colors, stored);
        if (entry == null) return null;
        return permissionOf(entry);
    }

    /** Catalog value for a key (via {@code find}). May return null. */
    public static String colorValue(Object plugin, String key) {
        if (key == null || key.isEmpty()) return null;
        Object colors = colorsOf(plugin);
        if (colors == null) return null;
        Object entry = invoke(mFind, colors, key);
        if (entry == null) return null;
        Object v = invoke(mGetValue, entry);
        return v == null ? null : v.toString();
    }

    /** Permission node for a modifier canonical value (e.g. {@code &l}). May return null. */
    public static String modifierPermission(Object plugin, String canon) {
        if (canon == null || canon.isEmpty()) return null;
        Object colors = colorsOf(plugin);
        if (colors == null) return null;
        Object entry = invoke(mFindModifierByValue, colors, canon);
        if (entry == null) entry = invoke(mFindModifier, colors, canon);
        if (entry == null) entry = invoke(mFind, colors, canon);
        if (entry == null) return null;
        return permissionOf(entry);
    }
}
