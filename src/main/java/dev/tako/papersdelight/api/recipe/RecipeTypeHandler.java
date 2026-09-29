package dev.tako.papersdelight.api.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.jetbrains.annotations.NotNull;

/**
 * 配方类型扩展点: 附属插件通过 {@link RecipeTypeRegistry} 注册自己的 {@code type},
 * PapersDelight 的 recipe parser 遇到未知 type 时转交给它解析, 而不是当成坏数据跳过.
 * <p>一轮解析会依次调用 {@link #beginParse()}, 若干次 {@link #parse} 和 {@link #publish()},
 * 三个方法都在 parser 线程上串行执行, 实现里不用加锁; 但 {@link #publish()} 发布的快照会被其他线程读, 必须是不可变的.
 */
public interface RecipeTypeHandler {

    /**
     * 本 handler 负责的配方 type, 对应配置里的 {@code type: xxx}.
     * <p>必须全局唯一, 与 PapersDelight 内置 type 冲突的注册会被拒绝.
     */
    @NotNull
    String typeId();

    /** 一轮解析开始, 清空上一轮的暂存数据. */
    void beginParse();

    /**
     * 解析一条匹配 type 的配方, 失败只跳过这一条, 不影响其余配方.
     *
     * @param recipeId 配方 ID(如 {@code brewinandchewin:kombucha})
     * @param source   来源文件路径, 仅用于日志
     * @param section  配方配置节
     * @return 解析成功并收下这条配方返回 {@code true}, 失败返回 {@code false}
     */
    boolean parse(@NotNull String recipeId, @NotNull String source, @NotNull ConfigSection section);

    /** 一轮解析结束, 原子发布不可变快照. */
    void publish();

    /** 重置已发布的快照, PapersDelight 在插件卸载或重新加载配方时调用, 避免旧配方被继续用. */
    void reset();
}
