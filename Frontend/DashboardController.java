import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

public class DashboardController {

    @FXML private Label quizzesCompletedLabel;
    @FXML private Label accuracyLabel;
    @FXML private Label recommendationLabel;
    @FXML private HBox backButton;

    @FXML private Button synthMenuBtn;
    @FXML private Button quizMenuBtn;
    @FXML private Button dashboardMenuBtn;

    private Popup synthPopup;
    private Popup quizPopup;
    private Popup dashboardPopup;
    private Popup currentOpenPopup = null;

    @FXML
    public void initialize() {
        int quizzesCompleted = 14;
        double accuracy = 91.2;

        if (quizzesCompletedLabel != null) {
            quizzesCompletedLabel.setText("Quizzes Completed: " + quizzesCompleted);
        }
        
        if (accuracyLabel != null) {
            accuracyLabel.setText(String.format("Answer Accuracy: %.1f%%", accuracy));
        }

        if (recommendationLabel != null) {
            recommendationLabel.setText(generateRecommendations(accuracy));
        }

        setupCustomDropdownPopups();

        if (backButton != null) {
            backButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("main_menu.fxml", "SoundLab - Main Menu");
            });
        }
    }

    private void setupCustomDropdownPopups() {
        synthPopup = createCustomPopup(
            new String[]{"Acoustic Grand Piano", "Organ", "Synth Lead"},
            new Runnable[]{
                () -> { ScreenManager.switchTo("piano.fxml", "SoundLab - Acoustic Grand Piano"); },
                () -> { ScreenManager.switchTo("organ.fxml", "SoundLab - 3-Manual Organ"); },
                () -> { ScreenManager.switchTo("synth_lead.fxml", "SoundLab - Synth Lead"); }
            }
        );

        quizPopup = createCustomPopup(
            new String[]{"Pitch Identification Quiz", "Interval Training Mode"},
            new Runnable[]{
                () -> { ScreenManager.switchTo("quiz.fxml", "SoundLab - Ear Training Quiz"); },
                () -> { ScreenManager.switchTo("quiz.fxml", "SoundLab - Ear Training Quiz"); }
            }
        );

        // Exactly 1 item in both arrays so it never throws an array index error!
        dashboardPopup = createCustomPopup(
            new String[]{"Learning Dashboard"},
            new Runnable[]{
                () -> {} // Already on Dashboard
            }
        );

        synthPopup.setAutoHide(false);
        quizPopup.setAutoHide(false);
        dashboardPopup.setAutoHide(false);

        if (synthMenuBtn != null) synthMenuBtn.setOnAction(e -> togglePopup(synthPopup, synthMenuBtn));
        if (quizMenuBtn != null) quizMenuBtn.setOnAction(e -> togglePopup(quizPopup, quizMenuBtn));
        if (dashboardMenuBtn != null) dashboardMenuBtn.setOnAction(e -> togglePopup(dashboardPopup, dashboardMenuBtn));
    }

    private Popup createCustomPopup(String[] options, Runnable[] actions) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox contentBox = new VBox(2.0);
        contentBox.setStyle(
            "-fx-background-color: #3b3542; " +
            "-fx-border-color: #2b2633; " +
            "-fx-border-width: 3px; " +
            "-fx-padding: 6px;"
        );

        for (int i = 0; i < options.length; i++) {
            final String text = options[i];
            final Runnable action = actions[i];

            Label optLabel = new Label(text);
            optLabel.setMaxWidth(Double.MAX_VALUE);

            String idleStyle = 
                "-fx-background-color: #3b3542; " +
                "-fx-text-fill: #bfa1db; " +
                "-fx-font-family: 'Super Mario World Text Box'; " +
                "-fx-font-size: 18px; " +
                "-fx-padding: 8px 12px; " +
                "-fx-cursor: hand;";

            String hoverStyle = 
                "-fx-background-color: #bfa1db; " +
                "-fx-text-fill: #1a1523; " +
                "-fx-font-family: 'Super Mario World Text Box'; " +
                "-fx-font-size: 18px; " +
                "-fx-padding: 8px 12px; " +
                "-fx-cursor: hand;";

            optLabel.setStyle(idleStyle);
            optLabel.setOnMouseEntered(ev -> optLabel.setStyle(hoverStyle));
            optLabel.setOnMouseExited(ev -> optLabel.setStyle(idleStyle));

            optLabel.setOnMouseClicked(ev -> {
                popup.hide();
                action.run();
            });

            contentBox.getChildren().add(optLabel);
        }

        popup.getContent().add(contentBox);
        return popup;
    }

    private void togglePopup(Popup popup, Button owner) {
        if (currentOpenPopup != null && currentOpenPopup.isShowing()) {
            currentOpenPopup.hide();
        }

        if (currentOpenPopup == popup) {
            currentOpenPopup = null;
        } else {
            popup.show(owner, owner.localToScreen(0, owner.getHeight()).getX(), owner.localToScreen(0, owner.getHeight()).getY() + 3);
            currentOpenPopup = popup;
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