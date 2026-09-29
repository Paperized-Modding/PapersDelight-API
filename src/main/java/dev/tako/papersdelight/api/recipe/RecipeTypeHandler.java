package dev.tako.papersdelight.api.recipe;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import org.jetbrains.annotations.NotNull;

/**
 * 配方类型扩展点。
 *
 * <p>附属插件通过 {@link RecipeTypeRegistry} 注册自己的 {@code type}，
 * PapersDelight 的 recipe parser 在遇到未知 type 时会转交给对应 handler，
 * 而不是当成坏数据跳过。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>{@link #beginParse()} 在每轮解析开始时调用，用于清空上一轮的暂存数据</li>
 *   <li>{@link #parse} 对每个匹配 type 的 section 调用一次，解析失败应返回
 *       {@code false} 并自行记录原因；单条失败只跳过该条，不影响其余配方</li>
 *   <li>{@link #publish()} 在本轮解析结束时调用，此时应原子发布不可变快照</li>
 * </ul>
 *
 * <p>线程约定：这三个方法都在 PapersDelight 的 parser 线程上串行调用，
 * 实现内部无需加锁；但 {@link #publish()} 发布的快照会被其他线程读取，
 * 必须是不可变的，且通过 volatile 或原子引用切换。</p>
 */
public interface RecipeTypeHandler {

    /**
     * 本 handler 负责的配方 type 值，对应配置里的 {@code type: xxx}。
     *
     * <p>必须全局唯一。与 PapersDelight 内置 type
     * （{@code cooking}/{@code cutting}/{@code single}（别名 {@code info}）/{@code decomposition}）
     * 冲突的注册会被拒绝。</p>
     */
    @NotNull
    String typeId();

    /** 一轮解析开始，清空暂存状态。 */
    void beginParse();

    /**
     * 解析单条配方。
     *
     * @param recipeId 配方 ID（命名空间形式，如 {@code brewinandchewin:kombucha}）
     * @param source   来源文件路径，仅用于日志
     * @param section  配方配置节
     * @return 解析成功并已收下该条配方则 {@code true}；失败则 {@code false}
     */
    boolean parse(@NotNull String recipeId, @NotNull String source, @NotNull ConfigSection section);

    /** 一轮解析结束，原子发布快照。 */
    void publish();

    /**
     * 重置已发布的快照。
     *
     * <p>PapersDelight 在 generation 失活 / 插件卸载时调用，
     * 避免旧配方在下一轮生效前被误用。</p>
     */
    void reset();
}
