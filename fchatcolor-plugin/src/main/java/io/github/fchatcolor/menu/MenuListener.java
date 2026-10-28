/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 */
package io.github.fchatcolor.menu;

import io.github.fchatcolor.menu.ChatColorMenu;
import io.github.fchatcolor.menu.MenuConfig;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuListener
implements Listener {
    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        ChatColorMenu menu = MenuListener.menuOf(top);
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals((Object)top)) {
            return;
        }
        int slot = event.getRawSlot();
        MenuConfig.Button close = menu.getConfig().getClose();
        MenuConfig.Button prev = menu.getConfig().getPreviousPage();
        MenuConfig.Button next = menu.getConfig().getNextPage();
        if (MenuListener.matches(close, slot)) {
            clicker.closeInventory();
            return;
        }
        if (MenuListener.matches(prev, slot)) {
            menu.flipPage(false);
            return;
        }
        if (MenuListener.matches(next, slot)) {
            menu.flipPage(true);
            return;
        }
        menu.handleClick(slot);
    }

    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void onInventoryDrag(InventoryDragEvent event) {
        ChatColorMenu menu = MenuListener.menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
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
        return holder instanceof ChatColorMenu ? (ChatColorMenu)holder : null;
    }

    private static boolean matches(MenuConfig.Button button, int slot) {
        return button != null && button.isEnabled() && button.getSlot() == slot;
    }
}

