package dev.tako.papersdelight.api.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

/**
 * 菜单服务契约, 由 PapersDelight 在启动时实现并注册到 Bukkit 服务管理器.
 * <p>附属插件通过下列方式取得实现, 不需要也不应该自行创建:
 * <pre>{@code
 * MenuService menus = Bukkit.getServicesManager().load(MenuService.class);
 * if (menus == null) {
 *     return;
 * }
 * menus.openMenu(player, "my_module");
 * }</pre>
 */
public interface MenuService {

    /** 注册菜单模块, 同 id 覆盖旧模块. */
    void registerModule(MenuModule module);

    /** 注销模块, 已打开的菜单不受影响. */
    void unregisterModule(String moduleId);

    /** 打开菜单, 模块不存在时静默返回. */
    void openMenu(Player player, String moduleId);

    /** 打开菜单, 玩家关闭时回调 {@code onClose}, 传 {@code null} 表示不需要. */
    void openMenu(Player player, String moduleId, Consumer<Inventory> onClose);

    /** 关闭玩家当前菜单. */
    void closeMenu(Player player);

    /** 关闭所有玩家当前打开的菜单. */
    void closeAll();

    /** 取当前实现, PapersDelight 未安装时返回 {@code null}. */
    static MenuService get() {
        return Bukkit.getServicesManager().load(MenuService.class);
    }
}
