package org.ssd.project.frontend;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class LoginController {

    @FXML private Button loginButton;
    @FXML private Button shutdownButton;

    @FXML
    public void initialize() {
        if (loginButton != null) {
            loginButton.setOnMouseClicked(
                    event -> {
                        ScreenManager.switchTo(
                                ResourcePaths.FXML_MAIN_MENU, "SoundLab - Main Menu");
                    });
        }

        if (shutdownButton != null) {
            shutdownButton.setOnMouseClicked(
                    event -> {
                        Platform.exit();
                        System.exit(0);
                    });
        }
    }
}
