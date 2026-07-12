package ru.alliancemc.alliancebots.bot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class ClipBotController {
    private final ClipBotTrait bot;
    private final MovementController movementController;
    private final RotationController rotationController;
    private final AttackController attackController;
    private final KnockbackController knockbackController;
    private int lostTicks;

    public ClipBotController(ClipBotTrait bot) {
        this.bot = bot;
        this.movementController = new MovementController(bot);
        this.rotationController = new RotationController(bot);
        this.attackController = new AttackController(bot);
        this.knockbackController = new KnockbackController(bot);
    }

    public void tick(long tick) {
        if (!bot.isRunning()) {
            return;
        }
        if (bot.getNPC() == null || !bot.getNPC().isSpawned()) {
            bot.setState(BotState.IDLE);
            return;
        }
        if (bot.getState() == BotState.KNOCKBACK) {
            if (knockbackController.tickPause()) {
                bot.setState(BotState.CHASE);
            } else {
                return;
            }
        }

        Player target = getValidTarget();
        if (target == null) {
            movementController.stop();
            bot.setState(BotState.TARGET_LOST);
            lostTicks++;
            if (lostTicks >= bot.getSettings().getTargetLostTimeoutTicks()) {
                bot.stop(true);
            }
            return;
        }

        lostTicks = 0;
        rotationController.lookAt(target);
        movementController.chase(target);
        attackController.tryAttack(target, tick);
    }

    public void enterKnockback(Entity damager) {
        knockbackController.apply(damager);
    }

    public void stop() {
        movementController.stop();
    }

    private Player getValidTarget() {
        if (bot.getTargetUuid() == null) {
            return null;
        }
        Player target = Bukkit.getPlayer(bot.getTargetUuid());
        if (target == null || !target.isOnline() || target.isDead()) {
            return null;
        }
        if (bot.getNPC() == null || bot.getNPC().getEntity() == null) {
            return null;
        }
        if (!target.getWorld().equals(bot.getNPC().getEntity().getWorld())) {
            return null;
        }
        double max = bot.getSettings().getDetectRange();
        if (bot.getNPC().getEntity().getLocation().distanceSquared(target.getLocation()) > max * max) {
            return null;
        }
        return target;
    }
}
