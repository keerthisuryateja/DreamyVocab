package com.vocabulary;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class UIController {

    // -- FXML bindings --------------------------------------------------------
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sourceCombo;
    @FXML private Button searchBtn;
    @FXML private VBox cardArea;
    @FXML private VBox emptyState;
    @FXML private Label wordLabel;
    @FXML private TextArea meaningArea;
    @FXML private ListView<String> suggestionList;
    @FXML private Label sourceLabel;
    @FXML private Label todayBadge;
    @FXML private Label totalBadge;
    @FXML private Label statusLabel;
    @FXML private ListView<String> historyList;
    @FXML private TextField historyFilter;
    @FXML private Button editBtn;
    @FXML private Button cancelEditBtn;
    @FXML private VBox sidebarPane;
    @FXML private Button sidebarToggleBtn;

    // -- State ----------------------------------------------------------------
    private PythonBridge bridge;
    private Scene scene;
    private Stage stage;

    /** Window dimensions for the base zoom level (1.0�). */
    private static final double BASE_FONT   = 14.0;
    private static final double BASE_WIDTH  = 820.0;
    private static final double BASE_HEIGHT = 580.0;

    /** Decoration offset measured after first show (title bar + borders). */
    private double decorW = 0;
    private double decorH = 0;

    private double zoomLevel = 1.0;
    private boolean editMode = false;
    private boolean sidebarVisible = true;
    private String  savedMeaning  = "";

    private final ObservableList<String> allWords = FXCollections.observableArrayList();

    // -- Init -----------------------------------------------------------------
    @FXML
    public void initialize() {
        sourceCombo.getItems().addAll("auto", "merriam", "wiktionary", "manual");
        sourceCombo.getSelectionModel().selectFirst();
        historyList.setItems(allWords);

        historyList.setOnMouseClicked(e -> {
            String sel = historyList.getSelectionModel().getSelectedItem();
            if (sel != null) { searchField.setText(sel); onSearch(); }
        });
        suggestionList.setOnMouseClicked(e -> {
            String sel = suggestionList.getSelectionModel().getSelectedItem();
            if (sel != null) { searchField.setText(sel); onSearch(); }
        });

        try {
            bridge = new PythonBridge();
            updateStats();
            refreshHistory();
        } catch (IOException ex) {
            showError("Failed to start backend: " + ex.getMessage());
        }
    }

    /**
     * Called by Main.java AFTER primaryStage.show() so that stage dimensions
     * (including OS decorations) are fully known. We measure the decoration
     * offset once here so applyZoom() can resize correctly at any zoom level.
     */
    public void setupZoom(Scene s) {
        this.scene = s;
        this.stage = (Stage) s.getWindow();

        // Measure decoration (title bar + borders) now that the window is shown
        decorW = stage.getWidth()  - s.getWidth();
        decorH = stage.getHeight() - s.getHeight();

        stage.setMinWidth (BASE_WIDTH  * 0.75 + decorW);
        stage.setMinHeight(BASE_HEIGHT * 0.75 + decorH);

        // Keyboard shortcuts
        s.setOnKeyPressed(e -> {
            if (e.isControlDown()) {
                KeyCode code = e.getCode();
                boolean zoomInShortcut = code == KeyCode.ADD
                        || code == KeyCode.PLUS
                        || (code == KeyCode.EQUALS && e.isShiftDown());
                boolean zoomOutShortcut = code == KeyCode.SUBTRACT
                        || code == KeyCode.MINUS;

                if (zoomInShortcut) {
                    onZoomIn();
                    e.consume();
                    return;
                }
                if (zoomOutShortcut) {
                    onZoomOut();
                    e.consume();
                    return;
                }
            }

            if (e.getCode() == KeyCode.ESCAPE) {
                if (editMode) {
                    onCancelEdit();
                } else {
                    hideCard();
                    searchField.clear();
                    showStatus("");
                }
            }
        });

        // Focus the search field on startup
        Platform.runLater(searchField::requestFocus);
    }

    // -- Search ---------------------------------------------------------------
    @FXML
    private void onSearch() {
        String word = searchField.getText().trim();
        if (word.isEmpty()) return;

        // Reset edit mode silently
        if (editMode) exitEditMode(false);

        showCard();
        meaningArea.setText("Looking up\u2026");
        sourceLabel.setText("");
        suggestionList.setVisible(false);
        suggestionList.setManaged(false);
        showStatus("");
        setLoading(true);

        new Thread(() -> {
            Map<String, Object> req = Map.of(
                    "action", "lookup",
                    "word",   word,
                    "source", sourceCombo.getValue());
            Map<String, Object> resp = bridge.sendRequest(req);

            Platform.runLater(() -> {
                setLoading(false);
                String status = (String) resp.get("status");
                wordLabel.setText(word);

                switch (status == null ? "" : status) {
                    case "found", "fetched" -> {
                        meaningArea.setText((String) resp.get("meaning"));
                        sourceLabel.setText("via " + resp.get("source"));
                        updateStats();
                        refreshHistory();
                    }
                    case "suggestions" -> {
                        meaningArea.setText("Did you mean one of these?");
                        @SuppressWarnings("unchecked")
                        List<String> sugs = (List<String>) resp.get("suggestions");
                        suggestionList.getItems().setAll(sugs);
                        suggestionList.setVisible(true);
                        suggestionList.setManaged(true);
                    }
                    default -> meaningArea.setText("No results found.");
                }
            });
        }).start();
    }

    private void setLoading(boolean loading) {
        searchField.setDisable(loading);
        sourceCombo.setDisable(loading);
        searchBtn.setDisable(loading);
        searchBtn.setText(loading ? "\u2026" : "Search");
    }

    // -- Sidebar --------------------------------------------------------------
    @FXML
    private void onToggleSidebar() {
        sidebarVisible = !sidebarVisible;
        sidebarPane.setVisible(sidebarVisible);
        sidebarPane.setManaged(sidebarVisible);
        sidebarToggleBtn.setText(sidebarVisible ? "\u25c0" : "\u25b6");
    }

    @FXML
    private void onFilterHistory() {
        String filter = historyFilter.getText().trim().toLowerCase();
        if (filter.isEmpty()) {
            historyList.setItems(allWords);
        } else {
            ObservableList<String> filtered = FXCollections.observableArrayList();
            for (String w : allWords) if (w.contains(filter)) filtered.add(w);
            historyList.setItems(filtered);
        }
    }

    private void refreshHistory() {
        if (bridge == null) return;
        new Thread(() -> {
            Map<String, Object> req  = Map.of("action", "list_words");
            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if ("success".equals(resp.get("status"))) {
                    @SuppressWarnings("unchecked")
                    List<String> words = (List<String>) resp.get("words");
                    allWords.setAll(words);
                    onFilterHistory();
                }
            });
        }).start();
    }

    // -- Zoom -----------------------------------------------------------------
    @FXML private void onZoomIn()    { if (zoomLevel < 2.0) { zoomLevel = round1(zoomLevel + 0.1); applyZoom(); } }
    @FXML private void onZoomOut()   { if (zoomLevel > 0.6) { zoomLevel = round1(zoomLevel - 0.1); applyZoom(); } }
    @FXML private void onZoomReset() { zoomLevel = 1.0; applyZoom(); }

    private void applyZoom() {
        if (scene == null) return;
        // Change root font-size so all em-relative CSS values scale uniformly.
        // Since every size in dreamy.css is expressed in em, this single change
        // cascades to the entire UI without touching the window dimensions.
        int px = (int) Math.round(BASE_FONT * zoomLevel);
        scene.getRoot().setStyle("-fx-font-size: " + px + "px;");
    }

    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    // -- TTS ------------------------------------------------------------------
    @FXML
    private void onTTS() {
        String text = wordLabel.getText();
        if (text == null || text.isEmpty()) return;
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return;
        // strip non-alphanumeric to prevent shell injection
        String safe = text.replaceAll("[^a-zA-Z0-9 \\-]", "").trim();
        if (safe.isEmpty()) return;
        new Thread(() -> {
            try {
                new ProcessBuilder("powershell", "-NoProfile", "-c",
                        "Add-Type -AssemblyName System.Speech; " +
                        "(New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('" + safe + "')")
                        .start();
            } catch (Exception ignored) {}
        }).start();
    }

    // -- Edit / Save / Cancel -------------------------------------------------
    @FXML
    private void onEdit() {
        if (!editMode) {
            // Enter edit mode
            savedMeaning = meaningArea.getText();
            editMode = true;
            meaningArea.setEditable(true);
            meaningArea.requestFocus();
            editBtn.setText("Save");
            editBtn.getStyleClass().remove("action-btn");
            if (!editBtn.getStyleClass().contains("action-btn-active"))
                editBtn.getStyleClass().add("action-btn-active");
            cancelEditBtn.setVisible(true);
            cancelEditBtn.setManaged(true);
            showStatus("Editing \u2014 Save to confirm, Cancel or Escape to discard");
        } else {
            commitEdit();
        }
    }

    @FXML
    private void onCancelEdit() {
        meaningArea.setText(savedMeaning);
        exitEditMode(false);
        showStatus("Edit cancelled");
    }

    private void commitEdit() {
        String newMeaning = meaningArea.getText().trim();
        if (newMeaning.isEmpty()) { showError("Meaning cannot be empty"); return; }
        exitEditMode(false);
        String word = wordLabel.getText();
        new Thread(() -> {
            Map<String, Object> req  = Map.of("action", "edit", "word", word, "meaning", newMeaning);
            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if ("success".equals(resp.get("status"))) showSuccess("Saved \u2713");
                else showError("Save failed");
            });
        }).start();
    }

    private void exitEditMode(boolean keepEditable) {
        editMode = false;
        meaningArea.setEditable(keepEditable);
        editBtn.setText("Edit");
        editBtn.getStyleClass().remove("action-btn-active");
        if (!editBtn.getStyleClass().contains("action-btn"))
            editBtn.getStyleClass().add("action-btn");
        cancelEditBtn.setVisible(false);
        cancelEditBtn.setManaged(false);
    }

    // -- Delete ---------------------------------------------------------------
    @FXML
    private void onDelete() {
        String word = wordLabel.getText();
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
        dlg.setTitle("Delete Word");
        dlg.setHeaderText(null);
        dlg.setContentText("Remove \u201c" + word + "\u201d from your vocabulary?");
        ButtonType btnDelete = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getButtonTypes().setAll(btnDelete, btnCancel);
        dlg.showAndWait().ifPresent(btn -> {
            if (btn == btnDelete) {
                new Thread(() -> {
                    Map<String, Object> req  = Map.of("action", "delete", "word", word);
                    Map<String, Object> resp = bridge.sendRequest(req);
                    Platform.runLater(() -> {
                        if ("success".equals(resp.get("status"))) {
                            hideCard();
                            searchField.clear();
                            updateStats();
                            refreshHistory();
                            showSuccess("\u201c" + word + "\u201d deleted");
                        }
                    });
                }).start();
            }
        });
    }

    // -- Stats ----------------------------------------------------------------
    private void updateStats() {
        if (bridge == null) return;
        new Thread(() -> {
            Map<String, Object> req  = Map.of("action", "stats");
            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if ("success".equals(resp.get("status"))) {
                    Number t = (Number) resp.get("today");
                    Number x = (Number) resp.get("total");
                    todayBadge.setText("Today: " + (t != null ? t.intValue() : 0));
                    totalBadge.setText("Total: " + (x != null ? x.intValue() : 0));
                }
            });
        }).start();
    }

    // -- Helpers --------------------------------------------------------------
    private void showCard() {
        emptyState.setVisible(false);  emptyState.setManaged(false);
        cardArea.setVisible(true);     cardArea.setManaged(true);
    }

    private void hideCard() {
        cardArea.setVisible(false);    cardArea.setManaged(false);
        emptyState.setVisible(true);   emptyState.setManaged(true);
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
    }

    private void showSuccess(String msg) {
        statusLabel.setText(msg);
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add("status-success");
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.getStyleClass().removeAll("status-error", "status-success");
        statusLabel.getStyleClass().add("status-error");
    }

    public void shutdown() {
        if (bridge != null) bridge.close();
    }
}
