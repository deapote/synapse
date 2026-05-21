package com.synapse.kb.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class ChunkTokenizer {

    List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (isCjk(c)) {
                flushLatin(latin, tokens);
                cjk.append(c);
                continue;
            }
            flushCjk(cjk, tokens);
            if (Character.isLetterOrDigit(c)) {
                latin.append(c);
            } else {
                flushLatin(latin, tokens);
            }
        }

        flushLatin(latin, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private void flushLatin(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() >= 2) {
            tokens.add(buffer.toString().toLowerCase(Locale.ROOT));
        }
        buffer.setLength(0);
    }

    private void flushCjk(StringBuilder buffer, List<String> tokens) {
        if (buffer.length() == 0) {
            return;
        }
        for (int i = 0; i < buffer.length(); i++) {
            tokens.add(String.valueOf(buffer.charAt(i)));
        }
        for (int i = 0; i < buffer.length() - 1; i++) {
            tokens.add(buffer.substring(i, i + 2));
        }
        buffer.setLength(0);
    }

    private boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
