package com.klinbee;

import java.nio.file.Path;

/**
 * @param lineNumber -1 for filename matches
 * @param lineText   null for filename matches
 */
public record SearchResult(Path file,
                           MatchType type,
                           int lineNumber,
                           String lineText
) {
    public enum MatchType {FILE_NAME, FILE_CONTENT}
}