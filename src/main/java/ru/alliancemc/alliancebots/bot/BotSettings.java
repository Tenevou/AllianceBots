package ru.alliancemc.alliancebots.bot;

import net.citizensnpcs.api.util.DataKey;
import org.bukkit.configuration.file.FileConfiguration;

public final class BotSettings {
    private double detectRange;
    private double swingRange;
    private double hitRange;
    private double movementSpeed;
    private double damage;
    private int cpsMin;
    private int cpsMax;
    private boolean smoothRotation;
    private double maxYawChangePerTick;
    private double maxPitchChangePerTick;
    private boolean requireLineOfSight;
    private double maxAngle;
    private boolean respectNoDamageTicks;
    private boolean knockbackEnabled;
    private String knockbackMode;
    private double knockbackHorizontal;
    private double knockbackVertical;
    private double knockbackExtraHorizontal;
    private double knockbackMaxVertical;
    private int navigationPauseTicks;
    private boolean sprint;
    private boolean invulnerable;
    private double stopDistance;
    private int targetLostTimeoutTicks;
    private double preferredDistance;
    private double tooCloseDistance;
    private double maximumHealth;
    private boolean autoRespawn;
    private int respawnDelayTicks;

    public static BotSettings fromConfig(FileConfiguration config) {
        BotSettings settings = new BotSettings();
        settings.detectRange = config.getDouble("defaults.detect-range", 12.0);
        settings.swingRange = config.getDouble("defaults.swing-range", 4.0);
        settings.hitRange = config.getDouble("defaults.hit-range", 2.0);
        settings.movementSpeed = config.getDouble("defaults.movement-speed", 1.0);
        settings.damage = config.getDouble("defaults.damage", 1.0);
        settings.cpsMin = config.getInt("defaults.cps.min", 8);
        settings.cpsMax = config.getInt("defaults.cps.max", 12);
        settings.smoothRotation = config.getBoolean("defaults.rotation.smooth", true);
        settings.maxYawChangePerTick = config.getDouble("defaults.rotation.max-yaw-change-per-tick", 25.0);
        settings.maxPitchChangePerTick = config.getDouble("defaults.rotation.max-pitch-change-per-tick", 18.0);
        settings.requireLineOfSight = config.getBoolean("defaults.attack.require-line-of-sight", true);
        settings.maxAngle = config.getDouble("defaults.attack.max-angle", 45.0);
        settings.respectNoDamageTicks = config.getBoolean("defaults.attack.respect-no-damage-ticks", true);
        settings.knockbackEnabled = config.getBoolean("defaults.knockback.enabled", true);
        settings.knockbackMode = config.getString("defaults.knockback.mode", "VANILLA");
        settings.knockbackHorizontal = config.getDouble("defaults.knockback.horizontal", 0.53);
        settings.knockbackVertical = config.getDouble("defaults.knockback.vertical", 0.3622);
        settings.knockbackExtraHorizontal = config.getDouble("defaults.knockback.extra-horizontal", 0.339);
        settings.knockbackMaxVertical = config.getDouble("defaults.knockback.max-vertical", 0.4);
        settings.navigationPauseTicks = config.getInt("defaults.knockback.navigation-pause-ticks", 5);
        settings.sprint = config.getBoolean("defaults.bot.sprint", true);
        settings.invulnerable = config.getBoolean("defaults.bot.invulnerable", true);
        settings.stopDistance = config.getDouble("defaults.bot.stop-distance", 1.2);
        settings.targetLostTimeoutTicks = config.getInt("defaults.bot.target-lost-timeout-ticks", 100);
        settings.preferredDistance = config.getDouble("fight.preferred-distance", 2.4);
        settings.tooCloseDistance = config.getDouble("fight.too-close-distance", 1.2);
        settings.maximumHealth = config.getDouble("fight.health.maximum", 20.0);
        settings.autoRespawn = config.getBoolean("fight.health.auto-respawn", true);
        settings.respawnDelayTicks = config.getInt("fight.health.respawn-delay-ticks", 40);
        settings.normalize();
        return settings;
    }

