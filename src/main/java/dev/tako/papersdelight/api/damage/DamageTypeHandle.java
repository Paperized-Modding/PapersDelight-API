package dev.tako.papersdelight.api.damage;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 一次伤害类型注册的结果, 由 PapersDelight 的伤害类型注册器注册后返回.
 * <p>状态只会从 {@code REGISTERED} 单向降级为 {@code FALLBACK}, 不会回升.
 */
public final class DamageTypeHandle {

    private final @NotNull DamageTypeDefinition definition;
    private volatile @NotNull DamageTypeRegistrationState state;

    /** 正常由 PapersDelight 的伤害类型注册器返回, 不需要自己 new. */
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
