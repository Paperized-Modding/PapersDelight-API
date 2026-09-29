package dev.tako.papersdelight.api.damage;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 伤害类型定义与注册状态句柄。定义不可变；状态只允许由 REGISTERED 单向降级为 FALLBACK。
 */
public final class DamageTypeHandle {

    private final @NotNull DamageTypeDefinition definition;
    private volatile @NotNull DamageTypeRegistrationState state;

    public DamageTypeHandle(@NotNull DamageTypeDefinition definition,
                            @NotNull DamageTypeRegistrationState state) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    /**
     * @return 伤害类型定义
     */
    @NotNull
    public DamageTypeDefinition getDefinition() {
        return definition;
    }

    /**
     * @return 注册状态
     */
    @NotNull
    public DamageTypeRegistrationState getState() {
        return state;
    }

    /**
     * @return 伤害类型键
     */
    @NotNull
    public Key getKey() {
        return definition.key();
    }

    /**
     * @return 回退键
     */
    @NotNull
    public Key getFallback() {
        return definition.fallback();
    }

    /**
     * @return 是否已成功注册
     */
    public boolean isRegistered() {
        return state == DamageTypeRegistrationState.REGISTERED;
    }

    /** 注册冲突或延迟失败时单向降级；重复调用保持 FALLBACK。 */
    void downgrade() {
        state = DamageTypeRegistrationState.FALLBACK;
    }

    @Override
    public String toString() {
        return "DamageTypeHandle{" +
                "definition=" + definition +
                ", state=" + state +
                '}';
    }
}
