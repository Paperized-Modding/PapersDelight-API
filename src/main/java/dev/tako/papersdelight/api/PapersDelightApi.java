package dev.tako.papersdelight.api;

/**
 * 附属插件的 API 版本契约: 启动时用 {@link #isCompatible(int)} 自检, 避免 PapersDelight 单独升级后的 {@code NoSuchMethodError}.
 * <p><strong>整个包在 ZKM 混淆中被豁免</strong>, 公开签名即对外契约, 不要改动.
 */
public final class PapersDelightApi {

    public static final int VERSION = 1;

    public static final int MINIMUM_COMPATIBLE_VERSION = 1;

    private PapersDelightApi() {
    }

    /** 编译期记录的 API 版本能不能在当前运行时工作. */
    public static boolean isCompatible(int requiredVersion) {
        return requiredVersion >= MINIMUM_COMPATIBLE_VERSION && requiredVersion <= VERSION;
    }
}
