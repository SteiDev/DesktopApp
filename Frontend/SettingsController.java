import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class SettingsController {

    @FXML private Button audioEngineButton;
    @FXML private Button themeButton;
    @FXML private Button volumeButton;
    @FXML private Label statusMessageLabel;
    @FXML private HBox backButton;

    private int volumeLevel = 80;
    private boolean useMidi = true;

    @FXML
    public void initialize() {
        // Toggle Audio Engine behavior
        if (audioEngineButton != null) {
            audioEngineButton.setOnAction(event -> {
                useMidi = !useMidi;
                audioEngineButton.setText(useMidi ? "MIDI (Default)" : "Synthesizer Raw");
                updateStatus("Audio engine configuration updated.");
            });
        }

        // Toggle Theme behavior
        if (themeButton != null) {
            themeButton.setOnAction(event -> {
                updateStatus("Theme configuration is locked to Retro Dark.");
            });
        }

        // Cycle Volume levels
        if (volumeButton != null) {
            volumeButton.setOnAction(event -> {
                volumeLevel = (volumeLevel + 20) > 100 ? 20 : volumeLevel + 20;
                volumeButton.setText(volumeLevel + "%");
                updateStatus("Master volume adjusted to " + volumeLevel + "%.");
            });
        }

        // Wire up back button to return to Main Menu
        if (backButton != null) {
            backButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("main_menu.fxml", "SoundLab - Main Menu");
            });
        }
    }

    private void updateStatus(String message) {
        if (statusMessageLabel != null) {
            statusMessageLabel.setText("Status: " + message);
        }
    }
}