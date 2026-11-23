package org.ssd.project;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Scanner;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.ssd.project.accounts.*;
import org.ssd.project.email.IEmailService;
import org.ssd.project.email.JakartaMailEmailService;
import org.ssd.project.email.SmtpConfig;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        SmtpConfig smtpConfig = SmtpConfig.fromEnvironment(dotenv::get);
        IEmailService emailService = new JakartaMailEmailService(smtpConfig);
        SQLiteDatabase db = new SQLiteDatabase();
        IUserRepository userRepo = new SQLiteUserRepository(db);

        AuthService authService = new AuthService(userRepo, emailService);
        // IStatsRepository statsRepo = new SQLiteStatsRepository(db);

        /* Console demo of the verification flow. It runs off the FX thread because
        reading stdin blocks, and the window must not freeze while waiting for the code. */
        Thread demo = new Thread(() -> runVerificationDemo(authService), "verification-demo");
        demo.setDaemon(true);
        demo.start();

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

    private static void runVerificationDemo(AuthService authService) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        AuthService.StartResult started = authService.startRegistration(username, email, password);
        if (started != AuthService.StartResult.CODE_SENT) {
            System.out.println("Could not start registration: " + started);
            return;
        }
        System.out.print("Enter the code sent to your email: ");
        String code = scanner.nextLine().trim();
        System.out.println(authService.completeRegistration(email, code));
    }

    public static void main(String[] args) {
        launch();
    }
}
