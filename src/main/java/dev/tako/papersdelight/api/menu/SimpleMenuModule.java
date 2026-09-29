package dev.tako.papersdelight.api.menu;

import java.util.function.Supplier;

public class SimpleMenuModule implements MenuModule {

    private final String id;
    private final Supplier<Menu> menuSupplier;
    private final MenuEventHandler eventHandler;

    private SimpleMenuModule(Builder builder) {
        this.id = builder.id;
        this.menuSupplier = builder.menuSupplier;
        this.eventHandler = builder.eventHandler;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Menu createMenu() {
        return menuSupplier.get();
    }

    @Override
    public MenuEventHandler getEventHandler() {
        return eventHandler;
    }

    public static class Builder {

        private String id;
        private Supplier<Menu> menuSupplier;
        private MenuEventHandler eventHandler;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder menu(Supplier<Menu> menuSupplier) {
            this.menuSupplier = menuSupplier;
            return this;
        }

        public Builder handler(MenuEventHandler handler) {
            this.eventHandler = handler;
            return this;
        }

        public SimpleMenuModule build() {
            return new SimpleMenuModule(this);
        }
    }
}
