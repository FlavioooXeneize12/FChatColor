package io.github.fchatcolor.api.util;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LastMessageCache {
    /** Upper bound so big servers can't grow these maps without limit. */
    private static final int MAX_ENTRIES = 5000;
    private static final int TRIM_TO = 2500;

    private static final ConcurrentHashMap<UUID, String> MAP = new ConcurrentHashMap<UUID, String>();
    private static final ConcurrentHashMap<UUID, String> LAST_COLOR = new ConcurrentHashMap<UUID, String>();
    private LastMessageCache() {}
    public static void set(UUID id, String msg) {
        if (id == null) return;
        if (msg == null) MAP.remove(id);
        else {
            MAP.put(id, msg);
            trim(MAP);
        }
    }
    public static String get(UUID id) {
        if (id == null) return null;
        return MAP.get(id);
    }
    public static void setColor(UUID id, String color) {
        if (id == null) return;
        if (color == null) LAST_COLOR.remove(id);
        else {
            LAST_COLOR.put(id, color);
            trim(LAST_COLOR);
        }
    }
    public static String getColor(UUID id) {
        if (id == null) return null;
        return LAST_COLOR.get(id);
    }

    private static void trim(ConcurrentHashMap<UUID, String> map) {
        if (map.size() <= MAX_ENTRIES) return;
        int toRemove = map.size() - TRIM_TO;
        Iterator<UUID> it = map.keySet().iterator();
        while (toRemove-- > 0 && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
