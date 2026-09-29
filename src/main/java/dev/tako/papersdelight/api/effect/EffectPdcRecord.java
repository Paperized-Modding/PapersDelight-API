package dev.tako.papersdelight.api.effect;

/**
 * 从玩家 PDC 读回的限时效果快照.
 * <p>存的是剩余 tick 而不是到期时间戳, 所以离线期间倒计时冻结, 重连后从下线那一刻的剩余量继续.
 *
 * @param remainingTicks 下线时的剩余 tick
 * @param totalTicks     原始总时长, 用于还原 BossBar 进度
 * @param amplifier      效果等级
 * @param extra          子类的额外状态, 可为空数组
 */
public record EffectPdcRecord(int remainingTicks, int totalTicks, int amplifier, byte[] extra) {
}
