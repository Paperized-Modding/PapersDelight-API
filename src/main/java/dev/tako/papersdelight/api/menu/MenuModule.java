package dev.tako.papersdelight.api.menu;

public interface MenuModule {

    String getId();

    Menu createMenu();

    MenuEventHandler getEventHandler();
}
