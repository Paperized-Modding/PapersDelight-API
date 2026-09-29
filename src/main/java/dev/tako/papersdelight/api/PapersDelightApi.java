package dev.tako.papersdelight.api;

/**
 * PapersDelight 共享 API 的版本标识。
 *
 * <p>附属插件（如 PapersBrewin）在启动时比对 {@link #VERSION}，
 * 避免 PapersDelight 单独升级后出现静默的 {@code NoSuchMethodError}。</p>
 *
 * <p><b>混淆约定</b>：整个 {@code dev.tako.papersdelight.api} 包在
 * ZKM 混淆中被豁免，类名与方法名保持原样，附属插件才能在运行期正确链接。
 * 修改本包的公开签名等同于修改对外契约。</p>
 */
public final class PapersDelightApi {

    /**
     * API 契约版本。
     *
     * <p>规则：公开签名发生不兼容变更时递增主版本；
     * 仅新增而不破坏既有签名时递增次版本。</p>
     */
    public static final int VERSION = 1;

    /** 附属插件能正常工作所需的最低 API 版本。 */
    public static final int MINIMUM_COMPATIBLE_VERSION = 1;

    private PapersDelightApi() {
    }

    /**
     * 判断附属插件编译时依据的 API 版本能否在当前运行时下工作。
     *
     * @param requiredVersion 附属插件编译期记录的 API 版本
     * @return 兼容则 {@code true}
     */
    public static boolean isCompatible(int requiredVersion) {
        return requiredVersion >= MINIMUM_COMPATIBLE_VERSION && requiredVersion <= VERSION;
    }
}
