package dev.tako.papersdelight.api.item;

/**
 * 告诉 {@link ItemMatcher} 怎么判断物品是否命中物品 ID, 普通标签或高级标签, 由调用方按自己的物品类型实现.
 *
 * @param <T> 待匹配物品的运行时类型
 */
public interface ItemMatcherResolver<T> {

    boolean matchesItem(T item, String itemId);

    boolean matchesTag(T item, String tagId);

    boolean matchesAdvancedTag(T item, String tagId);
}
