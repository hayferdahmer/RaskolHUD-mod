package dev.raskol.hud.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

/** Локальные настройки внешнего вида HUD. */
public final class HudConfig {
    private static final String FILE_NAME = "raskolhud.properties";
    private static boolean showIdentity = true;
    private static boolean showStatus = true;
    private static boolean showEffects = true;
    private static boolean showConnection = true;
    private static boolean showScoreboard = true;
    private static boolean showHotbarFrame = true;
    private static boolean showClassCrosshair = true;
    private static double scale = 1.0D;
    private static int identityX;
    private static int identityY;
    private static int statusX;
    private static int statusY;
    private static int effectsX;
    private static int effectsY;
    private static int connectionX;
    private static int connectionY;
    private static int identityOpacity = 100;
    private static int statusOpacity = 100;
    private static int effectsOpacity = 100;
    private static int connectionOpacity = 100;

    private HudConfig() {}

    public static void load() {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            showIdentity = readBoolean(properties, "showIdentity", true);
            showStatus = readBoolean(properties, "showStatus", true);
            showEffects = readBoolean(properties, "showEffects", true);
            showConnection = readBoolean(properties, "showConnection", true);
            showScoreboard = readBoolean(properties, "showScoreboard", true);
            showHotbarFrame = readBoolean(properties, "showHotbarFrame", true);
            showClassCrosshair = readBoolean(properties, "showClassCrosshair", true);
            scale = clamp(parseDouble(properties.getProperty("scale"), 1.0D), 0.75D, 1.50D);
            identityX = readOffset(properties, "identityX");
            identityY = readOffset(properties, "identityY");
            statusX = readOffset(properties, "statusX");
            statusY = readOffset(properties, "statusY");
            effectsX = readOffset(properties, "effectsX");
            effectsY = readOffset(properties, "effectsY");
            connectionX = readOffset(properties, "connectionX");
            connectionY = readOffset(properties, "connectionY");
            identityOpacity = readPercent(properties, "identityOpacity");
            statusOpacity = readPercent(properties, "statusOpacity");
            effectsOpacity = readPercent(properties, "effectsOpacity");
            connectionOpacity = readPercent(properties, "connectionOpacity");
        } catch (IOException exception) {
            System.err.println("[RaskolHUD] Не удалось прочитать настройки: " + exception.getMessage());
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("showIdentity", Boolean.toString(showIdentity));
        properties.setProperty("showStatus", Boolean.toString(showStatus));
        properties.setProperty("showEffects", Boolean.toString(showEffects));
        properties.setProperty("showConnection", Boolean.toString(showConnection));
        properties.setProperty("showScoreboard", Boolean.toString(showScoreboard));
        properties.setProperty("showHotbarFrame", Boolean.toString(showHotbarFrame));
        properties.setProperty("showClassCrosshair", Boolean.toString(showClassCrosshair));
        properties.setProperty("scale", Double.toString(scale));
        properties.setProperty("identityX", Integer.toString(identityX));
        properties.setProperty("identityY", Integer.toString(identityY));
        properties.setProperty("statusX", Integer.toString(statusX));
        properties.setProperty("statusY", Integer.toString(statusY));
        properties.setProperty("effectsX", Integer.toString(effectsX));
        properties.setProperty("effectsY", Integer.toString(effectsY));
        properties.setProperty("connectionX", Integer.toString(connectionX));
        properties.setProperty("connectionY", Integer.toString(connectionY));
        properties.setProperty("identityOpacity", Integer.toString(identityOpacity));
        properties.setProperty("statusOpacity", Integer.toString(statusOpacity));
        properties.setProperty("effectsOpacity", Integer.toString(effectsOpacity));
        properties.setProperty("connectionOpacity", Integer.toString(connectionOpacity));

        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "RaskolHUD client settings");
            }
        } catch (IOException exception) {
            System.err.println("[RaskolHUD] Не удалось сохранить настройки: " + exception.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int readOffset(Properties properties, String key) {
        try {
            return Math.max(-500, Math.min(500, Integer.parseInt(properties.getProperty(key, "0"))));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int readPercent(Properties properties, String key) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, "100"));
            return Math.max(20, Math.min(100, value - value % 20));
        } catch (NumberFormatException ignored) {
            return 100;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int offset(int value, int delta) {
        return Math.max(-500, Math.min(500, value + delta));
    }

    public static boolean showIdentity() { return showIdentity; }
    public static boolean showStatus() { return showStatus; }
    public static boolean showEffects() { return showEffects; }
    public static boolean showConnection() { return showConnection; }
    public static boolean showScoreboard() { return showScoreboard; }
    public static boolean showHotbarFrame() { return showHotbarFrame; }
    public static boolean showClassCrosshair() { return showClassCrosshair; }
    public static double scale() { return scale; }
    public static int identityX() { return identityX; }
    public static int identityY() { return identityY; }
    public static int statusX() { return statusX; }
    public static int statusY() { return statusY; }
    public static int effectsX() { return effectsX; }
    public static int effectsY() { return effectsY; }
    public static int connectionX() { return connectionX; }
    public static int connectionY() { return connectionY; }

    public static int opacity(String panel) {
        return switch (panel) {
            case "identity" -> identityOpacity;
            case "status" -> statusOpacity;
            case "effects" -> effectsOpacity;
            case "connection" -> connectionOpacity;
            default -> 100;
        };
    }

    public static void move(String panel, int dx, int dy) {
        switch (panel) {
            case "identity" -> { identityX = offset(identityX, dx); identityY = offset(identityY, dy); }
            case "status" -> { statusX = offset(statusX, dx); statusY = offset(statusY, dy); }
            case "effects" -> { effectsX = offset(effectsX, dx); effectsY = offset(effectsY, dy); }
            case "connection" -> { connectionX = offset(connectionX, dx); connectionY = offset(connectionY, dy); }
            default -> { return; }
        }
        save();
    }

    public static void resetPosition(String panel) {
        switch (panel) {
            case "identity" -> { identityX = 0; identityY = 0; }
            case "status" -> { statusX = 0; statusY = 0; }
            case "effects" -> { effectsX = 0; effectsY = 0; }
            case "connection" -> { connectionX = 0; connectionY = 0; }
            default -> { return; }
        }
        save();
    }

    public static void cycleOpacity(String panel) {
        switch (panel) {
            case "identity" -> identityOpacity = nextOpacity(identityOpacity);
            case "status" -> statusOpacity = nextOpacity(statusOpacity);
            case "effects" -> effectsOpacity = nextOpacity(effectsOpacity);
            case "connection" -> connectionOpacity = nextOpacity(connectionOpacity);
            default -> { return; }
        }
        save();
    }

    private static int nextOpacity(int current) {
        return current <= 20 ? 100 : current - 20;
    }

    public static void toggleIdentity() { showIdentity = !showIdentity; save(); }
    public static void toggleStatus() { showStatus = !showStatus; save(); }
    public static void toggleEffects() { showEffects = !showEffects; save(); }
    public static void toggleConnection() { showConnection = !showConnection; save(); }
    public static void toggleScoreboard() { showScoreboard = !showScoreboard; save(); }
    public static void toggleHotbarFrame() { showHotbarFrame = !showHotbarFrame; save(); }
    public static void toggleClassCrosshair() { showClassCrosshair = !showClassCrosshair; save(); }

    public static void cycleScale() {
        scale = scale >= 1.49D ? 0.75D : Math.min(1.50D, scale + 0.25D);
        save();
    }

    public static void reset() {
        showIdentity = true;
        showStatus = true;
        showEffects = true;
        showConnection = true;
        showScoreboard = true;
        showHotbarFrame = true;
        showClassCrosshair = true;
        scale = 1.0D;
        identityX = identityY = statusX = statusY = effectsX = effectsY = connectionX = connectionY = 0;
        identityOpacity = statusOpacity = effectsOpacity = connectionOpacity = 100;
        save();
    }
}
