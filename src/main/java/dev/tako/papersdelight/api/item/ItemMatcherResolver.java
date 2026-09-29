package dev.tako.papersdelight.api.item;

/**
 * 将不可变匹配表达式与具体物品运行时解耦的解析接口。
 *
 * @param <T> 待匹配物品的运行时类型
 */
public interface ItemMatcherResolver<T> {

    boolean matchesItem(T item, String itemId);

    boolean matchesTag(T item, String tagId);

    boolean matchesAdvancedTag(T item, String tagId);
}
