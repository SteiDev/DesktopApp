import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class AccountController {

    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label passwordLabel;
    @FXML private Label quizzesCompletedLabel;
    @FXML private Label accuracyLabel;
    @FXML private Label recommendationLabel;
    @FXML private Button logoutButton;
    @FXML private HBox backButton;

    @FXML
    public void initialize() {
        // Change these to whatever personal/mock info you'd like
        String personalUsername = "John Doe";
        String personalEmail = "john.doe@soundlab.com";
        String rawPassword = "supersecretpassword";
        int quizzesCompleted = 14;
        double accuracy = 91.2;

        if (usernameLabel != null) usernameLabel.setText("Username: " + personalUsername);
        if (emailLabel != null) emailLabel.setText("Email: " + personalEmail);
        
        if (passwordLabel != null) {
            passwordLabel.setText("Password: " + "*".repeat(rawPassword.length()));
        }

        if (quizzesCompletedLabel != null) {
            quizzesCompletedLabel.setText("Quizzes Completed: " + quizzesCompleted);
        }
        
        if (accuracyLabel != null) {
            accuracyLabel.setText(String.format("Answer Accuracy: %.1f%%", accuracy));
        }

        if (recommendationLabel != null) {
            recommendationLabel.setText(generateRecommendations(accuracy));
        }

        // Wire up back button to main menu
        if (backButton != null) {
            backButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("main_menu.fxml", "SoundLab - Main Menu");
            });
        }

        // Wire up logout button to login screen
        if (logoutButton != null) {
            logoutButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("login.fxml", "SoundLab - Login");
            });
        }
    }

    private String generateRecommendations(double accuracy) {
        if (accuracy < 75.0) {
            return "- Fundamental Note Identification\n- Basic Triad Chords Drill";
        } else if (accuracy < 90.0) {
            return "- Interval Recognition (Thirds & Sixths)\n- Scale Degree Training";
        } else {
            return "- Advanced Extended Chords (9th / 13th)\n- Complex Ear Training Progressions";
        }
    }
}