package dev.raskol.hud.bridge;

import java.io.IOException;
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

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.dot.DotService;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.effect.EffectType;
import dev.raskol.hud.protocol.RaskolHudEffect;
import dev.raskol.hud.protocol.RaskolHudProtocol;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import me.clip.placeholderapi.PlaceholderAPI;

public final class RaskolHudBridgePlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private final Set<UUID> hudClients = new HashSet<>();
    private RaskolClasses raskolClasses;

    @Override
    public void onEnable() {
        Plugin dependency = getServer().getPluginManager().getPlugin("RaskolClasses");
        if (!(dependency instanceof RaskolClasses classes)) {
            throw new IllegalStateException("RaskolClasses dependency is not available");
        }
        this.raskolClasses = classes;

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
            PlayerClass playerClass = raskolClasses.getClassProvider().getClassOf(player);

            int resourceMax = (int) Math.round(
                    raskolClasses.getResources().stateOf(player.getUniqueId()).getEffectiveMax());

            RaskolHudSnapshot snapshot = new RaskolHudSnapshot(
                    System.currentTimeMillis(),
                    presentation.id(),
                    presentation.name(),
                    placeholder(player, "spec"),
                    presentation.resourceName(),
                    presentation.resourceSymbol(),
                    readInt(player, "level"),
                    readInt(player, "hp"),
                    readInt(player, "hp_max"),
                    readInt(player, "resource"),
                    resourceMax,
                    activeEffects(player, playerClass),
                    activeDamageOverTime(player));

            player.sendPluginMessage(this, RaskolHudProtocol.STATE_CHANNEL, RaskolHudProtocol.encode(snapshot));
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Could not publish RaskolHUD snapshot for "
                    + player.getUniqueId() + ": " + exception.getMessage());
        }
    }

    private List<RaskolHudEffect> activeEffects(Player player, PlayerClass playerClass) {
        Map<EffectType, Long> active = raskolClasses.getEffects().getActiveEffects(player.getUniqueId());
        if (active.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        List<RaskolHudEffect> result = new ArrayList<>();
        active.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(RaskolHudProtocol.MAX_EFFECTS)
                .forEach(entry -> {
                    EffectType type = entry.getKey();
                    String displayName = type.displayName();
                    if (displayName == null) {
                        return;
                    }

                    long expiresAt = entry.getValue();
                    int remaining = expiresAt == Long.MAX_VALUE
                            ? -1
                            : (int) Math.max(0L, (expiresAt - now + 999L) / 1000L);
                    int duration = effectDurationSeconds(type);
                    result.add(new RaskolHudEffect(
                            type.name().toLowerCase(java.util.Locale.ROOT),
                            displayName,
                            effectSymbol(type),
                            remaining,
                            duration,
                            1,
                            false));
                });
        return List.copyOf(result);
    }

    private int effectDurationSeconds(EffectType type) {
        var config = raskolClasses.getRaskolConfig();
        return switch (type) {
            case SHIELD_WALL -> config.durationSeconds(PlayerClass.WARRIOR, "steel_skin", 5);
            case BLOOD_FURY -> config.durationSeconds(PlayerClass.WARRIOR, "blood_fury", 4);
            case EVASION -> config.durationSeconds(PlayerClass.ROGUE, "evasion", 4);
            case STEALTH -> config.durationSeconds(PlayerClass.ROGUE, "stealth", 15);
            case NO_FALL_DAMAGE -> config.durationSeconds(PlayerClass.HUNTER, "cheetah_aspect", "no-fall", 10);
            case AIMED_SHOT, EXECUTE -> 0;
        };
    }

    private static String effectSymbol(EffectType type) {
        return switch (type) {
            case SHIELD_WALL -> "▣";
            case BLOOD_FURY -> "✦";
            case AIMED_SHOT -> "➶";
            case STEALTH -> "◈";
            case EVASION -> "◇";
            case NO_FALL_DAMAGE -> "➹";
            case EXECUTE -> "✧";
        };
    }

    private List<RaskolHudEffect> activeDamageOverTime(Player player) {
        List<DotInstance> active = raskolClasses.getCombat().dots().activeDotsOf(player.getUniqueId());
        if (active.isEmpty()) {
            return List.of();
        }

        long now = System.currentTimeMillis();
        List<DotInstance> ordered = new ArrayList<>(active);
        ordered.sort(Comparator.comparing(instance -> instance.def().id()));

        List<RaskolHudEffect> result = new ArrayList<>();
        for (DotInstance instance : ordered) {
            if (result.size() >= RaskolHudProtocol.MAX_EFFECTS) {
                break;
            }

            String id = instance.def().id();
            int remaining = (int) Math.max(0L, DotService.remainingSeconds(instance, now));
            int duration = (int) Math.max(1L, (instance.def().durationMillis() + 999L) / 1000L);
            School school = instance.def().school();

            result.add(new RaskolHudEffect(
                    id,
                    dotDisplayName(id),
                    schoolSymbol(school),
                    remaining,
                    duration,
                    instance.stacks(),
                    true));
        }
        return List.copyOf(result);
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

    private static String schoolSymbol(School school) {
        return switch (school) {
            case PHYSICAL -> "⚔";
            case FIRE -> "♨";
            case FROST -> "❄";
            case NATURE -> "✧";
            case SHADOW -> "☾";
            case HOLY -> "✚";
            case ARCANE -> "✦";
            case TRUE -> "◈";
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
