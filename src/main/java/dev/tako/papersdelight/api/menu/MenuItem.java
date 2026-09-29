package dev.tako.papersdelight.api.menu;

import org.bukkit.inventory.ItemStack;

public class MenuItem {

    private final ItemStack itemStack;
    private final String actionId;
    private final boolean interactive;

    /** {@code itemStack} 会 clone; {@code interactive=false} 表示点击被取消, 只触发事件. */
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

    public boolean isInteractive() {
        return interactive;
    }
}
