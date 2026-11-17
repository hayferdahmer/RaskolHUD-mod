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
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;

import dev.raskol.hud.protocol.CrosshairSignalRules;
import dev.raskol.hud.protocol.CrosshairSignalRules.DotState;
import dev.raskol.hud.protocol.RaskolHudEffect;
import dev.raskol.hud.protocol.RaskolHudCooldown;
import dev.raskol.hud.protocol.RaskolHudCharacterStats;
import dev.raskol.hud.protocol.RaskolHudProtocol.CrosshairSignal;
import dev.raskol.hud.protocol.RaskolHudProtocol;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import me.clip.placeholderapi.PlaceholderAPI;

public final class RaskolHudBridgePlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private final Set<UUID> hudClients = new HashSet<>();
    private final Map<UUID, Set<String>> previousPlayerEffects = new HashMap<>();
    private final Map<UUID, Boolean> previousStealthStates = new HashMap<>();
    private final Map<UUID, PendingBackstab> pendingBackstabs = new HashMap<>();
    private Object raskolClasses;
    private boolean warnedOptionalApi;

    private record PendingBackstab(UUID targetUuid, long expiresAtMillis) {}

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
        pendingBackstabs.clear();
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
        pendingBackstabs.remove(playerId);
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
        Map<String, DotState> dotsBefore = ownedDotStates(target, ownerId);
        switch (classId) {
            case "warlock" -> signalAfterTargetState(
                    attacker, target,
                    () -> CrosshairSignalRules.hasNewOrRefreshedDot(
                                    dotsBefore, ownedDotStates(target, ownerId), null)
                            ? CrosshairSignal.WARLOCK_CURSE : CrosshairSignal.NONE);
            case "hunter" -> {
                // Метка определяется по конкретному свитку, а не по чужому Glowing.
            }
            case "warrior" -> signalAfterTargetState(
                    attacker, target,
                    () -> CrosshairSignalRules.hasNewOrRefreshedDot(
                                    dotsBefore,
                                    ownedDotStates(target, ownerId),
                                    Set.of("bleed", "bleed_passive"))
                            ? CrosshairSignal.WARRIOR_BLEED : CrosshairSignal.WARRIOR_HIT);
            case "mage" -> signalAfterTargetState(
                    attacker, target,
                    () -> CrosshairSignalRules.hasNewOrRefreshedDot(
                                    dotsBefore, ownedDotStates(target, ownerId), Set.of("chilled"))
                            ? CrosshairSignal.MAGE_FROST : CrosshairSignal.NONE);
            case "rogue" -> {
                PendingBackstab pending = pendingBackstabs.remove(ownerId);
                if (pending != null
                        && pending.expiresAtMillis() > System.currentTimeMillis()
                        && pending.targetUuid().equals(target.getUniqueId())
                        && isBehind(target, attacker)) {
                    sendCrosshairSignal(attacker, CrosshairSignal.ROGUE_BACKSTAB);
                }
            }
            default -> {
                // Для неизвестного класса боевые импульсы не отправляются.
            }
        }
    }

    /**
     * Считывает контекст свитка до обработчика RaskolClasses (NORMAL).
     * Сам импульс отправляется только после подтверждённого изменения эффекта.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAbilityInteraction(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!hudClients.contains(player.getUniqueId())) {
            return;
        }

        String abilityId = readAbilityId(event.getItem());
        if (abilityId.isBlank()) {
            return;
        }

        String classId = classIdOf(player);
        if ("hunter".equals(classId)
                && ("wolf_mark".equals(abilityId) || "aimed_shot".equals(abilityId))) {
            int range = "aimed_shot".equals(abilityId) ? 24 : 20;
            Entity entity = player.getTargetEntity(range);
            if (entity instanceof LivingEntity target) {
                Map<String, Long> before = hunterMarkState(target);
                getServer().getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline() && target.isValid() && !target.isDead()
                            && hunterMarkState(target).containsKey("glowing")
                            && CrosshairSignalRules.hasNewOrExtendedState(
                                    before, hunterMarkState(target), 25L)) {
                        sendCrosshairSignal(player, CrosshairSignal.HUNTER_MARK);
                    }
                }, 1L);
            }
            return;
        }

        if ("rogue".equals(classId) && "backstab".equals(abilityId)) {
            Entity entity = player.getTargetEntity(4);
            if (entity instanceof LivingEntity target) {
                pendingBackstabs.put(
                        player.getUniqueId(),
                        new PendingBackstab(target.getUniqueId(), System.currentTimeMillis() + 1_500L));
            } else {
                pendingBackstabs.remove(player.getUniqueId());
            }
            return;
        }

        if ("priest".equals(classId)
                && Set.of("aegis_faith", "pain_suppression", "spirit_shell").contains(abilityId)) {
            Map<UUID, Map<String, Long>> before = priestWardSnapshots(player, abilityId);
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline() || !"priest".equals(classIdOf(player))) {
                    return;
                }
                for (Map.Entry<UUID, Map<String, Long>> entry : before.entrySet()) {
                    Player affected = Bukkit.getPlayer(entry.getKey());
                    if (affected != null && affected.isOnline()
                            && CrosshairSignalRules.hasNewOrExtendedState(
                                    entry.getValue(), priestWardState(affected), 25L)) {
                        sendCrosshairSignal(player, CrosshairSignal.PRIEST_BLESSING);
                        return;
                    }
                }
            }, 1L);
        }
    }

    private String readAbilityId(ItemStack item) {
        if (item == null) {
            return "";
        }
        try {
            Object tokens = invoke(raskolClasses, "getTokens");
            Object value = invoke(tokens, "readId", new Class<?>[] {ItemStack.class}, item);
            return stringValue(value).trim().toLowerCase(java.util.Locale.ROOT);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read the active ability token for crosshair feedback", exception);
            return "";
        }
    }

    private Map<String, Long> hunterMarkState(LivingEntity target) {
        Map<String, Long> state = new HashMap<>();
        if (target.isGlowing()) {
            state.put("glowing", Long.MAX_VALUE);
        }
        PotionEffect slowness = target.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slowness != null) {
            long expiry = System.currentTimeMillis() + (long) slowness.getDuration() * 50L;
            state.put("slowness:" + slowness.getAmplifier(), expiry);
        }
        return Map.copyOf(state);
    }

    private Map<UUID, Map<String, Long>> priestWardSnapshots(Player caster, String abilityId) {
        Set<Player> affected = new HashSet<>();
        affected.add(caster);

        if ("pain_suppression".equals(abilityId)) {
            Entity target = caster.getTargetEntity(20);
            if (target instanceof Player ally) {
                affected.add(ally);
            }
        } else if ("spirit_shell".equals(abilityId)) {
            for (Entity entity : caster.getNearbyEntities(6.0, 6.0, 6.0)) {
                if (entity instanceof Player ally) {
                    affected.add(ally);
                }
            }
        }

        Map<UUID, Map<String, Long>> snapshots = new HashMap<>();
        for (Player player : affected) {
            snapshots.put(player.getUniqueId(), priestWardState(player));
        }
        return Map.copyOf(snapshots);
    }

    private Map<String, Long> priestWardState(Player player) {
        Map<String, Long> state = new HashMap<>();
        long now = System.currentTimeMillis();
        for (PotionEffectType type : List.of(PotionEffectType.ABSORPTION, PotionEffectType.RESISTANCE)) {
            PotionEffect effect = player.getPotionEffect(type);
            if (effect != null) {
                String key = "potion:" + type.getKey().getKey() + ":" + effect.getAmplifier();
                state.put(key, now + (long) effect.getDuration() * 50L);
            }
        }

        try {
            Object service = invoke(raskolClasses, "getResists");
            Object breakdown = invoke(service, "breakdown", new Class<?>[] {UUID.class}, player.getUniqueId());
            Object modifiers = invoke(breakdown, "active");
            if (modifiers instanceof List<?> list) {
                for (Object modifier : list) {
                    String source = stringValue(invoke(modifier, "source"))
                            .toLowerCase(java.util.Locale.ROOT);
                    if (source.equals("aegis_faith") || source.contains("shield") || source.contains("ward")) {
                        Object value = invoke(modifier, "expiresAt");
                        if (value instanceof Number number && number.longValue() > now) {
                            state.put("resist:" + source, number.longValue());
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read priest ward state", exception);
        }
        return Map.copyOf(state);
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
                    cooldowns(player),
                    characterStats(player));

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

        boolean stealth = player.isSneaking() || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
        Boolean wasStealth = previousStealthStates.put(playerId, stealth);
        if ("rogue".equals(classId) && Boolean.FALSE.equals(wasStealth) && stealth) {
            sendCrosshairSignal(player, CrosshairSignal.ROGUE_STEALTH);
        }
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

    private Map<String, DotState> ownedDotStates(LivingEntity target, UUID ownerId) {
        try {
            Object combat = invoke(raskolClasses, "getCombat");
            Object dots = invoke(combat, "dots");
            Object value = invoke(dots, "activeDotsOf", new Class<?>[] {UUID.class}, target.getUniqueId());
            if (!(value instanceof List<?> active)) {
                return Map.of();
            }

            Map<String, DotState> states = new HashMap<>();
            for (Object instance : active) {
                Object source = invoke(instance, "ownerUuid");
                if (!ownerId.equals(source)) {
                    continue;
                }
                Object definition = invoke(instance, "def");
                String id = stringValue(invoke(definition, "id"))
                        .toLowerCase(java.util.Locale.ROOT);
                int stacks = ((Number) invoke(instance, "stacks")).intValue();
                long expiresAt = ((Number) invoke(instance, "expiresAt")).longValue();
                if (!id.isBlank()) {
                    states.put(id, new DotState(stacks, expiresAt));
                }
            }
            return Map.copyOf(states);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not inspect target DoT attribution for crosshair feedback", exception);
            return Map.of();
        }
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

    private boolean isBehind(LivingEntity target, Player attacker) {
        Vector facing = target.getLocation().getDirection().setY(0.0);
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .setY(0.0);
        if (facing.lengthSquared() < 1.0e-9 || toAttacker.lengthSquared() < 1.0e-9) {
            return false;
        }
        double dot = facing.normalize().dot(toAttacker.normalize());
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return CrosshairSignalRules.isWithinBackstabArc(angle, configuredBackAngle());
    }

    private double configuredBackAngle() {
        try {
            Object config = invoke(raskolClasses, "getConfig");
            Object value = invoke(
                    config,
                    "getDouble",
                    new Class<?>[] {String.class, double.class},
                    "avoidance.back-angle",
                    135.0);
            if (value instanceof Number number
                    && Double.isFinite(number.doubleValue())
                    && number.doubleValue() >= 0.0
                    && number.doubleValue() <= 180.0) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnOptionalApi("Could not read RaskolClasses backstab angle", exception);
        }
        return 135.0;
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

    private RaskolHudCharacterStats characterStats(Player player) {
        String physical = placeholder(player, "phys_resist");
        String magic = placeholder(player, "magic_resist");
        String dodge = placeholder(player, "dodge");
        String parry = placeholder(player, "parry");
        String meleeCrit = placeholder(player, "crit_melee");
        String spellCrit = placeholder(player, "crit_spell");
        String availablePoints = placeholder(player, "talent_points");
        String talentProgress = placeholder(player, "talents");

        boolean available = !(physical.isBlank() && magic.isBlank() && dodge.isBlank()
                && parry.isBlank() && meleeCrit.isBlank() && spellCrit.isBlank()
                && availablePoints.isBlank() && talentProgress.isBlank());
        if (!available) {
            return RaskolHudCharacterStats.UNAVAILABLE;
        }

        int spent = 0;
        int earned = 0;
        if (!talentProgress.isBlank()) {
            String[] parts = talentProgress.split("/", -1);
            if (parts.length == 2) {
                spent = parseNonNegativeInt(parts[0]);
                earned = parseNonNegativeInt(parts[1]);
            }
        }

        return new RaskolHudCharacterStats(
                true,
                parseDecimal(physical),
                parseDecimal(magic),
                parseDecimal(dodge),
                parseDecimal(parry),
                parseDecimal(meleeCrit),
                parseDecimal(spellCrit),
                parseNonNegativeInt(availablePoints),
                spent,
                earned);
    }

    private static double parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return 0.0D;
        }
        try {
            double parsed = Double.parseDouble(value.replace(',', '.').replace("%", "").trim());
            return Double.isFinite(parsed) ? parsed : 0.0D;
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private static int parseNonNegativeInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return 0;
        }
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
