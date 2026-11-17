package dev.raskol.hud.protocol;

import java.nio.charset.StandardCharsets;

/** Ограничивает строки протокола по реальному размеру UTF-8, не разрезая символы. */
final class RaskolHudText {
    private static final int MAX_UTF8_BYTES = 128;

    private RaskolHudText() {}

    static String limit(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int length = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + length > MAX_UTF8_BYTES) {
                break;
            }
            result.append(character);
            bytes += length;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }
}
