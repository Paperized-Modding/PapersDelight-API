package dev.tako.papersdelight.api.item;

/** 配方产物: 物品 ID, 数量与产出概率; 构造时数量至少为 1, 概率被钳到 [0, 1]. */
public record ItemResult(String item, int count, double chance) {
    public ItemResult {
        count = Math.max(1, count);
        chance = Math.max(0, Math.min(1, chance)); // 钳制到 [0, 1]
    }

    public ItemResult(String item, int count) {
        this(item, count, 1.0);
    }
}
