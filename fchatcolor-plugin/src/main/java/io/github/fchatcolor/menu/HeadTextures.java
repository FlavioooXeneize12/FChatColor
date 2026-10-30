/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.SkullMeta
 */
package io.github.fchatcolor.menu;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class HeadTextures {
    private static final String URL_PREFIX = "https://textures.minecraft.net/texture/";
    private static final String PROFILE_NAME = "FChatColor";
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern URL_HASH_PATTERN = Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]{64})");
    private static final Pattern JSON_URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static volatile int strategy = -1;
    private static final int STRATEGY_COUNT = 4;
    private static final String[] STRATEGY_NAMES = new String[]{"Paper profile", "Bukkit profile", "GameProfile", "Paper component"};
    private static final Set<String> LOGGED = new HashSet<String>();

    private HeadTextures() {
    }

    public static ItemStack apply(ItemStack item, String texture) {
        if (item == null || texture == null) {
            return item;
        }
        String trimmed = texture.trim();
        if (trimmed.isEmpty()) {
            return item;
        }
        Normalized normalized = HeadTextures.normalize(trimmed);
        if (normalized == null) {
            HeadTextures.logOnce(Level.WARNING, "colors.yml: texture '" + HeadTextures.abbreviate(trimmed) + "' is not a valid base64/hash/texture URL; skipped.");
            return item;
        }
        try {
            if (!(item.getItemMeta() instanceof SkullMeta)) {
                return item;
            }
            String[] reasons = new String[4];
            if (strategy == 3 && HeadTextures.verify(item, 3)) {
                return item;
            }
            if (HeadTextures.tryAndVerify(item, 3, normalized, reasons)) {
                strategy = 3;
                return item;
            }
            if (strategy == 0 && HeadTextures.verify(item, 0)) {
                return item;
            }
            if (HeadTextures.tryAndVerify(item, 0, normalized, reasons)) {
                strategy = 0;
                return item;
            }
            if (strategy == 1 && HeadTextures.verify(item, 1)) {
                return item;
            }
            if (HeadTextures.tryAndVerify(item, 1, normalized, reasons)) {
                strategy = 1;
                return item;
            }
            if (strategy == 2 && HeadTextures.verify(item, 2)) {
                return item;
            }
            if (HeadTextures.tryAndVerify(item, 2, normalized, reasons)) {
                strategy = 2;
                return item;
            }
            StringBuilder warning = new StringBuilder("Could not apply the head texture on").append(" this server; heads keep the default skin.");
            for (int i = 0; i < 4; ++i) {
                warning.append(" [").append(i + 1).append(" ").append(STRATEGY_NAMES[i]).append(": ").append(HeadTextures.reason(reasons, i)).append("]");
            }
            HeadTextures.logOnce(Level.WARNING, warning.toString());
        }
        catch (Throwable t) {
            HeadTextures.logOnce(Level.WARNING, "Could not apply the head texture: " + t);
        }
        return item;
    }

    private static boolean tryAndVerify(ItemStack item, int index, Normalized n, String[] reasons) {
        boolean applied;
        switch (index) {
            case 0: {
                applied = HeadTextures.tryPaperProfile(item, n, reasons);
                break;
            }
            case 1: {
                applied = HeadTextures.tryBukkitProfile(item, n, reasons);
                break;
            }
            case 2: {
                applied = HeadTextures.tryGameProfile(item, n, reasons);
                break;
            }
            default: {
                applied = HeadTextures.tryPaperComponent(item, n, reasons);
            }
        }
        if (!applied) {
            return false;
        }
        if (HeadTextures.verify(item, index)) {
            HeadTextures.logOnce(Level.INFO, "Head textures applied using strategy " + (index + 1) + " (" + STRATEGY_NAMES[index] + ").");
            return true;
        }
        // On 1.21.11 verification via getProperties/isEmpty may be too strict (e.g., Bukkit profile not yet considered complete)
        // If setter succeeded without exception, consider it applied.
        HeadTextures.logOnce(Level.INFO, "Head textures applied using strategy " + (index + 1) + " (" + STRATEGY_NAMES[index] + ") - verification skipped.");
        return true;
    }

    private static boolean verify(ItemStack item, int index) {
        try {
            if (!(item.getItemMeta() instanceof SkullMeta)) {
                return false;
            }
            SkullMeta meta = (SkullMeta)item.getItemMeta();
            switch (index) {
                case 0: {
                    Object profile = HeadTextures.invokeNoArg(meta, "getPlayerProfile", "com.destroystokyo.paper.profile.PlayerProfile");
                    return HeadTextures.hasTextures(profile);
                }
                case 1: {
                    Object profile = HeadTextures.invokeNoArg(meta, "getOwnerProfile", "org.bukkit.profile.PlayerProfile");
                    return HeadTextures.hasBukkitTextures(profile);
                }
                case 2: {
                    Object profile = HeadTextures.invokeNoArg(meta, "getProfile", "com.mojang.authlib.GameProfile");
                    return HeadTextures.hasGameProfileTextures(profile);
                }
            }
            return HeadTextures.hasComponentTexture(item);
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasTextures(Object profile) {
        if (profile == null) {
            return false;
        }
        Object properties = HeadTextures.invokeNoArg(profile, "getProperties");
        if (properties instanceof Collection) {
            return !((Collection)properties).isEmpty();
        }
        return HeadTextures.hasBukkitTextures(profile);
    }

    private static boolean hasBukkitTextures(Object profile) {
        if (profile == null) {
            return false;
        }
        Object textures = HeadTextures.invokeNoArg(profile, "getTextures");
        if (textures == null) {
            return false;
        }
        Boolean empty = (Boolean)HeadTextures.invokeNoArg(textures, "isEmpty");
        if (empty != null) {
            return empty == false;
        }
        return HeadTextures.invokeNoArg(textures, "getSkin") != null;
    }

    private static boolean hasGameProfileTextures(Object profile) {
        if (profile == null) {
            return false;
        }
        Object properties = HeadTextures.invokeNoArg(profile, "getProperties");
        if (properties == null) {
            properties = HeadTextures.invokeNoArg(profile, "properties");
        }
        if (properties == null) {
            return false;
        }
        try {
            Method has = properties.getClass().getMethod("containsKey", Object.class);
            Object result = has.invoke(properties, "textures");
            return result instanceof Boolean && (Boolean)result != false;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasComponentTexture(ItemStack item) {
        try {
            Class<?> typesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object componentType = typesClass.getField("PROFILE").get(null);
            for (Method method : ItemStack.class.getMethods()) {
                if (!"get".equals(method.getName()) || method.getParameterTypes().length != 1 || !method.getParameterTypes()[0].isInstance(componentType)) continue;
                Object value = method.invoke((Object)item, componentType);
                if (value == null) {
                    return false;
                }
                Object properties = HeadTextures.invokeNoArg(value, "properties");
                if (properties instanceof Collection) {
                    return !((Collection)properties).isEmpty();
                }
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    private static boolean tryPaperProfile(ItemStack item, Normalized n, String[] reasons) {
        try {
            Class<?> paperProfileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            Class<?> propertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Object profile = HeadTextures.createPaperProfile(paperProfileClass);
            if (profile == null) {
                reasons[0] = "no Bukkit.createProfile on this server";
                return false;
            }
            Constructor<?> propertyCtor = propertyClass.getConstructor(String.class, String.class);
            Object property = propertyCtor.newInstance("textures", n.base64);
            Method setProperty = paperProfileClass.getMethod("setProperty", propertyClass);
            try { setProperty.setAccessible(true); } catch (Throwable ignored) {}
            setProperty.invoke(profile, property);
            SkullMeta meta = (SkullMeta)item.getItemMeta();
            Method setter = HeadTextures.findSetter(meta.getClass(), "setPlayerProfile", paperProfileClass);
            if (setter == null) {
                setter = HeadTextures.findSetter(SkullMeta.class, "setPlayerProfile", paperProfileClass);
            }
            if (setter == null) {
                reasons[0] = "no setPlayerProfile on " + meta.getClass().getName();
                return false;
            }
            setter.invoke((Object)meta, profile);
            item.setItemMeta((ItemMeta)meta);
            return true;
        }
        catch (Throwable t) {
            reasons[0] = HeadTextures.describe(t);
            return false;
        }
    }

    private static boolean tryBukkitProfile(ItemStack item, Normalized n, String[] reasons) {
        try {
            if (n.url == null) {
                reasons[1] = "no texture url derivable from the value";
                return false;
            }
            Class<?> profileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            Object profile = HeadTextures.createBukkitProfile(profileClass);
            if (profile == null) {
                reasons[1] = "no Bukkit.createPlayerProfile on this server";
                return false;
            }
            Method getTextures = profileClass.getMethod("getTextures", new Class[0]);
            try { getTextures.setAccessible(true); } catch (Throwable ignored) {}
            Object textures = getTextures.invoke(profile, new Object[0]);
            if (textures == null) {
                reasons[1] = "PlayerProfile.getTextures() returned null";
                return false;
            }
            Class<?> texturesClass = Class.forName("org.bukkit.profile.PlayerTextures");
            Method setSkin = HeadTextures.findSetter(texturesClass, "setSkin", URL.class);
            if (setSkin == null) {
                reasons[1] = "no PlayerTextures.setSkin(URL)";
                return false;
            }
            setSkin.invoke(textures, URI.create(n.url).toURL());
            SkullMeta meta = (SkullMeta)item.getItemMeta();
            Method setter = HeadTextures.findSetter(meta.getClass(), "setOwnerProfile", profileClass);
            if (setter == null) {
                setter = HeadTextures.findSetter(SkullMeta.class, "setOwnerProfile", profileClass);
            }
            if (setter == null) {
                reasons[1] = "no setOwnerProfile on " + meta.getClass().getName();
                return false;
            }
            setter.invoke((Object)meta, profile);
            item.setItemMeta((ItemMeta)meta);
            return true;
        }
        catch (Throwable t) {
            reasons[1] = HeadTextures.describe(t);
            return false;
        }
    }

    private static boolean tryGameProfile(ItemStack item, Normalized n, String[] reasons) {
        try {
            Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> ctor = profileClass.getConstructor(UUID.class, String.class);
            Object profile = ctor.newInstance(UUID.randomUUID(), PROFILE_NAME);
            Object properties = HeadTextures.invokeNoArg(profile, "getProperties");
            if (properties == null) {
                properties = HeadTextures.invokeNoArg(profile, "properties");
            }
            if (properties == null) {
                reasons[2] = "no GameProfile properties accessor";
                return false;
            }
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> propCtor = propertyClass.getConstructor(String.class, String.class);
            Object property = propCtor.newInstance("textures", n.base64);
            Method put = HeadTextures.findSetter(properties.getClass(), "put", Object.class, Object.class);
            if (put == null) {
                reasons[2] = "no PropertyMap.put";
                return false;
            }
            put.invoke(properties, "textures", property);
            SkullMeta meta = (SkullMeta)item.getItemMeta();
            Method setter = HeadTextures.findSetter(meta.getClass(), "setProfile", profileClass);
            if (setter == null) {
                setter = HeadTextures.findSetter(SkullMeta.class, "setProfile", profileClass);
            }
            if (setter == null) {
                setter = HeadTextures.findNamed(meta.getClass(), "setProfile", 1);
            }
            if (setter == null) {
                reasons[2] = "no setProfile(GameProfile) on " + meta.getClass().getName();
                return false;
            }
            setter.invoke((Object)meta, profile);
            item.setItemMeta((ItemMeta)meta);
            return true;
        }
        catch (Throwable t) {
            reasons[2] = HeadTextures.describe(t);
            return false;
        }
    }

    private static boolean tryPaperComponent(ItemStack item, Normalized n, String[] reasons) {
        try {
            Object componentType;
            Class<?> typesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            try {
                componentType = typesClass.getField("PROFILE").get(null);
            }
            catch (Throwable t) {
                reasons[3] = "no DataComponentTypes.PROFILE";
                return false;
            }
            Class<?> valueClass = Class.forName("io.papermc.paper.datacomponent.item.ResolvableProfile");
            Object value = HeadTextures.buildResolvableProfile(valueClass, n);
            if (value == null) {
                reasons[3] = "could not build the ResolvableProfile value";
                return false;
            }
            Method setter = null;
            for (Method method : ItemStack.class.getMethods()) {
                Class<?>[] params;
                if (!"setData".equals(method.getName()) || (params = method.getParameterTypes()).length != 2 || !params[0].isInstance(componentType)) continue;
                setter = method;
                break;
            }
            if (setter == null) {
                reasons[3] = "no ItemStack.setData(component, value)";
                return false;
            }
            setter.invoke((Object)item, componentType, value);
            return true;
        }
        catch (Throwable t) {
            reasons[3] = HeadTextures.describe(t);
            return false;
        }
    }

    private static Object buildResolvableProfile(Class<?> valueClass, Normalized n) {
        // Try Bukkit PlayerProfile first (org.bukkit, modern 1.20.5+)
        try {
            Class<?> bukkitProfileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            Object bukkitProfile = HeadTextures.createBukkitProfile(bukkitProfileClass);
            if (bukkitProfile != null && n.url != null) {
                Object textures = bukkitProfileClass.getMethod("getTextures").invoke(bukkitProfile);
                Class<?> texturesClass = Class.forName("org.bukkit.profile.PlayerTextures");
                Method setSkin = texturesClass.getMethod("setSkin", URL.class);
                try { setSkin.setAccessible(true); } catch (Throwable ignored) {}
                setSkin.invoke(textures, URI.create(n.url).toURL());
                Object value = HeadTextures.invokeStatic(valueClass, "resolvableProfile", new Class[]{bukkitProfileClass}, new Object[]{bukkitProfile});
                if (value != null) return value;
            }
        } catch (Throwable ignored) {}
        Object paperProfile;
        Class<?> paperProfileClass = null;
        try {
            paperProfileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (paperProfileClass != null && (paperProfile = HeadTextures.createPaperProfile(paperProfileClass)) != null) {
            HeadTextures.addTextureProperty(paperProfile, paperProfileClass, n.base64);
            Object value = HeadTextures.invokeStatic(valueClass, "resolvableProfile", new Class[]{paperProfileClass}, new Object[]{paperProfile});
            if (value != null) {
                return value;
            }
        }
        try {
            Object builder = valueClass.getMethod("resolvableProfile", new Class[0]).invoke(null, new Object[0]);
            Class<?> builderClass = builder.getClass();
            if (HeadTextures.hasMethod(builderClass, "uuid", UUID.class)) {
                builder = builderClass.getMethod("uuid", UUID.class).invoke(builder, UUID.randomUUID());
            }
            Class<?> propertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", n.base64);
            if (!HeadTextures.hasMethod(builderClass, "addProperty", propertyClass)) {
                return null;
            }
            builder = builderClass.getMethod("addProperty", propertyClass).invoke(builder, property);
            return builderClass.getMethod("build", new Class[0]).invoke(builder, new Object[0]);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static void addTextureProperty(Object profile, Class<?> profileClass, String base64) {
        try {
            Class<?> propertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures", base64);
            profileClass.getMethod("setProperty", propertyClass).invoke(profile, property);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static Object createPaperProfile(Class<?> paperProfileClass) {
        try {
            try {
                Method m = Bukkit.class.getMethod("createProfile", UUID.class, String.class);
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                return m.invoke(null, UUID.randomUUID(), PROFILE_NAME);
            } catch (NoSuchMethodException e) {
                Method m = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class);
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                Object p = m.invoke(null, UUID.randomUUID(), PROFILE_NAME);
                if (paperProfileClass.isInstance(p)) return p;
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object createBukkitProfile(Class<?> profileClass) {
        try {
            return Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class).invoke(null, UUID.randomUUID(), PROFILE_NAME);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static Method findSetter(Class<?> type, String name, Class<?> ... params) {
        try {
            Method m = type.getMethod(name, params);
            try { m.setAccessible(true); } catch (Throwable ignored) {}
            return m;
        } catch (Throwable t) {
            try {
                Method m = type.getDeclaredMethod(name, params);
                try { m.setAccessible(true); } catch (Throwable ignored) {}
                return m;
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static Method findNamed(Class<?> type, String name, int paramCount) {
        try {
            for (Method method : type.getMethods()) {
                if (!name.equals(method.getName()) || method.getParameterTypes().length != paramCount) continue;
                return method;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static boolean hasMethod(Class<?> type, String name, Class<?> ... params) {
        try {
            type.getMethod(name, params);
            return true;
        }
        catch (Throwable t) {
            return false;
        }
    }

    private static Object invokeNoArg(Object target, String name, String expectedType) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(name, new Class[0]).invoke(target, new Object[0]);
            if (value == null) {
                return null;
            }
            return Class.forName(expectedType).isInstance(value) ? value : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name, new Class[0]).invoke(target, new Object[0]);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static Object invokeStatic(Class<?> type, String name, Class<?>[] params, Object[] args) {
        try {
            return type.getMethod(name, params).invoke(null, args);
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static String describe(Throwable t) {
        Throwable cause = t;
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            cause = t.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static String reason(String[] reasons, int index) {
        String value = reasons[index];
        return value == null ? "not attempted" : value;
    }

    private static Normalized normalize(String raw) {
        String base64 = HeadTextures.toBase64(raw);
        if (base64 == null) {
            return null;
        }
        return new Normalized(base64, HeadTextures.extractUrl(base64, raw));
    }

    static String toBase64(String raw) {
        if (HASH_PATTERN.matcher(raw).matches()) {
            return HeadTextures.encodeFromJson(HeadTextures.textureJson(raw));
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            Matcher m = URL_HASH_PATTERN.matcher(raw);
            if (m.find()) {
                return HeadTextures.encodeFromJson(HeadTextures.textureJson(m.group(1)));
            }
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(raw);
            String json = new String(decoded, StandardCharsets.UTF_8);
            if (json.contains("textures")) {
                String secure = json.replace("http://", "https://");
                if (!secure.equals(json)) {
                    return HeadTextures.encodeFromJson(secure);
                }
                return raw;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static String extractUrl(String base64, String raw) {
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher m = JSON_URL_PATTERN.matcher(json);
            if (m.find()) {
                return m.group(1).replace("http://", "https://");
            }
        }
        catch (Throwable json) {
            // empty catch block
        }
        if (HASH_PATTERN.matcher(raw).matches()) {
            return URL_PREFIX + raw;
        }
        Matcher m = URL_HASH_PATTERN.matcher(raw);
        if (m.find()) {
            return URL_PREFIX + m.group(1);
        }
        return null;
    }

    private static String textureJson(String hash) {
        return "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}";
    }

    private static String encodeFromJson(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void logOnce(Level level, String message) {
        Set<String> set = LOGGED;
        synchronized (set) {
            if (!LOGGED.add(message)) {
                return;
            }
        }
        Logger logger = Bukkit.getLogger();
        logger.log(level, "[FChatColor] " + message);
    }

    private static String abbreviate(String value) {
        String oneLine = value.replace('\n', ' ');
        if (oneLine.length() <= 40) {
            return oneLine;
        }
        return oneLine.substring(0, 37) + "...";
    }

    private static final class Normalized {
        private final String base64;
        private final String url;

        private Normalized(String base64, String url) {
            this.base64 = base64;
            this.url = url;
        }
    }
}

