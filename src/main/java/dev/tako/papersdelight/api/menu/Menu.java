package dev.tako.papersdelight.api.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Menu {

    private final String title;
    private final int size;
    private final Map<Integer, MenuItem> items = new HashMap<>();

    public Menu(String title, int size) {
        this.title = title;
        this.size = size;
    }

    public Menu setItem(int slot, MenuItem item) {
        if (slot >= 0 && slot < size) {
            items.put(slot, item);
        }
        return this;
    }

    public MenuItem getItemAt(int slot) {
        return items.get(slot);
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public Map<Integer, MenuItem> getItems() {
        return Collections.unmodifiableMap(items);
    }
}
