package dev.raskol.hud.bridge;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import dev.raskol.hud.protocol.RaskolHudProtocol;
import dev.raskol.hud.protocol.RaskolHudSnapshot;
import me.clip.placeholderapi.PlaceholderAPI;

public final class RaskolHudBridgePlugin extends JavaPlugin implements PluginMessageListener, Listener {

    private final Set<UUID> hudClients = new HashSet<>();

    @Override
    public void onEnable() {
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
                    0);

            player.sendPluginMessage(this, RaskolHudProtocol.STATE_CHANNEL, RaskolHudProtocol.encode(snapshot));
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Could not publish RaskolHUD snapshot for "
                    + player.getUniqueId() + ": " + exception.getMessage());
        }
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
