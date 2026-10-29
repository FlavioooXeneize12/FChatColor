package io.github.fchatcolor.menu;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Method;

public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = getTopInventory(event);
        ChatColorMenu menu = menuOf(top);
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }
        int slot = event.getRawSlot();
        MenuConfig.Button close = menu.getConfig().getClose();
        MenuConfig.Button prev = menu.getConfig().getPreviousPage();
        MenuConfig.Button next = menu.getConfig().getNextPage();
        if (matches(close, slot)) {
            clicker.closeInventory();
            return;
        }
        if (matches(prev, slot)) {
            menu.flipPage(false);
            return;
        }
        if (matches(next, slot)) {
            menu.flipPage(true);
            return;
        }
        menu.handleClick(slot);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = getTopInventory(event);
        ChatColorMenu menu = menuOf(top);
        if (menu == null) {
            return;
        }
        int topSize = getTopSize(event, top);
        for (Integer raw : event.getRawSlots()) {
            if (raw == null || raw >= topSize) continue;
            event.setCancelled(true);
            return;
        }
    }

    private static ChatColorMenu menuOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof ChatColorMenu ? (ChatColorMenu) holder : null;
    }

    private static boolean matches(MenuConfig.Button button, int slot) {
        return button != null && button.isEnabled() && button.getSlot() == slot;
    }

    // Reflective helpers to handle InventoryView being a class (1.8) vs interface (1.14+ / Paper 26.2)
    // Compiling against 1.8 generates INVOKEVIRTUAL; on Paper it must be INVOKEINTERFACE -> IncompatibleClassChangeError
    private static Inventory getTopInventory(Object event) {
        try {
            Method getView = event.getClass().getMethod("getView");
            Object view = getView.invoke(event);
            Method getTop = view.getClass().getMethod("getTopInventory");
            return (Inventory) getTop.invoke(view);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int getTopSize(Object event, Inventory fallback) {
        Inventory top = getTopInventory(event);
        if (top != null) return top.getSize();
        return fallback != null ? fallback.getSize() : 0;
    }
}
