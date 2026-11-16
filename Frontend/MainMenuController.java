import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

public class MainMenuController {

    @FXML private Button synthTabBtn;
    @FXML private Button quizTabBtn;
    @FXML private Button dashboardTabBtn;

    @FXML private AnchorPane synthPane;
    @FXML private AnchorPane quizPane;
    @FXML private AnchorPane dashboardPane;

    @FXML private HBox pianoOption;
    @FXML private HBox organOption;
    @FXML private HBox leadOption;

    @FXML private HBox startQuizOption;
    @FXML private HBox reviewQuizOption;

    @FXML private HBox openDashboardOption; // Added for the dashboard navigation

    @FXML private HBox profileButton;
    @FXML private Button logoutButton;

    @FXML
    public void initialize() {
        // Set Synthesizer tab active by default on load
        showSynthTab();

        // Tab button click actions
        synthTabBtn.setOnAction(e -> showSynthTab());
        quizTabBtn.setOnAction(e -> showQuizTab());
        dashboardTabBtn.setOnAction(e -> showDashboardTab());

        // Navigation actions for Synthesizer options
        if (pianoOption != null) {
            pianoOption.setOnMouseClicked(e -> ScreenManager.switchTo("piano.fxml", "SoundLab - Acoustic Grand Piano"));
        }
        if (organOption != null) {
            organOption.setOnMouseClicked(e -> ScreenManager.switchTo("organ.fxml", "SoundLab - 3-Manual Organ"));
        }
        if (leadOption != null) {
            leadOption.setOnMouseClicked(e -> ScreenManager.switchTo("synth_lead.fxml", "SoundLab - Synth Lead"));
        }

        // Navigation actions for Quiz options
        if (startQuizOption != null) {
            startQuizOption.setOnMouseClicked(e -> ScreenManager.switchTo("quiz.fxml", "SoundLab - Ear Training Quiz"));
        }
        if (reviewQuizOption != null) {
            reviewQuizOption.setOnMouseClicked(e -> ScreenManager.switchTo("quiz.fxml", "SoundLab - Ear Training Quiz"));
        }

        // Navigation action for Learning Dashboard option
        if (openDashboardOption != null) {
            openDashboardOption.setOnMouseClicked(e -> ScreenManager.switchTo("dashboard.fxml", "SoundLab - Learning Dashboard"));
        }

        // Profile, Settings, and Logout actions
        if (profileButton != null) {
            profileButton.setOnMouseClicked(e -> ScreenManager.switchTo("account.fxml", "SoundLab - User Account"));
        }
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> ScreenManager.switchTo("login.fxml", "SoundLab - Login"));
        }
    }

    private void showSynthTab() {
        synthPane.setVisible(true);
        synthPane.setManaged(true);
        quizPane.setVisible(false);
        quizPane.setManaged(false);
        dashboardPane.setVisible(false);
        dashboardPane.setManaged(false);

        updateActiveTab(synthTabBtn);
    }

    private void showQuizTab() {
        synthPane.setVisible(false);
        synthPane.setManaged(false);
        quizPane.setVisible(true);
        quizPane.setManaged(true);
        dashboardPane.setVisible(false);
        dashboardPane.setManaged(false);

        updateActiveTab(quizTabBtn);
    }

    private void showDashboardTab() {
        synthPane.setVisible(false);
        synthPane.setManaged(false);
        quizPane.setVisible(false);
        quizPane.setManaged(false);
        dashboardPane.setVisible(true);
        dashboardPane.setManaged(true);

        updateActiveTab(dashboardTabBtn);
    }

    private void updateActiveTab(Button activeBtn) {
        String activeStyle = 
            "-fx-background-color: #bfa1db; " +
            "-fx-text-fill: #2b2633; " +
            "-fx-font-family: 'Super Mario World Text Box'; " +
            "-fx-font-size: 28px; " +
            "-fx-font-weight: bold; " +
            "-fx-border-color: #2b2633; " +
            "-fx-border-width: 3px; " +
            "-fx-cursor: hand; " +
            "-fx-pref-height: 72px; " +
            "-fx-padding: 0 45px; " +
            "-fx-effect: dropshadow(three-pass-box, #1a1523, 0, 0, -3, 3);";

        String inactiveStyle = 
            "-fx-background-color: #4b4459; " +
            "-fx-text-fill: #bfa1db; " +
            "-fx-font-family: 'Super Mario World Text Box'; " +
            "-fx-font-size: 28px; " +
            "-fx-border-color: #2b2633; " +
            "-fx-border-width: 3px; " +
            "-fx-cursor: hand; " +
            "-fx-pref-height: 72px; " +
            "-fx-padding: 0 45px; " +
            "-fx-effect: dropshadow(three-pass-box, #1a1523, 0, 0, -3, 3);";

        synthTabBtn.setStyle(activeBtn == synthTabBtn ? activeStyle : inactiveStyle);
        quizTabBtn.setStyle(activeBtn == quizTabBtn ? activeStyle : inactiveStyle);
        dashboardTabBtn.setStyle(activeBtn == dashboardTabBtn ? activeStyle : inactiveStyle);
    }
}