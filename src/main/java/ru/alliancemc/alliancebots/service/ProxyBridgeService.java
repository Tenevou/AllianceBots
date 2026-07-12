package ru.alliancemc.alliancebots.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;

public final class ProxyBridgeService {
    private final AllianceBotsPlugin plugin;
    private BukkitTask task;
    private String channel;

    public ProxyBridgeService(AllianceBotsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("proxy-bridge.enabled", true)) {
            return;
        }
        channel = plugin.getConfig().getString("proxy-bridge.channel", "AllianceBots");
        if (channel == null || channel.trim().isEmpty()) {
            channel = "AllianceBots";
        }
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
        int interval = Math.max(5, plugin.getConfig().getInt("proxy-bridge.update-interval-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                sendUpdate();
            }
        }, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (channel != null) {
            try {
                sendUpdate(0);
            } catch (RuntimeException ignored) {
                // The server can already be disconnecting players during disable.
            }
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        }
    }

    public void sendUpdate() {
        if (!isEnabled()) {
            return;
        }
        sendUpdate(plugin.getBotManager() == null ? 0 : plugin.getBotManager().countBuildFfaOnlineBots());
    }

    private void sendUpdate(int botOnline) {
        Player carrier = findCarrier();
        if (carrier == null) {
            return;
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("ONLINE");
            out.writeUTF(plugin.getConfig().getString("proxy-bridge.server-name", ""));
            out.writeInt(Math.max(0, botOnline));
            out.writeLong(System.currentTimeMillis());
            carrier.sendPluginMessage(plugin, channel, bytes.toByteArray());
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("proxy-bridge.debug", false)) {
                plugin.getLogger().warning("Proxy bridge update failed: " + ex.getMessage());
            }
        }
    }

    private Player findCarrier() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && player.isOnline() && !CitizensAPI.getNPCRegistry().isNPC(player)) {
                return player;
            }
        }
        return null;
    }

    private boolean isEnabled() {
        return channel != null && plugin.getConfig().getBoolean("proxy-bridge.enabled", true);
    }
}
