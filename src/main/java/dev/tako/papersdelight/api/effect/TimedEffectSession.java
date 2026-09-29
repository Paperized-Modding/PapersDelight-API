package dev.tako.papersdelight.api.effect;

import net.kyori.adventure.bossbar.BossBar;

/** 一个玩家身上生效中的限时效果会话, 由 {@link TimedEffectManager} 维护, 不要自己创建. */
public record TimedEffectSession(BossBar bossBar, int endTick, int totalDurationTicks, int amplifier) {
}
