package dev.raskol.hud.client.render;

/** Сдержанная цветовая тема класса для всех клиентских элементов RaskolHUD. */
public record RaskolClassTheme(int accent, int highlight, int muted) {

    public static RaskolClassTheme forClass(String classId) {
        if (classId == null) {
            return neutral();
        }
        return switch (classId.toLowerCase(java.util.Locale.ROOT)) {
            case "warrior" -> new RaskolClassTheme(0xFF9D4854, 0xFFD47A79, 0xFF6E303A);
            case "hunter" -> new RaskolClassTheme(0xFF718B5C, 0xFFA7B98A, 0xFF40573D);
            case "priest" -> new RaskolClassTheme(0xFFB69A5B, 0xFFE3CA8D, 0xFF79633E);
            case "mage" -> new RaskolClassTheme(0xFF6F9FC8, 0xFF9BC9E8, 0xFF3E607D);
            case "rogue" -> new RaskolClassTheme(0xFF927BB4, 0xFFC0A8DE, 0xFF5E4D7B);
            case "warlock" -> new RaskolClassTheme(0xFF9B6DA8, 0xFFC79BD2, 0xFF67466F);
            default -> neutral();
        };
    }

    private static RaskolClassTheme neutral() {
        return new RaskolClassTheme(0xFF9C8061, 0xFFD2B58C, 0xFF5E4B3A);
    }
}
