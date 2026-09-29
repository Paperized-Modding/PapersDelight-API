package dev.tako.papersdelight.api.effect;

import net.kyori.adventure.bossbar.BossBar;

/**
 * 限时效果的运行时会话。
 *
 * <p>由 {@link TimedEffectManager} 维护，记录 BossBar、到期的内部 tick、
 * 施加时的总时长（用于还原进度条）以及等级（BAC 效果按等级变化，PD 效果恒为 0）。</p>
 *
 * @param bossBar            该效果的 BossBar
 * @param endTick            到期时的内部 tick（相对 {@link TimedEffectManager} 自身的 internalTick）
 * @param totalDurationTicks 施加时的总时长 tick，用于 BossBar 进度还原
 * @param amplifier          效果等级（0 起），影响子类的强度计算
 */
public record TimedEffectSession(BossBar bossBar, int endTick, int totalDurationTicks, int amplifier) {
}
