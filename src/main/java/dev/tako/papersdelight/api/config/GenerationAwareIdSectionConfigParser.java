package dev.tako.papersdelight.api.config;

import dev.tako.papersdelight.api.config.ParserGeneration;
import net.momirealms.craftengine.core.pack.CachedConfigSection;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;

import java.util.Objects;

/**
 * 阻止已失活的 generation 继续参与解析或发布。
 * CE 26.8 的 registerConfigSectionParser 会把 parser 永久写入 BuiltInRegistries.CONFIG_PARSER，
 * unregister 无法移除，因此 parser 实例常驻、靠 generation 判定是否生效。
 */
public abstract class GenerationAwareIdSectionConfigParser extends IdSectionConfigParser {
    private volatile ParserGeneration generation;
    private final Runnable beforeCommit;

    protected GenerationAwareIdSectionConfigParser(ParserGeneration generation) {
        this(generation, () -> { });
    }

    protected GenerationAwareIdSectionConfigParser(ParserGeneration generation, Runnable beforeCommit) {
        this.generation = Objects.requireNonNull(generation, "generation");
        this.beforeCommit = Objects.requireNonNull(beforeCommit, "beforeCommit");
    }

    /**
     * 把常驻 parser 绑定到新一轮 generation。
     * CE 26.8 无法从 CONFIG_PARSER registry 移除 parser，所以实例常驻、
     * 每轮 enable 通过重绑 generation 来切换生效批次。
     */
    public final void rebindGeneration(ParserGeneration next) {
        Objects.requireNonNull(next, "next");
        ParserGeneration.runExclusive(() -> this.generation = next);
    }

    /**
     * 当前绑定的 generation 是否处于生效状态。
     *
     * <p>{@code public} 而非 {@code protected}：附属插件的 parser 位于其他包，
     * 需要用它判断本轮解析是否应当继续。</p>
     */
    public final boolean generationActive() {
        return generation.isActive();
    }

    /**
     * 仅在 generation 生效时执行状态变更。
     *
     * @return 实际执行了变更则 {@code true}；generation 已失活则 {@code false}
     */
    public final boolean runIfGenerationActive(Runnable mutation) {
        return generation.commitIfActive(mutation);
    }

    /**
     * 在 generation 生效时提交快照，提交前先跑 {@code beforeCommit} 钩子。
     *
     * <p>{@code public} 理由同 {@link #runIfGenerationActive(Runnable)}：
     * 附属插件的 parser 需要在自己的 {@code postProcess} 里调用它发布快照。</p>
     */
    public final boolean commitIfGenerationActive(Runnable commit) {
        return runIfGenerationActive(() -> {
            beforeCommit.run();
            commit.run();
        });
    }

    @Override
    public void addConfig(CachedConfigSection section) {
        if (generationActive()) super.addConfig(section);
    }

    @Override
    public synchronized void addPendingConfigSection(PendingConfigSection section) {
        if (generationActive()) super.addPendingConfigSection(section);
    }

    @Override
    public void loadAll() {
        if (generationActive()) super.loadAll();
    }

    @Override
    public void clearConfigs() {
        // CE 26.8 的 IdConfigParser 在 checkDuplicated=false 时 idToPath 持有 Map.of()，
        // 直接调用 super.clearConfigs() 会对不可变 map 执行 clear()。
        this.configStorage.clear();
        this.pendingConfigSections.clear();
        if (checkDuplicated()) clearIdToPath();
    }
}
