package dev.tako.papersdelight.api;

/**
 * 附属插件引用的 API 版本标识.
 * <p>{@link #VERSION} 是当前版本, 启动时用 {@link #isCompatible(int)} 比一下, 就能避免 PapersDelight 单独升级后的 {@code NoSuchMethodError}.
 * <p><strong>整个 {@code dev.tako.papersdelight.api} 包在 ZKM 混淆中被豁免</strong>, 类名与方法名保持原样, 改这个包的公开签名等于改对外契约.
 */
public final class PapersDelightApi {

    /** 当前 API 版本; 公开签名不兼容变更时加主版本, 只新增时加次版本. */
    public static final int VERSION = 1;

    /** 附属插件能正常工作所需的最低 API 版本. */
    public static final int MINIMUM_COMPATIBLE_VERSION = 1;

    private PapersDelightApi() {
    }

    /**
     * 附属插件编译时记录的 API 版本能不能在当前运行时工作.
     *
     * @param requiredVersion 附属插件编译期记录的 API 版本
     * @return 兼容返回 {@code true}
     */
    public static boolean isCompatible(int requiredVersion) {
        return requiredVersion >= MINIMUM_COMPATIBLE_VERSION && requiredVersion <= VERSION;
    }
}
