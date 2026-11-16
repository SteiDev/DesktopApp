import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;

public class SynthLeadController {

    @FXML private Label activeNoteLabel;
    @FXML private StackPane leadKeyboardContainer;
    @FXML private AnchorPane loadingOverlay;

    @FXML private Button synthMenuBtn;
    @FXML private Button quizMenuBtn;
    @FXML private Button dashboardMenuBtn;

    @FXML private ToggleButton sawWaveToggle;
    @FXML private ToggleButton squareWaveToggle;
    @FXML private ToggleButton sineWaveToggle;

    @FXML private Slider cutoffSlider;
    @FXML private Slider glideSlider;

    @FXML private HBox backButton;

    private Synthesizer midiSynth;
    private MidiChannel leadChannel;
    private int currentPlayingMidiNote = -1;
    private Popup synthPopup;
    private Popup quizPopup;
    private Popup dashboardPopup;
    private Popup currentOpenPopup = null;

    private static final String[] WHITE_NOTES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final int[] WHITE_OFFSETS = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] BLACK_WHITE_INDEX = {0, 1, 3, 4, 5};
    private static final String[] BLACK_NOTES = {"C#", "D#", "F#", "G#", "A#"};
    private static final int[] BLACK_OFFSETS = {1, 3, 6, 8, 10};

    private static final double WHITE_KEY_W = 72.0;
    private static final double WHITE_KEY_H = 210.0;
    private static final double BLACK_KEY_W = 42.0;
    private static final double BLACK_KEY_H = 130.0;

    @FXML
    public void initialize() {
        Task<Void> initTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                initMidi();
                Platform.runLater(() -> buildLeadKeyboard(leadKeyboardContainer, 48)); // C3 base
                return null;
            }
        };

        initTask.setOnSucceeded(e -> {
            if (loadingOverlay != null) {
                loadingOverlay.setVisible(false);
                loadingOverlay.setManaged(false);
            }
        });

        new Thread(initTask).start();

        setupCustomDropdownPopups();

        if (backButton != null) {
            backButton.setOnMouseClicked(e -> {
                closeMidi();
                ScreenManager.switchTo("main_menu.fxml", "SoundLab - Main Menu");
            });
        }

        // Highlight active tab
        if (synthMenuBtn != null) {
            synthMenuBtn.setStyle(
                "-fx-background-color: #bfa1db; " +
                "-fx-text-fill: #2b2633; " +
                "-fx-font-family: 'Super Mario World Text Box'; " +
                "-fx-font-size: 16px; " +
                "-fx-font-weight: bold; " +
                "-fx-border-color: #2b2633; " +
                "-fx-border-width: 3px; " +
                "-fx-cursor: hand;"
            );
        }
    }

    private void setupCustomDropdownPopups() {
        synthPopup = createCustomPopup(
            new String[]{"Acoustic Grand Piano", "Organ", "Synth Lead"},
            new Runnable[]{
                () -> { closeMidi(); ScreenManager.switchTo("piano.fxml", "SoundLab - Acoustic Grand Piano"); },
                () -> { closeMidi(); ScreenManager.switchTo("organ.fxml", "SoundLab - 3-Manual Organ"); },
                () -> {} // Already on Synth Lead
            }
        );

        quizPopup = createCustomPopup(
            new String[]{"Pitch Identification Quiz", "Interval Training Mode"},
            new Runnable[]{
                () -> { closeMidi(); ScreenManager.switchTo("quiz.fxml", "SoundLab - Ear Training Quiz"); },
                () -> { closeMidi(); ScreenManager.switchTo("quiz.fxml", "SoundLab - Ear Training Quiz"); }
            }
        );

        dashboardPopup = createCustomPopup(
            new String[]{"Learning Dashboard"},
            new Runnable[]{
                () -> { closeMidi(); ScreenManager.switchTo("dashboard.fxml", "SoundLab - Learning Dashboard"); }
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

    private void buildLeadKeyboard(StackPane container, int baseMidiNote) {
        if (container == null) return;

        HBox whiteRow = new HBox(2.0);
        whiteRow.setAlignment(Pos.CENTER);

        Pane blackOverlay = new Pane();
        blackOverlay.setPickOnBounds(false);

        int octaves = 3;
        int totalWhiteKeys = octaves * 7;

        for (int i = 0; i <= totalWhiteKeys; i++) {
            int oct = i / 7;
            int w = i % 7;
            int octaveStartMidi = baseMidiNote + (oct * 12);
            int octaveNum = (octaveStartMidi / 12) - 1;

            String noteName = WHITE_NOTES[w] + octaveNum;
            int midiNote = octaveStartMidi + WHITE_OFFSETS[w];

            if (i == 21) {
                octaveNum = 6;
                noteName = "C6";
                midiNote = baseMidiNote + (3 * 12);
            }

            final String finalNoteName = noteName;
            final int finalMidiNote = midiNote;

            Button whiteKey = new Button(noteName);
            whiteKey.setAlignment(Pos.BOTTOM_CENTER);
            whiteKey.setPrefSize(WHITE_KEY_W, WHITE_KEY_H);
            whiteKey.setMinSize(WHITE_KEY_W, WHITE_KEY_H);
            whiteKey.setMaxSize(WHITE_KEY_W, WHITE_KEY_H);
            String idleStyle = "-fx-background-color: #e6dff0; -fx-text-fill: #3b3542; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 13px; -fx-padding: 0 0 14 0; -fx-border-color: #2b2633; -fx-border-width: 2px; -fx-cursor: hand;";
            String activeStyle = "-fx-background-color: #bfa1db; -fx-text-fill: #1a1523; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 13px; -fx-padding: 0 0 14 0; -fx-border-color: #2b2633; -fx-border-width: 2px; -fx-cursor: hand;";
            whiteKey.setStyle(idleStyle);

            whiteKey.setOnMousePressed(ev -> {
                whiteKey.setStyle(activeStyle);
                triggerMonoNote(finalNoteName, finalMidiNote, true);
            });
            whiteKey.setOnMouseReleased(ev -> {
                whiteKey.setStyle(idleStyle);
                triggerMonoNote(finalNoteName, finalMidiNote, false);
            });

            whiteRow.getChildren().add(whiteKey);
        }

        for (int oct = 0; oct < octaves; oct++) {
            int octaveStartMidi = baseMidiNote + (oct * 12);
            int octaveNum = (octaveStartMidi / 12) - 1;

            for (int b = 0; b < 5; b++) {
                int whiteIndexInKeyboard = (oct * 7) + BLACK_WHITE_INDEX[b];
                String noteName = BLACK_NOTES[b] + octaveNum;
                int midiNote = octaveStartMidi + BLACK_OFFSETS[b];

                final String finalNoteName = noteName;
                final int finalMidiNote = midiNote;

                Button blackKey = new Button(BLACK_NOTES[b]);
                blackKey.setAlignment(Pos.BOTTOM_CENTER);
                blackKey.setPrefSize(BLACK_KEY_W, BLACK_KEY_H);
                blackKey.setMinSize(BLACK_KEY_W, BLACK_KEY_H);
                blackKey.setMaxSize(BLACK_KEY_W, BLACK_KEY_H);
                String idleStyle = "-fx-background-color: #2b2633; -fx-text-fill: #bfa1db; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 11px; -fx-padding: 0 0 10 0; -fx-border-color: #1a1523; -fx-border-width: 2px; -fx-cursor: hand;";
                String activeStyle = "-fx-background-color: #4b4459; -fx-text-fill: white; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 11px; -fx-padding: 0 0 10 0; -fx-border-color: #bfa1db; -fx-border-width: 2px; -fx-cursor: hand;";
                blackKey.setStyle(idleStyle);

                double xPos = (whiteIndexInKeyboard + 1) * (WHITE_KEY_W + 2.0) - (BLACK_KEY_W / 2.0);
                blackKey.setLayoutX(xPos);
                blackKey.setLayoutY(0);

                blackKey.setOnMousePressed(ev -> {
                    blackKey.setStyle(activeStyle);
                    triggerMonoNote(finalNoteName, finalMidiNote, true);
                });
                blackKey.setOnMouseReleased(ev -> {
                    blackKey.setStyle(idleStyle);
                    triggerMonoNote(finalNoteName, finalMidiNote, false);
                });

                blackOverlay.getChildren().add(blackKey);
            }
        }

        double totalWrapperWidth = ((totalWhiteKeys + 1) * WHITE_KEY_W) + (totalWhiteKeys * 2.0);
        Pane keyboardWrapper = new Pane();
        keyboardWrapper.setPrefSize(totalWrapperWidth, WHITE_KEY_H);
        keyboardWrapper.setMinSize(totalWrapperWidth, WHITE_KEY_H);
        keyboardWrapper.setMaxSize(totalWrapperWidth, WHITE_KEY_H);
        keyboardWrapper.getChildren().addAll(whiteRow, blackOverlay);

        StackPane.setAlignment(keyboardWrapper, Pos.CENTER);
        container.getChildren().clear();
        container.getChildren().add(keyboardWrapper);
    }

    private void triggerMonoNote(String noteName, int midiNote, boolean noteOn) {
        if (leadChannel != null) {
            int cutoff = cutoffSlider != null ? (int) cutoffSlider.getValue() : 100;
            leadChannel.controlChange(74, cutoff);

            if (noteOn) {
                if (currentPlayingMidiNote != -1 && currentPlayingMidiNote != midiNote) {
                    leadChannel.noteOff(currentPlayingMidiNote);
                }
                leadChannel.noteOn(midiNote, 110);
                currentPlayingMidiNote = midiNote;
            } else {
                if (currentPlayingMidiNote == midiNote) {
                    leadChannel.noteOff(midiNote);
                    currentPlayingMidiNote = -1;
                }
            }
        }

        if (activeNoteLabel != null) {
            activeNoteLabel.setText(noteOn ? String.format("[ LEAD : %s ]", noteName) : "[ READY ]");
        }
    }

    private void setupToggleGroup(ToggleButton... toggles) {
        for (ToggleButton t : toggles) {
            if (t == null) continue;
            t.setOnAction(e -> {
                for (ToggleButton other : toggles) {
                    if (other != t && other != null) {
                        other.setSelected(false);
                    }
                }
                if (!t.isSelected()) {
                    t.setSelected(true);
                }
            });
        }
    }

    private void setupToggleStyling(ToggleButton toggle) {
        if (toggle == null) return;
        Runnable updateStyle = () -> {
            if (toggle.isSelected()) {
                toggle.setStyle("-fx-background-color: #bfa1db; -fx-text-fill: #2b2633; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 15px; -fx-font-weight: bold; -fx-border-color: #2b2633; -fx-border-width: 3px; -fx-cursor: hand;");
            } else {
                toggle.setStyle("-fx-background-color: #4b4459; -fx-text-fill: #bfa1db; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 15px; -fx-border-color: #2b2633; -fx-border-width: 3px; -fx-cursor: hand;");
            }
        };
        updateStyle.run();
        toggle.selectedProperty().addListener((obs, oldVal, newVal) -> updateStyle.run());
    }

    private void initMidi() {
        try {
            midiSynth = MidiSystem.getSynthesizer();
            midiSynth.open();
            MidiChannel[] channels = midiSynth.getChannels();
            if (channels != null && channels.length > 0) {
                leadChannel = channels[0];
                leadChannel.programChange(81);
            }
        } catch (Exception e) {
            System.err.println("MIDI Lead Synth unavailable: " + e.getMessage());
        }
    }

    private void closeMidi() {
        if (midiSynth != null && midiSynth.isOpen()) {
            midiSynth.close();
        }
    }
}