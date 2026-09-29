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
 * 限时效果的通用骨架 —— 自追踪时长 + BossBar 显示 + PDC 跨下线持久化。
 *
 * <p>抽出 PD 三效果（Nourishment/Comfort/Garlic）与 PB 四效果的公共部分：
 * 会话表、每 2 tick 的倒计时循环、BossBar 更新、下线/上线的 PDC 存取、
 * 关服兜底。子类只需覆盖少量钩子注入差异逻辑。</p>
 *
 * <p>持久化语义：存「剩余 tick」，离线冻结、重连续期。只有下线与关服写 PDC；
 * 主动移除（喝奶、随机清除等）不写、并清除 PDC——那是真的失去效果，不该复活。</p>
 *
 * <p>Folia 安全：BossBar 的创建/更新/移除都通过 {@link PaperScheduler#runEntity} 回到
 * 玩家所在线程。</p>
 */
public abstract class TimedEffectManager implements Listener {

    /** 恢复延迟——确保 BossBar 能正常挂上。 */
    private static final long RESTORE_DELAY_TICKS = 20L;

    /**
     * 跨插件注册表：所有限时效果管理器按 {@link #qualifiedId()} 索引。
     *
     * <p>PB 等下游插件以 {@code compileOnly} 引入本 API 且不打包 API 类
     * （由其构建门禁保证），运行时与 PD 共用同一份本类，因此这里的静态表
     * 是跨插件共享的。移除函数（remove_effect / remove_random_effect）与
     * 喝奶清效果据此遍历，无需硬编码效果枚举。</p>
     */
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
     * @param plugin      命名空间来源（PDC key 用）
     * @param effectId    效果短标识（如 {@code garlic}），仅用于 PDC key，不可随意改动否则丢存档
     * @param qualifiedId 对外完整 key，必须与该效果**施加** function 的 CE 注册 id 一致
     *                    （如 {@code dumplings_delight:garlic_effect}）
     * @param nameKey     BossBar 效果名的客户端翻译键（如 {@code effect.brewinandchewin.tipsy}），
     *                    由客户端按资源包 lang 渲染，实现多语言本地化
     */
    protected TimedEffectManager(Plugin plugin, String effectId, String qualifiedId, String nameKey) {
        this.plugin = plugin;
        this.effectId = effectId;
        this.qualifiedId = qualifiedId.toLowerCase(Locale.ROOT);
        this.nameKey = nameKey;
        this.pdcStore = new EffectPdcStore(plugin, effectId);
        // 注册表在字段赋值后写入——REGISTRY 只被后续事件/命令读取，
        // 不会在构造中途被并发访问。
        REGISTRY.put(this.qualifiedId, this);
    }

    // ==================== 跨插件注册表 ====================

    /**
     * 对外完整效果 key，等于该效果**施加** function 的 CE 注册 id。
     *
     * <p>刻意与施加侧对齐（含 {@code _effect} 后缀与来源模组命名空间），
     * 使同一效果在 YAML 里施加与移除写法一致：
     * <ul>
     *   <li>{@code dumplings_delight:garlic_effect} —— 大蒜跟随饺子模组命名空间</li>
     *   <li>{@code papersdelight:comfort_effect}</li>
     *   <li>{@code papersbrewin:tipsy_effect}</li>
     * </ul>
     * 因此本值**不能**从插件名推导，必须由子类显式传入。</p>
     */
    public final String qualifiedId() {
        return qualifiedId;
    }

    /** 所有已注册的限时效果管理器快照（跨插件）。 */
    public static Collection<TimedEffectManager> registered() {
        return List.copyOf(REGISTRY.values());
    }

    /** 按完整 key 查找管理器，未注册返回 {@code null}。 */
    public static TimedEffectManager byQualifiedId(String key) {
        return key == null ? null : REGISTRY.get(key.toLowerCase(Locale.ROOT));
    }

    /**
     * 该效果是否为有害效果。默认 {@code false}（增益）。
     *
     * <p>供 {@code remove_random_effect} 的 {@code harmful_only=true} 模式判断是否
     * 纳入候选池。PD 三效果均为增益；PB 的 Tipsy/Intoxication 覆盖为 {@code true}。</p>
     */
    public boolean isHarmful() {
        return false;
    }

    /**
     * 该效果在 {@code remove_random_effect} 中是否为「低优先级」。默认 {@code false}。
     *
     * <p>低优先级效果只在候选池里<b>还存在非低优先级效果</b>时才参与随机移除；
     * 若玩家身上只剩低优先级效果，则整个候选池视为空，本次移除无效。</p>
     *
     * <p>对齐 BaC 4.5.0 的 {@code PreventTaggedEffectRemovalMixin}（NeoForge 版）与
     * {@code brewinandchewin:low_priority/milk_bottle} 标签：微醺被刻意做成不易被
     * 奶瓶解除——身上只有微醺时喝奶瓶完全无效，避免随手一瓶就解酒。
     * PD 三效果均非低优先级；PB 的 Tipsy 覆盖为 {@code true}。</p>
     */
    public boolean isLowPriority() {
        return false;
    }

    // ==================== 配置与生命周期 ====================

    /**
     * 由子类在 {@code load()} 中调用，注入外观配置并（若未启动）启动 tick 循环与事件监听。
     *
     * <p>BossBar 标题不再由服务端模板拼接——效果名走 {@link #nameKey} 客户端翻译，
     * 时间/等级由 {@link #buildTitle} 组装，故此处只需颜色与样式。</p>
     *
     * @param enabled   效果是否启用
     * @param colorName BossBar 条块色名（PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE，非法值保留默认）
     * @param styleName 样式名（SOLID/SEGMENTED_6/10/12/20，非法值保留默认）
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

    /** 旧 Bukkit BarStyle 名 → Adventure Overlay。非法返回 null。 */
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

    /**
     * 组装 BossBar 标题：{@code 译文名 [罗马等级] 时间}。
     *
     * <p>效果名用 {@link Component#translatable(String)}，客户端按资源包 lang 渲染；
     * 等级 &gt; 0（显示等级 &ge; II）时插入罗马数字（复用 vanilla 客户端键，无需资源包）；
     * 时间为纯文本 {@code m:ss}，语言无关。</p>
     */
    protected Component buildTitle(int remainingTicks, int amplifier) {
        Component title = Component.translatable(nameKey);
        String roman = romanKey(amplifier);
        if (roman != null) {
            title = title.append(Component.text(" ")).append(Component.translatable(roman));
        }
        return title.append(Component.text(" " + formatDuration(remainingTicks)));
    }

    /**
     * amplifier → 罗马数字的 vanilla 客户端翻译键。amplifier 0（显示等级 I）返回 null（不显示）。
     *
     * <ul>
     *   <li>1..5 → {@code potion.potency.N} 渲染 II..VI</li>
     *   <li>&ge;6 → {@code enchantment.level.(amplifier+1)} 渲染 VII..（vanilla 定义到 10=X）</li>
     * </ul>
     * 两组均为 vanilla 自带键，无需资源包。
     */
    static String romanKey(int amplifier) {
        if (amplifier <= 0) return null;
        if (amplifier <= 5) return "potion.potency." + amplifier;
        return "enchantment.level." + (amplifier + 1);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 关服/reload：先把在线玩家的会话写 PDC（关服兜底），再取消任务、清 BossBar、摘除注册。 */
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
        // 两参 remove：仅当表中仍是本实例时摘除，避免误删 reload 后已替换的新实例。
        REGISTRY.remove(qualifiedId(), this);
    }

    // ==================== 核心 API ====================

    /** 施加效果（等级 0）。重复施加会刷新持续时间。 */
    public void applyEffect(Player player, int durationTicks) {
        applyEffect(player, durationTicks, 0);
    }

    /** 施加效果。重复施加会刷新持续时间。 */
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
     * 合并施加：重复施加时**时长与等级各取 max**（对齐 vanilla {@code addEffect} 语义），
     * 而非 {@link #applyEffect} 的无条件覆盖。
     *
     * <p>用于「喝下去」的 consume 路径——喝一杯时长更短的酒不应缩短已有效果，
     * 喝一杯更低等级的酒也不应降级。调试命令仍走 {@link #applyEffect} 精确覆盖。</p>
     */
    public void applyEffectMerging(Player player, int durationTicks, int amplifier) {
        if (player == null) return;
        int mergedDuration = Math.max(durationTicks, getRemainingTicks(player));
        // getAmplifier 无会话返回 -1，Math.max(≥0, -1) 天然取到新值
        int mergedAmplifier = Math.max(Math.max(0, amplifier), getAmplifier(player));
        applyEffect(player, mergedDuration, mergedAmplifier);
    }

    /** 效果被移除的原因，供子类区分处理（如 Tipsy 的欠账是否结算）。 */
    public enum RemovalCause {
        /** 喝奶、随机移除等主动消除——原版语义下等价于「效果消失」，欠账应结算。 */
        CONSUMED,
        /** 玩家死亡——欠账作废，否则重生后立刻掉血。 */
        DEATH
    }

    /** 立即移除效果（真的失去，非下线）。同时清除 PDC，避免下次登录复活。 */
    public void removeEffect(Player player) {
        removeEffect(player, RemovalCause.CONSUMED);
    }

    /** 立即移除效果，并告知子类移除原因。 */
    public void removeEffect(Player player, RemovalCause cause) {
        if (player == null) return;
        TimedEffectSession session = sessions.remove(player.getUniqueId());
        if (session != null) player.hideBossBar(session.bossBar());
        pdcStore.clear(player);
        onRemove(player, cause == null ? RemovalCause.CONSUMED : cause);
    }

    /** 剩余 tick，无则 0。 */
    public int getRemainingTicks(Player player) {
        if (player == null) return 0;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        if (session == null) return 0;
        return Math.max(0, session.endTick() - internalTick);
    }

    /** 效果是否生效中。 */
    public boolean isActive(Player player) {
        return player != null && getRemainingTicks(player) > 0;
    }

    /** 施加时的总时长 tick，用于持久化还原 BossBar 进度。无会话则 0。 */
    public int getTotalTicks(Player player) {
        if (player == null) return 0;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        return session == null ? 0 : session.totalDurationTicks();
    }

    /** 当前等级，无会话则 -1。 */
    public int getAmplifier(Player player) {
        if (player == null) return -1;
        TimedEffectSession session = sessions.get(player.getUniqueId());
        return session == null ? -1 : session.amplifier();
    }

    /**
     * 从持久化数据恢复会话——重连后继续未走完的效果。
     *
     * <p>与 {@link #applyEffect} 的区别：不触发 {@link #onApply}（恢复不是一次新施加，
     * 不计统计、不授予进度），改触发 {@link #onRestore}。</p>
     */
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

    /** 把玩家当前会话写入 PDC（无会话则清除）。 */
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
                // 玩家离线，Adventure BossBar 已随其视图自动移除，直接丢会话
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

    /**
     * 下线：先写 PDC（LOWEST 优先级，早于清理），再删会话、清 BossBar。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        persist(player);
        TimedEffectSession session = sessions.remove(player.getUniqueId());
        if (session != null) player.hideBossBar(session.bossBar());
    }

    /**
     * 上线：延迟从 PDC 读回并恢复。读回后清除 PDC（一次性恢复，与旧 SQLite 语义一致）。
     */
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

    /**
     * 死亡：立即移除效果。
     *
     * <p>走 {@link #removeEffect} 而非到期路径——死亡是真的失去效果，应清 PDC
     * 避免重生后复活，并触发 {@link #onRemove}（Tipsy 的麻痹欠账作废、Raging
     * 移除属性修正），而不是 {@link #onExpire}（会结算欠账，导致重生后掉血）。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        removeEffect(event.getEntity(), RemovalCause.DEATH);
    }

    // ==================== 子类钩子 ====================

    /** 施加副作用（PD 用于记统计/授予进度；PB 用于初始化额外状态）。默认空。 */
    protected void onApply(Player player, int durationTicks, int amplifier) {
    }

    /**
     * 主动移除时的清理（清额外状态 map 等）。默认空。
     *
     * @param cause 移除原因，子类可据此区分处理（如 Tipsy 死亡时作废欠账、喝奶时结算）
     */
    protected void onRemove(Player player, RemovalCause cause) {
    }

    /** 从持久化恢复后的副作用（恢复额外状态由 {@link #deserializeExtra} 负责）。默认空。 */
    protected void onRestore(Player player, int amplifier) {
    }

    /** 效果自然到期或被 tick 判定结束时。默认空。 */
    protected void onExpire(Player player) {
    }

    /**
     * 每个存活会话每 2 tick 调用一次的专属逻辑（在玩家所在线程执行）。
     *
     * @param amplifier 当前等级
     * @param now       当前内部 tick（每真实 tick +2）
     */
    protected void onEffectTick(Player player, int amplifier, int now) {
    }

    /** 序列化子类额外状态用于 PDC（如 numbedHealth/stacks）。默认无额外状态。 */
    protected byte[] serializeExtra(Player player) {
        return new byte[0];
    }

    /** 从 PDC 反序列化子类额外状态。默认忽略。 */
    protected void deserializeExtra(Player player, byte[] extra) {
    }

    // ==================== 工具方法 ====================

    /** 将 tick 数格式化为 {@code M:SS}。 */
    public static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int rest = seconds % 60;
        return (seconds / 60) + ":" + (rest < 10 ? "0" : "") + rest;
    }
}
