package ru.alliancemc.alliancebots.bot;

import java.util.Random;
import java.util.Locale;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import ru.alliancemc.alliancebots.AllianceBotsPlugin;

public final class FightBotController {
    private final ClipBotTrait bot;
    private final RotationController rotationController;
    private final AttackController attackController;
    private final KnockbackController knockbackController;
    private final Random random = new Random();
    private int retargetCooldown;
    private int pathRetargetCooldown;
    private int targetRefreshTicks;
    private int strafeTicks;
    private int strafeDirection = 1;
    private int jumpCooldown;
    private int tapReleaseTicks;
    private int tapResumeTicks;
    private int tapCooldownTicks;
    private int hitSelectWaitTicks;
    private int hitSelectCooldownTicks;
    private int lastReleaseTicks;
    private boolean forwardMovementEnabled = true;
    private boolean sprintState = true;
    private boolean sTapActive;

    public FightBotController(ClipBotTrait bot) {
        this.bot = bot;
        this.rotationController = new RotationController(bot);
        this.attackController = new AttackController(bot);
        this.knockbackController = new KnockbackController(bot);
    }

    public void tick(long tick) {
        if (!bot.isRunning() || bot.getNPC() == null || !bot.getNPC().isSpawned()) {
            return;
        }
        if (bot.getState() == BotState.KNOCKBACK) {
            cancelTap();
            if (knockbackController.tickPause()) {
                bot.setState(BotState.CHASE);
            } else {
                return;
            }
        }
        if (bot.isInitialSpawnExitActive()) {
            Entity entity = bot.getNPC().getEntity();
            if (entity != null) {
                moveFromSpawn(entity, null);
            }
            bot.tickInitialSpawnExit();
            return;
        }

        Player target = refreshTarget();
        if (target == null && !bot.isTargetLocked()) {
            target = findNearestTarget();
        }
        if (target != null && !target.getUniqueId().equals(bot.getTargetUuid())) {
            bot.setTarget(target);
        }
        if (target == null) {
            Entity entity = bot.getNPC().getEntity();
            if (moveFromSpawn(entity, null)) {
                return;
            }
            stopNavigation();
            cancelTap();
            bot.setState(BotState.IDLE);
            return;
        }

        rotationController.lookAt(target);
        boolean tapMovementActive = tickTapMovement(target);
        if (!tapMovementActive) {
            move(target);
        }
        if (shouldDelayForHitSelect(target)) {
            return;
        }
        boolean successfulHit = attackController.tryAttack(target, tick);
        if (successfulHit) {
            maybeStartTap(target);
        }
        debugTap(successfulHit, bot.getState() == BotState.W_TAP_RELEASE);
    }

    public void enterKnockback(Entity damager) {
        cancelTap();
        if (!bot.isTargetLocked() && damager instanceof Player && isValidTarget((Player) damager)) {
            bot.setTarget((Player) damager);
        }
        knockbackController.apply(damager);
    }

    public void stop() {
        stopNavigation();
    }

    private Player getTarget() {
        if (bot.getTargetUuid() == null || bot.getNPC().getEntity() == null) {
            return null;
        }
        Player target = bot.getNPC().getEntity().getServer().getPlayer(bot.getTargetUuid());
        if (!isValidTarget(target, getTargetSearchRange(bot.getNPC().getEntity()), bot.isTargetLocked())) {
            return null;
        }
        return target;
    }

    private Player refreshTarget() {
        Player current = getTarget();
        if (bot.isTargetLocked()) {
            return current;
        }
        if (targetRefreshTicks > 0) {
            targetRefreshTicks--;
            return current;
        }
        targetRefreshTicks = 10;
        Player nearest = findNearestTarget();
        if (nearest == null) {
            return current;
        }
        if (current == null) {
            return nearest;
        }
        Entity entity = bot.getNPC().getEntity();
        if (entity == null) {
            return nearest;
        }
        double currentDistance = entity.getLocation().distanceSquared(current.getLocation());
        double nearestDistance = entity.getLocation().distanceSquared(nearest.getLocation());
        if (nearestDistance + 4.0D < currentDistance) {
            return nearest;
        }
        return current;
    }

