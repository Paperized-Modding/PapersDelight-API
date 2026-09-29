package dev.tako.papersdelight.api.damage;

/**
 * 自定义伤害类型的注册状态。
 */
public enum DamageTypeRegistrationState {
    /**
     * 自定义伤害类型已在本进程成功注册进注册表。
     */
    REGISTERED,

    /**
     * 未注册成功，运行期将回退解析。
     */
    FALLBACK
}
