package dev.raskol.hud.bridge;

import java.util.Locale;
import java.util.Map;

record ClassPresentation(String id, String name, String resourceName, String resourceSymbol) {

    private static final Map<String, ClassPresentation> BY_NAME = Map.of(
            "воин", new ClassPresentation("warrior", "Воин", "Ярость", "⚔"),
            "охотник", new ClassPresentation("hunter", "Охотник", "Концентрация", "➳"),
            "жрец", new ClassPresentation("priest", "Жрец", "Свет", "✚"),
            "маг", new ClassPresentation("mage", "Маг", "Мана", "✦"),
            "разбойник", new ClassPresentation("rogue", "Разбойник", "Энергия", "☠"),
            "чернокнижник", new ClassPresentation("warlock", "Чернокнижник", "Скверна", "☾"));

    static ClassPresentation fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new ClassPresentation("", "", "", "");
        }
        return BY_NAME.getOrDefault(
                displayName.trim().toLowerCase(Locale.ROOT),
                new ClassPresentation("", displayName.trim(), "", ""));
    }
}
