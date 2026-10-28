/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.permissions.Permission
 *  org.bukkit.permissions.PermissionDefault
 *  org.bukkit.plugin.PluginManager
 */
package io.github.fchatcolor.colors;

import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

public final class ColorCatalog {
    private static final String DEFAULT_RESOURCE = "/colors.yml";
    private static final String DEFAULT_CONTENT = "# =====================================================================\n#  FChatColor - Available chat colors (colors.yml)\n# =====================================================================\n# Each entry under \"colors\" is a name players can use with:\n#   /fchatcolor set <name>\n#\n#   name:       display name of the color: may carry MiniMessage tags\n#               (e.g. <white>) or & codes. Returned by the\n#               %fchatcolor_color_name% placeholder and accepted as an\n#               alternative input for /fchatcolor set. When omitted it\n#               defaults to the key.\n#   permission: permission node required to use this color.\n#               Registered with default \"op\": admins can use every\n#               color; grant nodes such as fchatcolor.color.red to\n#               players (fchatcolor.color.* grants them all).\n#   value:      color stored in the database: #RRGGBB (hex) or\n#               a legacy code such as &c or c. Special value \"rainbow\"\n#               makes the color cycle through the spectrum in chat.\n#   material:   item material shown in the /chatcolor menu (default\n#               PLAYER_HEAD). Use any Bukkit material name.\n#   texture:    skin for PLAYER_HEAD items in the menu. Leave empty for\n#               the default head. Accepts a base64 texture string, a\n#               64-char texture hash or a full http(s) textures URL.\n#\n# Colors are NOT limited to the defaults below: add as many entries as\n# you want under \"colors\" (your own key, name, permission and value);\n# every entry is loaded on /fchatcolor reload.\n#\n# /fchatcolor reload reloads this file. Names not listed here still\n# work if typed as #RRGGBB or a legacy code (&9), subject to\n# fchatcolor.chat.hex.\n\ncolors:\n  black:\n    name: '<black>Black'\n    permission: fchatcolor.color.black\n    value: '#000000'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmE1MmQ1NzlhZmUyZmRmN2I4ZWNmYTc0NmNkMDE2MTUwZDk2YmViNzUwMDliYjI3MzNhZGUxNWQ0ODdjNDJhMSJ9fX0=\"\n  dark_blue:\n    name: '<dark_blue>Dark Blue'\n    permission: fchatcolor.color.dark_blue\n    value: '#0000AA'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTExOTRmZTllZGY1ODNjMGViZTdkYzFkMzQzMDliZWVmYjIyOWJiMTViNmE4YzNjN2IwYzc2ZGUyN2M4YjdiZiJ9fX0=\"\n  dark_green:\n    name: '<dark_green>Dark Green'\n    permission: fchatcolor.color.dark_green\n    value: '#00AA00'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWUzOGZhMzEzMWUyZGEwNGA4ZjhkNTA4NzJhODIzMmQ3ZDlkZWEzNDBkMDZjOTA5N2ZmYTNjYzQ4MjA4ZGYxZCJ9fX0=\"\n  dark_aqua:\n    name: '<dark_aqua>Dark Aqua'\n    permission: fchatcolor.color.dark_aqua\n    value: '#00AAAA'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzFmNTcwNTExMzBlODUwODQ4ZThlMzdlNzIxMTBhMTZmMDlkYmRhYjdkOWQ2ZTMzYTlmZWNmZDM0OGQ1YTExMCJ9fX0=\"\n  dark_red:\n    name: '<dark_red>Dark Red'\n    permission: fchatcolor.color.dark_red\n    value: '#AA0000'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjhkNDA5MzUyNzk3NzFhZGM2MzkzNmVkOWM4NDYzYWJkZjVjNWJhNzhkMmU4NmNiMWVjMTBiNGQxZDIyNWZiIn19fQ==\"\n  dark_purple:\n    name: '<dark_purple>Dark Purple'\n    permission: fchatcolor.color.dark_purple\n    value: '#AA00AA'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI4MmU3MmI4ZTQ4MzJlNWExMTRhYjBmYzEyN2M4YWNiODNmMzFmZDRkMjY2ZDA4YjJjYWNjNWI2NDAxYTQwMCJ9fX0=\"\n  gold:\n    name: '<gold>Gold'\n    permission: fchatcolor.color.gold\n    value: '#FFAA00'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjA5MGQwOWUxNzNlZTM0MTM4YzNiMDFiNDhlZTBiZTUzNGJiYjFhY2UwZGRmNWZmOThlNjZmN2IwMjExMzk5NSJ9fX0=\"\n  gray:\n    name: '<gray>Gray'\n    permission: fchatcolor.color.gray\n    value: '#AAAAAA'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWM4ZTBkZGYyNDMyZjQzMzJiODc2OTFiNTk1MmM3Njc5NzYzZWY0ZjI3NWI4NzRlOWJjZWI4ODhlZDViNWI5In19fQ==\"\n  dark_gray:\n    name: '<dark_gray>Dark Gray'\n    permission: fchatcolor.color.dark_gray\n    value: '#555555'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmY5YmI5ZTU2MTI1YzgyMjdiOTRiYmRhOWY2ZTBmODYyOTMxYzIyOTI1NWJhOGYxMjA1ZDEzYzQ0YzFiYjU2MSJ9fX0=\"\n  blue:\n    name: '<blue>Blue'\n    permission: fchatcolor.color.blue\n    value: '#5555FF'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2Y5YzMyMTM4Yzk3NjRjNjM5YWViZDgxOWNkOTE5OTJhZWQwMWJmNDQ4ZjBlNzEwYTAzYWI0NDNhYzQ5MGVlOSJ9fX0=\"\n  green:\n    name: '<green>Green'\n    permission: fchatcolor.color.green\n    value: '#55FF55'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTI1YjhlZWQ1YzU2NWJkNDQwZWM0N2M3OWMyMGQ1Y2YzNzAxNjJiMWQ5YjVkZDMxMDBlZDYyODNmZTAxZDZlIn19fQ==\"\n  aqua:\n    name: '<aqua>Aqua'\n    permission: fchatcolor.color.aqua\n    value: '#55FFFF'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmQ2YThiNDdkYTkyM2I3ZDEwMTQyNDQ3ZmRiZGNmZDFlOGU4MmViNDg0OTY0MjUyYmIzNmRkYjVmNzNiNTFjMiJ9fX0=\"\n  red:\n    name: '<red>Red'\n    permission: fchatcolor.color.red\n    value: '#FF5555'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjNmNzliMjA3ZDYxZTEyMjUyM2I4M2Q2MTUwOGQ5OWNmYTA3OWQ0NWJmMjNkZjJhOWE1MTI3ZjkwNzFkNGIwMCJ9fX0=\"\n  light_purple:\n    name: '<light_purple>Light Purple'\n    permission: fchatcolor.color.light_purple\n    value: '#FF55FF'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGNmMjgzNTE4MGNiZmVjM2IzMTdkNmE0NzQ5MWE3NGFlNzE0MzViYTE2OWE1NzkyNWI5MDk2ZWEyZjljNjFiNiJ9fX0=\"\n  yellow:\n    name: '<yellow>Yellow'\n    permission: fchatcolor.color.yellow\n    value: '#FFFF55'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTg5YjkzZmQ2MTZlZDM2NzBjY2Y2NDdhMGY5MzgwMzk4YzBkNDYxNTYzNGYyZGVmZjQ2YzZlZGJkYzcxMjg4NSJ9fX0=\"\n  white:\n    name: '<white>White'\n    permission: fchatcolor.color.white\n    value: '#FFFFFF'\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDM1MzE3YTk1NjcwM2Y5MjllZmQyMDEzNTY1Njc1ZmVjY2I2MGJjY2QxYzZlNWE4YWQ0YjkxNTUzOWI3YTU0ZiJ9fX0=\"\n  rainbow:\n    name: '<rainbow>Rainbow'\n    permission: fchatcolor.color.rainbow\n    value: rainbow\n    material: \"PLAYER_HEAD\"\n    texture: \"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjEwODY3YTgxNzdkMjMyZjU2YTM5ZDk4MjE5MTRjNTA4OWU3NjQ2YmUwN2NjMWNiOTRmNzk3MGM3YmMwN2JjMiJ9fX0=\"\n";
    public static final String WILDCARD_PERMISSION = "fchatcolor.color.*";
    public static final String DEFAULT_MATERIAL = "PLAYER_HEAD";
    private final Logger logger;
    private final File file;
    private Map<String, Entry> byKey = Collections.emptyMap();
    private Map<String, Entry> byDisplayName = Collections.emptyMap();
    private List<Entry> entries = Collections.emptyList();

