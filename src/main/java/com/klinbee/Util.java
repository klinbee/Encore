package com.klinbee;

import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.regex.Pattern;

public final class Util {
    private Util() {
    }

    public static void appendHighlighted(TextFlow flow, String text, String query,
                                         String hlColor, String hlBg, boolean bold) {
        int i = 0;
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        while (i < text.length()) {
            int idx = lowerText.indexOf(lowerQuery, i);
            if (idx == -1) {
                flow.getChildren().add(styledText(text.substring(i), "#333", bold));
                break;
            }
            if (idx > i) flow.getChildren().add(styledText(text.substring(i, idx), "#333", bold));
            Text match = styledText(text.substring(idx, idx + query.length()), hlColor, true);
            match.setStyle(match.getStyle() + " -fx-background-color: " + hlBg + ";");
            flow.getChildren().add(match);
            i = idx + query.length();
        }
    }

    public static Text styledText(String content, String color, boolean bold) {
        Text t = new Text(content);
        t.setStyle("-fx-fill: " + color + "; -fx-font-size: 12px;"
                + (bold ? " -fx-font-weight: bold;" : ""));
        t.setFont(Font.font("Monospace", bold ? javafx.scene.text.FontWeight.BOLD
                : javafx.scene.text.FontWeight.NORMAL, 12));
        return t;
    }

    public static Pattern buildPattern(String query, SearchOptions opts) {
        String regex = opts.useRegex() ? query : Pattern.quote(query);
        int flags = opts.matchCase() ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile(regex, flags);
    }
}
