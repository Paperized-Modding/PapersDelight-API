package dev.tako.papersdelight.api.menu;

import org.bukkit.inventory.ItemStack;

public class MenuItem {

    private final ItemStack itemStack;
    private final String actionId;
    private final boolean interactive;

    /**
     * @param itemStack 显示的物品
     * @param actionId  行为标识，用于事件分发
     * @param interactive 是否允许玩家自由存取物品（false 表示点击被取消，仅触发事件处理）
     */
    public MenuItem(ItemStack itemStack, String actionId, boolean interactive) {
        this.itemStack = itemStack.clone();
        this.actionId = actionId;
        this.interactive = interactive;
    }

    /** 兼容旧代码：默认不可交互（静态展示物/按钮） */
    public MenuItem(ItemStack itemStack, String actionId) {
        this(itemStack, actionId, false);
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public String getActionId() {
        return actionId;
    }

    /**
     * 该槽位是否允许玩家自由放进/取出物品。
     * true  → 不取消点击，玩家可以正常移动物品
     * false → 取消点击，物品被锁定在槽位中（装饰物/按钮）
     */
    public boolean isInteractive() {
        return interactive;
    }
}
