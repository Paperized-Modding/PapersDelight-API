package dev.tako.papersdelight.api.util;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * 文本解析工具。
 * <p>
 * 支持四种输入格式并存：
 * <ul>
 *   <li><b>PlaceholderAPI 占位符</b> — {@code "%player_name%"}（需 PAPI 插件 + Player 上下文）</li>
 *   <li><b>MiniMessage</b> — {@code "<green>Hello</green>"}、{@code "<#ff6688>粉色</#ff6688>"}</li>
 *   <li><b>传统 § 色码</b> — {@code "§aHello"} 或 {@code "\u00a77提示"}</li>
 *   <li><b>& 别名</b> — {@code "&aHello"} 自动转为 {@code "§aHello"}</li>
 * </ul>
 * <p>
 * 解析策略：
 * <ol>
 *   <li>若提供了 Player 且 PAPI 可用、文本含 {@code %}，先解析 PAPI 占位符</li>
 *   <li>检测文本中是否包含 MiniMessage 标签（{@code <tag>}），
 *       有则用 MiniMessage 解析</li>
 *   <li>否则将 {@code &} 替换为 {@code §} 后用传统解析</li>
 * </ol>
 */
public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** 匹配 MiniMessage 标签：以 &lt; 开头，紧跟字母/#，至少一个非 &gt; 字符，以 &gt; 结尾 */
    private static final Pattern MINI_MESSAGE_DETECT = Pattern.compile("<[a-zA-Z#][^>]*>");

    /**
     * PlaceholderAPI 是否可用。
     *
     * <p>api 模块不能反向依赖具体插件主类，所以这里由宿主插件在启动时注入。
     * 默认 {@code false}，即未注入时不尝试解析 PAPI 占位符。</p>
     */
    private static volatile BooleanSupplier papiAvailable = () -> false;

    /**
     * 由宿主插件在启动时注入 PAPI 可用性判断。
     *
     * @param supplier 可用性来源，传 {@code null} 视为不可用
     */
    public static void setPapiAvailability(BooleanSupplier supplier) {
        papiAvailable = supplier != null ? supplier : () -> false;
    }

    private static boolean isPapiAvailable() {
        try {
            return papiAvailable.getAsBoolean();
        } catch (Throwable ignored) {
            // 注入方异常不应让文本解析整体失败，退化为不解析占位符
            return false;
        }
    }

    private TextUtil() {
    }

    /**
     * 智能解析文本（不含 PAPI 占位符解析）。
     */
    public static Component parse(String text) {
        return parse(null, text);
    }

    /**
     * 智能解析文本，优先解析 PAPI 占位符。
     *
     * @param player 玩家上下文（可为 null，跳过 PAPI）
     * @param text   待解析文本
     */
    public static Component parse(Player player, String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // 1. PAPI 占位符解析
        String resolved = text;
        if (player != null && isPapiAvailable() && text.indexOf('%') >= 0) {
            resolved = PlaceholderAPI.setPlaceholders(player, text);
        }

        // 2. MiniMessage 解析
        if (MINI_MESSAGE_DETECT.matcher(resolved).find()) {
            try {
                return MINI_MESSAGE.deserialize(resolved);
            } catch (Exception ignored) {
                // MiniMessage 标签存在但格式错误时，回退到传统解析
            }
        }

        // 3. 传统 § / & 解析
        return LEGACY.deserialize(resolved.replace("&", "§"));
    }

    /**
     * 根据发送者类型智能解析。若为玩家则解析 PAPI，控制台则跳过。
     */
    public static Component parse(CommandSender sender, String text) {
        if (sender instanceof Player player) return parse(player, text);
        return parse(text);
    }

    /**
     * 批量解析（不含 PAPI）。
     */
    public static List<Component> parseList(List<String> lines) {
        return parseList(null, lines);
    }

    /**
     * 批量解析，含 PAPI。
     */
    public static List<Component> parseList(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream().map(line -> parse(player, line)).toList();
    }

    /**
     * @deprecated 请使用 {@link #parse(String)} 或 {@link #parse(Player, String)}
     */
    @Deprecated
    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /**
     * @deprecated 请使用 {@link #parseList(List)} 或 {@link #parseList(Player, List)}
     */
    @Deprecated
    public static List<Component> legacyList(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream().map(TextUtil::legacy).toList();
    }
}
