package ru.alliancemc.alliancebots.listener;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDamageByEntityEvent;
import net.citizensnpcs.api.event.NPCDamageEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;
import ru.alliancemc.alliancebots.bot.BotMode;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;

public final class ClipBotListener implements Listener {
    private final AllianceBotsPlugin plugin;

    public ClipBotListener(AllianceBotsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNPCDamageByEntity(NPCDamageByEntityEvent event) {
        if (!event.getNPC().hasTrait(ClipBotTrait.class)) {
            return;
        }
        ClipBotTrait bot = event.getNPC().getTrait(ClipBotTrait.class);
        if (shouldCancelFightDamage(event, bot)) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            return;
        }
        bot.handleDamage(event.getDamager());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNPCDamage(NPCDamageEvent event) {
        if (!event.getNPC().hasTrait(ClipBotTrait.class)) {
            return;
        }
        ClipBotTrait bot = event.getNPC().getTrait(ClipBotTrait.class);
        if (shouldCancelFightDamage(event, bot)) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            return;
        }
        if (bot.getMode() == BotMode.FIGHT && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            if (event.getNPC().isSpawned() && event.getNPC().getEntity() != null) {
                event.getNPC().getEntity().setFallDistance(0.0F);
            }
            return;
        }
        if (event instanceof NPCDamageByEntityEvent) {
            bot.handleDamage(((NPCDamageByEntityEvent) event).getDamager());
        }
        double finalDamage = event.getDamage();
        if (event.getEvent() != null) {
            finalDamage = event.getEvent().getFinalDamage();
        }
        if (bot.handleFatalDamage(finalDamage)) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            return;
        }
        if (bot.getSettings().isInvulnerable()) {
            if (event.getNPC().isSpawned() && event.getNPC().getEntity() instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) event.getNPC().getEntity();
                double health = living.getHealth();
                if (living.getHealth() <= 1.0D) {
                    living.setHealth(Math.min(living.getMaxHealth(), 20.0D));
                    health = living.getHealth();
                }
                if (event.getDamage() >= health) {
                    event.setDamage(Math.max(0.0D, health - 1.0D));
                }
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (!living.isDead()) {
                            living.setHealth(Math.min(living.getMaxHealth(), 20.0D));
                        }
                    }
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBotEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(player);
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            return;
        }
        ClipBotTrait bot = npc.getTrait(ClipBotTrait.class);
        if (bot.getMode() != BotMode.FIGHT) {
            return;
        }
        if (shouldCancelFightDamage(event, bot)) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            player.setFallDistance(0.0F);
            return;
        }
        Entity damager = getDamageSource(event);
        if (damager != null) {
            bot.handleDamage(damager);
        }
        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0.0D || player.getHealth() - finalDamage > 0.0D) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0D);
        player.setHealth(Math.max(1.0D, Math.min(player.getMaxHealth(), player.getHealth())));
        if (bot.handleFatalDamage(finalDamage)) {
            player.setFallDistance(0.0F);
            player.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNPCPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(player);
        if (npc == null || !npc.hasTrait(ClipBotTrait.class)) {
            return;
        }
        ClipBotTrait bot = npc.getTrait(ClipBotTrait.class);
        if (bot.getMode() != BotMode.FIGHT) {
            return;
        }
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        bot.handleVanillaDeath(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        scheduleTabVisibilitySync(event.getPlayer(), 5L);
        scheduleTabVisibilitySync(event.getPlayer(), 25L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        scheduleTabVisibilitySync(event.getPlayer(), 1L);
        scheduleTabVisibilitySync(event.getPlayer(), 10L);
    }

    private void scheduleTabVisibilitySync(final Player player, long delay) {
        if (player == null || CitizensAPI.getNPCRegistry().isNPC(player)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.getBuildFfaIntegration().syncTabVisibilityForViewer(player);
            }
        }, delay);
    }

    private boolean shouldCancelFightDamage(NPCDamageEvent event, ClipBotTrait bot) {
        if (bot.getMode() != BotMode.FIGHT || !event.getNPC().isSpawned()
                || !(event.getNPC().getEntity() instanceof Player)) {
            return false;
        }
        Player botPlayer = (Player) event.getNPC().getEntity();
        if (plugin.getBuildFfaIntegration().isPlayerInSpawn(botPlayer)) {
            return true;
        }
        if (plugin.getConfig().getBoolean("fight.health.cancel-bedwars-fireball-damage", true)
                && isExternalFireballDamage(event)) {
            return true;
        }
        if (plugin.getConfig().getBoolean("fight.health.cancel-incoming-during-no-damage-ticks", false)
                && botPlayer.getNoDamageTicks() > 0
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return true;
        }
        if (event instanceof NPCDamageByEntityEvent) {
            NPCDamageByEntityEvent byEntity = (NPCDamageByEntityEvent) event;
            if (byEntity.getDamager() instanceof Player
                    && plugin.getBuildFfaIntegration().isPlayerInSpawn((Player) byEntity.getDamager())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldCancelFightDamage(EntityDamageEvent event, ClipBotTrait bot) {
        if (bot.getMode() != BotMode.FIGHT || bot.getNPC() == null || !bot.getNPC().isSpawned()
                || !(bot.getNPC().getEntity() instanceof Player)) {
            return false;
        }
        Player botPlayer = (Player) bot.getNPC().getEntity();
        if (plugin.getBuildFfaIntegration().isPlayerInSpawn(botPlayer)) {
            return true;
        }
        return plugin.getConfig().getBoolean("fight.health.cancel-bedwars-fireball-damage", true)
                && isExternalFireballDamage(event);
    }

    private Entity getDamageSource(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent)) {
            return null;
        }
        Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
        if (damager instanceof Projectile) {
            try {
                Object shooter = ((Projectile) damager).getShooter();
                if (shooter instanceof Entity) {
                    return (Entity) shooter;
                }
            } catch (Exception ignored) {
                return damager;
            }
        }
        return damager;
    }

    private boolean isExternalFireballDamage(NPCDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (event instanceof NPCDamageByEntityEvent) {
            NPCDamageByEntityEvent byEntity = (NPCDamageByEntityEvent) event;
            if (byEntity.getDamager() instanceof Fireball) {
                return true;
            }
        }
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.PROJECTILE
                || cause == EntityDamageEvent.DamageCause.CUSTOM;
    }

    private boolean isExternalFireballDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.PROJECTILE
                || cause == EntityDamageEvent.DamageCause.CUSTOM;
    }
}
