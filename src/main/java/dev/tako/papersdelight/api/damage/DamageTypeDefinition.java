package dev.tako.papersdelight.api.damage;

import net.kyori.adventure.key.Key;
import org.bukkit.damage.DamageEffect;
import org.bukkit.damage.DamageScaling;
import org.bukkit.damage.DeathMessageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一个不可变的自定义伤害类型定义, 由 {@link Builder} 构建.
 * <p><strong>{@code key} 的命名空间不能是 {@code minecraft}</strong>, {@code fallback} 与 {@code messageId} 必须显式设置, 否则 {@link Builder#build()} 会抛异常.
 */
public final class DamageTypeDefinition {

    private static final String MINECRAFT_NAMESPACE = "minecraft";

    // ── 必填字段 ──────────────────────────────────────────────────────────────
    private final @NotNull Key key;
    private final @NotNull Key fallback;
    private final @NotNull String messageId;

    // ── 带默认值的字段 ────────────────────────────────────────────────────────
    private final float exhaustion;
    private final @NotNull DamageScaling scaling;
    private final @Nullable DamageEffect effect;
    private final @NotNull DeathMessageType deathMessageType;
    private final @NotNull Set<Key> tags;

    private DamageTypeDefinition(Builder builder) {
        this.key             = builder.key;
        this.fallback        = builder.fallback;
        this.messageId       = builder.messageId;
        this.exhaustion      = builder.exhaustion;
        this.scaling         = builder.scaling;
        this.effect          = builder.effect;
        this.deathMessageType = builder.deathMessageType;
        // 防御复制,并生成不可变集合
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
    }

    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    /** 创建以 {@code key} 为主键的 Builder. */
    @NotNull
    public static Builder builder(@NotNull Key key) {
        Objects.requireNonNull(key, "key must not be null");
        if (MINECRAFT_NAMESPACE.equals(key.namespace())) {
            throw new IllegalArgumentException(
                    "key namespace must not be 'minecraft', got: " + key);
        }
        return new Builder(key);
    }

    // ── 访问器 ────────────────────────────────────────────────────────────────

    @NotNull
    public Key key() {
        return key;
    }

    @NotNull
    public Key fallback() {
        return fallback;
    }

    @NotNull
    public String messageId() {
        return messageId;
    }

    public float exhaustion() {
        return exhaustion;
    }

    @NotNull
    public DamageScaling scaling() {
        return scaling;
    }

    @Nullable
    public DamageEffect effect() {
        return effect;
    }

    @NotNull
    public DeathMessageType deathMessageType() {
        return deathMessageType;
    }

    @NotNull
    public Set<Key> tags() {
        return tags;
    }

    // ── equals / hashCode / toString ─────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DamageTypeDefinition other)) return false;
        return Float.compare(exhaustion, other.exhaustion) == 0
                && key.equals(other.key)
                && fallback.equals(other.fallback)
                && messageId.equals(other.messageId)
                && scaling == other.scaling
                && Objects.equals(effect, other.effect)
                && deathMessageType == other.deathMessageType
                && tags.equals(other.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, fallback, messageId, exhaustion, scaling, effect, deathMessageType, tags);
    }

    @Override
    public String toString() {
        return "DamageTypeDefinition{"
                + "key=" + key
                + ", fallback=" + fallback
                + ", messageId='" + messageId + '\''
                + ", exhaustion=" + exhaustion
                + ", scaling=" + scaling
                + ", effect=" + effect
                + ", deathMessageType=" + deathMessageType
                + ", tags=" + tags
                + '}';
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /** {@link DamageTypeDefinition} 的构建器. */
    public static final class Builder {

        // 必填
        private final @NotNull Key key;
        private @Nullable Key fallback = null;
        private @Nullable String messageId = null;

        // 可选(有默认值)
        private float exhaustion = 0f;
        private @NotNull DamageScaling scaling = DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER;
        private @Nullable DamageEffect effect = null;
        private @NotNull DeathMessageType deathMessageType = DeathMessageType.DEFAULT;
        private @NotNull Set<Key> tags = new LinkedHashSet<>();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        /** 设置回退键, 必填. */
        @NotNull
        public Builder fallback(@NotNull Key fallback) {
            this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
            return this;
        }

        /** 设置死亡消息 ID, 必填, 最终组成 {@code death.attack.<messageId>} 翻译键. */
        @NotNull
        public Builder messageId(@NotNull String messageId) {
            this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
            return this;
        }

        /** 设置饥饿消耗量, 非负有限值, 默认 {@code 0f}, 非法值抛 {@link IllegalArgumentException}. */
        @NotNull
        public Builder exhaustion(float exhaustion) {
            if (!Float.isFinite(exhaustion) || exhaustion < 0f) {
                throw new IllegalArgumentException(
                        "exhaustion must be a non-negative finite value, got: " + exhaustion);
            }
            this.exhaustion = exhaustion;
            return this;
        }

        /** 设置难度缩放规则, 默认 {@link DamageScaling#WHEN_CAUSED_BY_LIVING_NON_PLAYER}. */
        @NotNull
        public Builder scaling(@NotNull DamageScaling scaling) {
            this.scaling = Objects.requireNonNull(scaling, "scaling must not be null");
            return this;
        }

        /**
         * 设置受击音效, 默认 {@code null} 表示不指定.
         * <p><strong>不要传 {@code DamageEffect.HURT}</strong>, 该静态常量的初始化会触发 {@code InternalAPIBridge}, 在非服务端 JVM 上直接抛异常.
         */
        @NotNull
        public Builder effect(@Nullable DamageEffect effect) {
            this.effect = effect;
            return this;
        }

        /** 设置死亡消息类型, 默认 {@link DeathMessageType#DEFAULT}. */
        @NotNull
        public Builder deathMessageType(@NotNull DeathMessageType deathMessageType) {
            this.deathMessageType = Objects.requireNonNull(deathMessageType, "deathMessageType must not be null");
            return this;
        }

        /** 设置标签集合, 会做防御复制. */
        @NotNull
        public Builder tags(@NotNull Set<Key> tags) {
            Objects.requireNonNull(tags, "tags must not be null");
            this.tags = new LinkedHashSet<>(tags);
            return this;
        }

        /** 构建不可变的 {@link DamageTypeDefinition}, {@code fallback} 与 {@code messageId} 未设置或为空白时抛异常. */
        @NotNull
        public DamageTypeDefinition build() {
            if (fallback == null) {
                throw new IllegalStateException("fallback must be set before building DamageTypeDefinition");
            }
            if (messageId == null) {
                throw new IllegalStateException("messageId must be set before building DamageTypeDefinition");
            }
            if (messageId.isBlank()) {
                throw new IllegalStateException("messageId must not be blank");
            }
            return new DamageTypeDefinition(this);
        }
    }
}
