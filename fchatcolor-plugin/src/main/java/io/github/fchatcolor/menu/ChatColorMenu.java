/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package io.github.fchatcolor.menu;

import io.github.fchatcolor.FChatColorPlugin;
import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.scheduler.SchedulerAdapter;
import io.github.fchatcolor.api.util.RainbowColor;
import io.github.fchatcolor.colors.ColorCatalog;
import io.github.fchatcolor.menu.HeadTextures;
import io.github.fchatcolor.menu.MenuConfig;
import io.github.fchatcolor.messages.Messages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ChatColorMenu
implements InventoryHolder {
    private static final String RESET = "\u00a7r";
    private static final Pattern MINIMESSAGE_TAG = Pattern.compile("<[^<>]*>");
    private static final Map<UUID, ChatColorMenu> OPEN_MENUS = new java.util.concurrent.ConcurrentHashMap<UUID, ChatColorMenu>();
    private final FChatColorPlugin plugin;
    private final Messages messages;
    private MenuConfig config;
    private Inventory inventory;
    private int page;
    private Player viewer;
    private final Map<Integer, ColorCatalog.Entry> slotToColor = new HashMap<Integer, ColorCatalog.Entry>();

    public static ChatColorMenu getOpenMenu(Player player) {
        if (player == null) return null;
        return OPEN_MENUS.get(player.getUniqueId());
    }

    public static ChatColorMenu getOpenMenu(UUID id) {
        if (id == null) return null;
        return OPEN_MENUS.get(id);
    }

    public static void setOpenMenu(Player player, ChatColorMenu menu) {
        if (player == null) return;
        if (menu == null) OPEN_MENUS.remove(player.getUniqueId());
        else OPEN_MENUS.put(player.getUniqueId(), menu);
    }

    public static void closeMenu(Player player) {
        if (player == null) return;
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
        this.inventory = Bukkit.createInventory((InventoryHolder)this, (int)size, (String)title);
        this.slotToColor.clear();
        this.fillBackground();
        this.renderColors(all, perPage);
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

    private ItemStack buildColorItem(ColorCatalog.Entry entry) {
        MenuConfig.ItemFormat format = this.chooseFormat(entry);
        String materialName = ChatColorMenu.resolveMaterial(format.getMaterial(), entry);
        String name = ChatColorMenu.applyPlaceholders(format.getName(), entry);
        ArrayList<String> lore = new ArrayList<String>();
        for (String line : format.getLore()) {
            lore.add(ChatColorMenu.applyPlaceholders(line, entry));
        }
        ItemStack item = this.makeItem(materialName, name, lore);
        if (item != null && entry.getTexture() != null && !entry.getTexture().isEmpty()) {
            HeadTextures.apply(item, entry.getTexture());
        }
        return item;
    }

    private MenuConfig.ItemFormat chooseFormat(ColorCatalog.Entry entry) {
        boolean hasPermission;
        String current = this.readCurrentColor();
        boolean isCurrent = current != null && ChatColorMenu.matches(entry, current);
        boolean bl = hasPermission = entry.getPermission().isEmpty() || this.viewer.hasPermission(entry.getPermission());
        if (isCurrent) {
            return hasPermission ? this.config.getUnselect() : this.config.getSelected();
        }
        return hasPermission ? this.config.getSelect() : this.config.getNoPermission();
    }

    private String readCurrentColor() {
        try {
            return this.plugin.getStorage().getSelectedColor(this.viewer.getUniqueId());
        }
        catch (Throwable t) {
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
        String material;
        String string = material = template == null ? "" : template.trim();
        if (material.indexOf(123) >= 0) {
            material = material.replace("{color_material}", entry.getMaterial());
        }
        return material.isEmpty() ? "PLAYER_HEAD" : material;
    }

    private static String applyPlaceholders(String text, ColorCatalog.Entry entry) {
        if (text == null) {
            return "";
        }
        return text.replace("{color_name}", entry.getDisplayName()).replace("{color_material}", entry.getMaterial()).replace("{permission}", entry.getPermission());
    }

    private ItemStack makeItem(String materialName, String displayName, List<String> lore) {
        Material material = ChatColorMenu.resolveMaterial(materialName);
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.trim().isEmpty()) {
                meta.setDisplayName(RESET + this.translate(displayName.trim()));
            }
            if (lore != null && !lore.isEmpty()) {
                ArrayList<String> translated = new ArrayList<String>(lore.size());
                for (String line : lore) {
                    translated.add(RESET + this.translate(line));
                }
                meta.setLore(translated);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Material resolveMaterial(String name) {
        Material pane;
        Material bed;
        Material skull;
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String key = name.trim().toUpperCase().replace(' ', '_');
        Material direct = Material.getMaterial((String)key);
        if (direct != null) {
            return direct;
        }
        if ((key.equals("PLAYER_HEAD") || key.equals("PLAYER_WALL_HEAD")) && (skull = Material.getMaterial((String)"SKULL_ITEM")) != null) {
            return skull;
        }
        if ((key.equals("RED_BED") || key.endsWith("_BED")) && (bed = Material.getMaterial((String)"BED")) != null) {
            return bed;
        }
        if (key.equals("GRAY_STAINED_GLASS_PANE") && (pane = Material.getMaterial((String)"STAINED_GLASS_PANE")) != null) {
            return pane;
        }
        return null;
    }

    private String translate(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        ColorService service = this.colorService();
        if (service != null) {
            try {
                return service.translate(text, ColorMode.MINIMESSAGE);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return Messages.translate(text);
    }

    private ColorService colorService() {
        try {
            return this.plugin.getColorService();
        }
        catch (Throwable t) {
            return null;
        }
    }

    public boolean handleClick(int slot) {
        ColorCatalog.Entry entry = this.slotToColor.get(slot);
        if (entry == null) {
            return false;
        }
        if (!entry.getPermission().isEmpty() && !this.viewer.hasPermission(entry.getPermission())) {
            this.messages.send((CommandSender)this.viewer, "no-color-permission", "{color}", entry.getDisplayName());
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

    private void selectColor(final ColorCatalog.Entry entry, final int clickedSlot) {
        final String previous = readCurrentColor();
        final UUID id = viewer.getUniqueId();
        final Player player = viewer;
        final SchedulerAdapter scheduler = plugin.getScheduler();
        Runnable storageTask = new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.getStorage().setSelectedColor(id, entry.getValue());
                } catch (Throwable ex) {
                    Runnable err = new Runnable() {
                        @Override
                        public void run() {
                            messages.send(player, "storage-error", new String[0]);
                        }
                    };
                    if (scheduler != null) {
                        scheduler.runEntity(player, err);
                    } else {
                        err.run();
                    }
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
                            String preview = preview(entry);
                            String displayName = translate(entry.getDisplayName());
                            messages.send(player, "color-set", "{color}", preview, "{value}", entry.getValue(), "{name}", displayName);
                        } catch (Throwable t) {
                            messages.send(player, "storage-error", new String[0]);
                        }
                    }
                };
                if (scheduler != null) {
                    scheduler.runEntity(player, sync);
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

    private void clearColor(final int clickedSlot) {
        final String previous = readCurrentColor();
        final UUID id = viewer.getUniqueId();
        final Player player = viewer;
        final SchedulerAdapter scheduler = plugin.getScheduler();
        Runnable storageTask = new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.getStorage().clearSelectedColor(id);
                } catch (Throwable ex) {
                    Runnable err = new Runnable() {
                        @Override
                        public void run() {
                            messages.send(player, "storage-error", new String[0]);
                        }
                    };
                    if (scheduler != null) {
                        scheduler.runEntity(player, err);
                    } else {
                        err.run();
                    }
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
                            messages.send(player, "color-cleared", new String[0]);
                        } catch (Throwable t) {
                            messages.send(player, "storage-error", new String[0]);
                        }
                    }
                };
                if (scheduler != null) {
                    scheduler.runEntity(player, sync);
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
            Object view = p.getClass().getMethod("getOpenInventory").invoke(p);
            return (Inventory) view.getClass().getMethod("getTopInventory").invoke(view);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

