package dev.tako.papersdelight.api.effect;

/**
 * 从玩家 PDC 读回的限时效果快照, 存的是剩余 tick 而不是到期时间戳, 离线期间冻结, 重连后接着走.
 *
 * @param totalTicks 原始总时长, 用于还原 BossBar 进度
 */
public record EffectPdcRecord(int remainingTicks, int totalTicks, int amplifier, byte[] extra) {
}
