package dev.raskol.hud.bridge;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;

import dev.raskol.hud.protocol.RaskolHudEffect;
import dev.raskol.hud.protocol.RaskolHudCooldown;
import dev.raskol.hud.protocol.RaskolHudProtocol.CrosshairSignal;
import dev.raskol.hud.protocol.RaskolHudProtocol;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import me.clip.placeholderapi.PlaceholderAPI;

public final class RaskolHudBridgePlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private final Set<UUID> hudClients = new HashSet<>();
    private final Map<UUID, Set<String>> previousPlayerEffects = new HashMap<>();
    private final Map<UUID, Boolean> previousStealthStates = new HashMap<>();
    private final Map<UUID, Set<String>> previousPriestWards = new HashMap<>();
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
        messenger.registerOutgoingPluginChannel(this, RaskolHudProtocol.SIGNAL_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        registerCustomHealSignal();

        getServer().getScheduler().runTaskTimer(this, this::publishSnapshots, 10L, 10L);
        getLogger().info("RaskolHUD bridge enabled (protocol v" + RaskolHudProtocol.PROTOCOL_VERSION + ").");
    }

    @Override
    public void onDisable() {
        Messenger messenger = getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(this, RaskolHudProtocol.HELLO_CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(this, RaskolHudProtocol.STATE_CHANNEL);
        messenger.unregisterOutgoingPluginChannel(this, RaskolHudProtocol.SIGNAL_CHANNEL);
        hudClients.clear();
        previousPlayerEffects.clear();
        previousStealthStates.clear();
        previousPriestWards.clear();
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
        UUID playerId = event.getPlayer().getUniqueId();
        hudClients.remove(playerId);
        previousPlayerEffects.remove(playerId);
        previousStealthStates.remove(playerId);
        previousPriestWards.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatHit(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || !hudClients.contains(attacker.getUniqueId())
                || !(event.getEntity() instanceof LivingEntity target)
                || target.isDead() || event.getFinalDamage() <= 0.0) {
            return;
        }

        String classId = classIdOf(attacker);
        UUID ownerId = attacker.getUniqueId();
        switch (classId) {
            case "warlock" -> signalAfterTargetState(
                    attacker, target,
                    () -> hasOwnedDot(target, ownerId, null)
                            ? CrosshairSignal.WARLOCK_CURSE : CrosshairSignal.NONE);
            case "hunter" -> signalAfterTargetState(
                    attacker, target,
                    () -> target.isGlowing()
                                    || target.hasPotionEffect(PotionEffectType.SLOWNESS)
                                    || hasOwnedCrowdControl(target, ownerId)
                            ? CrosshairSignal.HUNTER_MARK : CrosshairSignal.NONE);
            case "warrior" -> signalAfterTargetState(
                    attacker, target,
                    () -> hasOwnedDot(target, ownerId, Set.of("bleed", "bleed_passive"))
                            ? CrosshairSignal.WARRIOR_BLEED : CrosshairSignal.WARRIOR_HIT);
            case "mage" -> signalAfterTargetState(
                    attacker, target,
                    () -> hasOwnedDot(target, ownerId, Set.of("chilled"))
                            ? CrosshairSignal.MAGE_FROST : CrosshairSignal.NONE);
            case "rogue" -> {
                if (isBehind(target, attacker)) {
                    sendCrosshairSignal(attacker, CrosshairSignal.ROGUE_BACKSTAB);
                } else if (attacker.isSneaking()
                        || attacker.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    sendCrosshairSignal(attacker, CrosshairSignal.ROGUE_STEALTH);
                }
            }
            default -> {
                // Для неизвестного класса боевые импульсы не отправляются.
            }
        }
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
            List<RaskolHudEffect> playerEffects = activeEffects(player.getUniqueId());
            int health = readInt(player, "hp");
            observeClassSignals(player, presentation.id(), playerEffects, health);

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
                    health,
                    readInt(player, "hp_max"),
                    readInt(player, "resource"),
                    resourceMax(player.getUniqueId()),
                    playerEffects,
                    activeDamageOverTime(player.getUniqueId()),
                    activeCrowdControl(player.getUniqueId()),
                    cooldowns(player));

            player.sendPluginMessage(this, RaskolHudProtocol.STATE_CHANNEL, RaskolHudProtocol.encode(snapshot));
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Could not publish RaskolHUD snapshot for "
                    + player.getUniqueId() + ": " + exception.getMessage());
        }
    }


    private void observeClassSignals(
            Player player, String classId, List<RaskolHudEffect> effects, int health) {
        UUID playerId = player.getUniqueId();
        Set<String> currentEffects = new HashSet<>();
        for (RaskolHudEffect effect : effects) {
            currentEffects.add(effect.id());
        }

        Set<String> previousEffects = previousPlayerEffects.put(playerId, Set.copyOf(currentEffects));
        if ("priest".equals(classId) && previousEffects != null) {
            boolean newBlessing = currentEffects.stream()
                    .filter(id -> !previousEffects.contains(id))
                    .anyMatch(RaskolHudBridgePlugin::isBlessingEffect);
            if (newBlessing) {
                sendCrosshairSignal(player, CrosshairSignal.PRIEST_BLESSING);
            }
        }

        if ("priest".equals(classId)) {
            Set<String> currentWards = activePriestWardStates(player);
            Set<String> previousWards = previousPriestWards.put(playerId, Set.copyOf(currentWards));
            if (previousWards != null && currentWards.stream().anyMatch(id -> !previousWards.contains(id))) {
                sendCrosshairSignal(player, CrosshairSignal.PRIEST_BLESSING);
            }
        } else {
            previousPriestWards.remove(playerId);
        }

        boolean stealth = player.isSneaking() || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
        Boolean wasStealth = previousStealthStates.put(playerId, stealth);
        if ("rogue".equals(classId) && Boolean.FALSE.equals(wasStealth) && stealth) {
            sendCrosshairSignal(player, CrosshairSignal.ROGUE_STEALTH);
        }
    }

    /**
     * Читает именно активные защитные состояния жреца из публичного API RaskolClasses.
     * «Эгида веры» хранится в ResistService, а не в Bukkit PotionEffect.
     */
    private Set<String> activePriestWardStates(Player player) {
        Set<String> active = new HashSet<>();
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            active.add("potion:levitation");
        }
        if (player.hasPotionEffect(PotionEffectType.ABSORPTION)) {
            active.add("potion:absorption");
        }
        if (player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            active.add("potion:resistance");
        }

        try {
            Object service = invoke(raskolClasses, "getResists");
            Object breakdown = invoke(service, "breakdown", new Class<?>[] {UUID.class}, player.getUniqueId());
            Object modifiers = invoke(breakdown, "active");
            if (modifiers instanceof List<?> list) {
                for (Object modifier : list) {
                    String source = stringValue(invoke(modifier, "source"))
                            .toLowerCase(java.util.Locale.ROOT);
                    if (source.equals("aegis_faith")
                            || source.equals("pain_suppression")
                            || source.contains("shield")
                            || source.contains("ward")) {
                        active.add("resist:" + source);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read active priest ward modifiers", exception);
        }
        return active;
    }

    private static boolean isBlessingEffect(String id) {
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("shield")
                || normalized.contains("aegis")
                || normalized.contains("levitat")
                || normalized.contains("bless")
                || normalized.contains("ward")
                || normalized.contains("holy")
                || normalized.contains("light");
    }

    private void registerCustomHealSignal() {
        try {
            Class<?> rawType = Class.forName(
                    "dev.raskol.classes.event.CustomHealEvent",
                    false,
                    raskolClasses.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(rawType)) {
                return;
            }

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) rawType;
            getServer().getPluginManager().registerEvent(
                    eventType,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                            return;
                        }
                        try {
                            Object healer = invoke(event, "getHealer");
                            if (healer instanceof Player player && "priest".equals(classIdOf(player))) {
                                sendCrosshairSignal(player, CrosshairSignal.PRIEST_BLESSING);
                            }
                        } catch (ReflectiveOperationException | RuntimeException exception) {
                            warnOptionalApi("Could not read the healer from CustomHealEvent", exception);
                        }
                    },
                    this,
                    true);
            getLogger().info("RaskolHUD registered the optional RaskolClasses heal signal.");
        } catch (ClassNotFoundException | IllegalArgumentException exception) {
            getLogger().fine("CustomHealEvent is unavailable; priest crosshair uses observed effects only.");
        }
    }

    private void signalAfterTargetState(
            Player attacker, LivingEntity target, Supplier<CrosshairSignal> signalSupplier) {
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!attacker.isOnline() || !target.isValid() || target.isDead()) {
                return;
            }
            CrosshairSignal signal = signalSupplier.get();
            if (signal != CrosshairSignal.NONE) {
                sendCrosshairSignal(attacker, signal);
            }
        }, 1L);
    }

    private boolean hasOwnedDot(LivingEntity target, UUID ownerId, Set<String> acceptedIds) {
        try {
            Object combat = invoke(raskolClasses, "getCombat");
            Object dots = invoke(combat, "dots");
            Object value = invoke(dots, "activeDotsOf", new Class<?>[] {UUID.class}, target.getUniqueId());
            if (!(value instanceof List<?> active)) {
                return false;
            }

            for (Object instance : active) {
                Object source = invoke(instance, "ownerUuid");
                Object definition = invoke(instance, "def");
                String id = stringValue(invoke(definition, "id"));
                if (ownerId.equals(source)
                        && (acceptedIds == null || acceptedIds.contains(id.toLowerCase(java.util.Locale.ROOT)))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not inspect target DoT attribution for crosshair feedback", exception);
        }
        return false;
    }

    private boolean hasOwnedCrowdControl(LivingEntity target, UUID ownerId) {
        try {
            Object manager = invoke(raskolClasses, "getCC");
            Object value = invoke(manager, "activeOf", new Class<?>[] {UUID.class}, target.getUniqueId());
            if (!(value instanceof List<?> active)) {
                return false;
            }
            long now = System.currentTimeMillis();
            for (Object instance : active) {
                Object source = invoke(instance, "sourceUuid");
                long expiresAt = ((Number) invoke(instance, "expiresAt")).longValue();
                if (ownerId.equals(source) && expiresAt > now) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not inspect target crowd-control attribution for crosshair feedback", exception);
        }
        return false;
    }

    private String classIdOf(Player player) {
        return ClassPresentation.fromDisplayName(placeholder(player, "class")).id();
    }

    private void sendCrosshairSignal(Player player, CrosshairSignal signal) {
        if (player == null || signal == null || signal == CrosshairSignal.NONE
                || !hudClients.contains(player.getUniqueId())) {
            return;
        }
        player.sendPluginMessage(
                this,
                RaskolHudProtocol.SIGNAL_CHANNEL,
                new byte[] {(byte) signal.wireId()});
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static boolean isBehind(LivingEntity target, Player attacker) {
        Vector facing = target.getLocation().getDirection().setY(0.0);
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .setY(0.0);
        if (facing.lengthSquared() < 1.0e-9 || toAttacker.lengthSquared() < 1.0e-9) {
            return false;
        }
        double dot = facing.normalize().dot(toAttacker.normalize());
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angle >= 135.0;
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
