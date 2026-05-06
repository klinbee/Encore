package com.klinbee;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.klinbee.Util.buildPattern;

public class SearchEngine {

    public List<SearchResult> search(Path root, String query, SearchOptions opts) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        if (query.isBlank()) return results;

        Pattern pattern = buildPattern(query, opts);

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> !f.toString().endsWith(".nbt"))
                    .forEach(file -> {
                        if (opts.searchFileNames()) {
                            String name = file.getFileName().toString();
                            if (pattern.matcher(name).find()) {
                                results.add(new SearchResult(file, SearchResult.MatchType.FILE_NAME, -1, null));
                            }
                        }
                        if (opts.searchContents()) {
                            try {
                                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                                for (int i = 0; i < lines.size(); i++) {
                                    if (pattern.matcher(lines.get(i)).find()) {
                                        results.add(new SearchResult(file, SearchResult.MatchType.FILE_CONTENT,
                                                i + 1, lines.get(i)));
                                    }
                                }
                            } catch (IOException ignored) {
                            }
                        }
                    });
        }
        return results;
    }

    public int replaceAll(Path root, String find, String replace, SearchOptions opts) throws IOException {
        Pattern pattern = buildPattern(find, opts);
        int[] count = {0};

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> !f.toString().endsWith(".nbt"))
                    .forEach(file -> {
                        try {
                            if (opts.searchContents()) {
                                String content = Files.readString(file, StandardCharsets.UTF_8);
                                String newContent = pattern.matcher(content).replaceAll(
                                        opts.useRegex() ? replace : Matcher.quoteReplacement(replace));
                                if (!newContent.equals(content)) {
                                    Files.writeString(file, newContent, StandardCharsets.UTF_8);
                                    count[0]++;
                                }
                            }
                            if (opts.searchFileNames()) {
                                String name = file.getFileName().toString();
                                String newName = pattern.matcher(name).replaceAll(
                                        opts.useRegex() ? replace : Matcher.quoteReplacement(replace));
                                if (!newName.equals(name)) {
                                    Path newPath = file.getParent().resolve(newName);
                                    Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
                                    count[0]++;
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        return count[0];
    }
}