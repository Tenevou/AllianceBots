package ru.alliancemc.alliancebots.bot;

import net.citizensnpcs.api.ai.Navigator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class MovementController {
    private final ClipBotTrait bot;
    private int retargetCooldown;

    public MovementController(ClipBotTrait bot) {
        this.bot = bot;
    }

    public void chase(Player target) {
        Entity entity = bot.getNPC().getEntity();
        if (entity == null || target == null) {
            stop();
            return;
        }
        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        double stopDistance = bot.getSettings().getStopDistance();
        Navigator navigator = bot.getNPC().getNavigator();
        navigator.getLocalParameters().baseSpeed((float) bot.getSettings().getMovementSpeed());
        navigator.getLocalParameters().speedModifier((float) bot.getSettings().getMovementSpeed());

        if (distanceSquared <= stopDistance * stopDistance) {
            if (navigator.isNavigating()) {
                navigator.cancelNavigation();
            }
            entity.setVelocity(entity.getVelocity().multiply(0.35));
            if (bot.getState() != BotState.SWING && bot.getState() != BotState.ATTACK) {
                bot.setState(BotState.CHASE);
            }
            return;
        }

        if (retargetCooldown > 0) {
            retargetCooldown--;
        }
        if (!navigator.isNavigating() || retargetCooldown <= 0) {
            navigator.setPaused(false);
            navigator.setStraightLineTarget(target, false);
            retargetCooldown = 10;
        }
        if (bot.getSettings().isSprint() && entity instanceof Player) {
            ((Player) entity).setSprinting(true);
        }
        if (bot.getState() != BotState.SWING && bot.getState() != BotState.ATTACK) {
            bot.setState(BotState.CHASE);
        }
    }

    public void stop() {
        if (bot.getNPC() != null && bot.getNPC().isSpawned()) {
            bot.getNPC().getNavigator().cancelNavigation();
        }
    }
}
