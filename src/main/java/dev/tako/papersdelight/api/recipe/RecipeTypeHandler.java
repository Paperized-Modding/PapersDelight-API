package dev.tako.papersdelight.api.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.jetbrains.annotations.NotNull;

/**
 * 配方类型扩展点: 附属插件通过 {@link RecipeTypeRegistry} 注册自己的 {@code type}, parser 遇到未知 type 就转交给它解析, 不当成坏数据跳过.
 * <p>一轮解析依次调用 {@link #beginParse()}, 若干次 {@link #parse} 与 {@link #publish()}, 都在 parser 线程串行执行, 实现不用加锁.
 * <p><strong>{@link #publish()} 发布的快照会被其他线程读, 必须不可变</strong>.
 */
public interface RecipeTypeHandler {

    /** 对应配置里的 {@code type: xxx}; 必须全局唯一, 与内置 type 冲突的注册会被拒绝. */
    @NotNull
    String typeId();

    void beginParse();

    /** 失败只跳过这一条, 不影响其余配方, 返回 {@code false}. */
    boolean parse(@NotNull String recipeId, @NotNull String source, @NotNull ConfigSection section);

    void publish();

    /** 插件卸载或重新加载配方时调用, 避免旧配方被继续用. */
    void reset();
}
