import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.scene.control.Button;

public class MainMenuController {

    @FXML private HBox profileButton;
    @FXML private HBox synthMenuTrigger;
    @FXML private VBox synthDropdown;

    @FXML private HBox quizMenuTrigger;
    @FXML private VBox quizDropdown;
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;

    private boolean isSynthOpen = false;
    private boolean isQuizOpen = false;

    @FXML
    public void initialize() {
        // Wire up the profile button to go to the account screen
        if (profileButton != null) {
            profileButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("account.fxml", "SoundLab - Account");
            });
        }

        synthDropdown.setVisible(false);
        synthDropdown.setManaged(false);
        synthMenuTrigger.setOnMouseClicked(event -> {
            if (isQuizOpen) {
                closeDropdown(quizDropdown, () -> isQuizOpen = false);
            }
            if (!isSynthOpen) {
                openDropdown(synthDropdown);
                isSynthOpen = true;
            } else {
                closeDropdown(synthDropdown, () -> isSynthOpen = false);
            }
        });

        quizDropdown.setVisible(false);
        quizDropdown.setManaged(false);
        quizMenuTrigger.setOnMouseClicked(event -> {
            if (isSynthOpen) {
                closeDropdown(synthDropdown, () -> isSynthOpen = false);
            }
            if (!isQuizOpen) {
                openDropdown(quizDropdown);
                isQuizOpen = true;
            } else {
                closeDropdown(quizDropdown, () -> isQuizOpen = false);
            }
        });

        if (logoutButton != null) {
            logoutButton.setOnMouseClicked(event -> {
                ScreenManager.switchTo("login.fxml", "SoundLab - Login");
            });
        }

        if (settingsButton != null) {
            settingsButton.setOnAction(event -> {
                ScreenManager.switchTo("settings.fxml", "SoundLab - Settings");
            });
        }
    }

    private void openDropdown(VBox dropdown) {
        dropdown.setVisible(true);
        dropdown.setManaged(true);
        dropdown.setOpacity(0.0);
        dropdown.setTranslateY(-20);

        FadeTransition fade = new FadeTransition(Duration.millis(200), dropdown);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), dropdown);
        slide.setFromY(-20);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition openAnim = new ParallelTransition(fade, slide);
        openAnim.play();
    }

    private void closeDropdown(VBox dropdown, Runnable onClosed) {
        FadeTransition fade = new FadeTransition(Duration.millis(150), dropdown);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(150), dropdown);
        slide.setFromY(0);
        slide.setToY(-20);
        slide.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition closeAnim = new ParallelTransition(fade, slide);
        closeAnim.setOnFinished(e -> {
            dropdown.setVisible(false);
            dropdown.setManaged(false);
            onClosed.run();
        });
        closeAnim.play();
    }
}