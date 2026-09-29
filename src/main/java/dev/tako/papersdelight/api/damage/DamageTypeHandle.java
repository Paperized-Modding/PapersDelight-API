package dev.tako.papersdelight.api.damage;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 一次伤害类型注册的结果, 持有定义与当前注册状态, 由 {@link DamageTypes} 注册后返回.
 * <p>状态只会从 {@code REGISTERED} 单向降级为 {@code FALLBACK}, 不会回升.
 */
public final class DamageTypeHandle {

    private final @NotNull DamageTypeDefinition definition;
    private volatile @NotNull DamageTypeRegistrationState state;

    /**
     * 创建一个句柄, 正常由 {@link DamageTypes} 注册后返回, 不需要自己 new.
     *
     * @param definition 伤害类型定义
     * @param state      初始注册状态
     */
    public DamageTypeHandle(@NotNull DamageTypeDefinition definition,
                            @NotNull DamageTypeRegistrationState state) {
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    @NotNull
    public DamageTypeDefinition getDefinition() {
        return definition;
    }

    @NotNull
    public DamageTypeRegistrationState getState() {
        return state;
    }

    @NotNull
    public Key getKey() {
        return definition.key();
    }

    @NotNull
    public Key getFallback() {
        return definition.fallback();
    }

    public boolean isRegistered() {
        return state == DamageTypeRegistrationState.REGISTERED;
    }

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
