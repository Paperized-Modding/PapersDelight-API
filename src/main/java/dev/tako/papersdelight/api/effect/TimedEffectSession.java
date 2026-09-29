package dev.tako.papersdelight.api.effect;

import net.kyori.adventure.bossbar.BossBar;

/**
 * 一个玩家身上生效中的限时效果会话, 由 {@link TimedEffectManager} 维护, 一般不用自己创建.
 *
 * @param bossBar            该效果的 BossBar
 * @param endTick            到期时的内部 tick
 * @param totalDurationTicks 施加时的总时长 tick, 用于还原 BossBar 进度
 * @param amplifier          效果等级(0 起)
 */
public record TimedEffectSession(BossBar bossBar, int endTick, int totalDurationTicks, int amplifier) {
}
