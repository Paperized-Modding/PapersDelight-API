package dev.tako.papersdelight.api.effect;

import dev.tako.papersdelight.api.util.PaperScheduler;
import dev.tako.papersdelight.api.util.TaskHandle;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限时效果管理器: 替子类管好倒计时, BossBar 与重登恢复, 所有管理器共用一张静态表, 附属插件 compileOnly 引入时与 PapersDelight 是同一份.
 * <p>子类按需覆写 {@link #onApply}, {@link #onRemove}, {@link #onRestore}, {@link #onExpire} 与 {@link #onEffectTick} 注入逻辑, 有额外状态要持久化再覆写 {@link #serializeExtra} 与 {@link #deserializeExtra}.
 * <p><strong>applyEffect 与 removeEffect 必须在玩家所在线程调用</strong>. 剩余时间存玩家 PDC, 离线冻结, 重登接着走, 主动移除与死亡会清掉 PDC.
 */
public abstract class TimedEffectManager implements Listener {

    private static final long RESTORE_DELAY_TICKS = 20L;

    private static final Map<String, TimedEffectManager> REGISTRY = new ConcurrentHashMap<>();

    protected final Plugin plugin;
    protected final String effectId;
    private final String qualifiedId;
    private final String nameKey;
    private final EffectPdcStore pdcStore;

    private final Map<UUID, TimedEffectSession> sessions = new ConcurrentHashMap<>();

    // ── 可配置外观 ──
    private boolean enabled = true;
    private BossBar.Color color = BossBar.Color.YELLOW;
    private BossBar.Overlay overlay = BossBar.Overlay.NOTCHED_20;

    private TaskHandle tickTask;
    private int internalTick;

    /**
     * @param effectId 效果短标识, 改了会丢存档
     * @param qualifiedId 对外完整 key, 必须与施加该效果的 CE 注册 id 一致
     * @param nameKey BossBar 效果名的客户端翻译键
     */
    protected TimedEffectManager(Plugin plugin, String effectId, String qualifiedId, String nameKey) {
        this.plugin = plugin;
        this.effectId = effectId;
        this.qualifiedId = qualifiedId.toLowerCase(Locale.ROOT);
        this.nameKey = nameKey;
        this.pdcStore = new EffectPdcStore(plugin, effectId);
        // 注册表在字段赋值后写入——REGISTRY 只被后续事件/命令读取,
        // 不会在构造中途被并发访问.
        REGISTRY.put(this.qualifiedId, this);
    }

    // ==================== 跨插件注册表 ====================

    /** 对外完整效果 key. */
    public final String qualifiedId() {
        return qualifiedId;
    }

    /** 当前已注册的全部限时效果管理器. */
    public static Collection<TimedEffectManager> registered() {
        return List.copyOf(REGISTRY.values());
    }

    /** 按完整 key 查找管理器, 没注册返回 {@code null}. */
    public static TimedEffectManager byQualifiedId(String key) {
        return key == null ? null : REGISTRY.get(key.toLowerCase(Locale.ROOT));
    }

    /** 该效果算不算有害效果, 默认 {@code false}(增益), 只有 {@code remove_random_effect} 的 {@code harmful_only=true} 模式会看它. */
    public boolean isHarmful() {
        return false;
    }

    /** 该效果在 {@code remove_random_effect} 里算不算低优先级, 默认 {@code false}, 玩家身上只剩低优先级效果时本次移除无效. */
    public boolean isLowPriority() {
        return false;
    }

    // ==================== 配置与生命周期 ====================

    /**
     * 子类在 {@code load()} 里调用注入外观配置, 第一次调用会顺便注册事件监听并启动 tick 循环.
     * <p>{@code colorName} 与 {@code styleName} 传非法值时保留默认外观.
     */
    protected void configure(boolean enabled, String colorName, String styleName) {
        this.enabled = enabled;
        if (colorName != null) {
            try { this.color = BossBar.Color.valueOf(colorName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) {}
        }
        if (styleName != null) {
            BossBar.Overlay mapped = mapOverlay(styleName);
            if (mapped != null) this.overlay = mapped;
        }

        if (tickTask == null || tickTask.isCancelled()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            tickTask = PaperScheduler.runGlobalTimer(plugin, this::tick, 1L, 2L);
        }
    }

    private static BossBar.Overlay mapOverlay(String styleName) {
        return switch (styleName.toUpperCase(Locale.ROOT)) {
            case "SOLID", "PROGRESS" -> BossBar.Overlay.PROGRESS;
            case "SEGMENTED_6", "NOTCHED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_10", "NOTCHED_10" -> BossBar.Overlay.NOTCHED_10;
            case "SEGMENTED_12", "NOTCHED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20", "NOTCHED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> null;
        };
    }

    /** 拼出 BossBar 标题: {@code 译文名 [罗马等级] 时间}, 等级 0 不显示等级. */
    protected Component buildTitle(int remainingTicks, int amplifier) {
        Component title = Component.translatable(nameKey);
        String roman = romanKey(amplifier);
        if (roman != null) {
            title = title.append(Component.text(" ")).append(Component.translatable(roman));
        }
        return title.append(Component.text(" " + formatDuration(remainingTicks)));
    }

    static String romanKey(int amplifier) {
        if (amplifier <= 0) return null;
        if (amplifier <= 5) return "potion.potency." + amplifier;
        return "enchantment.level." + (amplifier + 1);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 关服或 reload 时调用: 先把在线玩家的会话写进 PDC, 再停任务, 清 BossBar, 摘掉注册. */
    public void stopAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            persist(player);
        }
        if (tickTask != null) tickTask.cancel();
        for (Map.Entry<UUID, TimedEffectSession> e : sessions.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue().bossBar());
        }
        sessions.clear();
        // 两参 remove:仅当表中仍是本实例时摘除,避免误删 reload 后已替换的新实例.
        REGISTRY.remove(qualifiedId(), this);
    }

    // ==================== 核心 API ====================

    /** 施加效果(等级 0), 重复施加会刷新持续时间. */
    public void applyEffect(Player player, int durationTicks) {
        applyEffect(player, durationTicks, 0);
    }

    /** 施加效果, 重复施加会刷新持续时间, 效果没启用, 玩家为 {@code null} 或时长为 0 时直接忽略. */
    public void applyEffect(Player player, int durationTicks, int amplifier) {
        if (!enabled || player == null || durationTicks <= 0) return;

        TimedEffectSession old = sessions.remove(player.getUniqueId());
        if (old != null) player.hideBossBar(old.bossBar());

        int amp = Math.max(0, amplifier);
        int endTick = internalTick + durationTicks;
        BossBar bar = BossBar.bossBar(buildTitle(durationTicks, amp), 1.0f, color, overlay);
        player.showBossBar(bar);
        sessions.put(player.getUniqueId(), new TimedEffectSession(bar, endTick, durationTicks, amp));

        onApply(player, durationTicks, amp);
    }

    /**
     * 合并施加, 给 "喝下去" 这类路径用.
     * <p><strong>重复施加时时长和等级各取较大值</strong>, 而不是直接覆盖, 更短或更低等级不会削弱已有效果.
     */
    public void applyEffectMerging(Player player, int durationTicks, int amplifier) {
        if (player == null) return;
        int mergedDuration = Math.max(durationTicks, getRemainingTicks(player));
        // getAmplifier 无会话返回 -1,Math.max(≥0, -1) 天然取到新值
        int mergedAmplifier = Math.max(Math.max(0, amplifier), getAmplifier(player));
        applyEffect(player, mergedDuration, mergedAmplifier);
    }

    /** 效果被移除的原因, 供子类在 {@code onRemove} 里区分处理. */
    public enum RemovalCause {
        /** 喝奶, 随机移除这类主动清除. */
        CONSUMED,
        /** 玩家死亡. */
        DEATH
    }

    /** 立即移除效果, 同时清掉 PDC, 避免重登后复活. */
    public void removeEffect(Player player) {
        removeEffect(player, RemovalCause.CONSUMED);
    }

    /** 立即移除效果, 并把移除原因告知子类. */
    public void removeEffect(Player player, RemovalCause cause) {
        if (player == null) return;
        TimedEffectSession session = sessions.remove(player.getUniqueId());
        if (session != null) player.hideBossBar(session.bossBar());
        pdcStore.clear(player);
        onRemove(player, cause == null ? RemovalCause.CONSUMED : cause);
    }

    /** 剩余 tick, 没有会话返回 0. */
    public int getRemainingTicks(Player player) {
        if (player == null) return 0;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        if (session == null) return 0;
        return Math.max(0, session.endTick() - internalTick);
    }

    /** 效果是否正在生效. */
    public boolean isActive(Player player) {
        return player != null && getRemainingTicks(player) > 0;
    }

    /** 施加时的总时长 tick, 用来还原 BossBar 进度, 没有会话返回 0. */
    public int getTotalTicks(Player player) {
        if (player == null) return 0;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        return session == null ? 0 : session.totalDurationTicks();
    }

    /** 当前等级, 没有会话返回 -1. */
    public int getAmplifier(Player player) {
        if (player == null) return -1;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        return session == null ? -1 : session.amplifier();
    }

    /** 从持久化数据恢复会话, 让效果在重登后接着走, 只触发 {@link #onRestore}, 不触发 {@link #onApply}. */
    public void restoreSession(Player player, int remainingTicks, int totalTicks, int amplifier) {
        if (!enabled || player == null || remainingTicks <= 0) return;

        TimedEffectSession old = sessions.remove(player.getUniqueId());
        if (old != null) player.hideBossBar(old.bossBar());

        int amp = Math.max(0, amplifier);
        int total = Math.max(totalTicks, remainingTicks);
        float progress = (float) Math.min(1.0, (double) remainingTicks / total);
        BossBar bar = BossBar.bossBar(buildTitle(remainingTicks, amp), progress, color, overlay);
        player.showBossBar(bar);
        sessions.put(player.getUniqueId(),
                new TimedEffectSession(bar, internalTick + remainingTicks, total, amp));

        onRestore(player, amp);
    }

    // ==================== PDC 持久化 ====================

    /** 把玩家当前会话写进 PDC, 没有会话就清除. */
    protected void persist(Player player) {
        if (player == null) return;
        int remaining = getRemainingTicks(player);
        if (remaining <= 0) {
            pdcStore.clear(player);
            return;
        }
        pdcStore.save(player, remaining, getTotalTicks(player), getAmplifier(player), serializeExtra(player));
    }

    // ==================== Tick 循环 ====================

    private void tick() {
        internalTick += 2; // timer 每 2 tick 跑
        if (sessions.isEmpty()) return;

        int now = internalTick;
        for (var it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, TimedEffectSession> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            TimedEffectSession session = entry.getValue();

            if (player == null || !player.isOnline()) {
                // 玩家离线,Adventure BossBar 已随其视图自动移除,直接丢会话
                it.remove();
                continue;
            }

            int remaining = session.endTick() - now;
            if (remaining <= 0) {
                Player expired = player;
                PaperScheduler.runEntity(plugin, player, () -> {
                    expired.hideBossBar(session.bossBar());
                    onExpire(expired);
                });
                it.remove();
                continue;
            }

            UUID playerId = entry.getKey();
            PaperScheduler.runEntity(plugin, player, () -> tickPlayer(player, playerId, session, now));
        }
    }

    private void tickPlayer(Player player, UUID playerId, TimedEffectSession session, int now) {
        if (sessions.get(playerId) != session) return;
        if (!player.isOnline()) {
            sessions.remove(playerId, session);
            return;
        }

        int remaining = session.endTick() - now;
        if (remaining <= 0) {
            player.hideBossBar(session.bossBar());
            sessions.remove(playerId, session);
            onExpire(player);
            return;
        }

        Component title = buildTitle(remaining, session.amplifier());
        double progress = (double) remaining / Math.max(session.totalDurationTicks(), 1);
        session.bossBar().progress((float) Math.max(0.0, Math.min(1.0, progress)));
        if (!title.equals(session.bossBar().name())) {
            session.bossBar().name(title);
        }

        onEffectTick(player, session.amplifier(), now);
    }

    // ==================== 事件 ====================

    /** 下线时先把快照写进 PDC, 再删会话, 清 BossBar. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        persist(player);
        TimedEffectSession session = sessions.remove(player.getUniqueId());
        if (session != null) player.hideBossBar(session.bossBar());
    }

    /** 上线后延迟从 PDC 读回并恢复效果, 读完立刻清除(只恢复一次). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        PaperScheduler.runEntityLater(plugin, player, () -> {
            if (!player.isOnline()) return;
            EffectPdcRecord record = pdcStore.read(player);
            if (record == null || record.remainingTicks() <= 0) return;
            pdcStore.clear(player);
            restoreSession(player, record.remainingTicks(), record.totalTicks(), record.amplifier());
            deserializeExtra(player, record.extra());
        }, RESTORE_DELAY_TICKS);
    }

    /** 死亡时按 {@link RemovalCause#DEATH} 移除效果, 重生后不会复活. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeEffect(event.getEntity(), RemovalCause.DEATH);
    }

    // ==================== 子类钩子 ====================

    /** 效果施加成功后的副作用. */
    protected void onApply(Player player, int durationTicks, int amplifier) {
    }

    /** 效果被移除后的清理, 可据 {@code cause} 区分处理. */
    protected void onRemove(Player player, RemovalCause cause) {
    }

    /** 从 PDC 恢复后的副作用. */
    protected void onRestore(Player player, int amplifier) {
    }

    /** 效果自然到期后被调用. */
    protected void onExpire(Player player) {
    }

    /** 每个生效中的会话每 2 tick 调用一次, 在玩家所在线程执行. */
    protected void onEffectTick(Player player, int amplifier, int now) {
    }

    /** 把子类自己的额外状态序列化进 PDC, 默认没有额外状态. */
    protected byte[] serializeExtra(Player player) {
        return new byte[0];
    }

    /** 从 PDC 读回子类的额外状态, 默认忽略. */
    protected void deserializeExtra(Player player, byte[] extra) {
    }

    // ==================== 工具方法 ====================

    /** 把 tick 数格式化成 {@code M:SS}. */
    public static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int rest = seconds % 60;
        return (seconds / 60) + ":" + (rest < 10 ? "0" : "") + rest;
    }
}
