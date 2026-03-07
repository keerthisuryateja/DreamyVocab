package com.vocabulary;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
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
    @FXML private Label streakBadge;
    @FXML private Label statusLabel;
    @FXML private ListView<String> historyList;
    @FXML private TextField historyFilter;
    @FXML private Button editBtn;
    @FXML private Button cancelEditBtn;
    @FXML private VBox sidebarPane;
    @FXML private Button sidebarToggleBtn;
    @FXML private Button quizStartBtn;
    @FXML private ComboBox<String> quizSizeCombo;
    @FXML private VBox quizPane;
    @FXML private Label quizProgressLabel;
    @FXML private Label quizSummaryLabel;
    @FXML private Label quizWordLabel;
    @FXML private Label quizFeedbackLabel;
    @FXML private RadioButton quizOptionA;
    @FXML private RadioButton quizOptionB;
    @FXML private RadioButton quizOptionC;
    @FXML private RadioButton quizOptionD;
    @FXML private Button quizSubmitBtn;
    @FXML private Button quizNextBtn;
    @FXML private Button retryWrongBtn;
    @FXML private Button quizCloseBtn;
    @FXML private ListView<String> quizHistoryList;

    // -- State ----------------------------------------------------------------
    private PythonBridge bridge;
    private Scene scene;
    private Stage stage;

    /** Window dimensions for the base zoom level (1.0). */
    private static final double BASE_FONT   = 16.0;
    private static final double BASE_WIDTH  = 820.0;
    private static final double BASE_HEIGHT = 580.0;

    /** Decoration offset measured after first show (title bar + borders). */
    private double decorW = 0;
    private double decorH = 0;

    private double zoomLevel = 1.3;
    private boolean editMode = false;
    private boolean sidebarVisible = true;
    private String  savedMeaning  = "";

    private final ObservableList<String> allWords = FXCollections.observableArrayList();
    private final ObservableList<String> quizHistoryRows = FXCollections.observableArrayList();

    private final ToggleGroup quizToggleGroup = new ToggleGroup();
    private List<Map<String, Object>> quizQuestions = List.of();
    private final List<Map<String, Object>> quizAnswers = new ArrayList<>();
    private final List<String> retryWords = new ArrayList<>();
    private int currentQuizIndex = -1;
    private String activeQuizSessionId = "";

    // -- Init -----------------------------------------------------------------
    @FXML
    public void initialize() {
        sourceCombo.getItems().addAll("auto", "merriam", "wiktionary", "manual");
        sourceCombo.getSelectionModel().selectFirst();
        quizSizeCombo.getItems().addAll("5", "10", "15", "20");
        quizSizeCombo.getSelectionModel().selectFirst();
        historyList.setItems(allWords);
        quizHistoryList.setItems(quizHistoryRows);

        quizOptionA.setToggleGroup(quizToggleGroup);
        quizOptionB.setToggleGroup(quizToggleGroup);
        quizOptionC.setToggleGroup(quizToggleGroup);
        quizOptionD.setToggleGroup(quizToggleGroup);

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
            refreshQuizHistory();
            refreshStreak();
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

        // Apply the default zoom so BASE_FONT takes effect immediately
        applyZoom();

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
        VBox.setVgrow(meaningArea, Priority.ALWAYS);
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
                        VBox.setVgrow(meaningArea, Priority.NEVER);
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

    // -- Add Word manually ---------------------------------------------------
    @FXML
    private void onAddWord() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Add Word");
        dlg.setHeaderText("Add a word manually to your vocabulary");

        TextField wordField = new TextField();
        wordField.setPromptText("Word");
        TextArea defArea = new TextArea();
        defArea.setPromptText("Definition");
        defArea.setPrefRowCount(4);
        defArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8);
        grid.add(new Label("Word:"), 0, 0);
        grid.add(wordField, 1, 0);
        grid.add(new Label("Definition:"), 0, 1);
        grid.add(defArea, 1, 1);
        GridPane.setHgrow(wordField, Priority.ALWAYS);
        GridPane.setHgrow(defArea, Priority.ALWAYS);
        grid.setMinWidth(420);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Platform.runLater(wordField::requestFocus);

        dlg.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            String w = wordField.getText().trim().toLowerCase();
            String def = defArea.getText().trim();
            if (w.isEmpty() || def.isEmpty()) {
                showError("Word and definition cannot be empty.");
                return;
            }
            new Thread(() -> {
                Map<String, Object> req = Map.of("action", "add", "word", w, "meaning", def);
                Map<String, Object> resp = bridge.sendRequest(req);
                Platform.runLater(() -> {
                    if ("success".equals(resp.get("status"))) {
                        searchField.setText(w);
                        onSearch();
                        updateStats();
                        refreshHistory();
                        refreshStreak();
                        showSuccess("\u201c" + w + "\u201d added.");
                    } else {
                        showError("Word already exists or could not be saved.");
                    }
                });
            }).start();
        });
    }

    // -- Export ---------------------------------------------------------------
    @FXML
    private void onExport() {
        if (bridge == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Vocabulary");
        fc.setInitialFileName("vocabulary.txt");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Text Files", "*.txt"),
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        java.io.File file = fc.showSaveDialog(stage);
        if (file == null) return;

        boolean csv = file.getName().toLowerCase().endsWith(".csv");
        new Thread(() -> {
            Map<String, Object> req  = Map.of("action", "export_words");
            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if (!"success".equals(resp.get("status"))) {
                    showError("Export failed."); return;
                }
                Object raw = resp.get("entries");
                if (!(raw instanceof List<?> list)) { showError("No data to export."); return; }
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(
                        Path.of(file.getAbsolutePath()), StandardCharsets.UTF_8))) {
                    if (csv) pw.println("word,meaning");
                    for (Object item : list) {
                        if (!(item instanceof Map<?,?> m)) continue;
                        String word = asString(m.get("word"));
                        String meaning = asString(m.get("meaning")).replace("\n", " ");
                        if (csv) {
                            pw.println("\"" + word.replace("\"","\"\"") + "\",\"" + meaning.replace("\"","\"\"") + "\"");
                        } else {
                            pw.println(word.toUpperCase());
                            pw.println(meaning);
                            pw.println();
                        }
                    }
                    showSuccess("Exported " + list.size() + " words to " + file.getName());
                } catch (Exception ex) {
                    showError("Could not write file: " + ex.getMessage());
                }
            });
        }).start();
    }

    // -- Streak ---------------------------------------------------------------
    private void refreshStreak() {
        if (bridge == null) return;
        new Thread(() -> {
            Map<String, Object> req  = Map.of("action", "streak");
            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if ("success".equals(resp.get("status"))) {
                    int s = asInt(resp.get("streak"), 0);
                    if (s > 1) {
                        streakBadge.setText("\uD83D\uDD25 " + s + " day streak");
                        streakBadge.setVisible(true);
                        streakBadge.setManaged(true);
                    } else {
                        streakBadge.setVisible(false);
                        streakBadge.setManaged(false);
                    }
                }
            });
        }).start();
    }

    // -- Quiz -----------------------------------------------------------------
    @FXML
    private void onCloseQuiz() {
        quizPane.setVisible(false);
        quizPane.setManaged(false);
        // Restore welcome state if no word card is currently showing
        if (!cardArea.isManaged()) {
            emptyState.setVisible(true);
            emptyState.setManaged(true);
        }
    }

    @FXML
    private void onStartQuiz() {
        startQuiz(null);
    }

    @FXML
    private void onRetryWrongQuiz() {
        if (retryWords.isEmpty()) {
            showStatus("No incorrect words to retry.");
            return;
        }
        startQuiz(new ArrayList<>(retryWords));
    }

    private void startQuiz(List<String> specificWords) {
        if (bridge == null) return;

        int limit = 5;
        try { limit = Integer.parseInt(quizSizeCombo.getValue()); } catch (Exception ignored) {}
        final int quizLimit = limit;

        quizStartBtn.setDisable(true);
        showStatus("Preparing quiz…");

        new Thread(() -> {
            Map<String, Object> req = new HashMap<>();
            req.put("action", "start_quiz");
            if (specificWords != null && !specificWords.isEmpty()) {
                req.put("words", specificWords);
                req.put("limit", specificWords.size());
            } else {
                req.put("limit", quizLimit);
            }

            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                quizStartBtn.setDisable(false);

                if (!"success".equals(resp.get("status"))) {
                    String msg = asString(resp.get("message"));
                    showError(msg.isBlank() ? "Unable to start quiz." : msg);
                    return;
                }

                activeQuizSessionId = asString(resp.get("session_id"));
                quizQuestions = asQuestionList(resp.get("questions"));
                quizAnswers.clear();
                retryWords.clear();
                currentQuizIndex = 0;

                quizPane.setVisible(true);
                quizPane.setManaged(true);
                // Hide the welcome empty-state so it doesn't steal vertical space
                // (has VBox.vgrow=ALWAYS which would collapse quiz pane)
                if (!cardArea.isManaged()) {
                    emptyState.setVisible(false);
                    emptyState.setManaged(false);
                }
                retryWrongBtn.setVisible(false);
                retryWrongBtn.setManaged(false);
                quizSummaryLabel.setText("");
                renderCurrentQuizQuestion();
                showSuccess("Quiz ready.");
            });
        }).start();
    }

    @FXML
    private void onSubmitQuizAnswer() {
        if (currentQuizIndex < 0 || currentQuizIndex >= quizQuestions.size()) return;

        Toggle selectedToggle = quizToggleGroup.getSelectedToggle();
        if (selectedToggle == null) {
            showStatus("Choose an option first.");
            return;
        }

        Map<String, Object> question = quizQuestions.get(currentQuizIndex);
        int selectedIndex = asInt(selectedToggle.getUserData(), -1);
        int correctIndex = asInt(question.get("correct_index"), -1);
        List<String> options = asStringList(question.get("options"));
        String word = asString(question.get("word"));

        if (selectedIndex < 0 || selectedIndex >= options.size() || correctIndex < 0 || correctIndex >= options.size()) {
            showError("Quiz data is invalid. Start the quiz again.");
            return;
        }

        String selectedAnswer = options.get(selectedIndex);
        String correctAnswer = options.get(correctIndex);
        boolean isCorrect = selectedIndex == correctIndex;

        Map<String, Object> answer = new HashMap<>();
        answer.put("word", word);
        answer.put("selected_answer", selectedAnswer);
        answer.put("correct_answer", correctAnswer);
        answer.put("is_correct", isCorrect);
        quizAnswers.add(answer);

        if (!isCorrect && !retryWords.contains(word)) {
            retryWords.add(word);
        }

        quizFeedbackLabel.setText(isCorrect ? "Correct." : "Wrong. Correct answer: " + correctAnswer);
        quizFeedbackLabel.getStyleClass().removeAll("status-error", "status-success");
        quizFeedbackLabel.getStyleClass().add(isCorrect ? "status-success" : "status-error");

        setQuizOptionsDisabled(true);
        quizSubmitBtn.setDisable(true);
        quizNextBtn.setText(currentQuizIndex < quizQuestions.size() - 1 ? "Next" : "Finish");
        quizNextBtn.setVisible(true);
        quizNextBtn.setManaged(true);
    }

    @FXML
    private void onNextQuizQuestion() {
        if (currentQuizIndex < quizQuestions.size() - 1) {
            currentQuizIndex++;
            renderCurrentQuizQuestion();
        } else {
            submitQuizResults();
        }
    }

    private void renderCurrentQuizQuestion() {
        if (quizQuestions.isEmpty() || currentQuizIndex < 0 || currentQuizIndex >= quizQuestions.size()) {
            quizWordLabel.setText("No quiz questions available.");
            quizProgressLabel.setText("");
            quizFeedbackLabel.setText("");
            quizSubmitBtn.setDisable(true);
            return;
        }

        Map<String, Object> question = quizQuestions.get(currentQuizIndex);
        String word = asString(question.get("word"));
        List<String> options = asStringList(question.get("options"));

        quizProgressLabel.setText("Question " + (currentQuizIndex + 1) + " / " + quizQuestions.size());
        quizWordLabel.setText("What is the meaning of \"" + word + "\"?");
        setQuizOptions(options);
        setQuizOptionsDisabled(false);
        quizSubmitBtn.setDisable(false);
        quizFeedbackLabel.setText("");
        quizNextBtn.setVisible(false);
        quizNextBtn.setManaged(false);
    }

    private void submitQuizResults() {
        if (bridge == null) return;

        showStatus("Saving quiz results…");
        new Thread(() -> {
            Map<String, Object> req = new HashMap<>();
            req.put("action", "submit_quiz");
            req.put("session_id", activeQuizSessionId);
            req.put("answers", quizAnswers);

            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if (!"success".equals(resp.get("status"))) {
                    showError("Failed to save quiz results.");
                    return;
                }

                int total = asInt(resp.get("total"), quizAnswers.size());
                int correct = asInt(resp.get("correct"), countCorrectAnswers());
                quizSummaryLabel.setText("Score: " + correct + " / " + total);
                quizProgressLabel.setText("Completed");
                quizFeedbackLabel.setText(retryWords.isEmpty()
                        ? "Great run. All answers were correct."
                        : "You can retry the words you missed.");

                retryWrongBtn.setVisible(!retryWords.isEmpty());
                retryWrongBtn.setManaged(!retryWords.isEmpty());
                quizNextBtn.setVisible(false);
                quizNextBtn.setManaged(false);
                quizSubmitBtn.setDisable(true);

                refreshQuizHistory();
                showSuccess("Quiz completed.");
            });
        }).start();
    }

    private int countCorrectAnswers() {
        int total = 0;
        for (Map<String, Object> ans : quizAnswers) {
            if (Boolean.TRUE.equals(ans.get("is_correct"))) total++;
        }
        return total;
    }

    private void setQuizOptions(List<String> options) {
        RadioButton[] buttons = {quizOptionA, quizOptionB, quizOptionC, quizOptionD};
        for (int i = 0; i < buttons.length; i++) {
            RadioButton btn = buttons[i];
            if (i < options.size()) {
                btn.setText(options.get(i));
                btn.setUserData(i);
                btn.setVisible(true);
                btn.setManaged(true);
            } else {
                btn.setText("");
                btn.setUserData(-1);
                btn.setVisible(false);
                btn.setManaged(false);
            }
        }
        quizToggleGroup.selectToggle(null);
    }

    private void setQuizOptionsDisabled(boolean disabled) {
        quizOptionA.setDisable(disabled || !quizOptionA.isManaged());
        quizOptionB.setDisable(disabled || !quizOptionB.isManaged());
        quizOptionC.setDisable(disabled || !quizOptionC.isManaged());
        quizOptionD.setDisable(disabled || !quizOptionD.isManaged());
    }

    private void refreshQuizHistory() {
        if (bridge == null) return;

        new Thread(() -> {
            Map<String, Object> req = Map.of("action", "quiz_history", "limit", 10);
            Map<String, Object> resp = bridge.sendRequest(req);
            Platform.runLater(() -> {
                if (!"success".equals(resp.get("status"))) return;

                List<String> rows = new ArrayList<>();
                Object historyObj = resp.get("history");
                if (historyObj instanceof List<?> list) {
                    for (Object item : list) {
                        if (!(item instanceof Map<?, ?> map)) continue;
                        int total = asInt(map.get("total_questions"), 0);
                        int correct = asInt(map.get("correct_answers"), 0);
                        String created = asString(map.get("created_on"));
                        rows.add(created + "  -  " + correct + "/" + total);
                    }
                }
                quizHistoryRows.setAll(rows);
            });
        }).start();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static List<String> asStringList(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) out.add(asString(item));
        }
        return out;
    }

    private static List<Map<String, Object>> asQuestionList(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Map<String, Object> row = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    row.put(asString(e.getKey()), e.getValue());
                }
                out.add(row);
            }
        }
        return out;
    }

    // -- Zoom -----------------------------------------------------------------
    @FXML private void onZoomIn()    { if (zoomLevel < 2.0) { zoomLevel = round1(zoomLevel + 0.1); applyZoom(); } }
    @FXML private void onZoomOut()   { if (zoomLevel > 0.6) { zoomLevel = round1(zoomLevel - 0.1); applyZoom(); } }
    @FXML private void onZoomReset() { zoomLevel = 1.3; applyZoom(); }

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
        refreshStreak();
    }

    // -- Helpers --------------------------------------------------------------
    private void showCard() {
        emptyState.setVisible(false);  emptyState.setManaged(false);
        cardArea.setVisible(true);     cardArea.setManaged(true);
    }

    private void hideCard() {
        cardArea.setVisible(false);    cardArea.setManaged(false);
        emptyState.setVisible(true);   emptyState.setManaged(true);
        suggestionList.setVisible(false);
        suggestionList.setManaged(false);
        VBox.setVgrow(meaningArea, Priority.ALWAYS);
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
