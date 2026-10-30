package io.github.fchatcolor.menu;

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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MenuConfig {
    public static final String MATERIAL_PLACEHOLDER = "{color_material}";
    public static final String MODIFIERS_MATERIAL_PLACEHOLDER = "{modifiers_material}";
    private static final String DEFAULT_RESOURCE = "/menu.yml";
    private final Logger logger;
    private final File file;
    private String title = "<dark_gray>ChatColor (Page. %page%/%maxpage%)";
    private int size = 54;
    private List<Integer> colorSlots = Collections.emptyList();
    private Map<String, Integer> modifierSlots = Collections.emptyMap();
    private boolean fillEnabled;
    private String fillMaterial = "GRAY_STAINED_GLASS_PANE";
    private String fillName = " ";
    private ItemFormat select;
    private ItemFormat unselect;
    private ItemFormat selected;
    private ItemFormat noPermission;
    private ItemFormat modifierSelect;
    private ItemFormat modifierSelected;
    private ItemFormat modifierUnselect;
    private ItemFormat modifierNoPermission;
    private Button close;
    private Button previousPage;
    private Button nextPage;

    public MenuConfig(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    public void reload() {
        if (!this.file.exists()) {
            this.writeDefault();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration((File)this.file);
        this.title = yaml.getString("title", this.title);
        this.size = MenuConfig.clampSize(yaml.getInt("size", 54));
        String slotsRaw = yaml.getString("colors-slots", null);
        if (slotsRaw == null || slotsRaw.isEmpty()) {
            slotsRaw = yaml.getString("clors-slots", "0-8;9-17;18-26;27-35;36-44");
        }
        this.colorSlots = MenuConfig.parseSlots(slotsRaw);
        ArrayList<Integer> bounded = new ArrayList<Integer>();
        for (Integer slot : this.colorSlots) {
            if (slot == null || slot < 0 || slot >= this.size) continue;
            bounded.add(slot);
        }
        this.colorSlots = Collections.unmodifiableList(bounded);

        // Parse modifiers-slots: ["bold:46", "strikethrough:47", ...]
        LinkedHashMap<String, Integer> modMap = new LinkedHashMap<String, Integer>();
        List<String> modSlotsRaw = null;
        try {
            modSlotsRaw = yaml.getStringList("modifiers-slots");
        } catch (Throwable t) {
            modSlotsRaw = null;
        }
        if (modSlotsRaw == null || modSlotsRaw.isEmpty()) {
            String fallback = yaml.getString("modifiers-slots", null);
            if (fallback != null && !fallback.isEmpty()) {
                modSlotsRaw = Collections.singletonList(fallback);
            }
        }
        if (modSlotsRaw != null) {
            for (String entry : modSlotsRaw) {
                if (entry == null) continue;
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                // Remove brackets/quotes artefacts
                entry = entry.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
                // Could be comma separated in single string
                String[] parts = entry.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.isEmpty()) continue;
                    int colon = part.indexOf(':');
                    if (colon <= 0) continue;
                    String key = part.substring(0, colon).trim().toLowerCase();
                    String val = part.substring(colon + 1).trim();
                    try {
                        int slot = Integer.parseInt(val);
                        if (slot >= 0 && slot < this.size) {
                            modMap.put(key, slot);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        // If still empty, try parsing as single string list with alternative method: check yaml.getList
        if (modMap.isEmpty()) {
            try {
                List<?> list = yaml.getList("modifiers-slots");
                if (list != null) {
                    for (Object o : list) {
                        if (o == null) continue;
                        String s = o.toString().trim();
                        int colon = s.indexOf(':');
                        if (colon <= 0) continue;
                        String k = s.substring(0, colon).trim().toLowerCase();
                        String v = s.substring(colon + 1).trim();
                        try {
                            int slot = Integer.parseInt(v);
                            if (slot >= 0 && slot < this.size) modMap.put(k, slot);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        }
        this.modifierSlots = Collections.unmodifiableMap(modMap);

        ConfigurationSection fill = yaml.getConfigurationSection("fill");
        if (fill != null) {
            this.fillEnabled = fill.getBoolean("enabled", false);
            this.fillMaterial = fill.getString("material", this.fillMaterial);
            this.fillName = fill.getString("name", this.fillName);
        }
        this.select = MenuConfig.readFormat(yaml, "item-format.select", MATERIAL_PLACEHOLDER, "{color_name}", Collections.singletonList("&aClick to select!"));
        this.unselect = MenuConfig.readFormat(yaml, "item-format.unselect", MATERIAL_PLACEHOLDER, "{color_name}", Collections.singletonList("&cClick to remove your chat color!"));
        this.selected = MenuConfig.readFormat(yaml, "item-format.selected", MATERIAL_PLACEHOLDER, "{color_name}", Collections.singletonList("&cYou have already selected this color!"));
        this.noPermission = MenuConfig.readFormat(yaml, "item-format.no_permission", MATERIAL_PLACEHOLDER, "{color_name}", Collections.singletonList("&cYou need the {permission} permission to select the color!"));

        this.modifierSelect = MenuConfig.readFormat(yaml, "modifiers-format.select", MODIFIERS_MATERIAL_PLACEHOLDER, "{modifiers_name}", Collections.singletonList("&aClick to select!"));
        this.modifierSelected = MenuConfig.readFormat(yaml, "modifiers-format.selected", MODIFIERS_MATERIAL_PLACEHOLDER, "{modifiers_name}", Collections.singletonList("&cYou have already selected this modifier {modifier}!"));
        this.modifierUnselect = MenuConfig.readFormat(yaml, "modifiers-format.unselect", MODIFIERS_MATERIAL_PLACEHOLDER, "{modifiers_name}", Collections.singletonList("&cClick to remove your chat modifier {modifier}!"));
        this.modifierNoPermission = MenuConfig.readFormat(yaml, "modifiers-format.no_permission", MODIFIERS_MATERIAL_PLACEHOLDER, "{modifiers_name}", Collections.singletonList("&cYou need the {permission} permission to select the modifier {modifier}!"));

        this.close = MenuConfig.readButton(yaml, "buttons.close", true, "RED_BED", "&cClose", 49, "close");
        this.previousPage = MenuConfig.readButton(yaml, "buttons.previous-page", true, "ARROW", "&aBack", 45, "prev");
        this.nextPage = MenuConfig.readButton(yaml, "buttons.next-page", true, "ARROW", "&aNext", 53, "next");
    }

    public String getTitle() {
        return this.title;
    }

    public int getSize() {
        return this.size;
    }

    public List<Integer> getColorSlots() {
        return this.colorSlots;
    }

    public int getColorsPerPage() {
        return this.colorSlots.size();
    }

    public Map<String, Integer> getModifierSlots() {
        return this.modifierSlots;
    }

    public boolean isFillEnabled() {
        return this.fillEnabled;
    }

    public String getFillMaterial() {
        return this.fillMaterial;
    }

    public String getFillName() {
        return this.fillName;
    }

    public ItemFormat getSelect() {
        return this.select;
    }

    public ItemFormat getUnselect() {
        return this.unselect;
    }

    public ItemFormat getSelected() {
        return this.selected;
    }

    public ItemFormat getNoPermission() {
        return this.noPermission;
    }

    public ItemFormat getModifierSelect() {
        return this.modifierSelect;
    }

    public ItemFormat getModifierSelected() {
        return this.modifierSelected;
    }

    public ItemFormat getModifierUnselect() {
        return this.modifierUnselect;
    }

    public ItemFormat getModifierNoPermission() {
        return this.modifierNoPermission;
    }

    public Button getClose() {
        return this.close;
    }

    public Button getPreviousPage() {
        return this.previousPage;
    }

    public Button getNextPage() {
        return this.nextPage;
    }

    private static int clampSize(int requested) {
        if (requested <= 9) {
            return 9;
        }
        if (requested >= 54) {
            return 54;
        }
        return requested / 9 * 9;
    }

    private static List<Integer> parseSlots(String raw) {
        ArrayList<Integer> slots = new ArrayList<Integer>();
        if (raw == null || raw.trim().isEmpty()) {
            return slots;
        }
        for (String part : raw.split(";")) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;
            int dash = piece.indexOf(45);
            try {
                if (dash > 0) {
                    int to;
                    int from = Integer.parseInt(piece.substring(0, dash).trim());
                    if (from > (to = Integer.parseInt(piece.substring(dash + 1).trim()))) {
                        int swap = from;
                        from = to;
                        to = swap;
                    }
                    for (int i = from; i <= to; ++i) {
                        slots.add(i);
                    }
                    continue;
                }
                slots.add(Integer.parseInt(piece));
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return slots;
    }

    private static ItemFormat readFormat(YamlConfiguration yaml, String path, String defMaterial, String defName, List<String> defLore) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return new ItemFormat(defMaterial, defName, defLore);
        }
        String material = section.getString("material", defMaterial);
        String name = section.getString("name", defName);
        List<String> lore = section.getStringList("lore");
        if (lore.isEmpty()) {
            lore = defLore;
        }
        return new ItemFormat(material, name, lore);
    }

    private static Button readButton(YamlConfiguration yaml, String path, boolean defEnabled, String defMaterial, String defName, int defSlot, String defAction) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return new Button(defEnabled, defMaterial, defName, defSlot, defAction);
        }
        boolean enabled = section.getBoolean("enabled", defEnabled);
        String material = section.getString("material", defMaterial);
        String name = section.getString("name", defName);
        int slot = section.getInt("slot", defSlot);
        String action = section.getString("action", defAction);
        return new Button(enabled, material, name, slot, action);
    }

    private void writeDefault() {
        InputStream in;
        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            this.logger.warning("Could not create the directory for menu.yml: " + parent);
        }
        if ((in = MenuConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) != null) {
            try {
                Files.copy(in, this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            catch (Throwable t) {
                this.logger.warning("Could not copy the default menu.yml from the jar: " + t.getMessage());
            }
            finally {
                MenuConfig.closeQuietly(in);
            }
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter((OutputStream)new FileOutputStream(this.file), StandardCharsets.UTF_8);
            writer.write(MenuConfig.buildDefaultContent());
        }
        catch (Throwable t) {
            this.logger.warning("Could not create menu.yml: " + t.getMessage());
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

    private static String buildDefaultContent() {
        return "# =====================================================================\n#  FChatColor - ChatColor Menu Configuration\n# =====================================================================\n# Menu opened with /chatcolor (no arguments) and /fchatcolor menu.\n#\n#   title:   inventory title. Supports the %page% and %maxpage%\n#            placeholders and MiniMessage tags or & codes.\n#   size:    inventory size (multiple of 9, max 54).\n#   colors-slots: slots reserved for the colors, written as ranges\n#            separated by ';'.\n#   modifiers-slots: slots for modifiers, format \"key:slot\" per entry.\n#   item-format: how each color is rendered.\n#   modifiers-format: how each modifier is rendered (placeholders {modifiers_name}, {modifiers_material}, {modifier}, {permission}).\n#   buttons: fixed controls (close / previous-page / next-page).\n\ntitle: \"<dark_gray>ChatColor (Page. %page%/%maxpage%)\"\nsize: 54\n\ncolors-slots: 0-8;9-17;18-26;27-35;36-44\nmodifiers-slots: [\"bold:46\", \"strikethrough:47\", \"underline:51\", \"italic:52\"]\n\nfill:\n  enabled: false\n  material: GRAY_STAINED_GLASS_PANE\n  name: \" \"\n\nitem-format:\n  select:\n    name: \"{color_name}\"\n    material: \"{color_material}\"\n    lore:\n      - '&aClick to select!'\n  selected:\n    name: \"{color_name}\"\n    material: \"{color_material}\"\n    lore:\n      - '&cYou have already selected this color!'\n  unselect:\n    name: \"{color_name}\"\n    material: \"{color_material}\"\n    lore:\n      - '&cClick to remove your chat color!'\n  no_permission:\n    name: \"{color_name}\"\n    material: \"{color_material}\"\n    lore:\n      - '&cYou need the {permission} permission to select the color!'\n\nmodifiers-format:\n  select:\n    name: \"{modifiers_name}\"\n    material: \"{modifiers_material}\"\n    lore:\n      - '&aClick to select!'\n  selected:\n    name: \"{modifiers_name}\"\n    material: \"{modifiers_material}\"\n    lore:\n      - '&cYou have already selected this modifier {modifier}!'\n  unselect:\n    name: \"{modifiers_name}\"\n    material: \"{modifiers_material}\"\n    lore:\n      - '&cClick to remove your chat modifier {modifier}!'\n  no_permission:\n    name: \"{modifiers_name}\"\n    material: \"{modifiers_material}\"\n    lore:\n      - '&cYou need the {permission} permission to select the modifier {modifier}!'\n\nbuttons:\n  close:\n    enabled: true\n    material: RED_BED\n    name: \"&cClose\"\n    slot: 49\n    action: \"close\"\n  previous-page:\n    enabled: true\n    material: ARROW\n    name: \"&aBack\"\n    slot: 45\n    action: \"prev\"\n  next-page:\n    enabled: true\n    material: ARROW\n    name: \"&aNext\"\n    slot: 53\n    action: \"next\"\n";
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

    public static final class ItemFormat {
        private final String material;
        private final String name;
        private final List<String> lore;

        ItemFormat(String material, String name, List<String> lore) {
            this.material = material;
            this.name = name;
            this.lore = Collections.unmodifiableList(lore);
        }

        public String getMaterial() {
            return this.material;
        }

        public String getName() {
            return this.name;
        }

        public List<String> getLore() {
            return this.lore;
        }
    }

    public static final class Button {
        private final boolean enabled;
        private final String material;
        private final String name;
        private final int slot;
        private final String action;

        Button(boolean enabled, String material, String name, int slot, String action) {
            this.enabled = enabled;
            this.material = material;
            this.name = name;
            this.slot = slot;
            this.action = action;
        }

        public boolean isEnabled() {
            return this.enabled;
        }

        public String getMaterial() {
            return this.material;
        }

        public String getName() {
            return this.name;
        }

        public int getSlot() {
            return this.slot;
        }

        public String getAction() {
            return this.action;
        }
    }
}
