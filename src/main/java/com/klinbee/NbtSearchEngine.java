package com.klinbee;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.klinbee.Util.buildPattern;

public class NbtSearchEngine {

    private static final byte[] PALETTE_KEY = new byte[]{0x00, 0x07, 'p', 'a', 'l', 'e', 't', 't', 'e'};
    private static final byte[] NAME_KEY = new byte[]{0x00, 0x04, 'N', 'a', 'm', 'e'};

    private byte[] decompress(Path file) throws IOException {
        try (var in = new GZIPInputStream(new FileInputStream(file.toFile()))) {
            return in.readAllBytes();
        }
    }

    private void compress(byte[] data, Path file) throws IOException {
        try (var out = new GZIPOutputStream(new FileOutputStream(file.toFile())) {
            {
                def.setLevel(Deflater.BEST_COMPRESSION);
            }
        }) {
            out.write(data);
        }
    }

    private int indexOf(byte[] data, byte[] needle, int from) {
        outer:
        for (int i = from; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++)
                if (data[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }

    private List<int[]> findNameOffsets(byte[] data) {
        List<int[]> offsets = new ArrayList<>();

        int palettePos = indexOf(data, PALETTE_KEY, 0);
        if (palettePos == -1) return offsets;

        int searchFrom = palettePos + PALETTE_KEY.length;
        while (true) {
            int namePos = indexOf(data, NAME_KEY, searchFrom);
            if (namePos == -1) break;

            int valueStart = namePos + NAME_KEY.length;
            if (valueStart + 2 > data.length) break;
            int len = ((data[valueStart] & 0xff) << 8) | (data[valueStart + 1] & 0xff);
            if (valueStart + 2 + len > data.length) break;

            offsets.add(new int[]{valueStart, len});
            searchFrom = valueStart + 2 + len;
        }
        return offsets;
    }

    public List<SearchResult> search(Path file, String query, SearchOptions opts) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        byte[] data = decompress(file);
        Pattern pattern = buildPattern(query, opts);

        List<int[]> nameOffsets = findNameOffsets(data);
        for (int i = 0; i < nameOffsets.size(); i++) {
            int[] slot = nameOffsets.get(i);
            String value = new String(data, slot[0] + 2, slot[1], StandardCharsets.UTF_8);
            if (pattern.matcher(value).find()) {
                results.add(new SearchResult(
                        file,
                        SearchResult.MatchType.FILE_CONTENT,
                        i,
                        "palette[" + i + "].Name = " + value
                ));
            }
        }
        return results;
    }

    public int replaceAll(Path file, String find, String replace, SearchOptions opts) throws IOException {
        byte[] data = decompress(file);
        Pattern pattern = buildPattern(find, opts);

        List<int[]> nameOffsets = findNameOffsets(data);
        if (nameOffsets.isEmpty()) return 0;

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        int count = 0;
        int cursor = 0;

        for (int[] slot : nameOffsets) {
            int lenOffset = slot[0];
            int len = slot[1];
            String value = new String(data, lenOffset + 2, len, StandardCharsets.UTF_8);
            Matcher m = pattern.matcher(value);
            if (!m.find()) continue;

            String replaced = opts.useRegex()
                    ? m.replaceAll(replace)
                    : m.replaceAll(Matcher.quoteReplacement(replace));
            byte[] replacedBytes = replaced.getBytes(StandardCharsets.UTF_8);
            if (replacedBytes.length > 32767) throw new IOException("Replacement string too long for NBT");

            out.write(data, cursor, lenOffset - cursor);
            out.write((replacedBytes.length >> 8) & 0xff);
            out.write(replacedBytes.length & 0xff);
            out.write(replacedBytes);
            cursor = lenOffset + 2 + len;
            count++;
        }

        if (count == 0) return 0;
        out.write(data, cursor, data.length - cursor);
        compress(out.toByteArray(), file);
        return count;
    }
}