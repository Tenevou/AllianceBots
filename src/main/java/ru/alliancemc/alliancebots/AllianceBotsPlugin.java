package ru.alliancemc.alliancebots;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.TraitInfo;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.alliancemc.alliancebots.bot.BotSettings;
import ru.alliancemc.alliancebots.bot.ClipBotManager;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;
import ru.alliancemc.alliancebots.command.ClipBotCommand;
import ru.alliancemc.alliancebots.listener.ClipBotListener;
import ru.alliancemc.alliancebots.message.MessageService;
import ru.alliancemc.alliancebots.service.BuildFfaIntegration;
import ru.alliancemc.alliancebots.service.ProxyBridgeService;

public final class AllianceBotsPlugin extends JavaPlugin {
    private static AllianceBotsPlugin instance;

    private BotSettings defaults;
    private ClipBotManager botManager;
    private MessageService messageService;
    private BuildFfaIntegration buildFfaIntegration;
    private ProxyBridgeService proxyBridgeService;

    public static AllianceBotsPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadLocalConfig();
        buildFfaIntegration = new BuildFfaIntegration(this);
        proxyBridgeService = new ProxyBridgeService(this);
        proxyBridgeService.start();

        botManager = new ClipBotManager(this);
        botManager.start();

        CitizensAPI.getTraitFactory().registerTrait(TraitInfo.create(ClipBotTrait.class).withName("clipbot"));

        ClipBotCommand command = new ClipBotCommand(this);
        PluginCommand pluginCommand = getCommand("clipbot");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new ClipBotListener(this), this);
        getLogger().info("AllianceBots enabled.");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.stop();
        }
        if (proxyBridgeService != null) {
            proxyBridgeService.stop();
        }
        try {
            CitizensAPI.getTraitFactory().deregisterTrait(TraitInfo.create(ClipBotTrait.class).withName("clipbot"));
        } catch (RuntimeException ignored) {
            // Citizens may already be disabling.
        }
        instance = null;
    }

    public void reloadLocalConfig() {
        reloadConfig();
        defaults = BotSettings.fromConfig(getConfig());
        messageService = new MessageService(getConfig().getString("messages.prefix", "&8[&6AllianceBots&8] "));
    }

    public BotSettings getDefaults() {
        return defaults.copy();
    }

    public ClipBotManager getBotManager() {
        return botManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public BuildFfaIntegration getBuildFfaIntegration() {
        return buildFfaIntegration;
    }

    public ProxyBridgeService getProxyBridgeService() {
        return proxyBridgeService;
    }

    public int getMaximumBots() {
        return getConfig().getInt("limits.maximum-bots", 10);
    }
}
