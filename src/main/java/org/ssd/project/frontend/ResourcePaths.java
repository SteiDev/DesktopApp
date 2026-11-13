package org.ssd.project.frontend;

public final class ResourcePaths {
    private ResourcePaths() {
        throw new AssertionError("No instances of this class are allowed!");
    }

    private static final String FRONTEND_BASE = "/org/ssd/project/frontend/";

    public static final String FXML_MAIN_MENU = FRONTEND_BASE + "main_menu.fxml";
    public static final String FXML_LOGIN = FRONTEND_BASE + "login.fxml";
    public static final String FXML_ACCOUNT = FRONTEND_BASE + "account.fxml";
    public static final String FXML_SETTINGS = FRONTEND_BASE + "settings.fxml";

    public static final String FONT = FRONTEND_BASE + "font.ttf";
}
