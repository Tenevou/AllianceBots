package ru.alliancemc.alliancebots.bot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;

public final class ClipBotManager {
    private final AllianceBotsPlugin plugin;
    private final Set<ClipBotTrait> bots = new LinkedHashSet<ClipBotTrait>();
    private BukkitTask task;
    private long tick;

    public ClipBotManager(AllianceBotsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick++;
                for (ClipBotTrait bot : snapshot()) {
                    bot.tick(tick);
                }
                int visibilityInterval = Math.max(20, plugin.getConfig()
                        .getInt("fight.tab.visibility-sync-interval-ticks", 40));
                if (tick % visibilityInterval == 0L) {
                    plugin.getBuildFfaIntegration().syncTabVisibilityForAllViewers();
                }
            }
        }, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ClipBotTrait bot : snapshot()) {
            bot.stop(false);
        }
        bots.clear();
    }

    public void register(ClipBotTrait bot) {
        if (bot != null) {
            bots.add(bot);
        }
    }

    public void unregister(ClipBotTrait bot) {
        if (bot != null) {
            bots.remove(bot);
        }
    }

    public int count() {
        return bots.size();
    }

    public int countBuildFfaOnlineBots() {
        return countBuildFfaOnlineBots(null);
    }

    public int countBuildFfaOnlineBots(World world) {
        int count = 0;
        for (ClipBotTrait bot : snapshot()) {
            if (!bot.isBuildFfaOnlineLikePlayer()) {
                continue;
            }
            Entity entity = bot.getNPC() == null ? null : bot.getNPC().getEntity();
            World botWorld = entity == null ? null : entity.getWorld();
            if (botWorld == null) {
                botWorld = bot.getSpawnLocation() == null ? null : bot.getSpawnLocation().getWorld();
            }
            if (botWorld == null || (world != null && !world.equals(botWorld))) {
                continue;
            }
            if (plugin.getBuildFfaIntegration().isFfaWorld(botWorld)) {
                count++;
            }
        }
        return count;
    }

    public List<ClipBotTrait> snapshot() {
        return Collections.unmodifiableList(new ArrayList<ClipBotTrait>(bots));
    }
}
