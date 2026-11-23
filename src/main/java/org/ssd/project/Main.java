package org.ssd.project;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.ssd.project.accounts.*;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        /*
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        SmtpConfig smtpConfig = SmtpConfig.fromEnvironment(dotenv::get);
        IEmailService emailService = new JakartaMailEmailService(smtpConfig);
        SQLiteDatabase db = new SQLiteDatabase();
        IUserRepository userRepo = new SQLiteUserRepository(db);

        AuthService authService = new AuthService(userRepo, emailService);
        IStatsRepository statsRepo = new SQLiteStatsRepository(db);

        authService.register("John Cena", "PUT HERE AN ACTUAL EMAIL", "poopoo");
        */
        String javaVersion = System.getProperty("java.version");
        String javafxVersion = System.getProperty("javafx.version");
        Label l =
                new Label(
                        "Hello, JavaFX "
                                + javafxVersion
                                + ", running on Java "
                                + javaVersion
                                + ".");
        Scene scene = new Scene(new StackPane(l), 640, 480);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