    public BotSettings copy() {
        BotSettings settings = new BotSettings();
        settings.detectRange = detectRange;
        settings.swingRange = swingRange;
        settings.hitRange = hitRange;
        settings.movementSpeed = movementSpeed;
        settings.damage = damage;
        settings.cpsMin = cpsMin;
        settings.cpsMax = cpsMax;
        settings.smoothRotation = smoothRotation;
        settings.maxYawChangePerTick = maxYawChangePerTick;
        settings.maxPitchChangePerTick = maxPitchChangePerTick;
        settings.requireLineOfSight = requireLineOfSight;
        settings.maxAngle = maxAngle;
        settings.respectNoDamageTicks = respectNoDamageTicks;
        settings.knockbackEnabled = knockbackEnabled;
        settings.knockbackMode = knockbackMode;
        settings.knockbackHorizontal = knockbackHorizontal;
        settings.knockbackVertical = knockbackVertical;
        settings.knockbackExtraHorizontal = knockbackExtraHorizontal;
        settings.knockbackMaxVertical = knockbackMaxVertical;
        settings.navigationPauseTicks = navigationPauseTicks;
        settings.sprint = sprint;
        settings.invulnerable = invulnerable;
        settings.stopDistance = stopDistance;
        settings.targetLostTimeoutTicks = targetLostTimeoutTicks;
        settings.preferredDistance = preferredDistance;
        settings.tooCloseDistance = tooCloseDistance;
        settings.maximumHealth = maximumHealth;
        settings.autoRespawn = autoRespawn;
        settings.respawnDelayTicks = respawnDelayTicks;
        return settings;
    }

    public void load(DataKey key, BotSettings defaults) {
        detectRange = key.getDouble("detect-range", defaults.detectRange);
        swingRange = key.getDouble("swing-range", defaults.swingRange);
        hitRange = key.getDouble("hit-range", defaults.hitRange);
        movementSpeed = key.getDouble("movement-speed", defaults.movementSpeed);
        damage = key.getDouble("damage", defaults.damage);
        cpsMin = key.getInt("cps-min", defaults.cpsMin);
        cpsMax = key.getInt("cps-max", defaults.cpsMax);
        smoothRotation = key.getBoolean("rotation-smooth", defaults.smoothRotation);
        maxYawChangePerTick = key.getDouble("max-yaw-change-per-tick", defaults.maxYawChangePerTick);
        maxPitchChangePerTick = key.getDouble("max-pitch-change-per-tick", defaults.maxPitchChangePerTick);
        requireLineOfSight = key.getBoolean("require-line-of-sight", defaults.requireLineOfSight);
        maxAngle = key.getDouble("max-angle", defaults.maxAngle);
        respectNoDamageTicks = key.getBoolean("respect-no-damage-ticks", defaults.respectNoDamageTicks);
        knockbackEnabled = key.getBoolean("knockback-enabled", defaults.knockbackEnabled);
        knockbackMode = key.getString("knockback-mode", defaults.knockbackMode);
        knockbackHorizontal = key.getDouble("knockback-horizontal", defaults.knockbackHorizontal);
        knockbackVertical = key.getDouble("knockback-vertical", defaults.knockbackVertical);
        knockbackExtraHorizontal = key.getDouble("knockback-extra-horizontal", defaults.knockbackExtraHorizontal);
        knockbackMaxVertical = key.getDouble("knockback-max-vertical", defaults.knockbackMaxVertical);
        navigationPauseTicks = key.getInt("navigation-pause-ticks", defaults.navigationPauseTicks);
        sprint = key.getBoolean("sprint", defaults.sprint);
        invulnerable = key.getBoolean("invulnerable", defaults.invulnerable);
        stopDistance = key.getDouble("stop-distance", defaults.stopDistance);
        targetLostTimeoutTicks = key.getInt("target-lost-timeout-ticks", defaults.targetLostTimeoutTicks);
        preferredDistance = key.getDouble("preferred-distance", defaults.preferredDistance);
        tooCloseDistance = key.getDouble("too-close-distance", defaults.tooCloseDistance);
        maximumHealth = key.getDouble("maximum-health", defaults.maximumHealth);
        autoRespawn = key.getBoolean("auto-respawn", defaults.autoRespawn);
        respawnDelayTicks = key.getInt("respawn-delay-ticks", defaults.respawnDelayTicks);
        normalize();
    }

