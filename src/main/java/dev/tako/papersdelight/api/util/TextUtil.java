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
 * 文本解析工具: 把配置里的字符串转成 Adventure {@link Component}.
 * <p>四种写法都认: PlaceholderAPI 占位符 {@code %player_name%}, MiniMessage 标签 {@code <green>Hello</green>}, 传统 § 色码, 以及会自动转成 § 的 {@code &} 别名; 含 {@code <tag>} 的文本走 MiniMessage(格式错误时回退传统解析), 传了 {@link Player} 且 PAPI 可用时先做占位符替换.
 * <p><strong>PAPI 是否可用由宿主插件通过 {@link #setPapiAvailability} 注入</strong>, 没注入就一律跳过占位符解析.
 */
public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Pattern MINI_MESSAGE_DETECT = Pattern.compile("<[a-zA-Z#][^>]*>");

    /** PAPI 可用性, 由 {@link #setPapiAvailability} 注入; 默认不可用. */
    private static volatile BooleanSupplier papiAvailable = () -> false;

    /**
     * 注入 PAPI 可用性判断, 由宿主插件在启动时调用.
     *
     * @param supplier 传 {@code null} 等同于不可用
     */
    public static void setPapiAvailability(BooleanSupplier supplier) {
        papiAvailable = supplier != null ? supplier : () -> false;
    }

    private static boolean isPapiAvailable() {
        try {
            return papiAvailable.getAsBoolean();
        } catch (Throwable ignored) {
            // 注入方异常不应让文本解析整体失败,退化为不解析占位符
            return false;
        }
    }

    private TextUtil() {
    }

    /** 解析文本, 不做 PAPI 占位符替换. */
    public static Component parse(String text) {
        return parse(null, text);
    }

    /**
     * 解析文本, 先替换 PAPI 占位符.
     *
     * @param player 玩家上下文, 传 {@code null} 就跳过占位符
     * @param text 待解析文本
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
                // MiniMessage 标签存在但格式错误时,回退到传统解析
            }
        }

        // 3. 传统 § / & 解析
        return LEGACY.deserialize(resolved.replace("&", "§"));
    }

    /** 玩家会做 PAPI 替换, 控制台等其他发送者不会. */
    public static Component parse(CommandSender sender, String text) {
        if (sender instanceof Player player) return parse(player, text);
        return parse(text);
    }

    /** 批量解析, 不做 PAPI 替换. */
    public static List<Component> parseList(List<String> lines) {
        return parseList(null, lines);
    }

    /** 批量解析, 每一项都带玩家上下文做 PAPI 替换. */
    public static List<Component> parseList(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream().map(line -> parse(player, line)).toList();
    }

    /**
     * @deprecated 改用 {@link #parse(String)} 或 {@link #parse(Player, String)}.
     */
    @Deprecated
    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /**
     * @deprecated 改用 {@link #parseList(List)} 或 {@link #parseList(Player, List)}.
     */
    @Deprecated
    public static List<Component> legacyList(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream().map(TextUtil::legacy).toList();
    }
}
