package dev.tako.papersdelight.api.damage;

/**
 * 自定义伤害类型在本进程中的注册状态.
 */
public enum DamageTypeRegistrationState {
    /** 已经成功注册进运行期注册表. */
    REGISTERED,

    /** 没注册成功, 运行期会按回退键解析. */
    FALLBACK
}
