package ru.alliancemc.alliancebots.bot;

import java.util.UUID;
import net.citizensnpcs.api.exception.NPCLoadException;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;
import ru.alliancemc.alliancebots.api.event.ClipBotStartEvent;
import ru.alliancemc.alliancebots.api.event.ClipBotStopEvent;
import ru.alliancemc.alliancebots.api.event.ClipBotTargetChangeEvent;

@TraitName("clipbot")
public class ClipBotTrait extends Trait {
    @Persist("target-uuid")
    private UUID targetUuid;
    @Persist("enabled")
    private boolean running;
    @Persist("attack-other-bots")
    private boolean attackOtherBots;
    @Persist("target-locked")
    private boolean targetLocked;

    private BotSettings settings;
    private BotState state = BotState.IDLE;
    private BotMode mode = BotMode.CLIP;
    private BotDifficulty difficulty = BotDifficulty.MEDIUM;
    private boolean debug;
    private Location spawnLocation;
    private UUID lastDamagerUuid;
    private long lastDamagerMillis;
    private boolean deathInProgress;
    private int ffaSyncTicks;
    private int ffaAppearanceSyncTicks;
    private int ffaPassiveHealTicks;
    private int initialSpawnExitTicks;
    private boolean ffaJoinAnnounced;
    private boolean removed;
    private int lifecycleGeneration;
    private String lastFfaSpawnSignature;
    private ClipBotController clipController;
    private FightBotController fightController;

    public ClipBotTrait() {
        super("clipbot");
    }

