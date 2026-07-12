package ru.alliancemc.alliancebots.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import ru.alliancemc.alliancebots.bot.ClipBotTrait;

public final class ClipBotAttackEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClipBotTrait bot;
    private final Player target;
    private double damage;
    private boolean cancelled;

    public ClipBotAttackEvent(ClipBotTrait bot, Player target, double damage) {
        this.bot = bot;
        this.target = target;
        this.damage = damage;
    }

    public ClipBotTrait getBot() {
        return bot;
    }

    public Player getTarget() {
        return target;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = Math.max(0.0, damage);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
