package dev.tako.papersdelight.api.effect;

/**
 * 从玩家 PDC 读回的限时效果快照。
 *
 * <p>存的是「剩余 tick」而非到期时间戳，因此离线期间倒计时冻结，
 * 重连后从下线那一刻的剩余量继续（与 PD 原 SQLite 持久化语义一致）。</p>
 *
 * @param remainingTicks 下线时的剩余 tick
 * @param totalTicks     原始总时长，用于 BossBar 进度还原
 * @param amplifier      效果等级
 * @param extra          子类的额外状态（如 Tipsy 的 numbedHealth、Raging 的 stacks），可为空数组
 */
public record EffectPdcRecord(int remainingTicks, int totalTicks, int amplifier, byte[] extra) {
}