    @Override
    public void onAttach() {
        removed = false;
        ensureReady();
        configureNpcMetadata();
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin != null && plugin.getBotManager() != null) {
            plugin.getBotManager().register(this);
        }
        if (npc != null) {
            npc.setProtected(false);
        }
    }

    @Override
    public void onSpawn() {
        configureNpcMetadata();
        applyLoadout();
    }

    @Override
    public void onRemove() {
        removed = true;
        cancelPendingRespawn();
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin != null && plugin.getBotManager() != null) {
            plugin.getBotManager().unregister(this);
        }
        stop(false);
    }

    @Override
    public void load(DataKey key) throws NPCLoadException {
        ensureSettings();
        String uuid = key.getString("target-uuid", "");
        if (uuid != null && uuid.length() > 0) {
            try {
                targetUuid = UUID.fromString(uuid);
            } catch (IllegalArgumentException ignored) {
                targetUuid = null;
            }
        }
        running = key.getBoolean("enabled", false);
        mode = BotMode.parse(key.getString("mode", "CLIP"));
        difficulty = BotDifficulty.parse(key.getString("difficulty", "MEDIUM"));
        debug = key.getBoolean("debug", false);
        attackOtherBots = key.getBoolean("attack-other-bots", false);
        targetLocked = key.getBoolean("target-locked", false);
        spawnLocation = loadLocation(key.getRelative("spawn"));
        settings.load(key.getRelative("settings"), getDefaultSettings());
        applyModeDefaults(false);
        state = running ? BotState.CHASE : BotState.IDLE;
    }

    @Override
    public void save(DataKey key) {
        ensureSettings();
        key.setString("target-uuid", targetUuid == null ? "" : targetUuid.toString());
        key.setBoolean("enabled", running);
        key.setString("mode", mode.name());
        key.setString("difficulty", difficulty.name());
        key.setBoolean("debug", debug);
        key.setBoolean("attack-other-bots", attackOtherBots);
        key.setBoolean("target-locked", targetLocked);
        saveLocation(key.getRelative("spawn"), spawnLocation);
        settings.save(key.getRelative("settings"));
    }

    public void tick(long tick) {
        ensureReady();
        if (mode == BotMode.FIGHT) {
            syncFfaState();
            if (checkFfaVoidDeath()) {
                return;
            }
            fightController.tick(tick);
        } else {
            clipController.tick(tick);
        }
    }

    public void start() {
        if (removed || running) {
            return;
        }
        if (mode == BotMode.FIGHT) {
            updateFfaSpawn(true);
            if (npc != null && !npc.isSpawned() && spawnLocation != null) {
                respawn();
                if (running) {
                    announceFfaJoin();
                    Bukkit.getPluginManager().callEvent(new ClipBotStartEvent(this));
                }
                return;
            }
        }
        running = true;
        state = BotState.CHASE;
        beginInitialSpawnExit();
        announceFfaJoin();
        Bukkit.getPluginManager().callEvent(new ClipBotStartEvent(this));
    }

    public void stop(boolean notify) {
        boolean wasActive = running || state != BotState.IDLE || deathInProgress;
        cancelPendingRespawn();
        if (!wasActive) {
            return;
        }
        running = false;
        state = BotState.IDLE;
        if (clipController != null) {
            clipController.stop();
        }
        if (fightController != null) {
            fightController.stop();
        }
        if (notify) {
            Bukkit.getPluginManager().callEvent(new ClipBotStopEvent(this));
        }
    }

    public void setTarget(Player target) {
        setTargetUuid(target == null ? null : target.getUniqueId());
    }

    public void setTargetUuid(UUID targetUuid) {
        UUID oldTarget = this.targetUuid;
        this.targetUuid = targetUuid;
        Player target = targetUuid == null ? null : Bukkit.getPlayer(targetUuid);
        Bukkit.getPluginManager().callEvent(new ClipBotTargetChangeEvent(this, oldTarget, target));
    }

    public void handleDamage(Entity damager) {
        ensureReady();
        if (damager instanceof Player && isDamageableGameMode((Player) damager)) {
            lastDamagerUuid = damager.getUniqueId();
            lastDamagerMillis = System.currentTimeMillis();
        }
        if (mode == BotMode.FIGHT) {
            fightController.enterKnockback(damager);
        } else {
            clipController.enterKnockback(damager);
        }
    }

    public boolean handleFatalDamage(double damage) {
        ensureReady();
        if (mode != BotMode.FIGHT || npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
            return false;
        }
        if (settings.isInvulnerable()) {
            return false;
        }
        Player botPlayer = (Player) npc.getEntity();
        if (botPlayer.getHealth() - damage > 0.0D) {
            return false;
        }
        Player killer = getRecentDamager();
        if (killer == null) {
            killer = botPlayer.getKiller();
        }
        killAndRespawn(botPlayer, killer);
        return true;
    }

    public boolean handleVanillaDeath(Player deadPlayer) {
        ensureReady();
        if (mode != BotMode.FIGHT || deadPlayer == null || npc == null) {
            return false;
        }
        Player killer = deadPlayer.getKiller();
        if (killer == null) {
            killer = getRecentDamager();
        }
        killAndRespawn(deadPlayer, killer);
        return true;
    }

    private boolean checkFfaVoidDeath() {
        if (deathInProgress || npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
            return false;
        }
        Player botPlayer = (Player) npc.getEntity();
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || !plugin.getBuildFfaIntegration().isFfaWorld(botPlayer.getWorld())) {
            return false;
        }
        if (botPlayer.getLocation().getY() >= plugin.getBuildFfaIntegration().getVoidHeight(botPlayer.getWorld())) {
            return false;
        }
        killAndRespawn(botPlayer, getRecentDamager());
        return true;
    }

    private Player getRecentDamager() {
        if (lastDamagerUuid == null) {
            return null;
        }
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        long timeout = plugin == null ? 10000L : plugin.getBuildFfaIntegration().getCombatLogMillis();
        if (System.currentTimeMillis() - lastDamagerMillis > timeout) {
            lastDamagerUuid = null;
            return null;
        }
        return Bukkit.getPlayer(lastDamagerUuid);
    }

    private void killAndRespawn(Player botPlayer, Player killer) {
        if (deathInProgress) {
            return;
        }
        deathInProgress = true;
        AllianceBotsPlugin.getInstance().getBuildFfaIntegration().syncBotHealth(botPlayer);
        if (killer != null) {
            boolean recorded = AllianceBotsPlugin.getInstance().getBuildFfaIntegration().recordBotKilledByPlayer(
                    botPlayer, killer, botPlayer.getLocation());
            if (!recorded) {
                AllianceBotsPlugin.getInstance().getBuildFfaIntegration().sendDeathMessage(botPlayer, killer);
            }
            AllianceBotsPlugin.getInstance().getBuildFfaIntegration().healLikeBuildFfa(killer);
        } else {
            AllianceBotsPlugin.getInstance().getBuildFfaIntegration().sendDeathMessage(botPlayer, null);
        }
        running = false;
        state = BotState.IDLE;
        if (fightController != null) {
            fightController.stop();
        }
        if (npc.isSpawned()) {
            npc.despawn();
        }
        if (settings.isAutoRespawn()) {
            final int generation = ++lifecycleGeneration;
            Bukkit.getScheduler().runTaskLater(AllianceBotsPlugin.getInstance(), new Runnable() {
                @Override
                public void run() {
                    if (removed || generation != lifecycleGeneration) {
                        return;
                    }
                    respawn();
                }
            }, settings.getRespawnDelayTicks());
        }
        lastDamagerUuid = null;
        lastDamagerMillis = 0L;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isBuildFfaOnlineLikePlayer() {
        return !removed && mode == BotMode.FIGHT && (running || deathInProgress);
    }

    public void markRemoved() {
        removed = true;
        stop(false);
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public BotSettings getSettings() {
        ensureSettings();
        return settings;
    }

    public BotState getState() {
        return state;
    }

    public void setState(BotState state) {
        this.state = state == null ? BotState.IDLE : state;
    }

    public BotMode getMode() {
        return mode;
    }

    public void setMode(BotMode mode) {
        this.mode = mode == null ? BotMode.CLIP : mode;
        applyModeDefaults(true);
        if (this.mode == BotMode.FIGHT) {
            updateFfaSpawn(true);
        }
        applyLoadout();
    }

    public BotDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(BotDifficulty difficulty) {
        this.difficulty = difficulty == null ? BotDifficulty.MEDIUM : difficulty;
        applyModeDefaults(true);
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isAttackOtherBots() {
        return attackOtherBots;
    }

    public void setAttackOtherBots(boolean attackOtherBots) {
        this.attackOtherBots = attackOtherBots;
    }

    public boolean isTargetLocked() {
        return targetLocked;
    }

    public void setTargetLocked(boolean targetLocked) {
        this.targetLocked = targetLocked;
    }

    public Location getSpawnLocation() {
        return spawnLocation == null ? null : spawnLocation.clone();
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation == null ? null : spawnLocation.clone();
    }

    public void respawn() {
        ensureReady();
        if (removed || npc == null) {
            return;
        }
        if (mode == BotMode.FIGHT) {
            updateFfaSpawn(false);
        }
        Location location = spawnLocation;
        if (location == null && npc != null) {
            location = npc.getStoredLocation();
        }
        if (location == null) {
            return;
        }
        if (npc.isSpawned()) {
            npc.teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            npc.spawn(location);
        }
        if (npc.getEntity() instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) npc.getEntity();
            living.setHealth(Math.min(living.getMaxHealth(), settings.getMaximumHealth()));
        }
        applyLoadout();
        syncFfaState();
        deathInProgress = false;
        running = true;
        state = BotState.CHASE;
        beginInitialSpawnExit();
    }

    private void cancelPendingRespawn() {
        lifecycleGeneration++;
        deathInProgress = false;
        initialSpawnExitTicks = 0;
    }

    public boolean isInitialSpawnExitActive() {
        return mode == BotMode.FIGHT && initialSpawnExitTicks > 0;
    }

    public void tickInitialSpawnExit() {
        if (initialSpawnExitTicks > 0) {
            initialSpawnExitTicks--;
        }
    }

    private void beginInitialSpawnExit() {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (mode != BotMode.FIGHT || plugin == null) {
            initialSpawnExitTicks = 0;
            return;
        }
        initialSpawnExitTicks = Math.max(0, plugin.getConfig().getInt("fight.initial-spawn-exit-only-ticks", 45));
        alignInitialSpawnLook();
    }

    private void alignInitialSpawnLook() {
        if (npc == null || !npc.isSpawned() || spawnLocation == null || !(npc.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) npc.getEntity();
        if (spawnLocation.getWorld() == null || !spawnLocation.getWorld().equals(player.getWorld())) {
            return;
        }
        Location look = player.getLocation().clone();
        look.setYaw(spawnLocation.getYaw());
        look.setPitch(0.0F);
        npc.teleport(look, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private void announceFfaJoin() {
        if (ffaJoinAnnounced || mode != BotMode.FIGHT || npc == null || !npc.isSpawned()
                || !(npc.getEntity() instanceof Player)) {
            return;
        }
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        plugin.getBuildFfaIntegration().sendJoinMessage((Player) npc.getEntity());
        ffaJoinAnnounced = true;
    }

    private void syncFfaState() {
        if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
            return;
        }
        boolean spawnChanged = updateFfaSpawn(false);
        Player player = (Player) npc.getEntity();
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        Location currentSpawn = plugin.getBuildFfaIntegration().getCurrentSpawn();
        if (currentSpawn != null && currentSpawn.getWorld() != null
                && plugin.getConfig().getBoolean("fight.ffa.follow-current-map-spawn", true)
                && !deathInProgress
                && shouldTeleportToCurrentFfaSpawn(player, currentSpawn, spawnChanged, plugin)) {
            npc.teleport(currentSpawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            applyLoadout();
        }
        applyPassiveFfaHeal(player, plugin);
        refreshFfaAppearance(player, plugin);
        if (ffaSyncTicks-- > 0) {
            return;
        }
        ffaSyncTicks = Math.max(1, plugin.getConfig().getInt("fight.ffa.sync-interval-ticks", 5));
        plugin.getBuildFfaIntegration().syncBotHealth(player);
    }

    private void refreshFfaAppearance(Player player, AllianceBotsPlugin plugin) {
        if (plugin == null || player == null || !player.isValid()) {
            return;
        }
        if (ffaAppearanceSyncTicks-- > 0) {
            return;
        }
        ffaAppearanceSyncTicks = Math.max(20, plugin.getConfig()
                .getInt("fight.tab.appearance-refresh-interval-ticks", 40));
        plugin.getBuildFfaIntegration().refreshBotAppearance(player);
    }

    private boolean shouldTeleportToCurrentFfaSpawn(Player player, Location currentSpawn,
                                                   boolean spawnChanged, AllianceBotsPlugin plugin) {
        if (!player.getWorld().equals(currentSpawn.getWorld())) {
            return true;
        }
        if (!spawnChanged || !plugin.getConfig().getBoolean("fight.ffa.teleport-on-map-rotation", true)) {
            return false;
        }
        return true;
    }

    private boolean updateFfaSpawn(boolean teleportIfSpawned) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || !plugin.getConfig().getBoolean("fight.ffa.use-buildffa-spawn", true)) {
            return false;
        }
        Location currentSpawn = plugin.getBuildFfaIntegration().getCurrentSpawn();
        if (currentSpawn == null || currentSpawn.getWorld() == null) {
            return false;
        }
        String signature = ffaSpawnSignature(currentSpawn);
        boolean changed = lastFfaSpawnSignature != null && !lastFfaSpawnSignature.equals(signature);
        lastFfaSpawnSignature = signature;
        spawnLocation = currentSpawn.clone();
        if (teleportIfSpawned && npc != null && npc.isSpawned()) {
            npc.teleport(currentSpawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        return changed;
    }

    private String ffaSpawnSignature(Location location) {
        return location.getWorld().getName() + ":"
                + location.getBlockX() + ":"
                + location.getBlockY() + ":"
                + location.getBlockZ() + ":"
                + Math.round(location.getYaw()) + ":"
                + Math.round(location.getPitch());
    }

    private void applyPassiveFfaHeal(Player player, AllianceBotsPlugin plugin) {
        if (!plugin.getConfig().getBoolean("fight.ffa.passive-heal.enabled", true)
                || deathInProgress || player.isDead() || !isDamageableGameMode(player)) {
            ffaPassiveHealTicks = 0;
            return;
        }
        if (player.getHealth() <= 0.0D || player.getHealth() >= player.getMaxHealth()) {
            ffaPassiveHealTicks = 0;
            return;
        }
        if (plugin.getConfig().getBoolean("fight.ffa.passive-heal.respect-natural-regeneration-gamerule", true)) {
            String natural = player.getWorld().getGameRuleValue("naturalRegeneration");
            if ("false".equalsIgnoreCase(natural)) {
                ffaPassiveHealTicks = 0;
                return;
            }
        }

        EntityRegainHealthEvent.RegainReason reason;
        int interval;
        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            reason = EntityRegainHealthEvent.RegainReason.REGEN;
            interval = plugin.getConfig().getInt("fight.ffa.passive-heal.peaceful-interval-ticks", 20);
        } else {
            int foodMinimum = plugin.getConfig().getInt("fight.ffa.passive-heal.food-minimum", 18);
            if (player.getFoodLevel() < foodMinimum) {
                ffaPassiveHealTicks = 0;
                return;
            }
            reason = EntityRegainHealthEvent.RegainReason.SATIATED;
            interval = plugin.getConfig().getInt("fight.ffa.passive-heal.satiated-interval-ticks", 80);
        }

        interval = Math.max(1, interval);
        if (ffaPassiveHealTicks <= 0) {
            ffaPassiveHealTicks = interval;
        }
        ffaPassiveHealTicks--;
        if (ffaPassiveHealTicks > 0) {
            return;
        }

        double amount = Math.max(0.0D, plugin.getConfig().getDouble("fight.ffa.passive-heal.amount", 1.0D));
        if (amount <= 0.0D) {
            return;
        }
        EntityRegainHealthEvent event = new EntityRegainHealthEvent(player, amount, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled() && event.getAmount() > 0.0D) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + event.getAmount()));
            plugin.getBuildFfaIntegration().syncBotHealth(player);
        }
        ffaPassiveHealTicks = interval;
    }

    private boolean isDamageableGameMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void ensureReady() {
        ensureSettings();
        if (clipController == null) {
            clipController = new ClipBotController(this);
        }
        if (fightController == null) {
            fightController = new FightBotController(this);
        }
    }

    private void configureNpcMetadata() {
        if (npc == null) {
            return;
        }
        npc.data().setPersistent(net.citizensnpcs.api.npc.NPC.Metadata.REMOVE_FROM_PLAYERLIST, false);
        npc.data().setPersistent("removefromtablist", false);
        npc.data().setPersistent("removefromplayerlist", false);
        npc.data().set("removefromtablist", false);
        npc.data().set("removefromplayerlist", false);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.NAMEPLATE_VISIBLE, true);
        npc.data().set("nameplate-visible", true);
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        String teamName = plugin == null ? "ab_bot" : plugin.getBuildFfaIntegration().getScoreboardTeamName();
        npc.data().setPersistent(net.citizensnpcs.api.npc.NPC.Metadata.SCOREBOARD_FAKE_TEAM_NAME, teamName);
        npc.data().setPersistent("fake-scoreboard-team-name", teamName);
        npc.data().set("fake-scoreboard-team-name", teamName);
        npc.data().setPersistent("alliancebots", true);
        npc.data().setPersistent("AllianceBotsNPC", true);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.SPAWN_NODAMAGE_TICKS, 0);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.TARGETABLE, true);
        npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.DAMAGE_OTHERS, true);
        if (npc.isSpawned() && npc.getEntity() instanceof Player) {
            configurePlayerMetadata((Player) npc.getEntity());
        }
    }

    private void configurePlayerMetadata(Player player) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || player == null) {
            return;
        }
        player.setMetadata("alliancebots", new FixedMetadataValue(plugin, true));
        player.setMetadata("AllianceBotsNPC", new FixedMetadataValue(plugin, true));
        player.setMetadata("NPC", new FixedMetadataValue(plugin, true));
        player.setMetadata("CitizensNPC", new FixedMetadataValue(plugin, true));
        String suffix = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("fight.tab.plain-player-list-suffix", " [0]")));
        String baseName = player.getName();
        if (baseName.length() + suffix.length() > 16) {
            baseName = baseName.substring(0, Math.max(1, 16 - suffix.length()));
        }
        String listName = baseName + suffix;
        if (listName.length() > 16) {
            listName = listName.substring(0, 16);
        }
        String coloredListName = org.bukkit.ChatColor.GRAY + listName;
        player.setPlayerListName(coloredListName.length() <= 16 ? coloredListName : listName);
        player.setDisplayName(org.bukkit.ChatColor.GRAY + player.getName());
    }

    private void ensureSettings() {
        if (settings == null) {
            settings = getDefaultSettings();
        }
    }

    private BotSettings getDefaultSettings() {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin != null) {
            return plugin.getDefaults();
        }
        return BotSettings.fromConfig(new org.bukkit.configuration.file.YamlConfiguration());
    }

    private void equipSword() {
        if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) npc.getEntity();
        Material material = Material.getMaterial("DIAMOND_SWORD");
        if (material == null) {
            material = Material.getMaterial("IRON_SWORD");
        }
        if (material != null && player.getItemInHand() == null) {
            player.setItemInHand(new ItemStack(material));
        } else if (material != null && player.getItemInHand().getType() == Material.AIR) {
            player.setItemInHand(new ItemStack(material));
        }
    }

    private void applyLoadout() {
        if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) npc.getEntity();
        configurePlayerMetadata(player);
        if (mode == BotMode.FIGHT) {
            player.setGameMode(GameMode.SURVIVAL);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setExhaustion(0.0F);
            AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
            if (plugin != null && plugin.getBuildFfaIntegration().giveDefaultKit(player)) {
                plugin.getBuildFfaIntegration().syncBotHealth(player);
                plugin.getBuildFfaIntegration().applyTabIntegration(player);
                return;
            }
        }
        equipSword();
    }

    private void applyModeDefaults(boolean resetFightValues) {
        ensureSettings();
        if (mode != BotMode.FIGHT || !resetFightValues) {
            return;
        }
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        String base = "fight.";
        settings.setDetectRange(plugin.getConfig().getDouble(base + "detect-range", 24.0));
        settings.setSwingRange(plugin.getConfig().getDouble(base + "swing-range", 4.0));
        settings.setDamage(plugin.getConfig().getDouble(base + "damage", 1.0));
        settings.setMovementSpeed(plugin.getConfig().getDouble(base + "movement-speed", settings.getMovementSpeed()));
        settings.setPreferredDistance(plugin.getConfig().getDouble(base + "preferred-distance", 2.4));
        settings.setTooCloseDistance(plugin.getConfig().getDouble(base + "too-close-distance", 1.2));
        settings.setStopDistance(settings.getTooCloseDistance());
        settings.setInvulnerable(plugin.getConfig().getBoolean(base + "health.invulnerable", false));
        settings.setMaximumHealth(plugin.getConfig().getDouble(base + "health.maximum", 20.0));

        String difficultyPath = base + "difficulty." + difficulty.name().toLowerCase() + ".";
        settings.setCpsMin(plugin.getConfig().getInt(difficultyPath + "cps-min", settings.getCpsMin()));
        settings.setCpsMax(plugin.getConfig().getInt(difficultyPath + "cps-max", settings.getCpsMax()));
        settings.setHitRange(plugin.getConfig().getDouble(difficultyPath + "hit-range",
                plugin.getConfig().getDouble(base + "hit-range", 3.0)));
        settings.setMaxYawChangePerTick(plugin.getConfig().getDouble(difficultyPath + "yaw-per-tick",
                settings.getMaxYawChangePerTick()));
        settings.setMaxPitchChangePerTick(plugin.getConfig().getDouble(difficultyPath + "pitch-per-tick",
                settings.getMaxPitchChangePerTick()));
    }

    private Location loadLocation(DataKey key) {
        String worldName = key.getString("world", "");
        if (worldName == null || worldName.length() == 0) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        Location location = new Location(world,
                key.getDouble("x", 0.0),
                key.getDouble("y", 0.0),
                key.getDouble("z", 0.0));
        location.setYaw((float) key.getDouble("yaw", 0.0));
        location.setPitch((float) key.getDouble("pitch", 0.0));
        return location;
    }

    private void saveLocation(DataKey key, Location location) {
        if (location == null || location.getWorld() == null) {
            key.removeKey("world");
            return;
        }
        key.setString("world", location.getWorld().getName());
        key.setDouble("x", location.getX());
        key.setDouble("y", location.getY());
        key.setDouble("z", location.getZ());
        key.setDouble("yaw", location.getYaw());
        key.setDouble("pitch", location.getPitch());
    }
}
