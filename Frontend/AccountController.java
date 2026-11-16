import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

public class AccountController {

    @FXML private Label usernameLabel;
    @FXML private Label emailLabel;
    @FXML private Label passwordLabel;
    @FXML private Button changeUsernameButton;
    @FXML private Button changePasswordButton;
    @FXML private Button logoutButton;
    @FXML private Button deleteAccountButton;
    @FXML private Button cancelDeleteButton;
    @FXML private Button confirmDeleteButton;
    @FXML private AnchorPane deleteModal;
    @FXML private HBox backButton;

    @FXML
    public void initialize() {
        String personalUsername = "John Doe";
        String personalEmail = "john.doe@soundlab.com";
        String rawPassword = "supersecretpassword";

        if (usernameLabel != null) usernameLabel.setText("Username: " + personalUsername);
        if (emailLabel != null) emailLabel.setText("Email: " + personalEmail);
        
        if (passwordLabel != null) {
            passwordLabel.setText("Password: " + "*".repeat(rawPassword.length()));
        }

        // Change Username action placeholder
        if (changeUsernameButton != null) {
            changeUsernameButton.setOnAction(event -> {
                System.out.println("Change username clicked");
                // TODO: Add prompt dialog to update username
            });
        }

        // Change Password action placeholder
        if (changePasswordButton != null) {
            changePasswordButton.setOnAction(event -> {
                System.out.println("Change password clicked");
                // TODO: Add prompt dialog to update password
            });
        }

        // Wire up back button to main menu
        if (backButton != null) {
            backButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("main_menu.fxml", "SoundLab - Main Menu");
            });
        }

        // Wire up logout button to login screen
        if (logoutButton != null) {
            logoutButton.setOnAction(event -> {
                ScreenManager.switchTo("login.fxml", "SoundLab - Login");
            });
        }

        // Show custom retro delete confirmation modal
        if (deleteAccountButton != null && deleteModal != null) {
            deleteAccountButton.setOnAction(event -> {
                deleteModal.setVisible(true);
                deleteModal.setManaged(true);
            });
        }

        // Hide modal on cancel
        if (cancelDeleteButton != null && deleteModal != null) {
            cancelDeleteButton.setOnAction(event -> {
                deleteModal.setVisible(false);
                deleteModal.setManaged(false);
            });
        }

        // Confirm deletion and return to login
        if (confirmDeleteButton != null) {
            confirmDeleteButton.setOnAction(event -> {
                ScreenManager.switchTo("login.fxml", "SoundLab - Login");
            });
        }
    }
}