package org.ssd.project;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssd.project.frontend.ResourcePaths;
import org.ssd.project.frontend.ScreenManager;

public class Main extends Application {
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
        Logger logger = LoggerFactory.getLogger(Main.class);
        logger.info("This is a test to see if the main logger works");
        logger.info("Testing sqlite database");
        SqliteDemo.testSqLite();
        launch(args);
    }

    // For testing purposes
    public static boolean returnTrue() {
        return true;
    }
}
