package com.klinbee;

public record SearchOptions(boolean matchCase,
                            boolean useRegex,
                            boolean searchFileNames,
                            boolean searchContents
) {
}