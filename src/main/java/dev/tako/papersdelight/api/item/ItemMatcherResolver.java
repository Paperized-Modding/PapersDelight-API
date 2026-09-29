package dev.tako.papersdelight.api.item;

/** {@link ItemMatcher} 的判定回调, 由调用方按自己的物品类型实现. */
public interface ItemMatcherResolver<T> {

    boolean matchesItem(T item, String itemId);

    boolean matchesTag(T item, String tagId);

    boolean matchesAdvancedTag(T item, String tagId);
}
