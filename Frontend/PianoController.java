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

public class PianoController {

    @FXML private Label activeNoteLabel;
    @FXML private StackPane pianoKeyboardContainer;
    @FXML private AnchorPane loadingOverlay;

    @FXML private Button synthMenuBtn;
    @FXML private Button quizMenuBtn;
    @FXML private Button dashboardMenuBtn;

    @FXML private ToggleButton sustainPedalToggle;
    @FXML private Slider velocitySlider;
    @FXML private HBox backButton;

    private Synthesizer midiSynth;
    private MidiChannel pianoChannel;
    private Popup synthPopup;
    private Popup quizPopup;
    private Popup dashboardPopup;
    private Popup currentOpenPopup = null;

    private static final String[] WHITE_NOTES = {"C", "D", "E", "F", "G", "A", "B"};
    private static final int[] WHITE_OFFSETS = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] BLACK_WHITE_INDEX = {0, 1, 3, 4, 5};
    private static final String[] BLACK_NOTES = {"C#", "D#", "F#", "G#", "A#"};
    private static final int[] BLACK_OFFSETS = {1, 3, 6, 8, 10};

    private static final double WHITE_KEY_W = 45.6;
    private static final double WHITE_KEY_H = 210.0;
    private static final double BLACK_KEY_W = 26.5;
    private static final double BLACK_KEY_H = 130.0;

    @FXML
    public void initialize() {
        Task<Void> initTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                initMidi();
                Platform.runLater(() -> buildPianoKeyboard(pianoKeyboardContainer, 36));
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
        setupPedalStyling(sustainPedalToggle);

        if (backButton != null) {
            backButton.setOnMouseClicked(e -> {
                closeMidi();
                ScreenManager.switchTo("main_menu.fxml", "SoundLab - Main Menu");
            });
        }

        // Highlight the current active tab button
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
                () -> {},
                () -> { closeMidi(); ScreenManager.switchTo("organ.fxml", "SoundLab - 3-Manual Organ"); },
                () -> { closeMidi(); ScreenManager.switchTo("synth_lead.fxml", "SoundLab - Synth Lead"); }
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
        // If another popup is currently open, close it first
        if (currentOpenPopup != null && currentOpenPopup.isShowing()) {
            currentOpenPopup.hide();
        }

        // If clicking the same open popup, toggle it closed; otherwise open the new one
        if (currentOpenPopup == popup) {
            currentOpenPopup = null;
        } else {
            popup.show(owner, owner.localToScreen(0, owner.getHeight()).getX(), owner.localToScreen(0, owner.getHeight()).getY() + 3);
            currentOpenPopup = popup;
        }
    }

    private void buildPianoKeyboard(StackPane container, int baseMidiNote) {
        if (container == null) return;

        HBox whiteRow = new HBox(1.0);
        whiteRow.setAlignment(Pos.CENTER);

        Pane blackOverlay = new Pane();
        blackOverlay.setPickOnBounds(false);

        int totalWhiteKeys = 36;
        for (int i = 0; i < totalWhiteKeys; i++) {
            int oct = i / 7;
            int w = i % 7;
            int octaveStartMidi = baseMidiNote + (oct * 12);
            int octaveNum = (octaveStartMidi / 12) - 1;

            String noteName = WHITE_NOTES[w] + octaveNum;
            int midiNote = octaveStartMidi + WHITE_OFFSETS[w];

            if (i == 35) {
                octaveNum = 7;
                noteName = "C7";
                midiNote = baseMidiNote + (5 * 12);
            }

            final String finalNoteName = noteName;
            final int finalMidiNote = midiNote;

            Button whiteKey = new Button(noteName);
            whiteKey.setAlignment(Pos.BOTTOM_CENTER);
            whiteKey.setPrefSize(WHITE_KEY_W, WHITE_KEY_H);
            whiteKey.setMinSize(WHITE_KEY_W, WHITE_KEY_H);
            whiteKey.setMaxSize(WHITE_KEY_W, WHITE_KEY_H);
            String idleStyle = "-fx-background-color: #e6dff0; -fx-text-fill: #3b3542; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 11px; -fx-padding: 0 0 12 0; -fx-border-color: #2b2633; -fx-border-width: 1px; -fx-cursor: hand;";
            String activeStyle = "-fx-background-color: #bfa1db; -fx-text-fill: #1a1523; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 11px; -fx-padding: 0 0 12 0; -fx-border-color: #2b2633; -fx-border-width: 1px; -fx-cursor: hand;";
            whiteKey.setStyle(idleStyle);

            whiteKey.setOnMousePressed(ev -> {
                whiteKey.setStyle(activeStyle);
                triggerPianoNote(finalNoteName, finalMidiNote, true);
            });
            whiteKey.setOnMouseReleased(ev -> {
                whiteKey.setStyle(idleStyle);
                triggerPianoNote(finalNoteName, finalMidiNote, false);
            });

            whiteRow.getChildren().add(whiteKey);
        }

        for (int oct = 0; oct < 5; oct++) {
            int octaveStartMidi = baseMidiNote + (oct * 12);
            int octaveNum = (octaveStartMidi / 12) - 1;

            for (int b = 0; b < 5; b++) {
                int whiteIndexInKeyboard = (oct * 7) + BLACK_WHITE_INDEX[b];
                String noteName = BLACK_NOTES[b] + octaveNum;
                int midiNote = octaveStartMidi + BLACK_OFFSETS[b];

                final String finalNoteName = noteName;
                final int finalMidiNote = midiNote;

                Button blackKey = new Button(noteName);
                blackKey.setAlignment(Pos.BOTTOM_CENTER);
                blackKey.setPrefSize(BLACK_KEY_W, BLACK_KEY_H);
                blackKey.setMinSize(BLACK_KEY_W, BLACK_KEY_H);
                blackKey.setMaxSize(BLACK_KEY_W, BLACK_KEY_H);
                String idleStyle = "-fx-background-color: #2b2633; -fx-text-fill: #bfa1db; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 9px; -fx-padding: 0 0 6 0; -fx-border-color: #1a1523; -fx-border-width: 1px; -fx-cursor: hand;";
                String activeStyle = "-fx-background-color: #4b4459; -fx-text-fill: white; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 9px; -fx-padding: 0 0 6 0; -fx-border-color: #bfa1db; -fx-border-width: 1px; -fx-cursor: hand;";
                blackKey.setStyle(idleStyle);

                double xPos = (whiteIndexInKeyboard + 1) * (WHITE_KEY_W + 1.0) - (BLACK_KEY_W / 2.0) - 0.5;
                blackKey.setLayoutX(xPos);
                blackKey.setLayoutY(0);

                blackKey.setOnMousePressed(ev -> {
                    blackKey.setStyle(activeStyle);
                    triggerPianoNote(finalNoteName, finalMidiNote, true);
                });
                blackKey.setOnMouseReleased(ev -> {
                    blackKey.setStyle(idleStyle);
                    triggerPianoNote(finalNoteName, finalMidiNote, false);
                });

                blackOverlay.getChildren().add(blackKey);
            }
        }

        double totalKeyboardWidth = (totalWhiteKeys * WHITE_KEY_W) + ((totalWhiteKeys - 1) * 1.0);
        Pane keyboardWrapper = new Pane();
        keyboardWrapper.setPrefSize(totalKeyboardWidth, WHITE_KEY_H);
        keyboardWrapper.setMinSize(totalKeyboardWidth, WHITE_KEY_H);
        keyboardWrapper.setMaxSize(totalKeyboardWidth, WHITE_KEY_H);
        keyboardWrapper.getChildren().addAll(whiteRow, blackOverlay);

        StackPane.setAlignment(keyboardWrapper, Pos.CENTER);
        container.getChildren().clear();
        container.getChildren().add(keyboardWrapper);
    }

    private void triggerPianoNote(String noteName, int midiNote, boolean noteOn) {
        if (pianoChannel != null) {
            int sustain = (sustainPedalToggle != null && sustainPedalToggle.isSelected()) ? 127 : 0;
            pianoChannel.controlChange(64, sustain);

            int velocity = velocitySlider != null ? (int) velocitySlider.getValue() : 95;

            if (noteOn) {
                pianoChannel.noteOn(midiNote, velocity);
            } else if (sustain == 0) {
                pianoChannel.noteOff(midiNote);
            }
        }

        if (activeNoteLabel != null) {
            activeNoteLabel.setText(noteOn ? String.format("[ PIANO : %s ]", noteName) : "[ READY ]");
        }
    }

    private void setupPedalStyling(ToggleButton toggle) {
        if (toggle == null) return;
        Runnable updateStyle = () -> {
            if (toggle.isSelected()) {
                toggle.setStyle("-fx-background-color: #bfa1db; -fx-text-fill: #2b2633; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 16px; -fx-font-weight: bold; -fx-border-color: #2b2633; -fx-border-width: 3px; -fx-cursor: hand;");
            } else {
                toggle.setStyle("-fx-background-color: #4b4459; -fx-text-fill: #bfa1db; -fx-font-family: 'Super Mario World Text Box'; -fx-font-size: 16px; -fx-border-color: #2b2633; -fx-border-width: 3px; -fx-cursor: hand;");
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
                pianoChannel = channels[0];
                pianoChannel.programChange(0);
            }
        } catch (Exception e) {
            System.err.println("MIDI Piano Synth unavailable: " + e.getMessage());
        }
    }

    private void closeMidi() {
        if (midiSynth != null && midiSynth.isOpen()) {
            midiSynth.close();
        }
    }
}