    public void save(DataKey key) {
        key.setDouble("detect-range", detectRange);
        key.setDouble("swing-range", swingRange);
        key.setDouble("hit-range", hitRange);
        key.setDouble("movement-speed", movementSpeed);
        key.setDouble("damage", damage);
        key.setInt("cps-min", cpsMin);
        key.setInt("cps-max", cpsMax);
        key.setBoolean("rotation-smooth", smoothRotation);
        key.setDouble("max-yaw-change-per-tick", maxYawChangePerTick);
        key.setDouble("max-pitch-change-per-tick", maxPitchChangePerTick);
        key.setBoolean("require-line-of-sight", requireLineOfSight);
        key.setDouble("max-angle", maxAngle);
        key.setBoolean("respect-no-damage-ticks", respectNoDamageTicks);
        key.setBoolean("knockback-enabled", knockbackEnabled);
        key.setString("knockback-mode", knockbackMode);
        key.setDouble("knockback-horizontal", knockbackHorizontal);
        key.setDouble("knockback-vertical", knockbackVertical);
        key.setDouble("knockback-extra-horizontal", knockbackExtraHorizontal);
        key.setDouble("knockback-max-vertical", knockbackMaxVertical);
        key.setInt("navigation-pause-ticks", navigationPauseTicks);
        key.setBoolean("sprint", sprint);
        key.setBoolean("invulnerable", invulnerable);
        key.setDouble("stop-distance", stopDistance);
        key.setInt("target-lost-timeout-ticks", targetLostTimeoutTicks);
        key.setDouble("preferred-distance", preferredDistance);
        key.setDouble("too-close-distance", tooCloseDistance);
        key.setDouble("maximum-health", maximumHealth);
        key.setBoolean("auto-respawn", autoRespawn);
        key.setInt("respawn-delay-ticks", respawnDelayTicks);
    }

    public void normalize() {
        detectRange = Math.max(1.0, detectRange);
        swingRange = Math.max(0.1, swingRange);
        hitRange = Math.max(0.1, Math.min(hitRange, swingRange));
        movementSpeed = Math.max(0.05, movementSpeed);
        damage = Math.max(0.0, damage);
        cpsMin = Math.max(1, cpsMin);
        cpsMax = Math.max(cpsMin, cpsMax);
        maxYawChangePerTick = Math.max(1.0, maxYawChangePerTick);
        maxPitchChangePerTick = Math.max(1.0, maxPitchChangePerTick);
        maxAngle = Math.max(1.0, Math.min(180.0, maxAngle));
        if (knockbackMode == null || (!"VANILLA".equalsIgnoreCase(knockbackMode) && !"CUSTOM".equalsIgnoreCase(knockbackMode))) {
            knockbackMode = "VANILLA";
        }
        knockbackMode = knockbackMode.toUpperCase(java.util.Locale.ENGLISH);
        navigationPauseTicks = Math.max(0, navigationPauseTicks);
        knockbackHorizontal = Math.max(0.0, knockbackHorizontal);
        knockbackVertical = Math.max(0.0, knockbackVertical);
        knockbackExtraHorizontal = Math.max(0.0, knockbackExtraHorizontal);
        knockbackMaxVertical = Math.max(0.0, knockbackMaxVertical);
        stopDistance = Math.max(0.1, stopDistance);
        targetLostTimeoutTicks = Math.max(1, targetLostTimeoutTicks);
        preferredDistance = Math.max(0.1, preferredDistance);
        tooCloseDistance = Math.max(0.1, tooCloseDistance);
        maximumHealth = Math.max(1.0, maximumHealth);
        respawnDelayTicks = Math.max(1, respawnDelayTicks);
    }

