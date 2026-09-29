package dev.tako.papersdelight.api.item;

/**
 * 配方产物定义：物品 ID、数量与产出概率。
 *
 * <p>迁入 api 模块时移除了原有的 {@code @Include} ZKM 标记 ——
 * api 包已整体豁免混淆，强制混淆标记在此语义矛盾。</p>
 */
public record ItemResult(String item, int count, double chance) {
    public ItemResult {
        count = Math.max(1, count);
        chance = Math.max(0, Math.min(1, chance)); // 钳制到 [0, 1]
    }

    /** 向后兼容构造：无概率 = 100% */
    public ItemResult(String item, int count) {
        this(item, count, 1.0);
    }
}
