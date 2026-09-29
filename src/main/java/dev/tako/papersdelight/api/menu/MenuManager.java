package dev.tako.papersdelight.api.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GUI 管理器: 注册 {@link MenuModule} 后按 moduleId 给玩家打开菜单, 点击/拖拽/关闭事件由它接管.
 * <p><strong>未注册槽位与非交互槽位都会被锁死</strong>, 物品不会随 GUI 丢失.
 */
public final class MenuManager implements Listener {

    private static MenuManager instance;
    private final Map<UUID, MenuSession> openSessions = new ConcurrentHashMap<>();
    private final Map<String, MenuModule> registeredModules = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastClickTick = new ConcurrentHashMap<>();

    private volatile BiFunction<String, String, String> messageResolver = (key, fallback) -> fallback;

    private MenuManager() {}

    public static MenuManager getInstance() {
        if (instance == null) instance = new MenuManager();
        return instance;
    }

    /** 注入错误日志文案, 由宿主在启动时调用; 传 {@code null} 恢复用 fallback. */
    public void setMessageResolver(BiFunction<String, String, String> resolver) {
        this.messageResolver = resolver != null ? resolver : (key, fallback) -> fallback;
    }

    private String message(String key, String fallback) {
        try {
            String resolved = messageResolver.apply(key, fallback);
            return resolved != null ? resolved : fallback;
        } catch (Throwable ignored) {
            // 文案解析失败不应掩盖真正要记录的 GUI 异常
            return fallback;
        }
    }

    public void registerModule(MenuModule module) {
        registeredModules.put(module.getId(), module);
    }

    /** 注销模块; 已打开的菜单不受影响. */
    public void unregisterModule(String moduleId) {
        registeredModules.remove(moduleId);
    }

