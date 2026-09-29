package dev.tako.papersdelight.api.menu;

import org.bukkit.inventory.ItemStack;

public class MenuItem {

    private final ItemStack itemStack;
    private final String actionId;
    private final boolean interactive;

    /**
     * @param itemStack 显示的物品, 内部会 clone
     * @param actionId 行为标识, 事件分发用
     * @param interactive 是否允许玩家自由存取(false 表示点击被取消, 只触发事件)
     */
    public MenuItem(ItemStack itemStack, String actionId, boolean interactive) {
        this.itemStack = itemStack.clone();
        this.actionId = actionId;
        this.interactive = interactive;
    }

    /** 默认不可交互, 用于静态展示物与按钮. */
    public MenuItem(ItemStack itemStack, String actionId) {
        this(itemStack, actionId, false);
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public String getActionId() {
        return actionId;
    }

    /** 是否允许玩家自由存取物品: {@code false} 时点击被取消, 物品锁在槽里. */
    public boolean isInteractive() {
        return interactive;
    }
}
