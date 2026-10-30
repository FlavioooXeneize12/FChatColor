package io.github.fchatcolor.menu;

import io.github.fchatcolor.FChatColorPlugin;
import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import io.github.fchatcolor.colors.ColorCatalog;
import io.github.fchatcolor.messages.Messages;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ChatColorMenu implements InventoryHolder {
    private static final String RESET = "\u00a7r";
    private static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^<>]*>");
    private static final Map<UUID, ChatColorMenu> OPEN_MENUS = new ConcurrentHashMap<UUID, ChatColorMenu>();
    private final FChatColorPlugin plugin;
    private final Messages messages;
    private MenuConfig config;
    private Inventory inventory;
    private int page;
    private Player viewer;
    private final Map<Integer, ColorCatalog.Entry> slotToColor = new HashMap<Integer, ColorCatalog.Entry>();
    private final Map<Integer, ColorCatalog.Entry> slotToModifier = new HashMap<Integer, ColorCatalog.Entry>();

    public static ChatColorMenu getOpenMenu(Player player) {
        if (player == null) {
            return null;
        }
        return OPEN_MENUS.get(player.getUniqueId());
    }

    public static ChatColorMenu getOpenMenu(UUID id) {
        if (id == null) {
            return null;
        }
        return OPEN_MENUS.get(id);
    }

    public static void setOpenMenu(Player player, ChatColorMenu menu) {
        if (player == null) {
            return;
        }
        if (menu == null) {
            OPEN_MENUS.remove(player.getUniqueId());
        } else {
            OPEN_MENUS.put(player.getUniqueId(), menu);
        }
    }

    public static void closeMenu(Player player) {
        if (player == null) {
            return;
        }
        OPEN_MENUS.remove(player.getUniqueId());
    }

    public ChatColorMenu(FChatColorPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessages();
        this.config = plugin.getMenu();
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public int getPage() {
        return this.page;
    }

    public MenuConfig getConfig() {
        return this.config;
    }

    public void open(Player player, int targetPage) {
        this.viewer = player;
        this.config = this.plugin.getMenu();
        this.page = Math.max(0, targetPage);
        int size = this.config.getSize();
        int perPage = Math.max(1, this.config.getColorsPerPage());
        Collection<ColorCatalog.Entry> all = this.plugin.getColors().entries();
        int maxPage = ChatColorMenu.totalPages(all.size(), perPage);
        if (this.page >= maxPage) {
            this.page = maxPage - 1;
            if (this.page < 0) {
                this.page = 0;
            }
        }
        String title = this.buildTitle(this.page + 1, Math.max(1, maxPage));
        this.inventory = Bukkit.createInventory((InventoryHolder) this, size, title);
        this.slotToColor.clear();
        this.slotToModifier.clear();
        this.fillBackground();
        this.renderColors(all, perPage);
        this.renderModifiers();
        this.renderButtons(maxPage);
        player.openInventory(this.inventory);
        OPEN_MENUS.put(player.getUniqueId(), this);
    }

    static int totalPages(int colorCount, int perPage) {
        if (colorCount <= 0) {
            return 1;
        }
        return (colorCount + perPage - 1) / perPage;
    }

    private String buildTitle(int pageNum, int maxPage) {
        String raw = this.config.getTitle().replace("%page%", String.valueOf(pageNum)).replace("%maxpage%", String.valueOf(maxPage));
        raw = this.applyGlobalPlaceholders(raw);
        return this.translate(raw);
    }

    private void fillBackground() {
        if (!this.config.isFillEnabled()) {
            return;
        }
        ItemStack filler = this.makeItem(this.config.getFillMaterial(), this.config.getFillName(), new ArrayList<String>());
        if (filler == null) {
            return;
        }
        for (int slot = 0; slot < this.inventory.getSize(); ++slot) {
            this.inventory.setItem(slot, filler);
        }
    }

    private void renderColors(Collection<ColorCatalog.Entry> all, int perPage) {
        List<Integer> slots = this.config.getColorSlots();
        if (slots.isEmpty()) {
            return;
        }
        int start = this.page * perPage;
        int index = 0;
        for (ColorCatalog.Entry entry : all) {
            if (index < start) {
                ++index;
                continue;
            }
            int offset = index - start;
            if (offset >= slots.size()) break;
            int slot = slots.get(offset);
            this.inventory.setItem(slot, this.buildColorItem(entry));
            this.slotToColor.put(slot, entry);
            ++index;
        }
    }

    private void renderModifiers() {
        Map<String, Integer> modSlots = this.config.getModifierSlots();
        if (modSlots == null || modSlots.isEmpty()) {
            return;
        }
        Collection<ColorCatalog.Entry> mods = this.plugin.getColors().modifierEntries();
        if (mods == null || mods.isEmpty()) {
            return;
        }
        for (ColorCatalog.Entry entry : mods) {
            ItemStack item;
            String key = entry.getName().toLowerCase();
            Integer slot = modSlots.get(key);
            if (slot == null) {
                slot = modSlots.get(key.replace("_", "").replace("-", ""));
            }
            if (slot == null || slot < 0 || slot >= this.inventory.getSize() || (item = this.buildModifierItem(entry)) == null) continue;
            this.inventory.setItem(slot.intValue(), item);
            this.slotToModifier.put(slot, entry);
        }
    }

    private ItemStack buildColorItem(ColorCatalog.Entry entry) {
        MenuConfig.ItemFormat format = this.chooseFormat(entry);
        String materialName = ChatColorMenu.resolveMaterial(format.getMaterial(), entry);
        String name = this.applyPlaceholders(format.getName(), entry);
        ArrayList<String> lore = new ArrayList<String>();
        for (String line : format.getLore()) {
            lore.add(this.applyPlaceholders(line, entry));
        }
        ItemStack item = this.makeItem(materialName, name, lore);
        if (item != null && entry.getTexture() != null && !entry.getTexture().isEmpty()) {
            HeadTextures.apply(item, entry.getTexture());
        }
        return item;
    }

    private ItemStack buildModifierItem(ColorCatalog.Entry entry) {
        String matTemplate;
        MenuConfig.ItemFormat format = this.chooseModifierFormat(entry);
        String materialName = matTemplate = format.getMaterial();
        if (matTemplate != null && matTemplate.contains("{modifiers_material}")) {
            materialName = matTemplate.replace("{modifiers_material}", entry.getMaterial());
        }
        if (matTemplate != null && matTemplate.contains("{color_material}")) {
            materialName = materialName.replace("{color_material}", entry.getMaterial());
        }
        if (materialName == null || materialName.trim().isEmpty()) {
            materialName = entry.getMaterial();
        }
        String name = this.applyModifierPlaceholders(format.getName(), entry);
        ArrayList<String> lore = new ArrayList<String>();
        for (String line : format.getLore()) {
            lore.add(this.applyModifierPlaceholders(line, entry));
        }
        ItemStack item = this.makeItem(materialName, name, lore);
        if (item != null && entry.getTexture() != null && !entry.getTexture().isEmpty()) {
            HeadTextures.apply(item, entry.getTexture());
        }
        return item;
    }

    private MenuConfig.ItemFormat chooseFormat(ColorCatalog.Entry entry) {
        String current = this.readCurrentColor();
        boolean isCurrent = current != null && ChatColorMenu.matches(entry, current);
        boolean hasPermission = entry.getPermission().isEmpty() || this.viewer.hasPermission(entry.getPermission());
        if (isCurrent) {
            return hasPermission ? this.config.getUnselect() : this.config.getSelected();
        }
        return hasPermission ? this.config.getSelect() : this.config.getNoPermission();
    }

    private MenuConfig.ItemFormat chooseModifierFormat(ColorCatalog.Entry entry) {
        boolean hasPermission = entry.getPermission() == null || entry.getPermission().isEmpty() || this.viewer.hasPermission(entry.getPermission());
        if (!hasPermission) {
            return this.config.getModifierNoPermission();
        }
        Set<String> selected = ChatColorMenu.parseModifiersSet(this.readCurrentModifiers());
        boolean isSelected = selected.contains(entry.getValue().toLowerCase()) || selected.contains(entry.getValue());
        if (!isSelected) {
            for (String s : selected) {
                if (!s.equalsIgnoreCase(entry.getValue())) continue;
                isSelected = true;
                break;
            }
        }
        if (isSelected) {
            return this.config.getModifierUnselect();
        }
        return this.config.getModifierSelect();
    }

    private String readCurrentColor() {
        try {
            return this.plugin.getStorage().getSelectedColor(this.viewer.getUniqueId());
        } catch (Throwable t) {
            return null;
        }
    }

    private String readCurrentModifiers() {
        try {
            return this.plugin.getStorage().getSelectedModifiers(this.viewer.getUniqueId());
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean matches(ColorCatalog.Entry entry, String stored) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        return entry.getValue().equalsIgnoreCase(stored.trim());
    }

    private void renderButtons(int maxPage) {
        MenuConfig.Button next;
        this.placeButton(this.config.getClose());
        MenuConfig.Button prev = this.config.getPreviousPage();
        if (prev.isEnabled() && this.page > 0) {
            this.placeButton(prev);
        }
        if ((next = this.config.getNextPage()).isEnabled() && this.page + 1 < maxPage) {
            this.placeButton(next);
        }
    }

    private void placeButton(MenuConfig.Button button) {
        if (button == null || !button.isEnabled() || button.getSlot() < 0 || button.getSlot() >= this.inventory.getSize()) {
            return;
        }
        ItemStack item = this.makeItem(button.getMaterial(), button.getName(), new ArrayList<String>());
        if (item != null) {
            this.inventory.setItem(button.getSlot(), item);
        }
    }

    private static String resolveMaterial(String template, ColorCatalog.Entry entry) {
        String material = template == null ? "" : template.trim();
        if (material.indexOf(123) >= 0) {
            material = material.replace("{color_material}", entry.getMaterial());
            material = material.replace("{modifiers_material}", entry.getMaterial());
        }
        return material.isEmpty() ? "PLAYER_HEAD" : material;
    }

    private String applyPlaceholders(String text, ColorCatalog.Entry entry) {
        if (text == null) {
            return "";
        }
        String entryRep = this.getEntryColorReplacement(entry);
        String curRep = this.getCurrentColorReplacement();
        String entryPlain = ChatColorMenu.stripColor(entry.getDisplayName());
        if (entryPlain.isEmpty()) {
            entryPlain = entry.getName();
        }
        String selectedMods = this.getSelectedModifiersCodes();
        // Provide modifier placeholders for color items as well
        String modifierCode = entry.getValue() == null ? "" : entry.getValue();
        String result = text
                .replace("{color_name}", entry.getDisplayName())
                .replace("{color_plain}", entryPlain)
                .replace("{color_name_plain}", entryPlain)
                .replace("{plain_color_name}", entryPlain)
                .replace("{color_without_color}", entryPlain)
                .replace("{color_material}", entry.getMaterial())
                .replace("{modifiers_material}", entry.getMaterial())
                .replace("{permission}", entry.getPermission())
                .replace("{modifier}", selectedMods)
                .replace("{modifier_code}", selectedMods)
                .replace("{modifier_value}", selectedMods)
                .replace("{selected_modifiers}", selectedMods)
                .replace("{modifiers}", selectedMods)
                .replace("{player_modifiers}", selectedMods)
                .replace("{modifiers_name}", entry.getDisplayName())
                .replace("{modifiers_plain}", entryPlain)
                .replaceAll("(?i)\\{color\\}", Matcher.quoteReplacement(entryRep))
                .replace("<color>", entryRep)
                .replace("<COLOR>", entryRep)
                .replace("{entry_color}", entryRep)
                .replace("{current_color}", curRep)
                .replace("{player_color}", curRep)
                .replace("{my_color}", curRep);
        result = this.applyPAPI(result);
        result = ChatColorMenu.normalizeUnderlineTags(result);
        return result;
    }

    private String applyModifierPlaceholders(String text, ColorCatalog.Entry entry) {
        if (text == null) {
            return "";
        }
        String colorReplacement = this.getCurrentColorReplacement();
        String displayName = entry.getDisplayName();
        if (displayName != null && displayName.contains("<color>")) {
            displayName = displayName.replace("<color>", colorReplacement);
        } else if (displayName != null && displayName.contains("{color}")) {
            displayName = displayName.replace("{color}", colorReplacement);
        }
        String displayPlain = ChatColorMenu.stripColor(displayName);
        if (displayPlain.isEmpty()) {
            displayPlain = entry.getName();
        }
        String selectedMods = this.getSelectedModifiersCodes();
        String entryValue = entry.getValue() == null ? "" : entry.getValue();
        String result = text
                .replaceAll("(?i)\\{modifiers_name\\}", Matcher.quoteReplacement(displayName))
                .replaceAll("(?i)\\{modifiers_plain\\}", Matcher.quoteReplacement(displayPlain))
                .replaceAll("(?i)\\{modifiers_name_plain\\}", Matcher.quoteReplacement(displayPlain))
                .replaceAll("(?i)\\{modifiers_material\\}", Matcher.quoteReplacement(entry.getMaterial()))
                .replaceAll("(?i)\\{color_material\\}", Matcher.quoteReplacement(entry.getMaterial()))
                .replaceAll("(?i)\\{modifier_code\\}", Matcher.quoteReplacement(entryValue))
                .replaceAll("(?i)\\{modifier_value\\}", Matcher.quoteReplacement(entryValue))
                .replaceAll("(?i)\\{modifier_name\\}", Matcher.quoteReplacement(entry.getName()))
                .replaceAll("(?i)\\{selected_modifiers\\}", Matcher.quoteReplacement(selectedMods))
                .replaceAll("(?i)\\{modifiers\\}", Matcher.quoteReplacement(selectedMods))
                .replaceAll("(?i)\\{player_modifiers\\}", Matcher.quoteReplacement(selectedMods))
                .replaceAll("(?i)\\{modifier\\}", Matcher.quoteReplacement(entry.getName()))
                .replace("{permission}", entry.getPermission() == null ? "" : entry.getPermission())
                .replace("{color_name}", displayName)
                .replace("{color_plain}", displayPlain)
                .replace("{color_name_plain}", displayPlain);
        if (result.contains("<color>")) {
            result = result.replace("<color>", colorReplacement);
        }
        result = result.replaceAll("(?i)\\{color\\}", Matcher.quoteReplacement(colorReplacement));
        // Additional aliases for current color in modifier context
        result = result.replace("{current_color}", colorReplacement)
                .replace("{player_color}", colorReplacement)
                .replace("{my_color}", colorReplacement)
                .replace("{entry_color}", this.getEntryColorReplacement(entry))
                .replace("<COLOR>", colorReplacement);
        result = this.applyPAPI(result);
        result = ChatColorMenu.normalizeUnderlineTags(result);
        return result;
    }

    private static String normalizeUnderlineTags(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String out = input.replaceAll("(?i)<underline>", "<underlined>");
        out = out.replaceAll("(?i)</underline>", "</underlined>");
        return out;
    }

    private String getCurrentColorReplacement() {
        String col = this.getEffectiveColor();
        if (col == null || col.isEmpty()) {
            return "";
        }
        col = col.trim();
        if (io.github.fchatcolor.api.util.GradientColor.isGradient(col)) {
            return gradientTag(col);
        }
        if (col.startsWith("#")) {
            return "<" + col + ">";
        }
        if (col.equalsIgnoreCase("rainbow")) {
            return "<rainbow>";
        }
        if (col.startsWith("&") || col.startsWith("\u00a7")) {
            return col;
        }
        if (col.length() == 1) {
            return "&" + col;
        }
        return col;
    }

    private String getEntryColorReplacement(ColorCatalog.Entry entry) {
        if (entry == null) {
            return "";
        }
        String v = entry.getValue();
        if (v == null || v.isEmpty()) {
            return "";
        }
        v = v.trim();
        if (io.github.fchatcolor.api.util.GradientColor.isGradient(v)) {
            return gradientTag(v);
        }
        if (v.startsWith("#")) {
            return "<" + v + ">";
        }
        if (v.equalsIgnoreCase("rainbow")) {
            return "<rainbow>";
        }
        if (v.startsWith("&") || v.startsWith("\u00a7")) {
            return v;
        }
        if (v.length() == 1) {
            return "&" + v;
        }
        return v;
    }

    private String getEffectiveColor() {
        try {
            if (this.viewer == null) {
                return this.readCurrentColor();
            }
            return this.resolveEffectiveColor(this.viewer, this.readCurrentColor());
        } catch (Throwable t) {
            return this.readCurrentColor();
        }
    }

    /** MiniMessage gradient tag for a stored gradient value (falls back to "" if invalid). */
    private static String gradientTag(String stored) {
        return io.github.fchatcolor.api.util.GradientColor.miniTag(stored);
    }

    private static String toMiniColor(String entry) {
        return io.github.fchatcolor.api.util.GradientColor.toMiniColor(entry);
    }

    private String resolveEffectiveColor(Player player, String stored) {
        String perm;
        if (stored != null && !stored.isEmpty() && ((perm = this.getPermissionForStored(stored)) == null || perm.isEmpty() || player.hasPermission(perm))) {
            return stored;
        }
        try {
            Map<String, String> permMap = this.plugin.getContext().getSettings().getDefaultPermissionColors();
            if (permMap != null) {
                for (Map.Entry<String, String> e : permMap.entrySet()) {
                    String r;
                    String perm2 = e.getKey();
                    String val = e.getValue();
                    if (perm2 == null || val == null || perm2.isEmpty() || val.isEmpty() || !player.hasPermission(perm2) || (r = this.resolveDefaultColor(val)) == null || r.isEmpty()) continue;
                    return r;
                }
            }
        } catch (Throwable ignored) {
        }
        String def = null;
        try {
            def = this.plugin.getContext().getSettings().getDefaultColor();
        } catch (Throwable ignored) {
        }
        return this.resolveDefaultColor(def);
    }

    private String getPermissionForStored(String stored) {
        try {
            ColorCatalog.Entry e = this.plugin.getColors().findByValue(stored);
            if (e == null) {
                e = this.plugin.getColors().find(stored);
            }
            if (e != null) {
                return e.getPermission();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String resolveDefaultColor(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("disabled") || s.equalsIgnoreCase("off")) {
            return null;
        }
        try {
            ColorCatalog.Entry e = this.plugin.getColors().find(s);
            if (e != null) {
                return e.getValue();
            }
            Integer rgb = HexColor.parseRgb(s);
            if (rgb != null) {
                return HexColor.toHex(rgb);
            }
            String clean = s;
            if (clean.startsWith("&") || clean.startsWith("\u00a7")) {
                clean = clean.substring(1);
            }
            if (HexColor.isLegacyCode(clean)) {
                return HexColor.normalizeLegacyCode(clean);
            }
            if (RainbowColor.isRainbow(s)) {
                return "rainbow";
            }
        } catch (Throwable ignored) {
        }
        Integer rgb = HexColor.parseRgb(s);
        if (rgb != null) {
            return HexColor.toHex(rgb);
        }
        String clean = s.trim();
        if (clean.startsWith("&") || clean.startsWith("\u00a7")) {
            clean = clean.substring(1);
        }
        if (HexColor.isLegacyCode(clean)) {
            return HexColor.normalizeLegacyCode(clean);
        }
        if (RainbowColor.isRainbow(s)) {
            return "rainbow";
        }
        return s;
    }

    private static String stripColor(String input) {
        if (input == null) {
            return "";
        }
        String s = MINIMESSAGE_TAG.matcher(input).replaceAll("");
        s = s.replaceAll("(?i)gradient:", "");
        s = s.replaceAll("(?i)&#[0-9A-Fa-f]{6}", "");
        s = s.replaceAll("(?i)#[0-9A-Fa-f]{6}", "");
        s = s.replaceAll("(?i)&[0-9A-Fa-fk-orK-Or]", "");
        s = s.replaceAll("\u00a7[0-9A-Fa-fk-orK-Or]", "");
        s = s.replaceAll("(?i)\u00a7x(\u00a7[0-9A-Fa-f]){6}", "");
        return s.trim();
    }

    private String applyPAPI(String text) {
        if (text == null || this.viewer == null) {
            return text;
        }
        if (text.indexOf(37) < 0) {
            return text;
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                return text;
            }
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method m = papi.getMethod("setPlaceholders", Player.class, String.class);
            Object out = m.invoke(null, this.viewer, text);
            if (out instanceof String) {
                return (String) out;
            }
        } catch (Throwable ignored) {
        }
        return text;
    }

    private String applyGlobalPlaceholders(String text) {
        if (text == null) {
            return "";
        }
        String curRep = this.getCurrentColorReplacement();
        text = text.replaceAll("(?i)\\{color\\}", Matcher.quoteReplacement(curRep)).replace("<color>", curRep).replace("<COLOR>", curRep);
        // Aliases for current color
        text = text.replace("{current_color}", curRep).replace("{player_color}", curRep).replace("{my_color}", curRep).replace("{entry_color}", curRep);
        String curStored = this.getEffectiveColor();
        String curNameColored = "";
        String curNamePlain = "";
        if (curStored != null && !curStored.isEmpty()) {
            ColorCatalog.Entry curEntry = this.plugin.getColors().findByValue(curStored);
            if (curEntry != null) {
                curNameColored = curEntry.getDisplayName();
                curNamePlain = ChatColorMenu.stripColor(curNameColored);
                if (curNamePlain.isEmpty()) {
                    curNamePlain = curEntry.getName();
                }
            } else {
                curNamePlain = curStored;
                curNameColored = curStored;
            }
        }
        text = text.replace("{color_name}", curNameColored).replace("{color_plain}", curNamePlain).replace("{color_name_plain}", curNamePlain).replace("{plain_color_name}", curNamePlain).replace("{color_without_color}", curNamePlain);
        // Modifiers placeholders for global context (title/fill/buttons)
        String selectedMods = this.getSelectedModifiersCodes();
        text = text.replace("{selected_modifiers}", selectedMods).replace("{modifiers}", selectedMods).replace("{player_modifiers}", selectedMods);
        // For global, {modifier} and {modifier_code} not meaningful, replace with first modifier value or empty
        // Keep them as empty if not applicable, but support replacement to avoid leaving raw placeholder
        if (text.contains("{modifier_code}") || text.contains("{modifier_value}") || text.contains("{modifier}") || text.contains("{modifiers_name}") || text.contains("{modifiers_plain}")) {
            // Use display of first selected modifier if available, otherwise empty
            String modName = "";
            String modCode = "";
            String modPlain = "";
            try {
                String rawMods = this.readCurrentModifiers();
                Set<String> set = ChatColorMenu.parseModifiersSet(rawMods);
                for (String code : set) {
                    ColorCatalog.Entry e = this.plugin.getColors().findModifierByValue(code);
                    if (e == null) e = this.plugin.getColors().findModifier(code);
                    if (e != null) {
                        String perm = e.getPermission();
                        if (perm != null && !perm.isEmpty() && this.viewer != null && !this.viewer.hasPermission(perm)) continue;
                        modName = e.getDisplayName();
                        modPlain = ChatColorMenu.stripColor(modName);
                        if (modPlain.isEmpty()) modPlain = e.getName();
                        modCode = e.getValue();
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            text = text.replace("{modifier}", modName)
                    .replace("{modifier_code}", modCode)
                    .replace("{modifier_value}", modCode)
                    .replace("{modifiers_name}", modName)
                    .replace("{modifiers_plain}", modPlain)
                    .replace("{modifiers_name_plain}", modPlain);
        }
        text = this.applyPAPI(text);
        return text;
    }

    private String getSelectedModifiersCodes() {
        try {
            String raw = this.readCurrentModifiers();
            if (raw == null || raw.trim().isEmpty()) {
                return "";
            }
            Set<String> parsed = ChatColorMenu.parseModifiersSet(raw);
            if (parsed.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (String code : parsed) {
                ColorCatalog.Entry e = this.plugin.getColors().findModifierByValue(code);
                if (e == null) {
                    e = this.plugin.getColors().findModifier(code);
                }
                if (e != null) {
                    String perm = e.getPermission();
                    if (perm != null && !perm.isEmpty() && this.viewer != null && !this.viewer.hasPermission(perm)) {
                        continue;
                    }
                    String val = e.getValue();
                    if (val == null || val.isEmpty()) continue;
                    out.append(val);
                } else {
                    // Fallback: raw code if it looks like a modifier (& + single char)
                    String c = code.trim();
                    if (c.startsWith("&") && c.length() == 2) {
                        out.append(c);
                    } else if (c.length() == 1) {
                        out.append("&").append(c);
                    }
                }
            }
            return out.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private ItemStack makeItem(String materialName, String displayName, List<String> lore) {
        Material material = ChatColorMenu.resolveMaterial(materialName);
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isEmpty()) {
                displayName = this.applyGlobalPlaceholders(displayName);
                String translated = RESET + this.translate(displayName);
                if (!ChatColorMenu.trySetDisplayNameComponent(meta, translated)) {
                    meta.setDisplayName(translated);
                }
            }
            if (lore != null && !lore.isEmpty()) {
                ArrayList<String> translated = new ArrayList<String>(lore.size());
                for (String line : lore) {
                    line = this.applyGlobalPlaceholders(line);
                    translated.add(RESET + this.translate(line));
                }
                if (!ChatColorMenu.trySetLoreComponents(meta, translated)) {
                    meta.setLore(translated);
                }
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static boolean trySetDisplayNameComponent(ItemMeta meta, String legacyText) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> legacyClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object serializer = legacyClass.getMethod("legacySection", new Class[0]).invoke(null, new Object[0]);
            Object component = serializer.getClass().getMethod("deserialize", String.class).invoke(serializer, legacyText);
            try {
                meta.getClass().getMethod("displayName", componentClass).invoke(meta, component);
                return true;
            } catch (NoSuchMethodException ns) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean trySetLoreComponents(ItemMeta meta, List<String> legacyLines) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> legacyClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object serializer = legacyClass.getMethod("legacySection", new Class[0]).invoke(null, new Object[0]);
            Method deser = serializer.getClass().getMethod("deserialize", String.class);
            ArrayList<Object> components = new ArrayList<Object>(legacyLines.size());
            for (String line : legacyLines) {
                components.add(deser.invoke(serializer, line));
            }
            try {
                meta.getClass().getMethod("lore", List.class).invoke(meta, components);
                return true;
            } catch (NoSuchMethodException ns) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Material resolveMaterial(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String key = name.trim().toUpperCase().replace(' ', '_');
        Material direct = Material.getMaterial(key);
        if (direct != null) {
            return direct;
        }
        if ((key.equals("PLAYER_HEAD") || key.equals("PLAYER_WALL_HEAD")) && Material.getMaterial("SKULL_ITEM") != null) {
            return Material.getMaterial("SKULL_ITEM");
        }
        if ((key.equals("RED_BED") || key.endsWith("_BED")) && Material.getMaterial("BED") != null) {
            return Material.getMaterial("BED");
        }
        if (key.equals("GRAY_STAINED_GLASS_PANE") && Material.getMaterial("STAINED_GLASS_PANE") != null) {
            return Material.getMaterial("STAINED_GLASS_PANE");
        }
        return null;
    }

    private String translate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        text = ChatColorMenu.normalizeUnderlineTags(text);
        ColorService service = this.colorService();
        if (service != null) {
            try {
                return service.translate(text, ColorMode.MINIMESSAGE);
            } catch (Throwable ignored) {
            }
        }
        return Messages.translate(text);
    }

    private ColorService colorService() {
        try {
            return this.plugin.getColorService();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean handleClick(int slot) {
        ColorCatalog.Entry modEntry = this.slotToModifier.get(slot);
        if (modEntry != null) {
            return this.handleModifierClick(modEntry);
        }
        ColorCatalog.Entry entry = this.slotToColor.get(slot);
        if (entry == null) {
            return false;
        }
        if (!entry.getPermission().isEmpty() && !this.viewer.hasPermission(entry.getPermission())) {
            this.messages.send((CommandSender) this.viewer, "no-color-permission", "{color}", this.translate(entry.getDisplayName()));
            return true;
        }
        String current = this.readCurrentColor();
        if (current != null && ChatColorMenu.matches(entry, current)) {
            this.clearColor(slot);
            return true;
        }
        this.selectColor(entry, slot);
        return true;
    }

    private boolean handleModifierClick(ColorCatalog.Entry entry) {
        String perm = entry.getPermission();
        if (perm != null && !perm.isEmpty() && !this.viewer.hasPermission(perm)) {
            this.messages.send((CommandSender) this.viewer, "no-modifier-permission", "{modifier}", entry.getName());
            return true;
        }
        this.toggleModifier(entry);
        return true;
    }

    private void toggleModifier(final ColorCatalog.Entry entry) {
        final UUID id = this.viewer.getUniqueId();
        final Player player = this.viewer;
        final SchedulerAdapter scheduler = this.plugin.getScheduler();
        Runnable storageTask = new Runnable() {
            @Override
            public void run() {
                boolean[] wasSelectedBox = new boolean[1];
                try {
                    String currentMods = ChatColorMenu.this.plugin.getStorage().getSelectedModifiers(id);
                    Set<String> set = ChatColorMenu.parseModifiersSet(currentMods);
                    String canon = entry.getValue().toLowerCase();
                    HashSet<String> lower = new HashSet<String>();
                    for (String s : set) {
                        lower.add(s.toLowerCase());
                    }
                    boolean wasSelected = lower.contains(canon);
                    wasSelectedBox[0] = wasSelected;
                    if (wasSelected) {
                        lower.remove(canon);
                    } else {
                        lower.add(canon);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String v : lower) {
                        String cv = ColorCatalog.canonicalizeModifier(v);
                        if (cv == null) {
                            cv = ColorCatalog.canonicalizeModifier("&" + v);
                        }
                        if (cv == null) continue;
                        if (sb.length() > 0) {
                            sb.append(",");
                        }
                        sb.append(cv);
                    }
                    String toSave = sb.toString();
                    if (toSave.isEmpty()) {
                        ChatColorMenu.this.plugin.getStorage().clearSelectedModifiers(id);
                    } else {
                        ChatColorMenu.this.plugin.getStorage().setSelectedModifiers(id, toSave);
                    }
                } catch (Throwable ex) {
                    ChatColorMenu.this.plugin.getLogger().warning("Could not toggle modifier for " + player.getName() + ": " + ex.getMessage());
                    return;
                }
                final boolean wasSelectedFinal = wasSelectedBox[0];
                Runnable sync = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!player.isOnline()) {
                                return;
                            }
                            ChatColorMenu open = ChatColorMenu.getOpenMenu(player);
                            if (open == ChatColorMenu.this) {
                                ChatColorMenu.this.open(player, ChatColorMenu.this.page);
                            }
                            String displayName = ChatColorMenu.this.translate(ChatColorMenu.this.applyModifierPlaceholders(entry.getDisplayName(), entry));
                            ChatColorMenu.this.messages.send((CommandSender) player, wasSelectedFinal ? "modifier-removed" : "modifier-set", "{modifier}", entry.getName(), "{name}", displayName);
                        } catch (Throwable t) {
                            ChatColorMenu.this.plugin.getLogger().warning("Failed to send modifier message to " + player.getName() + ": " + t.getMessage());
                        }
                    }
                };
                if (scheduler != null) {
                    scheduler.runEntity((Entity) player, sync);
                } else {
                    sync.run();
                }
            }
        };
        if (scheduler != null) {
            scheduler.runAsync(storageTask);
        } else {
            storageTask.run();
        }
    }

    private static Set<String> parseModifiersSet(String raw) {
        HashSet<String> set = new HashSet<String>();
        if (raw == null || raw.trim().isEmpty()) {
            return set;
        }
        String[] parts = raw.split(",");
        for (String p : parts) {
            p = p.trim().toLowerCase();
            if (p.isEmpty()) continue;
            if (p.contains("&") && !p.contains(",") && p.length() > 2) {
                String[] sub = p.split("&");
                for (String s : sub) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    set.add("&" + s.toLowerCase());
                }
                continue;
            }
            if (!p.startsWith("&") && p.length() == 1) {
                p = "&" + p;
            }
            set.add(p);
        }
        return set;
    }

    private void selectColor(final ColorCatalog.Entry entry, int clickedSlot) {
        final UUID id = this.viewer.getUniqueId();
        final Player player = this.viewer;
        final SchedulerAdapter scheduler = this.plugin.getScheduler();
        Runnable storageTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ChatColorMenu.this.plugin.getStorage().setSelectedColor(id, entry.getValue());
                } catch (Throwable ex) {
                    ChatColorMenu.this.plugin.getLogger().warning("Could not save color for " + player.getName() + ": " + ex.getMessage());
                    return;
                }
                Runnable sync = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!player.isOnline()) {
                                return;
                            }
                            try {
                                ChatColorMenu open = ChatColorMenu.getOpenMenu(player);
                                if (open == ChatColorMenu.this) {
                                    player.closeInventory();
                                    ChatColorMenu.closeMenu(player);
                                }
                            } catch (Throwable ignored) {
                            }
                            String preview = ChatColorMenu.this.preview(entry);
                            String displayName = ChatColorMenu.this.translate(entry.getDisplayName());
                            ChatColorMenu.this.messages.send((CommandSender) player, "color-set", "{color}", preview, "{value}", entry.getValue(), "{name}", displayName);
                        } catch (Throwable t) {
                            ChatColorMenu.this.plugin.getLogger().warning("Failed to send color-set to " + player.getName() + ": " + t.getMessage());
                        }
                    }
                };
                if (scheduler != null) {
                    scheduler.runEntity((Entity) player, sync);
                } else {
                    sync.run();
                }
            }
        };
        if (scheduler != null) {
            scheduler.runAsync(storageTask);
        } else {
            storageTask.run();
        }
    }

    private void clearColor(int clickedSlot) {
        final UUID id = this.viewer.getUniqueId();
        final Player player = this.viewer;
        final SchedulerAdapter scheduler = this.plugin.getScheduler();
        Runnable storageTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ChatColorMenu.this.plugin.getStorage().clearSelectedColor(id);
                } catch (Throwable ex) {
                    ChatColorMenu.this.plugin.getLogger().warning("Could not clear color for " + player.getName() + ": " + ex.getMessage());
                    return;
                }
                Runnable sync = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!player.isOnline()) {
                                return;
                            }
                            try {
                                ChatColorMenu open = ChatColorMenu.getOpenMenu(player);
                                if (open == ChatColorMenu.this) {
                                    player.closeInventory();
                                    ChatColorMenu.closeMenu(player);
                                }
                            } catch (Throwable ignored) {
                            }
                            ChatColorMenu.this.messages.send((CommandSender) player, "color-cleared", new String[0]);
                        } catch (Throwable t) {
                            ChatColorMenu.this.plugin.getLogger().warning("Failed to send color-cleared to " + player.getName() + ": " + t.getMessage());
                        }
                    }
                };
                if (scheduler != null) {
                    scheduler.runEntity((Entity) player, sync);
                } else {
                    sync.run();
                }
            }
        };
        if (scheduler != null) {
            scheduler.runAsync(storageTask);
        } else {
            storageTask.run();
        }
    }

    private String preview(ColorCatalog.Entry entry) {
        String value = entry.getValue();
        String label = this.plainName(entry);
        if (label.isEmpty()) {
            label = value;
        }
        if (RainbowColor.isRainbow(value)) {
            return RainbowColor.colorize(label, this.viewer != null && this.viewer.hasPermission("fchatcolor.chat.hex"));
        }
        if (io.github.fchatcolor.api.util.GradientColor.isGradient(value)) {
            java.util.List<String> palette = io.github.fchatcolor.api.util.GradientColor.parseColors(value);
            if (!palette.isEmpty()) {
                return io.github.fchatcolor.api.util.GradientColor.colorize(label, palette, this.viewer != null && this.viewer.hasPermission("fchatcolor.chat.hex"));
            }
        }
        ColorService service = this.colorService();
        if (service != null) {
            if (entry.isHex()) {
                return service.translate("&#" + value.substring(1) + label, ColorMode.HEX);
            }
            return service.translate("&" + value + label, ColorMode.LEGACY);
        }
        return entry.isHex() ? label : Messages.translate("&" + value + label);
    }

    private String plainName(ColorCatalog.Entry entry) {
        String name = entry.getDisplayName();
        if (name == null) {
            return "";
        }
        return MINIMESSAGE_TAG.matcher(name).replaceAll("").trim();
    }

    public void flipPage(boolean forward) {
        int perPage = Math.max(1, this.config.getColorsPerPage());
        int maxPage = ChatColorMenu.totalPages(this.plugin.getColors().entries().size(), perPage);
        int target = this.page + (forward ? 1 : -1);
        if (target < 0 || target >= maxPage) {
            return;
        }
        this.open(this.viewer, target);
    }

    private Inventory getTopInventoryReflective(Player p) {
        try {
            Object view = p.getClass().getMethod("getOpenInventory", new Class[0]).invoke(p, new Object[0]);
            return (Inventory) view.getClass().getMethod("getTopInventory", new Class[0]).invoke(view, new Object[0]);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
