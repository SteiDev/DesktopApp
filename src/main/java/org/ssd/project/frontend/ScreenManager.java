package org.ssd.project.frontend;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class ScreenManager {
    private static Stage primaryStage;

    public static void initialize(Stage stage) {
        primaryStage = stage;
    }

    public static void switchTo(String fxmlFileName, String title) {
        try {
            Parent root = FXMLLoader.load(ScreenManager.class.getResource(fxmlFileName));
            if (primaryStage.getScene() == null) {
                primaryStage.setScene(new Scene(root, 1920, 1080));
            } else {
                primaryStage.getScene().setRoot(root);
            }
            primaryStage.setTitle(title);
            primaryStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to load screen: " + fxmlFileName);
        }
    }
}