    private Player findNearestTarget() {
        Entity entity = bot.getNPC().getEntity();
        if (entity == null) {
            return null;
        }
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin != null && !plugin.getBuildFfaIntegration().isFfaWorld(entity.getWorld())) {
            return null;
        }
        double maxDistance = getTargetSearchRange(entity);
        double bestDistance = maxDistance * maxDistance;
        double bestPlatformDistance = bestDistance;
        Player best = null;
        Player bestPlatform = null;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!isValidTarget(player, maxDistance)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            boolean platformTarget = plugin != null && plugin.getBuildFfaIntegration().isPlayerInSpawn(player);
            if (platformTarget) {
                if (distance < bestPlatformDistance) {
                    bestPlatformDistance = distance;
                    bestPlatform = player;
                }
            } else if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best == null ? bestPlatform : best;
    }

    private boolean isValidTarget(Player player) {
        return isValidTarget(player, getTargetSearchRange(bot.getNPC() == null ? null : bot.getNPC().getEntity()));
    }

    private boolean isValidTarget(Player player, double maxDistance) {
        return isValidTarget(player, maxDistance, false);
    }

    private boolean isValidTarget(Player player, double maxDistance, boolean ignoreDistance) {
        Entity entity = bot.getNPC().getEntity();
        if (player == null || entity == null || player.isDead()) {
            return false;
        }
        boolean npcTarget = CitizensAPI.getNPCRegistry().isNPC(player);
        if (!npcTarget && !player.isOnline()) {
            return false;
        }
        if (!isDamageableGameMode(player)) {
            return false;
        }
        if (npcTarget) {
            NPC targetNpc = CitizensAPI.getNPCRegistry().getNPC(player);
            if (!bot.isAttackOtherBots() || targetNpc == null || targetNpc == bot.getNPC()
                    || !targetNpc.hasTrait(ClipBotTrait.class)) {
                return false;
            }
            ClipBotTrait targetBot = targetNpc.getTrait(ClipBotTrait.class);
            if (targetBot == null || targetBot.getMode() != BotMode.FIGHT || !targetBot.isRunning()) {
                return false;
            }
        }
        if (!player.getWorld().equals(entity.getWorld())) {
            return false;
        }
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin != null) {
            if (!npcTarget && plugin.getBuildFfaIntegration().isVanished(player)) {
                return false;
            }
            if (npcTarget && plugin.getConfig().getBoolean("fight.bot-vs-bot.ignore-spawn-bots-until-exit", true)
                    && plugin.getBuildFfaIntegration().isPlayerInSpawn(player)) {
                return false;
            }
            if (plugin.getBuildFfaIntegration().isPlayerInSpawn(player)
                    && !canChaseSpawnPlatformTarget(entity, player, plugin)) {
                return false;
            }
        }
        return ignoreDistance || entity.getLocation().distanceSquared(player.getLocation()) <= maxDistance * maxDistance;
    }

    private boolean isDamageableGameMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private double getTargetSearchRange(Entity entity) {
        double range = bot.getSettings().getDetectRange();
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin != null && entity instanceof Player
                && plugin.getBuildFfaIntegration().isPlayerInSpawn((Player) entity)) {
            range = Math.max(range, plugin.getConfig().getDouble("fight.spawn-detect-range", 96.0D));
        }
        if (plugin != null && plugin.getConfig().getBoolean("fight.navigation.chase-spawn-platform-targets", true)) {
            range = Math.max(range, plugin.getConfig().getDouble("fight.navigation.platform-target-detect-range", 96.0D));
        }
        return range;
    }

    private void move(Player target) {
        Entity entity = bot.getNPC().getEntity();
        if (entity == null) {
            return;
        }
        if (moveFromSpawn(entity, target)) {
            return;
        }
        double distance = entity.getLocation().distance(target.getLocation());
        Navigator navigator = bot.getNPC().getNavigator();

        double tooCloseDistance = Math.min(bot.getSettings().getTooCloseDistance(), 0.85D);
        double preferredDistance = Math.min(bot.getSettings().getPreferredDistance(), 1.65D);
        configureNavigator(navigator, preferredDistance);
        boolean blockedLineOfSight = entity instanceof Player && !hasClearPathSight((Player) entity, target);

        if (distance <= tooCloseDistance) {
            navigator.cancelNavigation();
            Vector away = entity.getLocation().toVector().subtract(target.getLocation().toVector());
            away.setY(0.0);
            if (away.lengthSquared() > 0.0001) {
                Vector velocity = entity.getVelocity();
                Vector horizontal = away.normalize().multiply(0.08D);
                horizontal.setY(velocity.getY());
                entity.setVelocity(horizontal);
            }
            bot.setState(BotState.CHASE);
        } else if (distance > preferredDistance) {
            if (shouldUsePathfinder(entity, target, blockedLineOfSight)) {
                if (pathRetargetCooldown-- <= 0 || !navigator.isNavigating()) {
                    navigator.setPaused(false);
                    navigator.setTarget(target, false);
                    pathRetargetCooldown = AllianceBotsPlugin.getInstance().getConfig()
                            .getInt("fight.navigation.path-retarget-ticks", 6);
                }
            } else if (retargetCooldown-- <= 0 || !navigator.isNavigating()) {
                navigator.setPaused(false);
                navigator.setStraightLineTarget(target, false);
                retargetCooldown = 8;
                pathRetargetCooldown = 0;
            }
            bot.setState(BotState.SPRINTING);
        } else {
            navigator.cancelNavigation();
        }

        if (!blockedLineOfSight) {
            applyStrafe(target);
        }
        maybeJump(entity, target, distance);
        if (entity instanceof Player) {
            setSprinting((Player) entity, true);
        }
    }

    private boolean moveFromSpawn(Entity entity, Player target) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || !(entity instanceof Player)
                || !plugin.getBuildFfaIntegration().isPlayerInSpawn((Player) entity)) {
            return false;
        }
        Navigator navigator = bot.getNPC().getNavigator();
        if (target != null && !shouldIgnoreSpawnBotTarget(entity, target, plugin) && shouldUseSpawnPathfinder(entity, target)) {
            configureNavigator(navigator, Math.min(bot.getSettings().getPreferredDistance(), 1.65D));
            if (pathRetargetCooldown-- <= 0 || !navigator.isNavigating()) {
                navigator.cancelNavigation();
                navigator.setPaused(false);
                navigator.setTarget(target, false);
                pathRetargetCooldown = plugin.getConfig().getInt("fight.navigation.path-retarget-ticks", 6);
            }
            setSprinting((Player) entity, true);
            bot.setState(BotState.SPRINTING);
            return true;
        }
        navigator.cancelNavigation();
        navigator.setPaused(false);

        Vector direction;
        if (target != null && !shouldIgnoreSpawnBotTarget(entity, target, plugin)) {
            direction = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        } else {
            direction = getSpawnForwardDirection(entity);
            direction.add(spawnExitSpread());
        }
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            direction = getSpawnForwardDirection(entity);
            direction.setY(0.0D);
        }
        if (direction.lengthSquared() < 0.0001D) {
            return false;
        }

        rotationController.lookAtDirection(direction, false);

        double speed = plugin.getConfig().getDouble("fight.spawn-exit-speed", 0.32D);
        double max = plugin.getConfig().getDouble("fight.spawn-exit-max-horizontal-speed", 0.42D);
        Vector velocity = entity.getVelocity();
        double y = velocity.getY();
        Vector horizontal = new Vector(velocity.getX(), 0.0D, velocity.getZ())
                .multiply(0.45D)
                .add(direction.normalize().multiply(speed));
        if (horizontal.lengthSquared() > max * max) {
            horizontal.normalize().multiply(max);
        }
        horizontal.setY(y);
        entity.setVelocity(horizontal);
        setSprinting((Player) entity, true);
        bot.setState(BotState.SPRINTING);
        return true;
    }

    private Vector getSpawnForwardDirection(Entity entity) {
        Location spawn = bot.getSpawnLocation();
        if (spawn != null && spawn.getWorld() != null && entity != null
                && spawn.getWorld().equals(entity.getWorld())) {
            return spawn.getDirection();
        }
        return entity == null ? new Vector(0.0D, 0.0D, 1.0D) : entity.getLocation().getDirection();
    }

    private boolean shouldIgnoreSpawnBotTarget(Entity entity, Player target, AllianceBotsPlugin plugin) {
        if (plugin == null || target == null || !bot.isAttackOtherBots()
                || !CitizensAPI.getNPCRegistry().isNPC(target)) {
            return false;
        }
        return entity instanceof Player
                && plugin.getBuildFfaIntegration().isPlayerInSpawn((Player) entity)
                && plugin.getBuildFfaIntegration().isPlayerInSpawn(target)
                && plugin.getConfig().getBoolean("fight.bot-vs-bot.ignore-spawn-bots-until-exit", true);
    }

    private Vector spawnExitSpread() {
        int seed = bot.getNPC() == null ? random.nextInt(360) : bot.getNPC().getId() * 47;
        double angle = Math.toRadians(seed % 360);
        double strength = AllianceBotsPlugin.getInstance().getConfig()
                .getDouble("fight.bot-vs-bot.spawn-exit-spread", 0.18D);
        return new Vector(Math.cos(angle) * strength, 0.0D, Math.sin(angle) * strength);
    }

    private void configureNavigator(Navigator navigator, double distanceMargin) {
        float speed = (float) bot.getSettings().getMovementSpeed();
        navigator.getLocalParameters()
                .baseSpeed(speed)
                .speedModifier(speed)
                .distanceMargin(distanceMargin)
                .pathDistanceMargin(distanceMargin)
                .updatePathRate(Math.max(1, AllianceBotsPlugin.getInstance().getConfig()
                        .getInt("fight.navigation.path-update-rate-ticks", 6)))
                .stationaryTicks(Math.max(10, AllianceBotsPlugin.getInstance().getConfig()
                        .getInt("fight.navigation.stationary-ticks", 30)))
                .range((float) Math.max(bot.getSettings().getDetectRange(),
                        AllianceBotsPlugin.getInstance().getConfig()
                                .getDouble("fight.navigation.platform-target-detect-range", 96.0D)));
    }

    private boolean shouldUsePathfinder(Entity entity, Player target, boolean blockedLineOfSight) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || !plugin.getConfig().getBoolean("fight.navigation.use-pathfinder-for-platforms", true)) {
            return false;
        }
        if (blockedLineOfSight && plugin.getConfig()
                .getBoolean("fight.navigation.pathfinder-when-line-of-sight-blocked", true)) {
            return true;
        }
        double yDifference = Math.abs(target.getLocation().getY() - entity.getLocation().getY());
        double threshold = plugin.getConfig().getDouble("fight.navigation.platform-y-threshold", 1.35D);
        if (yDifference >= threshold) {
            return true;
        }
        return entity instanceof Player
                && target.getLocation().getBlockY() > entity.getLocation().getBlockY()
                && !hasClearPathSight((Player) entity, target);
    }

    private boolean hasClearPathSight(Player player, Player target) {
        if (player == null || target == null || !player.getWorld().equals(target.getWorld())) {
            return false;
        }
        if (!player.hasLineOfSight(target)) {
            return false;
        }
        Location start = player.getEyeLocation();
        Location head = target.getEyeLocation();
        Location body = target.getLocation().clone().add(0.0D, target.getEyeHeight() * 0.55D, 0.0D);
        return hasClearRay(start, head) || hasClearRay(start, body);
    }

    private boolean hasClearRay(Location start, Location end) {
        if (start == null || end == null || start.getWorld() == null
                || !start.getWorld().equals(end.getWorld())) {
            return false;
        }
        Vector between = end.toVector().subtract(start.toVector());
        double distance = between.length();
        if (distance < 0.001D) {
            return true;
        }
        BlockIterator iterator = new BlockIterator(start.getWorld(), start.toVector(), between.normalize(),
                0.0D, (int) Math.ceil(distance));
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (isSameBlock(block, start)) {
                continue;
            }
            if (isSameBlock(block, end)) {
                return true;
            }
            if (block.getType() != Material.AIR && block.getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameBlock(Block block, Location location) {
        return block != null && location != null
                && block.getX() == location.getBlockX()
                && block.getY() == location.getBlockY()
                && block.getZ() == location.getBlockZ();
    }

    private boolean shouldUseSpawnPathfinder(Entity entity, Player target) {
        AllianceBotsPlugin plugin = AllianceBotsPlugin.getInstance();
        if (plugin == null || !(entity instanceof Player)
                || !plugin.getConfig().getBoolean("fight.navigation.use-pathfinder-for-platforms", true)) {
            return false;
        }
        double yDelta = target.getLocation().getY() - entity.getLocation().getY();
        double threshold = plugin.getConfig().getDouble("fight.navigation.spawn-platform-y-threshold", 1.0D);
        return yDelta >= threshold || plugin.getBuildFfaIntegration().isPlayerInSpawn(target);
    }

    private boolean canChaseSpawnPlatformTarget(Entity entity, Player player, AllianceBotsPlugin plugin) {
        if (!(entity instanceof Player)
                || !plugin.getConfig().getBoolean("fight.navigation.chase-spawn-platform-targets", true)) {
            return false;
        }
        double yDelta = player.getLocation().getY() - entity.getLocation().getY();
        return yDelta >= plugin.getConfig().getDouble("fight.navigation.spawn-platform-y-threshold", 1.0D)
                || plugin.getBuildFfaIntegration().isPlayerInSpawn((Player) entity);
    }

    private boolean tickTapMovement(Player target) {
        if (tapCooldownTicks > 0) {
            tapCooldownTicks--;
        }
        if (hitSelectCooldownTicks > 0) {
            hitSelectCooldownTicks--;
        }
        if (tapReleaseTicks <= 0 && tapResumeTicks <= 0) {
            return false;
        }
        Entity entity = bot.getNPC().getEntity();
        if (!(entity instanceof Player)) {
            cancelTap();
            return false;
        }
        Player player = (Player) entity;
        if (tapReleaseTicks > 0) {
            Navigator navigator = bot.getNPC().getNavigator();
            navigator.cancelNavigation();
            navigator.setPaused(true);
            forwardMovementEnabled = false;
            bot.setState(BotState.W_TAP_RELEASE);
            if (sTapActive) {
                applySTapVelocity(entity);
            } else {
                releaseForwardVelocity(entity, target);
            }
            applyStrafe(target);
            tapReleaseTicks--;
            if (tapReleaseTicks <= 0) {
                tapResumeTicks = 1;
                tapCooldownTicks = randomBetween(
                        getInt("w-tap", "cooldown-min-ticks", 3),
                        getInt("w-tap", "cooldown-max-ticks", 7));
                bot.setState(BotState.W_TAP_RESUME);
            }
            return true;
        }
        if (tapResumeTicks > 0) {
            bot.getNPC().getNavigator().setPaused(false);
            forwardMovementEnabled = true;
            if (getBoolean("w-tap", "resume-sprint", true)) {
                setSprinting(player, true);
            }
            bot.setState(BotState.W_TAP_RESUME);
            tapResumeTicks--;
            if (tapResumeTicks <= 0) {
                sTapActive = false;
                bot.setState(BotState.CHASE);
            }
        }
        return false;
    }

    private void maybeStartTap(Player target) {
        if (!canTap(target)) {
            return;
        }
        if (getBoolean("s-tap", "enabled", false) && random.nextDouble() < getDouble("s-tap", "chance", 0.25D)) {
            beginTap(randomBetween(
                    getInt("s-tap", "backward-ticks-min", 1),
                    getInt("s-tap", "backward-ticks-max", 2)), true);
            return;
        }
        if (!getBoolean("w-tap", "enabled", true) || random.nextDouble() >= getDouble("w-tap", "chance", defaultWTapChance())) {
            return;
        }
        beginTap(randomBetween(
                getInt("w-tap", "release-forward-min-ticks", defaultWTapReleaseMin()),
                getInt("w-tap", "release-forward-max-ticks", defaultWTapReleaseMax())), false);
    }

    private boolean canTap(Player target) {
        Entity entity = bot.getNPC().getEntity();
        if (!(entity instanceof Player) || target == null || bot.getState() == BotState.KNOCKBACK) {
            return false;
        }
        if (tapReleaseTicks > 0 || tapResumeTicks > 0 || tapCooldownTicks > 0) {
            return false;
        }
        Player player = (Player) entity;
        if (!player.isOnGround()) {
            return false;
        }
        double hitRange = bot.getSettings().getHitRange();
        return entity.getLocation().distanceSquared(target.getLocation()) <= hitRange * hitRange;
    }

    private void beginTap(int releaseTicks, boolean backward) {
        tapReleaseTicks = Math.max(1, releaseTicks);
        lastReleaseTicks = tapReleaseTicks;
        sTapActive = backward;
        forwardMovementEnabled = false;
        bot.setState(BotState.W_TAP_RELEASE);
        Entity entity = bot.getNPC().getEntity();
        if (entity instanceof Player && (backward || getBoolean("w-tap", "disable-sprint-during-release", true))) {
            setSprinting((Player) entity, false);
        }
    }

    private void releaseForwardVelocity(Entity entity, Player target) {
        Vector toTarget = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        toTarget.setY(0.0D);
        if (toTarget.lengthSquared() < 0.0001D) {
            return;
        }
        Vector forward = toTarget.normalize();
        Vector velocity = entity.getVelocity();
        double y = velocity.getY();
        Vector horizontal = new Vector(velocity.getX(), 0.0D, velocity.getZ());
        double forwardComponent = horizontal.dot(forward);
        if (forwardComponent > 0.0D) {
            horizontal.subtract(forward.multiply(forwardComponent * 0.92D));
        }
        horizontal.setY(y);
        entity.setVelocity(horizontal);
    }

    private void applySTapVelocity(Entity entity) {
        Vector direction = entity.getLocation().getDirection();
        direction.setY(0.0D);
        if (direction.lengthSquared() < 0.0001D) {
            return;
        }
        Vector velocity = entity.getVelocity();
        double y = velocity.getY();
        Vector backward = direction.normalize().multiply(-getDouble("s-tap", "backward-speed-multiplier", 0.35D) * 0.18D);
        backward.setY(y);
        entity.setVelocity(backward);
    }

    private boolean shouldDelayForHitSelect(Player target) {
        if (hitSelectWaitTicks > 0) {
            hitSelectWaitTicks--;
            return true;
        }
        if (!getBoolean("hit-select", "enabled", false) || hitSelectCooldownTicks > 0 || tapReleaseTicks > 0) {
            return false;
        }
        Entity entity = bot.getNPC().getEntity();
        if (entity == null || target == null) {
            return false;
        }
        double hitRange = bot.getSettings().getHitRange();
        if (entity.getLocation().distanceSquared(target.getLocation()) > hitRange * hitRange) {
            return false;
        }
        if (random.nextDouble() >= getDouble("hit-select", "chance", 0.20D)) {
            return false;
        }
        hitSelectWaitTicks = randomBetween(
                getInt("hit-select", "wait-min-ticks", 1),
                getInt("hit-select", "wait-max-ticks", 4));
        hitSelectCooldownTicks = hitSelectWaitTicks + 8;
        return true;
    }

    private void cancelTap() {
        tapReleaseTicks = 0;
        tapResumeTicks = 0;
        sTapActive = false;
        forwardMovementEnabled = true;
        Entity entity = bot.getNPC() == null ? null : bot.getNPC().getEntity();
        if (entity instanceof Player) {
            setSprinting((Player) entity, true);
        }
    }

    private void setSprinting(Player player, boolean sprinting) {
        sprintState = sprinting;
        player.setSprinting(sprinting);
    }

    private int randomBetween(int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + random.nextInt(Math.max(1, high - low + 1));
    }

    private boolean getBoolean(String section, String key, boolean fallback) {
        FileConfiguration config = AllianceBotsPlugin.getInstance().getConfig();
        String difficultyPath = difficultyPath(section, key);
        if (config.contains(difficultyPath)) {
            return config.getBoolean(difficultyPath);
        }
        String path = "fight." + section + "." + key;
        return config.contains(path) ? config.getBoolean(path) : fallback;
    }

    private int getInt(String section, String key, int fallback) {
        FileConfiguration config = AllianceBotsPlugin.getInstance().getConfig();
        String difficultyPath = difficultyPath(section, key);
        if (config.contains(difficultyPath)) {
            return config.getInt(difficultyPath);
        }
        String path = "fight." + section + "." + key;
        return config.contains(path) ? config.getInt(path) : fallback;
    }

    private double getDouble(String section, String key, double fallback) {
        FileConfiguration config = AllianceBotsPlugin.getInstance().getConfig();
        String difficultyPath = difficultyPath(section, key);
        if (config.contains(difficultyPath)) {
            return config.getDouble(difficultyPath);
        }
        String path = "fight." + section + "." + key;
        return config.contains(path) ? config.getDouble(path) : fallback;
    }

    private String difficultyPath(String section, String key) {
        return "fight.difficulty." + bot.getDifficulty().name().toLowerCase(Locale.ENGLISH) + "." + section + "." + key;
    }

    private double defaultWTapChance() {
        if (bot.getDifficulty() == BotDifficulty.EASY) {
            return 0.0D;
        }
        if (bot.getDifficulty() == BotDifficulty.HARD) {
            return 0.75D;
        }
        return 0.35D;
    }

    private int defaultWTapReleaseMin() {
        if (bot.getDifficulty() == BotDifficulty.MEDIUM) {
            return 2;
        }
        return 1;
    }

    private int defaultWTapReleaseMax() {
        if (bot.getDifficulty() == BotDifficulty.EASY) {
            return 3;
        }
        if (bot.getDifficulty() == BotDifficulty.HARD) {
            return 2;
        }
        return 4;
    }

    private void debugTap(boolean successfulHit, boolean wTapTriggered) {
        if (!bot.isDebug()) {
            return;
        }
        if (!successfulHit && !wTapTriggered
                && bot.getState() != BotState.W_TAP_RELEASE
                && bot.getState() != BotState.W_TAP_RESUME) {
            return;
        }
        AllianceBotsPlugin.getInstance().getLogger().info("debug " + bot.getNPC().getName()
                + " successfulHit=" + successfulHit
                + " wTapTriggered=" + wTapTriggered
                + " wTapState=" + bot.getState()
                + " releaseTicks=" + lastReleaseTicks
                + " sprintState=" + sprintState
                + " forwardMovementEnabled=" + forwardMovementEnabled
                + " wTapCooldown=" + tapCooldownTicks);
    }

    private void applyStrafe(Player target) {
        Entity entity = bot.getNPC().getEntity();
        if (entity == null || !AllianceBotsPlugin.getInstance().getConfig().getBoolean("fight.strafe.enabled", true)) {
            return;
        }
        if (entity instanceof Player && !((Player) entity).isOnGround()) {
            return;
        }
        if (strafeTicks-- <= 0) {
            strafeDirection = random.nextBoolean() ? 1 : -1;
            int min = AllianceBotsPlugin.getInstance().getConfig().getInt("fight.strafe.minimum-duration-ticks", 8);
            int max = AllianceBotsPlugin.getInstance().getConfig().getInt("fight.strafe.maximum-duration-ticks", 24);
            strafeTicks = min + random.nextInt(Math.max(1, max - min + 1));
        }
        Vector toTarget = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        toTarget.setY(0.0);
        if (toTarget.lengthSquared() < 0.0001) {
            return;
        }
        Vector side = new Vector(-toTarget.getZ(), 0.0, toTarget.getX()).normalize().multiply(strafeDirection);
        Location next = entity.getLocation().clone().add(side.clone().multiply(0.6D));
        if (!isSafeGround(next)) {
            strafeDirection *= -1;
            return;
        }
        double speed = Math.min(AllianceBotsPlugin.getInstance().getConfig().getDouble("fight.strafe.speed-multiplier", 0.11), 0.11D);
        Vector velocity = entity.getVelocity();
        double y = velocity.getY();
        Vector horizontal = new Vector(velocity.getX(), 0.0D, velocity.getZ()).multiply(0.62D).add(side.multiply(speed));
        double max = Math.min(AllianceBotsPlugin.getInstance().getConfig().getDouble("fight.strafe.max-horizontal-speed", 0.36), 0.36D);
        if (horizontal.lengthSquared() > max * max) {
            horizontal.normalize().multiply(max);
        }
        horizontal.setY(y);
        entity.setVelocity(horizontal);
    }

    private void maybeJump(Entity entity, Player target, double distance) {
        if (!(entity instanceof Player) || !AllianceBotsPlugin.getInstance().getConfig().getBoolean("fight.jump.enabled", true)) {
            return;
        }
        Player player = (Player) entity;
        if (jumpCooldown > 0) {
            jumpCooldown--;
            return;
        }
        if (!player.isOnGround()) {
            return;
        }
        double chance = AllianceBotsPlugin.getInstance().getConfig().getDouble("fight.jump.combat-jump-chance", 0.04);
        boolean obstacle = hasOneBlockObstacle(entity, target);
        if (obstacle || (distance <= bot.getSettings().getSwingRange() && random.nextDouble() < chance)) {
            Vector velocity = entity.getVelocity();
            velocity.setY(0.42D);
            entity.setVelocity(velocity);
            jumpCooldown = AllianceBotsPlugin.getInstance().getConfig().getInt("fight.jump.cooldown-ticks", 12);
        }
    }

    private boolean hasOneBlockObstacle(Entity entity, Player target) {
        Vector direction = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() < 0.0001) {
            return false;
        }
        Location front = entity.getLocation().clone().add(direction.normalize().multiply(0.75D));
        return front.getBlock().getType().isSolid() && !front.clone().add(0.0, 1.0, 0.0).getBlock().getType().isSolid();
    }

    private boolean isSafeGround(Location location) {
        Material ground = location.clone().subtract(0.0, 1.0, 0.0).getBlock().getType();
        return ground != Material.AIR && ground.isSolid();
    }

    private void stopNavigation() {
        if (bot.getNPC() != null && bot.getNPC().isSpawned()) {
            bot.getNPC().getNavigator().cancelNavigation();
        }
    }
}
