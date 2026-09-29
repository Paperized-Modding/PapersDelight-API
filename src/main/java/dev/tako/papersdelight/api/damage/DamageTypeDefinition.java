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
 * 版本无关的伤害类型定义（不可变值对象）。
 *
 * <p>本类在纯 JVM 测试环境中安全使用：不引用 {@link DamageEffect} 的静态常量
 * （如 {@code DamageEffect.HURT}），因此不会触发 {@code InternalAPIBridge} 初始化。</p>
 *
 * <h3>典型用法</h3>
 * <pre>{@code
 * DamageTypeDefinition def = DamageTypeDefinition.builder(Key.key("myplugin", "blade"))
 *         .fallback(Key.key("minecraft", "generic"))
 *         .messageId("blade")
 *         .build();
 * }</pre>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code key} 的命名空间不得为 {@code minecraft}</li>
 *   <li>{@code fallback} 必须显式设置（无默认值）</li>
 *   <li>{@code messageId} 不得为空白字符串</li>
 *   <li>{@code exhaustion} 必须非负且为有限值（NaN / 无穷大不允许）</li>
 * </ul>
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
        // 防御复制，并生成不可变集合
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags));
    }

    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    /**
     * 创建以 {@code key} 为主键的 Builder。
     *
     * @param key 伤害类型键，命名空间不得为 {@code minecraft}
     * @return 新的 Builder 实例
     * @throws IllegalArgumentException 若 {@code key} 使用了 {@code minecraft} 命名空间
     * @throws NullPointerException     若 {@code key} 为 {@code null}
     */
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

    /** @return 伤害类型的主键（命名空间非 minecraft） */
    @NotNull
    public Key key() {
        return key;
    }

    /** @return 在目标服务端版本中使用的回退键（通常指向原版 damage type） */
    @NotNull
    public Key fallback() {
        return fallback;
    }

    /** @return 死亡消息翻译键的后半段（{@code death.attack.<messageId>}） */
    @NotNull
    public String messageId() {
        return messageId;
    }

    /** @return 此伤害类型造成的饥饿消耗量（≥0，有限值） */
    public float exhaustion() {
        return exhaustion;
    }

    /** @return 难度缩放规则 */
    @NotNull
    public DamageScaling scaling() {
        return scaling;
    }

    /**
     * @return 受击音效效果，{@code null} 表示使用默认效果（不在此处引用 {@code DamageEffect.HURT}）
     */
    @Nullable
    public DamageEffect effect() {
        return effect;
    }

    /** @return 死亡消息类型 */
    @NotNull
    public DeathMessageType deathMessageType() {
        return deathMessageType;
    }

    /**
     * @return 不可变的标签集合（对结果集的修改将抛出 {@link UnsupportedOperationException}）
     */
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

    /**
     * {@link DamageTypeDefinition} 的可变构建器。
     */
    public static final class Builder {

        // 必填
        private final @NotNull Key key;
        private @Nullable Key fallback = null;
        private @Nullable String messageId = null;

        // 可选（有默认值）
        private float exhaustion = 0f;
        private @NotNull DamageScaling scaling = DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER;
        private @Nullable DamageEffect effect = null;
        private @NotNull DeathMessageType deathMessageType = DeathMessageType.DEFAULT;
        private @NotNull Set<Key> tags = new LinkedHashSet<>();

        private Builder(@NotNull Key key) {
            this.key = key;
        }

        /**
         * 设置回退键（必填）。
         *
         * @param fallback 目标版本中的回退 damage type 键
         * @return this
         */
        @NotNull
        public Builder fallback(@NotNull Key fallback) {
            this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
            return this;
        }

        /**
         * 设置死亡消息 ID（必填）。
         *
         * @param messageId 非空白字符串
         * @return this
         */
        @NotNull
        public Builder messageId(@NotNull String messageId) {
            this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
            return this;
        }

        /**
         * 设置饥饿消耗量（默认 {@code 0f}）。
         *
         * @param exhaustion 非负有限值
         * @return this
         * @throws IllegalArgumentException 若值为负数、NaN 或无穷大
         */
        @NotNull
        public Builder exhaustion(float exhaustion) {
            if (!Float.isFinite(exhaustion) || exhaustion < 0f) {
                throw new IllegalArgumentException(
                        "exhaustion must be a non-negative finite value, got: " + exhaustion);
            }
            this.exhaustion = exhaustion;
            return this;
        }

        /**
         * 设置难度缩放规则（默认 {@link DamageScaling#WHEN_CAUSED_BY_LIVING_NON_PLAYER}）。
         *
         * @param scaling 非 null
         * @return this
         */
        @NotNull
        public Builder scaling(@NotNull DamageScaling scaling) {
            this.scaling = Objects.requireNonNull(scaling, "scaling must not be null");
            return this;
        }

        /**
         * 设置受击音效效果（默认 {@code null}，即不指定效果）。
         *
         * <p><b>注意</b>：请勿在此传入 {@code DamageEffect.HURT} 作为"默认值"——
         * 该静态常量的初始化会触发 {@code InternalAPIBridge}，在非服务端 JVM 上会抛出异常。</p>
         *
         * @param effect 可为 {@code null}
         * @return this
         */
        @NotNull
        public Builder effect(@Nullable DamageEffect effect) {
            this.effect = effect;
            return this;
        }

        /**
         * 设置死亡消息类型（默认 {@link DeathMessageType#DEFAULT}）。
         *
         * @param deathMessageType 非 null
         * @return this
         */
        @NotNull
        public Builder deathMessageType(@NotNull DeathMessageType deathMessageType) {
            this.deathMessageType = Objects.requireNonNull(deathMessageType, "deathMessageType must not be null");
            return this;
        }

        /**
         * 设置标签集合（做防御复制）。
         *
         * @param tags 可为空集合，但不得为 null
         * @return this
         */
        @NotNull
        public Builder tags(@NotNull Set<Key> tags) {
            Objects.requireNonNull(tags, "tags must not be null");
            this.tags = new LinkedHashSet<>(tags);
            return this;
        }

        /**
         * 构建并返回不可变的 {@link DamageTypeDefinition}。
         *
         * @return 新实例
         * @throws IllegalStateException 若必填字段（{@code fallback}、{@code messageId}）未设置，
         *                               或 {@code messageId} 为空白字符串
         */
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
