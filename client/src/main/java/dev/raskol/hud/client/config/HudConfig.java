package dev.raskol.hud.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

/** Локальные настройки внешнего вида HUD. */
public final class HudConfig {
    private static final String FILE_NAME = "raskolhud.properties";
    private static final int CONFIG_VERSION = 2;
    private static boolean showIdentity = true;
    private static boolean showStatus = true;
    private static boolean showEffects = true;
    private static boolean showConnection = true;
    private static boolean showScoreboard = true;
    private static boolean showHotbarFrame = true;
    private static boolean showClassCrosshair = true;
    private static boolean showCombatStats = true;
    private static boolean showTarget = true;
    private static double scale = 1.0D;
    private static int identityX;
    private static int identityY;
    private static int statusX;
    private static int statusY;
    private static int effectsX;
    private static int effectsY;
    private static int connectionX;
    private static int connectionY;
    private static int combatStatsX;
    private static int combatStatsY;
    private static int targetX;
    private static int targetY;
    private static int identityOpacity = 100;
    private static int statusOpacity = 100;
    private static int effectsOpacity = 100;
    private static int connectionOpacity = 100;
    private static int combatStatsOpacity = 100;
    private static int targetOpacity = 100;

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
            int storedVersion = parseInt(properties.getProperty("configVersion"), 1);
            if (storedVersion > CONFIG_VERSION) {
                System.err.println("[RaskolHUD] Конфиг создан более новой версией мода; неизвестные параметры могут быть потеряны при сохранении.");
            }
            showIdentity = readBoolean(properties, "showIdentity", true);
            showStatus = readBoolean(properties, "showStatus", true);
            showEffects = readBoolean(properties, "showEffects", true);
            showConnection = readBoolean(properties, "showConnection", true);
            showScoreboard = readBoolean(properties, "showScoreboard", true);
            showHotbarFrame = readBoolean(properties, "showHotbarFrame", true);
            showClassCrosshair = readBoolean(properties, "showClassCrosshair", true);
            showCombatStats = readBoolean(properties, "showCombatStats", true);
            showTarget = readBoolean(properties, "showTarget", true);
            scale = HudConfigValues.readScale(properties.getProperty("scale"), 1.0D);
            identityX = readOffset(properties, "identityX");
            identityY = readOffset(properties, "identityY");
            statusX = readOffset(properties, "statusX");
            statusY = readOffset(properties, "statusY");
            effectsX = readOffset(properties, "effectsX");
            effectsY = readOffset(properties, "effectsY");
            connectionX = readOffset(properties, "connectionX");
            connectionY = readOffset(properties, "connectionY");
            combatStatsX = readOffset(properties, "combatStatsX");
            combatStatsY = readOffset(properties, "combatStatsY");
            targetX = readOffset(properties, "targetX");
            targetY = readOffset(properties, "targetY");
            identityOpacity = readPercent(properties, "identityOpacity");
            statusOpacity = readPercent(properties, "statusOpacity");
            effectsOpacity = readPercent(properties, "effectsOpacity");
            connectionOpacity = readPercent(properties, "connectionOpacity");
            combatStatsOpacity = readPercent(properties, "combatStatsOpacity");
            targetOpacity = readPercent(properties, "targetOpacity");
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("[RaskolHUD] Не удалось прочитать настройки; восстановлены значения по умолчанию: "
                    + exception.getMessage());
            reset();
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("configVersion", Integer.toString(CONFIG_VERSION));
        properties.setProperty("showIdentity", Boolean.toString(showIdentity));
        properties.setProperty("showStatus", Boolean.toString(showStatus));
        properties.setProperty("showEffects", Boolean.toString(showEffects));
        properties.setProperty("showConnection", Boolean.toString(showConnection));
        properties.setProperty("showScoreboard", Boolean.toString(showScoreboard));
        properties.setProperty("showHotbarFrame", Boolean.toString(showHotbarFrame));
        properties.setProperty("showClassCrosshair", Boolean.toString(showClassCrosshair));
        properties.setProperty("showCombatStats", Boolean.toString(showCombatStats));
        properties.setProperty("showTarget", Boolean.toString(showTarget));
        properties.setProperty("scale", Double.toString(scale));
        properties.setProperty("identityX", Integer.toString(identityX));
        properties.setProperty("identityY", Integer.toString(identityY));
        properties.setProperty("statusX", Integer.toString(statusX));
        properties.setProperty("statusY", Integer.toString(statusY));
        properties.setProperty("effectsX", Integer.toString(effectsX));
        properties.setProperty("effectsY", Integer.toString(effectsY));
        properties.setProperty("connectionX", Integer.toString(connectionX));
        properties.setProperty("connectionY", Integer.toString(connectionY));
        properties.setProperty("combatStatsX", Integer.toString(combatStatsX));
        properties.setProperty("combatStatsY", Integer.toString(combatStatsY));
        properties.setProperty("targetX", Integer.toString(targetX));
        properties.setProperty("targetY", Integer.toString(targetY));
        properties.setProperty("identityOpacity", Integer.toString(identityOpacity));
        properties.setProperty("statusOpacity", Integer.toString(statusOpacity));
        properties.setProperty("effectsOpacity", Integer.toString(effectsOpacity));
        properties.setProperty("connectionOpacity", Integer.toString(connectionOpacity));
        properties.setProperty("combatStatsOpacity", Integer.toString(combatStatsOpacity));
        properties.setProperty("targetOpacity", Integer.toString(targetOpacity));

