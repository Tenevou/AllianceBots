package ru.alliancemc.alliancebots.api;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;
import ru.alliancemc.alliancebots.bot.BotMode;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;

public final class AllianceBotsAPI {
    private AllianceBotsAPI() {
    }

    public static boolean isClipBot(NPC npc) {
        return npc != null && npc.hasTrait(ClipBotTrait.class);
    }

    public static boolean isBot(NPC npc) {
        return isClipBot(npc);
    }

    public static boolean isClipBot(Entity entity) {
        if (entity == null || !CitizensAPI.getNPCRegistry().isNPC(entity)) {
            return false;
        }
        return isClipBot(CitizensAPI.getNPCRegistry().getNPC(entity));
    }

    public static boolean isBot(Entity entity) {
        return isClipBot(entity);
    }

    public static ClipBotTrait getClipBot(NPC npc) {
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            return null;
        }
        return npc.getTrait(ClipBotTrait.class);
    }

    public static ClipBotTrait getBot(NPC npc) {
        return getClipBot(npc);
    }

    public static BotMode getMode(NPC npc) {
        ClipBotTrait bot = getBot(npc);
        return bot == null ? null : bot.getMode();
    }

    public static boolean setTarget(NPC npc, Player target) {
        ClipBotTrait bot = getBot(npc);
        if (bot == null) {
            return false;
        }
        bot.setTarget(target);
        return true;
    }

    public static boolean start(NPC npc) {
        ClipBotTrait bot = getBot(npc);
        if (bot == null) {
            return false;
        }
        bot.start();
        return true;
    }

    public static boolean stop(NPC npc) {
        ClipBotTrait bot = getBot(npc);
        if (bot == null) {
            return false;
        }
        bot.stop(true);
        return true;
    }

    public static int getBuildFfaOnlineCount() {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || plugin.getBotManager() == null) {
            return 0;
        }
        return plugin.getBotManager().countBuildFfaOnlineBots();
    }

    public static int getBuildFfaOnlineCount(String worldName) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || plugin.getBotManager() == null) {
            return 0;
        }
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        return plugin.getBotManager().countBuildFfaOnlineBots(world);
    }

    public static int getOnlineBotCount() {
        return getBuildFfaOnlineCount();
    }
}
