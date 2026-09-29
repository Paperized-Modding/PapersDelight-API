package dev.tako.papersdelight.api.config;

import dev.tako.papersdelight.api.config.ParserGeneration;
import net.momirealms.craftengine.core.pack.CachedConfigSection;
import net.momirealms.craftengine.core.pack.PendingConfigSection;
import net.momirealms.craftengine.core.plugin.config.IdSectionConfigParser;

import java.util.Objects;

/**
 * 带 generation 判定的 {@link IdSectionConfigParser}: 已经失活的那轮提交上来的数据会被直接跳过.
 */
public abstract class GenerationAwareIdSectionConfigParser extends IdSectionConfigParser {
    private volatile ParserGeneration generation;
    private final Runnable beforeCommit;

    protected GenerationAwareIdSectionConfigParser(ParserGeneration generation) {
        this(generation, () -> { });
    }

    /** {@code beforeCommit} 在提交快照前先跑一遍, 可用来准备数据. */
    protected GenerationAwareIdSectionConfigParser(ParserGeneration generation, Runnable beforeCommit) {
        this.generation = Objects.requireNonNull(generation, "generation");
        this.beforeCommit = Objects.requireNonNull(beforeCommit, "beforeCommit");
    }

    /** 把常驻 parser 绑到新一轮 generation. */
    public final void rebindGeneration(ParserGeneration next) {
        Objects.requireNonNull(next, "next");
        ParserGeneration.runExclusive(() -> this.generation = next);
    }

    /** 当前绑定的 generation 是否还在生效, 据此判断要不要继续解析. */
    public final boolean generationActive() {
        return generation.isActive();
    }

    /** generation 已失活时返回 {@code false}, 不执行. */
    public final boolean runIfGenerationActive(Runnable mutation) {
        return generation.commitIfActive(mutation);
    }

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
        // CE 26.8 的 IdConfigParser 在 checkDuplicated=false 时 idToPath 持有 Map.of(),
        // 直接调用 super.clearConfigs() 会对不可变 map 执行 clear().
        this.configStorage.clear();
        this.pendingConfigSections.clear();
        if (checkDuplicated()) clearIdToPath();
    }
}
