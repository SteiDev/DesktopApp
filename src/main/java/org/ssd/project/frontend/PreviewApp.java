package org.ssd.project.frontend;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.text.Font;

import org.ssd.project.frontend.ResourcePaths;

public class PreviewApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Correct font loading via InputStream
        try {
            Font.loadFont(getClass().getResourceAsStream(ResourcePaths.FONT), 20);
        } catch (Exception e) {
            try {
                Font.loadFont(getClass().getResourceAsStream(ResourcePaths.FONT), 20);
            } catch (Exception ex) {
                // Fallback if font file path needs adjustment
            }
        }

        ScreenManager.initialize(stage);
        ScreenManager.switchTo(ResourcePaths.FXML_LOGIN, "SoundLab - Login");
    }

    public static void main(String[] args) {
        launch(args);
    }
}