    public ColorCatalog(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void reload() {
        if (!this.file.exists()) {
            this.writeDefault();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection section = yaml.getConfigurationSection("colors");
        LinkedHashMap<String, Entry> index = new LinkedHashMap<String, Entry>();
        LinkedHashMap<String, Entry> displayIndex = new LinkedHashMap<String, Entry>();
        ArrayList<Entry> list = new ArrayList<Entry>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String canonical;
                ConfigurationSection color = section.getConfigurationSection(key);
                if (color == null) continue;
                String name = key.trim();
                String permission = color.getString("permission", "").trim();
                String rawValue = color.getString("value", "").trim();
                String displayName = color.getString("name", "").trim();
                String material = color.getString("material", "").trim();
                String texture = color.getString("texture", "").trim();
                if (displayName.isEmpty()) {
                    displayName = name;
                }
                if ((canonical = ColorCatalog.canonicalize(rawValue)) == null) {
                    this.logger.warning("colors.yml: color '" + name + "' has an invalid value '" + rawValue + "' (use #RRGGBB or a legacy code such as &c); skipped.");
                    continue;
                }
                Entry entry = new Entry(name, displayName, permission, canonical, material, texture);
                list.add(entry);
                index.put(name.toLowerCase(Locale.ROOT), entry);
                index.put(ColorCatalog.separatorsRemoved(name), entry);
                String alias = ColorCatalog.separatorsRemoved(ColorCatalog.plainText(displayName));
                if (alias.isEmpty() || displayIndex.containsKey(alias)) continue;
                displayIndex.put(alias, entry);
            }
        }
        this.entries = Collections.unmodifiableList(list);
        this.byKey = Collections.unmodifiableMap(index);
        this.byDisplayName = Collections.unmodifiableMap(displayIndex);
    }