    public double getDetectRange() { return detectRange; }
    public void setDetectRange(double detectRange) { this.detectRange = detectRange; normalize(); }
    public double getSwingRange() { return swingRange; }
    public void setSwingRange(double swingRange) { this.swingRange = swingRange; normalize(); }
    public double getHitRange() { return hitRange; }
    public void setHitRange(double hitRange) { this.hitRange = hitRange; normalize(); }
    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; normalize(); }
    public double getDamage() { return damage; }
    public void setDamage(double damage) { this.damage = damage; normalize(); }
    public int getCpsMin() { return cpsMin; }
    public void setCpsMin(int cpsMin) { this.cpsMin = cpsMin; normalize(); }
    public int getCpsMax() { return cpsMax; }
    public void setCpsMax(int cpsMax) { this.cpsMax = cpsMax; normalize(); }
    public boolean isSmoothRotation() { return smoothRotation; }
    public double getMaxYawChangePerTick() { return maxYawChangePerTick; }
    public void setMaxYawChangePerTick(double maxYawChangePerTick) { this.maxYawChangePerTick = maxYawChangePerTick; normalize(); }
    public double getMaxPitchChangePerTick() { return maxPitchChangePerTick; }
    public void setMaxPitchChangePerTick(double maxPitchChangePerTick) { this.maxPitchChangePerTick = maxPitchChangePerTick; normalize(); }
    public boolean isRequireLineOfSight() { return requireLineOfSight; }
    public double getMaxAngle() { return maxAngle; }
    public boolean isRespectNoDamageTicks() { return respectNoDamageTicks; }
    public boolean isKnockbackEnabled() { return knockbackEnabled; }
    public String getKnockbackMode() { return knockbackMode; }
    public void setKnockbackMode(String knockbackMode) { this.knockbackMode = knockbackMode; normalize(); }
    public boolean isCustomKnockback() { return knockbackEnabled && "CUSTOM".equalsIgnoreCase(knockbackMode); }
    public double getKnockbackHorizontal() { return knockbackHorizontal; }
    public void setKnockbackHorizontal(double knockbackHorizontal) { this.knockbackHorizontal = Math.max(0.0, knockbackHorizontal); }
    public double getKnockbackVertical() { return knockbackVertical; }
    public void setKnockbackVertical(double knockbackVertical) { this.knockbackVertical = Math.max(0.0, knockbackVertical); }
    public double getKnockbackExtraHorizontal() { return knockbackExtraHorizontal; }
    public void setKnockbackExtraHorizontal(double knockbackExtraHorizontal) { this.knockbackExtraHorizontal = Math.max(0.0, knockbackExtraHorizontal); }
    public double getKnockbackMaxVertical() { return knockbackMaxVertical; }
    public void setKnockbackMaxVertical(double knockbackMaxVertical) { this.knockbackMaxVertical = Math.max(0.0, knockbackMaxVertical); }
    public int getNavigationPauseTicks() { return navigationPauseTicks; }
    public boolean isSprint() { return sprint; }
    public boolean isInvulnerable() { return invulnerable; }
    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
    public double getStopDistance() { return stopDistance; }
    public void setStopDistance(double stopDistance) { this.stopDistance = stopDistance; normalize(); }
    public int getTargetLostTimeoutTicks() { return targetLostTimeoutTicks; }
    public double getPreferredDistance() { return preferredDistance; }
    public void setPreferredDistance(double preferredDistance) { this.preferredDistance = preferredDistance; normalize(); }
    public double getTooCloseDistance() { return tooCloseDistance; }
    public void setTooCloseDistance(double tooCloseDistance) { this.tooCloseDistance = tooCloseDistance; normalize(); }
    public double getMaximumHealth() { return maximumHealth; }
    public void setMaximumHealth(double maximumHealth) { this.maximumHealth = maximumHealth; normalize(); }
    public boolean isAutoRespawn() { return autoRespawn; }
    public int getRespawnDelayTicks() { return respawnDelayTicks; }
}
