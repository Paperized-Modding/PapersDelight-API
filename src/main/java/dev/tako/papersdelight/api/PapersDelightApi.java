package dev.tako.papersdelight.api;

public final class PapersDelightApi {

    public static final int VERSION = 4;

    public static final int MINIMUM_COMPATIBLE_VERSION = 4;

    private PapersDelightApi() {
    }

    /**
     * @param requiredVersion 附属插件编译期记录的 API 版本
     * @return 兼容返回 {@code true}
     */
    public static boolean isCompatible(int requiredVersion) {
        return requiredVersion >= MINIMUM_COMPATIBLE_VERSION && requiredVersion <= VERSION;
    }
}