    /** 解析菜单标题: 先按 MiniMessage, 失败时回退传统 & 色码. */
    private static Component parseTitle(String title) {
        try {
            return MiniMessage.miniMessage().deserialize(title);
        } catch (Throwable ignored) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(title);
        }
    }

    public void openMenu(Player player, String moduleId) {
        openMenu(player, moduleId, null);
    }

    /** 玩家自己关掉菜单时同样会回调 {@code onClose}, 传 {@code null} 表示不需要. */
    public void openMenu(Player player, String moduleId, Consumer<Inventory> onClose) {
        MenuModule module = registeredModules.get(moduleId);
        if (module == null) return;
        Menu menu = module.createMenu();
        Inventory inv = Bukkit.createInventory(null, menu.getSize(), parseTitle(menu.getTitle()));
        menu.getItems().forEach((slot, item) -> inv.setItem(slot, item.getItemStack()));
        player.openInventory(inv);
        openSessions.put(player.getUniqueId(), new MenuSession(module, menu, inv, onClose));
    }

    public void closeMenu(Player player) {
        openSessions.remove(player.getUniqueId());
        lastClickTick.remove(player.getUniqueId());
        player.closeInventory();
    }

    public void closeAll() {
        for (UUID playerId : openSessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.closeInventory();
            openSessions.remove(playerId);
        }
        lastClickTick.clear();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;

        if (!session.inventory.equals(event.getView().getTopInventory())) return;

        int slot = event.getRawSlot();
        if (slot < 0) return;

        // 同 tick 只处理第一个点击,后续忽略——防止一键整理瞬间多发点击触发 GUI 切换
        int currentTick = Bukkit.getCurrentTick();
        Integer lastTick = lastClickTick.put(player.getUniqueId(), currentTick);
        if (lastTick != null && lastTick == currentTick) {
            event.setCancelled(true);
            return;
        }

        if (slot >= session.menu().getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                quickMoveIntoInteractiveSlots(session, player, event);
            }
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        MenuItem menuItem = session.menu().getItemAt(slot);

        if (menuItem == null) {
            // 未注册的槽位默认锁定,防止物品被放入/取出后丢失
            event.setCancelled(true);
            return;
        }

        if (menuItem.isInteractive()) {
            tryCallHandler(session, player, menuItem, event);
            return;
        }

        event.setCancelled(true);
        tryCallHandler(session, player, menuItem, event);
    }

    private void tryCallHandler(MenuSession session, Player player, MenuItem menuItem,
                                InventoryClickEvent event) {
        try {
            session.module().getEventHandler().handle(player, menuItem, event);
        } catch (Exception e) {
            Logger logger = Bukkit.getLogger();
            logger.log(Level.SEVERE,
                    message("gui_click_err",
                            "Error handling GUI click for module '%module%', actionId '%action%'")
                            .replace("%module%", session.module().getId())
                            .replace("%action%", menuItem.getActionId()), e);
        }
    }

    private void quickMoveIntoInteractiveSlots(MenuSession session, Player player, InventoryClickEvent event) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.isEmpty()) return;

        ItemStack remainder = moving.clone();
        Inventory target = session.inventory();
        int size = session.menu().getSize();
        String targetAction = quickMoveTargetAction(session.module().getId(), moving);

        for (int slot = 0; slot < size && remainder.getAmount() > 0; slot++) {
            MenuItem menuItem = session.menu().getItemAt(slot);
            if (!canQuickMoveInto(menuItem, targetAction)) continue;
            if (!session.module().getEventHandler().canQuickMove(player, menuItem, remainder)) continue;

            ItemStack existing = target.getItem(slot);
            if (existing == null || existing.isEmpty()) continue;
            if (!existing.isSimilar(remainder)) continue;

            int maxStackSize = Math.min(existing.getMaxStackSize(), target.getMaxStackSize());
            int room = maxStackSize - existing.getAmount();
            if (room <= 0) continue;

            int moved = Math.min(room, remainder.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            remainder.setAmount(remainder.getAmount() - moved);
            target.setItem(slot, existing);
        }

        for (int slot = 0; slot < size && remainder.getAmount() > 0; slot++) {
            MenuItem menuItem = session.menu().getItemAt(slot);
            if (!canQuickMoveInto(menuItem, targetAction)) continue;
            if (!session.module().getEventHandler().canQuickMove(player, menuItem, remainder)) continue;

            ItemStack existing = target.getItem(slot);
            if (existing != null && !existing.isEmpty()) continue;

            int moved = Math.min(remainder.getMaxStackSize(), remainder.getAmount());
            ItemStack placed = remainder.clone();
            placed.setAmount(moved);
            target.setItem(slot, placed);
            remainder.setAmount(remainder.getAmount() - moved);
        }

        event.setCurrentItem(remainder.getAmount() <= 0 ? null : remainder);
    }

    static String quickMoveTargetAction(String moduleId, ItemStack moving) {
        if ("jug".equals(moduleId)) return "input_slot";
        return moving.getType() == Material.BOWL ? "utensil_slot" : "ingredient_slot";
    }

    private boolean canQuickMoveInto(MenuItem menuItem, String targetAction) {
        return menuItem != null
                && menuItem.isInteractive()
                && targetAction.equals(menuItem.getActionId());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!session.inventory.equals(event.getView().getTopInventory())) return;

        int menuSize = session.menu().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= menuSize) continue;

            MenuItem menuItem = session.menu().getItemAt(rawSlot);
            if (menuItem == null || !menuItem.isInteractive()) {
                // 未注册的槽位或非交互槽位:禁止拖入
                event.setCancelled(true);
                return;
            }
            tryCallDragHandler(session, player, menuItem, event);
            if (event.isCancelled()) return;
        }
    }

    private void tryCallDragHandler(MenuSession session, Player player, MenuItem menuItem,
                                    InventoryDragEvent event) {
        try {
            session.module().getEventHandler().handleDrag(player, menuItem, event);
        } catch (Exception e) {
            Logger logger = Bukkit.getLogger();
            logger.log(Level.SEVERE,
                    message("gui_drag_err",
                            "Error handling GUI drag for module '%module%', actionId '%action%'")
                            .replace("%module%", session.module().getId())
                            .replace("%action%", menuItem.getActionId()), e);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        MenuSession session = openSessions.get(player.getUniqueId());
        if (session == null) return;
        if (!session.inventory.equals(event.getInventory())) return;
        openSessions.remove(player.getUniqueId());
        lastClickTick.remove(player.getUniqueId());

        if (session.onClose != null) {
            session.onClose.accept(event.getInventory());
        }
    }

    private record MenuSession(MenuModule module, Menu menu, Inventory inventory,
                               Consumer<Inventory> onClose) {}
}
