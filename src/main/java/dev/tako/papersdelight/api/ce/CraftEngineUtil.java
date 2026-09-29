package dev.tako.papersdelight.api.ce;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.world.BukkitWorld;
import net.momirealms.craftengine.bukkit.world.BukkitWorldManager;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.pack.PackManager;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CraftEngine 工具: 查/造自定义方块与物品; CE 没装或未就绪时不抛异常, 按各方法文档返回兜底值.
 * <p><strong>方块状态与属性的读写必须在方块所属的 region/chunk 线程调用</strong>.
 */
public final class CraftEngineUtil {
    private static final Map<String, Material> BASE_MATERIAL_CACHE = new ConcurrentHashMap<>();

    private CraftEngineUtil() {}

    public static boolean isCraftEngineInstalled(Plugin plugin) {
        if (plugin == null) return false;
        return plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null;
    }

    public static boolean isCraftEngineEnabled(Plugin plugin) {
        if (plugin == null) return false;
        Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        return craftEngine != null && craftEngine.isEnabled();
    }

    public static CraftEngine getCraftEngineInstanceIfReady() {
        try {
            return CraftEngine.instance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static PackManager getPackManagerIfReady(Plugin plugin) {
        if (!isCraftEngineInstalled(plugin)) return null;
        CraftEngine craftEngine = getCraftEngineInstanceIfReady();
        return craftEngine == null ? null : craftEngine.packManager();
    }

    public static String getCustomBlockId(Block block) {
        ImmutableBlockState state = getCustomBlockState(block);
        if (state == null) return null;
        return state.owner().value().id().toString();
    }

    /** 只在方块所在区块已被 CE 加载时才有值; 非自定义方块或未加载时返回 {@code null}. */
    public static ImmutableBlockState getCustomBlockState(Block block) {
        if (block == null) return null;
        try {
            BukkitWorldManager manager = BukkitWorldManager.instance();
            if (manager != null && manager.initialized()) {
                CEWorld ceWorld = getLoadedWorld(manager, block.getWorld());
                if (ceWorld == null) return null;

                ImmutableBlockState state = ceWorld.getBlockStateAtIfLoaded(
                        block.getX(), block.getY(), block.getZ());
                return state == null || state.isEmpty() ? null : state;
            }
        } catch (Exception ignored) {
        }

        try {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            return state == null || state.isEmpty() ? null : state;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static CEChunk getLoadedChunk(World world, int chunkX, int chunkZ) {
        CEWorld ceWorld = getLoadedWorld(world);
        return ceWorld == null ? null : ceWorld.getChunkAtIfLoaded(chunkX, chunkZ);
    }

    public static CEWorld getLoadedWorld(World world) {
        if (world == null) return null;
        try {
            BukkitWorldManager manager = BukkitWorldManager.instance();
            if (manager == null || !manager.initialized()) return null;
            return getLoadedWorld(manager, world);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static CEWorld getLoadedWorld(BukkitWorldManager manager, World world) {
        // CE 26.8:getWorld(UUID) 返回 BukkitWorld,其 storageWorld() 才是 CEWorld;
        // 未加载的世界返回 null,因此不再需要遍历兜底.
        BukkitWorld bukkitWorld = manager.getWorld(world.getUID());
        if (bukkitWorld == null) return null;
        CEWorld ceWorld = bukkitWorld.storageWorld();
        if (ceWorld == null) return null;
        return world.getUID().equals(ceWorld.uuid()) ? ceWorld : null;
    }

    public static boolean isCustomBlock(Block block, String id) {
        if (id == null || id.isEmpty()) return false;
        return id.equals(getCustomBlockId(block));
    }

    public static String getBlockEntityId(BlockEntity entity) {
        if (entity == null) return null;
        ImmutableBlockState state = entity.blockState();
        if (state == null || state.isEmpty()) return null;
        return state.owner().value().id().toString();
    }

    public static boolean isBlockEntity(BlockEntity entity, String id) {
        if (id == null || id.isEmpty()) return false;
        return id.equals(getBlockEntityId(entity));
    }

    public static boolean matchesAnyBlock(Block block, Collection<String> ids) {
        String blockId = getCustomBlockId(block);
        return blockId != null && ids.contains(blockId);
    }

    public static boolean matchesAnyBlockEntity(BlockEntity entity, Collection<String> ids) {
        String blockId = getBlockEntityId(entity);
        return blockId != null && ids.contains(blockId);
    }

    /** CE 尚未加载任何方块时跳过校验, 一律返回 {@code true}. */
    public static boolean isValidBlockId(String ceBlockId) {
        if (ceBlockId == null || ceBlockId.isEmpty()) return false;
        try {
            Key key = Key.of(ceBlockId);
            boolean found = CraftEngineBlocks.byId(key) != null;
            if (found) return true;
            // byId 返回 null:可能是 ID 无效,也可能是 CraftEngine 尚未完成注册
            // 如果 loadedBlocks() 为空,说明还在启动阶段,跳过验证
            return CraftEngineBlocks.loadedBlocks().isEmpty();
        } catch (Exception e) {
            // 异常也说明 CraftEngine 可能尚未就绪,跳过验证
            return true;
        }
    }

    /** 对应的原版 base Material(如 NOTE_BLOCK); CE 未加载或 ID 不存在时返回 {@code null}. */
    public static Material getBaseMaterial(String ceBlockId) {
        if (ceBlockId == null || ceBlockId.isEmpty()) return null;
        Material cached = BASE_MATERIAL_CACHE.get(ceBlockId);
        if (cached != null) return cached;
        try {
            BlockDefinition def = CraftEngineBlocks.byId(Key.of(ceBlockId));
            if (def == null) return null;
            Material material = CraftEngineBlocks.getBukkitBlockData(def.defaultState()).getMaterial();
            if (material != null) BASE_MATERIAL_CACHE.put(ceBlockId, material);
            return material;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean placeCustomBlock(Block block, String id) {
        if (block == null || id == null || id.isEmpty()) return false;
        return CraftEngineBlocks.place(block.getLocation(), Key.of(id), true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean advanceCustomBlockIntProperty(Block block, String propertyName, int maxValue) {
        int currentValue = getCustomBlockIntProperty(block, propertyName, -1);
        if (currentValue < 0 || currentValue >= maxValue) return false;
        return setCustomBlockProperty(block, propertyName, String.valueOf(currentValue + 1));
    }

    public static int getCustomBlockIntProperty(Block block, String propertyName, int fallback) {
        String value = getCustomBlockProperty(block, propertyName);
        return value == null ? fallback : parseInt(value, fallback);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static String getCustomBlockProperty(Block block, String propertyName) {
        if (block == null || propertyName == null || propertyName.isEmpty()) return null;

        ImmutableBlockState state = getCustomBlockState(block);
        if (state == null) return null;

        Property property = state.getProperty(propertyName);
        if (property == null) return null;

        Comparable value = state.get(property);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean setCustomBlockProperty(Block block, String propertyName, String valueName) {
        if (block == null || propertyName == null || propertyName.isEmpty()
                || valueName == null || valueName.isEmpty()) return false;

        try {
            ImmutableBlockState state = getCustomBlockState(block);
            if (state == null) return false;

            Property property = state.getProperty(propertyName);
            if (property == null) return false;

            Comparable nextValue = property.valueByName(valueName);
            if (nextValue == null) return false;

            ImmutableBlockState nextState = state.with(property, nextValue);
            return CraftEngineBlocks.place(block.getLocation(), nextState, true);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isItem(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty() || id == null || id.isEmpty()) return false;

        // 标签匹配(#namespace:tag_name)
        if (id.startsWith("#")) {
            // CE 自定义物品标签优先(CE 物品可能在 settings.tags 中定义此标签)
            var def = CraftEngineItems.byItemStack(stack);
            if (def != null && def.is(Key.of(id.substring(1)))) return true;

            // Bukkit Material 标签(仅匹配非 CE 自定义物品,避免 base material 误判)
            if (!CraftEngineItems.isCustomItem(stack)) {
                NamespacedKey tagKey = NamespacedKey.fromString(id.substring(1));
                if (tagKey != null) {
                    Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, tagKey, Material.class);
                    if (tag != null && tag.isTagged(stack.getType())) return true;
                }
            }
            return false;
        }

        // CE 自定义物品:先检查 CE ID,不做 Material fallback(避免 base material 误判)
        if (CraftEngineItems.isCustomItem(stack)) {
            Key key = CraftEngineItems.getCustomItemId(stack);
            return id.equals(key.toString()) || id.equals(key.value());
        }

        // 原版物品:Material 匹配
        Material material = materialFromId(id);
        if (material != null && stack.getType() == material) return true;

        return false;
    }

    public static String getCustomItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!CraftEngineItems.isCustomItem(stack)) return null;

        Key key = CraftEngineItems.getCustomItemId(stack);
        return key == null ? null : key.toString();
    }

    public static boolean matchesAnyItem(ItemStack stack, Collection<String> ids) {
        for (String id : ids) {
            if (isItem(stack, id)) return true;
        }
        return false;
    }

    /** CE 物品给 CE ID, 原版物品给 Minecraft key, 配合 {@link #createItem(String, int)} 可还原. */
    public static String getItemIdentifier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String ceId = getCustomItemId(stack);
        return ceId != null ? ceId : stack.getType().getKey().toString();
    }

    /** 制作后返还的容器 ID(炖菜返还碗, 饮品返还玻璃瓶); 没有返还物或查询失败时返回 {@code null}. */
    public static String getCraftRemainderId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;

        // CE 自定义物品:读 ItemSettings 里的 craft_remainder 配置
        if (!itemId.startsWith("minecraft:")) {
            try {
                var definition = CraftEngineItems.byId(itemId);
                if (definition != null) {
                    var remainder = definition.settings().craftRemainder();
                    if (remainder != null) {
                        // hurt_and_break 类型会解引用第二个参数取耐久,必须传真实 Item
                        var source = net.momirealms.craftengine.core.item.Item.byId(Key.of(itemId));
                        if (source != null) {
                            // recipeId 仅被 recipe_based 类型用于查表,这里没有真实配方
                            // 上下文,传物品自身 ID 让它走 fallback 分支
                            var result = remainder.remainder(Key.of(itemId), source);
                            // hurt_and_break 耐久耗尽时返回 count=0,视作无返还物
                            if (result != null && result.count() > 0) {
                                Key resultId = result.id();
                                if (resultId != null) return resultId.toString();
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // CE 未就绪 / API 变动 / remainder 实现抛异常 —— 落到原版逻辑
            }
        }

        // 原版物品:走 Bukkit 的 craftingRemainingItem
        try {
            Material material = materialFromId(itemId);
            if (material != null && material.isItem()) {
                Material remaining = material.getCraftingRemainingItem();
                if (remaining != null) return remaining.getKey().toString();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static ItemStack createItem(String id, int amount) {
        if (id == null || id.isEmpty()) return null;
        int safeAmount = Math.max(1, amount);

        if (id.contains(":") && !id.startsWith("minecraft:")) {
            try {
                var definition = CraftEngineItems.byId(id);
                if (definition != null) {
                    ItemStack item = definition.buildBukkitItem();
                    item.setAmount(safeAmount);
                    return item;
                }
            } catch (Exception ignored) {
            }
        }

        Material material = materialFromId(id);
        return material == null ? null : new ItemStack(material, safeAmount);
    }

    public static Material materialFromId(String id) {
        if (id == null || id.isEmpty()) return null;
        String name = id.toLowerCase(Locale.ROOT).startsWith("minecraft:")
                ? id.substring("minecraft:".length())
                : id;
        if (name.contains(":")) return null;
        try {
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
