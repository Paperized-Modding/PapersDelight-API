package dev.example.papersdelightaddon;

import dev.tako.papersdelight.api.menu.Menu;
import dev.tako.papersdelight.api.menu.MenuItem;
import dev.tako.papersdelight.api.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ExampleMenu {

    public static final String MODULE_ID = "example";
    public static final String ACTION_APPLY = "apply";
    public static final String ACTION_CLEAR = "clear";

    private ExampleMenu() {
    }

    public static Menu create() {
        Menu menu = new Menu("<!i><white>ExampleAddon", 27);
        menu.setItem(11, new MenuItem(named(Material.GLOWSTONE_DUST, "<!i><yellow>Apply glow (10s)"), ACTION_APPLY));
        menu.setItem(15, new MenuItem(named(Material.GLASS, "<!i><white>Clear glow"), ACTION_CLEAR));
        return menu;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.displayName(TextUtil.parse(name)));
        return stack;
    }
}