    public Entry find(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String trimmed = input.trim();
        Entry found = this.byKey.get(trimmed.toLowerCase(Locale.ROOT));
        if (found == null) {
            found = this.byKey.get(ColorCatalog.separatorsRemoved(trimmed));
        }
        if (found == null) {
            found = this.byDisplayName.get(ColorCatalog.separatorsRemoved(ColorCatalog.plainText(trimmed)));
        }
        return found;
    }

    public Entry findByValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String needle = ColorCatalog.normalizeValue(value);
        for (Entry entry : this.entries) {
            if (!ColorCatalog.normalizeValue(entry.getValue()).equals(needle)) continue;
            return entry;
        }
        return null;
    }

    public Collection<Entry> entries() {
        return this.entries;
    }

    public List<String> names() {
        ArrayList<String> names = new ArrayList<String>(this.entries.size());
        for (Entry entry : this.entries) {
            names.add(entry.getName());
        }
        return names;
    }

    public void registerPermissions(PluginManager pluginManager) {
        LinkedHashMap<String, Boolean> children = new LinkedHashMap<String, Boolean>();
        for (Entry entry : this.entries) {
            String node = entry.getPermission();
            if (node.isEmpty()) continue;
            children.put(node, Boolean.TRUE);
            if (pluginManager.getPermission(node) != null) continue;
            pluginManager.addPermission(new Permission(node, "Allows using the '" + entry.getName() + "' chat color", PermissionDefault.OP));
        }
        if (pluginManager.getPermission(WILDCARD_PERMISSION) == null && !children.isEmpty()) {
            pluginManager.addPermission(new Permission(WILDCARD_PERMISSION, "Allows using every FChatColor color", PermissionDefault.OP, children));
        }
    }

    private static String canonicalize(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        Integer rgb = HexColor.parseRgb(value);
        if (rgb != null) {
            return HexColor.toHex(rgb);
        }
        if (RainbowColor.isRainbow(value)) {
            return "rainbow";
        }
        String clean = value.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00a7")) {
            clean = clean.substring(1);
        }
        if (HexColor.isLegacyCode(clean)) {
            return HexColor.normalizeLegacyCode(clean);
        }
        return null;
    }

    private static String separatorsRemoved(String name) {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static String plainText(String display) {
        String text = display.replaceAll("<[^>]*>", " ");
        text = text.replaceAll("(?i)&[0-9a-fk-or]", " ");
        text = text.replace('\u00a7', ' ');
        return text.trim();
    }

    private static String normalizeValue(String value) {
        String clean = value.trim();
        if (clean.startsWith("#")) {
            clean = clean.substring(1);
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void writeDefault() {
        InputStream in;
        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            this.logger.warning("Could not create the directory for colors.yml: " + parent);
        }
        if ((in = ColorCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) != null) {
            try {
                Files.copy(in, this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            catch (Throwable t) {
                this.logger.warning("Could not copy the default colors.yml from the jar: " + t.getMessage());
            }
            finally {
                ColorCatalog.closeQuietly(in);
            }
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter((OutputStream)new FileOutputStream(this.file), StandardCharsets.UTF_8);
            writer.write(DEFAULT_CONTENT);
        }
        catch (Throwable t) {
            this.logger.warning("Could not create colors.yml: " + t.getMessage());
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (Throwable throwable) {}
            }
        }
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    public static final class Entry {
        private final String name;
        private final String displayName;
        private final String permission;
        private final String value;
        private final String material;
        private final String texture;

        Entry(String name, String displayName, String permission, String value, String material, String texture) {
            this.name = name;
            this.displayName = displayName;
            this.permission = permission;
            this.value = value;
            this.material = material == null || material.isEmpty() ? ColorCatalog.DEFAULT_MATERIAL : material;
            this.texture = texture == null ? "" : texture;
        }

        public String getName() {
            return this.name;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public String getPermission() {
            return this.permission;
        }

        public String getValue() {
            return this.value;
        }

        public boolean isHex() {
            return this.value.startsWith("#");
        }

        public String getMaterial() {
            return this.material;
        }

        public String getTexture() {
            return this.texture;
        }

        public String toString() {
            return this.name + "=" + this.value + " (" + (this.permission.isEmpty() ? "no permission" : this.permission) + ")";
        }
    }
}

