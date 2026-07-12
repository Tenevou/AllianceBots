package ru.alliancemc.alliancebots.bot;

import net.citizensnpcs.util.PlayerAnimation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class KnockbackController {
    private final ClipBotTrait bot;
    private int pauseTicks;

    public KnockbackController(ClipBotTrait bot) {
        this.bot = bot;
    }

    public void apply(Entity damager) {
        Entity entity = bot.getNPC().getEntity();
        if (entity == null) {
            return;
        }
        if (entity instanceof Player) {
            PlayerAnimation.HURT.play((Player) entity);
        }
        bot.getNPC().getNavigator().cancelNavigation();
        bot.getNPC().getNavigator().setPaused(true);
        pauseTicks = bot.getSettings().getNavigationPauseTicks();
        if (bot.getSettings().isCustomKnockback() && damager != null) {
            Vector direction = entity.getLocation().toVector().subtract(damager.getLocation().toVector());
            direction.setY(0.0);
            if (direction.lengthSquared() > 0.0001) {
                double horizontal = bot.getSettings().getKnockbackHorizontal();
                if (damager instanceof Player && ((Player) damager).isSprinting()) {
                    horizontal += bot.getSettings().getKnockbackExtraHorizontal();
                }
                double vertical = bot.getSettings().getKnockbackVertical();
                double maxVertical = bot.getSettings().getKnockbackMaxVertical();
                if (maxVertical > 0.0D) {
                    vertical = Math.min(vertical, maxVertical);
                }
                direction.normalize().multiply(horizontal);
                direction.setY(vertical);
                entity.setVelocity(direction);
            }
        }
        bot.setState(BotState.KNOCKBACK);
    }

    public boolean tickPause() {
        if (pauseTicks > 0) {
            pauseTicks--;
            return false;
        }
        if (bot.getNPC() != null && bot.getNPC().isSpawned()) {
            bot.getNPC().getNavigator().setPaused(false);
        }
        return true;
    }
}