        Path path = configPath();
        Path temporary = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "RaskolHUD client settings");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Повторная очистка временного файла не должна скрывать исходную ошибку.
            }
            System.err.println("[RaskolHUD] Не удалось сохранить настройки: " + exception.getMessage());
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        return HudConfigValues.readBoolean(properties.getProperty(key), fallback);
    }

    private static int parseInt(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int readOffset(Properties properties, String key) { return readOffset(properties, key, 0); }

    private static int readOffset(Properties properties, String key, int fallback) {
        return HudConfigValues.readOffset(properties.getProperty(key), fallback);
    }

    private static int readPercent(Properties properties, String key) {
        return HudConfigValues.readOpacity(properties.getProperty(key));
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
    public static boolean showCombatStats() { return showCombatStats; }
    public static boolean showTarget() { return showTarget; }
    public static double scale() { return scale; }
    public static int identityX() { return identityX; }
    public static int identityY() { return identityY; }
    public static int statusX() { return statusX; }
    public static int statusY() { return statusY; }
    public static int effectsX() { return effectsX; }
    public static int effectsY() { return effectsY; }
    public static int connectionX() { return connectionX; }
    public static int connectionY() { return connectionY; }
    public static int combatStatsX() { return combatStatsX; }
    public static int combatStatsY() { return combatStatsY; }
    public static int targetX() { return targetX; }
    public static int targetY() { return targetY; }

    public static int opacity(String panel) {
        return switch (panel) {
            case "identity" -> identityOpacity;
            case "status" -> statusOpacity;
            case "effects" -> effectsOpacity;
            case "connection" -> connectionOpacity;
            case "combatStats" -> combatStatsOpacity;
            case "target" -> targetOpacity;
            default -> 100;
        };
    }

    public static void move(String panel, int dx, int dy) {
        switch (panel) {
            case "identity" -> { identityX = offset(identityX, dx); identityY = offset(identityY, dy); }
            case "status" -> { statusX = offset(statusX, dx); statusY = offset(statusY, dy); }
            case "effects" -> { effectsX = offset(effectsX, dx); effectsY = offset(effectsY, dy); }
            case "connection" -> { connectionX = offset(connectionX, dx); connectionY = offset(connectionY, dy); }
            case "combatStats" -> { combatStatsX = offset(combatStatsX, dx); combatStatsY = offset(combatStatsY, dy); }
            case "target" -> { targetX = offset(targetX, dx); targetY = offset(targetY, dy); }
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
            case "combatStats" -> { combatStatsX = 0; combatStatsY = 0; }
            case "target" -> { targetX = 0; targetY = 0; }
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
            case "combatStats" -> combatStatsOpacity = nextOpacity(combatStatsOpacity);
            case "target" -> targetOpacity = nextOpacity(targetOpacity);
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
    public static void toggleCombatStats() { showCombatStats = !showCombatStats; save(); }
    public static void toggleTarget() { showTarget = !showTarget; save(); }

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
        showCombatStats = true;
        showTarget = true;
        scale = 1.0D;
        identityX = identityY = statusX = statusY = effectsX = effectsY = connectionX = connectionY = 0;
        combatStatsX = combatStatsY = targetX = targetY = 0;
        combatStatsY = 0;
        identityOpacity = statusOpacity = effectsOpacity = connectionOpacity = combatStatsOpacity = targetOpacity = 100;
        save();
    }
}
