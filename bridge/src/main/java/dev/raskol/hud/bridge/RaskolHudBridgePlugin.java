package dev.raskol.hud.bridge;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.inventory.ItemStack;

import dev.raskol.hud.protocol.RaskolHudEffect;
import dev.raskol.hud.protocol.RaskolHudCooldown;
import dev.raskol.hud.protocol.RaskolHudProtocol;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import me.clip.placeholderapi.PlaceholderAPI;

public final class RaskolHudBridgePlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private final Set<UUID> hudClients = new HashSet<>();
    private Object raskolClasses;
    private boolean warnedOptionalApi;

    @Override
    public void onEnable() {
        Plugin dependency = getServer().getPluginManager().getPlugin("RaskolClasses");
        if (dependency == null || !dependency.isEnabled()) {
            throw new IllegalStateException("RaskolClasses dependency is not available");
        }
        this.raskolClasses = dependency;

        Messenger messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, RaskolHudProtocol.HELLO_CHANNEL, this);
        messenger.registerOutgoingPluginChannel(this, RaskolHudProtocol.STATE_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getScheduler().runTaskTimer(this, this::publishSnapshots, 10L, 10L);
        getLogger().info("RaskolHUD bridge enabled (protocol v" + RaskolHudProtocol.PROTOCOL_VERSION + ").");
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(this, RaskolHudProtocol.HELLO_CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(this, RaskolHudProtocol.STATE_CHANNEL);
        hudClients.clear();
        raskolClasses = null;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!RaskolHudProtocol.HELLO_CHANNEL.equals(channel)
                || player == null
                || !RaskolHudProtocol.isValidHello(message)) {
            return;
        }

        hudClients.add(player.getUniqueId());
        publishSnapshot(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hudClients.remove(event.getPlayer().getUniqueId());
    }

    private void publishSnapshots() {
        for (UUID playerId : Set.copyOf(hudClients)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                hudClients.remove(playerId);
                continue;
            }
            publishSnapshot(player);
        }
    }

    private void publishSnapshot(Player player) {
        try {
            String className = placeholder(player, "class");
            ClassPresentation presentation = ClassPresentation.fromDisplayName(className);
            String factionName = displayFaction(crownPlaceholder(player, "faction"));
            String titleName = crownPlaceholder(player, "title");

            RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                    System.currentTimeMillis(),
                    presentation.id(),
                    presentation.name(),
                    placeholder(player, "spec"),
                    presentation.resourceName(),
                    presentation.resourceSymbol(),
                    factionName,
                    titleName,
                    readInt(player, "level"),
                    readInt(player, "hp"),
                    readInt(player, "hp_max"),
                    readInt(player, "resource"),
                    resourceMax(player.getUniqueId()),
                    activeEffects(player.getUniqueId()),
                    activeDamageOverTime(player.getUniqueId()),
                    activeCrowdControl(player.getUniqueId()),
                    cooldowns(player));

            player.sendPluginMessage(this, RaskolHudProtocol.STATE_CHANNEL, RaskolHudProtocol.encode(snapshot));
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Could not publish RaskolHUD snapshot for "
                    + player.getUniqueId() + ": " + exception.getMessage());
        }
    }

    private int resourceMax(UUID playerId) {
        try {
            Object resources = invoke(raskolClasses, "getResources");
            Object state = invoke(resources, "stateOf", new Class<?>[] {UUID.class}, playerId);
            Object maximum = invoke(state, "getEffectiveMax");
            if (maximum instanceof Number number) {
                return Math.max(0, (int) Math.round(number.doubleValue()));
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read the effective class resource maximum", exception);
        }
        return 0;
    }

    private List<RaskolHudEffect> activeEffects(UUID playerId) {
        try {
            Object manager = invoke(raskolClasses, "getEffects");
            Object value = invoke(manager, "getActiveEffects", new Class<?>[] {UUID.class}, playerId);
            if (!(value instanceof Map<?, ?> active) || active.isEmpty()) {
                return List.of();
            }

            long now = System.currentTimeMillis();
            List<Map.Entry<?, ?>> ordered = new ArrayList<>(active.entrySet());
            ordered.sort(Comparator.comparing(entry -> enumName(entry.getKey())));

            List<RaskolHudEffect> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ordered) {
                if (result.size() >= RaskolHudProtocol.MAX_EFFECTS) {
                    break;
                }

                Object type = entry.getKey();
                String displayName = stringValue(invoke(type, "displayName"));
                if (displayName.isBlank()) {
                    continue;
                }

                long expiresAt = entry.getValue() instanceof Number number
                        ? number.longValue()
                        : now;
                int remaining = expiresAt == Long.MAX_VALUE
                        ? -1
                        : (int) Math.max(0L, Math.min(86_400L, (expiresAt - now + 999L) / 1000L));
                String id = enumName(type).toLowerCase(java.util.Locale.ROOT);

                result.add(new RaskolHudEffect(
                        id,
                        displayName,
                        effectSymbol(id),
                        remaining,
                        0,
                        1,
                        false));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read active RaskolClasses effects", exception);
            return List.of();
        }
    }

    private List<RaskolHudEffect> activeDamageOverTime(UUID playerId) {
        try {
            Object combat = invoke(raskolClasses, "getCombat");
            Object service = invoke(combat, "dots");
            Object value = invoke(service, "activeDotsOf", new Class<?>[] {UUID.class}, playerId);
            if (!(value instanceof List<?> active) || active.isEmpty()) {
                return List.of();
            }

            long now = System.currentTimeMillis();
            List<Object> ordered = new ArrayList<>(active);
            ordered.sort(Comparator.comparing(instance -> {
                try {
                    Object def = invoke(instance, "def");
                    return stringValue(invoke(def, "id"));
                } catch (ReflectiveOperationException exception) {
                    return "";
                }
            }));

            List<RaskolHudEffect> result = new ArrayList<>();
            for (Object instance : ordered) {
                if (result.size() >= RaskolHudProtocol.MAX_EFFECTS) {
                    break;
                }

                Object def = invoke(instance, "def");
                String id = stringValue(invoke(def, "id"));
                String school = "arcane";
                Object schoolValue = invoke(def, "school");
                if (schoolValue != null) {
                    school = stringValue(invoke(schoolValue, "id"));
                }

                long expiresAt = ((Number) invoke(instance, "expiresAt")).longValue();
                long durationMillis = ((Number) invoke(def, "durationMillis")).longValue();
                int remaining = (int) Math.max(0L, Math.min(86_400L, (expiresAt - now + 999L) / 1000L));
                int duration = (int) Math.max(1L, Math.min(86_400L, (durationMillis + 999L) / 1000L));
                int stacks = ((Number) invoke(instance, "stacks")).intValue();

                result.add(new RaskolHudEffect(
                        id,
                        dotDisplayName(id),
                        schoolSymbol(school),
                        remaining,
                        duration,
                        stacks,
                        true));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read active RaskolClasses damage-over-time states", exception);
            return List.of();
        }
    }

    private List<RaskolHudEffect> activeCrowdControl(UUID playerId) {
        try {
            Object manager = invoke(raskolClasses, "getCC");
            Object value = invoke(manager, "activeOf", new Class<?>[] {UUID.class}, playerId);
            if (!(value instanceof List<?> active) || active.isEmpty()) {
                return List.of();
            }

            long now = System.currentTimeMillis();
            List<RaskolHudEffect> result = new ArrayList<>();
            for (Object instance : active) {
                if (result.size() >= RaskolHudProtocol.MAX_EFFECTS) {
                    break;
                }
                Object type = invoke(instance, "type");
                String id = stringValue(invoke(type, "id"));
                String displayName = stringValue(invoke(type, "ruName"));
                long startedAt = ((Number) invoke(instance, "startedAt")).longValue();
                long expiresAt = ((Number) invoke(instance, "expiresAt")).longValue();
                long remainingMillis = expiresAt - now;
                if (remainingMillis <= 0L || displayName.isBlank()) {
                    continue;
                }
                int remaining = (int) Math.min(86_400L, (remainingMillis + 999L) / 1000L);
                int duration = (int) Math.min(86_400L, Math.max(1L, (expiresAt - startedAt + 999L) / 1000L));
                result.add(new RaskolHudEffect(
                        id, displayName, crowdControlSymbol(id), remaining, duration, 1, true));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read active RaskolClasses crowd-control states", exception);
            return List.of();
        }
    }

    private List<RaskolHudCooldown> cooldowns(Player player) {
        try {
            Object classProvider = invoke(raskolClasses, "getClassProvider");
            Object playerClass = invoke(classProvider, "getClassOf", new Class<?>[] {Player.class}, player);
            if (playerClass == null) {
                return List.of();
            }

            Object tokens = invoke(raskolClasses, "getTokens");
            Object cooldownManager = invoke(raskolClasses, "getCooldowns");
            Object abilities = invoke(raskolClasses, "getAbilities");
            List<RaskolHudCooldown> result = new ArrayList<>();

            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                String abilityId = stringValue(invoke(
                        tokens, "readId", new Class<?>[] {ItemStack.class}, item));
                if (abilityId.isBlank()) {
                    continue;
                }
                long remaining = ((Number) invoke(
                        cooldownManager,
                        "getRemainingMillis",
                        new Class<?>[] {UUID.class, String.class},
                        player.getUniqueId(),
                        abilityId)).longValue();
                if (remaining <= 0L) {
                    continue;
                }

                long duration = remaining;
                Object definition = invoke(
                        abilities,
                        "findById",
                        new Class<?>[] {playerClass.getClass(), String.class},
                        playerClass,
                        abilityId);
                if (definition != null) {
                    Object configuredDuration = invoke(definition, "cooldownMillis");
                    if (configuredDuration instanceof Number number && number.longValue() > 0L) {
                        duration = number.longValue();
                    }
                }
                result.add(new RaskolHudCooldown(
                        slot,
                        abilityId,
                        (int) Math.min(86_400_000L, remaining),
                        (int) Math.min(86_400_000L, duration)));
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read RaskolClasses hotbar cooldowns", exception);
            return List.of();
        }
    }

    private static String crowdControlSymbol(String id) {
        return switch (id.toUpperCase(java.util.Locale.ROOT)) {
            case "STUN" -> "✣";
            case "ROOT" -> "⌘";
            case "SILENCE" -> "◖";
            case "DISARM" -> "⚔";
            case "FEAR" -> "☠";
            case "CHARM" -> "◇";
            case "SLOW" -> "⌛";
            case "BLIND" -> "◉";
            default -> "!";
        };
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static Object invoke(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments) throws ReflectiveOperationException {

        if (target == null) {
            throw new IllegalStateException("Cannot call " + methodName + " on a missing RaskolClasses service");
        }
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, arguments);
    }

    private static String enumName(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return stringValue(invoke(value, "name"));
        } catch (ReflectiveOperationException exception) {
            return value.toString();
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private void warnOptionalApi(String message, Exception exception) {
        if (!warnedOptionalApi) {
            getLogger().warning(message + ": " + exception.getMessage());
            warnedOptionalApi = true;
        }
    }

    private static String effectSymbol(String id) {
        return switch (id) {
            case "shield_wall" -> "▣";
            case "blood_fury" -> "✦";
            case "aimed_shot" -> "➶";
            case "stealth" -> "◈";
            case "evasion" -> "◇";
            case "no_fall_damage" -> "➹";
            default -> "✧";
        };
    }

    private static String dotDisplayName(String id) {
        return switch (id) {
            case "burning", "burning_passive" -> "Горение";
            case "poison", "poison_passive" -> "Отравление";
            case "bleed", "bleed_passive" -> "Кровотечение";
            case "chilled" -> "Охлаждение";
            case "wither" -> "Иссушение";
            default -> id.replace('_', ' ');
        };
    }

    private static String schoolSymbol(String school) {
        return switch (school) {
            case "physical" -> "⚔";
            case "fire" -> "♨";
            case "frost" -> "❄";
            case "nature" -> "✧";
            case "shadow" -> "☾";
            case "holy" -> "✚";
            case "arcane" -> "✦";
            case "true" -> "◈";
            default -> "✦";
        };
    }

    private String placeholder(Player player, String key) {
        String result = PlaceholderAPI.setPlaceholders(player, "%raskolclasses_" + key + "%");
        if (result == null) {
            return "";
        }
        String trimmed = result.trim();
        return trimmed.startsWith("%raskolclasses_") ? "" : trimmed;
    }

    private String crownPlaceholder(Player player, String key) {
        String result = PlaceholderAPI.setPlaceholders(player, "%raskolcrown_" + key + "%");
        if (result == null) {
            return "";
        }
        String trimmed = result.trim();
        return trimmed.startsWith("%raskolcrown_") ? "" : trimmed;
    }

    private static String displayFaction(String faction) {
        if (faction == null || faction.isBlank()) {
            return "";
        }
        return switch (faction.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "rassvet" -> "Рассвет";
            case "valradis" -> "Вальрадис";
            default -> {
                String normalized = faction.trim().replace('_', ' ').replace('-', ' ');
                yield normalized.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                        + normalized.substring(1);
            }
        };
    }

    private int readInt(Player player, String key) {
        String value = placeholder(player, key);
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.replace(" ", "")));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
