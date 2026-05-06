package com.klinbee;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.klinbee.Util.*;

public class MainController {

    private final SearchEngine engine = new SearchEngine();
    private final NbtSearchEngine nbtEngine = new NbtSearchEngine();
    private Path rootPath;

    private TreeView<Path> fileTree;
    private TextField findField;
    private TextField replaceField;
    private CheckBox matchCaseBox;
    private CheckBox regexBox;
    private CheckBox fileNamesBox;
    private CheckBox contentsBox;
    private VBox resultsBox;
    private Label statusLabel;

    public static TreeItem<Path> buildTree(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        item.setExpanded(true);
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path).sorted(
                    Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                            .thenComparing(p -> p.getFileName().toString()))) {
                stream.forEach(child -> item.getChildren().add(buildTree(child)));
            } catch (IOException ignored) {
            }
        }
        return item;
    }

    public BorderPane build(Stage stage) {
        BorderPane root = new BorderPane();
        root.setLeft(buildSidebar(stage));
        root.setCenter(buildMainPanel());
        return root;
    }

    private VBox buildSidebar(Stage stage) {
        Button openBtn = new Button("Open Folder…");
        openBtn.setOnAction(e -> openFolder(stage));

        fileTree = new TreeView<>();
        fileTree.setShowRoot(true);
        fileTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName().toString());
            }
        });
        VBox.setVgrow(fileTree, Priority.ALWAYS);

        VBox sidebar = new VBox(8, openBtn, fileTree);
        sidebar.setPadding(new Insets(10));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 0 1 0 0;");
        return sidebar;
    }

    private void openFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open Folder");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            rootPath = dir.toPath();
            fileTree.setRoot(buildTree(rootPath));
            stage.setTitle("Encore — " + rootPath.getFileName());
        }
    }

    private VBox buildMainPanel() {
        findField = new TextField();
        findField.setPromptText("Search…");
        replaceField = new TextField();
        replaceField.setPromptText("Replace with…");
        matchCaseBox = new CheckBox("Match case");
        regexBox = new CheckBox("Regex");
        fileNamesBox = new CheckBox("File names");
        fileNamesBox.setSelected(true);
        contentsBox = new CheckBox("File contents");
        contentsBox.setSelected(true);

        Button searchBtn = new Button("Search");
        Button replaceBtn = new Button("Replace All");
        searchBtn.setOnAction(e -> runSearch());
        replaceBtn.setOnAction(e -> runReplaceAll());

        HBox optionsRow = new HBox(12, matchCaseBox, regexBox, fileNamesBox, contentsBox);
        optionsRow.setAlignment(Pos.CENTER_LEFT);

        GridPane searchGrid = new GridPane();
        searchGrid.setHgap(8);
        searchGrid.setVgap(8);
        searchGrid.setPadding(new Insets(12));
        ColumnConstraints labelCol = new ColumnConstraints(70);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        searchGrid.getColumnConstraints().addAll(labelCol, fieldCol);

        searchGrid.add(new Label("Find:"), 0, 0);
        searchGrid.add(findField, 1, 0);
        searchGrid.add(new Label("Replace:"), 0, 1);
        searchGrid.add(replaceField, 1, 1);
        searchGrid.add(optionsRow, 1, 2);
        HBox buttons = new HBox(8, searchBtn, replaceBtn);
        searchGrid.add(buttons, 1, 3);

        resultsBox = new VBox();
        ScrollPane scroll = new ScrollPane(resultsBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        statusLabel = new Label("Open a folder to get started.");
        statusLabel.setPadding(new Insets(4, 10, 4, 10));
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        VBox main = new VBox(searchGrid, new Separator(), scroll, statusLabel);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return main;
    }

    private SearchOptions getOptions() {
        return new SearchOptions(
                matchCaseBox.isSelected(), regexBox.isSelected(),
                fileNamesBox.isSelected(), contentsBox.isSelected());
    }

    private void runSearch() {
        if (rootPath == null) {
            status("No folder open.");
            return;
        }
        String query = findField.getText().trim();
        if (query.isEmpty()) {
            status("Enter a search term.");
            return;
        }

        resultsBox.getChildren().clear();
        try {
            SearchOptions opts = getOptions();
            List<SearchResult> results = new ArrayList<>(engine.search(rootPath, query, opts));

            try (var nbtFiles = Files.walk(rootPath)) {
                Pattern pattern = buildPattern(query, opts);
                nbtFiles.filter(Files::isRegularFile)
                        .filter(f -> f.toString().endsWith(".nbt"))
                        .forEach(f -> {
                            if (opts.searchFileNames()) {
                                String name = f.getFileName().toString();
                                if (pattern.matcher(name).find()) {
                                    results.add(new SearchResult(
                                            f, SearchResult.MatchType.FILE_NAME, -1, null));
                                }
                            }
                            if (opts.searchContents()) {
                                try {
                                    results.addAll(nbtEngine.search(f, query, opts));
                                } catch (IOException ex) {
                                }
                            }
                        });
            }

            renderResults(results, query);
            status(results.size() + " result(s) found.");
        } catch (IOException ex) {
            status("Error: " + ex.getMessage());
        }
    }

    private void runReplaceAll() {
        if (rootPath == null) {
            status("No folder open.");
            return;
        }
        String find = findField.getText().trim();
        String repl = replaceField.getText();
        if (find.isEmpty()) {
            status("Enter a search term.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Replace all occurrences of \"" + find + "\" with \"" + repl + "\"?\n\nThis will modify files on disk.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                SearchOptions opts = getOptions();
                int[] count = {engine.replaceAll(rootPath, find, repl, opts)};

                try (var nbtFiles = Files.walk(rootPath)) {
                    Pattern pattern = buildPattern(find, opts);
                    nbtFiles.filter(Files::isRegularFile)
                            .filter(f -> f.toString().endsWith(".nbt"))
                            .forEach(f -> {
                                // Content first — path is still valid here
                                if (opts.searchContents()) {
                                    try {
                                        count[0] += nbtEngine.replaceAll(f, find, repl, opts);
                                    } catch (IOException ex) {
                                    }
                                }
                                // Rename after — mirrors SearchEngine.replaceAll ordering
                                if (opts.searchFileNames()) {
                                    String name = f.getFileName().toString();
                                    String newName = pattern.matcher(name).replaceAll(
                                            opts.useRegex() ? repl : Matcher.quoteReplacement(repl));
                                    if (!newName.equals(name)) {
                                        try {
                                            Files.move(f, f.resolveSibling(newName),
                                                    StandardCopyOption.REPLACE_EXISTING);
                                            count[0]++;
                                        } catch (IOException ex) {
                                        }
                                    }
                                }
                            });
                }

                fileTree.setRoot(buildTree(rootPath));
                resultsBox.getChildren().clear();
                status("Replaced " + count[0] + " occurrence(s). Run search again to verify.");
            } catch (IOException ex) {
                status("Error: " + ex.getMessage());
            }
        });
    }

    private void renderResults(List<SearchResult> results, String query) {
        Map<Path, List<SearchResult>> byFile = results.stream()
                .collect(Collectors.groupingBy(r -> r.file(), LinkedHashMap::new, Collectors.toList()));

        for (var entry : byFile.entrySet()) {
            Path file = entry.getKey();
            List<SearchResult> group = entry.getValue();

            boolean hasNameMatch = group.stream()
                    .anyMatch(r -> r.type() == SearchResult.MatchType.FILE_NAME);

            TextFlow header = new TextFlow();
            header.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 4 12 4 12;");
            String relPath = rootPath.relativize(file).toString();
            if (hasNameMatch) {
                appendHighlighted(header, relPath, query, "#c0392b", "#f9ebea", true);
                Text badge = new Text("  [filename match]");
                badge.setStyle("-fx-fill: #c0392b; -fx-font-size: 10px;");
                header.getChildren().add(badge);
            } else {
                header.getChildren().add(styledText(relPath, "#555", false));
            }

            VBox groupBox = new VBox(header);

            for (SearchResult r : group) {
                if (r.type() == SearchResult.MatchType.FILE_CONTENT) {
                    HBox row = new HBox(8);
                    row.setPadding(new Insets(2, 12, 2, 28));
                    row.setStyle("-fx-cursor: hand;");
                    row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #f9f9f9; -fx-cursor: hand;"));
                    row.setOnMouseExited(e -> row.setStyle("-fx-cursor: hand;"));

                    String lineLabel = r.lineText() != null && r.lineText().startsWith("palette[")
                            ? "p" + r.lineNumber()
                            : String.valueOf(r.lineNumber());
                    Label lineNum = new Label(lineLabel);

                    lineNum.setStyle("-fx-text-fill: #bbb; -fx-font-size: 11px; -fx-min-width: 32px; -fx-alignment: center-right;");

                    TextFlow lineFlow = new TextFlow();
                    HBox.setHgrow(lineFlow, Priority.ALWAYS);
                    appendHighlighted(lineFlow, r.lineText().stripLeading(), query, "#7d5a00", "#fef9e7", false);

                    row.getChildren().addAll(lineNum, lineFlow);
                    groupBox.getChildren().add(row);
                }
            }

            resultsBox.getChildren().add(groupBox);
        }
    }

    private void status(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
    }


}