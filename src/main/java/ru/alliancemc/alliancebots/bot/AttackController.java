package ru.alliancemc.alliancebots.bot;

import java.util.Random;
import java.lang.reflect.Method;
import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;
import ru.alliancemc.alliancebots.api.event.ClipBotAttackEvent;

public final class AttackController {
    private final ClipBotTrait bot;
    private final Random random = new Random();
    private long nextSwingTick;

    public AttackController(ClipBotTrait bot) {
        this.bot = bot;
    }

    public boolean tryAttack(Player target, long tick) {
        Entity entity = bot.getNPC().getEntity();
        if (!(entity instanceof Player) || target == null) {
            return false;
        }
        Player attacker = (Player) entity;
        if (isProtectedFightAttack(attacker, target)) {
            return false;
        }
        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        double swingRange = bot.getSettings().getSwingRange();
        if (distanceSquared > swingRange * swingRange) {
            return false;
        }
        if (tick < nextSwingTick) {
            return false;
        }
        scheduleNextSwing(tick);
        PlayerAnimation.ARM_SWING.play((Player) entity);

        double hitRange = bot.getSettings().getHitRange();
        if (distanceSquared <= hitRange * hitRange && canHit(target)) {
            ClipBotAttackEvent event = new ClipBotAttackEvent(bot, target, getAttackDamage((Player) entity));
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled() && event.getDamage() > 0.0) {
                double healthBefore = target.getHealth();
                int noDamageTicksBefore = target.getNoDamageTicks();
                double expectedFinalDamage = getExpectedFinalDamage(event.getDamage(), target);
                performPlayerAttack(attacker, target);
                boolean damaged = didDamageApply(target, healthBefore, noDamageTicksBefore);
                boolean canForceThisTick = !bot.getSettings().isRespectNoDamageTicks() || noDamageTicksBefore <= 0;
                if (!damaged && canForceThisTick && canForceFallbackDamage(attacker, target)) {
                    target.damage(event.getDamage(), attacker);
                    damaged = didDamageApply(target, healthBefore, noDamageTicksBefore);
                    debugFallbackDamage(target, event.getDamage(), damaged);
                }
                damaged = topUpWeakDamage(attacker, target, healthBefore, expectedFinalDamage, canForceThisTick) || damaged;
                if (damaged && (target.isDead() || target.getHealth() <= 0.0D)) {
                    AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
                    if (plugin != null && bot.getMode() == BotMode.FIGHT) {
                        plugin.getBuildFfaIntegration().healLikeBuildFfa(attacker);
                    }
                }
                bot.setState(BotState.ATTACK);
                return damaged;
            }
        }
        bot.setState(BotState.SWING);
        return false;
    }

    private void scheduleNextSwing(long tick) {
        int min = bot.getSettings().getCpsMin();
        int max = bot.getSettings().getCpsMax();
        int cps = min + random.nextInt(Math.max(1, max - min + 1));
        double ticks = 20.0D / cps;
        double jitter = (random.nextDouble() - 0.5D) * 0.8D;
        nextSwingTick = tick + Math.max(1L, Math.round(ticks + jitter));
    }

    private boolean canHit(Player target) {
        Entity entity = bot.getNPC().getEntity();
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        if (!target.isOnline() || target.isDead()) {
            return false;
        }
        if (!isDamageableGameMode(target)) {
            return false;
        }
        if (!target.getWorld().equals(entity.getWorld())) {
            return false;
        }
        LivingEntity attacker = (LivingEntity) entity;
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (bot.getMode() == BotMode.FIGHT && plugin != null && entity instanceof Player) {
            if (plugin.getBuildFfaIntegration().isPlayerInSpawn((Player) entity)
                    || plugin.getBuildFfaIntegration().isPlayerInSpawn(target)) {
                return false;
            }
        }
        if (bot.getSettings().isRequireLineOfSight() && !attacker.hasLineOfSight(target)) {
            return false;
        }
        return bot.getMode() == BotMode.FIGHT || isFacing(attacker, target);
    }

    private double getAttackDamage(Player attacker) {
        double configured = bot.getSettings().getDamage();
        if (bot.getMode() != BotMode.FIGHT) {
            return configured;
        }
        return Math.max(configured, getWeaponDamage(attacker.getItemInHand()));
    }

    private void performPlayerAttack(Player attacker, Player target) {
        double attackDamage = getBaseAttackDamage(attacker);
        try {
            Object attackerHandle = getHandle(attacker);
            Object targetHandle = getHandle(target);
            Method attack = findAttackMethod(attackerHandle.getClass(), targetHandle.getClass());
            if (attack != null) {
                invokeWithAttackDamage(attackerHandle, attackDamage, new Runnable() {
                    @Override
                    public void run() {
                        try {
                            attack.invoke(attackerHandle, targetHandle);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
                return;
            }
        } catch (Exception ignored) {
            // Fall back to Bukkit damage if the server implementation is not NMS-compatible.
        }
        target.damage(getAttackDamage(attacker), attacker);
    }

    private boolean didDamageApply(Player target, double healthBefore, int noDamageTicksBefore) {
        return target.isDead()
                || target.getHealth() < healthBefore;
    }

    private boolean isProtectedFightAttack(Player attacker, Player target) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        return bot.getMode() == BotMode.FIGHT && plugin != null
                && (plugin.getBuildFfaIntegration().isPlayerInSpawn(attacker)
                || plugin.getBuildFfaIntegration().isPlayerInSpawn(target));
    }

    private boolean topUpWeakDamage(Player attacker, Player target, double healthBefore, double expectedFinalDamage, boolean canForceThisTick) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || !plugin.getConfig().getBoolean("fight.damage-top-up.enabled", true)) {
            return false;
        }
        if (!canForceThisTick || !canForceFallbackDamage(attacker, target) || target.isDead()) {
            return false;
        }
        double actualDamage = Math.max(0.0D, healthBefore - target.getHealth());
        double minimumRatio = plugin.getConfig().getDouble("fight.damage-top-up.minimum-ratio", 0.85D);
        double minimumDamage = expectedFinalDamage * Math.max(0.0D, Math.min(1.0D, minimumRatio));
        if (actualDamage + 0.001D >= minimumDamage) {
            debugDamageResult(target, actualDamage, expectedFinalDamage, false);
            return actualDamage > 0.0D;
        }
        double targetHealth = Math.max(0.0D, healthBefore - expectedFinalDamage);
        if (targetHealth < target.getHealth()) {
            target.setHealth(targetHealth);
        }
        debugDamageResult(target, healthBefore - target.getHealth(), expectedFinalDamage, true);
        return target.isDead() || target.getHealth() < healthBefore;
    }

    private double getExpectedFinalDamage(double rawDamage, Player target) {
        double armorReduction = Math.min(20, getArmorPoints(target)) * 0.04D;
        return Math.max(0.0D, rawDamage * (1.0D - armorReduction));
    }

    private int getArmorPoints(Player target) {
        int points = 0;
        for (ItemStack armor : target.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) {
                continue;
            }
            points += getArmorPoints(armor.getType());
        }
        return points;
    }

    private int getArmorPoints(Material material) {
        if (material == Material.LEATHER_HELMET) {
            return 1;
        }
        if (material == Material.GOLD_HELMET || material == Material.CHAINMAIL_HELMET) {
            return 2;
        }
        if (material == Material.IRON_HELMET) {
            return 2;
        }
        if (material == Material.DIAMOND_HELMET) {
            return 3;
        }
        if (material == Material.LEATHER_CHESTPLATE) {
            return 3;
        }
        if (material == Material.GOLD_CHESTPLATE || material == Material.CHAINMAIL_CHESTPLATE) {
            return 5;
        }
        if (material == Material.IRON_CHESTPLATE) {
            return 6;
        }
        if (material == Material.DIAMOND_CHESTPLATE) {
            return 8;
        }
        if (material == Material.LEATHER_LEGGINGS) {
            return 2;
        }
        if (material == Material.GOLD_LEGGINGS) {
            return 3;
        }
        if (material == Material.CHAINMAIL_LEGGINGS) {
            return 4;
        }
        if (material == Material.IRON_LEGGINGS) {
            return 5;
        }
        if (material == Material.DIAMOND_LEGGINGS) {
            return 6;
        }
        if (material == Material.LEATHER_BOOTS || material == Material.GOLD_BOOTS
                || material == Material.CHAINMAIL_BOOTS) {
            return 1;
        }
        if (material == Material.IRON_BOOTS) {
            return 2;
        }
        if (material == Material.DIAMOND_BOOTS) {
            return 3;
        }
        return 0;
    }

    private boolean canForceFallbackDamage(Player attacker, Player target) {
        if (attacker == null || target == null || target.isDead() || !target.isOnline()) {
            return false;
        }
        if (!isDamageableGameMode(target)) {
            return false;
        }
        if (!attacker.getWorld().equals(target.getWorld())) {
            return false;
        }
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        return bot.getMode() != BotMode.FIGHT || plugin == null
                || (!plugin.getBuildFfaIntegration().isPlayerInSpawn(attacker)
                && !plugin.getBuildFfaIntegration().isPlayerInSpawn(target));
    }

    private boolean isDamageableGameMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void debugFallbackDamage(Player target, double damage, boolean damaged) {
        if (!bot.isDebug()) {
            return;
        }
        AllianceBotsPlugin.getInstance().getLogger().info("debug " + bot.getNPC().getName()
                + " forcedFallbackDamage target=" + target.getName()
                + " rawDamage=" + damage
                + " applied=" + damaged
                + " targetHealth=" + target.getHealth()
                + " targetNoDamageTicks=" + target.getNoDamageTicks());
    }

    private void debugDamageResult(Player target, double actualDamage, double expectedFinalDamage, boolean toppedUp) {
        if (!bot.isDebug()) {
            return;
        }
        AllianceBotsPlugin.getInstance().getLogger().info("debug " + bot.getNPC().getName()
                + " damageResult target=" + target.getName()
                + " actualFinalDamage=" + actualDamage
                + " expectedFinalDamage=" + expectedFinalDamage
                + " toppedUp=" + toppedUp
                + " targetHealth=" + target.getHealth()
                + " targetNoDamageTicks=" + target.getNoDamageTicks());
    }

    private Object getHandle(Player player) throws Exception {
        return player.getClass().getMethod("getHandle").invoke(player);
    }

    private Method findAttackMethod(Class<?> attackerClass, Class<?> targetClass) {
        for (Method method : attackerClass.getMethods()) {
            if (!"attack".equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(targetClass)) {
                return method;
            }
        }
        return null;
    }

    private void invokeWithAttackDamage(Object attackerHandle, double attackDamage, Runnable attack) {
        Object attribute = null;
        double previousValue = 1.0D;
        boolean restore = false;
        try {
            Class<?> nmsClass = attackerHandle.getClass();
            String packageName = nmsClass.getPackage().getName();
            Class<?> genericAttributes = Class.forName(packageName + ".GenericAttributes");
            Object attackAttribute = genericAttributes.getField("ATTACK_DAMAGE").get(null);
            Method getAttributeInstance = findGetAttributeInstanceMethod(nmsClass, attackAttribute.getClass());
            if (getAttributeInstance != null) {
                attribute = getAttributeInstance.invoke(attackerHandle, attackAttribute);
                if (attribute != null) {
                    Method getValue = attribute.getClass().getMethod("getValue");
                    Method setValue = attribute.getClass().getMethod("setValue", double.class);
                    previousValue = ((Number) getValue.invoke(attribute)).doubleValue();
                    setValue.invoke(attribute, attackDamage);
                    restore = true;
                }
            }
            attack.run();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ignored) {
            attack.run();
        } finally {
            if (restore && attribute != null) {
                try {
                    attribute.getClass().getMethod("setValue", double.class).invoke(attribute, previousValue);
                } catch (Exception ignored) {
                    // Attribute restore is best-effort; the next swing will set it again.
                }
            }
        }
    }

    private Method findGetAttributeInstanceMethod(Class<?> attackerClass, Class<?> attributeClass) {
        for (Method method : attackerClass.getMethods()) {
            if (!"getAttributeInstance".equals(method.getName()) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(attributeClass)) {
                return method;
            }
        }
        return null;
    }

    private double getBaseAttackDamage(Player attacker) {
        double configured = bot.getSettings().getDamage();
        if (bot.getMode() != BotMode.FIGHT) {
            return configured;
        }
        return Math.max(configured, getBaseWeaponDamage(attacker.getItemInHand()));
    }

    private double getWeaponDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 1.0D;
        }
        double damage = getBaseWeaponDamage(item);
        int sharpness = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        if (sharpness > 0) {
            damage += 1.25D * sharpness;
        }
        return damage;
    }

    private double getBaseWeaponDamage(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 1.0D;
        }
        Material type = item.getType();
        if (type == Material.DIAMOND_SWORD) {
            return 7.0D;
        }
        if (type == Material.IRON_SWORD) {
            return 6.0D;
        }
        if (type == Material.STONE_SWORD) {
            return 5.0D;
        }
        if (type == Material.WOOD_SWORD || type == Material.GOLD_SWORD) {
            return 4.0D;
        }
        return 1.0D;
    }

    private boolean isFacing(LivingEntity attacker, Player target) {
        Vector direction = attacker.getEyeLocation().getDirection().normalize();
        Vector toTarget = target.getLocation().clone().add(0.0, target.getEyeHeight() * 0.65, 0.0).toVector()
                .subtract(attacker.getEyeLocation().toVector());
        if (toTarget.lengthSquared() < 0.0001) {
            return true;
        }
        double dot = direction.dot(toTarget.normalize());
        double minDot = Math.cos(Math.toRadians(bot.getSettings().getMaxAngle()));
        return dot >= minDot;
    }
}
